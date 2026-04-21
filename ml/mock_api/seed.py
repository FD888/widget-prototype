"""
Standalone DB seeder.

Usage:
    python seed.py            # create vita.db and seed mock data
    python seed.py --reset    # wipe existing data and re-seed
"""
import asyncio
import os
import sys
from pathlib import Path

import aiosqlite

DB_PATH = os.getenv("DB_PATH", str(Path(__file__).parent / "vita.db"))
SCHEMA_PATH = Path(__file__).parent / "schema.sql"


async def main(reset: bool = False) -> None:
    if reset and Path(DB_PATH).exists():
        Path(DB_PATH).unlink()
        print(f"Deleted {DB_PATH}")

    conn = await aiosqlite.connect(DB_PATH)
    conn.row_factory = aiosqlite.Row
    await conn.execute("PRAGMA journal_mode=WAL")
    await conn.execute("PRAGMA foreign_keys=ON")
    await conn.commit()
    await conn.executescript(SCHEMA_PATH.read_text())

    from db import seed_db
    await seed_db(conn, reset=False)
    await conn.close()

    print(f"Seeded successfully: {DB_PATH}")
    print("Tables: users, accounts, contacts, transactions, scheduled_payments, pending_transactions")


if __name__ == "__main__":
    asyncio.run(main(reset="--reset" in sys.argv))
