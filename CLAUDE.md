# VTB Vita — Прототип виджета

## Что здесь строится

**VTB Vita** — голосовой/текстовый виджет на главном экране Android.
Одна фраза → перевод / баланс / пополнение телефона / оплата планового платежа — без открытия приложения.

Этот репозиторий — **standalone Android-прототип** для демонстрации на защите 19 апреля 2026.
Это НЕ интеграция с реальным ВТБ. Все банковские данные — mock. NLP — локальный сервис.

Полная продуктовая логика: `docs/PRODUCT.md`
Архитектура (C4): `docs/ARCHITECTURE.md`
Требования к прототипу: `docs/REQUIREMENTS.md`
Задачи разработки: `BACKLOG.md`

**Skills (`.claude/skills/`):**
- `commit` — коммит с обновлением документации (`/commit`)
- `plan` — планирование реализации перед нетривиальными задачами (`/plan`)
- `deploy` — деплой сервера (tgdom-server) и установка APK через adb
- `figma` — работа с дизайн-системой Omega (ВТБ) через Framelink MCP (цвета, токены, компоненты)

**Дизайн-система Omega (Figma):**
- Навигация по файлам: `docs/FIGMA.md`
- Доступ через Framelink MCP (`opencode.json`), токен в `.env` (`FIGMA_API_KEY`)
- 9 проектов: palette, tokens, guidelines, base-components, atoms, meta, templates, illustrations, cookbook
- При разработке UI — всегда сверяйся с Figma, не угадывай значения

---

## Стек

| Компонент | Технология | Владелец |
|-----------|-----------|---------|
| Android Widget + UI | Kotlin + AppWidget API (RemoteViews) | Денис |
| NLP / Intent Parser | Python (FastAPI) + LLM cascade (DeepSeek → OpenRouter) | Яна + Денис |
| Mock Backend | Python (FastAPI) + SQLite (aiosqlite) | Денис |

> Стек фиксируется в задаче **C-01**. До её закрытия таблица выше — предложение, не факт.
> После фиксации — обновить этот файл.

---

## Структура репозитория

```
android/     — Android-приложение (Kotlin + Compose + AppWidget)
ml/          — NLP/ML компонент (Python, Яна)
docs/        — продукт, архитектура, требования
BACKLOG.md   — все задачи + статусы
README.md    — обзор для людей
```

---

## Git-конвенции

- **Ветки:** `feature/<ID>-краткое-описание`
  Примеры: `feature/C-02-nlp-intent-parser`, `feature/C-04-widget-ui`
- **ID задачи** — из BACKLOG.md (C-01..C-05, U-01, C-06..)
- **Один PR = одна задача** из беклога
- **Прямой пуш в main запрещён** — только через PR
- **Коммит-сообщения:** `[C-02] Add intent classification for transfer/balance/topup`

---

## Команда

| Человек | Зона | Ветки |
|---------|------|-------|
| **Денис** | Android UI, интеграция, координация | feature/C-03, C-04, C-05, U-01 |
| **Яна** | ML/NLP компонент (Python) | feature/C-02, C-08 |

---

## Критические ограничения (никогда не нарушать)

1. **Никаких реальных банковских API** — только mock-данные
2. **Никаких реальных персональных данных** в коде, тестах, конфигах
3. **Никаких секретов в коде** — API-ключи, токены только через env/local.properties

---

## Дедлайны

| Дата | Цель |
|------|------|
| 5 апреля 2026 | Прототип в публичном доступе (задача C-05) |
| 12 апреля 2026 | Все улучшения Phase 2, если успеваем |
| 19 апреля 2026 | Финальная защита |

---

## Правила работы с задачами

**Перед выполнением любой нетривиальной задачи** (новый компонент, интеграция, рефактор, план реализации) — обязательно задай уточняющие вопросы через инструмент `AskUserQuestion`, не текстом.

Минимум 2 раунда:
1. **Первый раунд (4 вопроса):** масштаб задачи, ожидаемый результат, ограничения, приоритет
2. **Второй раунд (2–3 вопроса):** детали реализации, специфичные для конкретной задачи

Не приступай к реализации, пока не получены ответы. Цель — понять желаемый результат точнее, чем он сформулирован в запросе.

Исключения (вопросы не нужны):
- Правки в документации
- Исправление явной опечатки / бага с понятным фиксом
- Явный `/commit` без новой логики

---

## Как ориентироваться в коде

- Точка входа Android-приложения: `android/app/src/main/`
- Виджет-провайдер: ищи `VitaWidgetProvider` (AppWidgetProvider)
- NLP-сервис: `ml/` — FastAPI-приложение, endpoints: `POST /parse`, `GET /hint`, `POST /command`, `POST /confirm`
- Mock API с SQLite: `ml/mock_api/db.py` (9 таблиц, aiosqlite), `ml/mock_api/schema.sql` (DDL)
- Dashboard аналитики: `ml/mock_api/dashboard.html` + `GET /dashboard/stats`
- Dashboard подсказок: `ml/mock_api/hints.html` + `/dashboard/hints/api`
- Hint логика: `db.get_upcoming_reminder()`, `db.get_vygoda_offer()`, `db.get_available_offers()`
- LLM cascade: `_LLM_PROVIDERS` в `main.py` — DeepSeek прямой → OpenRouter → unknown
- Mock-данные: сидируются через `seed.py` в SQLite (`vita.db`)
- Personalization module: `personalization/HintRepository.kt` (серверный hint + offline fallback), `NeutralHints.kt` (пул фраз), `WidgetHintTexts.kt` (форматирование)
- OmegaSheetScaffold: единый bottom-sheet контейнер — все виджет-плашки используют его (scrim + handle + TopBar + content + footer)
- MockBankRepository: локальные mock-данные для UI — `AvatarPalette`, `detectOperator`, `MOCK_ACCOUNTS/OPERATORS`
- Tone of Voice: `docs/TONE_OF_VOICE.md` — утверждённая тональность (друг-финансист, «ты», строчные, без эмодзи)
- Design Audit: `docs/DESIGN_IMPROVEMENTS.md` — план правок UI (S-1..S-10)
