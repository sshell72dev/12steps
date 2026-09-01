from __future__ import annotations

from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from typing import Any, Iterator

import pymysql
from pymysql.cursors import DictCursor

import config


def utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")


def connect():
    return pymysql.connect(
        host=config.DB_HOST,
        port=config.DB_PORT,
        user=config.DB_USER,
        password=config.DB_PASSWORD,
        database=config.DB_NAME,
        charset="utf8mb4",
        cursorclass=DictCursor,
        autocommit=False,
    )


@contextmanager
def cursor() -> Iterator[Any]:
    conn = connect()
    try:
        cur = conn.cursor()
        try:
            yield cur
            conn.commit()
        finally:
            cur.close()
    finally:
        conn.close()


def init_schema() -> None:
    with cursor() as cur:
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS app_settings (
                `key` VARCHAR(64) NOT NULL PRIMARY KEY,
                `value` TEXT NOT NULL,
                updated_at DATETIME NOT NULL
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS notes (
                `id` VARCHAR(128) NOT NULL PRIMARY KEY,
                `title` VARCHAR(255) NOT NULL DEFAULT '',
                `body` MEDIUMTEXT NOT NULL,
                `mode` VARCHAR(16) NOT NULL DEFAULT 'collapsed',
                `show_title` TINYINT(1) NOT NULL DEFAULT 0,
                `updated_at` DATETIME NOT NULL
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )
        cur.execute(
            """
            SELECT COUNT(*) AS c FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'notes'
              AND COLUMN_NAME = 'show_title'
            """
        )
        if int((cur.fetchone() or {}).get("c") or 0) == 0:
            cur.execute(
                "ALTER TABLE notes ADD COLUMN `show_title` TINYINT(1) NOT NULL DEFAULT 0"
            )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS analysis_catalog (
                `k` VARCHAR(32) NOT NULL PRIMARY KEY,
                `body` MEDIUMTEXT NOT NULL,
                `updated_at` DATETIME NOT NULL
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS support_tickets (
                `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                `user_id` VARCHAR(64) NOT NULL,
                `user_name` VARCHAR(80) NOT NULL DEFAULT '',
                `screen` VARCHAR(160) NOT NULL DEFAULT '',
                `screen_route` VARCHAR(160) NOT NULL DEFAULT '',
                `created_at` DATETIME NOT NULL,
                `updated_at` DATETIME NOT NULL,
                `admin_read` TINYINT(1) NOT NULL DEFAULT 0,
                `user_read` TINYINT(1) NOT NULL DEFAULT 1,
                `status` VARCHAR(24) NOT NULL DEFAULT 'new',
                `belonging` VARCHAR(24) NOT NULL DEFAULT 'screen',
                `kind` VARCHAR(16) NOT NULL DEFAULT 'bug',
                `admin_source` VARCHAR(64) NOT NULL DEFAULT '',
                INDEX `idx_support_user` (`user_id`),
                INDEX `idx_support_updated` (`updated_at`),
                INDEX `idx_support_status` (`status`)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )
        cur.execute(
            """
            SELECT COUNT(*) AS c FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'support_tickets'
              AND COLUMN_NAME = 'status'
            """
        )
        if int((cur.fetchone() or {}).get("c") or 0) == 0:
            cur.execute(
                """
                ALTER TABLE support_tickets
                ADD COLUMN `status` VARCHAR(24) NOT NULL DEFAULT 'new',
                ADD INDEX `idx_support_status` (`status`)
                """
            )
        for col, ddl in (
            (
                "belonging",
                "ALTER TABLE support_tickets ADD COLUMN `belonging` VARCHAR(24) NOT NULL DEFAULT 'screen'",
            ),
            (
                "kind",
                "ALTER TABLE support_tickets ADD COLUMN `kind` VARCHAR(16) NOT NULL DEFAULT 'bug'",
            ),
            (
                "admin_source",
                "ALTER TABLE support_tickets ADD COLUMN `admin_source` VARCHAR(64) NOT NULL DEFAULT ''",
            ),
        ):
            cur.execute(
                """
                SELECT COUNT(*) AS c FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'support_tickets'
                  AND COLUMN_NAME = %s
                """,
                (col,),
            )
            if int((cur.fetchone() or {}).get("c") or 0) == 0:
                cur.execute(ddl)
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS support_messages (
                `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                `ticket_id` BIGINT NOT NULL,
                `author` VARCHAR(16) NOT NULL,
                `body` MEDIUMTEXT NOT NULL,
                `created_at` DATETIME NOT NULL,
                `admin_read` TINYINT(1) NOT NULL DEFAULT 0,
                `edited_at` DATETIME NULL,
                INDEX `idx_support_ticket` (`ticket_id`)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )
        cur.execute(
            """
            SELECT COUNT(*) AS c FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'support_messages'
              AND COLUMN_NAME = 'admin_read'
            """
        )
        if int((cur.fetchone() or {}).get("c") or 0) == 0:
            cur.execute(
                """
                ALTER TABLE support_messages
                ADD COLUMN `admin_read` TINYINT(1) NOT NULL DEFAULT 0
                """
            )
            cur.execute(
                """
                UPDATE support_messages
                SET `admin_read` = 1
                WHERE `author` IN ('admin', 'system')
                """
            )
        cur.execute(
            """
            SELECT COUNT(*) AS c FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'support_messages'
              AND COLUMN_NAME = 'edited_at'
            """
        )
        if int((cur.fetchone() or {}).get("c") or 0) == 0:
            cur.execute(
                "ALTER TABLE support_messages ADD COLUMN `edited_at` DATETIME NULL"
            )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS premium_payments (
                `payment_id` VARCHAR(64) NOT NULL PRIMARY KEY,
                `device_id` VARCHAR(64) NOT NULL,
                `amount` VARCHAR(32) NOT NULL DEFAULT '',
                `currency` VARCHAR(8) NOT NULL DEFAULT 'RUB',
                `status` VARCHAR(32) NOT NULL DEFAULT 'pending',
                `confirmation_url` TEXT NULL,
                `created_at` DATETIME NOT NULL,
                `paid_at` DATETIME NULL,
                `raw_json` MEDIUMTEXT NULL,
                INDEX `idx_premium_pay_device` (`device_id`),
                INDEX `idx_premium_pay_created` (`created_at`),
                INDEX `idx_premium_pay_status` (`status`)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS premium_entitlements (
                `device_id` VARCHAR(64) NOT NULL PRIMARY KEY,
                `expires_at` DATETIME NOT NULL,
                `updated_at` DATETIME NOT NULL,
                `last_payment_id` VARCHAR(64) NOT NULL DEFAULT ''
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )


SUPPORT_STATUSES = ("new", "in_progress", "done")

SUPPORT_STATUS_LABELS = {
    "new": "Новое",
    "in_progress": "В разработке",
    "done": "Обработано",
}

SUPPORT_BELONGINGS = (
    "screen",
    "general",
    "family",
    "report",
    "idea_window",
    "cover",
    "life_idea",
    "life_note",
    "life_calendar",
)

SUPPORT_BELONGING_LABELS = {
    "screen": "Этот экран",
    "general": "Общее",
    "family": "Все подобные экраны",
    "report": "Сообщение об ошибке",
    "idea_window": "Предложить идею",
    "cover": "Обложка",
    "life_idea": "Идея",
    "life_note": "Заметки",
    "life_calendar": "Календарь",
}

SUPPORT_TOPICS = ("life_idea", "life_note", "life_calendar")

SUPPORT_KINDS = ("bug", "idea")

SUPPORT_KIND_LABELS = {
    "bug": "Сообщения об ошибке",
    "idea": "Идеи",
}

ADMIN_SOURCE_PAGES = {
    "settings": "Админка · ИИ",
    "notes": "Админка · Подсказки",
    "analyses": "Админка · Самоанализ",
    "voice": "Админка · Голос",
    "support": "Админка · Ошибки",
}

SUPPORT_FAMILIES = {
    "home": "Главный экран",
    "profile": "Анкета",
    "psych": "Электронный психолог",
    "analysis": "Самоанализ",
    "journal": "Дневник 12 шагов",
    "life": "Цели и заметки",
    "resentments": "Обиды / 4 шаг",
    "admin": "Админка",
    "support": "Сообщения об ошибке",
    "cover": "Обложка",
}


def _normalize_belonging(value: str | None) -> str:
    v = (value or "screen").strip().lower()
    if v in SUPPORT_BELONGINGS:
        return v
    return "screen"


def _normalize_kind(value: str | None) -> str:
    v = (value or "bug").strip().lower()
    if v in SUPPORT_KINDS:
        return v
    return "bug"


def support_family_key(route: str | None) -> str:
    r = (route or "").strip().lower()
    if not r or r == "home":
        return "home"
    if r.startswith("admin/") or r in ADMIN_SOURCE_PAGES:
        return "admin"
    if r.startswith("support"):
        return "support"
    if r in {"cover", "lock"} or r.startswith("lock"):
        return "cover"
    if r == "profile" or r.startswith("profile"):
        return "profile"
    if r.startswith("psych"):
        return "psych"
    if r.startswith("analysis"):
        return "analysis"
    if r.startswith("journal"):
        return "journal"
    if r.startswith("life"):
        return "life"
    if r.startswith("messenger"):
        return "messenger"
    if (
        r.startswith("edit")
        or r.startswith("situation")
        or r.startswith("assistant")
        or r in {"list", "guide", "categories", "step4"}
        or r.startswith("step")
        or r.startswith("soon")
    ):
        return "resentments"
    return "home"


def resolve_support_scope(
    belonging: str | None,
    screen: str,
    screen_route: str,
) -> tuple[str, str, str]:
    mode = _normalize_belonging(belonging)
    route = (screen_route or "").strip()[:160]
    label = (screen or "").strip()[:160]
    if mode == "general":
        return mode, "Общее", "general"
    if mode == "report":
        return mode, "Сообщить об ошибке", "support/report"
    if mode == "idea_window":
        return mode, "Предложить идею", "support/idea"
    if mode == "cover":
        return mode, "Обложка", "cover"
    if mode == "life_idea":
        return mode, "Идеи", "life/idea"
    if mode == "life_note":
        return mode, "Заметки", "life/note"
    if mode == "life_calendar":
        return mode, "Календарь", "life/event"
    if mode == "family":
        key = support_family_key(route)
        family_label = SUPPORT_FAMILIES.get(key, "Похожие экраны")
        return mode, f"{family_label} (все экраны)", f"family:{key}"
    if not label:
        label = "Экран приложения"
    return mode, label[:160], route[:160] or "screen"


def support_topic_key(belonging: str | None, screen_route: str | None) -> str | None:
    mode = (belonging or "").strip().lower()
    if mode in SUPPORT_TOPICS:
        return mode
    route = (screen_route or "").strip().lower()
    if route.startswith("life/idea"):
        return "life_idea"
    if route.startswith("life/note"):
        return "life_note"
    if route.startswith("life/event") or route.startswith("life/calendar"):
        return "life_calendar"
    return None


def support_topic_counts() -> dict[str, int]:
    counts = {key: 0 for key in SUPPORT_TOPICS}
    with cursor() as cur:
        cur.execute(
            """
            SELECT `belonging`, `screen_route`, COUNT(*) AS c
            FROM support_tickets
            GROUP BY `belonging`, `screen_route`
            """
        )
        rows = cur.fetchall() or []
    for row in rows:
        key = support_topic_key(str(row.get("belonging") or ""), str(row.get("screen_route") or ""))
        if key:
            try:
                counts[key] += int(row.get("c") or 0)
            except (TypeError, ValueError):
                pass
    return counts


def get_setting(key: str, default: str = "") -> str:
    with cursor() as cur:
        cur.execute("SELECT `value` FROM app_settings WHERE `key` = %s", (key,))
        row = cur.fetchone()
    if not row:
        return default
    return str(row["value"] or default)


def set_setting(key: str, value: str) -> None:
    with cursor() as cur:
        cur.execute(
            """
            INSERT INTO app_settings (`key`, `value`, updated_at)
            VALUES (%s, %s, %s)
            ON DUPLICATE KEY UPDATE
                `value` = VALUES(`value`),
                updated_at = VALUES(updated_at)
            """,
            (key, value, utc_now()),
        )


def all_settings() -> dict[str, str]:
    with cursor() as cur:
        cur.execute("SELECT `key`, `value` FROM app_settings")
        rows = cur.fetchall()
    return {str(row["key"]): str(row["value"] or "") for row in rows}


NOTE_MODES = ("popup", "collapsed", "expanded")


def _note_dict(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": str(row["id"]),
        "title": str(row.get("title") or ""),
        "text": str(row.get("body") or row.get("text") or ""),
        "mode": str(row.get("mode") or "collapsed"),
        "show_title": bool(int(row.get("show_title") or 0)),
        "updated_at": str(row.get("updated_at") or ""),
    }


def list_notes(since: str = "") -> list[dict[str, Any]]:
    with cursor() as cur:
        if since:
            cur.execute(
                """
                SELECT `id`, `title`, `body`, `mode`, `show_title`, `updated_at`
                FROM notes
                WHERE `updated_at` > %s
                ORDER BY `updated_at` ASC
                """,
                (since,),
            )
        else:
            cur.execute(
                """
                SELECT `id`, `title`, `body`, `mode`, `show_title`, `updated_at`
                FROM notes
                ORDER BY `updated_at` DESC
                """
            )
        rows = cur.fetchall() or []
    return [_note_dict(row) for row in rows]


def get_note(note_id: str) -> dict[str, Any] | None:
    with cursor() as cur:
        cur.execute(
            """
            SELECT `id`, `title`, `body`, `mode`, `show_title`, `updated_at`
            FROM notes WHERE `id` = %s
            """,
            (note_id,),
        )
        row = cur.fetchone()
    if not row:
        return None
    return _note_dict(row)


def upsert_note(
    note_id: str,
    title: str,
    body: str,
    mode: str,
    show_title: bool = False,
) -> dict[str, Any]:
    if mode not in NOTE_MODES:
        mode = "collapsed"
    note_id = (note_id or "").strip()[:128]
    title = (title or "").strip()[:255]
    body = body or ""
    show_title_i = 1 if show_title else 0
    now = utc_now()
    with cursor() as cur:
        cur.execute(
            """
            INSERT INTO notes (`id`, `title`, `body`, `mode`, `show_title`, `updated_at`)
            VALUES (%s, %s, %s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE
                `title` = VALUES(`title`),
                `body` = VALUES(`body`),
                `mode` = VALUES(`mode`),
                `show_title` = VALUES(`show_title`),
                `updated_at` = VALUES(`updated_at`)
            """,
            (note_id, title, body, mode, show_title_i, now),
        )
    return {
        "id": note_id,
        "title": title,
        "text": body,
        "mode": mode,
        "show_title": bool(show_title_i),
        "updated_at": now,
    }


def get_analysis_catalog() -> dict[str, Any] | None:
    with cursor() as cur:
        cur.execute(
            "SELECT `body`, `updated_at` FROM analysis_catalog WHERE `k` = %s",
            ("default",),
        )
        row = cur.fetchone()
    if not row:
        return None
    return {
        "body": str(row.get("body") or ""),
        "updated_at": str(row.get("updated_at") or ""),
    }


def set_analysis_catalog(body: str) -> str:
    now = utc_now()
    with cursor() as cur:
        cur.execute(
            """
            INSERT INTO analysis_catalog (`k`, `body`, `updated_at`)
            VALUES (%s, %s, %s)
            ON DUPLICATE KEY UPDATE
                `body` = VALUES(`body`),
                `updated_at` = VALUES(`updated_at`)
            """,
            ("default", body, now),
        )
    return now


def _normalize_status(status: str | None) -> str:
    value = (status or "new").strip().lower()
    if value in SUPPORT_STATUSES:
        return value
    return "new"


def _clip_preview(text: str, limit: int = 120) -> str:
    compact = " ".join((text or "").split())
    if len(compact) <= limit:
        return compact
    return compact[:limit].rstrip() + "…"


def _ticket_dict(row: dict[str, Any], messages: list[dict[str, Any]] | None = None) -> dict[str, Any]:
    status = _normalize_status(str(row.get("status") or "new"))
    belonging = _normalize_belonging(str(row.get("belonging") or "screen"))
    kind = _normalize_kind(str(row.get("kind") or "bug"))
    admin_source = str(row.get("admin_source") or "")
    first_raw = str(row.get("first_preview") or "")
    last_raw = str(row.get("last_preview") or "")
    try:
        message_count = int(row.get("message_count") or 0)
    except (TypeError, ValueError):
        message_count = 0
    if messages is not None:
        message_count = len(messages)
        first_raw = messages[0]["body"] if messages else ""
        last_raw = messages[-1]["body"] if len(messages) > 1 else ""
    preview = str(row.get("preview") or last_raw or first_raw)
    first_preview = _clip_preview(first_raw or preview)
    last_preview = _clip_preview(last_raw) if message_count > 1 else ""
    data = {
        "id": int(row["id"]),
        "user_id": str(row.get("user_id") or ""),
        "user_name": str(row.get("user_name") or ""),
        "screen": str(row.get("screen") or ""),
        "screen_route": str(row.get("screen_route") or ""),
        "created_at": str(row.get("created_at") or ""),
        "updated_at": str(row.get("updated_at") or ""),
        "admin_read": bool(int(row.get("admin_read") or 0)),
        "user_read": bool(int(row.get("user_read") or 0)),
        "status": status,
        "status_label": SUPPORT_STATUS_LABELS[status],
        "belonging": belonging,
        "belonging_label": SUPPORT_BELONGING_LABELS.get(belonging, belonging),
        "kind": kind,
        "kind_label": SUPPORT_KIND_LABELS.get(kind, kind),
        "admin_source": admin_source,
        "admin_source_label": ADMIN_SOURCE_PAGES.get(admin_source, admin_source),
        "preview": _clip_preview(preview, 240),
        "first_preview": first_preview,
        "last_preview": last_preview,
        "message_count": message_count,
    }
    if messages is not None:
        data["messages"] = messages
    return data


def _message_dict(row: dict[str, Any]) -> dict[str, Any]:
    author = str(row.get("author") or "")
    # Admin/system messages are always treated as read for the admin UI.
    if author in {"admin", "system"}:
        admin_read = True
    else:
        admin_read = bool(int(row.get("admin_read") or 0))
    edited_at = str(row.get("edited_at") or "")
    return {
        "id": int(row["id"]),
        "author": author,
        "body": str(row.get("body") or ""),
        "created_at": str(row.get("created_at") or ""),
        "edited_at": edited_at,
        "edited": bool(edited_at),
        "admin_read": admin_read,
    }


def _message_admin_read_value(author: str) -> int:
    return 0 if author == "user" else 1


def _sync_ticket_admin_read(cur, ticket_id: int) -> None:
    cur.execute(
        """
        SELECT COUNT(*) AS c FROM support_messages
        WHERE `ticket_id` = %s AND `author` = 'user' AND `admin_read` = 0
        """,
        (ticket_id,),
    )
    unread = int((cur.fetchone() or {}).get("c") or 0)
    cur.execute(
        "UPDATE support_tickets SET `admin_read` = %s WHERE `id` = %s",
        (0 if unread else 1, ticket_id),
    )


def _mark_user_messages_admin_read(cur, ticket_id: int) -> None:
    cur.execute(
        """
        UPDATE support_messages
        SET `admin_read` = 1
        WHERE `ticket_id` = %s AND `author` = 'user'
        """,
        (ticket_id,),
    )
    _sync_ticket_admin_read(cur, ticket_id)


def create_support_ticket(
    user_id: str,
    user_name: str,
    screen: str,
    screen_route: str,
    body: str,
    *,
    belonging: str = "screen",
    kind: str = "bug",
    admin_source: str = "",
    author: str = "user",
) -> dict[str, Any]:
    user_id = (user_id or "").strip()[:64]
    user_name = (user_name or "").strip()[:80]
    body = (body or "").strip()
    author = (author or "user").strip().lower()
    if author not in {"user", "admin"}:
        author = "user"
    admin_source = (admin_source or "").strip()[:64]
    if admin_source and admin_source not in ADMIN_SOURCE_PAGES:
        admin_source = admin_source[:64]
    kind = _normalize_kind(kind)
    belonging, screen, screen_route = resolve_support_scope(belonging, screen, screen_route)
    if not user_id or not body:
        raise ValueError("required")
    now = utc_now()
    admin_read = 1 if author == "admin" else 0
    user_read = 0 if author == "admin" else 1
    with cursor() as cur:
        cur.execute(
            """
            INSERT INTO support_tickets
                (`user_id`, `user_name`, `screen`, `screen_route`, `created_at`, `updated_at`,
                 `admin_read`, `user_read`, `status`, `belonging`, `kind`, `admin_source`)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, 'new', %s, %s, %s)
            """,
            (
                user_id,
                user_name,
                screen,
                screen_route,
                now,
                now,
                admin_read,
                user_read,
                belonging,
                kind,
                admin_source,
            ),
        )
        ticket_id = int(cur.lastrowid)
        cur.execute(
            """
            INSERT INTO support_messages (`ticket_id`, `author`, `body`, `created_at`, `admin_read`)
            VALUES (%s, %s, %s, %s, %s)
            """,
            (ticket_id, author, body, now, _message_admin_read_value(author)),
        )
        if admin_source:
            source_label = ADMIN_SOURCE_PAGES.get(admin_source, admin_source)
            cur.execute(
                """
                INSERT INTO support_messages (`ticket_id`, `author`, `body`, `created_at`, `admin_read`)
                VALUES (%s, 'system', %s, %s, 1)
                """,
                (ticket_id, f"Источник в админке: {source_label}", now),
            )
    return get_support_ticket(ticket_id) or {}


def add_support_message(ticket_id: int, author: str, body: str) -> dict[str, Any] | None:
    body = (body or "").strip()
    if author not in {"user", "admin", "system"} or not body:
        raise ValueError("required")
    now = utc_now()
    with cursor() as cur:
        cur.execute("SELECT `id`, `status` FROM support_tickets WHERE `id` = %s", (ticket_id,))
        row = cur.fetchone()
        if not row:
            return None
        cur.execute(
            """
            INSERT INTO support_messages (`ticket_id`, `author`, `body`, `created_at`, `admin_read`)
            VALUES (%s, %s, %s, %s, %s)
            """,
            (ticket_id, author, body, now, _message_admin_read_value(author)),
        )
        if author == "admin":
            status = _normalize_status(str(row.get("status") or "new"))
            # First admin reply moves ticket into work unless already done.
            next_status = "in_progress" if status == "new" else status
            _mark_user_messages_admin_read(cur, ticket_id)
            cur.execute(
                """
                UPDATE support_tickets
                SET `updated_at` = %s, `user_read` = 0, `admin_read` = 1, `status` = %s
                WHERE `id` = %s
                """,
                (now, next_status, ticket_id),
            )
            if next_status != status:
                label = SUPPORT_STATUS_LABELS[next_status]
                cur.execute(
                    """
                    INSERT INTO support_messages (`ticket_id`, `author`, `body`, `created_at`, `admin_read`)
                    VALUES (%s, 'system', %s, %s, 1)
                    """,
                    (ticket_id, f"Статус обращения изменён: {label}", now),
                )
        elif author == "system":
            cur.execute(
                """
                UPDATE support_tickets
                SET `updated_at` = %s, `user_read` = 0
                WHERE `id` = %s
                """,
                (now, ticket_id),
            )
        else:
            cur.execute(
                """
                UPDATE support_tickets
                SET `updated_at` = %s, `admin_read` = 0, `user_read` = 1
                WHERE `id` = %s
                """,
                (now, ticket_id),
            )
    return get_support_ticket(ticket_id)


def set_support_status(ticket_id: int, status: str) -> dict[str, Any] | None:
    status = _normalize_status(status)
    existing = get_support_ticket(ticket_id)
    if not existing:
        return None
    if existing.get("status") == status:
        return existing
    now = utc_now()
    label = SUPPORT_STATUS_LABELS[status]
    with cursor() as cur:
        if status == "done":
            _mark_user_messages_admin_read(cur, ticket_id)
        cur.execute(
            """
            UPDATE support_tickets
            SET `status` = %s, `updated_at` = %s, `user_read` = 0
            WHERE `id` = %s
            """,
            (status, now, ticket_id),
        )
        if cur.rowcount == 0:
            return None
        if status == "done":
            cur.execute(
                "UPDATE support_tickets SET `admin_read` = 1 WHERE `id` = %s",
                (ticket_id,),
            )
        else:
            _sync_ticket_admin_read(cur, ticket_id)
        cur.execute(
            """
            INSERT INTO support_messages (`ticket_id`, `author`, `body`, `created_at`, `admin_read`)
            VALUES (%s, 'system', %s, %s, 1)
            """,
            (ticket_id, f"Статус обращения изменён: {label}", now),
        )
    return get_support_ticket(ticket_id)


def mark_support_message_admin_read(message_id: int) -> dict[str, Any] | None:
    """Mark one user message as read; move ticket new → in_progress."""
    with cursor() as cur:
        cur.execute(
            """
            SELECT m.`id`, m.`ticket_id`, m.`author`, t.`status`
            FROM support_messages m
            JOIN support_tickets t ON t.`id` = m.`ticket_id`
            WHERE m.`id` = %s
            """,
            (message_id,),
        )
        row = cur.fetchone()
        if not row:
            return None
        ticket_id = int(row["ticket_id"])
        author = str(row.get("author") or "")
        if author == "user":
            cur.execute(
                "UPDATE support_messages SET `admin_read` = 1 WHERE `id` = %s",
                (message_id,),
            )
            _sync_ticket_admin_read(cur, ticket_id)
        status = _normalize_status(str(row.get("status") or "new"))
    if status == "new":
        return set_support_status(ticket_id, "in_progress")
    return get_support_ticket(ticket_id)


def complete_support_ticket(ticket_id: int) -> dict[str, Any] | None:
    """Mark ticket done and all user messages as read."""
    existing = get_support_ticket(ticket_id)
    if not existing:
        return None
    with cursor() as cur:
        _mark_user_messages_admin_read(cur, ticket_id)
        cur.execute(
            "UPDATE support_tickets SET `admin_read` = 1 WHERE `id` = %s",
            (ticket_id,),
        )
    if existing.get("status") == "done":
        return get_support_ticket(ticket_id)
    return set_support_status(ticket_id, "done")


def update_support_message(message_id: int, body: str) -> dict[str, Any] | None:
    body = (body or "").strip()
    if not body:
        raise ValueError("required")
    now = utc_now()
    with cursor() as cur:
        cur.execute(
            """
            SELECT `id`, `ticket_id`, `author`
            FROM support_messages
            WHERE `id` = %s
            """,
            (message_id,),
        )
        row = cur.fetchone()
        if not row:
            return None
        ticket_id = int(row["ticket_id"])
        author = str(row.get("author") or "")
        cur.execute(
            """
            UPDATE support_messages
            SET `body` = %s, `edited_at` = %s
            WHERE `id` = %s
            """,
            (body, now, message_id),
        )
        if author in ("admin", "system"):
            cur.execute(
                """
                UPDATE support_tickets
                SET `updated_at` = %s, `user_read` = 0
                WHERE `id` = %s
                """,
                (now, ticket_id),
            )
        else:
            cur.execute(
                "UPDATE support_tickets SET `updated_at` = %s WHERE `id` = %s",
                (now, ticket_id),
            )
    return get_support_ticket(ticket_id)


def delete_support_message(message_id: int) -> dict[str, Any] | None:
    with cursor() as cur:
        cur.execute(
            "SELECT `id`, `ticket_id` FROM support_messages WHERE `id` = %s",
            (message_id,),
        )
        row = cur.fetchone()
        if not row:
            return None
        ticket_id = int(row["ticket_id"])
        cur.execute("DELETE FROM support_messages WHERE `id` = %s", (message_id,))
        cur.execute(
            "UPDATE support_tickets SET `updated_at` = %s WHERE `id` = %s",
            (utc_now(), ticket_id),
        )
        _sync_ticket_admin_read(cur, ticket_id)
    return get_support_ticket(ticket_id)


def delete_support_ticket(ticket_id: int) -> bool:
    with cursor() as cur:
        cur.execute("DELETE FROM support_messages WHERE `ticket_id` = %s", (ticket_id,))
        cur.execute("DELETE FROM support_tickets WHERE `id` = %s", (ticket_id,))
        return cur.rowcount > 0


def mark_support_read(ticket_id: int, *, admin: bool) -> dict[str, Any] | None:
    with cursor() as cur:
        cur.execute("SELECT `id`, `status` FROM support_tickets WHERE `id` = %s", (ticket_id,))
        row = cur.fetchone()
        if not row:
            return None
        if admin:
            _mark_user_messages_admin_read(cur, ticket_id)
            status = _normalize_status(str(row.get("status") or "new"))
        else:
            cur.execute(
                "UPDATE support_tickets SET `user_read` = 1 WHERE `id` = %s",
                (ticket_id,),
            )
            status = None
    if admin and status == "new":
        return set_support_status(ticket_id, "in_progress")
    return get_support_ticket(ticket_id)


def get_support_ticket(ticket_id: int) -> dict[str, Any] | None:
    with cursor() as cur:
        cur.execute(
            """
            SELECT `id`, `user_id`, `user_name`, `screen`, `screen_route`,
                   `created_at`, `updated_at`, `admin_read`, `user_read`, `status`,
                   `belonging`, `kind`, `admin_source`
            FROM support_tickets WHERE `id` = %s
            """,
            (ticket_id,),
        )
        row = cur.fetchone()
        if not row:
            return None
        cur.execute(
            """
            SELECT `id`, `author`, `body`, `created_at`, `edited_at`, `admin_read`
            FROM support_messages
            WHERE `ticket_id` = %s
            ORDER BY `id` ASC
            """,
            (ticket_id,),
        )
        messages = [_message_dict(item) for item in (cur.fetchall() or [])]
    preview = messages[-1]["body"] if messages else ""
    row["preview"] = preview[:240]
    row["first_preview"] = messages[0]["body"] if messages else ""
    row["last_preview"] = messages[-1]["body"] if len(messages) > 1 else ""
    row["message_count"] = len(messages)
    return _ticket_dict(row, messages)


_TICKET_PREVIEW_SQL = """
                       ,
                       (
                         SELECT LEFT(m.`body`, 240) FROM support_messages m
                         WHERE m.`ticket_id` = t.`id`
                         ORDER BY m.`id` DESC LIMIT 1
                       ) AS preview,
                       (
                         SELECT LEFT(m.`body`, 240) FROM support_messages m
                         WHERE m.`ticket_id` = t.`id`
                         ORDER BY m.`id` ASC LIMIT 1
                       ) AS first_preview,
                       (
                         SELECT LEFT(m.`body`, 240) FROM support_messages m
                         WHERE m.`ticket_id` = t.`id`
                         ORDER BY m.`id` DESC LIMIT 1
                       ) AS last_preview,
                       (
                         SELECT COUNT(*) FROM support_messages m
                         WHERE m.`ticket_id` = t.`id`
                       ) AS message_count
"""


def list_support_tickets(user_id: str = "") -> list[dict[str, Any]]:
    order_sql = """
        ORDER BY FIELD(t.`status`, 'new', 'in_progress', 'done'),
                 t.`updated_at` DESC
    """
    with cursor() as cur:
        if user_id:
            cur.execute(
                f"""
                SELECT t.`id`, t.`user_id`, t.`user_name`, t.`screen`, t.`screen_route`,
                       t.`created_at`, t.`updated_at`, t.`admin_read`, t.`user_read`,
                       t.`status`, t.`belonging`, t.`kind`, t.`admin_source`
                       {_TICKET_PREVIEW_SQL}
                FROM support_tickets t
                WHERE t.`user_id` = %s
                {order_sql}
                """,
                (user_id,),
            )
        else:
            cur.execute(
                f"""
                SELECT t.`id`, t.`user_id`, t.`user_name`, t.`screen`, t.`screen_route`,
                       t.`created_at`, t.`updated_at`, t.`admin_read`, t.`user_read`,
                       t.`status`, t.`belonging`, t.`kind`, t.`admin_source`
                       {_TICKET_PREVIEW_SQL}
                FROM support_tickets t
                {order_sql}
                """
            )
        rows = cur.fetchall() or []
    return [_ticket_dict(row) for row in rows]


def support_unread_count(*, user_id: str = "", admin: bool = False) -> int:
    with cursor() as cur:
        if admin:
            cur.execute(
                "SELECT COUNT(*) AS c FROM support_tickets WHERE `admin_read` = 0"
            )
        elif user_id:
            cur.execute(
                """
                SELECT COUNT(*) AS c FROM support_tickets
                WHERE `user_id` = %s AND `user_read` = 0
                """,
                (user_id,),
            )
        else:
            return 0
        row = cur.fetchone() or {}
    return int(row.get("c") or 0)


def _parse_dt(value: str | None) -> datetime | None:
    raw = (value or "").strip()
    if not raw:
        return None
    try:
        return datetime.strptime(raw[:19], "%Y-%m-%d %H:%M:%S").replace(tzinfo=timezone.utc)
    except Exception:
        return None


def upsert_premium_payment(
    *,
    payment_id: str,
    device_id: str,
    amount: str,
    currency: str = "RUB",
    status: str = "pending",
    confirmation_url: str = "",
    raw_json: str = "",
    paid_at: str | None = None,
) -> None:
    payment_id = (payment_id or "").strip()[:64]
    device_id = (device_id or "").strip()[:64]
    if not payment_id:
        raise ValueError("payment_id required")
    now = utc_now()
    with cursor() as cur:
        cur.execute(
            """
            INSERT INTO premium_payments
                (`payment_id`, `device_id`, `amount`, `currency`, `status`,
                 `confirmation_url`, `created_at`, `paid_at`, `raw_json`)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE
                `device_id` = IF(VALUES(`device_id`) <> '', VALUES(`device_id`), `device_id`),
                `amount` = IF(VALUES(`amount`) <> '', VALUES(`amount`), `amount`),
                `currency` = IF(VALUES(`currency`) <> '', VALUES(`currency`), `currency`),
                `status` = VALUES(`status`),
                `confirmation_url` = IF(
                    VALUES(`confirmation_url`) IS NOT NULL AND VALUES(`confirmation_url`) <> '',
                    VALUES(`confirmation_url`),
                    `confirmation_url`
                ),
                `paid_at` = COALESCE(VALUES(`paid_at`), `paid_at`),
                `raw_json` = IF(
                    VALUES(`raw_json`) IS NOT NULL AND VALUES(`raw_json`) <> '',
                    VALUES(`raw_json`),
                    `raw_json`
                )
            """,
            (
                payment_id,
                device_id,
                (amount or "")[:32],
                (currency or "RUB")[:8],
                (status or "pending")[:32],
                confirmation_url or None,
                now,
                paid_at,
                raw_json or None,
            ),
        )


def get_premium_payment(payment_id: str) -> dict[str, Any] | None:
    with cursor() as cur:
        cur.execute(
            """
            SELECT `payment_id`, `device_id`, `amount`, `currency`, `status`,
                   `confirmation_url`, `created_at`, `paid_at`, `raw_json`
            FROM premium_payments WHERE `payment_id` = %s
            """,
            ((payment_id or "").strip()[:64],),
        )
        row = cur.fetchone()
    return dict(row) if row else None


def list_premium_payments(limit: int = 30) -> list[dict[str, Any]]:
    limit = max(1, min(int(limit or 30), 100))
    with cursor() as cur:
        cur.execute(
            """
            SELECT `payment_id`, `device_id`, `amount`, `currency`, `status`,
                   `created_at`, `paid_at`
            FROM premium_payments
            ORDER BY `created_at` DESC
            LIMIT %s
            """,
            (limit,),
        )
        rows = cur.fetchall() or []
    return [dict(row) for row in rows]


def get_premium_entitlement(device_id: str) -> dict[str, Any] | None:
    device_id = (device_id or "").strip()[:64]
    if not device_id:
        return None
    with cursor() as cur:
        cur.execute(
            """
            SELECT `device_id`, `expires_at`, `updated_at`, `last_payment_id`
            FROM premium_entitlements WHERE `device_id` = %s
            """,
            (device_id,),
        )
        row = cur.fetchone()
    return dict(row) if row else None


def premium_status_for_device(device_id: str) -> dict[str, Any]:
    ent = get_premium_entitlement(device_id)
    if not ent:
        return {
            "premium": False,
            "expires_at": "",
            "expires_at_unix": 0,
            "last_payment_id": "",
        }
    expires_raw = str(ent.get("expires_at") or "")
    expires_dt = _parse_dt(expires_raw)
    now = datetime.now(timezone.utc)
    active = bool(expires_dt and expires_dt > now)
    unix = int(expires_dt.timestamp()) if expires_dt else 0
    return {
        "premium": active,
        "expires_at": expires_raw,
        "expires_at_unix": unix,
        "last_payment_id": str(ent.get("last_payment_id") or ""),
    }


def mark_premium_payment_succeeded(
    *,
    payment_id: str,
    device_id: str,
    amount: str,
    currency: str,
    days: int,
    raw_json: str = "",
) -> bool:
    """Apply succeeded payment once. Returns True if entitlement was newly extended."""
    payment_id = (payment_id or "").strip()[:64]
    device_id = (device_id or "").strip()[:64]
    days = max(1, min(int(days or 365), 3650))
    if not payment_id or not device_id:
        raise ValueError("payment_id and device_id required")
    now = utc_now()
    with cursor() as cur:
        cur.execute(
            "SELECT `status` FROM premium_payments WHERE `payment_id` = %s FOR UPDATE",
            (payment_id,),
        )
        row = cur.fetchone()
        if row and str(row.get("status") or "") == "succeeded":
            return False
        if row:
            cur.execute(
                """
                UPDATE premium_payments
                SET `device_id` = %s,
                    `amount` = IF(%s <> '', %s, `amount`),
                    `currency` = IF(%s <> '', %s, `currency`),
                    `status` = 'succeeded',
                    `paid_at` = COALESCE(`paid_at`, %s),
                    `raw_json` = IF(%s IS NOT NULL AND %s <> '', %s, `raw_json`)
                WHERE `payment_id` = %s AND `status` <> 'succeeded'
                """,
                (
                    device_id,
                    (amount or "")[:32],
                    (amount or "")[:32],
                    (currency or "RUB")[:8],
                    (currency or "RUB")[:8],
                    now,
                    raw_json or None,
                    raw_json or "",
                    raw_json or None,
                    payment_id,
                ),
            )
            if cur.rowcount == 0:
                return False
        else:
            try:
                cur.execute(
                    """
                    INSERT INTO premium_payments
                        (`payment_id`, `device_id`, `amount`, `currency`, `status`,
                         `confirmation_url`, `created_at`, `paid_at`, `raw_json`)
                    VALUES (%s, %s, %s, %s, 'succeeded', NULL, %s, %s, %s)
                    """,
                    (
                        payment_id,
                        device_id,
                        (amount or "")[:32],
                        (currency or "RUB")[:8],
                        now,
                        now,
                        raw_json or None,
                    ),
                )
            except pymysql.err.IntegrityError:
                # Parallel webhook already inserted/succeeded this payment_id.
                return False
        cur.execute(
            """
            SELECT `expires_at` FROM premium_entitlements
            WHERE `device_id` = %s FOR UPDATE
            """,
            (device_id,),
        )
        ent = cur.fetchone()
        base = datetime.now(timezone.utc)
        if ent:
            existing = _parse_dt(str(ent.get("expires_at") or ""))
            if existing and existing > base:
                base = existing
        new_expires = (base + timedelta(days=days)).strftime("%Y-%m-%d %H:%M:%S")
        cur.execute(
            """
            INSERT INTO premium_entitlements
                (`device_id`, `expires_at`, `updated_at`, `last_payment_id`)
            VALUES (%s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE
                `expires_at` = VALUES(`expires_at`),
                `updated_at` = VALUES(`updated_at`),
                `last_payment_id` = VALUES(`last_payment_id`)
            """,
            (device_id, new_expires, now, payment_id),
        )
    return True
