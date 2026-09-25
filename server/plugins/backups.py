"""Резервные копии данных приложения: аккаунт по почте, приём и отдача архива.

Архив приходит по HTTPS и шифруется на сервере (AES-256-GCM, ключ BACKUP_ENCRYPTION_KEY),
поэтому на диске хостинга данные не лежат открытым текстом.
На аккаунт хранится ОДИН слот: новая выгрузка заменяет предыдущий файл атомарно.
"""
from __future__ import annotations

import hashlib
import hmac
import io
import os
import re
import secrets
import smtplib
from datetime import datetime, timedelta, timezone
from email.message import EmailMessage
from pathlib import Path

from flask import jsonify, redirect, render_template, request, send_file, url_for

import config
import db

BACKUPS_DIR = Path(__file__).resolve().parent.parent / "backups"
EMAIL_RE = re.compile(r"^[^@\s]{1,64}@[^@\s]{1,120}\.[A-Za-z]{2,10}$")
MAGIC = b"STB1"
IV_LEN = 12
TAG_LEN = 16
CODE_TTL_MIN = 15
SESSION_TTL_DAYS = 180
UPLOAD_COOLDOWN_SEC = 600
MAX_CODE_ATTEMPTS = 5


def _now() -> str:
    return db.utc_now()


def _parse(value: str | None) -> datetime | None:
    raw = (value or "").strip()
    if not raw:
        return None
    try:
        return datetime.strptime(raw, "%Y-%m-%d %H:%M:%S").replace(tzinfo=timezone.utc)
    except ValueError:
        return None


def _hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def _secret_key() -> bytes:
    """Ключ шифрования: 32 байта из BACKUP_ENCRYPTION_KEY (любая строка)."""
    raw = (config.BACKUP_ENCRYPTION_KEY or "").strip()
    return hashlib.sha256(raw.encode("utf-8")).digest() if raw else b""


def encryption_ready() -> bool:
    return bool(_secret_key())


def _aesgcm():
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM

    return AESGCM(_secret_key())


def encrypt_blob(raw: bytes) -> bytes:
    iv = os.urandom(IV_LEN)
    sealed = _aesgcm().encrypt(iv, raw, MAGIC)
    return MAGIC + iv + sealed


def decrypt_blob(blob: bytes) -> bytes:
    if not blob.startswith(MAGIC):
        raise ValueError("bad envelope")
    iv = blob[len(MAGIC): len(MAGIC) + IV_LEN]
    return _aesgcm().decrypt(iv, blob[len(MAGIC) + IV_LEN:], MAGIC)


def send_code(email: str, code: str) -> bool:
    """Отправка кода подтверждения. Без SMTP_* в .env возвращает False."""
    host = (config.SMTP_HOST or "").strip()
    if not host:
        return False
    msg = EmailMessage()
    msg["Subject"] = "Код для резервной копии 12 шагов"
    msg["From"] = config.SMTP_FROM or config.SMTP_USER or f"noreply@{config.DOMAIN}"
    msg["To"] = email
    msg.set_content(
        "Код подтверждения: {code}\n\n"
        "Он действует {ttl} минут. Введите его в приложении, чтобы включить "
        "сохранение копий на сервер.\n\n"
        "Если вы не запрашивали код, просто проигнорируйте письмо.".format(
            code=code, ttl=CODE_TTL_MIN
        )
    )
    try:
        if config.SMTP_SSL:
            with smtplib.SMTP_SSL(host, config.SMTP_PORT, timeout=20) as server:
                if config.SMTP_USER:
                    server.login(config.SMTP_USER, config.SMTP_PASSWORD)
                server.send_message(msg)
        else:
            with smtplib.SMTP(host, config.SMTP_PORT, timeout=20) as server:
                server.starttls()
                if config.SMTP_USER:
                    server.login(config.SMTP_USER, config.SMTP_PASSWORD)
                server.send_message(msg)
    except Exception:
        return False
    return True


def init_schema() -> None:
    BACKUPS_DIR.mkdir(parents=True, exist_ok=True)
    with db.cursor() as cur:
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS backup_accounts (
                `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                `email` VARCHAR(160) NOT NULL,
                `code_hash` VARCHAR(128) NOT NULL DEFAULT '',
                `code_expires_at` DATETIME NULL,
                `code_attempts` INT NOT NULL DEFAULT 0,
                `created_at` DATETIME NOT NULL,
                `updated_at` DATETIME NOT NULL,
                UNIQUE KEY `backup_accounts_email` (`email`)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS backup_sessions (
                `token_hash` CHAR(64) NOT NULL PRIMARY KEY,
                `account_id` BIGINT NOT NULL,
                `device_id` VARCHAR(64) NOT NULL DEFAULT '',
                `created_at` DATETIME NOT NULL,
                `expires_at` DATETIME NOT NULL,
                INDEX `idx_backup_sessions_account` (`account_id`)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS backup_slots (
                `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                `account_id` BIGINT NOT NULL,
                `email` VARCHAR(160) NOT NULL DEFAULT '',
                `file_name` VARCHAR(160) NOT NULL DEFAULT 'current.zip.enc',
                `file_size` BIGINT NOT NULL DEFAULT 0,
                `archive_size` BIGINT NOT NULL DEFAULT 0,
                `sha256` CHAR(64) NOT NULL DEFAULT '',
                `app_version` VARCHAR(32) NOT NULL DEFAULT '',
                `device_id` VARCHAR(64) NOT NULL DEFAULT '',
                `upload_count` INT NOT NULL DEFAULT 0,
                `uploaded_at` DATETIME NOT NULL,
                `downloaded_at` DATETIME NULL,
                UNIQUE KEY `backup_slots_account` (`account_id`),
                INDEX `idx_backup_slots_uploaded` (`uploaded_at`)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )


def account_by_email(email: str) -> dict | None:
    with db.cursor() as cur:
        cur.execute("SELECT * FROM backup_accounts WHERE email = %s", (email,))
        return cur.fetchone()


def upsert_account(email: str) -> int:
    now = _now()
    with db.cursor() as cur:
        cur.execute("SELECT id FROM backup_accounts WHERE email = %s", (email,))
        row = cur.fetchone()
        if row:
            cur.execute(
                "UPDATE backup_accounts SET updated_at = %s WHERE id = %s",
                (now, row["id"]),
            )
            return int(row["id"])
        cur.execute(
            "INSERT INTO backup_accounts (email, created_at, updated_at) VALUES (%s, %s, %s)",
            (email, now, now),
        )
        return int(cur.lastrowid)


def set_login_code(email: str, code: str) -> None:
    expires = (datetime.now(timezone.utc) + timedelta(minutes=CODE_TTL_MIN)).strftime(
        "%Y-%m-%d %H:%M:%S"
    )
    account_id = upsert_account(email)
    with db.cursor() as cur:
        cur.execute(
            """UPDATE backup_accounts
               SET code_hash = %s, code_expires_at = %s, code_attempts = 0, updated_at = %s
               WHERE id = %s""",
            (_hash(f"{email}:{code}"), expires, _now(), account_id),
        )


def verify_login_code(email: str, code: str) -> int | None:
    row = account_by_email(email)
    if not row:
        return None
    if int(row.get("code_attempts") or 0) >= MAX_CODE_ATTEMPTS:
        return None
    expires = _parse(row.get("code_expires_at"))
    if not expires or expires < datetime.now(timezone.utc):
        return None
    expected = (row.get("code_hash") or "").strip()
    if not expected or not hmac.compare_digest(expected, _hash(f"{email}:{code}")):
        with db.cursor() as cur:
            cur.execute(
                "UPDATE backup_accounts SET code_attempts = code_attempts + 1 WHERE id = %s",
                (row["id"],),
            )
        return None
    with db.cursor() as cur:
        cur.execute(
            """UPDATE backup_accounts
               SET code_hash = '', code_expires_at = NULL, code_attempts = 0, updated_at = %s
               WHERE id = %s""",
            (_now(), row["id"]),
        )
    return int(row["id"])


def create_session(account_id: int, device_id: str) -> str:
    token = secrets.token_urlsafe(32)
    expires = (datetime.now(timezone.utc) + timedelta(days=SESSION_TTL_DAYS)).strftime(
        "%Y-%m-%d %H:%M:%S"
    )
    with db.cursor() as cur:
        cur.execute(
            """INSERT INTO backup_sessions (token_hash, account_id, device_id, created_at, expires_at)
               VALUES (%s, %s, %s, %s, %s)""",
            (_hash(token), account_id, device_id[:64], _now(), expires),
        )
    return token


def account_by_token(token: str) -> dict | None:
    raw = (token or "").strip()
    if not raw:
        return None
    with db.cursor() as cur:
        cur.execute(
            """SELECT s.account_id, s.expires_at, a.email
               FROM backup_sessions s
               JOIN backup_accounts a ON a.id = s.account_id
               WHERE s.token_hash = %s""",
            (_hash(raw),),
        )
        row = cur.fetchone()
    if not row:
        return None
    expires = _parse(row.get("expires_at"))
    if not expires or expires < datetime.now(timezone.utc):
        return None
    return {"account_id": int(row["account_id"]), "email": row.get("email") or ""}


def get_slot(account_id: int) -> dict | None:
    with db.cursor() as cur:
        cur.execute("SELECT * FROM backup_slots WHERE account_id = %s", (account_id,))
        return cur.fetchone()


def list_slots() -> list[dict]:
    with db.cursor() as cur:
        cur.execute("SELECT * FROM backup_slots ORDER BY uploaded_at DESC")
        return list(cur.fetchall() or [])


def slot_path(account_id: int, file_name: str = "current.zip.enc") -> Path:
    folder = BACKUPS_DIR / str(int(account_id))
    folder.mkdir(parents=True, exist_ok=True)
    return folder / file_name


def save_slot(
    account_id: int, email: str, payload: bytes, app_version: str, device_id: str
) -> dict:
    """Шифрует архив и заменяет предыдущий файл слота атомарно."""
    sealed = encrypt_blob(payload)
    target = slot_path(account_id)
    tmp = folder_tmp(target)
    with open(tmp, "wb") as fh:
        fh.write(sealed)
        fh.flush()
        os.fsync(fh.fileno())
    os.replace(tmp, target)
    digest = hashlib.sha256(payload).hexdigest()
    now = _now()
    with db.cursor() as cur:
        cur.execute("SELECT id FROM backup_slots WHERE account_id = %s", (account_id,))
        row = cur.fetchone()
        if row:
            cur.execute(
                """UPDATE backup_slots
                   SET email = %s, file_name = %s, file_size = %s, archive_size = %s, sha256 = %s,
                       app_version = %s, device_id = %s, upload_count = upload_count + 1, uploaded_at = %s
                   WHERE id = %s""",
                (
                    email, target.name, len(sealed), len(payload), digest,
                    app_version[:32], device_id[:64], now, row["id"],
                ),
            )
        else:
            cur.execute(
                """INSERT INTO backup_slots
                   (account_id, email, file_name, file_size, archive_size, sha256,
                    app_version, device_id, upload_count, uploaded_at)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s, 1, %s)""",
                (
                    account_id, email, target.name, len(sealed), len(payload), digest,
                    app_version[:32], device_id[:64], now,
                ),
            )
    return get_slot(account_id) or {}


def folder_tmp(target: Path) -> Path:
    return target.parent / f".{target.name}.part"


def delete_slot(account_id: int, file_name: str = "current.zip.enc") -> None:
    with db.cursor() as cur:
        cur.execute("DELETE FROM backup_slots WHERE account_id = %s", (account_id,))
    try:
        slot_path(account_id, file_name).unlink()
    except FileNotFoundError:
        pass


def read_slot_payload(slot: dict) -> bytes:
    path = slot_path(int(slot["account_id"]), str(slot.get("file_name") or "current.zip.enc"))
    return decrypt_blob(path.read_bytes())


def mark_downloaded(account_id: int) -> None:
    with db.cursor() as cur:
        cur.execute(
            "UPDATE backup_slots SET downloaded_at = %s WHERE account_id = %s",
            (_now(), account_id),
        )


def _iso(value: str | None) -> str:
    dt = _parse(value)
    return dt.isoformat() if dt else ""


def _slot_public(slot: dict | None) -> dict | None:
    """Метаданные слота для приложения: без account_id и имён файлов."""
    if not slot:
        return None
    return {
        "size": int(slot.get("archive_size") or 0),
        "stored_size": int(slot.get("file_size") or 0),
        "app_version": slot.get("app_version") or "",
        "uploaded_at": _iso(slot.get("uploaded_at")),
        "downloaded_at": _iso(slot.get("downloaded_at")),
        "upload_count": int(slot.get("upload_count") or 0),
        "sha256": slot.get("sha256") or "",
    }


def _fail(code: str, status: int, **extra):
    payload = {"ok": False, "error": code}
    payload.update(extra)
    return jsonify(payload), status


def register(app, login_required, api_ok) -> None:
    """Подключение плагина к приложению Flask (как у messenger и voice)."""
    try:
        init_schema()
    except Exception:
        app.logger.exception("backup: не удалось создать схему")

    app.config.setdefault("MAX_CONTENT_LENGTH", int(config.MAX_BACKUP_BYTES) + 1024 * 1024)

    def _account():
        info = account_by_token(request.headers.get("X-Backup-Token") or "")
        if not info:
            return None, _fail("auth", 401)
        return info, None

    @app.post("/api/v1/backup/code")
    def api_backup_code():
        if not api_ok():
            return _fail("api", 401)
        if not encryption_ready():
            return _fail("disabled", 503)
        payload = request.get_json(silent=True) or {}
        email = str(payload.get("email") or "").strip().lower()
        if not EMAIL_RE.match(email):
            return _fail("email", 400)
        code = f"{secrets.randbelow(1000000):06d}"
        try:
            set_login_code(email, code)
        except Exception:
            app.logger.exception("backup: не удалось сохранить код")
            return _fail("server", 500)
        if not send_code(email, code):
            return _fail("mail", 503)
        return jsonify({"ok": True, "ttl_minutes": CODE_TTL_MIN})

    @app.post("/api/v1/backup/login")
    def api_backup_login():
        if not api_ok():
            return _fail("api", 401)
        payload = request.get_json(silent=True) or {}
        email = str(payload.get("email") or "").strip().lower()
        code = str(payload.get("code") or "").strip()
        device_id = str(payload.get("device_id") or "").strip()
        if not EMAIL_RE.match(email) or not code.isdigit():
            return _fail("email", 400)
        try:
            account_id = verify_login_code(email, code)
        except Exception:
            app.logger.exception("backup: вход не удался")
            return _fail("server", 500)
        if not account_id:
            return _fail("code", 403)
        return jsonify(
            {
                "ok": True,
                "token": create_session(account_id, device_id),
                "email": email,
                "slot": _slot_public(get_slot(account_id)),
            }
        )

    @app.get("/api/v1/backup/meta")
    def api_backup_meta():
        if not api_ok():
            return _fail("api", 401)
        info, err = _account()
        if err:
            return err
        return jsonify(
            {
                "ok": True,
                "email": info["email"],
                "slot": _slot_public(get_slot(info["account_id"])),
            }
        )

    @app.post("/api/v1/backup")
    def api_backup_upload():
        if not api_ok():
            return _fail("api", 401)
        info, err = _account()
        if err:
            return err
        if not encryption_ready():
            return _fail("disabled", 503)
        payload = request.get_data(cache=False)
        if not payload:
            return _fail("empty", 400)
        if len(payload) > int(config.MAX_BACKUP_BYTES):
            return _fail("too_large", 413, limit=int(config.MAX_BACKUP_BYTES))
        slot = get_slot(info["account_id"])
        uploaded = _parse((slot or {}).get("uploaded_at"))
        if slot and uploaded:
            elapsed = (datetime.now(timezone.utc) - uploaded).total_seconds()
            if elapsed < UPLOAD_COOLDOWN_SEC:
                return _fail("cooldown", 429, retry_after=UPLOAD_COOLDOWN_SEC)
        try:
            saved = save_slot(
                info["account_id"],
                info["email"],
                payload,
                request.headers.get("X-App-Version", ""),
                request.headers.get("X-Device-Id", ""),
            )
        except Exception:
            app.logger.exception("backup: не удалось сохранить архив")
            return _fail("server", 500)
        return jsonify({"ok": True, "slot": _slot_public(saved)})

    @app.get("/api/v1/backup/latest")
    def api_backup_latest():
        if not api_ok():
            return _fail("api", 401)
        info, err = _account()
        if err:
            return err
        slot = get_slot(info["account_id"])
        if not slot:
            return _fail("empty", 404)
        try:
            raw = read_slot_payload(slot)
        except Exception:
            app.logger.exception("backup: не удалось расшифровать архив")
            return _fail("server", 500)
        mark_downloaded(info["account_id"])
        stamp = datetime.now(timezone.utc).strftime("%Y-%m-%d")
        return send_file(
            io.BytesIO(raw),
            mimetype="application/zip",
            as_attachment=True,
            download_name=f"12steps-backup-{stamp}.zip",
        )

    @app.get("/admin/backups")
    @login_required
    def backups_admin():
        return render_template(
            "backups.html",
            slots=list_slots(),
            encryption=encryption_ready(),
            mail=bool((config.SMTP_HOST or "").strip()),
            max_mb=int(config.MAX_BACKUP_BYTES) // (1024 * 1024),
            notice="Файл удалён." if request.args.get("deleted") else "",
            warn=request.args.get("problem") or "",
        )

    @app.get("/admin/backups/<int:slot_id>/download")
    @login_required
    def backups_admin_download(slot_id: int):
        slot = next((s for s in list_slots() if int(s["id"]) == int(slot_id)), None)
        if not slot:
            return redirect(url_for("backups_admin", problem="Слот не найден."))
        try:
            raw = read_slot_payload(slot)
        except Exception:
            return redirect(url_for("backups_admin", problem="Файл повреждён или ключ не подходит."))
        stamp = str(slot.get("uploaded_at") or "").replace(":", "-").replace(" ", "_")
        return send_file(
            io.BytesIO(raw),
            mimetype="application/zip",
            as_attachment=True,
            download_name=f"backup-{slot_id}-{stamp}.zip",
        )

    @app.post("/admin/backups/<int:slot_id>/delete")
    @login_required
    def backups_admin_delete(slot_id: int):
        slot = next((s for s in list_slots() if int(s["id"]) == int(slot_id)), None)
        if slot:
            delete_slot(int(slot["account_id"]), str(slot.get("file_name") or "current.zip.enc"))
        return redirect(url_for("backups_admin", deleted=1))
