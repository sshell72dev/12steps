from __future__ import annotations

import os
import ssl
import subprocess
import sys
from ftplib import FTP, FTP_TLS, error_perm
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parent

SKIP_NAMES = {".git", "__pycache__", ".venv", "wheels", "deploy_ftp.py"}
SKIP_SUFFIXES = {".pyc"}
TEXT_SUFFIXES = {".py", ".html", ".txt", ".env", ".example", ".gitkeep", ".md", ".json"}

HOST_CANDIDATES = [
    os.getenv("FTP_HOST", "12stepsapp.luch-rehab.ru"),
    "d022773e8c10.hosting.myjino.ru",
    "j46510238.myjino.ru",
]


def load_env() -> None:
    env_path = ROOT / ".env"
    if not env_path.exists():
        return
    for raw in env_path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


def connect(host: str, user: str, password: str):
    errors = []
    try:
        ftp = FTP_TLS()
        ftp.connect(host, 21, timeout=25)
        ftp.auth()
        ftp.prot_p()
        ftp.login(user, password)
        ftp.set_pasv(True)
        return ftp, f"ftpes://{host}"
    except Exception as exc:
        errors.append(f"FTPES {host}: {exc}")
    try:
        ftp = FTP()
        ftp.connect(host, 21, timeout=25)
        ftp.login(user, password)
        ftp.set_pasv(True)
        return ftp, f"ftp://{host}"
    except Exception as exc:
        errors.append(f"FTP {host}: {exc}")
    raise RuntimeError(" | ".join(errors))


def ensure_dir(ftp, path: str) -> None:
    parts = [p for p in path.replace("\\", "/").split("/") if p]
    for part in parts:
        try:
            ftp.cwd(part)
        except error_perm:
            ftp.mkd(part)
            ftp.cwd(part)


def upload_file(ftp, local: Path, remote_name: str) -> None:
    with local.open("rb") as fh:
        ftp.storbinary(f"STOR {remote_name}", fh)


def walk_upload(ftp, local_dir: Path, remote_dir: str) -> list[str]:
    uploaded: list[str] = []
    cwd = ftp.pwd()
    ensure_dir(ftp, remote_dir)
    ftp.cwd(cwd)

    for dirpath, dirnames, filenames in os.walk(local_dir):
        dirnames[:] = [
            d
            for d in dirnames
            if d not in SKIP_NAMES and not d.endswith(".dist-info")
        ]
        rel = Path(dirpath).relative_to(local_dir)
        parts = [] if str(rel) == "." else list(rel.parts)
        ftp.cwd(cwd)
        ensure_dir(ftp, "/".join([remote_dir.rstrip("/"), *parts]) if parts else remote_dir)
        for name in filenames:
            if name in SKIP_NAMES or Path(name).suffix in SKIP_SUFFIXES:
                continue
            local = Path(dirpath) / name
            upload_file(ftp, local, name)
            uploaded.append(str(local.relative_to(local_dir)))
    ftp.cwd(cwd)
    return uploaded


def bump_release_version() -> None:
    if os.getenv("DEPLOY_SKIP_BUMP") == "1":
        print("skip version bump (DEPLOY_SKIP_BUMP=1)")
        return
    script = PROJECT_ROOT / "tools" / "bump_version.py"
    if not script.exists():
        raise SystemExit(f"missing bump script: {script}")
    notes = os.getenv("DEPLOY_NOTES", "").strip() or None
    cmd = [sys.executable, str(script)]
    if notes:
        cmd.extend(["--notes", notes])
    print("bumping release version...")
    subprocess.run(cmd, check=True, cwd=PROJECT_ROOT)


def main() -> None:
    load_env()
    bump_release_version()
    user = os.getenv("FTP_USER", "")
    password = os.getenv("FTP_PASSWORD", "")
    remote = os.getenv("FTP_REMOTE_DIR", "/domains/12stepsapp.luch-rehab.ru")
    if not user or not password:
        raise SystemExit("FTP_USER / FTP_PASSWORD are not set")

    last_error = None
    ftp = None
    used = ""
    for host in HOST_CANDIDATES:
        if not host:
            continue
        try:
            ftp, used = connect(host, user, password)
            break
        except Exception as exc:
            last_error = exc
    if ftp is None:
        raise SystemExit(f"FTP connect failed: {last_error}")

    print("connected", used, "pwd=", ftp.pwd())
    uploaded = walk_upload(ftp, ROOT, remote)
    ftp.cwd("/")
    ensure_dir(ftp, remote.rstrip("/") + "/tmp")
    from io import BytesIO

    ftp.storbinary("STOR restart.txt", BytesIO(b"restart\n"))
    print("uploaded", len(uploaded), "files")
    for item in uploaded:
        print(" ", item)
    ftp.quit()


if __name__ == "__main__":
    main()
