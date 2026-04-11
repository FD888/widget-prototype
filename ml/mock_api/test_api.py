"""
Integration-тесты FastAPI эндпоинтов через httpx TestClient.
Запуск: pytest test_api.py -v

Переменные окружения выставляются здесь же — реального сервера не нужно.
"""
import os
import pytest
from fastapi.testclient import TestClient

# Принудительно выставляем env-переменные (перебиваем возможный setdefault из других модулей)
os.environ["APP_API_KEY"]    = "test_key"
os.environ["JWT_SECRET"]     = "test_secret_32chars_long_enough!"
os.environ["ALLOWED_PHONES"] = "+79000000000"
os.environ["ALLOWED_PINS"]   = "1111:vitya,2222:olga,3333:artyom"
os.environ["DEEPSEEK_API_KEY"] = ""  # отключаем реальный LLM

from main import app

client = TestClient(app)


# ─── /health ─────────────────────────────────────────────────────────────────

def test_health():
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"


# ─── /parse — L1 regex путь ───────────────────────────────────────────────────

def test_parse_balance():
    r = client.post("/parse", json={"text": "баланс"})
    assert r.status_code == 200
    data = r.json()
    assert data["intent"] == "balance"
    assert data["confidence"] >= 0.85

def test_parse_transfer():
    r = client.post("/parse", json={"text": "переведи маше 500"})
    assert r.status_code == 200
    data = r.json()
    assert data["intent"] == "transfer"
    assert data["amount"] == 500.0
    assert data["recipient"] == "маше"

def test_parse_topup():
    r = client.post("/parse", json={"text": "пополни телефон на 300"})
    assert r.status_code == 200
    data = r.json()
    assert data["intent"] == "topup"
    assert data["amount"] == 300.0

def test_parse_alarm():
    r = client.post("/parse", json={"text": "поставь будильник на 7:30"})
    assert r.status_code == 200
    data = r.json()
    assert data["intent"] == "alarm"
    assert data["hour"] == 7
    assert data["minute"] == 30

def test_parse_open_app():
    r = client.post("/parse", json={"text": "открой телеграм"})
    assert r.status_code == 200
    data = r.json()
    assert data["intent"] == "open_app"
    assert data["app"] == "telegram"

def test_parse_unknown_falls_to_llm_disabled():
    """Если LLM key пустой и L1 не матчит — должен вернуть intent=unknown."""
    r = client.post("/parse", json={"text": "хочу пиццу"})
    assert r.status_code == 200
    assert r.json()["intent"] == "unknown"

def test_parse_empty_string():
    r = client.post("/parse", json={"text": ""})
    assert r.status_code == 200
    assert r.json()["intent"] == "unknown"


# ─── /verify-phone ────────────────────────────────────────────────────────────

def test_verify_phone_allowed():
    r = client.post("/verify-phone", json={"phone": "+79000000000"})
    assert r.status_code == 200
    assert "app_token" in r.json()

def test_verify_phone_unknown():
    r = client.post("/verify-phone", json={"phone": "+79999999999"})
    assert r.status_code == 403


# ─── /auth ────────────────────────────────────────────────────────────────────

def _get_app_token() -> str:
    r = client.post("/verify-phone", json={"phone": "+79000000000"})
    return r.json()["app_token"]

def test_auth_correct_pin():
    token = _get_app_token()
    r = client.post("/auth", json={"pin": "1111"}, headers={"X-Api-Key": token})
    assert r.status_code == 200
    assert "banking_token" in r.json()

def test_auth_all_personas():
    """Каждый PIN возвращает banking_token для своей персоны."""
    token = _get_app_token()
    for pin in ("1111", "2222", "3333"):
        r = client.post("/auth", json={"pin": pin}, headers={"X-Api-Key": token})
        assert r.status_code == 200, f"PIN {pin} failed"
        assert "banking_token" in r.json()

def test_auth_wrong_pin():
    token = _get_app_token()
    r = client.post("/auth", json={"pin": "0000"}, headers={"X-Api-Key": token})
    assert r.status_code == 403

def test_auth_without_api_key():
    r = client.post("/auth", json={"pin": "1111"})
    assert r.status_code == 422  # missing X-Api-Key header


# ─── /balance ─────────────────────────────────────────────────────────────────

def _get_banking_token(pin: str = "1111") -> str:
    token = _get_app_token()
    r = client.post("/auth", json={"pin": pin}, headers={"X-Api-Key": token})
    return r.json()["banking_token"]

def test_balance_returns_accounts():
    bt = _get_banking_token()
    r = client.get("/balance", headers={"X-Banking-Token": bt})
    assert r.status_code == 200
    data = r.json()
    assert len(data["accounts"]) > 0
    acc = data["accounts"][0]
    assert "id" in acc and "balance" in acc

def test_balance_without_token():
    r = client.get("/balance")
    assert r.status_code == 422


# ─── /command + /confirm ──────────────────────────────────────────────────────

def test_command_transfer_creates_pending():
    bt = _get_banking_token()
    r = client.post(
        "/command",
        json={"intent": "transfer", "amount": 100.0, "recipient": "маша"},
        headers={"X-Banking-Token": bt},
    )
    assert r.status_code == 200
    data = r.json()
    assert data["intent"] == "transfer"
    assert data["amount"] == 100.0
    assert "transaction_id" in data

def test_command_topup_creates_pending():
    bt = _get_banking_token()
    r = client.post(
        "/command",
        json={"intent": "topup", "amount": 200.0, "phone": "+79000000000"},
        headers={"X-Banking-Token": bt},
    )
    assert r.status_code == 200
    data = r.json()
    assert data["intent"] == "topup"
    assert data["amount"] == 200.0

def test_confirm_executes_and_deducts():
    bt = _get_banking_token()
    # Создаём pending
    cmd = client.post(
        "/command",
        json={"intent": "transfer", "amount": 500.0, "recipient": "маша"},
        headers={"X-Banking-Token": bt},
    ).json()
    txn_id = cmd["transaction_id"]

    # Подтверждаем
    confirm = client.post(
        f"/confirm/{txn_id}",
        json={"source_account_id": "debit"},
        headers={"X-Banking-Token": bt},
    )
    assert confirm.status_code == 200
    result = confirm.json()
    assert result["status"] == "success"
    assert result["balance_after"] is not None

def test_confirm_double_submit_returns_404():
    bt = _get_banking_token()
    cmd = client.post(
        "/command",
        json={"intent": "transfer", "amount": 100.0, "recipient": "маша"},
        headers={"X-Banking-Token": bt},
    ).json()
    txn_id = cmd["transaction_id"]
    headers = {"X-Banking-Token": bt}
    body = {"source_account_id": "debit"}

    client.post(f"/confirm/{txn_id}", json=body, headers=headers)
    r2 = client.post(f"/confirm/{txn_id}", json=body, headers=headers)
    assert r2.status_code == 404
