-- =============================================================================
-- VTB Vita Mock API — SQLite Schema
-- Open Banking UK v3.1 + ISO 20022 inspired, 3-user demo
-- =============================================================================

PRAGMA foreign_keys = ON;

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id             TEXT PRIMARY KEY,
    full_name           TEXT NOT NULL,
    display_name        TEXT NOT NULL,
    birth_date          TEXT NOT NULL,
    age                 INTEGER NOT NULL,
    phone               TEXT NOT NULL UNIQUE,
    email               TEXT,
    city                TEXT,
    segment             TEXT NOT NULL,
    score               INTEGER NOT NULL DEFAULT 0,
    rfm_recency_days    INTEGER NOT NULL DEFAULT 0,
    rfm_frequency_30d   INTEGER NOT NULL DEFAULT 0,
    rfm_monetary_30d    REAL    NOT NULL DEFAULT 0,
    is_salary_client    INTEGER NOT NULL DEFAULT 0,
    salary_day          INTEGER,
    months_as_client    INTEGER NOT NULL DEFAULT 0,
    active_products     TEXT    NOT NULL DEFAULT '[]'
);

-- ---------------------------------------------------------------------------
-- accounts
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS accounts (
    account_id          TEXT NOT NULL,
    user_id             TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    account_type        TEXT NOT NULL,
    name                TEXT NOT NULL,
    pan_masked          TEXT,
    payment_system      TEXT NOT NULL DEFAULT 'mir',
    balance             REAL NOT NULL DEFAULT 0,
    currency            TEXT NOT NULL DEFAULT 'RUB',
    status              TEXT NOT NULL DEFAULT 'Enabled',
    open_date           TEXT NOT NULL,
    cashback_category   TEXT,
    cashback_rate       REAL,
    credit_limit        REAL,
    available_credit    REAL,
    min_payment         REAL,
    payment_due_date    TEXT,
    interest_rate       REAL,
    interest_accrued    REAL,
    PRIMARY KEY (account_id, user_id)
);

-- ---------------------------------------------------------------------------
-- contacts
-- Same person appears under multiple lookup_keys (case forms: "коля"/"коле")
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS contacts (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id             TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    lookup_key          TEXT NOT NULL,
    display_name        TEXT NOT NULL,
    full_name           TEXT NOT NULL,
    phone               TEXT NOT NULL,
    bank                TEXT NOT NULL,
    available_banks     TEXT NOT NULL DEFAULT '[]',
    pan_masked          TEXT,
    transfer_count      INTEGER NOT NULL DEFAULT 0,
    last_transfer_date  TEXT,
    UNIQUE (user_id, lookup_key)
);

CREATE INDEX IF NOT EXISTS idx_contacts_phone ON contacts(phone);

-- ---------------------------------------------------------------------------
-- transactions
-- merchant / recipient / sender stored as JSON (sparse, heterogeneous shapes)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transactions (
    id              TEXT PRIMARY KEY,
    user_id         TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    account_id      TEXT NOT NULL,
    booking_date    TEXT NOT NULL,
    value_date      TEXT NOT NULL,
    type            TEXT NOT NULL,
    amount          REAL NOT NULL,
    currency        TEXT NOT NULL DEFAULT 'RUB',
    direction       TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'Posted',
    category        TEXT,
    description     TEXT,
    merchant        TEXT,
    recipient       TEXT,
    sender          TEXT
);

CREATE INDEX IF NOT EXISTS idx_transactions_user ON transactions(user_id, booking_date DESC);

-- ---------------------------------------------------------------------------
-- scheduled_payments
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS scheduled_payments (
    id              TEXT PRIMARY KEY,
    user_id         TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    amount          REAL NOT NULL,
    day_of_month    INTEGER,
    weekday         TEXT,
    category        TEXT,
    mcc             TEXT,
    status          TEXT NOT NULL DEFAULT 'Active'
);

-- ---------------------------------------------------------------------------
-- pending_transactions — replaces in-memory _pending dict
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pending_transactions (
    txn_id                TEXT PRIMARY KEY,
    user_id               TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    intent                TEXT NOT NULL,
    amount                REAL NOT NULL,
    display_name          TEXT,
    phone                 TEXT,
    requires_manual_input INTEGER NOT NULL DEFAULT 0,
    created_at            TEXT NOT NULL,
    expires_at            TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pending_expires ON pending_transactions(expires_at);
