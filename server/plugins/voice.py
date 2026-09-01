# Voice plugin — Vapi settings and public config for the app.
from __future__ import annotations

import json
import re
import urllib.error
import urllib.request

from flask import jsonify, redirect, render_template, request, url_for

import config
import db

VOICES = (
    ("azure", "ru-RU-SvetlanaNeural", "Светлана · Azure, русский"),
    ("azure", "ru-RU-DmitryNeural", "Дмитрий · Azure, русский"),
    ("azure", "ru-RU-DariyaNeural", "Дарья · Azure, русский"),
    ("vapi", "Elliot", "Elliot · Vapi"),
    ("vapi", "Savannah", "Savannah · Vapi"),
    ("vapi", "Emma", "Emma · Vapi"),
    ("vapi", "Clara", "Clara · Vapi"),
    ("vapi", "Nico", "Nico · Vapi"),
    ("vapi", "Kai", "Kai · Vapi"),
    ("vapi", "Layla", "Layla · Vapi"),
    ("vapi", "Sid", "Sid · Vapi"),
    ("vapi", "Naina", "Naina · Vapi"),
    ("vapi", "Rohan", "Rohan · Vapi"),
    ("vapi", "Sagar", "Sagar · Vapi"),
    ("vapi", "Godfrey", "Godfrey · Vapi"),
    ("vapi", "Neil", "Neil · Vapi"),
)

DEFAULT_PROVIDER = "azure"
DEFAULT_VOICE = "ru-RU-SvetlanaNeural"
DEFAULT_SPEED = "1.0"
VAPI_API = "https://api.vapi.ai"


def voice_key(provider: str, voice_id: str) -> str:
    return f"{provider}:{voice_id}"


def parse_voice_key(raw: str) -> tuple[str, str]:
    text = (raw or "").strip()
    if ":" in text:
        provider, voice_id = text.split(":", 1)
        provider, voice_id = provider.strip(), voice_id.strip()
        if any(item[0] == provider and item[1] == voice_id for item in VOICES):
            return provider, voice_id
    for provider, voice_id, _label in VOICES:
        if voice_id == text:
            return provider, voice_id
    return DEFAULT_PROVIDER, DEFAULT_VOICE


def mask_key(value: str) -> str:
    text = (value or "").strip()
    if not text:
        return ""
    if len(text) <= 8:
        return "•" * len(text)
    return f"{text[:4]}…{text[-4:]}"


def normalize_assistant_id(raw: str) -> tuple[str, str]:
    text = (raw or "").strip()
    if not text:
        return "", ""
    if text.lower() in {"admin", "administrator", "root", "логин", "admin_id"}:
        return "", (
            "В Assistant ID не пишите «admin». Это не логин админки, а идентификатор "
            "ассистента из Vapi. Поле можно оставить пустым."
        )
    compact = text.replace(" ", "")
    if re.fullmatch(r"[0-9a-fA-F-]{20,80}", compact):
        return compact, ""
    if len(compact) < 16:
        return "", (
            "Assistant ID слишком короткий. В dashboard.vapi.ai откройте Assistants, "
            "выберите ассистента и скопируйте id (длинная строка-UUID). "
            "Если ассистента нет — оставьте поле пустым."
        )
    return compact, ""


def clamp_speed(raw: str) -> str:
    try:
        value = float((raw or "").replace(",", ".").strip() or DEFAULT_SPEED)
    except ValueError:
        value = 1.0
    value = max(0.5, min(1.8, round(value, 2)))
    return f"{value:.2f}".rstrip("0").rstrip(".") or "1"


def public_config() -> dict:
    provider = db.get_setting("vapi_voice_provider", DEFAULT_PROVIDER) or DEFAULT_PROVIDER
    voice_id = db.get_setting("vapi_voice_id", DEFAULT_VOICE) or DEFAULT_VOICE
    if not any(item[0] == provider and item[1] == voice_id for item in VOICES):
        provider, voice_id = DEFAULT_PROVIDER, DEFAULT_VOICE
    try:
        speed = float(db.get_setting("vapi_speed", DEFAULT_SPEED) or DEFAULT_SPEED)
    except ValueError:
        speed = 1.0
    return {
        "ok": True,
        "public_key": db.get_setting("vapi_public_key", ""),
        "assistant_id": db.get_setting("vapi_assistant_id", ""),
        "voice_provider": provider,
        "voice_id": voice_id,
        "speed": speed,
        "voices": [
            {"provider": p, "id": vid, "label": label}
            for p, vid, label in VOICES
        ],
    }


def template_vars() -> dict:
    cfg = public_config()
    return {
        "voice_speed": cfg["speed"],
        "voice_id": cfg["voice_id"],
        "voice_provider": cfg["voice_provider"],
    }


def _vapi_headers(private_key: str) -> dict[str, str]:
    return {
        "Authorization": f"Bearer {private_key}",
        "Accept": "application/json",
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/131.0.0.0 Safari/537.36"
        ),
        "Accept-Language": "en-US,en;q=0.9",
        "Origin": "https://dashboard.vapi.ai",
        "Referer": "https://dashboard.vapi.ai/",
    }


def _cloudflare_block(code: int, body: str) -> bool:
    text = (body or "").lower()
    return (
        code in {403, 429}
        and (
            "cloudflare" in text
            or "error 1010" in text
            or "error-1010" in text
            or "access denied" in text
            or "browser's si" in text
        )
    )


def _test_vapi_urllib(private_key: str) -> tuple[int, str]:
    req = urllib.request.Request(
        f"{VAPI_API}/assistant",
        headers=_vapi_headers(private_key),
        method="GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            return resp.status, body
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        return exc.code, body
    except Exception as exc:
        return 0, str(exc)


def _test_vapi_curl(private_key: str) -> tuple[int, str] | None:
    import shutil
    import subprocess

    curl = shutil.which("curl")
    if not curl:
        return None
    try:
        completed = subprocess.run(
            [
                curl,
                "-sS",
                "-o",
                "-",
                "-w",
                "\nHTTPSTATUS:%{http_code}",
                "-A",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                "-H",
                f"Authorization: Bearer {private_key}",
                "-H",
                "Accept: application/json",
                f"{VAPI_API}/assistant",
            ],
            capture_output=True,
            text=True,
            timeout=20,
        )
    except Exception:
        return None
    raw = (completed.stdout or "") + (completed.stderr or "")
    if "HTTPSTATUS:" not in raw:
        return None
    body, _, status = raw.rpartition("HTTPSTATUS:")
    try:
        code = int(status.strip()[:3])
    except ValueError:
        return None
    return code, body.strip()


def _test_vapi(private_key: str) -> str:
    code, body = _test_vapi_urllib(private_key)
    if _cloudflare_block(code, body):
        via_curl = _test_vapi_curl(private_key)
        if via_curl:
            code, body = via_curl
    if _cloudflare_block(code, body):
        return (
            "Vapi (Cloudflare) блокирует проверку с этого хостинга — это не про ключ. "
            "Сохраните Public Key и голос, проверка кнопкой необязательна. "
            "Живой звонок проверится уже в приложении."
        )
    if code in (401, 403):
        return "Private Key не принят. Вставьте ключ из dashboard.vapi.ai → API Keys → Private Key."
    if code >= 400:
        return f"Vapi HTTP {code}: {(body or '')[:240]}"
    try:
        json.loads(body or "[]")
    except Exception:
        return "Ответ Vapi не похож на JSON — попробуйте позже."
    return ""


def register(app, login_required, api_ok) -> None:
    @app.context_processor
    def inject_voice():
        try:
            return template_vars()
        except Exception:
            return {
                "voice_speed": 1.0,
                "voice_id": DEFAULT_VOICE,
                "voice_provider": DEFAULT_PROVIDER,
            }

    @app.route("/voice", methods=["GET", "POST"])
    @login_required
    def voice_settings():
        notice = ""
        warn = ""
        public_key = db.get_setting("vapi_public_key", "")
        private_key = db.get_setting("vapi_private_key", "")
        assistant_id = db.get_setting("vapi_assistant_id", "")
        _ok_id, _bad_id = normalize_assistant_id(assistant_id)
        if _bad_id:
            assistant_id = ""
        provider = db.get_setting("vapi_voice_provider", DEFAULT_PROVIDER) or DEFAULT_PROVIDER
        voice_id = db.get_setting("vapi_voice_id", DEFAULT_VOICE) or DEFAULT_VOICE
        speed = db.get_setting("vapi_speed", DEFAULT_SPEED) or DEFAULT_SPEED

        if request.method == "POST":
            new_public = (
                request.form.get("vapi_public_key") or request.form.get("public_key") or ""
            ).strip()
            new_private = (
                request.form.get("vapi_private_key") or request.form.get("private_key") or ""
            ).strip()
            new_assistant, assistant_warn = normalize_assistant_id(
                request.form.get("assistant_id") or ""
            )
            provider, voice_id = parse_voice_key(request.form.get("voice") or "")
            speed = clamp_speed(request.form.get("speed") or DEFAULT_SPEED)
            saved_parts = []
            if new_public:
                db.set_setting("vapi_public_key", new_public)
                public_key = new_public
                saved_parts.append(f"Public Key ({mask_key(public_key)})")
            elif public_key:
                saved_parts.append(f"Public Key без изменений ({mask_key(public_key)})")
            if new_private:
                db.set_setting("vapi_private_key", new_private)
                private_key = new_private
                saved_parts.append("Private Key")
            elif private_key:
                saved_parts.append("Private Key без изменений")
            if assistant_warn:
                warn = assistant_warn
                db.set_setting("vapi_assistant_id", "")
                assistant_id = ""
                saved_parts.append("Assistant ID очищен — это поле не для логина админки")
            else:
                db.set_setting("vapi_assistant_id", new_assistant)
                assistant_id = new_assistant
                if assistant_id:
                    saved_parts.append(f"Assistant ID ({mask_key(assistant_id)})")
                else:
                    saved_parts.append("Assistant ID пустой — приложение само создаст ассистента")
            db.set_setting("vapi_voice_provider", provider)
            db.set_setting("vapi_voice_id", voice_id)
            db.set_setting("vapi_speed", speed)
            saved_parts.append(f"голос {voice_id}, скорость {speed}")
            if request.form.get("action") != "test":
                notice = "Сохранено: " + "; ".join(saved_parts) + "."

            if request.form.get("action") == "test":
                key = db.get_setting("vapi_private_key", "")
                if not key:
                    warn = "Для проверки вставьте Private Key из dashboard.vapi.ai → API Keys."
                else:
                    error = _test_vapi(key)
                    if error:
                        warn = f"Проверка не прошла: {error}"
                    else:
                        notice = "Private Key принят, доступ к Vapi есть."

        return render_template(
            "voice.html",
            domain=config.DOMAIN,
            notice=notice,
            warn=warn,
            has_public=bool(public_key),
            has_private=bool(private_key),
            public_mask=mask_key(public_key),
            private_mask=mask_key(private_key) if private_key else "",
            assistant_id=assistant_id,
            voice=voice_key(provider, voice_id),
            speed=speed,
            voices=[(voice_key(p, vid), label) for p, vid, label in VOICES],
        )

    @app.get("/api/v1/voice/config")
    def api_voice_config():
        if not api_ok():
            return jsonify({"error": "unauthorized"}), 401
        return jsonify(public_config())
