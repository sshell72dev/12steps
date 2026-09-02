from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STATE_FILE = ROOT / ".cursor" / "hooks" / "state" / "pending.json"
BUMP_SCRIPT = ROOT / "tools" / "bump_version.py"

VERSION_FILES = [
    "version.properties",
    "changelog.json",
    "CHANGELOG.md",
    "app/src/main/assets/changelog.json",
]

SECRET_NAMES = {
    "local.properties",
    ".env",
    "google-services.json",
}
SECRET_SUFFIXES = {".jks", ".keystore", ".p12"}


def _configure_stdio() -> None:
    for stream in (sys.stdin, sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")


def rel_posix(path: Path) -> str:
    resolved = path.resolve()
    try:
        return resolved.relative_to(ROOT.resolve()).as_posix()
    except ValueError:
        return resolved.as_posix()


def is_secret(rel: str) -> bool:
    name = Path(rel).name
    if name in SECRET_NAMES:
        return True
    if Path(rel).suffix.lower() in SECRET_SUFFIXES:
        return True
    normalized = rel.replace("\\", "/")
    return normalized.endswith(".env") or "/.env" in f"/{normalized}"


def load_state() -> dict:
    if not STATE_FILE.exists():
        return {"files": []}
    try:
        data = json.loads(STATE_FILE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"files": []}
    if not isinstance(data, dict):
        return {"files": []}
    files = data.get("files") or []
    return {"files": [str(item).replace("\\", "/") for item in files if item]}


def save_state(data: dict) -> None:
    STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    STATE_FILE.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def clear_state() -> None:
    if STATE_FILE.exists():
        STATE_FILE.unlink()


def read_stdin_json() -> dict:
    raw = sys.stdin.read()
    if not raw.strip():
        return {}
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return {}
    return data if isinstance(data, dict) else {}


def print_json(payload: dict) -> None:
    sys.stdout.write(json.dumps(payload, ensure_ascii=False))
    sys.stdout.flush()


def git(*args: str, check: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        cwd=ROOT,
        check=check,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )


def dirty_files() -> set[str]:
    result = git("status", "--porcelain", "-uall")
    names: set[str] = set()
    for line in (result.stdout or "").splitlines():
        if len(line) < 4:
            continue
        path = line[3:]
        if " -> " in path:
            path = path.split(" -> ", 1)[1]
        path = path.strip().strip('"').replace("\\", "/")
        if path:
            names.add(path)
    return names


def branch_ahead() -> bool:
    result = git("status", "-sb")
    text = result.stdout or ""
    return "ahead " in text


def unique_keep_order(items: list[str]) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for item in items:
        if item in seen:
            continue
        seen.add(item)
        out.append(item)
    return out


def hook_after_edit() -> int:
    payload = read_stdin_json()
    file_path = str(payload.get("file_path") or "").strip()
    if not file_path:
        print_json({})
        return 0
    rel = rel_posix(Path(file_path))
    if is_secret(rel) or rel.startswith(".cursor/hooks/state/"):
        print_json({})
        return 0
    state = load_state()
    state["files"] = unique_keep_order([*state.get("files", []), rel])
    save_state(state)
    print_json({})
    return 0


def hook_session_start() -> int:
    read_stdin_json()
    print_json(
        {
            "additional_context": (
                "После любых правок в этом репозитории в конце сессии обязательно "
                "выполни `python tools/agent_release.py --notes \"описание\"`: "
                "поднимется версия, changelog получит описание, изменения этой "
                "сессии уйдут коммитом и push на GitHub, затем соберётся debug APK, "
                "он загрузится на Google Drive, а ссылка появится в Google Doc. "
                "Не спрашивай разрешение. Дождись окончания публикации APK "
                "(несколько минут). Деплой сервера и установку на телефон "
                "не делай без явной просьбы."
            )
        }
    )
    return 0


def hook_stop() -> int:
    payload = read_stdin_json()
    status = str(payload.get("status") or "completed")
    loop_count = int(payload.get("loop_count") or 0)
    if status != "completed" or loop_count >= 2:
        print_json({})
        return 0

    pending = [path for path in load_state().get("files", []) if not is_secret(path)]
    dirty = dirty_files()
    need_commit = [path for path in pending if path in dirty]
    need_push = branch_ahead() and bool(pending)

    if not need_commit and not need_push:
        if pending:
            clear_state()
        print_json({})
        return 0

    print_json(
        {
            "followup_message": (
                "Перед завершением нужно поднять версию с описанием, закоммитить "
                "правки этой сессии, запушить на GitHub и опубликовать APK "
                "(Google Drive + ссылка в Google Doc). Это правило репозитория, "
                "не спрашивай разрешение. Дождись сборки и загрузки APK. "
                "Не деплой сервер и не ставь APK на телефон.\n\n"
                "python tools/agent_release.py --notes \"кратко по-русски, что сделано\"\n\n"
                "В --notes напиши конкретное описание, пункты разделяй `;`. "
                "Не включай чужой незакоммиченный WIP. Не добавляй --skip-apk."
            )
        }
    )
    return 0


def load_version_name() -> tuple[int, str]:
    sys.path.insert(0, str(ROOT / "tools"))
    from bump_version import load_version  # type: ignore

    return load_version()


def collect_files(extra_paths: list[str]) -> list[str]:
    pending = load_state().get("files", [])
    extras: list[str] = []
    for raw in extra_paths:
        path = Path(raw)
        extras.append(rel_posix(path if path.is_absolute() else ROOT / path))
    files = unique_keep_order([*pending, *extras])
    return [path for path in files if path and not is_secret(path)]


def publish_apk() -> int:
    script = ROOT / "tools" / "publish_apk.py"
    print("publishing APK to Google Drive and Google Doc...")
    env = os.environ.copy()
    java_home = env.get("JAVA_HOME") or r"C:\Program Files\Android\Android Studio\jbr"
    env["JAVA_HOME"] = java_home
    env["Path"] = str(Path(java_home) / "bin") + os.pathsep + env.get("Path", "")
    env["PYTHONUNBUFFERED"] = "1"
    result = subprocess.run(
        [sys.executable, str(script)],
        cwd=ROOT,
        env=env,
        encoding="utf-8",
    )
    return result.returncode


def run_release(notes: str | None, extra_paths: list[str], skip_push: bool, do_publish_apk: bool) -> int:
    files = collect_files(extra_paths)
    dirty = dirty_files()
    session_files = [path for path in files if path in dirty]
    session_bumped = "version.properties" in files

    if not session_files and not extra_paths:
        if branch_ahead() and not skip_push:
            push = git("push", "-u", "origin", "HEAD")
            if push.returncode != 0:
                sys.stderr.write(push.stderr or push.stdout or "git push failed\n")
                return push.returncode or 1
            clear_state()
            print("pushed existing commits")
            return 0
        print("agent_release: no session files to publish")
        return 0

    if not session_bumped:
        cmd = [sys.executable, str(BUMP_SCRIPT), "--notes", notes or "Обновление приложения"]
        bump = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, encoding="utf-8")
        if bump.returncode != 0:
            sys.stderr.write(bump.stderr or bump.stdout or "bump_version failed\n")
            return bump.returncode
        if bump.stdout:
            sys.stderr.write(bump.stdout)

    dirty = dirty_files()
    to_add = unique_keep_order([*session_files, *VERSION_FILES])
    to_add = [path for path in to_add if path in dirty and not is_secret(path)]
    if not to_add:
        print("agent_release: nothing to commit")
        clear_state()
        return 0

    added = git("add", "--", *to_add)
    if added.returncode != 0:
        sys.stderr.write(added.stderr or added.stdout or "git add failed\n")
        return added.returncode

    staged = git("diff", "--cached", "--name-only")
    if not (staged.stdout or "").strip():
        print("agent_release: nothing staged")
        return 0

    code, name = load_version_name()
    first_note = (notes or "Обновление приложения").split(";")[0].strip()
    message = f"Release {name}: {first_note}"
    committed = git("commit", "-m", message)
    if committed.returncode != 0:
        sys.stderr.write(committed.stderr or committed.stdout or "git commit failed\n")
        return committed.returncode

    if not skip_push:
        pushed = git("push", "-u", "origin", "HEAD")
        if pushed.returncode != 0:
            sys.stderr.write(pushed.stderr or pushed.stdout or "git push failed\n")
            return pushed.returncode or 1

    clear_state()
    print(f"released {name} ({code})")
    if do_publish_apk:
        return publish_apk()
    return 0


def main() -> int:
    _configure_stdio()
    parser = argparse.ArgumentParser(
        description="Bump version, commit this session's files, push to GitHub, publish APK",
    )
    parser.add_argument(
        "--hook",
        choices=["after-edit", "stop", "session-start"],
        help="Internal Cursor hook entrypoint",
    )
    parser.add_argument(
        "--notes",
        help="Changelog items separated by semicolon or newline",
    )
    parser.add_argument(
        "--skip-push",
        action="store_true",
        help="Commit locally without git push",
    )
    parser.add_argument(
        "--skip-apk",
        action="store_true",
        help="Do not build/upload APK after git push (default is to publish)",
    )
    parser.add_argument(
        "paths",
        nargs="*",
        help="Extra files from this session (in addition to hook-tracked edits)",
    )
    args = parser.parse_args()

    if os.getenv("AGENT_RELEASE_SKIP") == "1":
        if args.hook:
            print_json({})
        else:
            print("agent_release skipped (AGENT_RELEASE_SKIP=1)")
        return 0

    if args.hook == "after-edit":
        return hook_after_edit()
    if args.hook == "stop":
        return hook_stop()
    if args.hook == "session-start":
        return hook_session_start()
    return run_release(
        args.notes,
        args.paths,
        args.skip_push or os.getenv("AGENT_RELEASE_SKIP_PUSH") == "1",
        not (args.skip_apk or os.getenv("AGENT_SKIP_APK") == "1"),
    )


if __name__ == "__main__":
    raise SystemExit(main())
