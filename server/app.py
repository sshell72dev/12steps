from __future__ import annotations

import hmac
import json
import secrets
import string
import urllib.error
import urllib.request
from datetime import datetime
from functools import wraps
from pathlib import Path
from zoneinfo import ZoneInfo

from flask import (
    Flask,
    jsonify,
    redirect,
    render_template,
    request,
    session,
    url_for,
)
from werkzeug.security import check_password_hash, generate_password_hash

import config
import db
import psych
import roles
import translate
import yookassa
from plugins import voice as voice_plugin
from plugins import messenger as messenger_plugin

app = Flask(__name__)
app.secret_key = config.SECRET_KEY
app.config.update(
    SESSION_COOKIE_HTTPONLY=True,
    SESSION_COOKIE_SAMESITE="Lax",
    SESSION_COOKIE_SECURE=True,
    TEMPLATES_AUTO_RELOAD=False,
)

SYSTEM_PROMPT = roles.ANALYSIS_REVIEW


def _init() -> None:
    db.init_schema()


try:
    _init()
except Exception:
    # Schema is created on first successful DB call from a request.
    pass


def login_required(view):
    @wraps(view)
    def wrapped(*args, **kwargs):
        if session.get("admin") != config.ADMIN_USERNAME:
            return redirect(url_for("login"))
        return view(*args, **kwargs)

    return wrapped


def _api_ok() -> bool:
    token = (request.headers.get("X-Api-Token") or "").strip()
    expected = config.API_TOKEN
    if not expected or not token:
        return False
    return hmac.compare_digest(token, expected)


voice_plugin.register(app, login_required, _api_ok)
messenger_plugin.register(app, login_required, _api_ok)


def _as_bool(value) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value != 0
    return str(value or "").strip().lower() in ("1", "true", "on", "yes")


@app.before_request
def ensure_schema() -> None:
    if request.endpoint in {"static"}:
        return
    try:
        db.init_schema()
    except Exception as exc:
        if request.path.startswith("/api/"):
            return
        if request.endpoint == "health":
            return
        app.logger.exception("DB init failed: %s", exc)


@app.get("/")
def root():
    if session.get("admin") == config.ADMIN_USERNAME:
        return redirect(url_for("settings"))
    return redirect(url_for("login"))


@app.get("/health")
def health():
    try:
        db.init_schema()
        db.get_setting("deepseek_model", config.DEFAULT_MODEL)
        db_ok = True
    except Exception:
        db_ok = False
    return jsonify({"ok": True, "db": db_ok, "domain": config.DOMAIN})


@app.route("/login", methods=["GET", "POST"])
def login():
    error = ""
    if request.method == "POST":
        username = (request.form.get("username") or "").strip()
        password = request.form.get("password") or ""
        stored_hash = ""
        try:
            stored_hash = db.get_setting("admin_password_hash", "")
        except Exception:
            stored_hash = ""

        ok_user = hmac.compare_digest(username, config.ADMIN_USERNAME)
        if stored_hash:
            ok_pass = check_password_hash(stored_hash, password)
        else:
            ok_pass = hmac.compare_digest(password, config.ADMIN_PASSWORD)

        if ok_user and ok_pass:
            if not stored_hash and config.ADMIN_PASSWORD:
                try:
                    db.set_setting(
                        "admin_password_hash",
                        generate_password_hash(config.ADMIN_PASSWORD),
                    )
                except Exception:
                    pass
            session["admin"] = config.ADMIN_USERNAME
            return redirect(url_for("settings"))
        error = "Неверный логин или пароль"

    return render_template("login.html", error=error, domain=config.DOMAIN)


@app.post("/logout")
def logout():
    session.clear()
    return redirect(url_for("login"))


@app.route("/settings", methods=["GET", "POST"])
@login_required
def settings():
    notice = ""
    warn = ""
    saved_key = db.get_setting("deepseek_api_key", "")
    model = db.get_setting("deepseek_model", config.DEFAULT_MODEL)
    if model not in {item[0] for item in config.MODELS}:
        model = config.DEFAULT_MODEL
    premium_price = db.get_setting("premium_price_rub", "199")
    premium_days = db.get_setting("premium_days_after_payment", "365")
    yk_shop = db.get_setting("yookassa_shop_id", "") or config.getenv("YOOKASSA_SHOP_ID", "")
    yk_secret_saved = bool(
        db.get_setting("yookassa_secret_key", "") or config.getenv("YOOKASSA_SECRET_KEY", "")
    )
    yk_test_mode = db.get_setting("yookassa_test_mode", "1") in ("1", "true", "on", "yes")
    psych_dialogue_extra = _psych_int_setting("psych_dialogue_extra", 5)
    psych_work_questions = _psych_int_setting("psych_work_questions", 5)
    if request.args.get("rotated"):
        notice = "Новый код для приложения создан. Старый больше не действует."

    if request.method == "POST":
        new_key = (request.form.get("api_key") or "").strip()
        new_model = (request.form.get("model") or "").strip()
        new_price = (request.form.get("premium_price_rub") or "").strip().replace(",", ".")
        new_days = (request.form.get("premium_days_after_payment") or "").strip()
        yk_form = "yookassa_shop_id" in request.form
        new_shop = (request.form.get("yookassa_shop_id") or "").strip() if yk_form else ""
        new_yk_secret = (request.form.get("yookassa_secret_key") or "").strip() if yk_form else ""
        if yk_form:
            yk_test_mode = bool(request.form.get("yookassa_test_mode"))
        allowed = {item[0] for item in config.MODELS}
        if new_model not in allowed:
            warn = "Выберите одну из доступных моделей."
        else:
            if new_key:
                db.set_setting("deepseek_api_key", new_key)
                saved_key = new_key
            db.set_setting("deepseek_model", new_model)
            model = new_model
            if new_price:
                try:
                    price_val = float(new_price)
                    if price_val < 0:
                        warn = "Сумма Premium не может быть отрицательной."
                    else:
                        premium_price = (
                            str(int(price_val))
                            if price_val == int(price_val)
                            else str(price_val)
                        )
                        db.set_setting("premium_price_rub", premium_price)
                except ValueError:
                    warn = "Сумма Premium должна быть числом (рубли)."
            if new_days and not warn:
                try:
                    days_val = int(float(new_days.replace(",", ".")))
                    if days_val < 1 or days_val > 3650:
                        warn = "Срок Premium после оплаты: от 1 до 3650 дней."
                    else:
                        premium_days = str(days_val)
                        db.set_setting("premium_days_after_payment", premium_days)
                except ValueError:
                    warn = "Срок Premium должен быть целым числом дней."
            if yk_form and not warn:
                if new_shop:
                    db.set_setting("yookassa_shop_id", new_shop)
                    yk_shop = new_shop
                if new_yk_secret:
                    db.set_setting("yookassa_secret_key", new_yk_secret)
                    yk_secret_saved = True
                db.set_setting("yookassa_test_mode", "1" if yk_test_mode else "0")
            if "psych_dialogue_extra" in request.form and not warn:
                extra_raw = (request.form.get("psych_dialogue_extra") or "").strip()
                work_raw = (request.form.get("psych_work_questions") or "").strip()
                try:
                    extra_val = int(extra_raw)
                    work_val = int(work_raw)
                    if extra_val < 1 or extra_val > 30 or work_val < 1 or work_val > 30:
                        warn = "Лимиты вопросов психолога: целые числа от 1 до 30."
                    else:
                        psych_dialogue_extra = extra_val
                        psych_work_questions = work_val
                        db.set_setting("psych_dialogue_extra", str(extra_val))
                        db.set_setting("psych_work_questions", str(work_val))
                except ValueError:
                    warn = "Лимиты вопросов психолога должны быть целыми числами."
            if request.form.get("action") != "test" and not warn:
                notice = "Сохранено."

        if request.form.get("action") == "test" and not warn:
            key = db.get_setting("deepseek_api_key", "")
            if not key:
                warn = "Сначала сохраните API Key."
            else:
                try:
                    _deepseek_chat(
                        key,
                        model,
                        [{"role": "user", "content": "Ответьте одним словом: готово."}],
                        max_tokens=64,
                        thinking=False,
                    )
                    notice = "Ключ и модель работают."
                except Exception as exc:
                    warn = f"Проверка не прошла: {exc}"

    masked = ("•" * 24) if saved_key else ""
    yk_secret_masked = ("•" * 24) if yk_secret_saved else ""
    payments = []
    try:
        payments = db.list_premium_payments(25)
    except Exception:
        payments = []
    return render_template(
        "settings.html",
        domain=config.DOMAIN,
        notice=notice,
        warn=warn,
        api_key_masked=masked,
        has_key=bool(saved_key),
        model=model,
        models=config.MODELS,
        premium_price_rub=premium_price,
        premium_days_after_payment=premium_days,
        yookassa_shop_id=yk_shop,
        yookassa_secret_masked=yk_secret_masked,
        yookassa_has_secret=yk_secret_saved,
        yookassa_test_mode=yk_test_mode,
        yookassa_configured=yookassa.is_configured(),
        premium_payments=payments,
        webhook_url=f"https://{config.DOMAIN}/api/v1/premium/webhook",
        return_url=f"https://{config.DOMAIN}/premium/return",
        admin_code=_admin_app_code(),
        psych_dialogue_extra=psych_dialogue_extra,
        psych_work_questions=psych_work_questions,
    )


def _psych_int_setting(key: str, default: int = 5) -> int:
    raw = (db.get_setting(key, str(default)) or str(default)).strip()
    try:
        n = int(raw)
    except ValueError:
        n = default
    return max(1, min(30, n))


def _admin_app_code() -> str:
    code = db.get_setting("admin_app_code", "")
    if not code:
        code = _new_admin_code()
        db.set_setting("admin_app_code", code)
    return code


def _new_admin_code() -> str:
    alphabet = string.ascii_uppercase + string.digits
    alphabet = alphabet.replace("0", "").replace("O", "").replace("1", "").replace("I", "")
    return "".join(secrets.choice(alphabet) for _ in range(8))


def _admin_code_ok(value: str) -> bool:
    expected = db.get_setting("admin_app_code", "")
    got = (value or "").strip().upper()
    if not expected or not got:
        return False
    return hmac.compare_digest(got, expected.strip().upper())


DEFAULT_ANALYSES_PATH = Path(__file__).resolve().parent / "data" / "self-analysis-questions.json"


def _load_seed_catalog() -> dict:
    if not DEFAULT_ANALYSES_PATH.exists():
        return {"self_analyses": []}
    return json.loads(DEFAULT_ANALYSES_PATH.read_text(encoding="utf-8"))


def _get_catalog_payload() -> tuple[dict, str]:
    row = db.get_analysis_catalog()
    if row and (row.get("body") or "").strip():
        try:
            data = json.loads(row["body"])
            if isinstance(data, dict) and isinstance(data.get("self_analyses"), list):
                return data, str(row.get("updated_at") or "")
        except json.JSONDecodeError:
            pass
    data = _load_seed_catalog()
    updated = db.set_analysis_catalog(json.dumps(data, ensure_ascii=False))
    return data, updated


def _save_catalog(data: dict) -> str:
    if not isinstance(data, dict) or not isinstance(data.get("self_analyses"), list):
        raise ValueError("invalid_catalog")
    cleaned = []
    for item in data["self_analyses"]:
        if isinstance(item, dict) and str(item.get("id") or "").strip():
            cleaned.append(item)
    payload = {"self_analyses": cleaned}
    if data.get("source"):
        payload["source"] = data["source"]
    return db.set_analysis_catalog(json.dumps(payload, ensure_ascii=False))


@app.post("/settings/admin-code")
@login_required
def rotate_admin_code():
    db.set_setting("admin_app_code", _new_admin_code())
    return redirect(url_for("settings", rotated=1))


@app.route("/notes", methods=["GET", "POST"])
@login_required
def notes_admin():
    notice = ""
    warn = ""
    if request.method == "POST":
        note_id = (request.form.get("id") or "").strip()
        title = (request.form.get("title") or "").strip()
        body = request.form.get("body") or ""
        mode = (request.form.get("mode") or "collapsed").strip()
        show_title = request.form.get("show_title") in ("1", "on", "true")
        if not note_id:
            warn = "Укажите идентификатор подсказки."
        else:
            db.upsert_note(note_id, title, body, mode, show_title)
            notice = "Подсказка сохранена. Приложения заберут её при следующей синхронизации."
            return redirect(url_for("notes_admin", saved=1, q=note_id))
    if request.args.get("saved"):
        notice = "Подсказка сохранена. Приложения заберут её при следующей синхронизации."
    query = (request.args.get("q") or "").strip()
    items = db.list_notes()
    if query:
        q = query.lower()
        items = [
            item
            for item in items
            if q in item["id"].lower()
            or q in item["title"].lower()
            or q in item["text"].lower()
        ]
    edit_id = (request.args.get("edit") or "").strip()
    current = db.get_note(edit_id) if edit_id else None
    return render_template(
        "notes.html",
        domain=config.DOMAIN,
        notice=notice,
        warn=warn,
        items=items,
        query=query,
        current=current,
        modes=db.NOTE_MODES,
    )


@app.get("/api/v1/config")
def api_config():
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    model = db.get_setting("deepseek_model", config.DEFAULT_MODEL)
    has_key = bool(db.get_setting("deepseek_api_key", ""))
    return jsonify(
        {
            "model": model,
            "configured": has_key,
            "models": [item[0] for item in config.MODELS],
        }
    )


@app.get("/api/v1/notes")
def api_notes():
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    since = (request.args.get("since") or "").strip()
    notes = db.list_notes(since)
    return jsonify({"ok": True, "notes": notes})


@app.post("/api/v1/notes")
def api_notes_upsert():
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    payload = request.get_json(silent=True) or {}
    if not _admin_code_ok(str(payload.get("code") or "")):
        return jsonify({"error": "admin_code"}), 403
    note_id = str(payload.get("id") or "").strip()
    if not note_id:
        return jsonify({"error": "id_required"}), 400
    note = db.upsert_note(
        note_id,
        str(payload.get("title") or ""),
        str(payload.get("text") if payload.get("text") is not None else payload.get("body") or ""),
        str(payload.get("mode") or "collapsed"),
        _as_bool(payload.get("show_title", payload.get("showTitle"))),
    )
    return jsonify({"ok": True, "note": note})


@app.post("/api/v1/admin/activate")
def api_admin_activate():
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    payload = request.get_json(silent=True) or {}
    if not _admin_code_ok(str(payload.get("code") or "")):
        return jsonify({"ok": False, "error": "invalid_code"}), 403
    return jsonify({"ok": True, "admin": True})


@app.route("/analyses", methods=["GET"])
@login_required
def analyses_admin():
    catalog, updated_at = _get_catalog_payload()
    return render_template(
        "analyses.html",
        domain=config.DOMAIN,
        catalog_json=json.dumps(catalog, ensure_ascii=False),
        updated_at=updated_at,
        seed_json=json.dumps(_load_seed_catalog(), ensure_ascii=False),
    )


@app.post("/analyses/save")
@login_required
def analyses_save():
    payload = request.get_json(silent=True) or {}
    catalog = payload.get("catalog")
    try:
        updated = _save_catalog(catalog if isinstance(catalog, dict) else {})
    except ValueError:
        return jsonify({"ok": False, "error": "invalid"}), 400
    return jsonify({"ok": True, "updated_at": updated})


@app.get("/api/v1/analyses")
def api_analyses():
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    catalog, updated_at = _get_catalog_payload()
    since = (request.args.get("since") or "").strip()
    if since and updated_at and updated_at <= since:
        return jsonify({"ok": True, "updated_at": updated_at, "unchanged": True})
    return jsonify({"ok": True, "updated_at": updated_at, "catalog": catalog})


@app.post("/api/v1/analyses")
def api_analyses_save():
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    payload = request.get_json(silent=True) or {}
    if not _admin_code_ok(str(payload.get("code") or "")):
        return jsonify({"error": "admin_code"}), 403
    catalog = payload.get("catalog")
    try:
        updated = _save_catalog(catalog if isinstance(catalog, dict) else {})
    except ValueError:
        return jsonify({"ok": False, "error": "invalid"}), 400
    return jsonify({"ok": True, "updated_at": updated})


@app.post("/api/v1/analyze")
def api_analyze():
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401

    payload = request.get_json(silent=True) or {}
    title = str(payload.get("title") or "Самоанализ").strip()
    answers = payload.get("answers") or []
    if not isinstance(answers, list) or not answers:
        return jsonify({"error": "answers_required"}), 400

    lines = []
    for item in answers:
        if not isinstance(item, dict):
            continue
        question = str(item.get("question") or "").strip()
        answer = str(item.get("answer") or "").strip()
        if question or answer:
            lines.append(f"{question}\n- {answer}")
    if not lines:
        return jsonify({"error": "answers_empty"}), 400

    api_key = db.get_setting("deepseek_api_key", "")
    model = _model_for_user(_payload_premium(payload))
    if not api_key:
        return jsonify({"error": "not_configured"}), 503

    language = psych.resolve_response_language(
        str(payload.get("language") or payload.get("language_code") or "ru"),
        "\n".join(lines),
    )
    user_prompt = psych.render_language(language) + _build_self_analysis_prompt(
        title,
        lines,
        questionnaire=str(payload.get("questionnaire") or "").strip(),
        personality=str(payload.get("personality") or "").strip(),
        name=str(payload.get("name") or "").strip(),
        collect_personality=bool(payload.get("collect_personality")),
        language=language,
    )
    system = roles.system_for_chat("analysis.review") or SYSTEM_PROMPT
    collect = bool(payload.get("collect_personality"))
    try:
        text = _deepseek_chat(
            api_key,
            model,
            [
                {"role": "system", "content": system},
                {"role": "user", "content": user_prompt},
            ],
            max_tokens=3500 if collect else 2200,
            timeout=180,
            thinking=model == MODEL_PREMIUM,
        )
    except Exception as exc:
        return jsonify({"error": "upstream", "detail": str(exc)}), 502

    cleaned, portrait = psych.extract_personality(text)
    result = {"title": title, "model": model, "text": cleaned}
    if portrait:
        result["personality"] = portrait
    if payload.get("admin"):
        result["prompt"] = f"SYSTEM:\n{system}\n\nUSER:\n{user_prompt}"
    return jsonify(result)


@app.post("/api/v1/psych")
def api_psych():
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401

    payload = request.get_json(silent=True) or {}
    kind = str(payload.get("kind") or "").strip()
    allowed = {
        "analyze",
        "recommend",
        "questions",
        "questions_retry",
        "questions_next",
        "dialogue_question",
        "assistant",
        "reminder_outreach",
        "tts_understanding",
    }
    if kind not in allowed:
        return jsonify({"error": "kind_required"}), 400

    api_key = db.get_setting("deepseek_api_key", "")
    model = _model_for_user(_payload_premium(payload))
    if not api_key:
        return jsonify({"error": "not_configured"}), 503

    try:
        user_prompt = psych.build_prompt(kind, payload)
        system = psych.system_for(kind)
    except ValueError:
        return jsonify({"error": "kind_required"}), 400

    json_mode = kind in {
        "questions",
        "questions_retry",
        "questions_next",
        "dialogue_question",
    }
    max_tokens = 400 if kind in {"reminder_outreach", "tts_understanding"} else 1800
    if kind in {"analyze", "recommend", "assistant"}:
        profile = payload.get("profile") if isinstance(payload.get("profile"), dict) else {}
        collect = bool(profile.get("my_personality_collect_enabled"))
        max_tokens = 4000 if collect else 3200
    if json_mode:
        max_tokens = 900
    timeout = 60 if kind in {"reminder_outreach", "tts_understanding"} else 90
    if kind in {"analyze", "recommend", "assistant"}:
        timeout = 180

    try:
        text = _deepseek_chat(
            api_key,
            model,
            [
                {"role": "system", "content": system},
                {"role": "user", "content": user_prompt},
            ],
            max_tokens=max_tokens,
            timeout=timeout,
            thinking=False,
        )
    except Exception as exc:
        return jsonify({"error": "upstream", "detail": str(exc)}), 502

    parsed = psych.parse_model_output(kind, text, question_limit=psych.question_count(payload))
    if kind in {"questions", "questions_retry"} and not parsed.get("questions"):
        try:
            retry_prompt = psych.build_prompt("questions_retry", payload)
            text = _deepseek_chat(
                api_key,
                model,
                [
                    {"role": "system", "content": psych.system_for("questions_retry")},
                    {"role": "user", "content": retry_prompt},
                ],
                max_tokens=900,
                timeout=timeout,
                thinking=False,
            )
            parsed = psych.parse_model_output(
                "questions_retry", text, question_limit=psych.question_count(payload)
            )
            parsed["kind"] = kind
        except Exception:
            pass
        if not parsed.get("questions"):
            return jsonify({"error": "bad_json"}), 502

    if kind in {"dialogue_question", "questions_next"} and not parsed.get("question"):
        return jsonify({"error": "bad_json"}), 502

    parsed["model"] = model
    if payload.get("admin"):
        parsed["prompt"] = f"SYSTEM:\n{system}\n\nUSER:\n{user_prompt}"
    return jsonify(parsed)


@app.post("/api/v1/translate")
def api_translate():
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401

    payload = request.get_json(silent=True) or {}
    target, items = translate.normalize_request(payload)
    if not items:
        return jsonify({"error": "items_required"}), 400
    if len(items) > 80:
        return jsonify({"error": "too_many_items"}), 400

    api_key = db.get_setting("deepseek_api_key", "")
    model = MODEL_FLASH
    if not api_key:
        return jsonify({"error": "not_configured"}), 503

    user_prompt = translate.build_user_prompt(target, items)
    try:
        text = _deepseek_chat(
            api_key,
            model,
            [
                {"role": "system", "content": translate.SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            max_tokens=min(8000, 200 + sum(len(i["text"]) for i in items) * 3),
            timeout=180,
            thinking=False,
        )
    except Exception as exc:
        return jsonify({"error": "upstream", "detail": str(exc)}), 502

    parsed = translate.parse_items(text)
    if not parsed:
        return jsonify({"error": "bad_json"}), 502
    return jsonify({"items": parsed, "model": model, "target_language": target})


@app.post("/api/v1/chat")
def api_chat():
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401

    payload = request.get_json(silent=True) or {}
    role = str(payload.get("role") or "").strip()
    program = str(payload.get("program") or "").strip()
    system = str(payload.get("system") or "").strip()
    user = str(payload.get("user") or "").strip()
    language = str(payload.get("language") or payload.get("language_code") or "").strip()
    if not user:
        return jsonify({"error": "user_required"}), 400
    try:
        max_tokens = int(payload.get("max_tokens") or 4000)
    except (TypeError, ValueError):
        max_tokens = 4000
    max_tokens = max(256, min(max_tokens, 8000))

    api_key = db.get_setting("deepseek_api_key", "")
    model = _model_for_user(_payload_premium(payload))
    if not api_key:
        return jsonify({"error": "not_configured"}), 503

    if role:
        resolved = roles.system_for_chat(role, program)
        if not resolved:
            return jsonify({"error": "role_unknown"}), 400
        system = resolved
    if language:
        user = (psych.render_language(language) + user).strip()

    messages = []
    if system:
        messages.append({"role": "system", "content": system})
    messages.append({"role": "user", "content": user})
    try:
        text = _deepseek_chat(
            api_key,
            model,
            messages,
            max_tokens=max_tokens,
            timeout=180,
            thinking=model == MODEL_PREMIUM,
        )
    except Exception as exc:
        return jsonify({"error": "upstream", "detail": str(exc)}), 502

    result = {"model": model, "text": text}
    if payload.get("admin") and system:
        result["prompt"] = f"SYSTEM:\n{system}\n\nUSER:\n{user}"
    return jsonify(result)


@app.get("/api/v1/app-config")
def api_app_config():
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    try:
        messenger_on = messenger_plugin.is_enabled()
    except Exception:
        messenger_on = True
    return jsonify(
        {
            "premium_price_rub": db.get_setting("premium_price_rub", "199"),
            "premium_days": yookassa.premium_days(),
            "premium_payments_enabled": yookassa.is_configured(),
            "messenger_enabled": messenger_on,
            "models": {"free": MODEL_FLASH, "premium": MODEL_PREMIUM},
            "psych_dialogue_extra": _psych_int_setting("psych_dialogue_extra", 5),
            "psych_work_questions": _psych_int_setting("psych_work_questions", 5),
        }
    )


@app.post("/api/v1/premium/create-payment")
def api_premium_create_payment():
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    if not yookassa.is_configured():
        return jsonify({"error": "not_configured"}), 503
    payload = request.get_json(silent=True) or {}
    device_id = str(payload.get("device_id") or "").strip()
    return_url = str(payload.get("return_url") or "").strip() or None
    if not device_id:
        return jsonify({"error": "device_id_required"}), 400
    try:
        created = yookassa.create_payment(device_id=device_id, return_url=return_url)
    except ValueError as exc:
        return jsonify({"error": "bad_request", "detail": str(exc)}), 400
    except Exception as exc:
        app.logger.exception("create-payment failed: %s", exc)
        return jsonify({"error": "upstream", "detail": str(exc)}), 502
    return jsonify(
        {
            "payment_id": created["payment_id"],
            "confirmation_url": created["confirmation_url"],
            "status": created["status"],
            "amount": created["amount"],
            "currency": created.get("currency") or "RUB",
            "premium_days": yookassa.premium_days(),
        }
    )


@app.get("/api/v1/premium/status")
def api_premium_status_get():
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    device_id = (request.args.get("device_id") or "").strip()
    if not device_id:
        return jsonify({"error": "device_id_required"}), 400
    status = db.premium_status_for_device(device_id)
    status["premium_days"] = yookassa.premium_days()
    return jsonify(status)


@app.post("/api/v1/premium/status")
def api_premium_status_post():
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    payload = request.get_json(silent=True) or {}
    device_id = str(payload.get("device_id") or "").strip()
    payment_id = str(payload.get("payment_id") or "").strip()
    if not device_id:
        return jsonify({"error": "device_id_required"}), 400
    # Optional: pull latest status from YooKassa if payment_id given (client poll after return).
    if payment_id and yookassa.is_configured():
        try:
            remote = yookassa.get_payment(payment_id)
            if str(remote.get("status") or "") == "succeeded":
                yookassa.apply_succeeded_payment(remote)
            else:
                meta = remote.get("metadata") or {}
                db.upsert_premium_payment(
                    payment_id=payment_id,
                    device_id=device_id or str(meta.get("device_id") or ""),
                    amount=str((remote.get("amount") or {}).get("value") or ""),
                    currency=str((remote.get("amount") or {}).get("currency") or "RUB"),
                    status=str(remote.get("status") or "pending"),
                    confirmation_url=str(
                        (remote.get("confirmation") or {}).get("confirmation_url") or ""
                    ),
                    raw_json=json.dumps(remote, ensure_ascii=False),
                )
        except Exception as exc:
            app.logger.warning("premium status refresh failed: %s", exc)
    status = db.premium_status_for_device(device_id)
    status["premium_days"] = yookassa.premium_days()
    if payment_id:
        pay = db.get_premium_payment(payment_id)
        status["payment_status"] = str((pay or {}).get("status") or "")
        status["payment_id"] = payment_id
    return jsonify(status)


@app.post("/api/v1/premium/webhook")
def api_premium_webhook():
    """YooKassa HTTP notifications. No app API token; authenticity via IP + GET payment."""
    forwarded = (request.headers.get("X-Forwarded-For") or "").strip()
    remote = forwarded.split(",")[0].strip() if forwarded else (request.remote_addr or "")
    ip_ok = yookassa.client_ip_allowed(remote)
    event = request.get_json(silent=True) or {}
    event_type = str(event.get("event") or "")
    obj = event.get("object") or {}
    payment_id = str(obj.get("id") or "").strip()

    if event_type in {"payment.succeeded", "payment.waiting_for_capture", "payment.canceled"}:
        if not payment_id:
            return jsonify({"ok": False, "error": "no_id"}), 400
        if not yookassa.is_configured():
            app.logger.error("webhook received but YooKassa not configured")
            return jsonify({"ok": False}), 503
        try:
            verified = yookassa.get_payment(payment_id)
        except Exception as exc:
            app.logger.exception("webhook verify failed: %s", exc)
            return jsonify({"ok": False}), 502
        if not ip_ok:
            # Still accept if API confirms the payment (behind proxy IP may differ).
            app.logger.warning("webhook IP not in allowlist: %s (verified via API)", remote)
        status = str(verified.get("status") or "")
        if status == "succeeded":
            try:
                yookassa.apply_succeeded_payment(verified)
            except Exception as exc:
                app.logger.exception("webhook apply failed: %s", exc)
                return jsonify({"ok": False}), 500
        else:
            meta = verified.get("metadata") or {}
            db.upsert_premium_payment(
                payment_id=payment_id,
                device_id=str(meta.get("device_id") or ""),
                amount=str((verified.get("amount") or {}).get("value") or ""),
                currency=str((verified.get("amount") or {}).get("currency") or "RUB"),
                status=status or event_type,
                confirmation_url="",
                raw_json=json.dumps(verified, ensure_ascii=False),
            )
    return jsonify({"ok": True})


@app.get("/premium/return")
def premium_return():
    """Browser return after YooKassa; deep-link back into the app when possible."""
    return render_template(
        "premium_return.html",
        domain=config.DOMAIN,
        deep_link="ru.na.steps12://premium/return",
    )


def _fmt_msk(value) -> str:
    if not value:
        return ""
    raw = str(value)
    try:
        dt = datetime.strptime(raw[:19], "%Y-%m-%d %H:%M:%S")
        dt = dt.replace(tzinfo=ZoneInfo("UTC")).astimezone(ZoneInfo("Europe/Moscow"))
        return dt.strftime("%d.%m.%Y %H:%M")
    except Exception:
        return raw


@app.route("/support", methods=["GET", "POST"])
@login_required
def support_admin():
    notice = ""
    warn = ""
    if request.method == "POST":
        action = (request.form.get("action") or "").strip()
        try:
            ticket_id = int(request.form.get("id") or 0)
        except (TypeError, ValueError):
            ticket_id = 0
        try:
            message_id = int(request.form.get("message_id") or 0)
        except (TypeError, ValueError):
            message_id = 0
        if action == "compose":
            user_id = (request.form.get("user_id") or "").strip()
            user_name = (request.form.get("user_name") or "").strip()
            body = (request.form.get("body") or "").strip()
            belonging = (request.form.get("belonging") or "screen").strip()
            kind = (request.form.get("kind") or "bug").strip()
            family = (request.form.get("family") or "").strip()
            screen = (request.form.get("screen") or "").strip()
            screen_route = (request.form.get("screen_route") or "").strip()
            admin_source = (request.form.get("admin_source") or "support").strip()
            if belonging == "family" and family:
                screen_route = f"family:{family}" if not family.startswith("family:") else family
                screen = db.SUPPORT_FAMILIES.get(family.replace("family:", ""), screen)
            elif belonging == "general":
                screen = "Общее"
                screen_route = "general"
            elif belonging == "report":
                screen = "Сообщить об ошибке"
                screen_route = "support/report"
            elif belonging == "idea_window":
                screen = "Предложить идею"
                screen_route = "support/idea"
            elif belonging == "cover":
                screen = "Обложка"
                screen_route = "cover"
            elif belonging == "life_idea":
                screen = "Идеи"
                screen_route = "life/idea"
            elif belonging == "life_note":
                screen = "Заметки"
                screen_route = "life/note"
            elif belonging == "life_calendar":
                screen = "Календарь"
                screen_route = "life/event"
            if not user_id:
                warn = "Укажите ID пользователя."
            elif not body:
                warn = "Введите текст сообщения."
            else:
                try:
                    ticket = db.create_support_ticket(
                        user_id,
                        user_name or "Пользователь",
                        screen,
                        screen_route or admin_source,
                        body,
                        belonging=belonging,
                        kind=kind,
                        admin_source=admin_source,
                        author="admin",
                    )
                    notice = "Сообщение отправлено пользователю."
                    return redirect(url_for("support_admin", id=int(ticket["id"]), saved=1))
                except ValueError:
                    warn = "Не удалось отправить."
        elif action == "report":
            body = (request.form.get("body") or "").strip()
            belonging = (request.form.get("belonging") or "screen").strip()
            kind = (request.form.get("kind") or "bug").strip()
            family = (request.form.get("family") or "").strip()
            screen = (request.form.get("screen") or "").strip()
            screen_route = (request.form.get("screen_route") or "").strip()
            admin_source = (request.form.get("admin_source") or "support").strip()
            if admin_source not in db.ADMIN_SOURCE_PAGES:
                admin_source = "support"
            if belonging == "family" and family:
                screen_route = f"family:{family}" if not family.startswith("family:") else family
                screen = db.SUPPORT_FAMILIES.get(family.replace("family:", ""), screen)
            elif belonging == "general":
                screen = "Общее"
                screen_route = "general"
            elif belonging == "report":
                screen = "Сообщить об ошибке"
                screen_route = "support/report"
            elif belonging == "idea_window":
                screen = "Предложить идею"
                screen_route = "support/idea"
            elif belonging == "cover":
                screen = "Обложка"
                screen_route = "cover"
            elif belonging == "life_idea":
                screen = "Идеи"
                screen_route = "life/idea"
            elif belonging == "life_note":
                screen = "Заметки"
                screen_route = "life/note"
            elif belonging == "life_calendar":
                screen = "Календарь"
                screen_route = "life/event"
            elif belonging == "screen":
                if not screen:
                    screen = db.ADMIN_SOURCE_PAGES.get(admin_source, "Админка")
                if not screen_route:
                    screen_route = f"admin/{admin_source}"
            if not body:
                warn = "Опишите ошибку."
            else:
                try:
                    admin_name = str(session.get("admin") or config.ADMIN_USERNAME or "admin")
                    ticket = db.create_support_ticket(
                        f"admin:{admin_name}",
                        "Администратор",
                        screen,
                        screen_route or f"admin/{admin_source}",
                        body,
                        belonging=belonging,
                        kind=kind,
                        admin_source=admin_source,
                        author="user",
                    )
                    notice = "Ошибка записана. Можно обработать в списке слева."
                    return redirect(url_for("support_admin", id=int(ticket["id"]), saved=1))
                except ValueError:
                    warn = "Не удалось сохранить ошибку."
        elif action == "edit_message":
            if message_id <= 0:
                warn = "Не выбрано сообщение."
            else:
                body = (request.form.get("body") or "").strip()
                if not body:
                    warn = "Введите текст сообщения."
                else:
                    try:
                        ticket = db.update_support_message(message_id, body)
                    except ValueError:
                        ticket = None
                    if ticket:
                        notice = "Сообщение изменено."
                        return redirect(url_for("support_admin", id=int(ticket["id"]), saved=1))
                    warn = "Сообщение не найдено."
        elif action == "delete_message":
            if message_id <= 0:
                warn = "Не выбрано сообщение."
            else:
                ticket = db.delete_support_message(message_id)
                if ticket:
                    notice = "Сообщение удалено."
                    return redirect(url_for("support_admin", id=int(ticket["id"]), saved=1))
                warn = "Сообщение не найдено."
        elif action == "read_message":
            if message_id <= 0:
                warn = "Не выбрано сообщение."
            else:
                ticket = db.mark_support_message_admin_read(message_id)
                if ticket:
                    notice = "Сообщение прочитано."
                    return redirect(url_for("support_admin", id=int(ticket["id"]), saved=1))
                warn = "Сообщение не найдено."
        elif action == "delete_ticket":
            if ticket_id <= 0:
                warn = "Не выбрано обращение."
            elif db.delete_support_ticket(ticket_id):
                notice = "Обращение удалено."
                return redirect(url_for("support_admin", saved=1))
            else:
                warn = "Обращение не найдено."
        elif ticket_id <= 0:
            warn = "Не выбрано сообщение."
        elif action == "read":
            db.mark_support_read(ticket_id, admin=True)
            notice = "Отмечено прочитанным."
            return redirect(url_for("support_admin", id=ticket_id, saved=1))
        elif action == "complete":
            ticket = db.complete_support_ticket(ticket_id)
            if ticket:
                notice = "Задача завершена. Статус: Обработано."
                return redirect(url_for("support_admin", id=ticket_id, saved=1))
            warn = "Не удалось завершить задачу."
        elif action == "status":
            status = (request.form.get("status") or "").strip()
            ticket = db.set_support_status(ticket_id, status)
            if ticket:
                notice = f"Статус: {ticket.get('status_label') or status}. Пользователь уведомлён."
                return redirect(url_for("support_admin", id=ticket_id, saved=1))
            warn = "Не удалось изменить статус."
        elif action == "reply":
            body = (request.form.get("body") or "").strip()
            complete = (request.form.get("complete") or "").strip() in {"1", "on", "true"}
            if not body:
                warn = "Введите ответ."
            else:
                ticket = db.add_support_message(ticket_id, "admin", body)
                if ticket and complete:
                    ticket = db.complete_support_ticket(ticket_id) or ticket
                if ticket:
                    notice = "Ответ отправлен."
                    return redirect(url_for("support_admin", id=ticket_id, saved=1))
                warn = "Сообщение не найдено."
    if request.args.get("saved"):
        notice = notice or "Сохранено."
    try:
        current_id = int(request.args.get("id") or 0)
    except (TypeError, ValueError):
        current_id = 0
    current = db.get_support_ticket(current_id) if current_id else None
    items = db.list_support_tickets()
    unread = db.support_unread_count(admin=True)
    recent_users = []
    seen = set()
    for item in items:
        uid = item.get("user_id") or ""
        if not uid or uid in seen:
            continue
        seen.add(uid)
        recent_users.append(
            {
                "user_id": uid,
                "user_name": item.get("user_name") or "Без имени",
            }
        )
        if len(recent_users) >= 40:
            break
    compose = request.args.get("compose") == "1"
    report = request.args.get("report") == "1"
    if not current and not compose and not report:
        compose = True
    admin_source = (request.args.get("from") or "support").strip()
    if admin_source not in db.ADMIN_SOURCE_PAGES:
        admin_source = "support"
    report_screen = db.ADMIN_SOURCE_PAGES.get(admin_source, "Админка")
    report_route = f"admin/{admin_source}"
    return render_template(
        "support.html",
        domain=config.DOMAIN,
        notice=notice,
        warn=warn,
        items=items,
        current=current,
        unread=unread,
        statuses=db.SUPPORT_STATUSES,
        status_labels=db.SUPPORT_STATUS_LABELS,
        belongings=db.SUPPORT_BELONGINGS,
        belonging_labels=db.SUPPORT_BELONGING_LABELS,
        kinds=db.SUPPORT_KINDS,
        kind_labels=db.SUPPORT_KIND_LABELS,
        families=db.SUPPORT_FAMILIES,
        admin_sources=db.ADMIN_SOURCE_PAGES,
        admin_source=admin_source,
        recent_users=recent_users,
        compose=compose,
        report=report,
        report_screen=report_screen,
        report_route=report_route,
        fmt_time=_fmt_msk,
    )


@app.post("/api/v1/support")
def api_support_create():
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    payload = request.get_json(silent=True) or {}
    try:
        ticket = db.create_support_ticket(
            str(payload.get("user_id") or ""),
            str(payload.get("user_name") or ""),
            str(payload.get("screen") or ""),
            str(payload.get("screen_route") or ""),
            str(payload.get("body") or ""),
            belonging=str(payload.get("belonging") or "screen"),
            kind=str(payload.get("kind") or "bug"),
            admin_source=str(payload.get("admin_source") or ""),
            author=str(payload.get("author") or "user"),
        )
    except ValueError:
        return jsonify({"error": "required"}), 400
    return jsonify({"ok": True, "ticket": ticket})


@app.get("/api/v1/support")
def api_support_list():
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    user_id = (request.args.get("user_id") or "").strip()
    if not user_id:
        return jsonify({"error": "user_id"}), 400
    tickets = db.list_support_tickets(user_id)
    detailed = []
    for item in tickets:
        full = db.get_support_ticket(int(item["id"]))
        if full:
            detailed.append(full)
    return jsonify(
        {
            "ok": True,
            "tickets": detailed,
            "topic_counts": db.support_topic_counts(),
        }
    )


@app.get("/api/v1/support/inbox")
def api_support_inbox():
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    if not _admin_code_ok(str(request.args.get("code") or "")):
        return jsonify({"error": "admin_code"}), 403
    tickets = db.list_support_tickets()
    return jsonify(
        {
            "ok": True,
            "tickets": tickets,
            "unread": db.support_unread_count(admin=True),
            "topic_counts": db.support_topic_counts(),
        }
    )


@app.get("/api/v1/support/unread")
def api_support_unread():
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    code = str(request.args.get("code") or "")
    user_id = (request.args.get("user_id") or "").strip()
    if _admin_code_ok(code):
        return jsonify({"ok": True, "count": db.support_unread_count(admin=True), "admin": True})
    if not user_id:
        return jsonify({"error": "user_id"}), 400
    return jsonify({"ok": True, "count": db.support_unread_count(user_id=user_id), "admin": False})


@app.get("/api/v1/support/<int:ticket_id>")
def api_support_one(ticket_id: int):
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    ticket = db.get_support_ticket(ticket_id)
    if not ticket:
        return jsonify({"error": "not_found"}), 404
    code = str(request.args.get("code") or "")
    user_id = (request.args.get("user_id") or "").strip()
    if _admin_code_ok(code):
        return jsonify({"ok": True, "ticket": ticket})
    if user_id and user_id == ticket["user_id"]:
        return jsonify({"ok": True, "ticket": ticket})
    return jsonify({"error": "forbidden"}), 403


@app.post("/api/v1/support/<int:ticket_id>/reply")
def api_support_reply(ticket_id: int):
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    payload = request.get_json(silent=True) or {}
    body = str(payload.get("body") or "")
    code = str(payload.get("code") or "")
    user_id = str(payload.get("user_id") or "").strip()
    complete = bool(payload.get("complete"))
    try:
        if _admin_code_ok(code):
            ticket = db.add_support_message(ticket_id, "admin", body)
            if ticket and complete:
                ticket = db.complete_support_ticket(ticket_id) or ticket
        else:
            existing = db.get_support_ticket(ticket_id)
            if not existing or existing["user_id"] != user_id:
                return jsonify({"error": "forbidden"}), 403
            ticket = db.add_support_message(ticket_id, "user", body)
    except ValueError:
        return jsonify({"error": "required"}), 400
    if not ticket:
        return jsonify({"error": "not_found"}), 404
    return jsonify({"ok": True, "ticket": ticket})


@app.post("/api/v1/support/<int:ticket_id>/read")
def api_support_read(ticket_id: int):
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    payload = request.get_json(silent=True) or {}
    code = str(payload.get("code") or "")
    user_id = str(payload.get("user_id") or "").strip()
    if _admin_code_ok(code):
        ticket = db.mark_support_read(ticket_id, admin=True)
    else:
        existing = db.get_support_ticket(ticket_id)
        if not existing or existing["user_id"] != user_id:
            return jsonify({"error": "forbidden"}), 403
        ticket = db.mark_support_read(ticket_id, admin=False)
    if not ticket:
        return jsonify({"error": "not_found"}), 404
    return jsonify({"ok": True, "ticket": ticket})


@app.post("/api/v1/support/message/<int:message_id>/read")
def api_support_message_read(message_id: int):
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    payload = request.get_json(silent=True) or {}
    if not _admin_code_ok(str(payload.get("code") or "")):
        return jsonify({"error": "admin_code"}), 403
    ticket = db.mark_support_message_admin_read(message_id)
    if not ticket:
        return jsonify({"error": "not_found"}), 404
    return jsonify({"ok": True, "ticket": ticket})


@app.post("/api/v1/support/<int:ticket_id>/complete")
def api_support_complete(ticket_id: int):
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    payload = request.get_json(silent=True) or {}
    if not _admin_code_ok(str(payload.get("code") or "")):
        return jsonify({"error": "admin_code"}), 403
    ticket = db.complete_support_ticket(ticket_id)
    if not ticket:
        return jsonify({"error": "not_found"}), 404
    return jsonify({"ok": True, "ticket": ticket})


@app.post("/api/v1/support/<int:ticket_id>/status")
def api_support_status(ticket_id: int):
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    payload = request.get_json(silent=True) or {}
    if not _admin_code_ok(str(payload.get("code") or "")):
        return jsonify({"error": "admin_code"}), 403
    status = str(payload.get("status") or "")
    try:
        ticket = db.set_support_status(ticket_id, status)
    except ValueError:
        return jsonify({"error": "required"}), 400
    if not ticket:
        return jsonify({"error": "not_found"}), 404
    return jsonify({"ok": True, "ticket": ticket})


@app.post("/api/v1/support/message/<int:message_id>/edit")
def api_support_edit_message(message_id: int):
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    payload = request.get_json(silent=True) or {}
    if not _admin_code_ok(str(payload.get("code") or "")):
        return jsonify({"error": "admin_code"}), 403
    body = str(payload.get("body") or "").strip()
    if not body:
        return jsonify({"error": "required"}), 400
    try:
        ticket = db.update_support_message(message_id, body)
    except ValueError:
        return jsonify({"error": "required"}), 400
    if not ticket:
        return jsonify({"error": "not_found"}), 404
    return jsonify({"ok": True, "ticket": ticket})


@app.post("/api/v1/support/message/<int:message_id>/delete")
def api_support_delete_message(message_id: int):
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    payload = request.get_json(silent=True) or {}
    if not _admin_code_ok(str(payload.get("code") or "")):
        return jsonify({"error": "admin_code"}), 403
    ticket = db.delete_support_message(message_id)
    if not ticket:
        return jsonify({"error": "not_found"}), 404
    return jsonify({"ok": True, "ticket": ticket})


@app.post("/api/v1/support/<int:ticket_id>/delete")
def api_support_delete_ticket(ticket_id: int):
    if not _api_ok():
        return jsonify({"error": "unauthorized"}), 401
    payload = request.get_json(silent=True) or {}
    code = str(payload.get("code") or "")
    user_id = str(payload.get("user_id") or "").strip()
    existing = db.get_support_ticket(ticket_id)
    if not existing:
        return jsonify({"error": "not_found"}), 404
    if _admin_code_ok(code):
        ok = db.delete_support_ticket(ticket_id)
    elif user_id and user_id == existing["user_id"]:
        ok = db.delete_support_ticket(ticket_id)
    else:
        return jsonify({"error": "forbidden"}), 403
    if not ok:
        return jsonify({"error": "not_found"}), 404
    return jsonify({"ok": True})


def _today_ru() -> str:
    try:
        now = datetime.now(ZoneInfo("Europe/Moscow"))
    except Exception:
        now = datetime.now()
    return now.strftime("%d.%m.%Y")


def _build_self_analysis_prompt(
    title: str,
    answer_blocks: list[str],
    questionnaire: str = "",
    personality: str = "",
    name: str = "",
    collect_personality: bool = False,
    language: str = "ru",
) -> str:
    lang = (language or "ru").strip().lower().replace("_", "-")
    is_ru = lang in {"ru", "ru-ru", "russian"} or lang.startswith("ru-")
    if is_ru:
        h = {
            "context": "КОНТЕКСТ",
            "date": "Дата анализа",
            "title": "Заголовок самоанализа",
            "category": "Категория: самоанализ",
            "name": "Имя",
            "questionnaire": "АНКЕТА ПОЛЬЗОВАТЕЛЯ",
            "personality": "МОЯ ЛИЧНОСТЬ (портрет)",
            "answers": "ОТВЕТЫ ДЛЯ АНАЛИЗА",
            "criteria": "КРИТЕРИИ ОЦЕНКИ",
            "c1": "Раскрытие: глубина проработки темы, уровень откровенности, личная вовлеченность",
            "c2": "Честность: отсутствие самообмана, признание своих ролей, искренность перед собой",
            "c3": "Осознанность: понимание причинно-следственных связей, осознание моделей поведения, рефлексия эмоций",
            "c4": "Конструктивность: готовность к изменениям, практические инсайты, планы действий",
            "report": "АНАЛИТИЧЕСКИЙ ОТЧЕТ",
            "r1": "Сводная оценка по всем критериям",
            "r2": "Сильные стороны ответа",
            "r3": "Слепые зоны — что упущено или недостаточно раскрыто",
            "r4": "Рекомендации по углублению работы",
            "r5": "Вопросы для рефлексии (3-5 вопросов, каждый с новой строки, с номером)",
            "structure": "СТРУКТУРА ОТВЕТА (обязательно)",
            "structure_note": (
                "Каждый блок начинай с отдельной строки-заголовка в формате **Название** и пустой строкой перед ним.\n"
                "Используй ровно эти заголовки:\n"
                "**Сводная оценка**, **Сильные стороны**, **Слепые зоны**, **Рекомендации**, **Вопросы для рефлексии**.\n"
                "Если есть практические рекомендации — отдельный блок **Практические рекомендации**.\n"
                "Не сливай блоки в один абзац."
            ),
            "practical": "ПРАКТИЧЕСКИЕ РЕКОМЕНДАЦИИ",
            "p1": "На 24 часа: конкретная задача",
            "p2": "На неделю: среднесрочная задача",
            "p3": "Общий совет по развитию",
            "volume": "ОБЪЁМ: Ответ стандартного объёма — 600–1000 слов. Умеренная детализация, баланс между полнотой и лаконичностью.",
            "format": (
                "ФОРМАТ: Дай поддерживающий, сочувствующий отклик без критики и осуждения, "
                "помогая увидеть ошибки и зоны ответственности, не усиливая чувство стыда и безнадёжности."
            ),
            "reason_lang": "русском",
        }
    else:
        label = psych._language_label(lang)
        h = {
            "context": "CONTEXT",
            "date": "Analysis date",
            "title": "Self-analysis title",
            "category": "Category: self-analysis",
            "name": "Name",
            "questionnaire": "USER QUESTIONNAIRE",
            "personality": "MY PERSONALITY (portrait)",
            "answers": "ANSWERS TO ANALYZE",
            "criteria": "EVALUATION CRITERIA",
            "c1": "Depth: how thoroughly the topic is explored, honesty, personal engagement",
            "c2": "Honesty: no self-deception, owning one's part, sincerity",
            "c3": "Awareness: cause-and-effect, behavior patterns, emotional reflection",
            "c4": "Constructiveness: readiness to change, practical insights, action plans",
            "report": "ANALYTICAL REPORT",
            "r1": "Overall assessment across all criteria",
            "r2": "Strengths of the answers",
            "r3": "Blind spots — what was missed or under-explored",
            "r4": "Recommendations for deeper work",
            "r5": "Reflection questions (3–5 questions, each on a new numbered line)",
            "structure": "RESPONSE STRUCTURE (required)",
            "structure_note": (
                "Start each block with its own heading line in the form **Title** and a blank line before it.\n"
                "Use exactly these headings (translated into "
                f"{label}"
                "):\n"
                "**Overall assessment**, **Strengths**, **Blind spots**, **Recommendations**, **Reflection questions**.\n"
                "If practical tips are needed — a separate block **Practical recommendations**.\n"
                "Do not merge blocks into one paragraph. Do not keep Russian headings."
            ),
            "practical": "PRACTICAL RECOMMENDATIONS",
            "p1": "Next 24 hours: one concrete task",
            "p2": "This week: a medium-term task",
            "p3": "General growth advice",
            "volume": "LENGTH: Standard response — about 600–1000 words. Balanced detail.",
            "format": (
                "TONE: Supportive and compassionate, without criticism or shame, "
                "helping notice responsibility areas gently."
            ),
            "reason_lang": label,
        }

    prompt = f"{h['context']}:\n"
    prompt += f"{h['date']}: {_today_ru()}\n"
    if title:
        prompt += f"{h['title']}: {title}\n"
    prompt += f"{h['category']}\n"
    if name:
        prompt += f"{h['name']}: {name}\n"
    if questionnaire:
        prompt += f"\n{h['questionnaire']}:\n"
        prompt += questionnaire
        prompt += "\n"
    if personality:
        prompt += f"\n{h['personality']}:\n"
        prompt += personality
        prompt += "\n"
    prompt += "\n"
    prompt += f"{h['answers']}:\n"
    prompt += "\n\n".join(answer_blocks)
    prompt += "\n\n"
    prompt += (
        f"{h['criteria']}:\n"
        f"1. {h['c1']}\n"
        f"2. {h['c2']}\n"
        f"3. {h['c3']}\n"
        f"4. {h['c4']}\n\n"
        f"{h['report']}:\n"
        f"1. {h['r1']}\n"
        f"2. {h['r2']}\n"
        f"3. {h['r3']}\n"
        f"4. {h['r4']}\n"
        f"5. {h['r5']}\n\n"
        f"{h['structure']}:\n"
        f"{h['structure_note']}\n\n"
        f"{h['practical']}:\n"
        f"- {h['p1']}\n"
        f"- {h['p2']}\n"
        f"- {h['p3']}\n\n"
        f"{h['volume']}\n\n"
        f"{h['format']}\n\n"
    )
    if collect_personality:
        if is_ru:
            prompt += (
                "После основного текста и ОБЯЗАТЕЛЬНО ПЕРЕД блоком SPIRITUAL_DELTA выведи блок "
                "«Моя личность» строго между маркерами ---МОЯ_ЛИЧНОСТЬ--- и ---КОНЕЦ_МОЯ_ЛИЧНОСТЬ---.\n"
                "Внутри — полный обновлённый портрет: черты, ценности, реакции, ресурсы и зоны роста. "
                "Сохрани существенное из текущего портрета выше, если он не пустой, и дополни новым из этого самоанализа.\n"
                "Маркеры обязательны. В блоке только портрет, без оценки и рекомендаций.\n\n"
            )
        else:
            prompt += (
                "After the main text and BEFORE the SPIRITUAL_DELTA block, output a "
                "«My personality» block strictly between markers ---МОЯ_ЛИЧНОСТЬ--- and ---КОНЕЦ_МОЯ_ЛИЧНОСТЬ---.\n"
                "Inside — a full updated portrait. Keep what still fits from the portrait above and add new insights.\n"
                "Markers are required. Portrait only, no scoring or recommendations.\n\n"
            )
    prompt += (
        "ОБЯЗАТЕЛЬНО after the personality block (if any), otherwise after the whole analysis, "
        "output one SPIRITUAL_DELTA block in this exact format — last block of the answer:\n"
        "### SPIRITUAL_DELTA ###\n"
        "score: <integer from -5 to 10>\n"
        "quality: <high|ok|low|fictitious>\n"
        f"reason: <short reason in {h['reason_lang']}>\n"
        "### END_SPIRITUAL_DELTA ###\n"
        "Score rules: honesty, awareness, program alignment and depth raise the score; "
        "formality, avoidance, laziness lower it. "
        "quality=fictitious — obvious fluff/off-topic; low — shallow; ok — normal; high — deep honest work. "
        "Do not duplicate or comment this block in the main text.\n"
    )
    return prompt


MODEL_FLASH = "deepseek-v4-flash"
MODEL_PREMIUM = "deepseek-v4-pro"


def _payload_premium(payload: dict) -> bool:
    if bool(payload.get("premium") or payload.get("pro") or payload.get("pro_active")):
        return True
    profile = payload.get("profile")
    if isinstance(profile, dict) and bool(
        profile.get("pro_active") or profile.get("premium")
    ):
        return True
    return False


def _model_for_user(premium: bool) -> str:
    return MODEL_PREMIUM if premium else MODEL_FLASH


def _deepseek_chat(
    api_key: str,
    model: str,
    messages: list[dict[str, str]],
    max_tokens: int = 900,
    thinking: bool = False,
    timeout: int = 90,
) -> str:
    def once(use_thinking: bool) -> str:
        payload: dict = {
            "model": model,
            "messages": messages,
            "max_tokens": max_tokens,
            "thinking": {"type": "enabled" if use_thinking else "disabled"},
        }
        if use_thinking:
            payload["reasoning_effort"] = "high"
        else:
            payload["temperature"] = 0.4
        body = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            f"{config.DEEPSEEK_BASE_URL}/chat/completions",
            data=body,
            headers={
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                data = json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")[:400]
            raise RuntimeError(f"DeepSeek HTTP {exc.code}: {detail}") from exc
        choices = data.get("choices") or []
        if not choices:
            return ""
        message = (choices[0] or {}).get("message") or {}
        return str(message.get("content") or "").strip()

    try:
        text = once(thinking)
    except Exception:
        if not thinking:
            raise
        text = ""
    if not text and thinking:
        text = once(False)
    if not text:
        raise RuntimeError("Пустой текст модели")
    return text


if __name__ == "__main__":
    app.config["SESSION_COOKIE_SECURE"] = False
    app.run(host="127.0.0.1", port=8000, debug=True)
