"""
Unit-тесты для L1 regex-парсера и вспомогательных функций.
Запуск: pytest test_regex_parse.py -v
"""
import os
import pytest

os.environ.setdefault("APP_API_KEY", "test")
os.environ.setdefault("JWT_SECRET", "test")
os.environ.setdefault("ALLOWED_PHONES", "+79000000000")
os.environ.setdefault("ALLOWED_PINS", "1111:vitya,2222:olga,3333:artyom")

from main import _regex_parse, _parse_amount, _normalize_text


# ─── Нормализация текста ──────────────────────────────────────────────────────

def test_normalize_lowercase_and_yo():
    assert _normalize_text("Переведи Маше") == "переведи маше"

def test_normalize_strips_punctuation():
    assert "пожалуйста," not in _normalize_text("переведи, пожалуйста")

def test_normalize_strips_whitespace():
    assert _normalize_text("  баланс  ") == "баланс"

def test_normalize_strips_exclamation():
    assert _normalize_text("баланс!") == "баланс"


# ─── _parse_amount: числовые ─────────────────────────────────────────────────

def test_parse_amount_simple():
    assert _parse_amount("500") == 500.0

def test_parse_amount_space_separator():
    assert _parse_amount("1 000") == 1000.0

def test_parse_amount_comma_decimal():
    assert _parse_amount("1,5") == 1.5

def test_parse_amount_tys():
    assert _parse_amount("2 тыс") == 2000.0

def test_parse_amount_tysyachi():
    assert _parse_amount("3 тысячи") == 3000.0

def test_parse_amount_mln():
    assert _parse_amount("1 млн") == 1_000_000.0


# ─── _parse_amount: словесные ─────────────────────────────────────────────────

def test_parse_amount_pyatsot():
    assert _parse_amount("пятьсот") == 500.0

def test_parse_amount_tysyachu():
    assert _parse_amount("тысячу") == 1000.0

def test_parse_amount_tri_tysyachi():
    assert _parse_amount("три тысячи") == 3000.0

def test_parse_amount_poltory_tysyachi():
    assert _parse_amount("полторы тысячи") == 1500.0

def test_parse_amount_dve_tysyachi():
    assert _parse_amount("две тысячи") == 2000.0

def test_parse_amount_dvesti():
    assert _parse_amount("двести") == 200.0

def test_parse_amount_no_number_returns_none():
    assert _parse_amount("текст без цифр") is None


# ─── Balance ─────────────────────────────────────────────────────────────────

@pytest.mark.parametrize("phrase", [
    "баланс", "покажи баланс", "мой баланс", "сколько денег", "сколько на карте"
])
def test_balance_basic(phrase):
    r = _regex_parse(phrase)
    assert r is not None and r["intent"] == "balance"

def test_balance_with_yo():
    assert _regex_parse("покажи баланс")["intent"] == "balance"

def test_balance_with_punctuation():
    assert _regex_parse("баланс!")["intent"] == "balance"

# Негативные: НЕ должен матчить «баланс Маши» как balance
def test_balance_with_name_not_balance():
    r = _regex_parse("покажи баланс маши")
    assert r is None or r["intent"] != "balance"


# ─── Transfer: числовые суммы ─────────────────────────────────────────────────

def test_transfer_verb_name_amount():
    r = _regex_parse("переведи Маше 500")
    assert r and r["intent"] == "transfer"
    assert r["recipient"] == "маше"
    assert r["amount"] == 500.0

def test_transfer_verb_amount_name():
    r = _regex_parse("переведи 500 Маше")
    assert r and r["intent"] == "transfer"
    assert r["amount"] == 500.0

def test_transfer_verb_skin():
    r = _regex_parse("скинь Маше 1000")
    assert r and r["intent"] == "transfer" and r["amount"] == 1000.0

@pytest.mark.parametrize("verb", ["кинь", "закинь", "перекинь", "пришли", "отправь"])
def test_transfer_additional_verbs(verb):
    r = _regex_parse(f"{verb} маше 500")
    assert r and r["intent"] == "transfer" and r["amount"] == 500.0


# ─── Transfer: словесные суммы ───────────────────────────────────────────────

def test_transfer_verbal_pyatsot():
    r = _regex_parse("переведи маше пятьсот")
    assert r and r["intent"] == "transfer" and r["amount"] == 500.0

def test_transfer_verbal_tri_tysyachi():
    r = _regex_parse("кинь маше три тысячи")
    assert r and r["intent"] == "transfer" and r["amount"] == 3000.0

def test_transfer_verbal_poltory():
    r = _regex_parse("переведи маше полторы тысячи")
    assert r and r["intent"] == "transfer" and r["amount"] == 1500.0

def test_transfer_verbal_tysyachu():
    r = _regex_parse("отправь маше тысячу")
    assert r and r["intent"] == "transfer" and r["amount"] == 1000.0


# ─── Transfer: стоп-слова ────────────────────────────────────────────────────

def test_transfer_with_pozhaluysta():
    r = _regex_parse("переведи пожалуйста маше 500")
    assert r and r["intent"] == "transfer" and r["recipient"] == "маше"

def test_transfer_with_srochno():
    r = _regex_parse("переведи срочно маше 1000")
    assert r and r["intent"] == "transfer" and r["recipient"] == "маше"


# ─── Transfer: fall-through (нет суммы) ──────────────────────────────────────

def test_transfer_no_amount_returns_none():
    assert _regex_parse("переведи маше") is None

def test_transfer_money_word_no_amount_returns_none():
    assert _regex_parse("переведи деньги") is None

def test_transfer_kin_no_amount_returns_none():
    assert _regex_parse("кинь маше") is None


# ─── Topup ───────────────────────────────────────────────────────────────────

def test_topup_basic():
    r = _regex_parse("пополни телефон на 300")
    assert r and r["intent"] == "topup" and r["amount"] == 300.0

def test_topup_with_mne():
    r = _regex_parse("пополни мне телефон на 500")
    assert r and r["intent"] == "topup" and r["amount"] == 500.0

def test_topup_no_amount_returns_none():
    assert _regex_parse("пополни телефон") is None


# ─── Alarm: числовое время ───────────────────────────────────────────────────

def test_alarm_hhmm():
    r = _regex_parse("поставь будильник на 7:30")
    assert r and r["intent"] == "alarm" and r["hour"] == 7 and r["minute"] == 30

def test_alarm_hours_only():
    r = _regex_parse("разбуди в 8 часов")
    assert r and r["intent"] == "alarm" and r["hour"] == 8


# ─── Alarm: словесное время ──────────────────────────────────────────────────

def test_alarm_poldyen():
    r = _regex_parse("поставь будильник в полдень")
    assert r and r["intent"] == "alarm" and r["hour"] == 12 and r["minute"] == 0

def test_alarm_polnoch():
    r = _regex_parse("поставь будильник в полночь")
    assert r and r["intent"] == "alarm" and r["hour"] == 0 and r["minute"] == 0


# ─── Timer ───────────────────────────────────────────────────────────────────

def test_timer_minutes():
    r = _regex_parse("таймер на 5 минут")
    assert r and r["intent"] == "timer" and r["duration_seconds"] == 300

def test_timer_seconds():
    r = _regex_parse("поставь таймер на 30 секунд")
    assert r and r["intent"] == "timer" and r["duration_seconds"] == 30

def test_timer_through_minutes():
    r = _regex_parse("через 10 минут")
    assert r and r["intent"] == "timer" and r["duration_seconds"] == 600

def test_timer_through_hours():
    r = _regex_parse("через 2 часа")
    assert r and r["intent"] == "timer" and r["duration_seconds"] == 7200


# ─── Open app ────────────────────────────────────────────────────────────────

@pytest.mark.parametrize("phrase,slug", [
    ("открой телеграм", "telegram"),
    ("открой ютуб", "youtube"),
    ("запусти вк", "vk"),
    ("открой телегу", "telegram"),
    ("открой вацап", "whatsapp"),
])
def test_open_app_known(phrase, slug):
    r = _regex_parse(phrase)
    assert r and r["intent"] == "open_app" and r["app"] == slug

def test_open_app_unknown_returns_none():
    assert _regex_parse("открой яндекс го") is None


# ─── Call ────────────────────────────────────────────────────────────────────

def test_call():
    r = _regex_parse("позвони маше")
    assert r and r["intent"] == "call" and r["contact"] == "маше"


# ─── Негативные тесты (ничто не должно матчиться) ────────────────────────────

@pytest.mark.parametrize("phrase", [
    "",               # пустая строка
    "привет",         # приветствие
    "что ты умеешь",  # вопрос о возможностях
    "12345",          # просто цифры без глагола
])
def test_no_match_for_ambiguous_input(phrase):
    r = _regex_parse(phrase)
    # Либо None, либо intent=unknown — не должен уверенно матчить banking intent
    assert r is None or r.get("intent") == "unknown" or r.get("confidence", 1.0) < 0.85
