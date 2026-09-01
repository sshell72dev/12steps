from __future__ import annotations

import base64
import ipaddress
import json
import urllib.error
import urllib.request
import uuid
from typing import Any

import config
import db

API_BASE = "https://api.yookassa.ru/v3"

# https://yookassa.ru/developers/using-api/webhooks#ip
_WEBHOOK_NETS = (
    ipaddress.ip_network("185.71.76.0/27"),
    ipaddress.ip_network("185.71.77.0/27"),
    ipaddress.ip_network("77.75.153.0/25"),
    ipaddress.ip_network("77.75.156.11/32"),
    ipaddress.ip_network("77.75.156.35/32"),
    ipaddress.ip_network("2a02:5180::/32"),
)


def shop_id() -> str:
    return (
        db.get_setting("yookassa_shop_id", "")
        or config.getenv("YOOKASSA_SHOP_ID", "")
    ).strip()


def secret_key() -> str:
    return (
        db.get_setting("yookassa_secret_key", "")
        or config.getenv("YOOKASSA_SECRET_KEY", "")
    ).strip()


def is_configured() -> bool:
    return bool(shop_id() and secret_key())


def premium_days() -> int:
    raw = db.get_setting("premium_days_after_payment", "365").strip() or "365"
    try:
        days = int(float(raw.replace(",", ".")))
    except ValueError:
        days = 365
    return max(1, min(days, 3650))


def amount_value() -> str:
    raw = (db.get_setting("premium_price_rub", "199") or "199").strip().replace(",", ".")
    try:
        val = float(raw)
    except ValueError:
        val = 199.0
    if val < 1:
        val = 1.0
    return f"{val:.2f}"


def default_return_url() -> str:
    return f"https://{config.DOMAIN}/premium/return"


def _auth_header() -> str:
    token = base64.b64encode(f"{shop_id()}:{secret_key()}".encode("utf-8")).decode("ascii")
    return f"Basic {token}"


def _request(
    method: str,
    path: str,
    payload: dict[str, Any] | None = None,
    *,
    idempotence_key: str | None = None,
) -> dict[str, Any]:
    if not is_configured():
        raise RuntimeError("ЮKassa не настроена (shopId / secretKey)")
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {
        "Authorization": _auth_header(),
        "Accept": "application/json",
        "Content-Type": "application/json",
    }
    if idempotence_key:
        headers["Idempotence-Key"] = idempotence_key
    req = urllib.request.Request(
        f"{API_BASE}{path}",
        data=body,
        headers=headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")[:600]
        raise RuntimeError(f"ЮKassa HTTP {exc.code}: {detail}") from exc
    if not raw:
        return {}
    return json.loads(raw)


def create_payment(
    *,
    device_id: str,
    return_url: str | None = None,
    description: str = "Premium · 12 шагов",
) -> dict[str, Any]:
    device_id = (device_id or "").strip()[:64]
    if not device_id:
        raise ValueError("device_id required")
    amount = amount_value()
    ret = (return_url or "").strip() or default_return_url()
    payload = {
        "amount": {"value": amount, "currency": "RUB"},
        "capture": True,
        "confirmation": {
            "type": "redirect",
            "return_url": ret,
            "locale": "ru_RU",
        },
        "description": (description or "Premium · 12 шагов")[:128],
        "metadata": {
            "device_id": device_id,
            "app": "12steps",
        },
    }
    data = _request(
        "POST",
        "/payments",
        payload,
        idempotence_key=str(uuid.uuid4()),
    )
    payment_id = str(data.get("id") or "")
    status = str(data.get("status") or "pending")
    confirmation = data.get("confirmation") or {}
    confirmation_url = str(confirmation.get("confirmation_url") or "")
    if not payment_id or not confirmation_url:
        raise RuntimeError("ЮKassa не вернула confirmation_url")
    db.upsert_premium_payment(
        payment_id=payment_id,
        device_id=device_id,
        amount=amount,
        currency="RUB",
        status=status,
        confirmation_url=confirmation_url,
        raw_json=json.dumps(data, ensure_ascii=False),
    )
    return {
        "payment_id": payment_id,
        "confirmation_url": confirmation_url,
        "status": status,
        "amount": amount,
        "currency": "RUB",
    }


def get_payment(payment_id: str) -> dict[str, Any]:
    payment_id = (payment_id or "").strip()
    if not payment_id:
        raise ValueError("payment_id required")
    return _request("GET", f"/payments/{payment_id}")


def client_ip_allowed(remote_addr: str | None) -> bool:
    """True if IP is in YooKassa webhook ranges (or empty/unknown → False)."""
    raw = (remote_addr or "").strip()
    if not raw:
        return False
    # X-Forwarded-For may pass comma-separated list; take first.
    if "," in raw:
        raw = raw.split(",", 1)[0].strip()
    try:
        ip = ipaddress.ip_address(raw)
    except ValueError:
        return False
    return any(ip in net for net in _WEBHOOK_NETS)


def apply_succeeded_payment(payment: dict[str, Any]) -> dict[str, Any]:
    """Idempotently mark payment succeeded and extend entitlement. Verified object from API."""
    payment_id = str(payment.get("id") or "").strip()
    if not payment_id:
        raise ValueError("empty payment id")
    status = str(payment.get("status") or "")
    if status != "succeeded":
        db.upsert_premium_payment(
            payment_id=payment_id,
            device_id=str((payment.get("metadata") or {}).get("device_id") or ""),
            amount=str((payment.get("amount") or {}).get("value") or ""),
            currency=str((payment.get("amount") or {}).get("currency") or "RUB"),
            status=status or "unknown",
            confirmation_url="",
            raw_json=json.dumps(payment, ensure_ascii=False),
        )
        return {"applied": False, "reason": "not_succeeded", "status": status}

    metadata = payment.get("metadata") or {}
    device_id = str(metadata.get("device_id") or "").strip()
    existing = db.get_premium_payment(payment_id)
    if existing and not device_id:
        device_id = str(existing.get("device_id") or "")
    if not device_id:
        raise ValueError("device_id missing in payment metadata")

    amount = str((payment.get("amount") or {}).get("value") or "")
    currency = str((payment.get("amount") or {}).get("currency") or "RUB")
    newly = db.mark_premium_payment_succeeded(
        payment_id=payment_id,
        device_id=device_id,
        amount=amount,
        currency=currency,
        days=premium_days(),
        raw_json=json.dumps(payment, ensure_ascii=False),
    )
    ent = db.get_premium_entitlement(device_id) or {}
    return {
        "applied": newly,
        "device_id": device_id,
        "payment_id": payment_id,
        "expires_at": ent.get("expires_at") or "",
    }
