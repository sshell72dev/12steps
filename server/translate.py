"""UI / reference content translation for the Android app."""
from __future__ import annotations

import json
import re
from typing import Any


SYSTEM_PROMPT = (
    "You are a professional translator for a 12-step recovery mobile app. "
    "Translate UI labels, titles, hints and reference literature excerpts accurately. "
    "Keep placeholders like %1$d, %2$s, {{vars}} and HTML/markdown unchanged. "
    "Keep line breaks. Do not add explanations. Do not invent content. "
    "Return ONLY a JSON array of objects with keys \"key\" and \"text\"."
)


def build_user_prompt(target_language: str, items: list[dict[str, str]]) -> str:
    payload = [{"key": it["key"], "text": it["text"]} for it in items]
    return (
        f"Target language: {target_language}\n"
        "Source language: Russian\n"
        "Translate each item's text into the target language. "
        "Preserve meaning for 12-step / NA recovery context.\n\n"
        f"Items JSON:\n{json.dumps(payload, ensure_ascii=False)}"
    )


def parse_items(raw: str) -> list[dict[str, str]]:
    text = (raw or "").strip()
    if not text:
        return []
    # Strip markdown fences if present
    fence = re.search(r"```(?:json)?\s*([\s\S]*?)```", text, re.IGNORECASE)
    if fence:
        text = fence.group(1).strip()
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        start = text.find("[")
        end = text.rfind("]")
        if start < 0 or end <= start:
            return []
        try:
            data = json.loads(text[start : end + 1])
        except json.JSONDecodeError:
            return []
    if not isinstance(data, list):
        return []
    out: list[dict[str, str]] = []
    for row in data:
        if not isinstance(row, dict):
            continue
        key = str(row.get("key") or "").strip()
        value = str(row.get("text") or "").strip()
        if key and value:
            out.append({"key": key, "text": value})
    return out


def normalize_request(payload: dict[str, Any]) -> tuple[str, list[dict[str, str]]]:
    target = str(payload.get("target_language") or payload.get("language") or "en").strip()
    raw_items = payload.get("items")
    items: list[dict[str, str]] = []
    if isinstance(raw_items, list):
        for row in raw_items:
            if not isinstance(row, dict):
                continue
            key = str(row.get("key") or "").strip()
            text = str(row.get("text") or "").strip()
            if key and text:
                items.append({"key": key, "text": text})
    return target, items
