from __future__ import annotations

import argparse
import json
import re
import shutil
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSION_FILE = ROOT / "version.properties"
CHANGELOG_JSON = ROOT / "changelog.json"
CHANGELOG_MD = ROOT / "CHANGELOG.md"
ASSETS_CHANGELOG = ROOT / "app" / "src" / "main" / "assets" / "changelog.json"


def load_version() -> tuple[int, str]:
    props: dict[str, str] = {}
    for raw in VERSION_FILE.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key.strip()] = value.strip()
    code = int(props.get("VERSION_CODE", "1"))
    name = props.get("VERSION_NAME", "1.0.0")
    return code, name


def save_version(code: int, name: str) -> None:
    VERSION_FILE.write_text(
        f"VERSION_CODE={code}\nVERSION_NAME={name}\n",
        encoding="utf-8",
    )


def bump_name(name: str) -> str:
    match = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)", name)
    if not match:
        return name
    major, minor, patch = (int(x) for x in match.groups())
    return f"{major}.{minor}.{patch + 1}"


def parse_notes(raw: str | None) -> list[str]:
    if not raw:
        return ["Обновление сервера и приложения"]
    items = [part.strip() for part in re.split(r"[\n;]+", raw) if part.strip()]
    return items or ["Обновление сервера и приложения"]


def load_changelog() -> dict:
    if CHANGELOG_JSON.exists():
        return json.loads(CHANGELOG_JSON.read_text(encoding="utf-8"))
    return {"releases": []}


def save_changelog(data: dict) -> None:
    text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    CHANGELOG_JSON.write_text(text, encoding="utf-8")
    ASSETS_CHANGELOG.parent.mkdir(parents=True, exist_ok=True)
    ASSETS_CHANGELOG.write_text(text, encoding="utf-8")
    CHANGELOG_MD.write_text(render_markdown(data), encoding="utf-8")


def render_markdown(data: dict) -> str:
    lines = [
        "# История изменений",
        "",
        "Формат основан на [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/).",
        "Версия приложения обновляется скриптом `tools/bump_version.py` после работы агента и при деплое.",
        "",
    ]
    for release in data.get("releases", []):
        version = release.get("version", "?")
        when = release.get("date", "")
        lines.append(f"## [{version}] — {when}")
        lines.append("")
        for item in release.get("items", []):
            lines.append(f"- {item}")
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def bump(notes: str | None) -> tuple[int, str]:
    old_code, old_name = load_version()
    new_code = old_code + 1
    new_name = bump_name(old_name)
    save_version(new_code, new_name)

    data = load_changelog()
    releases = data.setdefault("releases", [])
    releases.insert(
        0,
        {
            "version": new_name,
            "versionCode": new_code,
            "date": date.today().isoformat(),
            "items": parse_notes(notes),
        },
    )
    save_changelog(data)
    return new_code, new_name


def sync_assets() -> None:
    if not CHANGELOG_JSON.exists():
        raise SystemExit(f"missing {CHANGELOG_JSON}")
    shutil.copy2(CHANGELOG_JSON, ASSETS_CHANGELOG)


def main() -> None:
    parser = argparse.ArgumentParser(description="Bump app release version and changelog")
    parser.add_argument(
        "--notes",
        help="Change items separated by newline or semicolon",
    )
    parser.add_argument(
        "--sync-only",
        action="store_true",
        help="Copy changelog.json to app assets without bumping",
    )
    args = parser.parse_args()

    if args.sync_only:
        sync_assets()
        code, name = load_version()
        print(f"synced changelog for {name} ({code})")
        return

    code, name = bump(args.notes)
    print(f"bumped to {name} ({code})")


if __name__ == "__main__":
    main()
