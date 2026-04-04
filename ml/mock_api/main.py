"""
VTB Vita — Mock API (C-03)
Принимает распарсенный intent от NLP, возвращает данные для модала подтверждения.

Flow (transfer/topup):
  Widget → NLP /parse → [Biometric/PIN on Android] → /command → modal → /confirm/{id} → result

Flow (balance):
  Widget → [Biometric/PIN on Android] → GET /balance → показать данные (без confirm)
"""

from fastapi import FastAPI, HTTPException, Header, Depends, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, Literal
from datetime import datetime, timezone, timedelta
from jose import jwt, JWTError
from dotenv import load_dotenv
import uuid
import os
import re
import asyncio
import json

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
    r"(?:\s+(?:мне|себе))?"                             # «пополни мне телефон»
    r"(?:\s+(?:телефон|номер))?"
    r"(?:\s+([\+\d][\d\s\-\(\)]{7,15}))?"              # группа 1: телефон (опционально)
    r"(?:\s+(?:на\s+)?)?" + _AMT_GROUP.replace("(", "(?:", 1),  # группа без захвата, захватываем ниже
    _RF,
)
# Упрощаем topup — отдельный паттерн с явным захватом суммы
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
    import json as _json

    api_key = os.getenv("DEEPSEEK_API_KEY", "")
    if not api_key:
        return _make_result("unknown", confidence=0.0)

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
        raise HTTPException(status_code=502, detail=f"OpenRouter error: {resp.text}")

    try:
        content = resp.json()["choices"][0]["message"]["content"]
        return _json.loads(content)
    except Exception as e:
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
    # L1: regex fast path
    result = _regex_parse(req.text)
    if result and result.get("confidence", 0) >= 0.85:
        return ParseResult(**{k: result.get(k) for k in ParseResult.model_fields})

    # L2: LLM fallback
    data = await _llm_parse(req.text)
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
    import logging as _log
    _log.warning("[STT] stt_endpoint called, accepting...")
    await ws.accept()
    api_key = os.getenv("YANDEX_SPEECHKIT_API_KEY", "").strip()
    _log.warning(f"[STT] endpoint: api_key={'SET' if api_key else 'EMPTY'}")
    if not api_key:
        await _stt_mock(ws)
    else:
        await _stt_stream(ws, api_key)


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


async def _stt_stream(ws: WebSocket, api_key: str):
    """
    STT через Яндекс SpeechKit REST API (v1/stt:recognize).
    Аудио-чанки (PCM 16kHz/16bit/mono) накапливаются из WebSocket.
    Partial: каждую секунду накопленного аудио → REST-запрос → {"type":"partial"}.
    Final: по закрытию WebSocket → финальный REST-запрос → {"type":"final"}.
    """
    import httpx

    SAMPLE_RATE    = 16_000
    BYTES_PER_SEC  = SAMPLE_RATE * 2          # 16-bit mono
    PARTIAL_EVERY  = BYTES_PER_SEC            # partial раз в ~1 сек

    folder_id = os.getenv("YANDEX_FOLDER_ID", "").strip()

    async def _recognize(pcm: bytes) -> str:
        """Отправляет сырой PCM в Яндекс SpeechKit REST API (format=lpcm)."""
        import logging as _log
        params = {
            "lang":             "ru-RU",
            "topic":            "general",
            "format":           "lpcm",
            "sampleRateHertz":  "16000",
        }
        if folder_id:
            params["folderId"] = folder_id
        _log.warning(f"[STT] _recognize: pcm={len(pcm)}b folder_id={folder_id!r}")
        async with httpx.AsyncClient(timeout=15) as client:
            resp = await client.post(
                "https://stt.api.cloud.yandex.net/speech/v1/stt:recognize",
                headers={
                    "Authorization": f"Api-Key {api_key}",
                    "Content-Type":  "application/octet-stream",
                },
                params=params,
                content=pcm,
            )
        _log.warning(f"[STT] Yandex response: status={resp.status_code}, body={resp.text[:300]!r}")
        if resp.status_code == 200:
            return resp.json().get("result", "")
        return ""

    import logging as _log
    _log.warning(f"[STT] _stt_stream started, folder_id={folder_id!r}")

    audio_chunks: list[bytes] = []
    total_bytes       = 0
    last_partial_bytes = 0

    try:
        while True:
            try:
                data = await asyncio.wait_for(ws.receive(), timeout=30.0)
            except asyncio.TimeoutError:
                _log.warning("[STT] receive timeout")
                break
            if data["type"] == "websocket.disconnect":
                break
            # Клиент явно сигнализирует конец аудио — выходим и отвечаем финалом
            if data.get("text") == "DONE":
                _log.warning("[STT] received DONE signal")
                break
            raw = data.get("bytes")
            if raw:
                audio_chunks.append(raw)
                total_bytes += len(raw)
                # Partial: каждую секунду аудио
                if total_bytes - last_partial_bytes >= PARTIAL_EVERY:
                    last_partial_bytes = total_bytes
                    text = await _recognize(b"".join(audio_chunks))
                    if text:
                        try:
                            await ws.send_text(json.dumps(
                                {"type": "partial", "text": text},
                                ensure_ascii=False
                            ))
                        except Exception:
                            pass  # client disconnected
    except WebSocketDisconnect:
        pass

    # Final
    combined = b"".join(audio_chunks)
    import logging as _log
    _log.warning(f"[STT] session ended: total_bytes={total_bytes}, chunks={len(audio_chunks)}")
    if combined:
        text = ""
        try:
            text = await _recognize(combined)
        except Exception as exc:
            print(f"[STT] _recognize exception: {exc}")
        try:
            await ws.send_text(json.dumps(
                {"type": "final", "text": text or ""},
                ensure_ascii=False
            ))
        except Exception:
            pass  # client already disconnected

    try:
        await ws.close()
    except Exception:
        pass
