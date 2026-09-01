#!/usr/bin/env python3
"""Convert const val string catalogs to I18n-backed getters."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(r"d:\sites\12steps")

FILES = [
    (ROOT / "app/src/main/java/ru/na/step4/obidy/Ru.kt", "ui", "ru.na.step4.obidy.data.i18n.I18n", "I18n"),
    (ROOT / "app/src/main/java/ru/na/step4/obidy/data/journal/JournalRu.kt", "journal", "ru.na.step4.obidy.data.i18n.I18n", "I18n"),
    (ROOT / "app/src/main/java/ru/na/step4/obidy/data/psych/PsychRu.kt", "psych", "ru.na.step4.obidy.data.i18n.I18n", "I18n"),
    (ROOT / "app/src/main/java/ru/na/step4/obidy/data/support/SupportRu.kt", "support", "ru.na.step4.obidy.data.i18n.I18n", "I18n"),
    (ROOT / "app/src/main/java/ru/na/step4/obidy/data/spiritual/SpiritualRu.kt", "spiritual", "ru.na.step4.obidy.data.i18n.I18n", "I18n"),
    (ROOT / "voice/src/main/java/ru/na/steps12/voice/VoiceRu.kt", "voice", "ru.na.steps12.voice.VoiceI18n", "VoiceI18n"),
]

START_RE = re.compile(r"^([ \t]*)const val ([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$")


def parse_string_expr(lines: list[str], start_idx: int, first_rest: str) -> tuple[str, int]:
    buf = first_rest.strip()
    i = start_idx
    if not buf:
        i += 1
        parts: list[str] = []
        while i < len(lines):
            stripped = lines[i].strip()
            if not stripped:
                i += 1
                continue
            if stripped.startswith('"') or stripped.startswith("+"):
                piece = stripped.lstrip("+").strip().rstrip("+").strip()
                parts.append(piece)
                nxt = lines[i + 1].strip() if i + 1 < len(lines) else ""
                if stripped.rstrip().endswith("+") or nxt.startswith("+"):
                    i += 1
                    continue
                i += 1
                break
            break
        if not parts:
            return '""', i
        if len(parts) == 1:
            return parts[0], i
        return " +\n        ".join(parts), i

    parts = [buf.rstrip().rstrip("+").strip()]
    i = start_idx + 1
    while i < len(lines):
        stripped = lines[i].strip()
        if stripped.startswith("+"):
            parts.append(stripped.lstrip("+").strip().rstrip("+").strip())
            i += 1
            continue
        break
    if len(parts) == 1:
        return parts[0], start_idx + 1
    return " +\n        ".join(parts), i


def ensure_import(text: str, import_fqn: str) -> str:
    line = f"import {import_fqn}"
    if line in text:
        return text
    m = re.match(r"(package [^\n]+\n)", text)
    if not m:
        return line + "\n\n" + text
    return text[: m.end()] + "\n" + line + "\n" + text[m.end() :]


def convert_file(path: Path, prefix: str, import_fqn: str, alias: str) -> list[str]:
    original = path.read_text(encoding="utf-8")
    lines = original.splitlines()
    out: list[str] = []
    keys: list[str] = []
    i = 0
    while i < len(lines):
        m = START_RE.match(lines[i])
        if not m:
            out.append(lines[i])
            i += 1
            continue
        indent, name, rest = m.group(1), m.group(2), m.group(3)
        expr, next_i = parse_string_expr(lines, i, rest)
        key = f"{prefix}.{name}"
        keys.append(key)
        if "\n" in expr:
            out.append(f'{indent}val {name}: String get() = {alias}.t("{key}",')
            out.append(f"{indent}    {expr}")
            out.append(f"{indent})")
        else:
            out.append(f'{indent}val {name}: String get() = {alias}.t("{key}", {expr})')
        i = next_i
    text = "\n".join(out) + "\n"
    text = ensure_import(text, import_fqn)
    path.write_text(text, encoding="utf-8")
    print(f"{path.name}: {len(keys)} keys")
    return keys


def emit_set(name: str, keys: list[str]) -> list[str]:
    lines = [f"    val {name}: Set<String> = setOf("]
    for k in keys:
        lines.append(f'        "{k}",')
    lines.append("    )")
    lines.append("")
    return lines


def main() -> None:
    all_keys: dict[str, list[str]] = {}
    for path, prefix, imp, alias in FILES:
        all_keys[prefix] = convert_file(path, prefix, imp, alias)

    out = ROOT / "app/src/main/java/ru/na/step4/obidy/data/i18n/UiSourceKeys.kt"
    clean: list[str] = [
        "package ru.na.step4.obidy.data.i18n",
        "",
        "/** Stable UI string keys registered from *Ru catalogs. Content keys added at runtime. */",
        "object UiSourceKeys {",
    ]
    clean += emit_set("ui", all_keys.get("ui", []))
    clean += emit_set("journal", all_keys.get("journal", []))
    clean += emit_set("psych", all_keys.get("psych", []))
    clean += emit_set("support", all_keys.get("support", []))
    clean += emit_set("spiritual", all_keys.get("spiritual", []))
    clean += emit_set("voice", all_keys.get("voice", []))
    clean += [
        "    val common: Set<String>",
        "        get() = ui + support + spiritual + voice +",
        '            SourceCatalog.keys().filter { it.startsWith("profile.") || it.startsWith("note.") }.toSet()',
        "    val home: Set<String> get() = ui",
        "    val profile: Set<String>",
        '        get() = ui + SourceCatalog.keys().filter { it.startsWith("profile.") }.toSet()',
        "    val analysis: Set<String> get() = ui",
        "    val inventory: Set<String>",
        '        get() = ui + SourceCatalog.keys().filter { it.startsWith("inventory.") }.toSet()',
        "    val lock: Set<String> get() = ui",
        "",
        "    fun treePrefixKeys(): Set<String> =",
        '        SourceCatalog.keys().filter { it.startsWith("tree.") }.toSet()',
        "",
        "    fun analysisCatalogKeys(): Set<String> =",
        '        SourceCatalog.keys().filter { it.startsWith("analysis.") }.toSet()',
        "}",
        "",
    ]
    out.write_text("\n".join(clean), encoding="utf-8")
    print(f"Wrote {out}")


if __name__ == "__main__":
    main()
