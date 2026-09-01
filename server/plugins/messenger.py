"""Experimental messenger: QR pairing, groups, text and voice. Isolated plugin."""
from __future__ import annotations

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
SETTING_KEY = "messenger_enabled"


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
                created_at DATETIME NOT NULL
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
                KEY messenger_messages_chat (chat_id, id)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        )


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


def _is_member(cur, chat_id: str, user_id: str) -> bool:
    cur.execute(
        "SELECT 1 FROM messenger_chat_members WHERE chat_id = %s AND user_id = %s",
        (chat_id, user_id),
    )
    return cur.fetchone() is not None


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
            "SELECT id, name, owner_id FROM messenger_groups WHERE id = %s",
            (group_id,),
        )
        group = cur.fetchone() or {}
        title = group.get("name") or ""
        is_owner = group.get("owner_id") == me
    cur.execute(
        """
        SELECT id, kind, body, sender_id,
               UNIX_TIMESTAMP(created_at) AS created_unix
        FROM messenger_messages
        WHERE chat_id = %s
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
    return {
        "id": chat["id"],
        "kind": kind,
        "title": title,
        "peer_id": peer_id,
        "group_id": group_id,
        "is_owner": is_owner,
        "last_body": preview,
        "last_kind": last_kind,
        "last_at": last_at,
        "unread": _unread(cur, chat["id"], me, last_read),
    }


def _message_json(row: dict, me: str, names: dict[str, str]) -> dict:
    sender = row.get("sender_id") or ""
    return {
        "id": int(row["id"]),
        "chat_id": row["chat_id"],
        "sender_id": sender,
        "sender_name": names.get(sender) or "",
        "kind": row["kind"],
        "body": row.get("body") or "",
        "voice_duration_ms": int(row.get("voice_duration_ms") or 0),
        "created_at": _ms(row, "created_unix"),
        "mine": sender == me,
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

    @app.get("/api/v1/messenger/chats")
    @guard(need_user=True)
    def api_messenger_chats(messenger_id: str):
        with db.cursor() as cur:
            user = _require_user(cur, messenger_id)
            if not user:
                return jsonify({"error": "not_registered"}), 404
            cur.execute(
                """
                SELECT c.id, c.kind, c.group_id, c.pair_key,
                       UNIX_TIMESTAMP(c.last_message_at) AS last_unix
                FROM messenger_chats c
                JOIN messenger_chat_members me ON me.chat_id = c.id AND me.user_id = %s
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
        with db.cursor() as cur:
            if not _is_member(cur, chat_id, messenger_id):
                return jsonify({"error": "forbidden"}), 403
            cur.execute(
                """
                SELECT id, chat_id, sender_id, kind, body, voice_duration_ms,
                       UNIX_TIMESTAMP(created_at) AS created_unix
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
        with db.cursor() as cur:
            if not _is_member(cur, chat_id, messenger_id):
                return jsonify({"error": "forbidden"}), 403
            message_id = _insert_message(cur, chat_id, messenger_id, "text", body)
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
        with db.cursor() as cur:
            if not _is_member(cur, chat_id, messenger_id):
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
