"""
Unit-тесты для L1 regex-парсера.
Запуск: python3 test_regex_parse.py
"""
import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

os.environ.setdefault("APP_API_KEY", "test")
os.environ.setdefault("JWT_SECRET", "test")
os.environ.setdefault("ALLOWED_PHONES", "+79000000000")
os.environ.setdefault("ALLOWED_PIN", "1234")

from main import _regex_parse, _parse_amount, _normalize_text

PASS = 0
FAIL = 0

def check(label: str, condition: bool, detail: str = ""):
    global PASS, FAIL
    if condition:
        print(f"  PASS  {label}")
        PASS += 1
    else:
        print(f"  FAIL  {label}" + (f" — {detail}" if detail else ""))
        FAIL += 1

def section(name: str):
    print(f"\n{'─'*50}")
    print(f"  {name}")
    print(f"{'─'*50}")


# ─────────────────────────────────────────────────
section("Нормализация текста")
# ─────────────────────────────────────────────────
check("ё→е",       _normalize_text("Переведи Маше") == "переведи маше")
check("пунктуация","пожалуйста," not in _normalize_text("переведи, пожалуйста"))
check("пробелы",   _normalize_text("  баланс  ") == "баланс")
check("восклицание", _normalize_text("баланс!") == "баланс")


# ─────────────────────────────────────────────────
section("_parse_amount — числовые")
# ─────────────────────────────────────────────────
check("простое число",         _parse_amount("500") == 500.0)
check("число с пробелом",      _parse_amount("1 000") == 1000.0)
check("число с запятой",       _parse_amount("1,5") == 1.5)
check("число + тыс",           _parse_amount("2 тыс") == 2000.0)
check("число + тысячи",        _parse_amount("3 тысячи") == 3000.0)
check("число + млн",           _parse_amount("1 млн") == 1_000_000.0)


# ─────────────────────────────────────────────────
section("_parse_amount — словесные (новое)")
# ─────────────────────────────────────────────────
check("пятьсот",               _parse_amount("пятьсот") == 500.0)
check("тысячу (одиночно)",     _parse_amount("тысячу") == 1000.0)
check("три тысячи",            _parse_amount("три тысячи") == 3000.0)
check("полторы тысячи",        _parse_amount("полторы тысячи") == 1500.0)
check("две тысячи",            _parse_amount("две тысячи") == 2000.0)
check("двести",                _parse_amount("двести") == 200.0)
check("нет числа → None",      _parse_amount("текст без цифр") is None)


# ─────────────────────────────────────────────────
section("Balance")
# ─────────────────────────────────────────────────
for phrase in ["баланс", "покажи баланс", "мой баланс", "сколько денег", "сколько на карте"]:
    r = _regex_parse(phrase)
    check(f"'{phrase}'", r is not None and r["intent"] == "balance")

check("ё в запросе",   _regex_parse("покажи баланс")["intent"] == "balance")
check("с пунктуацией", _regex_parse("баланс!")["intent"] == "balance")
# Не должен матчить «баланс Маши»
r = _regex_parse("покажи баланс маши")
check("'баланс маши' → не balance", r is None or r["intent"] != "balance")


# ─────────────────────────────────────────────────
section("Transfer — числовые суммы")
# ─────────────────────────────────────────────────
r = _regex_parse("переведи Маше 500")
check("переведи Маше 500", r and r["intent"] == "transfer" and r["recipient"] == "маше" and r["amount"] == 500.0)

r = _regex_parse("переведи 500 Маше")
check("переведи 500 Маше (VAN)", r and r["intent"] == "transfer" and r["amount"] == 500.0)

r = _regex_parse("скинь Маше 1000")
check("скинь Маше 1000", r and r["intent"] == "transfer" and r["amount"] == 1000.0)


# ─────────────────────────────────────────────────
section("Transfer — новые глаголы (новое)")
# ─────────────────────────────────────────────────
for verb in ["кинь", "закинь", "перекинь", "пришли", "отправь"]:
    r = _regex_parse(f"{verb} маше 500")
    check(f"'{verb} маше 500'", r and r["intent"] == "transfer" and r["amount"] == 500.0)


# ─────────────────────────────────────────────────
section("Transfer — словесные суммы (новое)")
# ─────────────────────────────────────────────────
r = _regex_parse("переведи маше пятьсот")
check("переведи маше пятьсот", r and r["intent"] == "transfer" and r["amount"] == 500.0)

r = _regex_parse("кинь маше три тысячи")
check("кинь маше три тысячи", r and r["intent"] == "transfer" and r["amount"] == 3000.0)

r = _regex_parse("переведи маше полторы тысячи")
check("переведи маше полторы тысячи", r and r["intent"] == "transfer" and r["amount"] == 1500.0)

r = _regex_parse("отправь маше тысячу")
check("отправь маше тысячу", r and r["intent"] == "transfer" and r["amount"] == 1000.0)


# ─────────────────────────────────────────────────
section("Transfer — стоп-слова (новое)")
# ─────────────────────────────────────────────────
r = _regex_parse("переведи пожалуйста маше 500")
check("переведи пожалуйста маше 500 (стоп-слово)", r and r["intent"] == "transfer" and r["recipient"] == "маше")

r = _regex_parse("переведи срочно маше 1000")
check("переведи срочно маше 1000", r and r["intent"] == "transfer" and r["recipient"] == "маше")


# ─────────────────────────────────────────────────
section("Transfer — fall-through (нет суммы)")
# ─────────────────────────────────────────────────
check("переведи маше → None",       _regex_parse("переведи маше") is None)
check("переведи деньги → None",     _regex_parse("переведи деньги") is None)
check("кинь маше → None",           _regex_parse("кинь маше") is None)


# ─────────────────────────────────────────────────
section("Topup")
# ─────────────────────────────────────────────────
r = _regex_parse("пополни телефон на 300")
check("пополни телефон на 300", r and r["intent"] == "topup" and r["amount"] == 300.0)

r = _regex_parse("пополни мне телефон на 500")
check("пополни мне телефон на 500 (новое)", r and r["intent"] == "topup" and r["amount"] == 500.0)

check("пополни телефон → None", _regex_parse("пополни телефон") is None)


# ─────────────────────────────────────────────────
section("Alarm — числовое время")
# ─────────────────────────────────────────────────
r = _regex_parse("поставь будильник на 7:30")
check("7:30", r and r["intent"] == "alarm" and r["hour"] == 7 and r["minute"] == 30)

r = _regex_parse("разбуди в 8 часов")
check("разбуди в 8 часов", r and r["intent"] == "alarm" and r["hour"] == 8)


# ─────────────────────────────────────────────────
section("Alarm — словесное время (новое)")
# ─────────────────────────────────────────────────
r = _regex_parse("поставь будильник в полдень")
check("в полдень → 12:00", r and r["intent"] == "alarm" and r["hour"] == 12 and r["minute"] == 0)

r = _regex_parse("поставь будильник в полночь")
check("в полночь → 0:00", r and r["intent"] == "alarm" and r["hour"] == 0 and r["minute"] == 0)


# ─────────────────────────────────────────────────
section("Timer")
# ─────────────────────────────────────────────────
r = _regex_parse("таймер на 5 минут")
check("таймер на 5 минут", r and r["intent"] == "timer" and r["duration_seconds"] == 300)

r = _regex_parse("поставь таймер на 30 секунд")
check("30 секунд", r and r["intent"] == "timer" and r["duration_seconds"] == 30)

r = _regex_parse("через 10 минут")
check("через 10 минут (новое)", r and r["intent"] == "timer" and r["duration_seconds"] == 600)

r = _regex_parse("через 2 часа")
check("через 2 часа (новое)", r and r["intent"] == "timer" and r["duration_seconds"] == 7200)


# ─────────────────────────────────────────────────
section("Open app")
# ─────────────────────────────────────────────────
cases = [
    ("открой телеграм", "telegram"),
    ("открой ютуб", "youtube"),
    ("запусти вк", "vk"),
    ("открой телегу", "telegram"),   # новый алиас
    ("открой вацап", "whatsapp"),    # новый алиас
]
for phrase, slug in cases:
    r = _regex_parse(phrase)
    check(f"'{phrase}'", r and r["intent"] == "open_app" and r["app"] == slug)

check("неизвестное приложение → None", _regex_parse("открой яндекс го") is None)


# ─────────────────────────────────────────────────
section("Call")
# ─────────────────────────────────────────────────
r = _regex_parse("позвони маше")
check("позвони маше", r and r["intent"] == "call" and r["contact"] == "маше")


# ─────────────────────────────────────────────────
print(f"\n{'═'*50}")
total = PASS + FAIL
print(f"  Итого: {PASS}/{total} пройдено")
if FAIL:
    print(f"  ПРОВАЛЕНО: {FAIL}")
    sys.exit(1)
else:
    print("  ВСЕ ТЕСТЫ ПРОЙДЕНЫ")
print(f"{'═'*50}")
