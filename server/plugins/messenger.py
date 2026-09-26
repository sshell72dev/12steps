"""Experimental messenger: QR pairing, groups, text and voice. Isolated plugin."""
from __future__ import annotations

import hmac
import os
import re
import secrets
import uuid
from functools import wraps
from pathlib import Path

from flask import jsonify, redirect, request, send_file, url_for

import db

UPLOAD_DIR = Path(__file__).resolve().parent.parent / "uploads" / "messenger"
ID_RE = re.compile(r"^[A-Za-z0-9_-]{8,64}$")
TOKEN_RE = re.compile(r"^[A-Za-z0-9_-]{8,80}$")
MAX_NAME = 40
MAX_TEXT = 4000
MAX_VOICE_BYTES = 1_048_576
MAX_VOICE_MS = 60_000
MAX_AVATAR_BYTES = 2_097_152
SETTING_KEY = "messenger_enabled"
SYSTEM_USER_ID = "steps12_system"
SYSTEM_USER_NAME = "Челленджи"
SUPPORT_KEY = "support"
SUPPORT_GROUP_ID = "challenge_support"
SUPPORT_GROUP_NAME = "Техподдержка"
IDEAS_KEY = "ideas"
IDEAS_GROUP_ID = "challenge_ideas"
IDEAS_GROUP_NAME = "Идеи и Ошибки"
SYSTEM_TEXT_NAME = "Администратор"
TOPIC_NAME_WORDS = 2
CHALLENGES = (
    ("steps", "challenge_steps", "Челлендж шагов"),
    ("analysis", "challenge_analysis", "Челлендж самоанализов"),
    (SUPPORT_KEY, SUPPORT_GROUP_ID, SUPPORT_GROUP_NAME),
    (IDEAS_KEY, IDEAS_GROUP_ID, IDEAS_GROUP_NAME),
)
CHALLENGE_KEYS = {item[0] for item in CHALLENGES}
# Группа обращений не выдаётся карточкой «Подключиться»: в неё попадают автоматически.
HIDDEN_CHALLENGE_KEYS = {IDEAS_KEY}


def is_enabled() -> bool:
    raw = db.get_setting(SETTING_KEY, "1")
    return str(raw or "1").strip().lower() in ("1", "true", "on", "yes")


def set_enabled(on: bool) -> None:
    db.set_setting(SETTING_KEY, "1" if on else "0")


def init_schema() -> None:
    UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    with db.cursor() as cur:
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS messenger_users (
                id VARCHAR(64) NOT NULL PRIMARY KEY,
                display_name VARCHAR(80) NOT NULL DEFAULT '',
                created_at DATETIME NOT NULL,
                updated_at DATETIME NOT NULL
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS messenger_invites (
                token VARCHAR(80) NOT NULL PRIMARY KEY,
                kind VARCHAR(16) NOT NULL,
                owner_id VARCHAR(64) NOT NULL,
                group_id VARCHAR(64) NULL,
                created_at DATETIME NOT NULL
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS messenger_contacts (
                user_id VARCHAR(64) NOT NULL,
                peer_id VARCHAR(64) NOT NULL,
                created_at DATETIME NOT NULL,
                PRIMARY KEY (user_id, peer_id)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS messenger_groups (
                id VARCHAR(64) NOT NULL PRIMARY KEY,
                name VARCHAR(80) NOT NULL,
                owner_id VARCHAR(64) NOT NULL,
                created_at DATETIME NOT NULL,
                challenge_key VARCHAR(32) NULL,
                UNIQUE KEY messenger_groups_challenge (challenge_key)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS messenger_group_members (
                group_id VARCHAR(64) NOT NULL,
                user_id VARCHAR(64) NOT NULL,
                role VARCHAR(16) NOT NULL DEFAULT 'member',
                created_at DATETIME NOT NULL,
                PRIMARY KEY (group_id, user_id)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS messenger_chats (
                id VARCHAR(64) NOT NULL PRIMARY KEY,
                kind VARCHAR(16) NOT NULL,
                group_id VARCHAR(64) NULL,
                pair_key VARCHAR(140) NULL,
                created_at DATETIME NOT NULL,
                last_message_at DATETIME NULL,
                UNIQUE KEY messenger_chats_pair (pair_key),
                KEY messenger_chats_group (group_id)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS messenger_chat_members (
                chat_id VARCHAR(64) NOT NULL,
                user_id VARCHAR(64) NOT NULL,
                last_read_id BIGINT NOT NULL DEFAULT 0,
                created_at DATETIME NOT NULL,
                PRIMARY KEY (chat_id, user_id)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS messenger_messages (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                chat_id VARCHAR(64) NOT NULL,
                sender_id VARCHAR(64) NOT NULL,
                kind VARCHAR(16) NOT NULL,
                body TEXT NOT NULL,
                voice_path VARCHAR(255) NOT NULL DEFAULT '',
                voice_duration_ms INT NOT NULL DEFAULT 0,
                created_at DATETIME NOT NULL,
                edited_at DATETIME NULL,
                deleted TINYINT(1) NOT NULL DEFAULT 0,
                KEY messenger_messages_chat (chat_id, id)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )
        _ensure_challenge_schema(cur)
        _ensure_media_schema(cur)


def _has_column(cur, table: str, column: str) -> bool:
    cur.execute(
        """
        SELECT COUNT(*) AS c FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = %s
          AND COLUMN_NAME = %s
        """,
        (table, column),
    )
    return int((cur.fetchone() or {}).get("c") or 0) > 0


def _has_index(cur, table: str, name: str) -> bool:
    cur.execute(
        """
        SELECT COUNT(*) AS c FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = %s
          AND INDEX_NAME = %s
        """,
        (table, name),
    )
    return int((cur.fetchone() or {}).get("c") or 0) > 0


def _ensure_challenge_schema(cur) -> None:
    if not _has_column(cur, "messenger_groups", "challenge_key"):
        cur.execute(
            "ALTER TABLE messenger_groups ADD COLUMN challenge_key VARCHAR(32) NULL"
        )
    if not _has_index(cur, "messenger_groups", "messenger_groups_challenge"):
        cur.execute(
            "ALTER TABLE messenger_groups ADD UNIQUE KEY messenger_groups_challenge (challenge_key)"
        )
    _ensure_challenge_groups(cur)


def _ensure_challenge_groups(cur) -> None:
    now = db.utc_now()
    cur.execute("SELECT id FROM messenger_users WHERE id = %s", (SYSTEM_USER_ID,))
    if not cur.fetchone():
        cur.execute(
            """
            INSERT INTO messenger_users (id, display_name, created_at, updated_at)
            VALUES (%s, %s, %s, %s)
            """,
            (SYSTEM_USER_ID, SYSTEM_USER_NAME, now, now),
        )
    for key, group_id, name in CHALLENGES:
        cur.execute(
            "SELECT id FROM messenger_groups WHERE challenge_key = %s",
            (key,),
        )
        row = cur.fetchone()
        if row:
            _ensure_group_chat(cur, row["id"])
            continue
        cur.execute("SELECT id FROM messenger_groups WHERE id = %s", (group_id,))
        existing = cur.fetchone()
        if existing:
            cur.execute(
                "UPDATE messenger_groups SET challenge_key = %s, name = %s WHERE id = %s",
                (key, name, group_id),
            )
        else:
            cur.execute(
                """
                INSERT INTO messenger_groups (id, name, owner_id, created_at, challenge_key)
                VALUES (%s, %s, %s, %s, %s)
                """,
                (group_id, name, SYSTEM_USER_ID, now, key),
            )
            cur.execute(
                """
                INSERT IGNORE INTO messenger_group_members (group_id, user_id, role, created_at)
                VALUES (%s, %s, 'owner', %s)
                """,
                (group_id, SYSTEM_USER_ID, now),
            )
        _ensure_group_chat(cur, group_id)


def _ensure_media_schema(cur) -> None:
    """Поля правки и удаления сообщений, а также темы внутри групп."""
    if not _has_column(cur, "messenger_messages", "edited_at"):
        cur.execute("ALTER TABLE messenger_messages ADD COLUMN edited_at DATETIME NULL")
    if not _has_column(cur, "messenger_messages", "deleted"):
        cur.execute(
            "ALTER TABLE messenger_messages ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0"
        )
    if not _has_column(cur, "messenger_chats", "topic_id"):
        cur.execute("ALTER TABLE messenger_chats ADD COLUMN topic_id VARCHAR(64) NULL")
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS messenger_topics (
            id VARCHAR(64) NOT NULL PRIMARY KEY,
            group_id VARCHAR(64) NOT NULL,
            name VARCHAR(80) NOT NULL,
            created_at DATETIME NOT NULL,
            KEY messenger_topics_group (group_id, created_at)
        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        """
    )
    if not _has_column(cur, "messenger_topics", "author_id"):
        cur.execute("ALTER TABLE messenger_topics ADD COLUMN author_id VARCHAR(64) NULL")
    if not _has_column(cur, "messenger_topics", "ticket_id"):
        cur.execute("ALTER TABLE messenger_topics ADD COLUMN ticket_id BIGINT NULL")
    if not _has_index(cur, "messenger_topics", "messenger_topics_ticket"):
        cur.execute(
            "ALTER TABLE messenger_topics ADD INDEX messenger_topics_ticket (ticket_id)"
        )


def _avatar_file(kind: str, owner_id: str) -> Path:
    return UPLOAD_DIR / f"avatar_{kind}_{owner_id}"


def _avatar_mime(raw: bytes) -> str:
    if raw[:3] == b"\xff\xd8\xff":
        return "image/jpeg"
    if raw[:8] == b"\x89PNG\r\n\x1a\n":
        return "image/png"
    if raw[:6] in (b"GIF87a", b"GIF89a"):
        return "image/gif"
    if raw[:4] == b"RIFF" and raw[8:12] == b"WEBP":
        return "image/webp"
    return ""


def _avatar_url(kind: str, owner_id: str) -> str:
    """Ссылка на аватар. Версия в адресе — время файла, иначе клиент покажет старую картинку."""
    if not owner_id:
        return ""
    try:
        stamp = int(_avatar_file(kind, owner_id).stat().st_mtime)
    except OSError:
        return ""
    return f"/api/v1/messenger/avatar/{kind}/{owner_id}?v={stamp}"


def _save_avatar(kind: str, owner_id: str, raw: bytes) -> bool:
    try:
        _avatar_file(kind, owner_id).write_bytes(raw)
    except OSError:
        return False
    return True


def _drop_avatar(kind: str, owner_id: str) -> None:
    try:
        _avatar_file(kind, owner_id).unlink()
    except OSError:
        pass


def _group_row(cur, group_id: str):
    cur.execute(
        "SELECT id, name, owner_id, challenge_key FROM messenger_groups WHERE id = %s",
        (group_id,),
    )
    return cur.fetchone()


def _is_group_member(cur, group_id: str, user_id: str) -> bool:
    cur.execute(
        "SELECT 1 FROM messenger_group_members WHERE group_id = %s AND user_id = %s",
        (group_id, user_id),
    )
    return cur.fetchone() is not None


def _drop_chat(cur, chat_id: str) -> None:
    """Удалить чат вместе с лентой, голосовыми файлами и участниками."""
    cur.execute(
        "SELECT id FROM messenger_messages WHERE chat_id = %s AND kind = 'voice'",
        (chat_id,),
    )
    for row in cur.fetchall():
        _drop_voice_file(int(row["id"]))
    cur.execute("DELETE FROM messenger_messages WHERE chat_id = %s", (chat_id,))
    cur.execute("DELETE FROM messenger_chat_members WHERE chat_id = %s", (chat_id,))
    cur.execute("DELETE FROM messenger_chats WHERE id = %s", (chat_id,))


def _ensure_topic_chat(cur, group_id: str, topic_id: str) -> str:
    cur.execute(
        "SELECT id FROM messenger_chats WHERE topic_id = %s AND kind = 'topic'",
        (topic_id,),
    )
    row = cur.fetchone()
    if row:
        return row["id"]
    chat_id = _new_id()
    cur.execute(
        """
        INSERT INTO messenger_chats (id, kind, group_id, topic_id, pair_key, created_at, last_message_at)
        VALUES (%s, 'topic', %s, %s, NULL, %s, NULL)
        """,
        (chat_id, group_id, topic_id, db.utc_now()),
    )
    return chat_id


def _topic_json(
    cur, topic_id: str, name: str, chat_id: str, me: str, is_default: bool = False
) -> dict:
    last_body = ""
    last_kind = ""
    last_at = 0
    unread = 0
    if chat_id:
        _add_chat_member(cur, chat_id, me)
        cur.execute(
            """
            SELECT kind, body, UNIX_TIMESTAMP(created_at) AS created_unix
            FROM messenger_messages
            WHERE chat_id = %s AND deleted = 0
            ORDER BY id DESC LIMIT 1
            """,
            (chat_id,),
        )
        last = cur.fetchone() or {}
        last_kind = last.get("kind") or ""
        last_body = last.get("body") or ""
        last_at = _ms(last, "created_unix")
        if last_kind == "voice":
            last_body = "Голосовое сообщение"
        cur.execute(
            "SELECT last_read_id FROM messenger_chat_members WHERE chat_id = %s AND user_id = %s",
            (chat_id, me),
        )
        member = cur.fetchone() or {}
        unread = _unread(cur, chat_id, me, int(member.get("last_read_id") or 0))
    return {
        "id": topic_id,
        "name": name,
        "chat_id": chat_id,
        "is_default": is_default,
        "unread": unread,
        "last_body": last_body,
        "last_kind": last_kind,
        "last_at": last_at,
    }


def _ensure_support_membership(cur, messenger_id: str) -> None:
    """Чат техподдержки доступен каждому: пользователь попадает в него автоматически."""
    if not messenger_id or messenger_id == SYSTEM_USER_ID:
        return
    cur.execute(
        """
        INSERT IGNORE INTO messenger_group_members (group_id, user_id, role, created_at)
        VALUES (%s, %s, 'member', %s)
        """,
        (SUPPORT_GROUP_ID, messenger_id, db.utc_now()),
    )
    chat_id = _ensure_group_chat(cur, SUPPORT_GROUP_ID)
    _add_chat_member(cur, chat_id, messenger_id)


def _topic_name_from_body(body: str) -> str:
    """Название подгруппы — первые два слова обращения."""
    words = [word for word in (body or "").split() if word]
    if not words:
        return ""
    return _clean_name(" ".join(words[:TOPIC_NAME_WORDS]))


def _admin_ok(raw: str) -> bool:
    """Код администратора приложения: владелец видит все обращения."""
    expected = str(db.get_setting("admin_app_code", "") or "").strip().upper()
    got = (raw or "").strip().upper()
    if not expected or not got:
        return False
    return hmac.compare_digest(got, expected)


def _ensure_ideas_membership(cur, messenger_id: str) -> None:
    """Группа обращений «Идеи и Ошибки» открыта каждому: в ней только его подгруппы."""
    if not messenger_id or messenger_id == SYSTEM_USER_ID:
        return
    cur.execute(
        """
        INSERT IGNORE INTO messenger_group_members (group_id, user_id, role, created_at)
        VALUES (%s, %s, 'member', %s)
        """,
        (IDEAS_GROUP_ID, messenger_id, db.utc_now()),
    )
    chat_id = _ensure_group_chat(cur, IDEAS_GROUP_ID)
    _add_chat_member(cur, chat_id, messenger_id)


def _ideas_ticket_for_chat(cur, chat_id: str) -> int:
    """Номер обращения, к которому привязана подгруппа (0 — это не подгруппа обращений)."""
    cur.execute(
        """
        SELECT t.ticket_id AS ticket_id
        FROM messenger_chats c
        JOIN messenger_topics t ON t.id = c.topic_id
        WHERE c.id = %s AND c.kind = 'topic'
        """,
        (chat_id,),
    )
    row = cur.fetchone() or {}
    try:
        return int(row.get("ticket_id") or 0)
    except (TypeError, ValueError):
        return 0


def attach_support_ticket(ticket: dict, messenger_id: str) -> dict:
    """Обращение из поддержки становится подгруппой «Идеи и Ошибки»."""
    try:
        if not is_enabled():
            return {}
        ticket_id = int(ticket.get("id") or 0)
        if not ticket_id:
            return {}
        body = str(ticket.get("preview") or "").strip()
        if not body:
            for message in ticket.get("messages") or []:
                if str(message.get("author") or "") == "user":
                    body = str(message.get("body") or "").strip()
                    break
        if not body:
            return {}
        author_id = messenger_id if _valid_id(messenger_id) else ""
        name = _topic_name_from_body(body) or IDEAS_GROUP_NAME
        with db.cursor() as cur:
            _ensure_challenge_groups(cur)
            # Клиент мессенджера мог ещё не заводить профиль — создаём его из обращения.
            if author_id:
                cur.execute("SELECT id FROM messenger_users WHERE id = %s", (author_id,))
                if not cur.fetchone():
                    now = db.utc_now()
                    cur.execute(
                        """
                        INSERT INTO messenger_users (id, display_name, created_at, updated_at)
                        VALUES (%s, %s, %s, %s)
                        """,
                        (
                            author_id,
                            _clean_name(str(ticket.get("user_name") or "")) or "Пользователь",
                            now,
                            now,
                        ),
                    )
            cur.execute("SELECT id FROM messenger_topics WHERE ticket_id = %s", (ticket_id,))
            if cur.fetchone():
                return {}
            topic_id = _new_id()
            cur.execute(
                """
                INSERT INTO messenger_topics (id, group_id, name, created_at, author_id, ticket_id)
                VALUES (%s, %s, %s, %s, %s, %s)
                """,
                (topic_id, IDEAS_GROUP_ID, name, db.utc_now(), author_id, ticket_id),
            )
            chat_id = _ensure_topic_chat(cur, IDEAS_GROUP_ID, topic_id)
            sender = author_id or SYSTEM_USER_ID
            if author_id:
                _add_chat_member(cur, chat_id, author_id)
            # Первым сообщением подгруппы идёт сам вопрос от имени пользователя.
            _insert_message(cur, chat_id, sender, "text", body)
        return {"topic_id": topic_id, "chat_id": chat_id}
    except Exception:
        return {}


def push_support_reply(ticket_id: int, body: str) -> dict:
    """Ответ администратора из поддержки дублируется в подгруппу обращения."""
    try:
        text = (body or "").strip()
        if not text or not is_enabled():
            return {}
        with db.cursor() as cur:
            cur.execute("SELECT id FROM messenger_topics WHERE ticket_id = %s", (int(ticket_id),))
            row = cur.fetchone()
            if not row:
                return {}
            chat_id = _ensure_topic_chat(cur, IDEAS_GROUP_ID, row["id"])
            message_id = _insert_message(cur, chat_id, SYSTEM_USER_ID, "text", text)
        return {"chat_id": chat_id, "message_id": message_id}
    except Exception:
        return {}


def _challenge_json(cur, key: str, group_id: str, name: str, messenger_id: str) -> dict:
    cur.execute(
        "SELECT 1 FROM messenger_group_members WHERE group_id = %s AND user_id = %s",
        (group_id, messenger_id),
    )
    joined = cur.fetchone() is not None
    cur.execute(
        """
        SELECT COUNT(*) AS c FROM messenger_group_members
        WHERE group_id = %s AND user_id <> %s
        """,
        (group_id, SYSTEM_USER_ID),
    )
    members = int((cur.fetchone() or {}).get("c") or 0)
    chat_id = ""
    if joined:
        chat_id = _ensure_group_chat(cur, group_id)
        _add_chat_member(cur, chat_id, messenger_id)
    return {
        "key": key,
        "name": name,
        "group_id": group_id,
        "chat_id": chat_id,
        "joined": joined,
        "members": members,
    }


def _new_id() -> str:
    return str(uuid.uuid4())


def _new_token() -> str:
    return secrets.token_urlsafe(18)


def _valid_id(value: str) -> bool:
    return bool(ID_RE.match(value or ""))


def _clean_name(raw: str) -> str:
    return (raw or "").strip()[:MAX_NAME]


def _ms(row: dict, key: str) -> int:
    try:
        return int(row.get(key) or 0) * 1000
    except (TypeError, ValueError):
        return 0


def _user_json(row: dict) -> dict:
    return {
        "id": row["id"],
        "display_name": row.get("display_name") or "",
        "avatar_url": _avatar_url("user", row["id"]),
    }


def _require_user(cur, messenger_id: str):
    if not _valid_id(messenger_id):
        return None
    cur.execute("SELECT id, display_name FROM messenger_users WHERE id = %s", (messenger_id,))
    return cur.fetchone()


def _pair_invite(cur, owner_id: str, rotate: bool = False) -> str:
    if not rotate:
        cur.execute(
            "SELECT token FROM messenger_invites WHERE owner_id = %s AND kind = 'pair' LIMIT 1",
            (owner_id,),
        )
        row = cur.fetchone()
        if row:
            return row["token"]
    cur.execute(
        "DELETE FROM messenger_invites WHERE owner_id = %s AND kind = 'pair'",
        (owner_id,),
    )
    token = _new_token()
    cur.execute(
        """
        INSERT INTO messenger_invites (token, kind, owner_id, group_id, created_at)
        VALUES (%s, 'pair', %s, NULL, %s)
        """,
        (token, owner_id, db.utc_now()),
    )
    return token


def _group_invite(cur, owner_id: str, group_id: str, rotate: bool = False) -> str:
    if not rotate:
        cur.execute(
            """
            SELECT token FROM messenger_invites
            WHERE group_id = %s AND kind = 'group' LIMIT 1
            """,
            (group_id,),
        )
        row = cur.fetchone()
        if row:
            return row["token"]
    cur.execute(
        "DELETE FROM messenger_invites WHERE group_id = %s AND kind = 'group'",
        (group_id,),
    )
    token = _new_token()
    cur.execute(
        """
        INSERT INTO messenger_invites (token, kind, owner_id, group_id, created_at)
        VALUES (%s, 'group', %s, %s, %s)
        """,
        (token, owner_id, group_id, db.utc_now()),
    )
    return token


def _add_contact(cur, user_id: str, peer_id: str) -> None:
    now = db.utc_now()
    cur.execute(
        """
        INSERT IGNORE INTO messenger_contacts (user_id, peer_id, created_at)
        VALUES (%s, %s, %s)
        """,
        (user_id, peer_id, now),
    )


def _ensure_direct_chat(cur, a: str, b: str) -> str:
    pair_key = ":".join(sorted([a, b]))
    cur.execute("SELECT id FROM messenger_chats WHERE pair_key = %s", (pair_key,))
    row = cur.fetchone()
    if row:
        chat_id = row["id"]
    else:
        chat_id = _new_id()
        now = db.utc_now()
        cur.execute(
            """
            INSERT INTO messenger_chats (id, kind, group_id, pair_key, created_at, last_message_at)
            VALUES (%s, 'direct', NULL, %s, %s, NULL)
            """,
            (chat_id, pair_key, now),
        )
        for uid in (a, b):
            cur.execute(
                """
                INSERT IGNORE INTO messenger_chat_members (chat_id, user_id, last_read_id, created_at)
                VALUES (%s, %s, 0, %s)
                """,
                (chat_id, uid, now),
            )
        return chat_id
    now = db.utc_now()
    for uid in (a, b):
        cur.execute(
            """
            INSERT IGNORE INTO messenger_chat_members (chat_id, user_id, last_read_id, created_at)
            VALUES (%s, %s, 0, %s)
            """,
            (chat_id, uid, now),
        )
    return chat_id


def _ensure_group_chat(cur, group_id: str) -> str:
    cur.execute("SELECT id FROM messenger_chats WHERE group_id = %s AND kind = 'group'", (group_id,))
    row = cur.fetchone()
    if row:
        return row["id"]
    chat_id = _new_id()
    now = db.utc_now()
    cur.execute(
        """
        INSERT INTO messenger_chats (id, kind, group_id, pair_key, created_at, last_message_at)
        VALUES (%s, 'group', %s, NULL, %s, NULL)
        """,
        (chat_id, group_id, now),
    )
    return chat_id


def _add_chat_member(cur, chat_id: str, user_id: str) -> None:
    cur.execute(
        """
        INSERT IGNORE INTO messenger_chat_members (chat_id, user_id, last_read_id, created_at)
        VALUES (%s, %s, 0, %s)
        """,
        (chat_id, user_id, db.utc_now()),
    )


def _ideas_topic_author(cur, topic_id: str) -> str:
    cur.execute("SELECT author_id FROM messenger_topics WHERE id = %s", (topic_id,))
    return str((cur.fetchone() or {}).get("author_id") or "")


def _is_member(cur, chat_id: str, user_id: str, admin: bool = False) -> bool:
    if admin:
        return True
    cur.execute(
        "SELECT 1 FROM messenger_chat_members WHERE chat_id = %s AND user_id = %s",
        (chat_id, user_id),
    )
    if cur.fetchone() is not None:
        return True
    # Лента темы открыта участникам группы, даже если запись о членстве ещё не создана.
    cur.execute(
        "SELECT group_id, topic_id FROM messenger_chats WHERE id = %s AND kind = 'topic'",
        (chat_id,),
    )
    chat = cur.fetchone() or {}
    group_id = chat.get("group_id") or ""
    if not group_id:
        return False
    cur.execute("SELECT challenge_key FROM messenger_groups WHERE id = %s", (group_id,))
    challenge_key = str((cur.fetchone() or {}).get("challenge_key") or "")
    if challenge_key == IDEAS_KEY:
        # Переписку по обращению видит только его автор (и администратор через admin=True).
        if _ideas_topic_author(cur, str(chat.get("topic_id") or "")) != user_id:
            return False
        _add_chat_member(cur, chat_id, user_id)
        return True
    cur.execute(
        "SELECT 1 FROM messenger_group_members WHERE group_id = %s AND user_id = %s",
        (group_id, user_id),
    )
    if cur.fetchone() is None:
        return False
    _add_chat_member(cur, chat_id, user_id)
    return True


def _peer_name(cur, chat_id: str, me: str) -> str:
    cur.execute(
        """
        SELECT u.display_name
        FROM messenger_chat_members m
        JOIN messenger_users u ON u.id = m.user_id
        WHERE m.chat_id = %s AND m.user_id <> %s
        LIMIT 1
        """,
        (chat_id, me),
    )
    row = cur.fetchone()
    return (row or {}).get("display_name") or ""


def _unread(cur, chat_id: str, me: str, last_read_id: int) -> int:
    cur.execute(
        """
        SELECT COUNT(*) AS c FROM messenger_messages
        WHERE chat_id = %s AND id > %s AND sender_id <> %s
        """,
        (chat_id, last_read_id, me),
    )
    row = cur.fetchone() or {}
    return int(row.get("c") or 0)


def _chat_json(cur, chat: dict, me: str) -> dict:
    kind = chat["kind"]
    title = ""
    peer_id = ""
    group_id = chat.get("group_id") or ""
    is_owner = False
    challenge_key = ""
    if kind == "direct":
        cur.execute(
            """
            SELECT m.user_id, u.display_name
            FROM messenger_chat_members m
            JOIN messenger_users u ON u.id = m.user_id
            WHERE m.chat_id = %s AND m.user_id <> %s
            LIMIT 1
            """,
            (chat["id"], me),
        )
        peer = cur.fetchone() or {}
        peer_id = peer.get("user_id") or ""
        title = peer.get("display_name") or ""
    else:
        cur.execute(
            "SELECT id, name, owner_id, challenge_key FROM messenger_groups WHERE id = %s",
            (group_id,),
        )
        group = cur.fetchone() or {}
        title = group.get("name") or ""
        is_owner = group.get("owner_id") == me
        challenge_key = group.get("challenge_key") or ""
    cur.execute(
        """
        SELECT id, kind, body, sender_id,
               UNIX_TIMESTAMP(created_at) AS created_unix
        FROM messenger_messages
        WHERE chat_id = %s AND deleted = 0
        ORDER BY id DESC LIMIT 1
        """,
        (chat["id"],),
    )
    last = cur.fetchone() or {}
    cur.execute(
        "SELECT last_read_id FROM messenger_chat_members WHERE chat_id = %s AND user_id = %s",
        (chat["id"], me),
    )
    member = cur.fetchone() or {}
    last_read = int(member.get("last_read_id") or 0)
    last_at = _ms(chat, "last_unix") or _ms(last, "created_unix")
    preview = last.get("body") or ""
    last_kind = last.get("kind") or ""
    if last_kind == "voice":
        preview = "Голосовое сообщение"
    avatar_url = _avatar_url("user", peer_id) if kind == "direct" else _avatar_url("group", group_id)
    return {
        "id": chat["id"],
        "kind": kind,
        "title": title,
        "peer_id": peer_id,
        "group_id": group_id,
        "is_owner": is_owner,
        "challenge_key": challenge_key,
        "avatar_url": avatar_url,
        "last_body": preview,
        "last_kind": last_kind,
        "last_at": last_at,
        "unread": _unread(cur, chat["id"], me, last_read),
    }


def _message_json(row: dict, me: str, names: dict[str, str]) -> dict:
    sender = row.get("sender_id") or ""
    kind = row["kind"]
    deleted = bool(int(row.get("deleted") or 0))
    # Сообщения об обновлении шлёт системный пользователь, но в чате
    # техподдержки подписывать их «Челленджи» нельзя.
    if kind == "update":
        sender_name = SUPPORT_GROUP_NAME
    elif sender == SYSTEM_USER_ID:
        sender_name = SYSTEM_TEXT_NAME
    else:
        sender_name = names.get(sender) or ""
    return {
        "id": int(row["id"]),
        "chat_id": row["chat_id"],
        "sender_id": sender,
        "sender_name": sender_name,
        "kind": kind,
        "body": "" if deleted else (row.get("body") or ""),
        "voice_duration_ms": 0 if deleted else int(row.get("voice_duration_ms") or 0),
        "created_at": _ms(row, "created_unix"),
        "mine": sender == me,
        "edited_at": _ms(row, "edited_unix"),
        "deleted": deleted,
    }


def _names_for(cur, user_ids: list[str]) -> dict[str, str]:
    ids = [uid for uid in set(user_ids) if uid]
    if not ids:
        return {}
    placeholders = ",".join(["%s"] * len(ids))
    cur.execute(
        f"SELECT id, display_name FROM messenger_users WHERE id IN ({placeholders})",
        ids,
    )
    return {row["id"]: row.get("display_name") or "" for row in cur.fetchall()}


def _insert_message(cur, chat_id: str, sender_id: str, kind: str, body: str, duration_ms: int = 0) -> int:
    now = db.utc_now()
    cur.execute(
        """
        INSERT INTO messenger_messages
            (chat_id, sender_id, kind, body, voice_path, voice_duration_ms, created_at)
        VALUES (%s, %s, %s, %s, '', %s, %s)
        """,
        (chat_id, sender_id, kind, body, duration_ms, now),
    )
    message_id = int(cur.lastrowid)
    cur.execute(
        "UPDATE messenger_chats SET last_message_at = %s WHERE id = %s",
        (now, chat_id),
    )
    return message_id


def _load_message(cur, message_id: int):
    cur.execute(
        """
        SELECT id, chat_id, sender_id, kind, body, voice_duration_ms, deleted,
               UNIX_TIMESTAMP(created_at) AS created_unix,
               UNIX_TIMESTAMP(edited_at) AS edited_unix
        FROM messenger_messages WHERE id = %s
        """,
        (message_id,),
    )
    return cur.fetchone()


def _drop_voice_file(message_id: int) -> None:
    try:
        (UPLOAD_DIR / f"{message_id}.m4a").unlink()
    except OSError:
        pass


def broadcast_support_update(version_name: str, version_code: int) -> dict:
    """Рассылает в чат «Техподдержка» сообщение о новой версии приложения."""
    name = (version_name or "").strip()
    if not name:
        return {"chat_id": "", "message_id": 0, "sent": 0}
    body = (
        f"Доступна версия {name} ({int(version_code or 0)}). "
        "Нажмите «Обновить приложение», чтобы установить."
    )
    with db.cursor() as cur:
        _ensure_challenge_groups(cur)
        chat_id = _ensure_group_chat(cur, SUPPORT_GROUP_ID)
        cur.execute(
            "SELECT user_id FROM messenger_group_members WHERE group_id = %s",
            (SUPPORT_GROUP_ID,),
        )
        members = [row["user_id"] for row in cur.fetchall()]
        for user_id in members:
            _add_chat_member(cur, chat_id, user_id)
        message_id = _insert_message(cur, chat_id, SYSTEM_USER_ID, "update", body)
    return {"chat_id": chat_id, "message_id": message_id, "sent": len(members)}


def register(app, login_required, api_ok) -> None:
    try:
        init_schema()
    except Exception:
        pass

    @app.context_processor
    def inject_messenger():
        try:
            return {"messenger_enabled": is_enabled()}
        except Exception:
            return {"messenger_enabled": True}

    @app.route("/messenger", methods=["POST"])
    @login_required
    def messenger_admin():
        set_enabled(bool(request.form.get("messenger_enabled")))
        return redirect(url_for("settings", messenger="1"))

    def guard(need_user: bool = True):
        def decorator(view):
            @wraps(view)
            def wrapped(*args, **kwargs):
                if not api_ok():
                    return jsonify({"error": "unauthorized"}), 401
                try:
                    init_schema()
                except Exception:
                    return jsonify({"error": "db"}), 503
                if not is_enabled():
                    return jsonify({"error": "disabled"}), 503
                messenger_id = (request.headers.get("X-Messenger-Id") or "").strip()
                if need_user and not _valid_id(messenger_id):
                    return jsonify({"error": "messenger_id_required"}), 400
                return view(messenger_id, *args, **kwargs)

            return wrapped

        return decorator

    @app.get("/api/v1/messenger/status")
    def api_messenger_status():
        if not api_ok():
            return jsonify({"error": "unauthorized"}), 401
        try:
            enabled = is_enabled()
        except Exception:
            enabled = True
        return jsonify({"enabled": enabled})

    @app.route("/api/v1/messenger/me", methods=["GET", "POST"])
    @guard(need_user=True)
    def api_messenger_me(messenger_id: str):
        payload = request.get_json(silent=True) or {}
        name = _clean_name(str(payload.get("display_name") or ""))
        with db.cursor() as cur:
            user = _require_user(cur, messenger_id)
            now = db.utc_now()
            if request.method == "POST":
                if not name:
                    return jsonify({"error": "name_required"}), 400
                if user:
                    cur.execute(
                        """
                        UPDATE messenger_users
                        SET display_name = %s, updated_at = %s
                        WHERE id = %s
                        """,
                        (name, now, messenger_id),
                    )
                else:
                    cur.execute(
                        """
                        INSERT INTO messenger_users (id, display_name, created_at, updated_at)
                        VALUES (%s, %s, %s, %s)
                        """,
                        (messenger_id, name, now, now),
                    )
                user = {"id": messenger_id, "display_name": name}
            if not user:
                return jsonify({"error": "not_registered"}), 404
            token = _pair_invite(cur, messenger_id)
        return jsonify({"user": _user_json(user), "pair_token": token})

    @app.post("/api/v1/messenger/invites")
    @guard(need_user=True)
    def api_messenger_invites(messenger_id: str):
        payload = request.get_json(silent=True) or {}
        kind = str(payload.get("kind") or "pair").strip()
        rotate = bool(payload.get("rotate"))
        group_id = str(payload.get("group_id") or "").strip()
        with db.cursor() as cur:
            user = _require_user(cur, messenger_id)
            if not user:
                return jsonify({"error": "not_registered"}), 404
            if kind == "pair":
                token = _pair_invite(cur, messenger_id, rotate=rotate)
                return jsonify({"kind": "pair", "token": token})
            if kind != "group" or not _valid_id(group_id):
                return jsonify({"error": "bad_request"}), 400
            cur.execute(
                "SELECT owner_id FROM messenger_groups WHERE id = %s",
                (group_id,),
            )
            group = cur.fetchone()
            if not group:
                return jsonify({"error": "not_found"}), 404
            if rotate and group["owner_id"] != messenger_id:
                return jsonify({"error": "forbidden"}), 403
            cur.execute(
                "SELECT 1 FROM messenger_group_members WHERE group_id = %s AND user_id = %s",
                (group_id, messenger_id),
            )
            if not cur.fetchone():
                return jsonify({"error": "forbidden"}), 403
            token = _group_invite(cur, group["owner_id"], group_id, rotate=rotate)
        return jsonify({"kind": "group", "token": token, "group_id": group_id})

    @app.post("/api/v1/messenger/join")
    @app.post("/api/v1/messenger/pair")
    @guard(need_user=True)
    def api_messenger_join(messenger_id: str):
        payload = request.get_json(silent=True) or {}
        token = str(payload.get("token") or "").strip()
        if not TOKEN_RE.match(token):
            return jsonify({"error": "bad_token"}), 400
        with db.cursor() as cur:
            user = _require_user(cur, messenger_id)
            if not user:
                return jsonify({"error": "not_registered"}), 404
            cur.execute(
                "SELECT token, kind, owner_id, group_id FROM messenger_invites WHERE token = %s",
                (token,),
            )
            invite = cur.fetchone()
            if not invite:
                return jsonify({"error": "invite_not_found"}), 404
            if invite["kind"] == "pair":
                owner_id = invite["owner_id"]
                if owner_id == messenger_id:
                    return jsonify({"error": "self_invite"}), 400
                if not _require_user(cur, owner_id):
                    return jsonify({"error": "invite_not_found"}), 404
                _add_contact(cur, messenger_id, owner_id)
                _add_contact(cur, owner_id, messenger_id)
                chat_id = _ensure_direct_chat(cur, messenger_id, owner_id)
                cur.execute("SELECT display_name FROM messenger_users WHERE id = %s", (owner_id,))
                peer = cur.fetchone() or {}
                return jsonify(
                    {
                        "ok": True,
                        "kind": "direct",
                        "chat_id": chat_id,
                        "peer_id": owner_id,
                        "title": peer.get("display_name") or "",
                    }
                )
            group_id = invite.get("group_id") or ""
            if not group_id:
                return jsonify({"error": "invite_not_found"}), 404
            cur.execute("SELECT id, name FROM messenger_groups WHERE id = %s", (group_id,))
            group = cur.fetchone()
            if not group:
                return jsonify({"error": "not_found"}), 404
            now = db.utc_now()
            cur.execute(
                """
                INSERT IGNORE INTO messenger_group_members (group_id, user_id, role, created_at)
                VALUES (%s, %s, 'member', %s)
                """,
                (group_id, messenger_id, now),
            )
            chat_id = _ensure_group_chat(cur, group_id)
            _add_chat_member(cur, chat_id, messenger_id)
            return jsonify(
                {
                    "ok": True,
                    "kind": "group",
                    "chat_id": chat_id,
                    "group_id": group_id,
                    "title": group.get("name") or "",
                }
            )

    @app.get("/api/v1/messenger/contacts")
    @guard(need_user=True)
    def api_messenger_contacts(messenger_id: str):
        with db.cursor() as cur:
            user = _require_user(cur, messenger_id)
            if not user:
                return jsonify({"error": "not_registered"}), 404
            cur.execute(
                """
                SELECT u.id, u.display_name
                FROM messenger_contacts c
                JOIN messenger_users u ON u.id = c.peer_id
                WHERE c.user_id = %s
                ORDER BY u.display_name ASC
                """,
                (messenger_id,),
            )
            items = [_user_json(row) for row in cur.fetchall()]
        return jsonify({"contacts": items})

    @app.post("/api/v1/messenger/groups")
    @guard(need_user=True)
    def api_messenger_create_group(messenger_id: str):
        payload = request.get_json(silent=True) or {}
        name = _clean_name(str(payload.get("name") or ""))
        if not name:
            return jsonify({"error": "name_required"}), 400
        member_ids = payload.get("user_ids") or []
        if not isinstance(member_ids, list):
            member_ids = []
        group_id = _new_id()
        now = db.utc_now()
        with db.cursor() as cur:
            user = _require_user(cur, messenger_id)
            if not user:
                return jsonify({"error": "not_registered"}), 404
            cur.execute(
                """
                INSERT INTO messenger_groups (id, name, owner_id, created_at)
                VALUES (%s, %s, %s, %s)
                """,
                (group_id, name, messenger_id, now),
            )
            cur.execute(
                """
                INSERT INTO messenger_group_members (group_id, user_id, role, created_at)
                VALUES (%s, %s, 'owner', %s)
                """,
                (group_id, messenger_id, now),
            )
            chat_id = _ensure_group_chat(cur, group_id)
            _add_chat_member(cur, chat_id, messenger_id)
            added = []
            for raw in member_ids:
                peer_id = str(raw or "").strip()
                if not _valid_id(peer_id) or peer_id == messenger_id:
                    continue
                cur.execute(
                    "SELECT 1 FROM messenger_contacts WHERE user_id = %s AND peer_id = %s",
                    (messenger_id, peer_id),
                )
                if not cur.fetchone():
                    continue
                cur.execute(
                    """
                    INSERT IGNORE INTO messenger_group_members (group_id, user_id, role, created_at)
                    VALUES (%s, %s, 'member', %s)
                    """,
                    (group_id, peer_id, now),
                )
                _add_chat_member(cur, chat_id, peer_id)
                added.append(peer_id)
            token = _group_invite(cur, messenger_id, group_id)
        return jsonify(
            {
                "group": {"id": group_id, "name": name, "owner_id": messenger_id},
                "chat_id": chat_id,
                "token": token,
                "added": added,
            }
        )

    @app.get("/api/v1/messenger/groups/<group_id>")
    @guard(need_user=True)
    def api_messenger_group(messenger_id: str, group_id: str):
        if not _valid_id(group_id):
            return jsonify({"error": "not_found"}), 404
        with db.cursor() as cur:
            cur.execute(
                "SELECT 1 FROM messenger_group_members WHERE group_id = %s AND user_id = %s",
                (group_id, messenger_id),
            )
            if not cur.fetchone():
                return jsonify({"error": "forbidden"}), 403
            cur.execute(
                "SELECT id, name, owner_id FROM messenger_groups WHERE id = %s",
                (group_id,),
            )
            group = cur.fetchone()
            if not group:
                return jsonify({"error": "not_found"}), 404
            cur.execute(
                """
                SELECT u.id, u.display_name, m.role
                FROM messenger_group_members m
                JOIN messenger_users u ON u.id = m.user_id
                WHERE m.group_id = %s
                ORDER BY m.role DESC, u.display_name ASC
                """,
                (group_id,),
            )
            members = [
                {
                    "id": row["id"],
                    "display_name": row.get("display_name") or "",
                    "role": row.get("role") or "member",
                    "avatar_url": _avatar_url("user", row["id"]),
                }
                for row in cur.fetchall()
            ]
            token = _group_invite(cur, group["owner_id"], group_id)
            cur.execute(
                "SELECT id FROM messenger_chats WHERE group_id = %s AND kind = 'group'",
                (group_id,),
            )
            chat = cur.fetchone() or {}
        return jsonify(
            {
                "group": {
                    "id": group["id"],
                    "name": group["name"],
                    "owner_id": group["owner_id"],
                    "is_owner": group["owner_id"] == messenger_id,
                    "can_manage": group["owner_id"] == messenger_id,
                    "avatar_url": _avatar_url("group", group_id),
                },
                "members": members,
                "token": token,
                "chat_id": chat.get("id") or "",
            }
        )

    @app.post("/api/v1/messenger/groups/<group_id>/members")
    @guard(need_user=True)
    def api_messenger_add_members(messenger_id: str, group_id: str):
        if not _valid_id(group_id):
            return jsonify({"error": "not_found"}), 404
        payload = request.get_json(silent=True) or {}
        member_ids = payload.get("user_ids") or []
        if not isinstance(member_ids, list):
            return jsonify({"error": "bad_request"}), 400
        with db.cursor() as cur:
            cur.execute(
                "SELECT owner_id FROM messenger_groups WHERE id = %s",
                (group_id,),
            )
            group = cur.fetchone()
            if not group:
                return jsonify({"error": "not_found"}), 404
            if group["owner_id"] != messenger_id:
                return jsonify({"error": "forbidden"}), 403
            chat_id = _ensure_group_chat(cur, group_id)
            now = db.utc_now()
            added = []
            for raw in member_ids:
                peer_id = str(raw or "").strip()
                if not _valid_id(peer_id) or peer_id == messenger_id:
                    continue
                cur.execute(
                    "SELECT 1 FROM messenger_contacts WHERE user_id = %s AND peer_id = %s",
                    (messenger_id, peer_id),
                )
                if not cur.fetchone():
                    continue
                cur.execute(
                    """
                    INSERT IGNORE INTO messenger_group_members (group_id, user_id, role, created_at)
                    VALUES (%s, %s, 'member', %s)
                    """,
                    (group_id, peer_id, now),
                )
                _add_chat_member(cur, chat_id, peer_id)
                added.append(peer_id)
        return jsonify({"ok": True, "added": added, "chat_id": chat_id})

    @app.post("/api/v1/messenger/support/broadcast")
    def api_messenger_support_broadcast():
        if not api_ok():
            return jsonify({"error": "unauthorized"}), 401
        payload = request.get_json(silent=True) or {}
        name = str(payload.get("version_name") or "").strip()
        code = int(payload.get("version_code") or 0)
        if not name:
            return jsonify({"error": "version_required"}), 400
        try:
            init_schema()
        except Exception:
            return jsonify({"error": "db"}), 503
        return jsonify({"ok": True, **broadcast_support_update(name, code)})

    @app.get("/api/v1/messenger/challenges")
    @guard(need_user=True)
    def api_messenger_challenges(messenger_id: str):
        with db.cursor() as cur:
            user = _require_user(cur, messenger_id)
            if not user:
                return jsonify({"error": "not_registered"}), 404
            _ensure_challenge_groups(cur)
            _ensure_support_membership(cur, messenger_id)
            _ensure_ideas_membership(cur, messenger_id)
            items = []
            for key, group_id, name in CHALLENGES:
                if key in HIDDEN_CHALLENGE_KEYS:
                    continue
                items.append(_challenge_json(cur, key, group_id, name, messenger_id))
        return jsonify({"challenges": items})

    @app.post("/api/v1/messenger/challenges/<key>/join")
    @guard(need_user=True)
    def api_messenger_join_challenge(messenger_id: str, key: str):
        key = (key or "").strip().lower()
        if key not in CHALLENGE_KEYS:
            return jsonify({"error": "not_found"}), 404
        with db.cursor() as cur:
            user = _require_user(cur, messenger_id)
            if not user:
                return jsonify({"error": "not_registered"}), 404
            _ensure_challenge_groups(cur)
            meta = next((item for item in CHALLENGES if item[0] == key), None)
            if not meta:
                return jsonify({"error": "not_found"}), 404
            _, group_id, name = meta
            now = db.utc_now()
            cur.execute(
                """
                INSERT IGNORE INTO messenger_group_members (group_id, user_id, role, created_at)
                VALUES (%s, %s, 'member', %s)
                """,
                (group_id, messenger_id, now),
            )
            chat_id = _ensure_group_chat(cur, group_id)
            _add_chat_member(cur, chat_id, messenger_id)
        return jsonify(
            {
                "ok": True,
                "kind": "group",
                "key": key,
                "chat_id": chat_id,
                "group_id": group_id,
                "title": name,
            }
        )

    @app.get("/api/v1/messenger/chats")
    @guard(need_user=True)
    def api_messenger_chats(messenger_id: str):
        with db.cursor() as cur:
            user = _require_user(cur, messenger_id)
            if not user:
                return jsonify({"error": "not_registered"}), 404
            _ensure_challenge_groups(cur)
            _ensure_support_membership(cur, messenger_id)
            _ensure_ideas_membership(cur, messenger_id)
            cur.execute(
                """
                SELECT c.id, c.kind, c.group_id, c.pair_key,
                       UNIX_TIMESTAMP(c.last_message_at) AS last_unix
                FROM messenger_chats c
                JOIN messenger_chat_members me ON me.chat_id = c.id AND me.user_id = %s
                WHERE c.kind <> 'topic'
                ORDER BY COALESCE(c.last_message_at, c.created_at) DESC
                """,
                (messenger_id,),
            )
            chats = [_chat_json(cur, row, messenger_id) for row in cur.fetchall()]
        return jsonify({"chats": chats})

    @app.get("/api/v1/messenger/chats/<chat_id>/messages")
    @guard(need_user=True)
    def api_messenger_messages(messenger_id: str, chat_id: str):
        if not _valid_id(chat_id):
            return jsonify({"error": "not_found"}), 404
        after = 0
        try:
            after = int(request.args.get("after") or 0)
        except ValueError:
            after = 0
        is_admin = _admin_ok(request.headers.get("X-Admin-Code") or "")
        with db.cursor() as cur:
            if not _is_member(cur, chat_id, messenger_id, admin=is_admin):
                return jsonify({"error": "forbidden"}), 403
            cur.execute(
                """
                SELECT id, chat_id, sender_id, kind, body, voice_duration_ms, deleted,
                       UNIX_TIMESTAMP(created_at) AS created_unix,
                       UNIX_TIMESTAMP(edited_at) AS edited_unix
                FROM messenger_messages
                WHERE chat_id = %s AND id > %s
                ORDER BY id ASC
                LIMIT 200
                """,
                (chat_id, after),
            )
            rows = cur.fetchall()
            names = _names_for(cur, [row["sender_id"] for row in rows])
            messages = [_message_json(row, messenger_id, names) for row in rows]
        return jsonify({"messages": messages})

    @app.post("/api/v1/messenger/chats/<chat_id>/messages")
    @guard(need_user=True)
    def api_messenger_send_text(messenger_id: str, chat_id: str):
        if not _valid_id(chat_id):
            return jsonify({"error": "not_found"}), 404
        payload = request.get_json(silent=True) or {}
        body = str(payload.get("body") or "").strip()[:MAX_TEXT]
        if not body:
            return jsonify({"error": "empty"}), 400
        is_admin = _admin_ok(request.headers.get("X-Admin-Code") or "")
        ticket_id = 0
        with db.cursor() as cur:
            if not _is_member(cur, chat_id, messenger_id, admin=is_admin):
                return jsonify({"error": "forbidden"}), 403
            message_id = _insert_message(cur, chat_id, messenger_id, "text", body)
            ticket_id = _ideas_ticket_for_chat(cur, chat_id)
            cur.execute(
                """
                SELECT id, chat_id, sender_id, kind, body, voice_duration_ms,
                       UNIX_TIMESTAMP(created_at) AS created_unix
                FROM messenger_messages WHERE id = %s
                """,
                (message_id,),
            )
            row = cur.fetchone()
            names = _names_for(cur, [messenger_id])
        if ticket_id:
            # Переписка в подгруппе «Идеи и Ошибки» продолжает обращение поддержки.
            try:
                db.add_support_message(ticket_id, "admin" if is_admin else "user", body)
            except ValueError:
                pass
        return jsonify({"message": _message_json(row, messenger_id, names)})

    @app.post("/api/v1/messenger/chats/<chat_id>/voice")
    @guard(need_user=True)
    def api_messenger_send_voice(messenger_id: str, chat_id: str):
        if not _valid_id(chat_id):
            return jsonify({"error": "not_found"}), 404
        upload = request.files.get("file")
        if upload is None:
            return jsonify({"error": "file_required"}), 400
        raw = upload.read(MAX_VOICE_BYTES + 1)
        if not raw or len(raw) > MAX_VOICE_BYTES:
            return jsonify({"error": "file_too_large"}), 400
        try:
            duration_ms = int(request.form.get("duration_ms") or 0)
        except ValueError:
            duration_ms = 0
        duration_ms = max(1, min(duration_ms, MAX_VOICE_MS))
        is_admin = _admin_ok(request.headers.get("X-Admin-Code") or "")
        with db.cursor() as cur:
            if not _is_member(cur, chat_id, messenger_id, admin=is_admin):
                return jsonify({"error": "forbidden"}), 403
            message_id = _insert_message(
                cur, chat_id, messenger_id, "voice", "", duration_ms
            )
            filename = f"{message_id}.m4a"
            path = UPLOAD_DIR / filename
            try:
                path.write_bytes(raw)
            except OSError:
                cur.execute("DELETE FROM messenger_messages WHERE id = %s", (message_id,))
                return jsonify({"error": "store_failed"}), 500
            rel = f"uploads/messenger/{filename}"
            cur.execute(
                "UPDATE messenger_messages SET voice_path = %s WHERE id = %s",
                (rel, message_id),
            )
            cur.execute(
                """
                SELECT id, chat_id, sender_id, kind, body, voice_duration_ms,
                       UNIX_TIMESTAMP(created_at) AS created_unix
                FROM messenger_messages WHERE id = %s
                """,
                (message_id,),
            )
            row = cur.fetchone()
            names = _names_for(cur, [messenger_id])
        return jsonify({"message": _message_json(row, messenger_id, names)})

    @app.get("/api/v1/messenger/voice/<int:message_id>")
    @guard(need_user=True)
    def api_messenger_voice(messenger_id: str, message_id: int):
        with db.cursor() as cur:
            cur.execute(
                "SELECT id, chat_id, voice_path, kind FROM messenger_messages WHERE id = %s",
                (message_id,),
            )
            row = cur.fetchone()
            if not row or row.get("kind") != "voice":
                return jsonify({"error": "not_found"}), 404
            if not _is_member(cur, row["chat_id"], messenger_id):
                return jsonify({"error": "forbidden"}), 403
        path = UPLOAD_DIR / f"{message_id}.m4a"
        stored = (row.get("voice_path") or "").strip()
        if stored:
            alt = Path(__file__).resolve().parent.parent / stored.replace("/", os.sep)
            if alt.is_file():
                path = alt
        if not path.is_file():
            return jsonify({"error": "not_found"}), 404
        return send_file(path, mimetype="audio/mp4", as_attachment=False, download_name=f"{message_id}.m4a")

    @app.post("/api/v1/messenger/chats/<chat_id>/read")
    @guard(need_user=True)
    def api_messenger_read(messenger_id: str, chat_id: str):
        if not _valid_id(chat_id):
            return jsonify({"error": "not_found"}), 404
        payload = request.get_json(silent=True) or {}
        try:
            last_id = int(payload.get("last_id") or 0)
        except (TypeError, ValueError):
            last_id = 0
        with db.cursor() as cur:
            if not _is_member(cur, chat_id, messenger_id):
                return jsonify({"error": "forbidden"}), 403
            cur.execute(
                """
                UPDATE messenger_chat_members
                SET last_read_id = GREATEST(last_read_id, %s)
                WHERE chat_id = %s AND user_id = %s
                """,
                (last_id, chat_id, messenger_id),
            )
        return jsonify({"ok": True})

    def _avatar_response(messenger_id: str, kind: str, owner_id: str):
        if kind not in ("user", "group") or not _valid_id(owner_id):
            return jsonify({"error": "not_found"}), 404
        with db.cursor() as cur:
            if not _require_user(cur, messenger_id):
                return jsonify({"error": "not_registered"}), 404
            if kind == "group":
                cur.execute(
                    "SELECT 1 FROM messenger_group_members WHERE group_id = %s AND user_id = %s",
                    (owner_id, messenger_id),
                )
                if not cur.fetchone():
                    return jsonify({"error": "forbidden"}), 403
        path = _avatar_file(kind, owner_id)
        if not path.is_file():
            return jsonify({"error": "not_found"}), 404
        try:
            with path.open("rb") as fh:
                head = fh.read(16)
        except OSError:
            head = b""
        return send_file(
            path,
            mimetype=_avatar_mime(head) or "application/octet-stream",
            as_attachment=False,
        )

    @app.get("/api/v1/messenger/avatar/user/<owner_id>")
    @guard(need_user=True)
    def api_messenger_avatar_user(messenger_id: str, owner_id: str):
        return _avatar_response(messenger_id, "user", owner_id)

    @app.get("/api/v1/messenger/avatar/group/<owner_id>")
    @guard(need_user=True)
    def api_messenger_avatar_group(messenger_id: str, owner_id: str):
        return _avatar_response(messenger_id, "group", owner_id)

    @app.post("/api/v1/messenger/me/avatar")
    @guard(need_user=True)
    def api_messenger_upload_my_avatar(messenger_id: str):
        upload = request.files.get("file")
        if upload is None:
            return jsonify({"error": "file_required"}), 400
        raw = upload.read(MAX_AVATAR_BYTES + 1)
        if not raw or len(raw) > MAX_AVATAR_BYTES:
            return jsonify({"error": "file_too_large"}), 400
        if not _avatar_mime(raw):
            return jsonify({"error": "bad_image"}), 400
        with db.cursor() as cur:
            user = _require_user(cur, messenger_id)
            if not user:
                return jsonify({"error": "not_registered"}), 404
            if not _save_avatar("user", messenger_id, raw):
                return jsonify({"error": "store_failed"}), 500
        return jsonify({"user": _user_json(user)})

    @app.delete("/api/v1/messenger/me/avatar")
    @guard(need_user=True)
    def api_messenger_delete_my_avatar(messenger_id: str):
        with db.cursor() as cur:
            if not _require_user(cur, messenger_id):
                return jsonify({"error": "not_registered"}), 404
        _drop_avatar("user", messenger_id)
        return jsonify({"ok": True})

    @app.post("/api/v1/messenger/groups/<group_id>/avatar")
    @guard(need_user=True)
    def api_messenger_upload_group_avatar(messenger_id: str, group_id: str):
        if not _valid_id(group_id):
            return jsonify({"error": "not_found"}), 404
        upload = request.files.get("file")
        if upload is None:
            return jsonify({"error": "file_required"}), 400
        raw = upload.read(MAX_AVATAR_BYTES + 1)
        if not raw or len(raw) > MAX_AVATAR_BYTES:
            return jsonify({"error": "file_too_large"}), 400
        if not _avatar_mime(raw):
            return jsonify({"error": "bad_image"}), 400
        with db.cursor() as cur:
            group = _group_row(cur, group_id)
            if not group:
                return jsonify({"error": "not_found"}), 404
            if group["challenge_key"]:
                return jsonify({"error": "challenge_locked"}), 403
            if group["owner_id"] != messenger_id:
                return jsonify({"error": "forbidden"}), 403
            if not _save_avatar("group", group_id, raw):
                return jsonify({"error": "store_failed"}), 500
        return jsonify({"avatar_url": _avatar_url("group", group_id)})

    @app.delete("/api/v1/messenger/groups/<group_id>/avatar")
    @guard(need_user=True)
    def api_messenger_delete_group_avatar(messenger_id: str, group_id: str):
        if not _valid_id(group_id):
            return jsonify({"error": "not_found"}), 404
        with db.cursor() as cur:
            group = _group_row(cur, group_id)
            if not group:
                return jsonify({"error": "not_found"}), 404
            if group["challenge_key"]:
                return jsonify({"error": "challenge_locked"}), 403
            if group["owner_id"] != messenger_id:
                return jsonify({"error": "forbidden"}), 403
        _drop_avatar("group", group_id)
        return jsonify({"ok": True})

    @app.post("/api/v1/messenger/messages/<int:message_id>/edit")
    @guard(need_user=True)
    def api_messenger_edit_message(messenger_id: str, message_id: int):
        payload = request.get_json(silent=True) or {}
        body = str(payload.get("body") or "").strip()[:MAX_TEXT]
        if not body:
            return jsonify({"error": "empty"}), 400
        with db.cursor() as cur:
            cur.execute(
                "SELECT id, chat_id, sender_id, kind, deleted FROM messenger_messages WHERE id = %s",
                (message_id,),
            )
            row = cur.fetchone()
            if not row or bool(int(row.get("deleted") or 0)):
                return jsonify({"error": "not_found"}), 404
            if row["sender_id"] != messenger_id:
                return jsonify({"error": "forbidden"}), 403
            if row["kind"] != "text":
                return jsonify({"error": "not_editable"}), 400
            if not _is_member(cur, row["chat_id"], messenger_id):
                return jsonify({"error": "forbidden"}), 403
            cur.execute(
                "UPDATE messenger_messages SET body = %s, edited_at = %s WHERE id = %s",
                (body, db.utc_now(), message_id),
            )
            message = _load_message(cur, message_id)
            names = _names_for(cur, [messenger_id])
        return jsonify({"message": _message_json(message, messenger_id, names)})

    @app.delete("/api/v1/messenger/messages/<int:message_id>")
    @guard(need_user=True)
    def api_messenger_delete_message(messenger_id: str, message_id: int):
        with db.cursor() as cur:
            cur.execute(
                "SELECT id, chat_id, sender_id, kind, deleted FROM messenger_messages WHERE id = %s",
                (message_id,),
            )
            row = cur.fetchone()
            if not row or bool(int(row.get("deleted") or 0)):
                return jsonify({"error": "not_found"}), 404
            if row["sender_id"] != messenger_id:
                return jsonify({"error": "forbidden"}), 403
            if not _is_member(cur, row["chat_id"], messenger_id):
                return jsonify({"error": "forbidden"}), 403
            if row["kind"] == "voice":
                _drop_voice_file(message_id)
            cur.execute(
                "UPDATE messenger_messages SET deleted = 1, body = '', voice_path = '' WHERE id = %s",
                (message_id,),
            )
            message = _load_message(cur, message_id)
            names = _names_for(cur, [messenger_id])
        return jsonify({"message": _message_json(message, messenger_id, names)})

    @app.post("/api/v1/messenger/groups/<group_id>")
    @guard(need_user=True)
    def api_messenger_rename_group(messenger_id: str, group_id: str):
        if not _valid_id(group_id):
            return jsonify({"error": "not_found"}), 404
        payload = request.get_json(silent=True) or {}
        name = _clean_name(str(payload.get("name") or ""))
        if not name:
            return jsonify({"error": "name_required"}), 400
        with db.cursor() as cur:
            group = _group_row(cur, group_id)
            if not group:
                return jsonify({"error": "not_found"}), 404
            if group["challenge_key"]:
                return jsonify({"error": "challenge_locked"}), 403
            if group["owner_id"] != messenger_id:
                return jsonify({"error": "forbidden"}), 403
            cur.execute(
                "UPDATE messenger_groups SET name = %s WHERE id = %s",
                (name, group_id),
            )
        return jsonify({"group": {"id": group_id, "name": name, "owner_id": messenger_id}})

    @app.delete("/api/v1/messenger/groups/<group_id>")
    @guard(need_user=True)
    def api_messenger_delete_group(messenger_id: str, group_id: str):
        if not _valid_id(group_id):
            return jsonify({"error": "not_found"}), 404
        with db.cursor() as cur:
            group = _group_row(cur, group_id)
            if not group:
                return jsonify({"error": "not_found"}), 404
            if group["challenge_key"]:
                return jsonify({"error": "challenge_locked"}), 403
            if group["owner_id"] != messenger_id:
                return jsonify({"error": "forbidden"}), 403
            cur.execute("SELECT id FROM messenger_chats WHERE group_id = %s", (group_id,))
            for chat in cur.fetchall():
                _drop_chat(cur, chat["id"])
            cur.execute("DELETE FROM messenger_topics WHERE group_id = %s", (group_id,))
            cur.execute("DELETE FROM messenger_invites WHERE group_id = %s", (group_id,))
            cur.execute("DELETE FROM messenger_group_members WHERE group_id = %s", (group_id,))
            cur.execute("DELETE FROM messenger_groups WHERE id = %s", (group_id,))
        _drop_avatar("group", group_id)
        return jsonify({"ok": True})

    @app.delete("/api/v1/messenger/groups/<group_id>/members/<user_id>")
    @guard(need_user=True)
    def api_messenger_remove_member(messenger_id: str, group_id: str, user_id: str):
        if not _valid_id(group_id) or not _valid_id(user_id):
            return jsonify({"error": "not_found"}), 404
        with db.cursor() as cur:
            group = _group_row(cur, group_id)
            if not group:
                return jsonify({"error": "not_found"}), 404
            if group["owner_id"] != messenger_id:
                return jsonify({"error": "forbidden"}), 403
            if user_id == group["owner_id"]:
                return jsonify({"error": "owner_immutable"}), 400
            cur.execute(
                "DELETE FROM messenger_group_members WHERE group_id = %s AND user_id = %s",
                (group_id, user_id),
            )
            cur.execute("SELECT id FROM messenger_chats WHERE group_id = %s", (group_id,))
            for chat in cur.fetchall():
                cur.execute(
                    "DELETE FROM messenger_chat_members WHERE chat_id = %s AND user_id = %s",
                    (chat["id"], user_id),
                )
        return jsonify({"ok": True})

    @app.get("/api/v1/messenger/groups/<group_id>/topics")
    @guard(need_user=True)
    def api_messenger_topics(messenger_id: str, group_id: str):
        if not _valid_id(group_id):
            return jsonify({"error": "not_found"}), 404
        with db.cursor() as cur:
            group = _group_row(cur, group_id)
            if not group:
                return jsonify({"error": "not_found"}), 404
            is_admin = _admin_ok(request.headers.get("X-Admin-Code") or "")
            is_ideas = (group.get("challenge_key") or "") == IDEAS_KEY
            if is_ideas:
                _ensure_ideas_membership(cur, messenger_id)
            if not _is_group_member(cur, group_id, messenger_id):
                return jsonify({"error": "forbidden"}), 403
            items = []
            if not is_ideas:
                general_chat = _ensure_group_chat(cur, group_id)
                items.append(_topic_json(cur, "", "", general_chat, messenger_id, is_default=True))
            cur.execute(
                """
                SELECT id, name, author_id FROM messenger_topics
                WHERE group_id = %s
                ORDER BY created_at ASC, id ASC
                """,
                (group_id,),
            )
            for row in cur.fetchall():
                author_id = str(row.get("author_id") or "")
                if is_ideas and not is_admin and author_id != messenger_id:
                    continue
                chat_id = _ensure_topic_chat(cur, group_id, row["id"])
                item = _topic_json(cur, row["id"], row["name"], chat_id, messenger_id)
                if is_ideas:
                    item["author_name"] = _names_for(cur, [author_id]).get(author_id, "")
                items.append(item)
        return jsonify({"topics": items, "is_admin": is_admin})

    @app.post("/api/v1/messenger/groups/<group_id>/topics")
    @guard(need_user=True)
    def api_messenger_create_topic(messenger_id: str, group_id: str):
        if not _valid_id(group_id):
            return jsonify({"error": "not_found"}), 404
        payload = request.get_json(silent=True) or {}
        name = _clean_name(str(payload.get("name") or ""))
        if not name:
            return jsonify({"error": "name_required"}), 400
        with db.cursor() as cur:
            group = _group_row(cur, group_id)
            if not group:
                return jsonify({"error": "not_found"}), 404
            if group["challenge_key"]:
                return jsonify({"error": "challenge_locked"}), 403
            if group["owner_id"] != messenger_id:
                return jsonify({"error": "forbidden"}), 403
            topic_id = _new_id()
            cur.execute(
                """
                INSERT INTO messenger_topics (id, group_id, name, created_at)
                VALUES (%s, %s, %s, %s)
                """,
                (topic_id, group_id, name, db.utc_now()),
            )
            chat_id = _ensure_topic_chat(cur, group_id, topic_id)
            item = _topic_json(cur, topic_id, name, chat_id, messenger_id)
        return jsonify({"topic": item})

    @app.post("/api/v1/messenger/groups/<group_id>/topics/<topic_id>")
    @guard(need_user=True)
    def api_messenger_rename_topic(messenger_id: str, group_id: str, topic_id: str):
        if not _valid_id(group_id) or not _valid_id(topic_id):
            return jsonify({"error": "not_found"}), 404
        payload = request.get_json(silent=True) or {}
        name = _clean_name(str(payload.get("name") or ""))
        if not name:
            return jsonify({"error": "name_required"}), 400
        with db.cursor() as cur:
            group = _group_row(cur, group_id)
            if not group:
                return jsonify({"error": "not_found"}), 404
            if group["challenge_key"]:
                return jsonify({"error": "challenge_locked"}), 403
            if group["owner_id"] != messenger_id:
                return jsonify({"error": "forbidden"}), 403
            cur.execute(
                "SELECT id FROM messenger_topics WHERE id = %s AND group_id = %s",
                (topic_id, group_id),
            )
            if not cur.fetchone():
                return jsonify({"error": "topic_not_found"}), 404
            cur.execute(
                "UPDATE messenger_topics SET name = %s WHERE id = %s",
                (name, topic_id),
            )
        return jsonify({"topic": {"id": topic_id, "name": name}})

    @app.delete("/api/v1/messenger/groups/<group_id>/topics/<topic_id>")
    @guard(need_user=True)
    def api_messenger_delete_topic(messenger_id: str, group_id: str, topic_id: str):
        if not _valid_id(group_id) or not _valid_id(topic_id):
            return jsonify({"error": "not_found"}), 404
        with db.cursor() as cur:
            group = _group_row(cur, group_id)
            if not group:
                return jsonify({"error": "not_found"}), 404
            if group["challenge_key"]:
                return jsonify({"error": "challenge_locked"}), 403
            if group["owner_id"] != messenger_id:
                return jsonify({"error": "forbidden"}), 403
            cur.execute(
                "SELECT id FROM messenger_topics WHERE id = %s AND group_id = %s",
                (topic_id, group_id),
            )
            if not cur.fetchone():
                return jsonify({"error": "topic_not_found"}), 404
            cur.execute(
                "SELECT id FROM messenger_chats WHERE topic_id = %s AND kind = 'topic'",
                (topic_id,),
            )
            for chat in cur.fetchall():
                _drop_chat(cur, chat["id"])
            cur.execute(
                "DELETE FROM messenger_topics WHERE id = %s AND group_id = %s",
                (topic_id, group_id),
            )
        return jsonify({"ok": True})
