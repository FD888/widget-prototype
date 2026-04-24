# VTB Vita — Прототип

Голосовой/текстовый виджет на главном экране Android для мгновенных банковских операций.
Одна фраза → перевод / баланс / пополнение телефона — без открытия приложения.

**Дедлайн:** 5 апреля 2026 (публичный доступ) → 19 апреля 2026 (защита)

---

## Быстрый старт

### 1. Mock API (нужен при первом запуске и после перезагрузки)

```bash
cd ml/mock_api
source venv/bin/activate          # или: python -m venv venv && pip install fastapi uvicorn
uvicorn main:app --host 127.0.0.1 --port 8000
```

### 2. USB-тоннель (после каждого подключения телефона)

```bash
adb reverse tcp:8000 tcp:8000
```

### 3. Сборка и установка Android-приложения

```bash
cd android
./gradlew installDebug
# APK: android/app/build/outputs/apk/debug/app-debug.apk
```

> Без шагов 1–2 запросы к Mock API будут падать с ошибкой сети.

---

## Демо-сценарии

| Сценарий | Действие |
|----------|---------|
| Перевод | Нажать на виджет → чип «Перевод» → выбрать контакт → сумма → «Отправить» |
| Перевод с комментарием | Ввести «Переведи Кате 500 за пиццу» → модал с комментарием |
| Баланс | Нажать на виджет → чип «Баланс» |
| Пополнение | Нажать на виджет → чип «Пополнить» → номер → «Пополнить» |
| Оплата планового платежа | Подсказка-напоминание → «Оплатить» → подтвердить |
| Подсказка ВЫГОДА | Подсказка с оффером → «Подробнее» |
| Голос | Нажать на микрофон в виджете → режим записи |
| Будильник | Нажать на виджет → ввести «Разбуди в 7 утра» |
| Открыть приложение | «Открой Telegram» / «Открой ВТБ» |
| Dashboard аналитики | vtb.vibefounder.ru/dashboard (vita/vtb2026) |
| Dashboard подсказок | vtb.vibefounder.ru/dashboard/hints (vita/vtb2026) |

---

## Документация

| Документ | Что внутри |
|----------|-----------|
| [BACKLOG.md](BACKLOG.md) | Все задачи, статусы, ADR |
| [docs/PRODUCT.md](docs/PRODUCT.md) | Продуктовая логика, флоу, операции |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Архитектура, компоненты, API-контракт |
| [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) | Функциональные требования, критерии приёмки |
| [docs/DEPLOY.md](docs/DEPLOY.md) | План деплоя на VDS: Docker, JWT, безопасность |
| [CLAUDE.md](CLAUDE.md) | Контекст для AI-ассистента |

---

## Команда разработки

| Человек | Зона |
|---------|------|
| Денис | Android Widget, интеграция, Mock API, деплой |
| Яна | NLP/ML сервис (Python, C-02) |

---

## Стек

- **Android:** Kotlin + Jetpack Compose + AppWidget API (minSdk 26, targetSdk 34)
- **Mock API:** Python + FastAPI (`ml/mock_api/`), SQLite + aiosqlite
- **Деплой:** Docker на VDS + nginx + vtb.vibefounder.ru
