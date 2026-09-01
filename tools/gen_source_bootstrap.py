#!/usr/bin/env python3
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(r"d:\sites\12steps")
FILES = [
    ROOT / "app/src/main/java/ru/na/step4/obidy/Ru.kt",
    ROOT / "app/src/main/java/ru/na/step4/obidy/data/journal/JournalRu.kt",
    ROOT / "app/src/main/java/ru/na/step4/obidy/data/psych/PsychRu.kt",
    ROOT / "app/src/main/java/ru/na/step4/obidy/data/support/SupportRu.kt",
    ROOT / "app/src/main/java/ru/na/step4/obidy/data/spiritual/SpiritualRu.kt",
    ROOT / "voice/src/main/java/ru/na/steps12/voice/VoiceRu.kt",
]


def extract(path: Path) -> list[tuple[str, str]]:
    lines = path.read_text(encoding="utf-8").splitlines()
    entries: list[tuple[str, str]] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if ".t(\"" not in line or "get() =" not in line:
            i += 1
            continue
        km = re.search(r'\.t\("([^"]+)",\s*(.*)$', line)
        if not km:
            i += 1
            continue
        key = km.group(1)
        rest = km.group(2).strip()
        if rest.endswith(")"):
            expr = rest[:-1].strip()
            entries.append((key, expr))
            i += 1
            continue
        parts = [rest]
        i += 1
        while i < len(lines):
            s = lines[i].strip()
            if s == ")":
                break
            parts.append(s)
            i += 1
        expr = " ".join(parts).strip()
        if expr.endswith(")"):
            expr = expr[:-1].strip()
        entries.append((key, expr))
        i += 1
    return entries


def main() -> None:
    entries: list[tuple[str, str]] = []
    for f in FILES:
        entries.extend(extract(f))
    out = ROOT / "app/src/main/java/ru/na/step4/obidy/data/i18n/SourceBootstrap.kt"
    lines = [
        "package ru.na.step4.obidy.data.i18n",
        "",
        "/** Registers Russian source strings for screen-bundle translation. */",
        "object SourceBootstrap {",
        "    fun registerAll() {",
    ]
    for key, expr in entries:
        lines.append(f'        SourceCatalog.put("{key}", {expr})')
    lines += ["    }", "}", ""]
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {len(entries)} entries to {out}")


if __name__ == "__main__":
    main()
