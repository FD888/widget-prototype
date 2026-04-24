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

    # Backfill intent_log for existing DBs that were created before this table existed
    async with _conn.execute("SELECT COUNT(*) FROM intent_log") as cur:
        intent_count = (await cur.fetchone())[0]
    if intent_count == 0 and count > 0:
        _log.info("intent_log empty — backfilling from transactions...")
        _intent_map = {"Transfer": "transfer", "TopUp": "topup"}
        async with _conn.execute(
            "SELECT user_id, type, booking_date FROM transactions WHERE direction='debit'"
        ) as cur:
            txn_rows = await cur.fetchall()
        for row in txn_rows:
            intent = _intent_map.get(row["type"])
            if intent:
                await _conn.execute(
                    "INSERT INTO intent_log (user_id, intent, created_at) VALUES (?,?,?)",
                    (row["user_id"], intent, row["booking_date"] + "T12:00:00+00:00"),
                )
        async with _conn.execute("SELECT DISTINCT user_id FROM users") as cur:
            user_rows = await cur.fetchall()
        for i, u in enumerate(user_rows):
            for j in range(3):
                await _conn.execute(
                    "INSERT INTO intent_log (user_id, intent, created_at) VALUES (?,?,?)",
                    (u["user_id"], "balance", f"2026-04-{10 + j:02d}T09:00:00+00:00"),
                )
        await _conn.commit()
        _log.info("Backfilled intent_log with %d entries", len(txn_rows))

    # Migration: add payment_type column to scheduled_payments if missing
    async with _conn.execute("PRAGMA table_info(scheduled_payments)") as cur:
        col_names = {row["name"] for row in await cur.fetchall()}
    if "payment_type" not in col_names:
        await _conn.execute(
            "ALTER TABLE scheduled_payments ADD COLUMN payment_type TEXT NOT NULL DEFAULT 'subscription'"
        )
        _log.info("migration: added payment_type column to scheduled_payments")

    # Migration: add comment column to pending_transactions if missing
    async with _conn.execute("PRAGMA table_info(pending_transactions)") as cur:
        pt_cols = {row["name"] for row in await cur.fetchall()}
    if "comment" not in pt_cols:
        await _conn.execute(
            "ALTER TABLE pending_transactions ADD COLUMN comment TEXT"
        )
        _log.info("migration: added comment column to pending_transactions")

    # Apply correct payment_type for known payment IDs (idempotent)
    _PAYMENT_TYPES = {
        "SUB_O01": "autopayment", "SUB_O02": "loan", "SUB_O03": "autopayment",
        "CC_A01": "credit_card",
    }
    for pid, ptype in _PAYMENT_TYPES.items():
        await _conn.execute(
            "UPDATE scheduled_payments SET payment_type = ? WHERE id = ?", (ptype, pid)
        )
    # Fix ТТК due date so reminder fires within 3-day window
    await _conn.execute(
        "UPDATE scheduled_payments SET day_of_month = 24, status = 'Active' WHERE id = 'SUB_O01'"
    )
    # Fix Ипотека: move outside reminder window
    await _conn.execute(
        "UPDATE scheduled_payments SET day_of_month = 28 WHERE id = 'SUB_O02'"
    )
    # Insert Артём credit card if not present yet
    await _conn.execute(
        """INSERT OR IGNORE INTO scheduled_payments
           (id, user_id, name, amount, day_of_month, weekday, category, mcc, status, payment_type)
           VALUES (?,?,?,?,?,?,?,?,?,?)""",
        ("CC_A01", "artyom", "Кредитная карта ВТБ", 2700.0, 24, None, "Кредиты", None, "Active", "credit_card"),
    )
    await _conn.commit()

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
                """INSERT OR IGNORE INTO scheduled_payments VALUES (?,?,?,?,?,?,?,?,?,?)""",
                (
                    sp["id"], user_id, sp["name"], sp["amount"],
                    sp.get("day_of_month"), sp.get("weekday"),
                    sp.get("category"), sp.get("mcc"),
                    sp.get("status", "Active"),
                    sp.get("payment_type", "subscription"),
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

    # Seed intent_log to match historical transactions (так дашборд выглядит заполненным с первого запуска)
    _INTENT_MAP = {"Transfer": "transfer", "TopUp": "topup"}
    async with conn.execute("SELECT COUNT(*) FROM intent_log") as cur:
        intent_count = (await cur.fetchone())[0]
    if intent_count == 0:
        for user_id, user in USERS.items():
            for txn in user["transactions"]:
                intent = _INTENT_MAP.get(txn["type"])
                if intent:
                    await conn.execute(
                        "INSERT OR IGNORE INTO intent_log (user_id, intent, created_at) VALUES (?,?,?)",
                        (user_id, intent, txn["booking_date"] + "T12:00:00+00:00"),
                    )
            # Несколько balance-запросов на пользователя для реалистичного распределения
            for i in range(3):
                await conn.execute(
                    "INSERT INTO intent_log (user_id, intent, created_at) VALUES (?,?,?)",
                    (user_id, "balance", f"2026-04-{10 + i:02d}T09:00:00+00:00"),
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
    comment: Optional[str] = None,
) -> None:
    now = datetime.now(timezone.utc)
    expires = now + timedelta(minutes=15)
    await _conn.execute(
        """INSERT INTO pending_transactions
           (txn_id, user_id, intent, amount, display_name, phone,
            comment, requires_manual_input, created_at, expires_at)
           VALUES (?,?,?,?,?,?,?,?,?,?)""",
        (
            txn_id, user_id, intent, amount, display_name, phone,
            comment, int(requires_manual_input),
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
    elif intent == "payment":
        txn_type = "Payment"
        recipient_data = {"display_name": display_name or "Получатель"}
        category = "Платежи"
        description = f"Оплата: {display_name or ''}".strip()
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


async def get_upcoming_reminder(user_id: str) -> Optional[dict]:
    """Return the most urgent upcoming payment reminder for the user, or None."""
    from datetime import date as _date
    today = _date.today()
    payments = await get_scheduled_payments(user_id)
    candidates = []

    for p in payments:
        ptype = p.get("payment_type", "subscription")
        if ptype not in ("credit_card", "loan", "autopayment"):
            continue
        dom = p.get("day_of_month")
        if not dom:
            continue
        try:
            due_date = _date(today.year, today.month, dom)
        except ValueError:
            continue

        days = (due_date - today).days

        if days < 0:
            # Overdue this month — only urgent for credit_card and loan
            if ptype in ("credit_card", "loan"):
                label = "просрочен"
                candidates.append({**p, "days_until_due": days, "is_overdue": True,
                                   "urgency": "urgent", "urgency_order": 0, "label": label})
        elif days == 0:
            if ptype in ("credit_card", "loan"):
                label = "сегодня последний день"
                candidates.append({**p, "days_until_due": 0, "is_overdue": False,
                                   "urgency": "urgent", "urgency_order": 0, "label": label})
        elif days == 1:
            order = 1 if ptype in ("credit_card", "loan") else 2
            candidates.append({**p, "days_until_due": 1, "is_overdue": False,
                               "urgency": "upcoming", "urgency_order": order, "label": "завтра"})
        elif 2 <= days <= 3:
            order = 1 if ptype in ("credit_card", "loan") else 2
            label = f"через {days} дня" if days <= 4 else f"через {days} дней"
            candidates.append({**p, "days_until_due": days, "is_overdue": False,
                               "urgency": "upcoming", "urgency_order": order, "label": label})

    if not candidates:
        return None
    # Lowest urgency_order first, then fewest days_until_due (abs for overdue)
    candidates.sort(key=lambda c: (c["urgency_order"], abs(c["days_until_due"])))
    return candidates[0]


async def _get_top_spending_category(user_id: str) -> Optional[str]:
    """Return the category with highest spend in the last 30 days."""
    cutoff = (datetime.now(timezone.utc) - timedelta(days=30)).strftime("%Y-%m-%d")
    async with _conn.execute(
        """SELECT category, SUM(amount) as total FROM transactions
           WHERE user_id = ? AND direction = 'debit' AND booking_date >= ?
             AND category IS NOT NULL AND category != ''
           GROUP BY category ORDER BY total DESC LIMIT 1""",
        (user_id, cutoff),
    ) as cur:
        row = await cur.fetchone()
        return row["category"] if row else None


async def _get_transfer_count_30d(user_id: str) -> int:
    """Return number of outgoing transfers in the last 30 days."""
    cutoff = (datetime.now(timezone.utc) - timedelta(days=30)).strftime("%Y-%m-%d")
    async with _conn.execute(
        """SELECT COUNT(*) as cnt FROM transactions
           WHERE user_id = ? AND type = 'Transfer' AND direction = 'debit'
             AND booking_date >= ?""",
        (user_id, cutoff),
    ) as cur:
        row = await cur.fetchone()
        return row["cnt"] if row else 0


async def _get_category_spend_30d(user_id: str) -> float:
    """Return total spend in top category over last 30 days."""
    cutoff = (datetime.now(timezone.utc) - timedelta(days=30)).strftime("%Y-%m-%d")
    async with _conn.execute(
        """SELECT COALESCE(SUM(amount), 0) as total FROM transactions
           WHERE user_id = ? AND direction = 'debit' AND booking_date >= ?
             AND category IS NOT NULL AND category != ''
           GROUP BY category ORDER BY total DESC LIMIT 1""",
        (user_id, cutoff),
    ) as cur:
        row = await cur.fetchone()
        return float(row["total"]) if row else 0.0


async def get_vygoda_offer(user_id: str) -> Optional[dict]:
    """Return the best ВЫГОДА offer for the user, or None."""
    user = await get_user(user_id)
    if not user:
        return None

    active = set(user.get("active_products") or [])
    age = user.get("age", 0)
    accounts = await get_accounts(user_id)

    debit_balance = sum(
        a["balance"] for a in accounts
        if a.get("account_type") == "CurrentAccount" and a["balance"] > 0
    )
    savings_balance = sum(
        a["balance"] for a in accounts
        if a.get("account_type") == "SavingsAccount"
    )
    max_balance = max(debit_balance, savings_balance)

    # Priority 1 — autopay_setup: any Active autopayment not yet auto-connected
    if "autopay" not in active:
        payments = await get_scheduled_payments(user_id)
        has_autopay_candidate = any(
            p.get("payment_type") == "autopayment" and p.get("status") == "Active"
            for p in payments
        )
        if has_autopay_candidate:
            autopay_names = [
                p["name"] for p in payments
                if p.get("payment_type") == "autopayment" and p.get("status") == "Active"
            ]
            names_str = " и ".join(autopay_names[:2])
            return {
                "offer_id": "autopay_setup",
                "offer_text": f"автоплатёж позволит не держать в голове плату за {names_str}",
                "offer_cta": "Подключить →",
                "offer_action": "open_autopay",
            }

    # Priority 2 — deposit_3m: debit > 80k, no deposit product
    if "deposit" not in active and debit_balance > 80_000:
        interest = round(debit_balance * 0.19 * 3 / 12)
        interest_str = f"{interest:,}".replace(",", " ")
        return {
            "offer_id": "deposit_3m",
            "offer_text": f"если перевести свободные деньги на вклад, за 3 мес заработаешь {interest_str} руб",
            "offer_cta": "Открыть вклад →",
            "offer_action": "open_deposit",
        }

    # Priority 3 — IIS: age 25-45, any balance > 50k, no iis product
    if "iis" not in active and 25 <= age <= 45 and max_balance > 50_000:
        return {
            "offer_id": "iis",
            "offer_text": "ИИС вернёт до 52 тыс руб из налогов — стоит глянуть",
            "offer_cta": "Подробнее →",
            "offer_action": "open_iis",
        }

    # Priority 4 — cashback_multi: no cashback product
    if "cashback" not in active:
        top_cat = await _get_top_spending_category(user_id)
        cat_label = top_cat if top_cat else "покупках"
        spending = await _get_category_spend_30d(user_id)
        cashback = round(spending * 0.05) if spending > 0 else 0
        if cashback > 0:
            return {
                "offer_id": "cashback_multi",
                "offer_text": f"можешь возвращать ~{cashback:,} руб в месяц кешбэком за {cat_label}".replace(",", " "),
                "offer_cta": "Подключить →",
                "offer_action": "open_cashback",
            }
        return {
            "offer_id": "cashback_multi",
            "offer_text": f"кешбэк на {cat_label} вернёт тебе деньги каждый месяц",
            "offer_cta": "Подключить →",
            "offer_action": "open_cashback",
        }

    # Priority 5 — prime: >= 5 transfers last 30d, no prime product
    if "prime" not in active:
        transfer_count = await _get_transfer_count_30d(user_id)
        if transfer_count >= 5:
            return {
                "offer_id": "prime",
                "offer_text": "с праймом переводы без комиссии — и приоритетная поддержка",
                "offer_cta": "Попробовать →",
                "offer_action": "open_prime",
            }

    # Priority 6 — round_up fallback
    if "round_up" not in active:
        return {
            "offer_id": "round_up",
            "offer_text": "округляй покупки — незаметно накопишь ~50 руб в месяц",
            "offer_cta": "Включить →",
            "offer_action": "open_round_up",
        }

    return None


# ── Intent log ────────────────────────────────────────────────────────────────

async def log_intent(user_id: Optional[str], intent: str) -> None:
    now = datetime.now(timezone.utc).isoformat()
    await _conn.execute(
        "INSERT INTO intent_log (user_id, intent, created_at) VALUES (?,?,?)",
        (user_id, intent, now),
    )
    await _conn.commit()


# ── Dashboard stats ───────────────────────────────────────────────────────────

async def get_dashboard_stats() -> dict:
    # Totals from transactions (debit = outgoing operations)
    txn_stats: dict = {}
    async with _conn.execute(
        """SELECT type, COUNT(*) as cnt, COALESCE(SUM(amount), 0) as vol
           FROM transactions WHERE direction = 'debit' GROUP BY type"""
    ) as cur:
        async for row in cur:
            txn_stats[row["type"]] = (row["cnt"], float(row["vol"]))

    transfers_count, transfers_volume = txn_stats.get("Transfer", (0, 0.0))
    topups_count, topups_volume = txn_stats.get("TopUp", (0, 0.0))

    # Intent distribution from intent_log
    intents: dict = {}
    async with _conn.execute(
        "SELECT intent, COUNT(*) as cnt FROM intent_log GROUP BY intent ORDER BY cnt DESC"
    ) as cur:
        async for row in cur:
            intents[row["intent"]] = row["cnt"]

    commands_sent = intents.get("transfer", 0) + intents.get("topup", 0)
    confirmed = transfers_count + topups_count

    # Activity by user
    by_user = []
    async with _conn.execute(
        """SELECT t.user_id, u.display_name,
                  COUNT(*) as cnt, COALESCE(SUM(t.amount), 0) as vol
           FROM transactions t JOIN users u ON t.user_id = u.user_id
           WHERE t.direction = 'debit'
           GROUP BY t.user_id ORDER BY cnt DESC"""
    ) as cur:
        async for row in cur:
            by_user.append({
                "user_id": row["user_id"],
                "display_name": row["display_name"],
                "count": row["cnt"],
                "volume": round(float(row["vol"]), 2),
            })

    # Transactions by day (last 30 days)
    cutoff = (datetime.now(timezone.utc) - timedelta(days=30)).strftime("%Y-%m-%d")
    by_day = []
    async with _conn.execute(
        """SELECT booking_date as date, COUNT(*) as count
           FROM transactions WHERE booking_date >= ? AND direction = 'debit'
           GROUP BY booking_date ORDER BY booking_date""",
        (cutoff,),
    ) as cur:
        async for row in cur:
            by_day.append({"date": row["date"], "count": row["count"]})

    # Recent transactions (last 20, all users)
    recent = []
    async with _conn.execute(
        """SELECT t.id, t.user_id, u.display_name, t.type, t.amount,
                  t.booking_date, t.description, t.direction
           FROM transactions t JOIN users u ON t.user_id = u.user_id
           ORDER BY t.booking_date DESC, t.rowid DESC LIMIT 20"""
    ) as cur:
        async for row in cur:
            recent.append(dict(row))

    return {
        "totals": {
            "transactions_count": int(transfers_count + topups_count),
            "transfers_count": int(transfers_count),
            "topups_count": int(topups_count),
            "transfers_volume": round(transfers_volume, 2),
            "topups_volume": round(topups_volume, 2),
            "m1_commission_estimate": round(topups_volume * 0.01, 2),
        },
        "funnel": {
            "commands_sent": int(commands_sent),
            "confirmed": int(confirmed),
            "conversion_rate": round(confirmed / commands_sent * 100, 1) if commands_sent > 0 else None,
        },
        "intents": intents,
        "by_user": by_user,
        "by_day": by_day,
        "recent_transactions": recent,
    }


# ── Hint overrides (admin CRUD) ────────────────────────────────────────────────

async def get_hint_override(user_id: str) -> Optional[dict]:
    async with _conn.execute(
        "SELECT * FROM hint_overrides WHERE user_id = ?", (user_id,)
    ) as cur:
        row = await cur.fetchone()
        return dict(row) if row else None


async def set_hint_override(
    user_id: str,
    hint_type: str = "auto",
    reminder_enabled: bool = True,
    vygoda_enabled: bool = True,
    forced_offer_id: Optional[str] = None,
    forced_payment_id: Optional[str] = None,
    custom_text: Optional[str] = None,
    custom_cta: Optional[str] = None,
    custom_action: Optional[str] = None,
) -> dict:
    now = datetime.now(timezone.utc).isoformat()
    await _conn.execute(
        """INSERT INTO hint_overrides
           (user_id, hint_type, reminder_enabled, vygoda_enabled,
            forced_offer_id, forced_payment_id, custom_text, custom_cta, custom_action, updated_at)
           VALUES (?,?,?,?,?,?,?,?,?,?)
           ON CONFLICT(user_id) DO UPDATE SET
             hint_type=excluded.hint_type,
             reminder_enabled=excluded.reminder_enabled,
             vygoda_enabled=excluded.vygoda_enabled,
             forced_offer_id=excluded.forced_offer_id,
             forced_payment_id=excluded.forced_payment_id,
             custom_text=excluded.custom_text,
             custom_cta=excluded.custom_cta,
             custom_action=excluded.custom_action,
             updated_at=excluded.updated_at""",
        (user_id, hint_type, int(reminder_enabled), int(vygoda_enabled),
         forced_offer_id, forced_payment_id, custom_text, custom_cta, custom_action, now),
    )
    await _conn.commit()
    return await get_hint_override(user_id)


async def delete_hint_override(user_id: str) -> bool:
    cursor = await _conn.execute(
        "DELETE FROM hint_overrides WHERE user_id = ?", (user_id,)
    )
    await _conn.commit()
    return cursor.rowcount > 0


async def get_all_hint_overrides() -> list:
    async with _conn.execute(
        "SELECT * FROM hint_overrides ORDER BY user_id"
    ) as cur:
        rows = await cur.fetchall()
        return [dict(row) for row in rows]


# ── User search ────────────────────────────────────────────────────────────────

async def search_users(q: str) -> list:
    pattern = f"%{q}%"
    async with _conn.execute(
        """SELECT user_id, full_name, display_name, segment, age, is_salary_client
           FROM users
           WHERE user_id LIKE ? OR full_name LIKE ? OR display_name LIKE ?
           ORDER BY user_id""",
        (pattern, pattern, pattern),
    ) as cur:
        rows = await cur.fetchall()
        results = []
        for row in rows:
            r = dict(row)
            r["is_salary_client"] = bool(r["is_salary_client"])
            accounts = await get_accounts(row["user_id"])
            r["debit_balance"] = sum(
                a["balance"] for a in accounts
                if a.get("account_type") == "CurrentAccount" and a["balance"] > 0
            )
            results.append(r)
        return results


async def get_all_users_brief() -> list:
    async with _conn.execute(
        """SELECT user_id, full_name, display_name, segment, age, is_salary_client
           FROM users ORDER BY user_id""",
    ) as cur:
        rows = await cur.fetchall()
        results = []
        for row in rows:
            r = dict(row)
            r["is_salary_client"] = bool(r["is_salary_client"])
            accounts = await get_accounts(row["user_id"])
            r["debit_balance"] = sum(
                a["balance"] for a in accounts
                if a.get("account_type") == "CurrentAccount" and a["balance"] > 0
            )
            results.append(r)
        return results


# ── Available offers/payments for hint management ─────────────────────────────

async def get_available_offers(user_id: str) -> list:
    user = await get_user(user_id)
    if not user:
        return []
    active = set(user.get("active_products") or [])
    age = user.get("age", 0)
    accounts = await get_accounts(user_id)
    debit_balance = sum(
        a["balance"] for a in accounts
        if a.get("account_type") == "CurrentAccount" and a["balance"] > 0
    )
    savings_balance = sum(
        a["balance"] for a in accounts
        if a.get("account_type") == "SavingsAccount"
    )
    max_balance = max(debit_balance, savings_balance)
    transfer_count = await _get_transfer_count_30d(user_id)
    top_cat = await _get_top_spending_category(user_id)

    offers = []

    if "autopay" not in active:
        payments = await get_scheduled_payments(user_id)
        has_autopay_candidate = any(
            p.get("payment_type") == "autopayment" and p.get("status") == "Active"
            for p in payments
        )
        if has_autopay_candidate:
            offers.append({
                "offer_id": "autopay_setup",
                "label": "Автоплатёж ЖКХ",
                "description": "Подключи автоплатёж — больше не забудешь",
                "available": True,
            })

    if "deposit" not in active:
        available = debit_balance > 80_000
        interest = round(debit_balance * 0.19 * 3 / 12) if available else 0
        offers.append({
            "offer_id": "deposit_3m",
            "label": f"Вклад на 3 мес (+{interest:,} ₽)".replace(",", " ") if available else "Вклад на 3 мес",
            "description": f"Баланс {debit_balance:,.0f} ₽ — нужен > 80 000 ₽".replace(",", " "),
            "available": available,
        })

    if "iis" not in active:
        available = 25 <= age <= 45 and max_balance > 50_000
        offers.append({
            "offer_id": "iis",
            "label": "ИИС",
            "description": f"Возраст {age}, баланс {max_balance:,.0f} ₽".replace(",", " "),
            "available": available,
        })

    if "cashback" not in active:
        cat_label = top_cat if top_cat else "покупках"
        offers.append({
            "offer_id": "cashback_multi",
            "label": f"Кешбэк: {cat_label}",
            "description": "Подключи кешбэк — получай деньги обратно",
            "available": True,
        })

    if "prime" not in active:
        available = transfer_count >= 5
        offers.append({
            "offer_id": "prime",
            "label": "Prime",
            "description": f"Переводов за 30 дней: {transfer_count} — нужен ≥ 5",
            "available": available,
        })

    if "round_up" not in active:
        offers.append({
            "offer_id": "round_up",
            "label": "Округление покупок",
            "description": "Копи незаметно: округляй покупки и откладывай сдачу",
            "available": True,
        })

    return offers


async def get_available_payments(user_id: str) -> list:
    payments = await get_scheduled_payments(user_id)
    result = []
    for p in payments:
        ptype = p.get("payment_type", "subscription")
        if ptype in ("credit_card", "loan", "autopayment"):
            result.append({
                "payment_id": p["id"],
                "name": p["name"],
                "amount": p["amount"],
                "payment_type": ptype,
                "day_of_month": p.get("day_of_month"),
            })
    return result
