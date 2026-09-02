from __future__ import annotations

import json
import re
from datetime import UTC, datetime, timedelta
from typing import Any

import roles

SYSTEM_PROMPT = roles.PSYCH_BASE

PERSONALITY_BLOCK_RE = re.compile(
    r"(?:#{3}|-{3})\s*МОЯ[_\s]*ЛИЧНОСТЬ\s*(?:#{3}|-{3})\s*(.*?)\s*(?:#{3}|-{3})\s*КОНЕЦ[_\s]*МОЯ[_\s]*ЛИЧНОСТЬ\s*(?:#{3}|-{3})",
    re.DOTALL | re.IGNORECASE,
)
SPEAKABLE_SPLIT = "===ОЗВУЧКА==="
JSON_FENCE_RE = re.compile(r"```(?:json)?\s*(.*?)\s*```", re.DOTALL | re.IGNORECASE)


def _as_dict(value: Any) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}


def _str(value: Any) -> str:
    return str(value or "").strip()


def _is_pro(profile: dict[str, Any]) -> bool:
    exp = _str(profile.get("pro_expiry"))
    if not exp:
        return bool(profile.get("pro_active"))
    try:
        dt = datetime.fromisoformat(exp.replace("Z", "+00:00"))
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=UTC)
        return dt > datetime.now(UTC)
    except Exception:
        return False


def _no_history(payload: dict[str, Any]) -> bool:
    return bool(payload.get("no_history"))


def _norm_variant(profile: dict[str, Any]) -> str:
    v = _str(profile.get("ai_response_variant")).lower()
    if v == "expanded" and not _is_pro(profile):
        return "compact"
    return "expanded" if v == "expanded" else "compact"


def _norm_style(profile: dict[str, Any]) -> str:
    s = _str(profile.get("ai_response_style")).lower()
    if s == "critical" and not _is_pro(profile):
        return "neutral"
    return "critical" if s == "critical" else "neutral"


def _norm_diff(profile: dict[str, Any]) -> str:
    v = _str(profile.get("work_question_difficulty")).lower()
    if v == "hard" and not _is_pro(profile):
        return "simple"
    return "hard" if v == "hard" else "simple"


def _norm_len(profile: dict[str, Any]) -> str:
    v = _str(profile.get("work_question_length")).lower()
    if v == "long" and not _is_pro(profile):
        return "short"
    return "long" if v == "long" else "short"


def render_language(lang: str | None) -> str:
    language = _str(lang) or "ru"
    code = language.lower().replace("_", "-")
    if code in {"ru", "ru-ru", "russian"} or code.startswith("ru-"):
        return (
            "\nОБЯЗАТЕЛЬНО: отвечай ПОЛНОСТЬЮ на русском языке. "
            "Не смешивай языки. Заголовки, списки и весь текст — только по-русски.\n"
        )
    label = _language_label(code)
    return (
        f"\nОБЯЗАТЕЛЬНО: отвечай ПОЛНОСТЬЮ на языке: {label}. "
        f"Код языка интерфейса: {code}.\n"
        "Не смешивай языки. Весь текст ответа, включая заголовки разделов, "
        "должен быть на этом языке. Не оставляй русские заголовки, если целевой язык не русский.\n"
    )


def _language_label(code: str) -> str:
    base = (code or "en").split("-", 1)[0].lower()
    names = {
        "en": "English",
        "uk": "Ukrainian",
        "be": "Belarusian",
        "de": "German",
        "fr": "French",
        "es": "Spanish",
        "it": "Italian",
        "pl": "Polish",
        "pt": "Portuguese",
        "tr": "Turkish",
        "ar": "Arabic",
        "zh": "Chinese",
        "ja": "Japanese",
        "ko": "Korean",
        "kk": "Kazakh",
        "uz": "Uzbek",
        "hy": "Armenian",
        "ka": "Georgian",
        "az": "Azerbaijani",
        "ro": "Romanian",
        "cs": "Czech",
        "sk": "Slovak",
        "bg": "Bulgarian",
        "sr": "Serbian",
        "hr": "Croatian",
        "nl": "Dutch",
        "sv": "Swedish",
        "fi": "Finnish",
        "no": "Norwegian",
        "da": "Danish",
        "he": "Hebrew",
        "hi": "Hindi",
        "vi": "Vietnamese",
        "th": "Thai",
        "id": "Indonesian",
        "ms": "Malay",
    }
    return names.get(base, code or "English")


def resolve_response_language(requested: str | None, sample_text: str = "") -> str:
    """Prefer Russian when the user content is clearly Cyrillic (typical self-analysis)."""
    req = (_str(requested) or "ru").strip()
    code = req.lower().replace("_", "-")
    if code in {"ru", "ru-ru", "russian"} or code.startswith("ru-"):
        return "ru"
    text = sample_text or ""
    cyr = sum(1 for ch in text if ("а" <= ch.lower() <= "я") or ch in "ёЁІіЇїЄєЎў")
    lat = sum(1 for ch in text if "a" <= ch.lower() <= "z")
    if cyr >= 24 and cyr >= lat:
        return "ru"
    return req or "ru"


def render_variant(profile: dict[str, Any]) -> str:
    if _norm_variant(profile) == "expanded":
        return "\nФОРМАТ ОТВЕТА: дай развёрнутый ответ с примерами и пояснениями, где уместно.\n"
    return "\nФОРМАТ ОТВЕТА: дай компактный ответ по существу, без лишних отступов.\n"


def render_style(profile: dict[str, Any]) -> str:
    if _norm_style(profile) == "critical":
        return (
            "\nСТИЛЬ ОТВЕТА: конструктивно критичный — указывай на слабые места, "
            "риски и зоны роста, сохраняя уважение к человеку.\n"
        )
    return "\nСТИЛЬ ОТВЕТА: нейтральный и поддерживающий.\n"


def render_difficulty(profile: dict[str, Any]) -> str:
    if _norm_diff(profile) == "hard":
        return (
            "\nСЛОЖНОСТЬ ВОПРОСОВ: посложнее — задавай более глубокие и точные вопросы, "
            "иногда «неудобные», но без грубости.\n"
        )
    return "\nСЛОЖНОСТЬ ВОПРОСОВ: простые — формулируй максимально понятные, бережные вопросы.\n"


def render_length(profile: dict[str, Any]) -> str:
    if _norm_len(profile) == "long":
        return "\nДЛИНА ВОПРОСОВ: подлиннее — 2–3 предложения в одном вопросе.\n"
    return "\nДЛИНА ВОПРОСОВ: короткие — 1 предложение в одном вопросе.\n"


def render_datetime(profile: dict[str, Any]) -> str:
    offset = profile.get("utc_offset_minutes")
    now = datetime.now(UTC)
    if offset is not None and str(offset) != "":
        try:
            now = now + timedelta(minutes=int(offset))
            suffix = "время пользователя"
        except Exception:
            suffix = "серверное время"
    else:
        suffix = "серверное время"
    return f"Текущая дата и время: {now.strftime('%d.%m.%Y %H:%M')} ({suffix})"


def render_profile(profile: dict[str, Any]) -> str:
    lines = [
        f"Имя: {_str(profile.get('name')) or 'не указано'}",
        f"Год рождения: {_str(profile.get('birth_year')) or 'не указан'}",
        f"Место нахождения: {_str(profile.get('location')) or 'не указано'}",
    ]
    program = _str(profile.get("recovery_program"))
    if program:
        lines.append(f"Программа: {program}")
    gender = _str(profile.get("gender"))
    if gender:
        lines.append(f"Пол: {gender}")
    addiction = _str(profile.get("addiction_type"))
    if addiction:
        lines.append(f"Основной вид зависимости: {addiction}")
    last_use = _str(profile.get("last_use_date"))
    if last_use:
        lines.append(f"Дата последнего употребления/срыва: {last_use}")
    reason = _str(profile.get("main_reason"))
    if reason:
        lines.append(f"Основная причина: {reason}")
    motivation = _str(profile.get("motivation_level"))
    if motivation:
        lines.append(f"Мотивация: {motivation}")
    problems = _str(profile.get("problems"))
    if problems:
        lines.append(f"Обозначенные проблемы: {problems}")
    goals = _str(profile.get("goals"))
    if goals:
        lines.append(goals)
    lines.append(f"Описание о себе: {_str(profile.get('about_me')) or 'не указано'}")
    lines.append(render_datetime(profile))
    return "\n".join(lines)


def render_personality(profile: dict[str, Any]) -> str:
    use = bool(profile.get("my_personality_use_enabled")) or bool(
        profile.get("my_personality_collect_enabled")
    )
    personality = _str(profile.get("my_personality"))
    if not use and not personality:
        return ""
    if not use:
        return ""
    body = personality or "портрет пока не сформирован"
    return f"\nМОЯ ЛИЧНОСТЬ (портрет личности пользователя):\n{body}\n"


def system_for(kind: str) -> str:
    return roles.system_for_psych(kind)


def render_qa(
    answers: list[Any] | None,
    *,
    empty: str,
    header: str = "ДИАЛОГ (вопросы и ответы с начала):",
) -> str:
    pairs: list[tuple[str, str]] = []
    for item in answers or []:
        if isinstance(item, dict):
            q = _str(item.get("question") or item.get("q"))
            a = _str(item.get("answer") or item.get("a"))
            if q or a:
                pairs.append((q, a))
    if not pairs:
        return f"\n{header}\n{empty}\n"
    lines: list[str] = []
    for i, (q, a) in enumerate(pairs, start=1):
        lines.append(f"Вопрос {i}: {q}")
        lines.append(f"Ответ: {a}")
    return f"\n{header}\n" + "\n".join(lines) + "\n"


def render_topic(topic: dict[str, Any] | None) -> str:
    topic = _as_dict(topic)
    name = _str(topic.get("name"))
    summary = _str(topic.get("summary"))
    past = topic.get("past") or []
    if not name and not summary and not past:
        return ""
    lines = [
        "Учти контекст темы ниже. Текущая ситуация может быть продолжением или логическим следствием "
        "ранее описанных — сверяйся с ними при ответе.",
        "Если связь неочевидна, не выдумывай продолжение; если связь есть — мягко укажи на неё.",
        "",
    ]
    if name:
        lines.append(f"ТЕМА: {name}")
    if summary:
        lines.append("")
        lines.append("СЖАТАЯ ПАМЯТЬ ПО ТЕМЕ (накопленные паттерны и итоги):")
        lines.append(summary)
    past_lines: list[str] = []
    for i, row in enumerate(past[:8], start=1):
        if isinstance(row, dict):
            date = _str(row.get("date"))
            text = _str(row.get("text"))
        else:
            date, text = "", _str(row)
        if not text:
            continue
        if len(text) > 500:
            text = text[:499].rstrip() + "…"
        prefix = f"Ситуация {i}"
        if date:
            prefix += f" ({date})"
        past_lines.append(f"{prefix}: {text}")
    if past_lines:
        lines.append("")
        lines.append(
            "ПРОШЛЫЕ СИТУАЦИИ ПО ЭТОЙ ТЕМЕ (от более новых к старым; "
            "текущая запись — в блоке «СИТУАЦИЯ» ниже):"
        )
        lines.extend(past_lines)
    body = "\n".join(lines).strip()
    return f"\nКОНТЕКСТ ТЕМЫ:\n{body}\n" if body else ""


def render_topics(payload: dict[str, Any]) -> str:
    raw = payload.get("topics")
    items: list[Any]
    if isinstance(raw, list) and raw:
        items = raw
    else:
        single = payload.get("topic")
        items = [single] if single else []
    blocks = [render_topic(_as_dict(item)) for item in items]
    blocks = [block for block in blocks if block]
    if not blocks:
        return ""
    if len(blocks) == 1:
        return blocks[0]
    header = (
        "\nУ этой ситуации несколько тем. Учитывай каждую и их пересечение; "
        "не своди ответ только к первой теме.\n"
    )
    return header + "\n".join(blocks)


def _context_blocks(
    payload: dict[str, Any],
    *,
    include_profile: bool,
) -> tuple[str, str, str]:
    profile = _as_dict(payload.get("profile"))
    if not include_profile:
        return "", "", ""
    anketa = f"АНКЕТА:\n{render_profile(profile)}\n"
    personality = render_personality(profile)
    topic = render_topics(payload)
    return anketa, personality, topic


def build_prompt(kind: str, payload: dict[str, Any]) -> str:
    profile = _as_dict(payload.get("profile"))
    situation = _str(payload.get("situation"))
    answers = payload.get("answers") or []
    dialogue_empty = "Диалога ещё не было — опирайся только на текст ситуации."
    dialogue = render_qa(answers, empty=dialogue_empty)
    lang = resolve_response_language(
        _str(payload.get("language")) or _str(profile.get("language_code")) or "ru",
        f"{situation}\n{dialogue}",
    )
    include_profile = not _no_history(payload)
    anketa, personality, topic = _context_blocks(payload, include_profile=include_profile)
    lang_ctx = render_language(lang)
    dialogue_note = (
        "\nУчитывай весь диалог с начала (ситуация + все вопросы и ответы) как единый контекст.\n"
        if answers
        else ""
    )
    qn = int(payload.get("question_number") or 1)

    if kind == "analyze":
        return (
            f"{lang_ctx}{render_variant(profile)}{render_style(profile)}{dialogue_note}\n"
            f"{anketa}СИТУАЦИЯ:\n{situation}\n{topic}{dialogue}{personality}"
        )
    if kind == "recommend":
        return (
            f"{lang_ctx}{render_variant(profile)}{render_style(profile)}{dialogue_note}\n"
            f"{anketa}СИТУАЦИЯ:\n{situation}\n{topic}{dialogue}{personality}"
        )
    if kind in {"questions", "questions_retry"}:
        retry = (
            "Верни ответ еще раз. Нужен только JSON-массив строк без пояснений.\n\n"
            if kind == "questions_retry"
            else ""
        )
        work_note = (
            "\nУже был живой диалог по ситуации — опирайся на него целиком и не дублируй уже заданное.\n"
            if answers
            else ""
        )
        n = question_count(payload)
        return (
            f"{retry}{lang_ctx}{render_difficulty(profile)}{render_length(profile)}{work_note}\n"
            f"Сгенерируй ровно {n} вопросов.\n"
            f"{anketa}СИТУАЦИЯ:\n{situation}\n{topic}"
            f"{render_qa(answers, empty='Диалога ещё не было.')}{personality}"
        )
    if kind == "questions_next":
        return (
            f"{lang_ctx}{render_difficulty(profile)}{render_length(profile)}\n\n"
            f"{anketa}СИТУАЦИЯ:\n{situation}\n{topic}{personality}"
            f"{render_qa(answers, empty='Предыдущих вопросов ещё не было.')}"
            f"\nНомер следующего вопроса по счёту: {qn}.\n"
        )
    if kind == "dialogue_question":
        return (
            f"{lang_ctx}{render_length(profile)}\n\n"
            f"{anketa}СИТУАЦИЯ:\n{situation}\n{topic}{personality}"
            f"{render_qa(answers, empty='Предыдущих вопросов ещё не было.')}"
            f"\nНомер следующего вопроса по счёту: {qn}.\n"
            "\nЕсли в ситуации мало фактов — спроси, что было до или после, кто что сделал. "
            "Не задавай подряд несколько вопросов только про чувства.\n"
        )
    if kind == "assistant":
        qa = render_qa(
            answers,
            empty="Ответы пока отсутствуют.",
            header="ВОПРОСЫ И ОТВЕТЫ:",
        )
        return (
            f"{lang_ctx}{render_variant(profile)}{render_style(profile)}\n\n"
            f"СИТУАЦИЯ:\n{situation}\n\n{anketa}\n{qa}{topic}{personality}"
        )
    if kind == "tts_understanding":
        return f"СИТУАЦИЯ:\n{situation}"
    if kind == "reminder_outreach":
        name = _str(profile.get("name"))
        name_hint = f"Имя пользователя: {name}." if name else "Имя не указано — можно без обращения по имени."
        portrait = _str(profile.get("my_personality"))
        portrait_block = f"\nПОРТРЕТ ЛИЧНОСТИ (учти тон, без прямых цитат):\n{portrait}\n" if portrait else ""
        return (
            f"{lang_ctx}\n{name_hint}\n"
            f"АНКЕТА:\n{render_profile(profile)}\n"
            f"{portrait_block}"
        )
    raise ValueError(f"unknown kind: {kind}")


def extract_personality(text: str) -> tuple[str, str | None]:
    match = PERSONALITY_BLOCK_RE.search(text or "")
    if not match:
        return (text or "").strip(), None
    personality = match.group(1).strip()
    cleaned = PERSONALITY_BLOCK_RE.sub("", text).strip()
    return cleaned, personality or None


def split_speakable(text: str) -> tuple[str, str]:
    raw = (text or "").strip()
    if SPEAKABLE_SPLIT in raw:
        readable, speakable = raw.split(SPEAKABLE_SPLIT, 1)
        return readable.strip(), speakable.strip()
    return raw, strip_markup(raw)


def strip_markup(text: str) -> str:
    cleaned = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text or "")
    cleaned = re.sub(r"\*+([^*]+)\*+", r"\1", cleaned)
    cleaned = re.sub(r"^#+\s*", "", cleaned, flags=re.MULTILINE)
    cleaned = re.sub(r"^[\s]*[-•*]\s+", "", cleaned, flags=re.MULTILINE)
    return " ".join(cleaned.split())


def sanitize_reminder(text: str, *, max_words: int = 36, max_chars: int = 220) -> str:
    raw = (text or "").strip().strip("\"'`«»").strip()
    parts = [ln.strip() for ln in raw.splitlines() if ln.strip()]
    cleaned = " ".join(parts[:2] if parts else [])
    cleaned = " ".join(cleaned.split())
    words = cleaned.split()
    if len(words) > max_words:
        cleaned = " ".join(words[:max_words])
    if len(cleaned) > max_chars:
        cleaned = cleaned[: max_chars - 1].rstrip(" .,;:—-") + "…"
    return cleaned.strip()


def _extract_json_blob(text: str) -> str:
    raw = (text or "").strip()
    fenced = JSON_FENCE_RE.search(raw)
    if fenced:
        return fenced.group(1).strip()
    start_obj = raw.find("{")
    start_arr = raw.find("[")
    if start_obj == -1 and start_arr == -1:
        return raw
    if start_arr == -1 or (start_obj != -1 and start_obj < start_arr):
        end = raw.rfind("}")
        return raw[start_obj : end + 1] if end > start_obj else raw
    end = raw.rfind("]")
    return raw[start_arr : end + 1] if end > start_arr else raw


def parse_question(text: str) -> str:
    blob = _extract_json_blob(text)
    try:
        data = json.loads(blob)
        if isinstance(data, dict):
            return _str(data.get("question"))
        if isinstance(data, list) and data:
            first = data[0]
            if isinstance(first, str):
                return first.strip()
            if isinstance(first, dict):
                return _str(first.get("question"))
    except Exception:
        pass
    return (text or "").strip().strip('"')


def question_count(payload: dict[str, Any], default: int = 5) -> int:
    try:
        n = int(payload.get("question_count") or default)
    except (TypeError, ValueError):
        n = default
    return max(1, min(30, n))


def parse_questions(text: str, limit: int = 5) -> list[str]:
    cap = max(1, min(30, int(limit or 5)))
    blob = _extract_json_blob(text)
    try:
        data = json.loads(blob)
        if isinstance(data, list):
            out = [_str(item) for item in data if _str(item)]
            return out[:cap] if out else []
        if isinstance(data, dict):
            q = data.get("questions") or data.get("question")
            if isinstance(q, list):
                return [_str(item) for item in q if _str(item)][:cap]
            if _str(q):
                return [_str(q)]
    except Exception:
        pass
    return []


def parse_model_output(kind: str, raw_text: str, question_limit: int = 5) -> dict[str, Any]:
    cleaned, personality = extract_personality(raw_text)
    readable, speakable = split_speakable(cleaned)
    if not readable:
        readable = speakable or cleaned
    if not speakable:
        speakable = readable
    result: dict[str, Any] = {
        "kind": kind,
        "text": readable,
        "speakable": speakable,
    }
    if personality:
        result["personality"] = personality
    if kind in {"dialogue_question", "questions_next"}:
        result["question"] = parse_question(readable)
        result["text"] = result["question"]
        result["speakable"] = result["question"]
    elif kind in {"questions", "questions_retry"}:
        questions = parse_questions(readable, question_limit)
        result["questions"] = questions
        result["text"] = json.dumps(questions, ensure_ascii=False) if questions else readable
    elif kind == "reminder_outreach":
        result["text"] = sanitize_reminder(readable)
        result["speakable"] = result["text"]
    elif kind == "tts_understanding":
        result["text"] = strip_markup(readable)
        result["speakable"] = result["text"]
    return result
