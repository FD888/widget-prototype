"""
VTB Vita — Mock API (C-03)
Принимает распарсенный intent от NLP, возвращает данные для модала подтверждения.

Flow (transfer/topup):
  Widget → NLP /parse → [Biometric/PIN on Android] → /command → modal → /confirm/{id} → result

Flow (balance):
  Widget → [Biometric/PIN on Android] → GET /balance → показать данные (без confirm)
"""

from fastapi import FastAPI, HTTPException, Header, Depends
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, Literal
from datetime import datetime, timezone, timedelta
from jose import jwt, JWTError
from dotenv import load_dotenv
import uuid
import os
import re

load_dotenv()

# ---------------------------------------------------------------------------
# Config from environment
# ---------------------------------------------------------------------------

APP_API_KEY     = os.getenv("APP_API_KEY", "vita_demo_2026")
JWT_SECRET      = os.getenv("JWT_SECRET", "dev_secret_change_in_production")
JWT_EXPIRE_MIN  = int(os.getenv("JWT_EXPIRE_MINUTES", "15"))

def _normalize_phone_cfg(phone: str) -> str:
    """Нормализует номер из .env для сравнения: +79186087207 → 79186087207"""
    return re.sub(r"\D", "", phone)

ALLOWED_PHONES = {_normalize_phone_cfg(p) for p in os.getenv("ALLOWED_PHONES", "").split(",") if p.strip()}
ALLOWED_PIN    = os.getenv("ALLOWED_PIN", "1234")  # fallback для локальной разработки

app = FastAPI(title="VTB Vita Mock API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ---------------------------------------------------------------------------
# Auth helpers
# ---------------------------------------------------------------------------

def _normalize_phone(phone: str) -> str:
    """Приводит номер к формату 7XXXXXXXXXX для сравнения."""
    digits = "".join(c for c in phone if c.isdigit())
    if len(digits) == 11 and digits[0] in ("7", "8"):
        return "7" + digits[1:]
    if len(digits) == 10:
        return "7" + digits
    return digits


def _check_app_token(x_api_key: str = Header(..., alias="X-Api-Key")):
    """
    Проверяет X-Api-Key заголовок.
    Принимает:
      1. Статический APP_API_KEY — для локальной разработки (adb tunnel).
      2. JWT app_token — выдаётся после верификации номера телефона (/verify-phone).
    """
    if x_api_key == APP_API_KEY:
        return  # локальная разработка

    try:
        payload = jwt.decode(x_api_key, JWT_SECRET, algorithms=["HS256"])
        if payload.get("type") != "app_access":
            raise HTTPException(status_code=403, detail="Invalid token type")
    except JWTError:
        raise HTTPException(status_code=403, detail="Invalid or expired token")


def _check_banking_token(x_banking_token: str = Header(..., alias="X-Banking-Token")):
    """Проверяет X-Banking-Token — выдаётся после валидации PIN через /auth."""
    try:
        payload = jwt.decode(x_banking_token, JWT_SECRET, algorithms=["HS256"])
        if payload.get("type") != "banking_access":
            raise HTTPException(status_code=403, detail="Invalid token type")
    except JWTError:
        raise HTTPException(status_code=403, detail="Invalid or expired banking token")

# ---------------------------------------------------------------------------
# Mock data
# ---------------------------------------------------------------------------

# Счета пользователя
MOCK_ACCOUNTS: list[dict] = [
    {
        "id": "debit",
        "name": "Дебетовая карта",
        "masked": "****5678",
        "balance": 47_230.50,
        "type": "debit",
        "currency": "RUB",
    },
    {
        "id": "credit",
        "name": "Кредитная карта",
        "masked": "****9012",
        "balance": 12_500.00,
        "limit": 50_000.00,
        "type": "credit",
        "currency": "RUB",
    },
    {
        "id": "savings",
        "name": "Накопительный счёт",
        "masked": "****3401",
        "balance": 120_000.00,
        "type": "savings",
        "currency": "RUB",
    },
]

# Контакты: ключ поиска (как пользователь говорит) → данные
# display_name — формат "Имя Ф." как в реальных банках
# phone — полный номер, хранится только на сервере, никогда не отдаётся клиенту
# banks — банки получателя (только они доступны для выбора в модале)
MOCK_CONTACTS = {
    "маша":   {"display_name": "Мария К.",   "phone": "+79261112233", "banks": ["ВТБ", "Сбер"]},
    "мария":  {"display_name": "Мария К.",   "phone": "+79261112233", "banks": ["ВТБ", "Сбер"]},
    "яна":    {"display_name": "Яна С.",     "phone": "+79031234567", "banks": ["ВТБ", "Т-Банк"]},
    "денис":  {"display_name": "Денис В.",   "phone": "+79162345678", "banks": ["ВТБ"]},
    "дидар":  {"display_name": "Дидар М.",   "phone": "+79273456789", "banks": ["Сбер", "Т-Банк", "Альфа"]},
    "элина":  {"display_name": "Элина Р.",   "phone": "+79114567890", "banks": ["ВТБ", "Альфа"]},
    "мама":   {"display_name": "Светлана В.","phone": "+79055678901", "banks": ["Сбер"]},
    "папа":   {"display_name": "Виктор В.",  "phone": "+79256789012", "banks": ["ВТБ", "Сбер"]},
    "саша":   {"display_name": "Александр П.","phone": "+79967890123", "banks": ["Т-Банк"]},
}

MOCK_OPERATORS = {
    "+7900": "МТС",  "+7901": "МТС",  "+7902": "МТС",  "+7903": "МТС",
    "+7904": "МТС",  "+7905": "МТС",  "+7906": "МТС",  "+7908": "МТС",
    "+7910": "МТС",  "+7911": "МТС",  "+7912": "МТС",  "+7913": "МТС",
    "+7914": "МТС",  "+7915": "МТС",  "+7916": "МТС",  "+7917": "МТС",
    "+7918": "МТС",  "+7919": "МТС",
    "+7920": "Tele2", "+7921": "Tele2", "+7922": "Tele2",
    "+7950": "Tele2", "+7951": "Tele2", "+7952": "Tele2", "+7953": "Tele2",
    "+7925": "МегаФон", "+7926": "МегаФон", "+7927": "МегаФон",
    "+7928": "МегаФон", "+7929": "МегаФон", "+7930": "МегаФон",
    "+7931": "МегаФон", "+7932": "МегаФон", "+7961": "МегаФон",
    "+7963": "МегаФон",
    "+7960": "Билайн", "+7962": "Билайн",
    "+7964": "Билайн", "+7965": "Билайн", "+7966": "Билайн",
}

# Pending operations (in-memory — ok for prototype demo)
_pending: dict[str, dict] = {}

# ---------------------------------------------------------------------------
# Phone index (нормализованный номер → ключ в MOCK_CONTACTS)
# ---------------------------------------------------------------------------

_PHONE_INDEX: dict[str, str] = {}

def _format_phone(phone: str) -> str:
    """Форматирует номер для отображения: +7 (926) 111-22-33"""
    digits = "".join(c for c in phone if c.isdigit())
    if len(digits) == 11:
        d = digits[1:]  # убираем 7/8
        return f"+7 ({d[0:3]}) {d[3:6]}-{d[6:8]}-{d[8:10]}"
    if len(digits) == 10:
        return f"+7 ({digits[0:3]}) {digits[3:6]}-{digits[6:8]}-{digits[8:10]}"
    return phone

def _build_phone_index() -> None:
    for key, contact in MOCK_CONTACTS.items():
        normalized = _normalize_phone(contact["phone"])
        if normalized:
            _PHONE_INDEX[normalized] = key

_build_phone_index()

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _now() -> str:
    return datetime.now(timezone.utc).isoformat()

def _new_txn() -> str:
    return f"TXN-{uuid.uuid4().hex[:8].upper()}"

def _account_by_id(account_id: str) -> dict:
    for acc in MOCK_ACCOUNTS:
        if acc["id"] == account_id:
            return acc
    return MOCK_ACCOUNTS[0]

def _detect_operator(phone: str) -> str:
    digits = "".join(c for c in phone if c.isdigit())
    if len(digits) >= 10:
        prefix = "+7" + digits[-10:-7]
        return MOCK_OPERATORS.get(prefix, "Оператор")
    return "Оператор"

def _accounts_for_response() -> list[dict]:
    return [
        {
            "id": acc["id"],
            "name": acc["name"],
            "masked": acc["masked"],
            "balance": acc["balance"],
            "type": acc["type"],
        }
        for acc in MOCK_ACCOUNTS
    ]


# ---------------------------------------------------------------------------
# Request / Response models
# ---------------------------------------------------------------------------

class CommandRequest(BaseModel):
    intent: Literal["transfer", "topup"]
    amount: Optional[float] = None
    recipient: Optional[str] = None   # transfer: имя или номер; topup: номер телефона
    phone: Optional[str] = None       # topup: явный номер телефона


class AccountInfo(BaseModel):
    id: str
    name: str
    masked: str
    balance: float
    type: str


class BalanceResponse(BaseModel):
    accounts: list[AccountInfo]
    timestamp: str


class ConfirmationResponse(BaseModel):
    transaction_id: str
    intent: str
    title: str
    subtitle: Optional[str] = None
    amount: Optional[float] = None
    currency: str = "RUB"

    # Источник списания — все счета + предвыбранный
    source_accounts: list[AccountInfo]
    default_account_id: str

    # Transfer
    contact_found: bool = False
    requires_manual_input: bool = False   # True → Android показывает поле ввода в модале
    recipient_display_name: Optional[str] = None  # "Мария К."
    recipient_phone: Optional[str] = None         # полный номер для отображения
    recipient_banks: list[str] = []

    # Topup
    topup_phone: Optional[str] = None    # полный номер для отображения
    operator: Optional[str] = None

    fee: float = 0.0
    timestamp: str


class LookupRequest(BaseModel):
    phone: str


class LookupResponse(BaseModel):
    found: bool
    display_name: Optional[str] = None   # "Мария К." если найден
    recipient_banks: list[str] = []


class ConfirmRequest(BaseModel):
    source_account_id: str = "debit"
    manual_phone: Optional[str] = None   # если requires_manual_input был True
    selected_bank: Optional[str] = None  # выбранный банк получателя


class OperationResult(BaseModel):
    transaction_id: str
    status: Literal["success", "failed"]
    title: str
    message: str
    source_account_name: Optional[str] = None
    balance_after: Optional[float] = None
    timestamp: str


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

PARSE_SYSTEM_PROMPT = """
Ты — парсер команд для банковского виджета ВТБ Vita.
Извлеки намерение пользователя и параметры. Верни ТОЛЬКО валидный JSON без markdown и пояснений.

Возможные intents:
- transfer   : перевести деньги (recipient, amount)
- balance    : проверить баланс
- topup      : пополнить телефон (phone, amount)
- open_app   : открыть приложение (app)
- alarm      : поставить будильник (hour 0-23, minute 0-59)
- timer      : запустить таймер (duration_seconds)
- call       : позвонить (contact)
- unknown    : непонятная команда

Известные значения app: telegram, whatsapp, vk, youtube, spotify,
  yandex_maps, yandex_music, instagram, tiktok, vtb, sber, tinkoff

Формат ответа (все поля обязательны, неизвестные = null):
{
  "intent": "...",
  "recipient": null,
  "amount": null,
  "phone": null,
  "app": null,
  "hour": null,
  "minute": null,
  "duration_seconds": null,
  "contact": null,
  "confidence": 0.9
}
""".strip()


class ParseRequest(BaseModel):
    text: str


class ParseResult(BaseModel):
    intent: str
    recipient: Optional[str] = None
    amount: Optional[float] = None
    phone: Optional[str] = None
    app: Optional[str] = None
    hour: Optional[int] = None
    minute: Optional[int] = None
    duration_seconds: Optional[int] = None
    contact: Optional[str] = None
    confidence: float = 0.9


@app.post("/parse", response_model=ParseResult)
async def parse_command(req: ParseRequest):
    """
    Принимает свободный текст → DeepSeek парсит → возвращает структурированный intent.
    Не требует JWT (только APP_API_KEY) — парсинг не касается банковских данных.
    """
    import httpx, json as _json

    api_key = os.getenv("DEEPSEEK_API_KEY", "")
    if not api_key:
        raise HTTPException(status_code=503, detail="DEEPSEEK_API_KEY не задан")

    payload = {
        "model": "deepseek/deepseek-chat",
        "messages": [
            {"role": "system", "content": PARSE_SYSTEM_PROMPT},
            {"role": "user",   "content": req.text},
        ],
        "temperature": 0.1,
        "max_tokens": 256,
        "response_format": {"type": "json_object"},
    }

    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.post(
            "https://openrouter.ai/api/v1/chat/completions",
            headers={
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
            },
            json=payload,
        )

    if resp.status_code != 200:
        raise HTTPException(status_code=502, detail=f"OpenRouter error: {resp.text}")

    try:
        content = resp.json()["choices"][0]["message"]["content"]
        data = _json.loads(content)
        return ParseResult(**{k: data.get(k) for k in ParseResult.model_fields})
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"Parse error: {e}")


@app.get("/health")
def health():
    return {"status": "ok", "service": "vtb-vita-mock-api"}


class AuthRequest(BaseModel):
    pin: str

class AuthResponse(BaseModel):
    banking_token: str
    expires_in_seconds: int

@app.post("/auth", response_model=AuthResponse)
def auth(req: AuthRequest, _: None = Depends(_check_app_token)):
    """
    Валидирует PIN → возвращает banking JWT (15 мин, только в памяти на клиенте).
    Требует X-Api-Key (app_token) — чтобы только верифицированные устройства могли войти.
    """
    if not ALLOWED_PIN or req.pin != ALLOWED_PIN:
        raise HTTPException(status_code=403, detail="Неверный PIN")
    expire = datetime.now(timezone.utc) + timedelta(minutes=JWT_EXPIRE_MIN)
    token = jwt.encode(
        {"type": "banking_access", "exp": expire},
        JWT_SECRET, algorithm="HS256"
    )
    return AuthResponse(banking_token=token, expires_in_seconds=JWT_EXPIRE_MIN * 60)


class PhoneVerifyRequest(BaseModel):
    phone: str

class PhoneVerifyResponse(BaseModel):
    app_token: str
    expires_in_days: int = 30

@app.post("/verify-phone", response_model=PhoneVerifyResponse)
def verify_phone(req: PhoneVerifyRequest):
    """
    Верификация номера телефона — первый запуск приложения.
    Не требует X-Api-Key (публичный эндпоинт).
    Если номер в ALLOWED_PHONES → возвращает app_token (JWT, 30 дней).
    """
    normalized = _normalize_phone(req.phone)
    if normalized not in ALLOWED_PHONES:
        raise HTTPException(status_code=403, detail="Номер не найден")

    expire = datetime.now(timezone.utc) + timedelta(days=30)
    token = jwt.encode(
        {"type": "app_access", "phone": normalized, "exp": expire},
        JWT_SECRET,
        algorithm="HS256",
    )
    return PhoneVerifyResponse(app_token=token)


@app.get("/balance", response_model=BalanceResponse)
def balance(_: None = Depends(_check_banking_token)):
    """
    Возвращает балансы всех счетов пользователя.
    Биометрия/PIN выполняется на стороне Android ДО вызова этого endpoint.
    Никакого confirm не нужно — это чтение, не действие.
    """
    return BalanceResponse(
        accounts=_accounts_for_response(),
        timestamp=_now(),
    )


@app.post("/lookup", response_model=LookupResponse)
def lookup(req: LookupRequest, _: None = Depends(_check_app_token)):
    """
    Android вызывает при ручном вводе номера в модале перевода.
    Возвращает display_name ("Мария К.") если номер найден в контактах.
    Полный номер на сервере, клиенту не отдаётся.
    """
    normalized = _normalize_phone(req.phone)
    key = _PHONE_INDEX.get(normalized)
    if key:
        contact = MOCK_CONTACTS[key]
        return LookupResponse(
            found=True,
            display_name=contact["display_name"],
            recipient_banks=contact["banks"],
        )
    return LookupResponse(found=False)


@app.post("/command", response_model=ConfirmationResponse)
def command(req: CommandRequest, _: None = Depends(_check_banking_token)):
    """
    Принимает распарсенный intent transfer или topup.
    Биометрия/PIN выполняется на Android ДО вызова.
    Возвращает данные для модала подтверждения.
    """
    txn_id = _new_txn()
    ts = _now()
    accounts = _accounts_for_response()
    default_acc = MOCK_ACCOUNTS[0]

    # --- transfer ---
    if req.intent == "transfer":
        if not req.amount or req.amount <= 0:
            raise HTTPException(status_code=422, detail="Укажите сумму перевода")

        contact_found = False
        requires_manual = False
        display_name = None
        recipient_phone = None
        recipient_banks: list[str] = []

        if req.recipient:
            key = req.recipient.lower().strip()
            contact = MOCK_CONTACTS.get(key)
            if contact:
                contact_found = True
                display_name = contact["display_name"]
                recipient_phone = _format_phone(contact["phone"])
                recipient_banks = contact["banks"]
            else:
                digits = "".join(c for c in req.recipient if c.isdigit())
                if len(digits) >= 10:
                    # Передан номер напрямую — ищем в индексе
                    normalized = _normalize_phone(req.recipient)
                    index_key = _PHONE_INDEX.get(normalized)
                    if index_key:
                        contact_found = True
                        c = MOCK_CONTACTS[index_key]
                        display_name = c["display_name"]
                        recipient_phone = _format_phone(c["phone"])
                        recipient_banks = c["banks"]
                    else:
                        # Незнакомый номер — отображаем как есть, банки неизвестны
                        recipient_phone = _format_phone(req.recipient)
                else:
                    # Имя не найдено → ручной ввод
                    requires_manual = True
                    display_name = req.recipient  # подсказка в поле

        _pending[txn_id] = {
            "intent": "transfer",
            "amount": req.amount,
            "display_name": display_name,
            "requires_manual_input": requires_manual,
        }

        return ConfirmationResponse(
            transaction_id=txn_id,
            intent="transfer",
            title="Перевод",
            subtitle="Укажите получателя" if requires_manual else display_name,
            amount=req.amount,
            source_accounts=accounts,
            default_account_id=default_acc["id"],
            contact_found=contact_found,
            requires_manual_input=requires_manual,
            recipient_display_name=display_name,
            recipient_phone=recipient_phone,
            recipient_banks=recipient_banks,
            fee=0.0,
            timestamp=ts,
        )

    # --- topup ---
    elif req.intent == "topup":
        if not req.amount or req.amount <= 0:
            raise HTTPException(status_code=422, detail="Укажите сумму пополнения")

        phone = req.phone or req.recipient
        requires_manual = phone is None

        formatted_phone = _format_phone(phone) if phone else None
        operator = _detect_operator(phone) if phone else None

        _pending[txn_id] = {
            "intent": "topup",
            "amount": req.amount,
            "phone": phone,
            "requires_manual_input": requires_manual,
        }

        return ConfirmationResponse(
            transaction_id=txn_id,
            intent="topup",
            title="Пополнение телефона",
            subtitle="Укажите номер телефона" if requires_manual else formatted_phone,
            amount=req.amount,
            source_accounts=accounts,
            default_account_id=default_acc["id"],
            requires_manual_input=requires_manual,
            topup_phone=formatted_phone,
            operator=operator,
            fee=0.0,
            timestamp=ts,
        )

    raise HTTPException(status_code=400, detail=f"Неизвестный intent: {req.intent}")


@app.post("/confirm/{transaction_id}", response_model=OperationResult)
def confirm(transaction_id: str, req: ConfirmRequest, _: None = Depends(_check_banking_token)):
    """
    Пользователь нажал «Подтвердить» в модале.
    Выполняет операцию, списывает с выбранного счёта.
    """
    pending = _pending.pop(transaction_id, None)
    if pending is None:
        raise HTTPException(status_code=404, detail="Операция не найдена или уже выполнена")

    ts = _now()
    intent = pending["intent"]
    source_account = _account_by_id(req.source_account_id)
    amount = pending.get("amount", 0)

    if amount > source_account["balance"]:
        raise HTTPException(status_code=422, detail="Недостаточно средств на выбранном счёте")

    balance_after = round(source_account["balance"] - amount, 2)
    source_account["balance"] = balance_after

    bank_label = f" через {req.selected_bank}" if req.selected_bank else ""

    if intent == "transfer":
        # При ручном вводе имя приходит из lookup (Android передаёт его обратно)
        recipient = pending.get("display_name") or req.manual_phone or "Получатель"
        return OperationResult(
            transaction_id=transaction_id,
            status="success",
            title="Перевод выполнен",
            message=f"{recipient} получит {amount:,.0f} ₽{bank_label}",
            source_account_name=source_account["name"],
            balance_after=balance_after,
            timestamp=ts,
        )

    elif intent == "topup":
        phone = pending.get("phone") or req.manual_phone or ""
        return OperationResult(
            transaction_id=transaction_id,
            status="success",
            title="Телефон пополнен",
            message=f"{amount:,.0f} ₽ → {_format_phone(phone)}",
            source_account_name=source_account["name"],
            balance_after=balance_after,
            timestamp=ts,
        )

    raise HTTPException(status_code=500, detail="Внутренняя ошибка")
