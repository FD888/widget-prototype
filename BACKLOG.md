# BACKLOG — VTB Vita Prototype

**Дедлайн Phase 1:** 5 апреля 2026
**Дедлайн Phase 2:** 12 апреля 2026

Статусы: `[ ]` todo · `[~]` in progress · `[x]` done

---

## Phase 1 — Core Prototype (до 5 апреля)

Цель: работающий прототип с демонстрацией ключевых операций.

| ID | Задача | Статус | Владелец | Ветка |
|----|--------|:------:|----------|-------|
| C-01 | Зафиксировать стек, создать репозиторий, настроить конвенции | [x] | Денис + Яна | — |
| C-02 | NLP-ядро: парсинг intent'ов (transfer / balance / topup) | [x] | Яна | `feature/C-02-nlp-intent-parser` |
| C-03 | Mock API: FastAPI-сервис → данные для модалов | [x] | Денис | `feature/C-03-mock-api` |
| C-04 | Android Widget UI: виджет → ввод → модал → статус | [x] | Денис | `feature/C-04-widget-ui` |
| C-05 | Деплой: прототип доступен по публичному URL | [x] | Денис | `feature/C-05-deploy` |

### Зависимости Phase 1

```
C-01 → C-02 (Яна может стартовать параллельно)
C-01 → C-03 → C-04 → C-05
C-02 + C-03 → C-05
```

---

## Phase 2 — Улучшения (5–12 апреля, только если Phase 1 закрыта)

| ID | Задача | Статус | Владелец |
|----|--------|:------:|----------|
| C-06 | Контекстная строка виджета с mock-событиями | [ ] | Денис |
| C-07 | Edge cases: NLP не распознал / несколько кандидатов | [x] | Яна + Денис |
| C-09 | Замер объёма данных виджета vs полного приложения (для питча) | [ ] | Яна |
| C-10 | Hint system: GET /hint + ReminderBanner + VygodaBanner | [x] | Денис |
| C-11 | Hint dashboard: /dashboard/hints (CRUD overrides) | [x] | Денис |
| C-12 | LLM cascade: DeepSeek → OpenRouter → unknown | [x] | Денис |
| C-13 | pay_scheduled intent + comment в переводах | [x] | Денис |

---

## Выполненные задачи

| ID | Задача | Дата | Ветка |
|----|--------|------|-------|
| C-01 | Репозиторий, .gitignore, CLAUDE.md, docs, /commit. CI пропущен осознанно — нет тестов, дедлайн 5 апреля | 2026-03-30 | — |
| C-03 | Mock API (FastAPI): /parse, /balance, /command, /confirm | 2026-03-31 | `feature/C-04-widget-ui` |
| C-04 | Android Widget UI (все экраны Phase 1 + экстра) | 2026-04-02 | `feature/C-04-widget-ui` |
| U-01 | UI/UX: виджет-капсула, InputActivity, флоу переводов, Mock Bank | 2026-04-02 | `feature/C-04-widget-ui` |
| C-08 | Голосовой ввод: MediaRecorder + waveform + таймер | 2026-04-02 | `feature/C-04-widget-ui` |
| C-04+ | Widget UX: VoiceRecordingService, пульсирующие кольца, VAD, кнопка ↑, анимация InputActivity | 2026-04-05 | `feature/C-04-widget-ui` |
| C-05 | Деплой: Docker + nginx + vtb.vibefounder.ru, GitHub Release APK | 2026-04-10 | `feature/C-05-deploy` |
| C-04++ | BiometricHelper (реальный BiometricPrompt), VTB-шрифты, логотипы банков, VitaComponents, /auth/biometric | 2026-04-10 | `feature/C-04-widget-ui` |
| DI-02 | Sheet host framework + Opus Design Audit: OmegaSheetScaffold, компактные плашки, карточки Откуда/Кому, шаг подтверждения, аватары, операторы, VTB-карты, personalization module, Tone of Voice | 2026-04-24 | `feature/DI-02-sheet-host-framework` |

---

## Идеи / Микрофичи (после питча)

Задачи не в скоупе защиты 19 апреля. Зафиксированы, чтобы не потерять.

| ID | Идея | Суть | Приоритет |
|----|------|------|-----------|
| I-01 | QR-чип в виджете | Виджет остаётся минималистичным: одна кнопка микрофона. QR-сканирование выводится как отдельный чип в ряду с балансом и переводом — не вторая кнопка, а чип. Сканирование QR открывает InputActivity с уже заполненным получателем/суммой. | Средний |

---

## Решения и ADR

| Дата | Решение | Причина |
|------|---------|---------|
| 2026-03-30 | Стек: Kotlin + Jetpack Compose + AppWidget API + FastAPI | Принято на C-01 |
| 2026-03-30 | Android виджет: RemoteViews (не Glance) | Glance конвертируется в RemoteViews под капотом, добавляет баги, меньше документации |
| 2026-03-30 | EditText в виджете невозможен — флоу: тап → InputActivity | Ограничение Android OS, одинаково для RemoteViews и Glance |
| 2026-03-30 | NLP: DeepSeek API для прототипа, self-hosted для продакшена ВТБ | Трансграничная передача данных — в реальном продукте ВТБ разворачивает модель на своей инфраструктуре |
| 2026-03-30 | Mock-данные: JSON/хардкод, без реального API | Критическое ограничение прототипа |
| 2026-03-31 | Widget shape: прозрачный FrameLayout + внутренний LinearLayout 64dp | Лончеры растягивают ячейку — фиксированная высота только у внутреннего слоя |
| 2026-03-31 | InputActivity как прозрачный overlay поверх лончера | Иллюзия «выплывающего» виджета без перехода на новый экран |
| 2026-03-31 | hideWidget() в onCreate / restoreWidget() в onPause | onDestroy ненадёжен — onPause гарантированно вызывается |
| 2026-04-01 | SessionManager (SharedPreferences) → состояние виджета | Виджет переключается между «Войдите» и персонализированным промптом |
| 2026-04-01 | MockBankActivity: скриншоты + кликабельный таббар | Симуляция банковского приложения без реального UI |
| 2026-04-02 | SystemIntentHandler: alarm / timer / app launch / call | Виджет выходит за рамки банкинга → демонстрирует потенциал ассистента |
| 2026-04-02 | Переименование Pulse → Vita | Финальное название продукта |
| 2026-04-03 | Двухуровневая JWT-авторизация: app_token (30д) + banking JWT (15мин) | PIN не хранится на клиенте — только серверная валидация через /auth |
| 2026-04-03 | BankingSession.clear() в onPause → PIN при каждом открытии виджета | Безопасность: виджет не должен оставлять active banking-сессию |
| 2026-04-04 | BankingSession.putInIntent/restoreFromIntent — передача токена в дочерние Activity | onPause очищает BankingSession раньше, чем дочерняя Activity вызывает API |
| 2026-04-04 | MockBankActivity: 5 реальных Compose-экранов вместо JPEG-скриншотов | Демо выглядит живым; Главная загружает реальные счета через /balance |
| 2026-04-04 | ContactMatcher: нечёткий поиск + scoring (0.8/0.3 порог однозначности) | "паше коноплеву" → токены → LIKE-запрос → scoring; контакты не покидают телефон |
| 2026-04-04 | TransferDetailsActivity: слать номер телефона в /command вместо имени | Сервер ищет по _PHONE_INDEX — надёжнее чем по имени с дательным падежом |
| 2026-04-04 | ContactMatcher scoring: Jaccard matched/max(tokens,parts) вместо fixed weights | "мама" (1 слово) → 1.0 vs "мама Саши" (2 слова) → 0.5; gap 0.5 ≥ 0.3 → auto-resolve |
| 2026-04-10 | Android: Timber вместо Log.d (инициализация в MainActivity.onCreate) | Автоочистка логов в release-сборке без ручного --log-level |
| 2026-04-10 | Python: stdlib logging с форматом HH:MM:SS + уровень INFO для ops-событий | Нет доп. зависимостей, нет overhead для продакшена |
| 2026-04-10 | CI: GitHub Actions (test-python + build-android) на push/PR в main | Задача C-01 помечала «CI пропущен осознанно — нет тестов»; теперь тесты есть |
| 2026-04-04 | ContactMemory: boost score на основе истории выборов (count≥3 → +0.5 → auto-resolve) | Позволяет виджету обучаться на предпочтениях пользователя без сервера |
| 2026-04-04 | VoiceStreamingRecorder: WebSocket + PCM 16kHz → Яндекс SpeechKit через прокси-сервер | Потоковый STT вместо batch записи — partial/final результаты в реальном времени |
| 2026-04-10 | BiometricHelper: реальный BiometricPrompt + /auth/biometric endpoint | Демо показывает нативную биометрию устройства |
| 2026-04-11 | Multi-user: data.py — единственный источник данных; PIN→user_id в JWT; 3 персоны из N=97 (Витя/Ольга/Артём) | Архитектурно правильная мультиюзерность = сильный аргумент на защите |
| 2026-04-10 | mock_api: Dockerfile + docker-compose на VDS, сервис доступен на vtb.vibefounder.ru | Изолирован от ТГДОМ, nginx-прокси |
| 2026-04-10 | STT переведён на gRPC Streaming (Яндекс SpeechKit v2): _stt_stream_grpc + yandex_speech/ | REST давал 4-6x переплату; gRPC — один стрим = один биллинг; partial слово-за-словом |
| 2026-04-21 | Mock API: data.py (in-memory) → SQLite + aiosqlite; schema.sql, db.py, seed.py; GET /transactions | Баланс персистентен, история транзакций растёт в ходе демо |
| 2026-04-22 | Analytics Dashboard: vtb.vibefounder.ru/dashboard (Basic Auth vita/vtb2026); intent_log; KPI, воронка, интенты, Chart.js | Продуктовые метрики прямо из SQLite — монетизационные прокси M1+M2A |
| 2026-04-23 | Hint system: GET /hint (reminder/vygoda/none); db.get_upcoming_reminder, db.get_vygoda_offer, db.get_available_offers | Персонализация виджета на основе реальных данных из БД |
| 2026-04-23 | Hint Dashboard: /dashboard/hints (CRUD hint_overrides, Basic Auth) | Продуктовый менеджер может форсировать подсказку для демо |
| 2026-04-23 | pay_scheduled intent: оплата плановых платежей (credit_card/loan/autopayment) через /command | Новый банковский intent помимо transfer/balance/topup |
| 2026-04-23 | comment в переводах: regex + LLM парсят «за пиццу» → comment field | ConfirmationResponse.comment для отображения в модале |
| 2026-04-23 | bot_redirect: неизвестные интенты → bot_redirect=true + original_text | Ключ для интеграции с чат-ботом банка |
| 2026-04-23 | LLM cascade: DeepSeek (прямой) → OpenRouter (deepseek/deepseek-chat) → unknown | Уменьшение latency и повышение надёжности NLP |
| 2026-04-24 | OmegaSheetScaffold — единый bottom-sheet контейнер для всех виджет-плашек | Устранение дублирования overlay-кода; scrim + handle + TopBar + content/footer slots |
| 2026-04-24 | Opus Design Audit → реализация S-1..S-10: компактные плашки vs fullscreen, карточки «Откуда/Кому», аватары, операторы, платёжные системы | Визуальное соответствие ВТБ Онлайн; виджет ≠ приложение — два визуальных языка |
| 2026-04-24 | Personalization module (HintRepository + NeutralHints + WidgetHintTexts) | Offline-first нейтральные фразы + серверные hint; чувствительность данных (credit_card/loan суммы скрыты) |
| 2026-04-24 | Tone of Voice утверждён: друг-финансист, «ты», строчные, без эмодзи | TONE_OF_VOICE.md — единый стиль всех текстов виджета |
