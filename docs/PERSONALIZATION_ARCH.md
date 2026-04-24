# VTB Vita — Техническая архитектура персонализации

> Продуктовая логика (типы подсказок, UX, фразы): `PERSONALIZATION.md`  
> Последнее обновление: 12 апреля 2026

---

## Компоненты системы

```
┌──────────────────────────────────────────────────────────────┐
│  Android Widget (RemoteViews)                                │
│   - запрашивает hint при каждом обновлении виджета           │
│   - таймаут 1 500 мс, затем fallback                         │
└──────────────────────┬───────────────────────────────────────┘
                       │ GET /hint?...
                       ▼
┌──────────────────────────────────────────────────────────────┐
│  Personalization Service  (FastAPI, ml/mock_api)             │
│                                                              │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │ Rule Engine │  │  Event Bus   │  │   Admin API      │   │
│  │ (время,     │  │  (события    │  │   (ручная        │   │
│  │  сегмент)   │  │   юзера)     │  │    отправка)     │   │
│  └──────┬──────┘  └──────┬───────┘  └────────┬─────────┘   │
│         └────────────────┴───────────────────┘             │
│                           ↓                                  │
│               ┌───────────────────────┐                     │
│               │     Message Pool      │                     │
│               │  (hints/pool.json)    │                     │
│               └───────────────────────┘                     │
└──────────────────────────────────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────────┐
│  Android App (InputActivity)                                 │
│   - получает hint из SharedPreferences (сохранил виджет)     │
│   - если type = VYGODA | REMINDER → показывает баннер        │
└──────────────────────────────────────────────────────────────┘
```

---

## Offline Fallback

Нейтральные фразы хранятся прямо в приложении — не требуют сети, никогда не исчезают.

```kotlin
// NeutralHints.kt — hardcoded локальный пул
val NEUTRAL_HINTS = listOf(
    "Как дела?",
    "Готов помочь",
    "Всё под контролем",
    "На связи",
    "Хорошего дня",
    "Чем могу помочь?",
    "Слушаю",
    "Рядом, если что"
)
```

**Логика выбора:**

```kotlin
fun resolveHint(serverHint: Hint?, lastShownNeutral: String): Hint {
    if (serverHint != null) return serverHint
    // Случайный нейтральный, не повторяющий предыдущий
    return Hint(
        text = NEUTRAL_HINTS.filter { it != lastShownNeutral }.random(),
        type = HintType.NEUTRAL,
        color = HintColor.MUTED
    )
}
```

**Таймаут запроса к серверу:**

```kotlin
// HintRepository.kt
suspend fun fetchHint(context: UserContext): Hint? {
    return withTimeoutOrNull(1_500) {
        api.getHint(context)
    }
}
```

---

## API эндпоинты

### `GET /hint` — получить актуальную подсказку

**Query params:**

| Параметр | Тип | Описание |
|----------|-----|----------|
| `balance` | Int | Текущий баланс в рублях |
| `segment` | String | `default`, `active_spender`, `investor`, `salary` |
| `last_action` | String | `transfer`, `topup`, `balance`, `none` |
| `has_cashback` | Bool | Подключён ли кешбэк |
| `has_deposit` | Bool | Есть ли вклад |
| `next_payment_days` | Int? | Дней до следующего обязательного платежа |
| `next_payment_amount` | Int? | Сумма платежа в рублях |

**Response:**

```json
{
  "text": "85 000 ₽ на накопительном дадут +1 275 ₽ в месяц",
  "type": "vygoda",
  "color": "success",
  "product_id": "deposit",
  "cta": "Открыть",
  "deep_link": "vtb://deposit/open"
}
```

| Поле | Значения |
|------|----------|
| `type` | `neutral`, `reminder`, `analytics`, `vygoda` |
| `color` | `muted`, `warning`, `primary`, `success` |
| `product_id` | `cashback`, `deposit`, `insurance`, `investments`, `null` |
| `cta` | Текст кнопки в баннере, `null` если баннера нет |
| `deep_link` | URI для перехода из баннера, `null` если не нужен |

---

### `POST /admin/hint` — ручная отправка подсказки пользователю

```json
{
  "user_id": "u1",
  "hint_id": "cashback_active_spender",
  "override_until": "2026-04-15T23:59:00"
}
```

Сохраняет override в памяти сервиса. При следующем запросе `/hint` от этого пользователя — вернёт заданную подсказку, пока не истечёт `override_until`.

---

### `GET /hints/pool` — весь каталог шаблонов (для отладки)

Возвращает полный `pool.json`.

---

## Message Pool — структура

Файл: `ml/mock_api/hints/pool.json`

```json
{
  "products": [
    {
      "id": "cashback",
      "messages": [
        {
          "segment": "active_spender",
          "template": "Возвращай {saving_rub} ₽ в месяц с покупок",
          "formula": {
            "saving_rub": "avg_monthly_spend * 0.05"
          },
          "condition": "has_cashback == false"
        },
        {
          "segment": "default",
          "template": "Подключи кешбэк — это бесплатно",
          "formula": {},
          "condition": "has_cashback == false"
        }
      ]
    },
    {
      "id": "deposit",
      "messages": [
        {
          "segment": "default",
          "template": "{balance} ₽ на накопительном дадут +{income} ₽ в месяц",
          "formula": {
            "income": "balance * 0.18 / 12"
          },
          "condition": "balance > 50000 and has_deposit == false"
        }
      ]
    },
    {
      "id": "insurance",
      "messages": [
        {
          "segment": "default",
          "template": "Страховка переводов — 99 ₽/мес, покрытие до 300 000 ₽",
          "formula": {},
          "condition": "big_transfers_count >= 2 and has_transfer_insurance == false"
        }
      ]
    },
    {
      "id": "investments",
      "messages": [
        {
          "segment": "investor",
          "template": "Портфель вырос на {growth_pct}% за неделю — посмотреть?",
          "formula": {
            "growth_pct": "mock_portfolio_growth"
          },
          "condition": "segment == 'investor'"
        }
      ]
    }
  ]
}
```

---

## Rule Engine — порядок вычисления

```python
def get_hint(ctx: UserContext) -> HintResponse:

    # 1. Проверяем manual override от Admin API
    override = get_admin_override(ctx.user_id)
    if override:
        return render_hint(override, ctx)

    # 2. Напоминание — высший приоритет если есть событие
    if ctx.next_payment_days is not None and ctx.next_payment_days <= 3:
        return build_reminder(ctx)

    # 3. Аналитика — если конец месяца или аномалия
    if is_end_of_month() and ctx.has_transactions:
        return build_analytics(ctx)

    # 4. ВЫГОДА — перебираем продукты по приоритету, берём первый подходящий
    for product in PRODUCT_PRIORITY:
        hint = try_build_vygoda(product, ctx)
        if hint:
            return hint

    # 5. Ничего не подошло — сервер возвращает null,
    #    виджет покажет нейтральный из локального пула
    return None
```

**Порядок продуктов (`PRODUCT_PRIORITY`):**
1. `deposit` — если `balance > 50k` и нет вклада
2. `cashback` — если `active_spender` и нет кешбэка
3. `insurance` — если `big_transfers_count >= 2`
4. `investments` — если сегмент `investor`

---

## Хранение состояния на устройстве

```
SharedPreferences "personalization"
  ├── last_hint_text          — текст последней показанной подсказки
  ├── last_hint_type          — тип (для цвета)
  ├── last_neutral_shown      — последняя нейтральная (чтобы не повторять)
  ├── dismissed_{product_id}  — timestamp закрытия баннера продукта
  └── shown_{product_id}      — timestamp последнего показа ВЫГОДА
```

Частотные ограничения проверяются на устройстве перед запросом к серверу — чтобы не слать лишние запросы.

---

## Файловая структура (добавляемые файлы)

```
ml/mock_api/
  hints/
    pool.json          — Message Pool
    router.py          — /hint, /admin/hint, /hints/pool
    engine.py          — Rule Engine
    renderer.py        — подстановка формул в шаблоны

android/app/src/main/java/com/vtbvita/widget/
  personalization/
    HintRepository.kt       — запрос к серверу + fallback
    NeutralHints.kt         — локальный пул нейтральных фраз
    HintState.kt            — data class Hint(text, type, color, ...)
    PersonalizationPrefs.kt — работа с SharedPreferences
```
