from __future__ import annotations

import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MODELS = (
    ("deepseek-v4-flash", "deepseek-v4-flash (быстрая)"),
)
DEFAULT_MODEL = MODELS[0][0]


def load_env(path: Path | None = None) -> None:
    env_path = path or ROOT / ".env"
    if not env_path.exists():
        return
    for raw in env_path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


load_env()


def getenv(name: str, default: str = "") -> str:
    return (os.getenv(name, default) or default).strip()


SECRET_KEY = getenv("SECRET_KEY") or "dev-secret-change-me"
ADMIN_USERNAME = getenv("ADMIN_USERNAME", "admin") or "admin"
ADMIN_PASSWORD = getenv("ADMIN_PASSWORD")
API_TOKEN = getenv("API_TOKEN")

DB_HOST = getenv("DB_HOST", "localhost") or "localhost"
DB_PORT = int(getenv("DB_PORT", "3306") or "3306")
DB_NAME = getenv("DB_NAME")
DB_USER = getenv("DB_USER")
DB_PASSWORD = getenv("DB_PASSWORD")

DEEPSEEK_BASE_URL = getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1").rstrip("/")
DOMAIN = "12stepsapp.luch-rehab.ru"

DEFAULT_MAX_BACKUP_BYTES = 50 * 1024 * 1024
DEFAULT_SMTP_PORT = 465
DEFAULT_BACKUP_UPLOAD_COOLDOWN_SEC = 600
DEFAULT_BACKUP_CODE_TTL_MIN = 15
DEFAULT_BACKUP_SESSION_TTL_DAYS = 180


# Значения по умолчанию для почты и копий; действующие значения задаются в админке
# и хранятся в app_settings (см. setting()).


def setting_key(name: str) -> str:
    """Ключ настройки в таблице app_settings."""
    return name.strip().lower()


def setting(name: str, default: str = "") -> str:
    """Действующее значение: админка (БД) → .env → default."""
    from_admin = ""
    try:
        import db

        from_admin = (db.get_setting(setting_key(name), "") or "").strip()
    except Exception:
        from_admin = ""
    return from_admin or getenv(name, default)


def setting_int(name: str, default: int) -> int:
    raw = setting(name, str(default))
    try:
        return int(float(raw))
    except (TypeError, ValueError):
        return int(default)


def setting_bool(name: str, default: bool) -> bool:
    raw = setting(name, "1" if default else "0").strip().lower()
    if raw in ("1", "true", "on", "yes", "да"):
        return True
    if raw in ("0", "false", "off", "no", "нет"):
        return False
    return bool(default)


def smtp_host() -> str:
    return setting("SMTP_HOST")


def smtp_port() -> int:
    return setting_int("SMTP_PORT", DEFAULT_SMTP_PORT)


def smtp_user() -> str:
    return setting("SMTP_USER")


def smtp_password() -> str:
    return setting("SMTP_PASSWORD")


def smtp_from() -> str:
    return setting("SMTP_FROM")


def smtp_ssl() -> bool:
    return setting_bool("SMTP_SSL", True)


def backup_encryption_key() -> str:
    """Ключ шифрования архивов. Пусто — приём копий выключен."""
    return setting("BACKUP_ENCRYPTION_KEY")


def max_backup_bytes() -> int:
    return max(1024, setting_int("MAX_BACKUP_BYTES", DEFAULT_MAX_BACKUP_BYTES))


def backup_upload_cooldown_sec() -> int:
    return max(0, setting_int("BACKUP_UPLOAD_COOLDOWN_SEC", DEFAULT_BACKUP_UPLOAD_COOLDOWN_SEC))


def backup_code_ttl_min() -> int:
    return max(1, setting_int("BACKUP_CODE_TTL_MIN", DEFAULT_BACKUP_CODE_TTL_MIN))


def backup_session_ttl_days() -> int:
    return max(1, setting_int("BACKUP_SESSION_TTL_DAYS", DEFAULT_BACKUP_SESSION_TTL_DAYS))
