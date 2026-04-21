"""
VTB Vita — Async database layer (SQLite + aiosqlite).
All application data previously in data.py USERS dict is now persisted here.
"""

import json
import logging
import os
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Optional

import aiosqlite

_log = logging.getLogger("vita.db")

DB_PATH = os.getenv("DB_PATH", str(Path(__file__).parent / "vita.db"))
_SCHEMA_PATH = Path(__file__).parent / "schema.sql"

_conn: Optional[aiosqlite.Connection] = None


# ── Helpers ──────────────────────────────────────────────────────────────────

def _j(v) -> Optional[str]:
    return json.dumps(v, ensure_ascii=False) if v is not None else None


def _row_to_dict(row: aiosqlite.Row, json_cols: Optional[list] = None) -> dict:
    d = dict(row)
    for col in (json_cols or []):
        if col in d and d[col] is not None:
            d[col] = json.loads(d[col])
    return d


def _norm_phone(phone: str) -> str:
    digits = "".join(c for c in phone if c.isdigit())
    if len(digits) == 11 and digits[0] in ("7", "8"):
        return "7" + digits[1:]
    if len(digits) == 10:
        return "7" + digits
    return digits


# ── Lifecycle ─────────────────────────────────────────────────────────────────

async def init_db() -> None:
    global _conn
    # Clean up orphaned WAL/SHM files: if the main DB was deleted but WAL remains,
    # SQLite raises SQLITE_IOERR when it finds WAL pointing to a non-existent DB.
    db_path = Path(DB_PATH)
    if not db_path.exists():
        for stale in (Path(DB_PATH + "-wal"), Path(DB_PATH + "-shm")):
            stale.unlink(missing_ok=True)

    _conn = await aiosqlite.connect(DB_PATH)
    _conn.row_factory = aiosqlite.Row
    await _conn.execute("PRAGMA foreign_keys=ON")
    # executescript issues implicit COMMIT first — the correct way to run DDL scripts
    await _conn.executescript(_SCHEMA_PATH.read_text())

    async with _conn.execute("SELECT COUNT(*) FROM users") as cur:
        count = (await cur.fetchone())[0]
    if count == 0:
        _log.info("DB empty — seeding mock data...")
        await seed_db(_conn)

    now_iso = datetime.now(timezone.utc).isoformat()
    await _conn.execute("DELETE FROM pending_transactions WHERE expires_at < ?", (now_iso,))
    await _conn.commit()
    _log.info("DB ready: path=%s", DB_PATH)


async def close_db() -> None:
    global _conn
    if _conn:
        await _conn.close()
        _conn = None


async def get_connection() -> aiosqlite.Connection:
    return _conn


# ── Seeding ───────────────────────────────────────────────────────────────────

async def seed_db(conn: aiosqlite.Connection, reset: bool = False) -> None:
    """Seed all mock data. Idempotent (INSERT OR IGNORE) unless reset=True."""
    from data import USERS

    if reset:
        for table in ("pending_transactions", "transactions", "scheduled_payments",
                      "contacts", "accounts", "users"):
            await conn.execute(f"DELETE FROM {table}")
        await conn.commit()

    for user_id, user in USERS.items():
        p = user["profile"]
        rfm = p.get("rfm", {})
        await conn.execute(
            """INSERT OR IGNORE INTO users VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            (
                p["user_id"], p["full_name"], p["display_name"], p["birth_date"],
                p["age"], p["phone"], p.get("email"), p.get("city"),
                p["segment"], p["score"],
                rfm.get("recency_days", 0), rfm.get("frequency_30d", 0),
                rfm.get("monetary_30d", 0),
                int(p.get("is_salary_client", False)), p.get("salary_day"),
                p.get("months_as_client", 0),
                _j(p.get("active_products", [])),
            ),
        )

        for acc in user["accounts"]:
            await conn.execute(
                """INSERT OR IGNORE INTO accounts VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                (
                    acc["account_id"], user_id, acc["account_type"], acc["name"],
                    acc.get("pan_masked"), acc.get("payment_system", "mir"),
                    acc["balance"], acc.get("currency", "RUB"),
                    acc.get("status", "Enabled"), acc["open_date"],
                    acc.get("cashback_category"), acc.get("cashback_rate"),
                    acc.get("credit_limit"), acc.get("available_credit"),
                    acc.get("min_payment"), acc.get("payment_due_date"),
                    acc.get("interest_rate"), acc.get("interest_accrued"),
                ),
            )

        for key, c in user["contacts"].items():
            await conn.execute(
                """INSERT OR IGNORE INTO contacts
                   (user_id, lookup_key, display_name, full_name,
                    phone, bank, available_banks, pan_masked,
                    transfer_count, last_transfer_date)
                   VALUES (?,?,?,?,?,?,?,?,?,?)""",
                (
                    user_id, key, c["display_name"], c["full_name"],
                    _norm_phone(c["phone"]),  # store normalized: 7XXXXXXXXXX
                    c["bank"], _j(c.get("available_banks", [])),
                    c.get("pan_masked"), c.get("transfer_count", 0),
                    c.get("last_transfer_date"),
                ),
            )

        for sp in user["scheduled_payments"]:
            await conn.execute(
                """INSERT OR IGNORE INTO scheduled_payments VALUES (?,?,?,?,?,?,?,?,?)""",
                (
                    sp["id"], user_id, sp["name"], sp["amount"],
                    sp.get("day_of_month"), sp.get("weekday"),
                    sp.get("category"), sp.get("mcc"),
                    sp.get("status", "Active"),
                ),
            )

        for txn in user["transactions"]:
            await conn.execute(
                """INSERT OR IGNORE INTO transactions VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                (
                    txn["id"], user_id, txn["account_id"],
                    txn["booking_date"], txn.get("value_date", txn["booking_date"]),
                    txn["type"], txn["amount"], txn.get("currency", "RUB"),
                    txn["direction"], txn.get("status", "Posted"),
                    txn.get("category"), txn.get("description"),
                    _j(txn.get("merchant")), _j(txn.get("recipient")),
                    _j(txn.get("sender")),
                ),
            )

    await conn.commit()
    _log.info("Seeded %d users", len(USERS))


# ── Phone index ───────────────────────────────────────────────────────────────

async def build_phone_index() -> dict:
    """SELECT all contacts → {normalized_phone: (user_id, lookup_key)}."""
    index: dict = {}
    seen: set = set()
    async with _conn.execute("SELECT user_id, lookup_key, phone FROM contacts") as cur:
        async for row in cur:
            norm = _norm_phone(row["phone"])
            if norm and norm not in seen:
                index[norm] = (row["user_id"], row["lookup_key"])
                seen.add(norm)
    return index


# ── User / Account ────────────────────────────────────────────────────────────

async def get_user(user_id: str) -> Optional[dict]:
    async with _conn.execute(
        "SELECT * FROM users WHERE user_id = ?", (user_id,)
    ) as cur:
        row = await cur.fetchone()
        return _row_to_dict(row, ["active_products"]) if row else None


async def get_accounts(user_id: str) -> list:
    async with _conn.execute(
        "SELECT * FROM accounts WHERE user_id = ? ORDER BY rowid", (user_id,)
    ) as cur:
        rows = await cur.fetchall()
        return [dict(row) for row in rows]


async def get_account(user_id: str, account_id: str) -> Optional[dict]:
    async with _conn.execute(
        "SELECT * FROM accounts WHERE user_id = ? AND account_id = ?",
        (user_id, account_id),
    ) as cur:
        row = await cur.fetchone()
        return dict(row) if row else None


async def update_balance(user_id: str, account_id: str, new_balance: float) -> None:
    await _conn.execute(
        "UPDATE accounts SET balance = ? WHERE user_id = ? AND account_id = ?",
        (new_balance, user_id, account_id),
    )
    await _conn.commit()


# ── Contacts ──────────────────────────────────────────────────────────────────

async def get_contact(user_id: str, lookup_key: str) -> Optional[dict]:
    async with _conn.execute(
        "SELECT * FROM contacts WHERE user_id = ? AND lookup_key = ?",
        (user_id, lookup_key),
    ) as cur:
        row = await cur.fetchone()
        return _row_to_dict(row, ["available_banks"]) if row else None


async def get_contact_by_phone(phone: str) -> Optional[dict]:
    """Normalize phone before calling: expects 7XXXXXXXXXX format."""
    async with _conn.execute(
        "SELECT * FROM contacts WHERE phone = ? LIMIT 1", (phone,)
    ) as cur:
        row = await cur.fetchone()
        return _row_to_dict(row, ["available_banks"]) if row else None


# ── Pending transactions ──────────────────────────────────────────────────────

async def create_pending(
    txn_id: str,
    user_id: str,
    intent: str,
    amount: float,
    display_name: Optional[str],
    phone: Optional[str],
    requires_manual_input: bool,
) -> None:
    now = datetime.now(timezone.utc)
    expires = now + timedelta(minutes=15)
    await _conn.execute(
        """INSERT INTO pending_transactions
           (txn_id, user_id, intent, amount, display_name, phone,
            requires_manual_input, created_at, expires_at)
           VALUES (?,?,?,?,?,?,?,?,?)""",
        (
            txn_id, user_id, intent, amount, display_name, phone,
            int(requires_manual_input),
            now.isoformat(), expires.isoformat(),
        ),
    )
    await _conn.commit()


async def pop_pending(txn_id: str) -> Optional[dict]:
    """Fetch-and-delete a pending transaction. Returns None if not found or expired."""
    now_iso = datetime.now(timezone.utc).isoformat()
    async with _conn.execute(
        "SELECT * FROM pending_transactions WHERE txn_id = ? AND expires_at > ?",
        (txn_id, now_iso),
    ) as cur:
        row = await cur.fetchone()
    if row is None:
        return None
    await _conn.execute("DELETE FROM pending_transactions WHERE txn_id = ?", (txn_id,))
    d = dict(row)
    d["requires_manual_input"] = bool(d["requires_manual_input"])
    return d


# ── Transactions ──────────────────────────────────────────────────────────────

async def insert_transaction(
    txn_id: str,
    user_id: str,
    account_id: str,
    intent: str,
    amount: float,
    display_name: Optional[str],
    phone: Optional[str],
    selected_bank: Optional[str],
) -> None:
    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    if intent == "transfer":
        txn_type = "Transfer"
        recipient_data = {"display_name": display_name or "Получатель", "bank": selected_bank or ""}
        category = "Переводы"
        description = f"Перевод {display_name or ''}".strip()
        merchant_data = None
        sender_data = None
    else:
        txn_type = "TopUp"
        recipient_data = {"phone": phone or "", "operator": ""}
        category = "Связь"
        description = "Пополнение телефона"
        merchant_data = None
        sender_data = None

    await _conn.execute(
        """INSERT INTO transactions
           (id, user_id, account_id, booking_date, value_date,
            type, amount, currency, direction, status,
            category, description, merchant, recipient, sender)
           VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
        (
            txn_id, user_id, account_id, today, today,
            txn_type, amount, "RUB", "debit", "Posted",
            category, description,
            _j(merchant_data), _j(recipient_data), _j(sender_data),
        ),
    )
    await _conn.commit()


async def get_transactions(user_id: str, limit: int = 50) -> list:
    async with _conn.execute(
        "SELECT * FROM transactions WHERE user_id = ? ORDER BY booking_date DESC LIMIT ?",
        (user_id, limit),
    ) as cur:
        rows = await cur.fetchall()
        return [_row_to_dict(row, ["merchant", "recipient", "sender"]) for row in rows]


async def get_scheduled_payments(user_id: str) -> list:
    async with _conn.execute(
        "SELECT * FROM scheduled_payments WHERE user_id = ?", (user_id,)
    ) as cur:
        rows = await cur.fetchall()
        return [dict(row) for row in rows]
