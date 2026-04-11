# Архитектура VTB Vita — Прототип

> **Принцип документа:**
> - **Текущее состояние** — только то, что реально есть в репозитории прямо сейчас.
> - **Целевой дизайн** — спецификация и план. При расхождении — обновить «Текущее состояние».

---

## Текущее состояние

*Последнее обновление: 2026-04-12 (unified transfer flow)*

### Структура репозитория

```
widget-prototype/
├── android/
│   └── app/src/main/
│       ├── java/com/vtbvita/widget/
│       │   ├── VitaWidgetProvider.kt       ← AppWidgetProvider
│       │   ├── InputActivity.kt            ← прозрачный overlay ввода
│       │   ├── BalanceActivity.kt          ← модал баланса
│       │   ├── ConfirmActivity.kt          ← модал подтверждения (legacy)
│       │   ├── ContactPickerActivity.kt    ← выбор контакта из телефонной книги
│       │   ├── TransferDetailsActivity.kt  ← шаг 2 перевода (сумма, банк, счёт)
│       │   ├── TopupInputActivity.kt       ← пополнение телефона
│       │   ├── SystemIntentHandler.kt      ← будильник / таймер / звонок / запуск приложения
│       │   ├── SessionManager.kt           ← SharedPreferences-сессия
│       │   ├── MainActivity.kt             ← выбор профиля (PERSONAS)
│       │   ├── PinEntryActivity.kt         ← PIN-экран (4 цифры)
│       │   ├── MockBankActivity.kt         ← mock банковское приложение
│       │   ├── BankingSession.kt           ← in-memory banking JWT (15 мин)
│       │   ├── PhoneVerificationActivity.kt← верификация номера телефона
│       │   ├── TransferFlowActivity.kt         ← единый модал NLP-флоу: disambiguation + подтверждение (Compose-навигация)
│       │   ├── ContactDisambiguationActivity.kt ← legacy (заменён TransferFlowActivity для NLP-пути)
│       │   ├── api/MockApiService.kt       ← HTTP-клиент к FastAPI
│       │   ├── VoiceStreamingRecorder.kt   ← WebSocket PCM-стриминг на сервер (16kHz/16-bit)
│       │   ├── VoiceRecordingService.kt    ← Foreground Service: запись голоса + анимация колец в виджете
│       │   ├── nlp/NlpService.kt           ← HTTP-клиент к /parse (object, не interface)
│       │   ├── nlp/ContactMatcher.kt       ← нечёткий поиск по ContactsContract (склонения)
│       │   ├── nlp/ContactMemory.kt        ← SharedPreferences: история выборов → boost score
│       │   ├── BiometricHelper.kt          ← реальный BiometricPrompt (STRONG + WEAK)
│       │   ├── model/Models.kt             ← data-классы
│       │   ├── ui/theme/                   ← VTB-цвета, типографика
│       │   ├── ui/components/VitaComponents.kt ← shared UI компоненты
│       │   └── ui/components/TransferDetailsContent.kt ← shared Composable для формы перевода
│       ├── res/
│       │   ├── layout/widget_vita.xml      ← RemoteViews-макет виджета
│       │   ├── drawable/widget_bg.xml      ← синий градиент, cornerRadius 32dp
│       │   ├── drawable/mic_bg.xml         ← фон кнопки микрофона
│       │   ├── drawable/stop_bg.xml        ← красный фон кнопки отмены
│       │   ├── drawable/submit_bg.xml      ← тёмно-синий фон кнопки отправки
│       │   ├── drawable/ripple_ring.xml    ← кольцо пульсации вокруг кнопки отправки
│       │   ├── drawable/ic_close.xml       ← иконка ✕
│       │   ├── drawable/ic_arrow_up.xml    ← иконка ↑ (отправить)
│       │   ├── drawable/widget_field_bg.xml
│       │   ├── drawable/bank_*.png         ← логотипы банков (vtb, sber, tbank, alfa, raiffeisen)
│       │   ├── font/vtb_*.ttf              ← VTB-шрифты (bold, book, demi_bold, light)
│       │   └── xml/vita_widget_info.xml    ← метаданные AppWidget
│       └── AndroidManifest.xml
├── ml/
│   └── mock_api/
│       ├── main.py           ← FastAPI: /verify-phone, /auth, /auth/biometric, /parse, /balance, /command, /confirm, /ws/stt
│       ├── data.py           ← mock-данные (баланс, контакты, счета)
│       ├── test_regex_parse.py ← pytest: 64 unit-теста L1-парсера
│       ├── test_api.py       ← pytest: 19 integration-тестов FastAPI (TestClient)
│       ├── Dockerfile        ← python:3.11-slim, uvicorn
│       ├── docker-compose.yml← порт 127.0.0.1:8001→8000, env_file
│       ├── requirements.txt  ← fastapi, uvicorn, httpx, jose, grpcio, protobuf, pytest
│       ├── .env.example      ← шаблон переменных окружения
│       ├── gen_proto.sh      ← одноразовая регенерация gRPC-стабов
│       ├── yandex_speech/    ← сгенерированные proto-стабы (stt_pb2, stt_pb2_grpc)
│       └── venv/
├── docs/
│   ├── ARCHITECTURE.md
│   ├── PRODUCT.md
│   ├── REQUIREMENTS.md
│   └── DEPLOY.md             ← инструкция деплоя на VDS, nginx, Docker
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
| Shared UI компоненты | `ui/components/VitaComponents.kt` | ✅ |
| Сессия (login/logout) | `SessionManager.kt` | ✅ |
| Выбор профиля | `MainActivity.kt` | ✅ |
| PIN-вход | `PinEntryActivity.kt` | ✅ |
| Mock банковское приложение (5 Compose-экранов) | `MockBankActivity.kt` | ✅ |
| Mock API (FastAPI) | `ml/mock_api/main.py` | ✅ |
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
| 2026-04-11 | data.py — единый источник данных (single source of truth) | main.py импортирует USERS + PHONE_INDEX из data.py; inline MOCK_ACCOUNTS/MOCK_CONTACTS удалены; PHONE_INDEX глобальный по всем пользователям |
| 2026-04-11 | /auth/biometric принимает user_id в теле запроса | Android знает выбранную персону → передаёт persona.id → сервер кодирует в JWT |

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
│  │  │ Banking flows                                       │  ││
│  │  │  ContactPickerActivity → TransferDetailsActivity    │  ││
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
│  GET  /balance  → { balance: 15320, account: "основной" }   │
│  POST /command  ← intent + params → данные для модала       │
│  POST /confirm  ← подтверждение → { status: "success" }     │
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
