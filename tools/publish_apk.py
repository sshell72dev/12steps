from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSION_FILE = ROOT / "version.properties"
CHANGELOG_JSON = ROOT / "changelog.json"
LINKS_FILE = ROOT / "tools" / "apk_links.json"

DOC_ID = os.getenv("GOOGLE_DOC_ID", "1dcUoEwGAmScEghfdHBUAiblaz0sXCmmCRrFMzhCPP9E")
DOC_URL = f"https://docs.google.com/document/d/{DOC_ID}/edit?usp=sharing"
REMOTE = os.getenv("GOOGLE_DRIVE_REMOTE", "steps12")
FOLDER = os.getenv("GOOGLE_DRIVE_FOLDER", "12steps-apk")
SCOPES = [
    "https://www.googleapis.com/auth/drive",
    "https://www.googleapis.com/auth/documents",
]


def _configure_stdio() -> None:
    for stream in (sys.stdin, sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")


def load_env() -> None:
    for path in (ROOT / "server" / ".env", ROOT / "local.properties"):
        if not path.exists():
            continue
        for raw in path.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


def load_version() -> tuple[int, str]:
    props: dict[str, str] = {}
    for raw in VERSION_FILE.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key.strip()] = value.strip()
    return int(props.get("VERSION_CODE", "1")), props.get("VERSION_NAME", "1.0.0")


def load_changelog() -> dict:
    if CHANGELOG_JSON.exists():
        return json.loads(CHANGELOG_JSON.read_text(encoding="utf-8"))
    return {"releases": []}


def load_links() -> dict[str, str]:
    if not LINKS_FILE.exists():
        return {}
    try:
        data = json.loads(LINKS_FILE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    if not isinstance(data, dict):
        return {}
    return {str(k): str(v) for k, v in data.items() if k and v}


def save_links(links: dict[str, str]) -> None:
    LINKS_FILE.write_text(
        json.dumps(links, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


RCLONE_ZIP = (
    "https://github.com/rclone/rclone/releases/download/"
    "v1.75.0/rclone-v1.75.0-windows-amd64.zip"
)


def install_rclone_portable() -> str:
    dest_dir = ROOT / "tools" / ".vendor" / "rclone"
    dest_exe = dest_dir / "rclone.exe"
    if dest_exe.exists():
        return str(dest_exe)
    dest_dir.mkdir(parents=True, exist_ok=True)
    zip_path = dest_dir / "rclone.zip"
    print("скачиваю rclone (портативная копия в tools/.vendor) …", flush=True)
    urllib.request.urlretrieve(RCLONE_ZIP, zip_path)
    with zipfile.ZipFile(zip_path) as archive:
        exe_info = next(
            (item for item in archive.infolist() if item.filename.replace("\\", "/").endswith("rclone.exe")),
            None,
        )
        if exe_info is None:
            raise SystemExit("в архиве rclone нет rclone.exe")
        with archive.open(exe_info) as src, dest_exe.open("wb") as out:
            out.write(src.read())
    zip_path.unlink(missing_ok=True)
    return str(dest_exe)


def find_rclone() -> str:
    env = os.getenv("RCLONE_EXE", "").strip()
    if env and Path(env).exists():
        return env
    found = shutil.which("rclone")
    if found:
        return found
    local = ROOT / "tools" / ".vendor" / "rclone" / "rclone.exe"
    if local.exists():
        return str(local)
    nested = ROOT / "tools" / ".vendor" / "rclone" / "rclone-v1.75.0-windows-amd64" / "rclone.exe"
    if nested.exists():
        return str(nested)
    for candidate in (
        Path(os.environ.get("LOCALAPPDATA", "")) / "Microsoft" / "WinGet" / "Links" / "rclone.exe",
        Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "rclone" / "rclone.exe",
        Path(os.environ.get("ProgramFiles(x86)", r"C:\Program Files (x86)")) / "rclone" / "rclone.exe",
    ):
        if candidate.exists():
            return str(candidate)
    return install_rclone_portable()


def rclone(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    exe = find_rclone()
    return subprocess.run(
        [exe, *args],
        cwd=ROOT,
        check=check,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )


def rclone_conf_path() -> Path:
    return Path(os.environ.get("APPDATA", "")) / "rclone" / "rclone.conf"


def remotes() -> list[str]:
    result = rclone("listremotes", check=False)
    if result.returncode != 0:
        return []
    return [line.strip().rstrip(":") for line in (result.stdout or "").splitlines() if line.strip()]


def ensure_remote() -> None:
    if REMOTE in remotes():
        return
    print("Откроется браузер Google — войдите в аккаунт, где лежит документ «12 steps».")
    result = rclone(
        "config",
        "create",
        REMOTE,
        "drive",
        "scope",
        "drive",
        "additional_scopes",
        "https://www.googleapis.com/auth/documents",
        check=False,
    )
    sys.stderr.write(result.stdout or "")
    sys.stderr.write(result.stderr or "")
    if result.returncode != 0 or REMOTE not in remotes():
        raise SystemExit(
            "Не удалось настроить Google Drive.\n"
            "Выполните вручную:\n"
            f"  rclone config create {REMOTE} drive scope drive additional_scopes https://www.googleapis.com/auth/documents\n"
            "и повторите python tools/publish_apk.py"
        )
    print(f"rclone remote «{REMOTE}» готов")


def rclone_path(name: str = "") -> str:
    base = f"{REMOTE}:{FOLDER}"
    return f"{base}/{name}" if name else base


def ensure_folder() -> None:
    mkdir = rclone("mkdir", rclone_path(), check=False)
    if mkdir.returncode != 0:
        err = (mkdir.stderr or mkdir.stdout or "").strip()
        if "already exists" not in err.lower() and "directory not empty" not in err.lower():
            sys.stderr.write(err + "\n")
            raise SystemExit("не удалось создать папку на Google Drive")


def apk_remote_name(version: str) -> str:
    return f"12steps-{version}.apk"


def upload_apk(local: Path, version: str) -> str:
    ensure_folder()
    remote = rclone_path(apk_remote_name(version))
    print(f"загрузка {local.name} → {remote}")
    rclone("copyto", str(local), remote)
    linked = rclone("link", remote)
    url = (linked.stdout or "").strip().splitlines()[-1].strip()
    if not url.startswith("http"):
        raise SystemExit(f"rclone link не вернул URL:\n{linked.stdout}\n{linked.stderr}")
    if "drive.google.com" in url and "/file/d/" in url:
        file_id = url.split("/file/d/", 1)[1].split("/", 1)[0]
        url = f"https://drive.google.com/file/d/{file_id}/view?usp=sharing"
    elif "id=" in url:
        file_id = url.split("id=", 1)[1].split("&", 1)[0]
        url = f"https://drive.google.com/file/d/{file_id}/view?usp=sharing"
    return url


def list_drive_links() -> dict[str, str]:
    ensure_folder()
    listed = rclone("lsjson", rclone_path(), check=False)
    if listed.returncode != 0:
        return {}
    try:
        items = json.loads(listed.stdout or "[]")
    except json.JSONDecodeError:
        return {}
    links: dict[str, str] = {}
    pattern = re.compile(r"^12steps-(\d+\.\d+\.\d+)\.apk$", re.I)
    for item in items:
        name = str(item.get("Name") or "")
        match = pattern.match(name)
        if not match:
            continue
        version = match.group(1)
        linked = rclone("link", rclone_path(name), check=False)
        url = (linked.stdout or "").strip().splitlines()[-1].strip() if linked.returncode == 0 else ""
        if url.startswith("http"):
            links[version] = url
    return links


def read_rclone_access_token() -> str:
    rclone("about", f"{REMOTE}:", check=False)
    path = rclone_conf_path()
    if not path.exists():
        raise SystemExit(f"нет rclone.conf: {path}")
    section = None
    token_raw = ""
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line.startswith("[") and line.endswith("]"):
            section = line[1:-1]
            continue
        if section != REMOTE or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() == "token":
            token_raw = value.strip()
    if not token_raw:
        raise SystemExit(f"в rclone.conf нет token для [{REMOTE}]")
    data = json.loads(token_raw)
    token = str(data.get("access_token") or "").strip()
    if not token:
        raise SystemExit("rclone token без access_token")
    return token


def ensure_google_libs() -> None:
    try:
        import googleapiclient.discovery  # noqa: F401
        import google.oauth2.credentials  # noqa: F401
    except ImportError:
        print("ставим google-api-python-client …")
        subprocess.run(
            [
                sys.executable,
                "-m",
                "pip",
                "install",
                "google-api-python-client",
                "google-auth",
                "google-auth-httplib2",
            ],
            check=True,
        )


def drive_service():
    ensure_google_libs()
    from google.oauth2.credentials import Credentials
    from googleapiclient.discovery import build

    creds = Credentials(token=read_rclone_access_token(), scopes=SCOPES)
    return build("drive", "v3", credentials=creds, cache_discovery=False)


def docs_service():
    ensure_google_libs()
    from google.oauth2.credentials import Credentials
    from googleapiclient.discovery import build

    creds = Credentials(token=read_rclone_access_token(), scopes=SCOPES)
    return build("docs", "v1", credentials=creds, cache_discovery=False)


def render_doc(releases: list[dict], links: dict[str, str]) -> str:
    lines = [
        "12 шагов — установочные файлы",
        "",
        "Новые версии сверху. «Скачать APK» открывает файл на Google Диске.",
        f"Этот документ: {DOC_URL}",
        "",
    ]
    for release in releases:
        version = str(release.get("version") or "?")
        when = str(release.get("date") or "")
        items = [str(item) for item in release.get("items") or [] if str(item).strip()]
        url = links.get(version, "")
        lines.append("─" * 40)
        lines.append(f"{version}  ·  {when}")
        if url:
            lines.append(f"Скачать APK: {url}")
        else:
            lines.append("Сборка APK для этой версии ещё не загружена.")
        lines.append("")
        if items:
            for item in items:
                lines.append(f"• {item}")
        else:
            lines.append("• (без описания)")
        lines.append("")
    stamp = datetime.now(timezone.utc).astimezone().strftime("%Y-%m-%d %H:%M")
    lines.append(f"Обновлено: {stamp}")
    return "\n".join(lines).rstrip() + "\n"


def render_html(releases: list[dict], links: dict[str, str]) -> str:
    import html as html_lib

    parts = [
        "<html><head><meta charset='utf-8'></head><body>",
        "<h1>12 шагов — установочные файлы</h1>",
        "<p>Новые версии сверху. «Скачать APK» открывает файл на Google Диске.</p>",
    ]
    for release in releases:
        version = html_lib.escape(str(release.get("version") or "?"))
        when = html_lib.escape(str(release.get("date") or ""))
        items = [str(item) for item in release.get("items") or [] if str(item).strip()]
        url = links.get(str(release.get("version") or ""), "")
        parts.append(f"<h2>{version} · {when}</h2>")
        if url:
            parts.append(f'<p><a href="{html_lib.escape(url, quote=True)}">Скачать APK</a></p>')
        else:
            parts.append("<p>Сборка APK для этой версии ещё не загружена.</p>")
        parts.append("<ul>")
        if items:
            for item in items:
                parts.append(f"<li>{html_lib.escape(item)}</li>")
        else:
            parts.append("<li>(без описания)</li>")
        parts.append("</ul>")
    stamp = html_lib.escape(datetime.now(timezone.utc).astimezone().strftime("%Y-%m-%d %H:%M"))
    parts.append(f"<p><small>Обновлено: {stamp}</small></p>")
    parts.append("</body></html>")
    return "\n".join(parts)


def replace_doc_via_docs_api(text: str) -> None:
    docs = docs_service()
    document = docs.documents().get(documentId=DOC_ID).execute()
    content = document.get("body", {}).get("content") or []
    end_index = 1
    if content:
        end_index = int(content[-1].get("endIndex") or 1)
    requests: list[dict] = []
    if end_index > 2:
        requests.append(
            {
                "deleteContentRange": {
                    "range": {"startIndex": 1, "endIndex": end_index - 1}
                }
            }
        )
    requests.append({"insertText": {"location": {"index": 1}, "text": text}})
    docs.documents().batchUpdate(
        documentId=DOC_ID,
        body={"requests": requests},
    ).execute()


def replace_doc_via_drive_html(html: str) -> None:
    import io

    from googleapiclient.discovery import build
    from googleapiclient.http import MediaIoBaseUpload
    from google.oauth2.credentials import Credentials

    media = MediaIoBaseUpload(
        io.BytesIO(html.encode("utf-8")),
        mimetype="text/html",
        resumable=True,
    )
    try:
        drive_service().files().update(
            fileId=DOC_ID,
            media_body=media,
            supportsAllDrives=True,
        ).execute()
        return
    except Exception as exc:
        print(f"Drive v3 update не принял HTML ({exc.__class__.__name__}), пробую v2 convert…", flush=True)
    creds = Credentials(token=read_rclone_access_token(), scopes=SCOPES)
    drive_v2 = build("drive", "v2", credentials=creds, cache_discovery=False)
    media = MediaIoBaseUpload(
        io.BytesIO(html.encode("utf-8")),
        mimetype="text/html",
        resumable=True,
    )
    drive_v2.files().update(fileId=DOC_ID, media_body=media, convert=True).execute()


def replace_doc(text: str, html: str) -> None:
    try:
        replace_doc_via_docs_api(text)
        return
    except Exception as exc:
        print(f"Docs API недоступен ({exc.__class__.__name__}), пробую Drive…", flush=True)
    replace_doc_via_drive_html(html)


def assemble_debug_apk() -> Path:
    java_home = os.getenv("JAVA_HOME") or r"C:\Program Files\Android\Android Studio\jbr"
    env = os.environ.copy()
    env["JAVA_HOME"] = java_home
    env["Path"] = str(Path(java_home) / "bin") + os.pathsep + env.get("Path", "")
    gradlew = ROOT / "gradlew.bat"
    print("сборка debug APK …")
    result = subprocess.run(
        [str(gradlew), "assembleDebug", "--quiet"],
        cwd=ROOT,
        env=env,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    if result.returncode != 0:
        sys.stderr.write(result.stdout or "")
        sys.stderr.write(result.stderr or "")
        raise SystemExit("сборка APK не удалась")
    apk = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
    if not apk.exists():
        raise SystemExit(f"APK не найден: {apk}")
    return apk


def publish(skip_build: bool, apk_path: Path | None) -> int:
    load_env()
    ensure_remote()
    code, name = load_version()
    if skip_build:
        apk = apk_path or (ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk")
        if not apk.exists():
            raise SystemExit(f"нет APK ({apk}); уберите --skip-build или укажите --apk")
    else:
        apk = assemble_debug_apk()
        if apk_path:
            apk = apk_path
    url = upload_apk(apk, name)
    links = load_links()
    links.update(list_drive_links())
    links[name] = url
    save_links(links)
    releases = load_changelog().get("releases") or []
    replace_doc(render_doc(releases, links), render_html(releases, links))
    print(f"опубликовано {name} ({code})")
    print(f"APK: {url}")
    print(f"Документ: {DOC_URL}")
    return 0


def main() -> int:
    _configure_stdio()
    parser = argparse.ArgumentParser(
        description="Собрать APK, загрузить на Google Drive и обновить Google Doc",
    )
    parser.add_argument("--auth", action="store_true", help="Только настроить rclone / Google Drive")
    parser.add_argument("--skip-build", action="store_true", help="Не собирать APK, взять уже готовый")
    parser.add_argument("--apk", type=Path, help="Путь к APK (по умолчанию debug assemble)")
    parser.add_argument("--doc-only", action="store_true", help="Только пересобрать Google Doc из changelog и ссылок")
    args = parser.parse_args()
    load_env()

    if args.auth:
        ensure_remote()
        ensure_folder()
        print(f"готово. Документ: {DOC_URL}")
        return 0
    if args.doc_only:
        ensure_remote()
        links = load_links()
        links.update(list_drive_links())
        save_links(links)
        releases = load_changelog().get("releases") or []
        replace_doc(render_doc(releases, links), render_html(releases, links))
        print(f"документ обновлён: {DOC_URL}")
        return 0
    return publish(args.skip_build, args.apk)


if __name__ == "__main__":
    raise SystemExit(main())
