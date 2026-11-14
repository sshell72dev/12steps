"""Литературная редактура глав «Моей книги».

Текст главы уходит в модель, обратно приходит список точечных правок:
old — фрагмент исходного текста, new — его замена (пустая строка = удалить),
reason — короткая причина на русском.
"""
from __future__ import annotations

import json
import re
from typing import Any

import roles

SYSTEM_PROMPT = roles.BOOK_FIX

MAX_EDITS = 400


def build_user_prompt(text: str, language: str = "ru") -> str:
    lang = (language or "ru").strip() or "ru"
    return (
        f"Язык текста: {lang}\n"
        "Отредактируй текст главы и верни JSON-массив правок.\n\n"
        f"Текст:\n{text}"
    )


def _as_list(text: str) -> list[Any] | None:
    raw = (text or "").strip()
    if not raw:
        return None
    fence = re.search(r"```(?:json)?\s*([\s\S]*?)```", raw, re.IGNORECASE)
    if fence:
        raw = fence.group(1).strip()
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        start = raw.find("[")
        end = raw.rfind("]")
        if start < 0 or end <= start:
            return None
        try:
            data = json.loads(raw[start : end + 1])
        except json.JSONDecodeError:
            return None
    if isinstance(data, dict):
        # Модель иногда оборачивает массив в объект вида {"edits": [...]}.
        for key in ("edits", "fixes", "items", "result"):
            value = data.get(key)
            if isinstance(value, list):
                return value
        return None
    return data if isinstance(data, list) else None


def parse_edits(raw: str, source: str = "") -> list[dict[str, str]] | None:
    """Разбирает ответ модели. None — ответ не разобрался вовсе;
    пустой список — модель честно не нашла правок.
    Правка без фрагмента в исходнике отбрасывается: приложение всё равно
    не сможет её применить."""
    rows = _as_list(raw)
    if rows is None:
        return None
    out: list[dict[str, str]] = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        old = row.get("old")
        new = row.get("new")
        reason = row.get("reason")
        old_text = old if isinstance(old, str) else ""
        new_text = new if isinstance(new, str) else ""
        if not old_text.strip() or old_text == new_text:
            continue
        if source and old_text not in source:
            continue
        out.append(
            {
                "old": old_text,
                "new": new_text,
                "reason": (reason if isinstance(reason, str) else "").strip(),
            }
        )
        if len(out) >= MAX_EDITS:
            break
    return out


def normalize_text(payload: dict[str, Any]) -> str:
    value = payload.get("text")
    return value if isinstance(value, str) else ""
