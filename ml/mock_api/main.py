"""
VTB Vita — Mock API (C-03)
Принимает распарсенный intent от NLP, возвращает данные для модала подтверждения.

Flow (transfer/topup):
  Widget → NLP /parse → [Biometric/PIN on Android] → /command → modal → /confirm/{id} → result

Flow (balance):
  Widget → [Biometric/PIN on Android] → GET /balance → показать данные (без confirm)
"""

from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException, Header, Depends, WebSocket, WebSocketDisconnect, Request
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, Literal
from datetime import datetime, timezone, timedelta
from collections import defaultdict, deque
from jose import jwt, JWTError
from dotenv import load_dotenv
import uuid
import os
import re
import asyncio
import json
import grpc
import grpc.aio
from yandex_speech import stt_pb2, stt_pb2_grpc
import db

load_dotenv()

# ---------------------------------------------------------------------------
# Logging — stdlib, низкий overhead; INFO не логирует VAD-уровень debug-шум
# ---------------------------------------------------------------------------

import logging
import time

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-8s %(name)s | %(message)s",
    datefmt="%H:%M:%S",
)
_log = logging.getLogger("vita.api")

# ---------------------------------------------------------------------------
# Config from environment
# ---------------------------------------------------------------------------

APP_API_KEY     = os.getenv("APP_API_KEY")
JWT_SECRET      = os.getenv("JWT_SECRET")

if not APP_API_KEY:
    raise RuntimeError("APP_API_KEY environment variable is required")
if not JWT_SECRET:
    raise RuntimeError("JWT_SECRET environment variable is required")
JWT_EXPIRE_MIN  = int(os.getenv("JWT_EXPIRE_MINUTES", "15"))

def _normalize_phone_cfg(phone: str) -> str:
    """Нормализует номер из .env для сравнения: +79186087207 → 79186087207"""
    return re.sub(r"\D", "", phone)

ALLOWED_PHONES = {_normalize_phone_cfg(p) for p in os.getenv("ALLOWED_PHONES", "").split(",") if p.strip()}

# PIN → user_id  (env: "1111:vitya,2222:olga,3333:artyom")
ALLOWED_PINS: dict[str, str] = {}
for _entry in os.getenv("ALLOWED_PINS", "1111:vitya,2222:olga,3333:artyom").split(","):
    if ":" in _entry:
        _pin, _uid = _entry.strip().split(":", 1)
        ALLOWED_PINS[_pin.strip()] = _uid.strip()

@asynccontextmanager
async def lifespan(app: FastAPI):
    await db.init_db()
    PHONE_INDEX.update(await db.build_phone_index())
    _log.info("DB ready, phone_index=%d entries", len(PHONE_INDEX))
    yield
    await db.close_db()

app = FastAPI(title="VTB Vita Mock API", version="1.0.0", lifespan=lifespan)

_ALLOWED_ORIGINS = [o.strip() for o in os.getenv("CORS_ORIGINS", "").split(",") if o.strip()]
if not _ALLOWED_ORIGINS:
    _ALLOWED_ORIGINS = ["https://vtb.vibefounder.ru"]

app.add_middleware(
    CORSMiddleware,
    allow_origins=_ALLOWED_ORIGINS,
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type", "X-Api-Key", "X-Banking-Token"],
)

# ---------------------------------------------------------------------------
# Rate limiting — простой sliding-window счётчик в памяти
# Генерирует 429 при превышении, не требует внешних зависимостей
# ---------------------------------------------------------------------------

_rate_buckets: dict[str, deque] = defaultdict(deque)

def _check_rate_limit(key: str, max_requests: int, window_seconds: int = 60) -> None:
    """
    Sliding-window rate limit.
    key — уникальный идентификатор (например "verify-phone:1.2.3.4").
    Бросает HTTP 429 при превышении лимита.
    """
    now = time.monotonic()
    dq = _rate_buckets[key]
    cutoff = now - window_seconds
    while dq and dq[0] < cutoff:
        dq.popleft()
    if len(dq) >= max_requests:
        _log.warning("rate limit exceeded: key=%s requests=%d/%d", key, len(dq), max_requests)
        raise HTTPException(status_code=429, detail="Слишком много запросов, попробуйте позже")
    dq.append(now)


def _client_ip(request: Request) -> str:
    forwarded = request.headers.get("X-Forwarded-For")
    return forwarded.split(",")[0].strip() if forwarded else (request.client.host if request.client else "unknown")


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


async def _get_user_from_banking_token(x_banking_token: str = Header(..., alias="X-Banking-Token")) -> str:
    """Проверяет X-Banking-Token и возвращает user_id из payload."""
    try:
        payload = jwt.decode(x_banking_token, JWT_SECRET, algorithms=["HS256"])
        if payload.get("type") != "banking_access":
            raise HTTPException(status_code=403, detail="Invalid token type")
        user_id = payload.get("user_id")
        if not user_id or not await db.get_user(user_id):
            raise HTTPException(status_code=403, detail="Invalid or expired banking token")
        return user_id
    except JWTError:
        raise HTTPException(status_code=403, detail="Invalid or expired banking token")

# ---------------------------------------------------------------------------
# Data — PHONE_INDEX строится из БД при старте, статика остаётся в data.py
# ---------------------------------------------------------------------------

PHONE_INDEX: dict = {}

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

def _format_phone(phone: str) -> str:
    """Форматирует номер для отображения: +7 (926) 111-22-33"""
    digits = "".join(c for c in phone if c.isdigit())
    if len(digits) == 11:
        d = digits[1:]  # убираем 7/8
        return f"+7 ({d[0:3]}) {d[3:6]}-{d[6:8]}-{d[8:10]}"
    if len(digits) == 10:
        return f"+7 ({digits[0:3]}) {digits[3:6]}-{digits[6:8]}-{digits[8:10]}"
    return phone

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _now() -> str:
    return datetime.now(timezone.utc).isoformat()

def _new_txn() -> str:
    return f"TXN-{uuid.uuid4().hex[:8].upper()}"

def _account_by_id(accounts: list[dict], account_id: str) -> dict:
    for acc in accounts:
        if acc["account_id"] == account_id:
            return acc
    return accounts[0]

def _detect_operator(phone: str) -> str:
    digits = "".join(c for c in phone if c.isdigit())
    if len(digits) >= 10:
        prefix = "+7" + digits[-10:-7]
        return MOCK_OPERATORS.get(prefix, "Оператор")
    return "Оператор"

def _accounts_for_response(accounts: list[dict]) -> list[dict]:
    result = []
    for acc in accounts:
        pan = acc.get("pan_masked") or ""
        digits_only = pan.replace(" ", "").replace("*", "")
        masked = "****" + digits_only[-4:] if len(digits_only) >= 4 else "****"

        acc_type = acc.get("account_type", "CurrentAccount")
        if "Credit" in acc_type:
            type_str = "credit"
        elif "Savings" in acc_type:
            type_str = "savings"
        else:
            type_str = "debit"

        result.append({
            "id": acc["account_id"],
            "name": acc["name"],
            "masked": masked,
            "balance": acc["balance"],
            "type": type_str,
            "payment_system": acc.get("payment_system", "mir"),
            "currency": acc.get("currency", "RUB"),
        })
    return result


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
    payment_system: str = "mir"
    currency: str = "RUB"


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

# ---------------------------------------------------------------------------
# NLP helpers — многоуровневый парсер (L1 regex → L2 LLM)
# ---------------------------------------------------------------------------

# --- Числа словами (для _parse_amount) ---

# Базовые числа: используются как самостоятельно, так и перед множителем
_WORD_TO_NUM: dict[str, float] = {
    "ноль": 0, "нуль": 0,
    "один": 1, "одна": 1, "одну": 1,
    "два": 2, "две": 2,
    "три": 3, "трёх": 3, "трех": 3,
    "четыре": 4, "четырёх": 4, "четырех": 4,
    "пять": 5, "шесть": 6, "семь": 7, "восемь": 8, "девять": 9, "десять": 10,
    "одиннадцать": 11, "двенадцать": 12, "тринадцать": 13, "четырнадцать": 14,
    "пятнадцать": 15, "шестнадцать": 16, "семнадцать": 17, "восемнадцать": 18, "девятнадцать": 19,
    "двадцать": 20, "тридцать": 30, "сорок": 40, "пятьдесят": 50,
    "шестьдесят": 60, "семьдесят": 70, "восемьдесят": 80, "девяносто": 90,
    "сто": 100, "двести": 200, "триста": 300, "четыреста": 400,
    "пятьсот": 500, "шестьсот": 600, "семьсот": 700, "восемьсот": 800, "девятьсот": 900,
    # «тысячу/тысяча» как самостоятельное значение
    "тысяча": 1_000, "тысячу": 1_000, "тысячи": 1_000, "тысяч": 1_000,
    "тыс": 1_000, "тыщ": 1_000, "тыщу": 1_000,
    # дроби для «полтора/полторы тысячи»
    "полтора": 1.5, "полторы": 1.5,
    # миллионы
    "миллион": 1_000_000, "миллиона": 1_000_000, "миллионов": 1_000_000, "млн": 1_000_000,
}

# Множители (идут ПОСЛЕ базового числа: «три тысячи», «два миллиона»)
_MULTIPLIERS: dict[str, float] = {
    "тысяча": 1_000, "тысячу": 1_000, "тысячи": 1_000, "тысяч": 1_000,
    "тыс": 1_000, "тыщ": 1_000,
    "миллион": 1_000_000, "миллиона": 1_000_000, "миллионов": 1_000_000, "млн": 1_000_000,
}


def _normalize_text(text: str) -> str:
    """
    Предобработка: нижний регистр, ё→е, пунктуация → пробел, нормализация пробелов.
    ВАЖНО: ':' и '.' между цифрами НЕ убираем (нужны для времени 7:30 и сумм 1.5).
    """
    t = text.lower().strip()
    t = t.replace("ё", "е")
    # Убираем безопасную пунктуацию
    t = re.sub(r"[!?«»;\"\']", " ", t)
    # Запятую и символы вроде '!' убираем только если они НЕ между двумя цифрами
    t = re.sub(r"(?<!\d)[,](?!\d)", " ", t)
    t = re.sub(r"\s+", " ", t).strip()
    return t


def _parse_amount(text: str) -> Optional[float]:
    """
    Расширенный парсинг суммы из строки.
    Поддерживает: '500', '1 000', '1,5', '2 тысячи', 'пятьсот', 'три тысячи', 'полторы тысячи'.
    Возвращает None если сумма не найдена.
    """
    t = text.lower().strip()

    # --- Шаг 1: Цифры (основной путь) ---
    m = re.search(r"(\d[\d\s]*(?:[.,]\d+)?)", t)
    if m:
        num_str = m.group(1).replace(" ", "").replace(",", ".")
        try:
            base = float(num_str)
        except ValueError:
            return None
        # Ищем множитель сразу после цифры
        rest = t[m.end():]
        for mw, mv in _MULTIPLIERS.items():
            if re.search(r"\b" + re.escape(mw) + r"\b", rest):
                return base * mv
        return base

    # --- Шаг 2: Словесная запись, двухпроходная ---
    # Проход 1: ищем базовое число (НЕ являющееся множителем)
    # Сортировка по длине desc: «восемьсот» (8) найдём раньше «восемь» (6), «пятьсот» (7) раньше «пять» (4)
    for word in sorted(_WORD_TO_NUM, key=len, reverse=True):
        if word in _MULTIPLIERS:
            continue  # пропускаем чистые множители (тысяча, тыс…) — они для Прохода 2
        m = re.search(r"\b" + re.escape(word) + r"\b", t)
        if m:
            base_val = float(_WORD_TO_NUM[word])
            # Ищем множитель ПОСЛЕ найденного базового слова
            rest = t[m.end():]
            for mw, mv in sorted(_MULTIPLIERS.items(), key=lambda x: len(x[0]), reverse=True):
                if re.search(r"\b" + re.escape(mw) + r"\b", rest):
                    return base_val * mv
            return base_val

    # Проход 2: одиночный множитель («тысячу», «тысяча», «тыс»)
    for word in sorted(_MULTIPLIERS, key=len, reverse=True):
        if re.search(r"\b" + re.escape(word) + r"\b", t):
            return float(_MULTIPLIERS[word])

    return None


def _make_result(intent: str, **kwargs) -> dict:
    base: dict = {
        "intent": intent, "recipient": None, "amount": None, "phone": None,
        "app": None, "hour": None, "minute": None, "duration_seconds": None,
        "contact": None, "destination": None, "confidence": 0.9,
    }
    base.update(kwargs)
    return base


_EXACT_CACHE: dict[str, dict] = {
    "баланс":                _make_result("balance", confidence=0.98),
    "покажи баланс":         _make_result("balance", confidence=0.98),
    "мой баланс":            _make_result("balance", confidence=0.98),
    "какой баланс":          _make_result("balance", confidence=0.98),
    "сколько денег":         _make_result("balance", confidence=0.98),
    "сколько на карте":      _make_result("balance", confidence=0.98),
    "сколько на счете":      _make_result("balance", confidence=0.98),
    "покажи счет":           _make_result("balance", confidence=0.98),
    "покажи мои счета":      _make_result("balance", confidence=0.98),
}

_APP_SLUG_MAP: dict[str, str] = {
    "telegram": "telegram",     "телеграм": "telegram",   "телега": "telegram",   "телегу": "telegram",
    "whatsapp": "whatsapp",     "ватсап": "whatsapp",     "вацап": "whatsapp",
    "вконтакте": "vk",          "вк": "vk",
    "youtube": "youtube",       "ютуб": "youtube",        "ютубе": "youtube",
    "spotify": "spotify",       "спотифай": "spotify",
    "яндекс карты": "yandex_maps",   "яндекскарты": "yandex_maps",  "яндекс.карты": "yandex_maps",
    "яндекс музыка": "yandex_music", "яндекс музыку": "yandex_music",
    "инстаграм": "instagram",   "instagram": "instagram",
    "тикток": "tiktok",         "tiktok": "tiktok",
    "втб": "vtb",
    "сбер": "sber",             "сбербанк": "sber",
    "тинькофф": "tinkoff",      "тинк": "tinkoff",        "тинькофф банк": "tinkoff",
}

_RF = re.IGNORECASE

_RE_BALANCE = re.compile(
    r"^(?:(?:покажи|узнай?|посмотр[её]|провер[её]|мой|моя|какой|какая|узнать|посмотреть|проверить)\s+)?"
    r"баланс\s*$|"
    r"^сколько\s+(?:у\s+меня\s+)?(?:денег|на\s+(?:карте|счет[ее]?|счету))\s*$|"
    r"^покажи\s+(?:мои?\s+)?счет[аа]?\s*$",
    _RF,
)

# Расширенный список глаголов для transfer
_TRANSFER_VERBS = (
    r"(?:переведи|перевести|перевод|"
    r"отправь|отправить|"
    r"скинь|скинуть|"
    r"кинь|кинуть|"             # очень частое в быту
    r"закинь|закинуть|"
    r"перекинь|перекинуть|"
    r"пришли|прислать|"
    r"заплати|заплатить|"       # «заплати маме 500»
    r"шли)"
)

# Стоп-слова, которые могут стоять между глаголом и именем получателя
_SKIP_WORDS = r"(?:(?:пожалуйста|срочно|быстро|сейчас|вот|ну|же)\s+)*"

# Группа захвата суммы: цифры + опциональный множитель ИЛИ словесное число
# ВАЖНО: более длинные варианты стоят первыми в альтернации,
# иначе «пять» совпадёт раньше «пятьсот», «двадцать» раньше «двести» и т.д.
_WORD_NUMS_LIST = sorted([
    "один", "одну", "два", "две", "три", "четыре", "пять", "шесть", "семь",
    "восемь", "девять", "десять", "пятнадцать", "двадцать", "тридцать",
    "сорок", "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят",
    "девяносто", "сто", "двести", "триста", "четыреста", "пятьсот",
    "шестьсот", "семьсот", "восемьсот", "девятьсот", "полтора", "полторы",
    "тысяча", "тысячу", "тысячи",
], key=len, reverse=True)
_WORD_NUMS = "|".join(_WORD_NUMS_LIST)
_MULT_WORDS = r"тысяч[аиу]?|тыщ[аи]?|тыс|млн|миллион(?:а|ов)?"
_AMT_GROUP = (
    r"(\d[\d\s]*(?:[.,]\d+)?(?:\s*(?:" + _MULT_WORDS + r"))?|"
    r"(?:" + _WORD_NUMS + r")(?:\s+(?:" + _MULT_WORDS + r"))?)"
)

# verb + (стоп-слова?) + name + amount
_RE_TRANSFER_VNA = re.compile(
    r"(?:" + _TRANSFER_VERBS + r")\s+" + _SKIP_WORDS +
    r"([а-яе][а-яе]+)\s+" + _AMT_GROUP,
    _RF,
)
# verb + amount + (рублей?) + name
_RE_TRANSFER_VAN = re.compile(
    r"(?:" + _TRANSFER_VERBS + r")\s+" + _AMT_GROUP +
    r"(?:\s+(?:рублей?|руб\.?|р\.?|₽))?\s+([а-яе][а-яе]+)",
    _RF,
)

_TOPUP_VERBS = r"(?:пополни(?:ть)?|положи(?:ть)?\s+(?:деньги\s+)?на\s+(?:телефон|номер))"
_RE_TOPUP = re.compile(
    r"(?:" + _TOPUP_VERBS + r")"
    r"(?:\s+(?:мне|себе))?"
    r"(?:\s+(?:телефон|номер))?"
    r"(?:\s+([\+\d][\d\s\-\(\)]{7,15}))?"
    r"(?:\s+(?:на\s+)?)?"
    r"(\d[\d\s]*(?:[.,]\d+)?(?:\s*(?:" + _MULT_WORDS + r"))?)",
    _RF,
)

# Будильник: числовое время
_RE_ALARM_NUM = re.compile(
    r"(?:поставь?|постав|разбуди?|включи?|поставить|разбудить)\s+(?:будильник\s+)?(?:на\s+|в\s+)?"
    r"(\d{1,2})[:.](\d{2})|"
    r"(?:поставь?|постав|разбуди?|включи?|поставить|разбудить)\s+(?:будильник\s+)?(?:на\s+|в\s+)?"
    r"(\d{1,2})\s*(?:час(?:а|ов)?|ч\.?)(?:\s+(\d{1,2})\s*(?:минут?|мин\.?))?",
    _RF,
)
# Будильник: словесное время (полдень, полночь).
# ВАЖНО: глагол или слово «будильник» обязательны — без них regex слишком жадный
# и ловит числа в других командах (таймер, перевод и т.д.).
_ALARM_FIXED = {
    "полночь": (0, 0), "полуночи": (0, 0),
    "полдень": (12, 0), "полудень": (12, 0),
}
_ALARM_VERBS = r"(?:поставь?|постав|разбуди?|включи?|поставить|разбудить)"
_RE_ALARM_WORD = re.compile(
    # Требует либо глагол, либо слово «будильник» — защита от ложных совпадений
    r"(?:" + _ALARM_VERBS + r"\s+(?:будильник\s+)?|будильник\s+(?:" + _ALARM_VERBS + r"\s+)?)"
    r"(?:на\s+|в\s+)?(полночь|полуночи|полдень|полудень)",
    _RF,
)

# Таймер: «поставь таймер на N» или «через N»
_RE_TIMER = re.compile(
    r"(?:поставь?|постав|включи?|засеки?|запусти?)\s+(?:таймер\s+)?(?:на\s+)?"
    r"(\d+)\s*(минут[ауы]?|сек(?:унд[ауы]?)?|час(?:а|ов)?)|"
    r"^таймер\s+(?:на\s+)?(\d+)\s*(минут[ауы]?|сек(?:унд[ауы]?)?|час(?:а|ов)?)\s*$|"
    r"через\s+(\d+)\s*(минут[ауы]?|сек(?:унд[ауы]?)?|час(?:а|ов)?)",  # «через N минут»
    _RF,
)

_KNOWN_APP_RE = "|".join(re.escape(k) for k in sorted(_APP_SLUG_MAP, key=len, reverse=True))
_APP_VERBS = r"(?:открой?|открыть|запусти?|запустить|включи?|включить|зайди?\s+в|перейди?\s+в)"
_RE_OPEN_APP = re.compile(r"(?:" + _APP_VERBS + r")\s+(" + _KNOWN_APP_RE + r")\b", _RF)

_RE_CALL = re.compile(
    r"(?:позвони(?:ть)?|набери(?:ть)?|вызови(?:ть)?|звони(?:ть)?)\s+(.+)", _RF
)

_RE_NAVIGATE = re.compile(
    r"(?:"
    r"найди[,\s]+как\s+(?:доехать|добраться|дойти)\s+до\s+(.+)|"
    r"как\s+(?:доехать|добраться|дойти)\s+до\s+(.+)|"
    r"(?:проложи[,\s]+)?маршрут\s+до\s+(.+)|"
    r"(?:открой?|запусти?)\s+(?:карты?|навигатор|маршрут)\s+(?:до\s+)?(.+)|"
    r"(?:доехать|добраться|дойти)\s+до\s+(.+)|"
    r"(?:навигация|навигатор)\s+(?:до|в|на)\s+(.+)"
    r")",
    _RF,
)


def _unit_to_seconds(amount: int, unit: str) -> int:
    u = unit.lower().replace("е", "е")  # ё→е уже сделано в нормализации
    if u.startswith("час"):
        return amount * 3600
    if u.startswith("мин"):
        return amount * 60
    return amount  # секунды


def _regex_parse(text: str) -> Optional[dict]:
    """
    L1: быстрый regex-парсер (без LLM).
    Возвращает dict если confidence ≥ 0.85, иначе None → L2 LLM.
    """
    # Нормализация: нижний регистр, ё→е, пунктуация, пробелы
    t = _normalize_text(text)

    # Exact cache
    cached = _EXACT_CACHE.get(t)
    if cached:
        return dict(cached)

    # --- Balance ---
    if _RE_BALANCE.search(t):
        return _make_result("balance", confidence=0.95)

    # --- Transfer: verb + (стоп-слова?) + name + amount (оба обязательны) ---
    m = _RE_TRANSFER_VNA.search(t)
    if m:
        amount = _parse_amount(m.group(2))
        if amount:
            return _make_result("transfer", recipient=m.group(1).lower(), amount=amount, confidence=0.90)
        return None  # имя есть, суммы нет → LLM

    m = _RE_TRANSFER_VAN.search(t)
    if m:
        amount = _parse_amount(m.group(1))
        if amount:
            return _make_result("transfer", recipient=m.group(2).lower(), amount=amount, confidence=0.90)
        return None

    # --- Topup: глагол + числовая сумма (оба обязательны) ---
    m = _RE_TOPUP.search(t)
    if m:
        amount = _parse_amount(m.group(2)) if m.group(2) else None
        if amount:
            phone = m.group(1).strip() if m.group(1) else None
            return _make_result("topup", phone=phone, amount=amount, confidence=0.90)
        return None

    # --- Alarm: числовое время ---
    m = _RE_ALARM_NUM.search(t)
    if m:
        if m.group(1) and m.group(2):
            h, mi = int(m.group(1)), int(m.group(2))
        elif m.group(3):
            h = int(m.group(3))
            mi = int(m.group(4)) if m.group(4) else 0
        else:
            h = mi = -1
        if 0 <= h <= 23 and 0 <= mi <= 59:
            return _make_result("alarm", hour=h, minute=mi, confidence=0.95)

    # --- Alarm: словесное время (полдень, полночь) ---
    m = _RE_ALARM_WORD.search(t)
    if m and m.group(1):
        h, mi = _ALARM_FIXED.get(m.group(1).lower(), (-1, -1))
        if h >= 0:
            return _make_result("alarm", hour=h, minute=mi, confidence=0.92)

    # --- Timer ---
    m = _RE_TIMER.search(t)
    if m:
        # Три пары групп: (1,2) verb, (3,4) bare, (5,6) через
        amt_str = m.group(1) or m.group(3) or m.group(5)
        unit_str = m.group(2) or m.group(4) or m.group(6)
        if amt_str and unit_str:
            return _make_result("timer", duration_seconds=_unit_to_seconds(int(amt_str), unit_str), confidence=0.95)

    # --- Open app ---
    m = _RE_OPEN_APP.search(t)
    if m:
        slug = _APP_SLUG_MAP.get(m.group(1).strip().lower())
        if slug:
            return _make_result("open_app", app=slug, confidence=0.95)

    # --- Call ---
    m = _RE_CALL.search(t)
    if m and m.group(1).strip():
        return _make_result("call", contact=m.group(1).strip(), confidence=0.88)

    # --- Navigate ---
    m = _RE_NAVIGATE.search(t)
    if m:
        dest = next((g for g in m.groups() if g and g.strip()), None)
        if dest:
            return _make_result("navigate", destination=dest.strip(), confidence=0.93)

    return None  # → L2 LLM


async def _llm_parse(text: str) -> dict:
    """L2: полный LLM-парсинг через DeepSeek / OpenRouter."""
    import httpx

    api_key = os.getenv("DEEPSEEK_API_KEY", "")
    if not api_key:
        _log.warning("LLM fallback triggered but DEEPSEEK_API_KEY not set, returning unknown")
        return _make_result("unknown", confidence=0.0)

    _log.info("LLM fallback: text=%r", text)

    payload = {
        "model": "deepseek/deepseek-chat",
        "messages": [
            {"role": "system", "content": PARSE_SYSTEM_PROMPT},
            {"role": "user",   "content": text},
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
        _log.error("LLM error: status=%s body=%r", resp.status_code, resp.text[:300])
        raise HTTPException(status_code=502, detail=f"OpenRouter error: {resp.text}")

    try:
        content = resp.json()["choices"][0]["message"]["content"]
        result = json.loads(content)
        _log.info("LLM result: intent=%s confidence=%s", result.get("intent"), result.get("confidence"))
        return result
    except Exception as e:
        _log.error("LLM parse error: %s", e)
        raise HTTPException(status_code=502, detail=f"Parse error: {e}")


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
- navigate   : проложить маршрут / найти как доехать (destination)
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
  "destination": null,
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
    destination: Optional[str] = None
    confidence: float = 0.9


@app.post("/parse", response_model=ParseResult)
async def parse_command(req: ParseRequest):
    """
    Каскадный парсинг команды:
      L1 (regex, ~0мс сервер) → если confidence ≥ 0.85, возврат без LLM
      L2 (DeepSeek LLM, ~2-5с) → fallback для сложных/неполных команд

    Не требует JWT — парсинг не касается банковских данных.
    """
    t0 = time.perf_counter()

    # L1: regex fast path
    result = _regex_parse(req.text)
    if result and result.get("confidence", 0) >= 0.85:
        ms = int((time.perf_counter() - t0) * 1000)
        _log.info("L1 hit: intent=%s confidence=%.2f text=%r latency=%dms",
                  result["intent"], result["confidence"], req.text, ms)
        return ParseResult(**{k: result.get(k) for k in ParseResult.model_fields})

    # L2: LLM fallback
    _log.info("L1 miss → LLM fallback: text=%r", req.text)
    data = await _llm_parse(req.text)
    ms = int((time.perf_counter() - t0) * 1000)
    _log.info("L2 result: intent=%s latency=%dms", data.get("intent"), ms)
    return ParseResult(**{k: data.get(k) for k in ParseResult.model_fields})


@app.get("/health")
def health():
    return {"status": "ok", "service": "vtb-vita-mock-api"}


class AuthRequest(BaseModel):
    pin: str

class AuthResponse(BaseModel):
    banking_token: str
    expires_in_seconds: int

@app.post("/auth", response_model=AuthResponse)
def auth(req: AuthRequest, request: Request, _: None = Depends(_check_app_token)):
    _check_rate_limit(f"auth:{_client_ip(request)}", max_requests=20)
    """
    Валидирует PIN → возвращает banking JWT (15 мин, только в памяти на клиенте).
    PIN однозначно идентифицирует персону: 1111→vitya, 2222→olga, 3333→artyom.
    Требует X-Api-Key (app_token) — чтобы только верифицированные устройства могли войти.
    """
    user_id = ALLOWED_PINS.get(req.pin)
    if not user_id:
        _log.warning("auth: PIN mismatch")
        raise HTTPException(status_code=403, detail="Неверный PIN")
    expire = datetime.now(timezone.utc) + timedelta(minutes=JWT_EXPIRE_MIN)
    token = jwt.encode(
        {"type": "banking_access", "user_id": user_id, "exp": expire},
        JWT_SECRET, algorithm="HS256"
    )
    _log.info("auth: user=%s banking token issued, expires_in=%ds", user_id, JWT_EXPIRE_MIN * 60)
    return AuthResponse(banking_token=token, expires_in_seconds=JWT_EXPIRE_MIN * 60)


class BiometricAuthRequest(BaseModel):
    user_id: str = "vitya"  # Android передаёт id выбранной персоны

@app.post("/auth/biometric", response_model=AuthResponse)
async def auth_biometric(req: BiometricAuthRequest, request: Request, _: None = Depends(_check_app_token)):
    _check_rate_limit(f"auth-bio:{_client_ip(request)}", max_requests=20)
    """
    Аутентификация через биометрию — биометрический промпт прошёл на устройстве,
    PIN не нужен. Тело запроса содержит user_id выбранной персоны.
    Требует X-Api-Key (app_token) чтобы только верифицированные устройства могли получить токен.
    """
    if not await db.get_user(req.user_id):
        raise HTTPException(status_code=403, detail="Unknown user")
    expire = datetime.now(timezone.utc) + timedelta(minutes=JWT_EXPIRE_MIN)
    token = jwt.encode(
        {"type": "banking_access", "user_id": req.user_id, "exp": expire},
        JWT_SECRET, algorithm="HS256"
    )
    _log.info("auth/biometric: user=%s token issued", req.user_id)
    return AuthResponse(banking_token=token, expires_in_seconds=JWT_EXPIRE_MIN * 60)


class PhoneVerifyRequest(BaseModel):
    phone: str

class PhoneVerifyResponse(BaseModel):
    app_token: str
    expires_in_days: int = 30

@app.post("/verify-phone", response_model=PhoneVerifyResponse)
def verify_phone(req: PhoneVerifyRequest, request: Request):
    _check_rate_limit(f"verify-phone:{_client_ip(request)}", max_requests=10)
    """
    Верификация номера телефона — первый запуск приложения.
    Не требует X-Api-Key (публичный эндпоинт).
    Если номер в ALLOWED_PHONES → возвращает app_token (JWT, 30 дней).
    """
    normalized = _normalize_phone(req.phone)
    if normalized not in ALLOWED_PHONES:
        _log.warning("verify-phone: rejected (unknown phone)")
        raise HTTPException(status_code=403, detail="Номер не найден")

    expire = datetime.now(timezone.utc) + timedelta(days=30)
    token = jwt.encode(
        {"type": "app_access", "phone": normalized, "exp": expire},
        JWT_SECRET,
        algorithm="HS256",
    )
    _log.info("verify-phone: app token issued")
    return PhoneVerifyResponse(app_token=token)


@app.get("/balance", response_model=BalanceResponse)
async def balance(user_id: str = Depends(_get_user_from_banking_token)):
    """
    Возвращает балансы всех счетов пользователя.
    Биометрия/PIN выполняется на стороне Android ДО вызова этого endpoint.
    Никакого confirm не нужно — это чтение, не действие.
    """
    accounts = await db.get_accounts(user_id)
    return BalanceResponse(
        accounts=_accounts_for_response(accounts),
        timestamp=_now(),
    )


@app.post("/lookup", response_model=LookupResponse)
async def lookup(req: LookupRequest, _: None = Depends(_check_app_token)):
    """
    Android вызывает при ручном вводе номера в модале перевода.
    Ищет номер в БД по всем пользователям.
    Полный номер на сервере, клиенту не отдаётся.
    """
    normalized = _normalize_phone(req.phone)
    contact = await db.get_contact_by_phone(normalized)
    if contact:
        return LookupResponse(
            found=True,
            display_name=contact["display_name"],
            recipient_banks=contact.get("available_banks", []),
        )
    return LookupResponse(found=False)


@app.post("/command", response_model=ConfirmationResponse)
async def command(req: CommandRequest, user_id: str = Depends(_get_user_from_banking_token)):
    """
    Принимает распарсенный intent transfer или topup.
    Биометрия/PIN выполняется на Android ДО вызова.
    Возвращает данные для модала подтверждения.
    """
    txn_id = _new_txn()
    ts = _now()
    user_accounts = await db.get_accounts(user_id)
    accounts = _accounts_for_response(user_accounts)
    default_acc_id = user_accounts[0]["account_id"]

    _log.info("command: user=%s txn=%s intent=%s amount=%s",
              user_id, txn_id, req.intent, req.amount)

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
            contact = await db.get_contact(user_id, key)
            if contact:
                contact_found = True
                display_name = contact["display_name"]
                recipient_phone = _format_phone(contact["phone"])
                recipient_banks = contact.get("available_banks", [])
            else:
                digits = "".join(c for c in req.recipient if c.isdigit())
                if len(digits) >= 10:
                    # Передан номер напрямую — ищем в глобальном индексе
                    normalized = _normalize_phone(req.recipient)
                    entry = PHONE_INDEX.get(normalized)
                    if entry:
                        contact_found = True
                        uid, ckey = entry
                        c = await db.get_contact(uid, ckey)
                        display_name = c["display_name"]
                        recipient_phone = _format_phone(c["phone"])
                        recipient_banks = c.get("available_banks", [])
                    else:
                        # Незнакомый номер — отображаем как есть, банки неизвестны
                        recipient_phone = _format_phone(req.recipient)
                else:
                    # Имя не найдено → ручной ввод
                    requires_manual = True
                    display_name = req.recipient  # подсказка в поле

        await db.create_pending(
            txn_id=txn_id,
            user_id=user_id,
            intent="transfer",
            amount=req.amount,
            display_name=display_name,
            phone=None,
            requires_manual_input=requires_manual,
        )

        return ConfirmationResponse(
            transaction_id=txn_id,
            intent="transfer",
            title="Перевод",
            subtitle="Укажите получателя" if requires_manual else display_name,
            amount=req.amount,
            source_accounts=accounts,
            default_account_id=default_acc_id,
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

        await db.create_pending(
            txn_id=txn_id,
            user_id=user_id,
            intent="topup",
            amount=req.amount,
            display_name=None,
            phone=phone,
            requires_manual_input=requires_manual,
        )

        return ConfirmationResponse(
            transaction_id=txn_id,
            intent="topup",
            title="Пополнение телефона",
            subtitle="Укажите номер телефона" if requires_manual else formatted_phone,
            amount=req.amount,
            source_accounts=accounts,
            default_account_id=default_acc_id,
            requires_manual_input=requires_manual,
            topup_phone=formatted_phone,
            operator=operator,
            fee=0.0,
            timestamp=ts,
        )

    raise HTTPException(status_code=400, detail=f"Неизвестный intent: {req.intent}")


@app.post("/confirm/{transaction_id}", response_model=OperationResult)
async def confirm(transaction_id: str, req: ConfirmRequest, user_id: str = Depends(_get_user_from_banking_token)):
    """
    Пользователь нажал «Подтвердить» в модале.
    Списывает с выбранного счёта, сохраняет транзакцию в БД.
    """
    pending = await db.pop_pending(transaction_id)
    if pending is None:
        _log.warning("confirm: txn=%s not found (already executed or expired)", transaction_id)
        raise HTTPException(status_code=404, detail="Операция не найдена или уже выполнена")

    ts = _now()
    intent = pending["intent"]
    op_user_id = pending.get("user_id", user_id)
    source_account = await db.get_account(op_user_id, req.source_account_id)
    if source_account is None:
        source_account = (await db.get_accounts(op_user_id))[0]
    amount = pending.get("amount", 0)

    if amount > source_account["balance"]:
        raise HTTPException(status_code=422, detail="Недостаточно средств на выбранном счёте")

    balance_after = round(source_account["balance"] - amount, 2)
    await db.update_balance(op_user_id, source_account["account_id"], balance_after)

    await db.insert_transaction(
        txn_id=transaction_id,
        user_id=op_user_id,
        account_id=source_account["account_id"],
        intent=intent,
        amount=amount,
        display_name=pending.get("display_name"),
        phone=pending.get("phone") or req.manual_phone,
        selected_bank=req.selected_bank,
    )

    _log.info("confirm: user=%s txn=%s intent=%s amount=%.2f account=%s balance_after=%.2f",
              op_user_id, transaction_id, intent, amount, source_account["account_id"], balance_after)

    bank_label = f" через {req.selected_bank}" if req.selected_bank else ""

    if intent == "transfer":
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


class TransactionItem(BaseModel):
    id: str
    type: str
    amount: float
    direction: str
    category: Optional[str] = None
    description: Optional[str] = None
    booking_date: str
    merchant: Optional[dict] = None
    recipient: Optional[dict] = None
    sender: Optional[dict] = None


class TransactionsResponse(BaseModel):
    transactions: list[TransactionItem]
    count: int


@app.get("/transactions", response_model=TransactionsResponse)
async def transactions(
    limit: int = 20,
    user_id: str = Depends(_get_user_from_banking_token),
):
    """История операций из БД: seed-записи + новые после каждого /confirm."""
    items = await db.get_transactions(user_id, limit=limit)
    return TransactionsResponse(transactions=items, count=len(items))


# ---------------------------------------------------------------------------
# STT WebSocket — /ws/stt
# ---------------------------------------------------------------------------
# Android подключается, стримит PCM 16kHz/16bit/mono chunks (binary frames).
# Сервер проксирует в Яндекс SpeechKit gRPC streaming и возвращает JSON:
#   {"type": "partial", "text": "..."}
#   {"type": "final",   "text": "..."}
#   {"type": "error",   "message": "..."}
#
# Если YANDEX_SPEECHKIT_API_KEY не задан — работает в режиме echo-mock
# (возвращает фиктивный partial/final для отладки Android-стороны).
# ---------------------------------------------------------------------------

@app.websocket("/ws/stt")
async def stt_endpoint(ws: WebSocket):
    await ws.accept()
    api_key = os.getenv("YANDEX_SPEECHKIT_API_KEY", "").strip()
    _log.info("STT session: mode=%s", "grpc" if api_key else "mock")
    if not api_key:
        await _stt_mock(ws)
    else:
        await _stt_stream_grpc(ws, api_key)


async def _stt_mock(ws: WebSocket):
    """
    Echo-mock STT: собирает PCM-чанки, через ~1 сек отдаёт partial,
    при отключении — final с демо-фразой.
    Используется когда YANDEX_SPEECHKIT_API_KEY не задан.
    """
    BYTES_PER_SEC = 16_000 * 2          # 16kHz, 16-bit mono
    PARTIAL_EVERY = BYTES_PER_SEC       # раз в секунду
    accumulated = 0
    DEMO_TEXT = "переведи сто рублей маше"

    try:
        while True:
            try:
                data = await asyncio.wait_for(ws.receive(), timeout=30.0)
            except asyncio.TimeoutError:
                break
            if data["type"] == "websocket.disconnect":
                break
            raw = data.get("bytes")
            if raw:
                accumulated += len(raw)
                if accumulated >= PARTIAL_EVERY:
                    accumulated = 0
                    await ws.send_text(json.dumps(
                        {"type": "partial", "text": DEMO_TEXT},
                        ensure_ascii=False
                    ))
    except WebSocketDisconnect:
        pass

    try:
        await ws.send_text(json.dumps(
            {"type": "final", "text": DEMO_TEXT},
            ensure_ascii=False
        ))
        await ws.close()
    except Exception:
        pass


_GRPC_ENDPOINT = "stt.api.cloud.yandex.net:443"
_GRPC_SENTINEL  = object()  # сигнал конца потока для asyncio.Queue


async def _stt_stream_grpc(ws: WebSocket, api_key: str):
    """
    STT через Яндекс SpeechKit gRPC Streaming API (v2).
    Один двунаправленный gRPC-стрим на WebSocket-сессию.
    Архитектура: asyncio.Queue соединяет WS-читателя и gRPC-итератор.

    Биллинг: одна сессия = одна единица тарификации (не N REST-запросов).
    Android-сторона не меняется — WebSocket-протокол идентичен.
    """
    folder_id = os.getenv("YANDEX_FOLDER_ID", "").strip()
    queue: asyncio.Queue = asyncio.Queue(maxsize=50)

    # --- Task A: читает PCM-чанки из WebSocket → кладёт в queue ---
    async def ws_reader():
        try:
            while True:
                data = await asyncio.wait_for(ws.receive(), timeout=30.0)
                if data["type"] == "websocket.disconnect":
                    break
                if data.get("text") == "DONE":
                    _log.warning("[STT-gRPC] received DONE signal")
                    break
                raw = data.get("bytes")
                if raw:
                    await queue.put(raw)
        except (asyncio.TimeoutError, WebSocketDisconnect):
            _log.info("STT-gRPC ws_reader: timeout or disconnect")
        finally:
            await queue.put(_GRPC_SENTINEL)

    # --- gRPC request async generator: конфиг → PCM-чанки из queue ---
    async def request_iterator():
        yield stt_pb2.StreamingRecognitionRequest(
            config=stt_pb2.RecognitionConfig(
                specification=stt_pb2.RecognitionSpec(
                    audio_encoding=stt_pb2.RecognitionSpec.LINEAR16_PCM,
                    sample_rate_hertz=16000,
                    language_code="ru-RU",
                    partial_results=True,
                    single_utterance=True,
                ),
                folder_id=folder_id,
            )
        )
        while True:
            chunk = await queue.get()
            if chunk is _GRPC_SENTINEL:
                return
            yield stt_pb2.StreamingRecognitionRequest(audio_content=chunk)

    # --- Task B: читает gRPC-ответы → шлёт JSON в WebSocket ---
    async def grpc_reader(stub):
        final_sent = False
        try:
            call = stub.StreamingRecognize(
                request_iterator(),
                metadata=[("authorization", f"Api-Key {api_key}")],
            )
            async for response in call:
                for chunk in response.chunks:
                    text = chunk.alternatives[0].text if chunk.alternatives else ""
                    if chunk.final:
                        _log.info("STT-gRPC final: %r", text)
                        await ws.send_text(json.dumps(
                            {"type": "final", "text": text}, ensure_ascii=False
                        ))
                        final_sent = True
                        return  # single_utterance=True → один финал на сессию
                    elif text:
                        _log.debug("STT-gRPC partial: %r", text)
                        try:
                            await ws.send_text(json.dumps(
                                {"type": "partial", "text": text}, ensure_ascii=False
                            ))
                        except Exception:
                            pass
        except grpc.aio.AioRpcError as e:
            _log.error("STT-gRPC error: %s — %s", e.code().name, e.details())
            try:
                await ws.send_text(json.dumps(
                    {"type": "error", "message": f"STT недоступен: {e.code().name}"},
                    ensure_ascii=False,
                ))
            except Exception:
                pass
        finally:
            # Гарантируем что Android всегда получит final и не зависнет
            if not final_sent:
                try:
                    await ws.send_text(json.dumps(
                        {"type": "final", "text": ""}, ensure_ascii=False
                    ))
                except Exception:
                    pass

    _log.info("STT-gRPC session start, folder_id=%r", folder_id)
    creds = grpc.ssl_channel_credentials()
    async with grpc.aio.secure_channel(_GRPC_ENDPOINT, creds) as channel:
        stub = stt_pb2_grpc.SttServiceStub(channel)
        await asyncio.gather(ws_reader(), grpc_reader(stub))

    try:
        await ws.close()
    except Exception:
        pass


