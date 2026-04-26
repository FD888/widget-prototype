# Архитектура VTB Vita — Прототип

> **Принцип документа:**
> - **Текущее состояние** — только то, что реально есть в репозитории прямо сейчас.
> - **Целевой дизайн** — спецификация и план. При расхождении — обновить «Текущее состояние».

---

## Текущее состояние

*Последнее обновление: 2026-04-26 (DI-03: fix VoiceRecordingService ring animation for Android 10)*

### Структура репозитория

```
widget-prototype/
├── android/
│   └── app/src/main/
│       ├── java/com/vtbvita/widget/
│       │   ├── VitaWidgetProvider.kt       ← AppWidgetProvider
│       │   ├── InputActivity.kt            ← прозрачный overlay ввода
│       │   ├── BalanceActivity.kt          ← модал баланса
│       │   ├── ConfirmActivity.kt          ← модал подтверждения
│       │   ├── ContactPickerActivity.kt    ← выбор контакта из телефонной книги
│       │   ├── TransferDetailsActivity.kt  ← шаг 2 перевода (сумма, банк, счёт)
│       │   ├── TopupInputActivity.kt       ← пополнение телефона
│       │   ├── SystemIntentHandler.kt      ← будильник / таймер / звонок / запуск приложения
│       │   ├── SessionManager.kt           ← EncryptedSharedPreferences-сессия (только из Activity)
│       │   ├── WidgetState.kt              ← plain SharedPreferences: зеркало login-статуса для виджета
│       │   ├── MainActivity.kt             ← выбор профиля (PERSONAS)
│       │   ├── PinEntryActivity.kt         ← PIN-экран (4 цифры)
│       │   ├── MockBankActivity.kt         ← mock банковское приложение
│       │   ├── BankingSession.kt           ← in-memory banking JWT (15 мин)
│       │   ├── PhoneVerificationActivity.kt← верификация номера телефона
│       │   ├── TransferFlowActivity.kt         ← единый модал NLP-флоу: disambiguation + подтверждение
│       │   ├── ContactDisambiguationActivity.kt ← legacy (заменён TransferFlowActivity для NLP-пути)
│       │   ├── api/MockApiService.kt       ← HTTP-клиент к FastAPI
│       │   ├── VoiceStreamingRecorder.kt   ← WebSocket PCM-стриминг на сервер (16kHz/16-bit)
│       │   ├── VoiceRecordingService.kt    ← Foreground Service: запись голоса + анимация колец в виджете
│       │   ├── nlp/NlpService.kt           ← HTTP-клиент к /parse
│       │   ├── nlp/ContactMatcher.kt       ← нечёткий поиск по ContactsContract (склонения)
│       │   ├── nlp/ContactMemory.kt        ← SharedPreferences: история выборов → boost score
│       │   ├── BiometricHelper.kt          ← реальный BiometricPrompt (только BIOMETRIC_STRONG)
│       │   ├── model/Models.kt             ← data-классы
│       │   ├── model/MockBankRepository.kt ← mock-данные: счета, операторы, AvatarPalette
│       │   ├── personalization/
│       │   │   ├── HintRepository.kt       ← серверный hint + offline fallback + SharedPreferences
│       │   │   ├── NeutralHints.kt         ← пул нейтральных фраз по времени суток и праздникам
│       │   │   └── WidgetHintTexts.kt      ← форматирование reminder/vygoda текстов для виджета
│       │   ├── ui/theme/
│       │   │   ├── OmegaColor.kt          ← палитра Omega (16 шкал + семантические токены)
│       │   │   ├── OmegaType.kt           ← типографика Omega (tight/paragraph, HeadlineXS/XXS)
│       │   │   ├── OmegaDimensions.kt     ← spacing, radius, elevation, stroke, size
│       │   │   └── Theme.kt               ← VTBVitaTheme (OMEgaDarkColorScheme)
│       │   ├── ui/components/
│       │   │   ├── OmegaComponents.kt      ← Omega UI компоненты (Button, Avatar, TopBar, SearchField, CompactInfoCard, AmountInputCard, AccountPickerSheet, TransferSummary, SbpRow, VtbCardWithBadge, SuccessScreen)
│       │   │   ├── OmegaSheetScaffold.kt   ← единый bottom-sheet контейнер: scrim + handle + TopBar + content + footer
│       │   │   └── TransferDetailsContent.kt ← shared Composable для формы перевода (sheet-based)
│       │   └── ui/effects/
│       │       └── AuroraEffect.kt        ← AGSL-шейдер (не Omega)
│       ├── res/
│       │   ├── layout/widget_vita.xml      ← RemoteViews-макет виджета
│       │   ├── drawable/widget_bg.xml      ← синий градиент, cornerRadius 32dp
│       │   ├── drawable/ic_*.xml           ← 55 Omega-иконок из Figma (навигация, действия, шаблоны)
│       │   ├── drawable/bank_*.png         ← логотипы банков (vtb, sber, tbank, alfa, raiffeisen)
│       │   ├── drawable/merch_*.png        ← логотипы мерчантов/банков для carousel (10)
│       │   ├── drawable/operator_*.xml     ← логотипы операторов связи (МТС, МегаФон, Билайн, Tele2, Yota)
│       │   ├── drawable/ps_*.xml/png       ← логотипы платёжных систем (МИР, Visa, Mastercard)
│       │   ├── drawable/vtb_card_*.png     ← изображения VTB-карт (current, savings, credit)
│       │   ├── drawable/payment_sbp.xml    ← лого СБП
│       │   ├── font/vtb_*.ttf              ← VTB-шрифты (bold, book, demi_bold, light)
│       │   └── xml/vita_widget_info.xml    ← метаданные AppWidget
│       └── AndroidManifest.xml
├── ml/
│   └── mock_api/
│       ├── main.py           ← FastAPI: /verify-phone, /auth, /auth/biometric, /parse, /hint, /balance, /command, /confirm, /transactions, /dashboard, /dashboard/hints, /ws/stt
│       ├── data.py           ← статик-конфиг (USERS для seed, RECOMMENDATIONS, BANNERS, MCC)
│       ├── db.py             ← async database layer (aiosqlite): init_db, seed_db, hint logic, vygoda offers, reminders, все SQL-запросы
│       ├── schema.sql        ← DDL: users, accounts, contacts, transactions, scheduled_payments, pending_transactions, intent_log, hint_overrides
│       ├── seed.py           ← standalone-сидер: python seed.py [--reset]
│       ├── dashboard.html    ← аналитический дашборд (Chart.js, dark theme, Basic Auth)
│       ├── hints.html        ← управление подсказками (Basic Auth, CRUD hint_overrides)
│       ├── vita.db           ← SQLite-файл (в Docker монтируется в /data/)
│       ├── test_regex_parse.py ← pytest: 64 unit-теста L1-парсера
│       ├── test_api.py       ← pytest: 19 integration-тестов FastAPI (TestClient)
│       ├── Dockerfile        ← python:3.11-slim, uvicorn
│       ├── docker-compose.yml← порт 127.0.0.1:8001→8000, volume ./data:/data, env_file
│       ├── requirements.txt  ← fastapi, uvicorn, httpx, jose, grpcio, protobuf, pytest, aiosqlite
│       ├── .env.example      ← шаблон переменных окружения
│       ├── gen_proto.sh      ← одноразовая регенерация gRPC-стабов
│       ├── yandex_speech/    ← сгенерированные proto-стабы (stt_pb2, stt_pb2_grpc)
│       └── venv/
├── docs/
│   ├── ARCHITECTURE.md
│   ├── PRODUCT.md
│   ├── REQUIREMENTS.md
│   ├── DEPLOY.md             ← инструкция деплоя на VDS, nginx, Docker
│   ├── DESIGN_IMPROVEMENTS.md← план правок UI по результатам Opus Design Audit
│   ├── PERSONALIZATION.md    ← продуктовая логика подсказок
│   ├── PERSONALIZATION_ARCH.md← тех. архитектура персонализации
│   ├── TONE_OF_VOICE.md      ← тональность виджета (утверждён)
│   ├── FIGMA.md              ← навигация по файлам дизайн-системы Omega
│   └── OPUS_DESIGN_AUDIT_BRIEF.md ← бриф для Opus дизайн-аудита
├── .github/workflows/ci.yml  ← GitHub Actions: Python pytest + Android assembleDebug + unit tests
├── .claude/skills/           ← model-invoked skills (commit, deploy, plan)
├── BACKLOG.md
├── CLAUDE.md
└── README.md
```

### Реализованные компоненты

| Компонент | Файл | Статус |
|-----------|------|--------|
| AppWidget (RemoteViews) | `VitaWidgetProvider.kt` | ✅ |
| Прозрачный overlay ввода | `InputActivity.kt` | ✅ |
| Голосовой ввод (MediaRecorder + waveform) | `InputActivity.kt` | ✅ |
| Системные интенты (alarm/timer/call/app) | `SystemIntentHandler.kt` | ✅ |
| Выбор контакта (ContactsContract) | `ContactPickerActivity.kt` | ✅ |
| Нечёткий поиск контакта (склонения, scoring) | `nlp/ContactMatcher.kt` | ✅ |
| Единый NLP-флоу перевода (disambiguation + подтверждение) | `TransferFlowActivity.kt` | ✅ |
| Обучение на выборах пользователя (boost score + авторезолв) | `nlp/ContactMemory.kt` | ✅ |
| WebSocket PCM-стриминг (голос → сервер → SpeechKit) | `VoiceStreamingRecorder.kt` | ✅ |
| Голосовой ввод нативно в виджете (Foreground Service) | `VoiceRecordingService.kt` | ✅ |
| Пульсирующие кольца в виджете (~12fps, partiallyUpdateAppWidget) | `VoiceRecordingService.kt` | ✅ |
| VAD: авто-сабмит по тишине 1.5 сек | `VoiceRecordingService.kt` | ✅ |
| Перевод (2 шага) | `TransferDetailsActivity.kt` | ✅ |
| Пополнение телефона | `TopupInputActivity.kt` | ✅ |
| Баланс | `BalanceActivity.kt` | ✅ |
| Верификация телефона | `PhoneVerificationActivity.kt` | ✅ |
| In-memory banking JWT | `BankingSession.kt` | ✅ |
| Inline PIN overlay (виджет) | `InputActivity.kt` | ✅ |
| Биометрическая аутентификация (BiometricPrompt) | `BiometricHelper.kt` | ✅ |
| OmegaSheetScaffold — единый bottom-sheet контейнер | `ui/components/OmegaSheetScaffold.kt` | ✅ |
| OmegaAvatar — аватар с инициалами на пастельном фоне | `ui/components/OmegaComponents.kt` | ✅ |
| OmegaTopBar — стандартный топ-бар (chevron + title + info/close) | `ui/components/OmegaComponents.kt` | ✅ |
| OmegaSearchField — поле поиска | `ui/components/OmegaComponents.kt` | ✅ |
| OmegaCompactInfoCard — компактная карточка для плашек | `ui/components/OmegaComponents.kt` | ✅ |
| AmountInputCard — карточка ввода суммы | `ui/components/OmegaComponents.kt` | ✅ |
| AccountPickerSheet — выбор счёта списания | `ui/components/OmegaComponents.kt` | ✅ |
| TransferSummary — строки деталей перевода | `ui/components/OmegaComponents.kt` | ✅ |
| SbpRow — способ перевода через СБП | `ui/components/OmegaComponents.kt` | ✅ |
| VtbCardWithBadge — VTB-карта + значок платёжной системы | `ui/components/OmegaComponents.kt` | ✅ |
| OmegaSelectableChip — универсальный чип выбора | `ui/components/OmegaComponents.kt` | ✅ |
| MockBankRepository — счета, операторы, AvatarPalette | `model/MockBankRepository.kt` | ✅ |
| HintRepository — серверный hint + offline fallback | `personalization/HintRepository.kt` | ✅ |
| NeutralHints — пул нейтральных фраз по времени/праздникам | `personalization/NeutralHints.kt` | ✅ |
| WidgetHintTexts — форматирование reminder/vygoda | `personalization/WidgetHintTexts.kt` | ✅ |
| 55 Omega-иконок из Figma (SVG → VectorDrawable) | `res/drawable/ic_*.xml` | ✅ |
| Логотипы мерчантов/банков (10) | `res/drawable/merch_*.png` | ✅ |
| Логотипы операторов связи (5) | `res/drawable/operator_*.xml` | ✅ |
| Логотипы платёжных систем (МИР, Visa, Mastercard) | `res/drawable/ps_*.xml/png` | ✅ |
| VTB-карты (current, savings, credit) | `res/drawable/vtb_card_*.png` | ✅ |
| Shared UI компоненты (Omega Design System) | `ui/components/OmegaComponents.kt` | ✅ |
| Omega типографика (tight/paragraph, HeadlineXS/XXS) | `ui/theme/OmegaType.kt` | ✅ |
| Omega палитра (16 шкал + семантика) | `ui/theme/OmegaColor.kt` | ✅ |
| Omega dimensions (spacing, radius, elevation) | `ui/theme/OmegaDimensions.kt` | ✅ |
| Сессия (login/logout) | `SessionManager.kt` | ✅ |
| Выбор профиля | `MainActivity.kt` | ✅ |
| PIN-вход | `PinEntryActivity.kt` | ✅ |
| Mock банковское приложение (5 Compose-экранов) | `MockBankActivity.kt` | ✅ |
| Mock API (FastAPI) | `ml/mock_api/main.py` | ✅ |
| SQLite БД (9 таблиц, aiosqlite) | `ml/mock_api/db.py` + `schema.sql` | ✅ |
| GET /transactions — история операций из БД | `ml/mock_api/main.py` | ✅ |
| GET /hint — персонализированные подсказки (reminder/vygoda/none) | `ml/mock_api/main.py` | ✅ |
| pay_scheduled intent — оплата плановых платежей | `ml/mock_api/main.py` | ✅ |
| Comment в переводах — парсинг «за пиццу» → comment field | `ml/mock_api/main.py` | ✅ |
| bot_redirect — маркировка неизвестных интентов для чат-бота | `ml/mock_api/main.py` | ✅ |
| LLM cascade — DeepSeek (прямой) → OpenRouter → unknown | `ml/mock_api/main.py` | ✅ |
| Analytics Dashboard (/dashboard, Basic Auth) | `ml/mock_api/dashboard.html` + `main.py` | ✅ |
| Hint Dashboard (/dashboard/hints, CRUD hint_overrides) | `ml/mock_api/hints.html` + `main.py` | ✅ |
| intent_log — логирование каждого /parse | `ml/mock_api/db.py` + `schema.sql` | ✅ |
| hint_overrides — ручное управление подсказками | `ml/mock_api/db.py` + `schema.sql` | ✅ |
| STT gRPC Streaming (Яндекс SpeechKit v2) | `ml/mock_api/main.py` + `yandex_speech/` | ✅ |
| NLP intent parsing | `ml/` (C-02) | 🔄 in progress (Яна) |

### Зафиксированные технические решения

| Дата | Решение | Причина |
|------|---------|---------|
| 2026-03-30 | Android виджет: RemoteViews (не Glance) | Glance конвертируется в RemoteViews под капотом, добавляет баги, меньше документации |
| 2026-03-30 | EditText в виджете невозможен — флоу: тап → InputActivity | Ограничение Android OS, одинаково для RemoteViews и Glance |
| 2026-03-30 | NLP: DeepSeek API для прототипа, self-hosted для продакшена ВТБ | Трансграничная передача данных — в реальном продукте ВТБ разворачивает модель на своей инфраструктуре |
| 2026-03-30 | Mock-данные: JSON/хардкод, без реального API | Критическое ограничение прототипа |
| 2026-03-31 | Pill-форма: прозрачный FrameLayout + внутренний LinearLayout 64dp | Лончеры растягивают ячейку — фиксированная высота только у внутреннего слоя |
| 2026-03-31 | InputActivity: прозрачная тема, overlay на 70% экрана | Иллюзия «выплывания» без смены экрана |
| 2026-03-31 | hideWidget в onCreate, restoreWidget в onPause | onDestroy ненадёжен |
| 2026-04-01 | SessionManager (SharedPreferences) | Персонализация виджета + контроль доступа |
| 2026-04-02 | adb reverse tcp:8000 tcp:8000 для USB-тоннеля | Нужен только при локальном запуске API; с 2026-04-03 API задеплоен на vtb.vibefounder.ru — тоннель не нужен |
| 2026-04-03 | Двухуровневая JWT-авторизация: app_token (30д) + banking_token (15мин) | PIN хранится только на сервере; banking_token живёт только в памяти (BankingSession) |
| 2026-04-03 | BankingSession.clear() в InputActivity.onPause() | Каждый выход из виджета требует повторного ввода PIN |
| 2026-04-03 | MainActivity роутинг: если persona сохранена → PinEntryActivity напрямую | Не нужно заново выбирать профиль после перезапуска приложения |
| 2026-04-04 | BankingSession.putInIntent() / restoreFromIntent() — токен передаётся в дочерние Activity через Intent | onPause() очищает BankingSession до того, как дочерняя Activity делает API-запрос; Intent гарантирует передачу |
| 2026-04-04 | MockBankActivity: реальные Compose-экраны вместо JPEG-скриншотов | Демо выглядит как живое приложение; добавлена зависимость material-icons-extended |
| 2026-04-05 | Голосовой ввод перенесён из InputActivity в VoiceRecordingService (Foreground Service) | InputActivity используется только для текста и PIN; виджет сам управляет состояниями IDLE/PREPARING/RECORDING |
| 2026-04-05 | Анимация колец в виджете через partiallyUpdateAppWidget ~12fps + setFloat (setScaleX/Y/Alpha) | RemoteViews не поддерживает нативную анимацию; частичное обновление через IPC минимально нагружает систему |
| 2026-04-05 | VAD: клиентский детектор тишины (amplitude < 0.20 на 1.5 сек) → auto-submit | После ≥0.6 сек речи; защита от двойного submit через флаг `submitted` |
| 2026-04-05 | "DONE"-сигнал протокол WebSocket: клиент шлёт текст "DONE" вместо закрытия соединения | Закрытие WS до ответа сервера → onFinal никогда не приходит; сервер выходит из цикла по сигналу и шлёт final по открытому соединению |
| 2026-04-10 | BiometricHelper: реальный BiometricPrompt (не заглушка) + /auth/biometric на сервере | Демо включает настоящую биометрию устройства; сервер выдаёт banking_token без PIN |
| 2026-04-10 | VTB-шрифты (vtb_bold/book/demi_bold/light.ttf) подключены как font resources | Визуальная идентичность ВТБ в Compose-экранах |
| 2026-04-10 | mock_api докеризирован: Dockerfile + docker-compose.yml, порт 127.0.0.1:8001 | Изоляция от ТГДОМ на одном VDS; nginx проксирует vita-api.vibefounder.ru → :8001 |
| 2026-04-10 | STT: REST-polling заменён на gRPC Streaming (Яндекс SpeechKit v2) | REST отправлял накопленный буфер каждую ~1 сек → 4-6x переплата; gRPC — один стрим на сессию, биллинг = реальная длина аудио; partial-результаты слово-за-словом |
| 2026-04-12 | TransferFlowActivity: единый модал NLP-флоу вместо ContactDisambiguationActivity + TransferDetailsActivity | Compose-state навигация внутри одной Activity; back из Confirmation → ContactSelection с inline-поиском; ContactPickerActivity-путь сохранён через TransferDetailsActivity |
| 2026-04-12 | ContactMatcher: filterCandidates (gap≤0.4 от лидера, cap=5) + нормализация телефонов (8/+7) + memory авторезолв при pickCount≥3 | Устранены дубли контактов с разным форматом номера; список в disambiguation сужен до релевантных; ★-контакт обходит disambiguation |
| 2026-04-11 | Multi-user архитектура: PIN → user_id → banking JWT содержит user_id | 3 персоны из исследования N=97: vitya(1111)/olga(2222)/artyom(3333); каждый endpoint роутится к USERS[user_id]; баланс уменьшается динамически при подтверждении |
| 2026-04-26 | WidgetState (plain SharedPrefs) как зеркало login-статуса для виджета | EncryptedSharedPreferences бросает исключение из BroadcastReceiver-контекста на Android 10; виджет читает только plain prefs |
| 2026-04-26 | setFloat("setAlpha") в RemoteViews заменён на setTextColor с alpha-каналом | Android 10 запрещает вызов setAlpha(float) через рефлексию для TextView в RemoteViews; вызов роняет виджет с ActionException |
| 2026-04-26 | Анимация колец в VoiceRecordingService: setFloat(setScaleX/Y/Alpha) → setViewVisibility + setInt("setImageAlpha") | setScaleX/setScaleY на ImageView в RemoteViews запрещены на Android 10 (ActionException); кольца анимируются visibility + alpha вместо scale |
| 2026-04-26 | Иконки виджета: ic_cross_widget.xml, ic_arrow_widget.xml (белые копии) вместо android:tint | android:tint в RemoteViews layout не поддерживается до API 31; белый цвет задаётся напрямую в векторе |
| 2026-04-11 | data.py — единый источник данных (single source of truth) | main.py импортирует USERS + PHONE_INDEX из data.py; inline MOCK_ACCOUNTS/MOCK_CONTACTS удалены; PHONE_INDEX глобальный по всем пользователям |
| 2026-04-11 | /auth/biometric принимает user_id в теле запроса | Android знает выбранную персону → передаёт persona.id → сервер кодирует в JWT |
| 2026-04-12 | Security hardening: usesCleartextTraffic=false + network_security_config.xml + allowBackup=false | Запрет HTTP, запрет резервного копирования данных приложения |
| 2026-04-12 | SessionManager → EncryptedSharedPreferences (AES256-GCM) | app_token и persona_id хранятся зашифрованными; зависимость androidx.security:security-crypto |
| 2026-04-12 | BiometricHelper: только BIOMETRIC_STRONG (убран BIOMETRIC_WEAK) | WEAK не даёт криптографических гарантий; для банковского демо только STRONG |
| 2026-04-12 | WebSocket: убран fallback ws:// (только wss://) | Запрет plaintext-транспорта согласован с network_security_config |
| 2026-04-12 | Rate limiting на /verify-phone (10/мин), /auth (20/мин), /auth/biometric (20/мин) — in-memory sliding window | Без внешних зависимостей; лимиты щедрые для демо-режима |
| 2026-04-12 | CORS ограничен до CORS_ORIGINS env (дефолт: vtb.vibefounder.ru); методы: GET/POST | Было allow_origins=["*"] |
| 2026-04-12 | APP_API_KEY и JWT_SECRET: убраны fallback-значения, fail-fast при старте если env не задан | Hardcoded секреты не должны попадать в репо |
| 2026-04-12 | NLP: добавлен «заплати/заплатить» в _TRANSFER_VERBS | Частое разговорное слово; покрыто регрессионными тестами |
| 2026-04-18 | Миграция UI на дизайн-систему Omega (ВТБ): OmegaColor, OmegaType, OmegaDimensions, OmegaComponents | Замена хардкод-цветов/VtbBlue/AccentGreen на семантические токены; tight/paragraph типографика; Material Icons → Omega-иконки из Figma; удалены Color.kt, Type.kt, VitaComponents.kt |
| 2026-04-18 | Tight vs Paragraph body-стили: BodyTight* для меток/чипов, BodyParagraph* для многострочного текста | Figma `03-omg-typography` разделяет body на tight/paragraph; Material3 bodyLarge/Medium/Small маппятся на Paragraph |
| 2026-04-18 | Omega Medium (500) маппится на SemiBold (600) — отсутствующий шрифт weight | VTB Group UI не содержит weight 500; созданы отдельные алиасы BodyTightMedium*/BodyParagraphMedium* для будущей миграции |
| 2026-04-21 | Mock API мигрирован с in-memory dict на SQLite + aiosqlite: 9 таблиц, persistent balance, история транзакций, hint_overrides, intent_log | data.py был единственным источником данных в памяти — баланс сбрасывался при рестарте; теперь БД переживает рестарт контейнера |
| 2026-04-21 | pending_transactions таблица вместо `_pending: dict` | Незавершённые операции выживают при рестарте; автоочистка просроченных при старте (expires_at < now) |
| 2026-04-21 | /confirm записывает новую строку в transactions | История растёт в ходе демо: seed-транзакции + живые переводы видны через GET /transactions |
| 2026-04-21 | docker-compose: volume ./data:/data + DB_PATH=/data/vita.db | vita.db персистентен между пересборками контейнера |
| 2026-04-22 | Analytics Dashboard: GET /dashboard (HTML, Basic Auth) + GET /dashboard/stats (JSON) | Один HTML-файл, Chart.js из CDN, без сборщика; 6 блоков: KPI, воронка, интенты, динамика по дням, активность пользователей, таблица транзакций |
| 2026-04-22 | intent_log таблица: логирование каждого /parse → NLP-распределение на дашборде | Дашборд показывает реальную долю transfer/balance/topup/unknown запросов; воронка = intent_log vs transactions |
| 2026-04-22 | DASHBOARD_USER/DASHBOARD_PASS в docker-compose.yml (env defaults vita/vtb2026) | HTTP Basic Auth через FastAPI HTTPBasicCredentials + secrets.compare_digest |
| 2026-04-23 | GET /hint — персонализированные подсказки: reminder (просрочка/ближайший платёж) → vygoda (персональный оффер) → none | Приоритет: override (dashboard) → reminder → vygoda → none; offline fallback на клиенте |
| 2026-04-23 | pay_scheduled intent — оплата планового платежа через /command | Новый intent в CommandRequest: payment_id из /hint, ссылка на scheduled_payments |
| 2026-04-23 | Comment в переводах: NLP парсит «переведи Кате 500 за пиццу» → comment="пиццу" | Regex + LLM извлекают comment; ConfirmationResponse.comment для UI |
| 2026-04-23 | bot_redirect: неизвестные интенты → bot_redirect=true, original_text сохраняется | Ключ для интеграции с чат-ботом банка: если L1+L2 не распознали — передать в диалоговую систему |
| 2026-04-23 | LLM cascade: DeepSeek (прямой API) → OpenRouter (deepseek/deepseek-chat) → unknown | Уменьшение latency: прямой DeepSeek быстрее; OpenRouter как fallback при недоступности; оба провайдера через общий интерфейс |
| 2026-04-23 | /dashboard/hints: CRUD hint_overrides через dashboard (Basic Auth) | Продуктовая команда может форсировать подсказку конкретному пользователю для демо |
| 2026-04-24 | OmegaSheetScaffold — единый bottom-sheet контейнер для всех виджет-плашек | Замена дублирующего overlay-кода в каждом Activity; scrim(0.5) + handle + OmegaTopBar + content slot + footer slot; slide-in-up 280ms |
| 2026-04-24 | Opus Design Audit: реализация S-1..S-10, P-1..P-7, C-1..C-3, SC-1..SC-4, T-1..T-5 | Полный цикл: аудит → DESIGN_IMPROVEMENTS.md → реализация. Компактные плашки vs fullscreen, карточки «Откуда/Кому», шаг подтверждения, аватары, операторы, платёжные системы, VTB-карты |
| 2026-04-24 | Personalization module: HintRepository + NeutralHints + WidgetHintTexts | Offline-first: NeutralHints (время суток, праздники, дни рождения) как fallback; HintRepository управляет серверным hint + локальным пулом; WidgetHintTexts форматирует reminder/vygoda с учётом чувствительности сумм |
| 2026-04-24 | MockBankRepository: AvatarPalette + detectOperator + MOCK_ACCOUNTS/MOCK_OPERATORS | Локальные mock-данные для UI без сети: пастельные аватары (6 пар цветов), DEF-коды операторов, 3 типа счетов |
| 2026-04-24 | Tone of Voice: утверждённая тональность виджета (TONE_OF_VOICE.md) | Друг-финансист, «ты», строчные, без эмодзи; чувствительность данных: credit_card/loan суммы скрыты на виджете |
| 2026-04-24 | OmegaButtonStyle.NeutralLight: белая CTA с тёмным текстом | Success-экран: белая кнопка «На главную» на тёмном фоне — эталон ВТБ |

---

## Целевой дизайн

### Прототип vs Продакшен

| Компонент | Продакшен (реальный ВТБ) | Прототип (наш код) |
|-----------|------------------------|-------------------|
| STT | Собственный STT-сервис ВТБ | MediaRecorder (запись) — распознавание в Phase 2 |
| NLP | NLP/Chatbot Service ВТБ (gRPC) | FastAPI-сервис (`ml/mock_api/`) |
| Core Banking API | Внутренний REST ВТБ | Mock-данные (data.py) |
| Биометрия | Android BiometricPrompt | BiometricHelper.kt — реальный BiometricPrompt (STRONG+WEAK) |
| JWT-сессия (app) | Сессия VTB Online App | SharedPreferences (app_token, 30д) |
| JWT-сессия (banking) | Banking API session | In-memory BankingSession (15мин) |
| Контакты | Серверная адресная книга | Android ContactsContract (реальная книга) |
| Push/FCM | Firebase → VTB Platform | Не реализовано |

### Компонентная схема

```
┌─────────────────────────────────────────────────────────────┐
│  Android Device                                              │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ VTB Vita App (APK)                                      ││
│  │                                                          ││
│  │  ┌───────────────┐   ┌───────────────────────────────┐  ││
│  │  │ VitaWidget    │   │ InputActivity (overlay)        │  ││
│  │  │ (RemoteViews) │──▶│  TEXT mode: поле + chips       │  ││
│  │  │               │   │  REC mode: waveform + таймер   │  ││
│  │  └───────┬───────┘   └───────────┬───────────────────┘  ││
│  │          │                       │                        ││
│  │  ┌───────▼──────────────────┐    │                        ││
│  │  │ SessionManager           │    │                        ││
│  │  │ SharedPreferences        │    │                        ││
│  │  └──────────────────────────┘    │                        ││
│  │                                  ▼                        ││
│  │  ┌────────────────────────────────────────────────────┐  ││
│  │  │ Banking flows (OmegaSheetScaffold)                 │  ││
│  │  │  ContactPickerActivity → TransferDetailsActivity    │  ││
│  │  │  TransferFlowActivity → ConfirmActivity            │  ││
│  │  │  TopupInputActivity                                 │  ││
│  │  │  BalanceActivity                                    │  ││
│  │  └───────────────────────┬────────────────────────────┘  ││
│  │                          │ HTTP                           ││
│  └──────────────────────────┼─────────────────────────────┘ │
│                             │ localhost:8000                  │
└─────────────────────────────┼───────────────────────────────┘
                              │ (adb reverse tcp:8000)
┌─────────────────────────────▼───────────────────────────────┐
│  Mock API  (ml/mock_api/ — Python FastAPI)                   │
│                                                              │
│  POST /parse    ← текст команды → { intent, entities }      │
│  GET  /hint     ← user_id → { type, reminder/vygoda/none }  │
│  GET  /balance  → { balance: 15320, account: "основной" }    │
│  POST /command  ← intent + params → данные для модала        │
│  POST /confirm  ← подтверждение → { status: "success" }      │
│  GET  /dashboard  → HTML-дашборд (Basic Auth)                │
│  GET  /dashboard/hints → управление подсказками (Basic Auth) │
└──────────────────────────────────────────────────────────────┘
```

### System intents (не требуют Mock API)

```
SystemIntentHandler.parse(text, context)
  → будильник  : AlarmClock.ACTION_SET_ALARM
  → таймер     : AlarmClock.ACTION_SET_TIMER
  → звонок     : ACTION_DIAL
  → открыть ПО : getLaunchIntentForPackage (appMap)
```

### Деплой (задача C-05)

- **NLP-сервис:** Railway / Render / Fly.io (бесплатный тир)
- **Android APK:** GitHub Releases
- **Демо:** APK установлен → виджет добавлен → операции живьём
