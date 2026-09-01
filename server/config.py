from __future__ import annotations

import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MODELS = (
    ("deepseek-v4-flash", "deepseek-v4-flash (быстрая)"),
    ("deepseek-v4-pro", "deepseek-v4-pro (аналитическая)"),
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
