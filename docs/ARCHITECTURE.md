# Архитектура VTB Vita — Прототип

> **Принцип документа:**
> - **Текущее состояние** — только то, что реально есть в репозитории прямо сейчас.
> - **Целевой дизайн** — спецификация и план. При расхождении — обновить «Текущее состояние».

---

## Текущее состояние

*Последнее обновление: 2026-04-02*

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
│       │   ├── api/MockApiService.kt       ← HTTP-клиент к FastAPI
│       │   ├── nlp/NlpService.kt           ← обёртка NLP
│       │   ├── model/Models.kt             ← data-классы
│       │   └── ui/theme/                   ← VTB-цвета, типографика
│       ├── res/
│       │   ├── layout/widget_vita.xml      ← RemoteViews-макет виджета
│       │   ├── drawable/widget_bg.xml      ← синий градиент, cornerRadius 32dp
│       │   ├── drawable/mic_bg.xml         ← фон кнопки микрофона
│       │   ├── drawable/widget_field_bg.xml
│       │   └── xml/vita_widget_info.xml    ← метаданные AppWidget
│       └── AndroidManifest.xml
├── ml/
│   └── mock_api/
│       ├── main.py     ← FastAPI: /parse, /balance, /command, /confirm
│       ├── data.py     ← mock-данные (баланс, контакты, счета)
│       └── venv/
├── docs/
│   ├── ARCHITECTURE.md
│   ├── PRODUCT.md
│   └── REQUIREMENTS.md
├── .claude/commands/
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
| Перевод (2 шага) | `TransferDetailsActivity.kt` | ✅ |
| Пополнение телефона | `TopupInputActivity.kt` | ✅ |
| Баланс | `BalanceActivity.kt` | ✅ |
| Сессия (login/logout) | `SessionManager.kt` | ✅ |
| Выбор профиля | `MainActivity.kt` | ✅ |
| PIN-вход | `PinEntryActivity.kt` | ✅ |
| Mock банковское приложение | `MockBankActivity.kt` | ✅ |
| Mock API (FastAPI) | `ml/mock_api/main.py` | ✅ |
| NLP intent parsing | `ml/` (C-02) | 🔄 in progress (Яна) |

### Зафиксированные технические решения

| Дата | Решение | Причина |
|------|---------|---------|
| 2026-03-31 | Pill-форма: прозрачный FrameLayout + внутренний LinearLayout 64dp | Лончеры растягивают ячейку — фиксированная высота только у внутреннего слоя |
| 2026-03-31 | InputActivity: прозрачная тема, overlay на 70% экрана | Иллюзия «выплывания» без смены экрана |
| 2026-03-31 | hideWidget в onCreate, restoreWidget в onPause | onDestroy ненадёжен |
| 2026-04-01 | SessionManager (SharedPreferences) | Персонализация виджета + контроль доступа |
| 2026-04-02 | adb reverse tcp:8000 tcp:8000 для USB-тоннеля | Mock API недоступен с устройства без тоннеля |

---

## Целевой дизайн

### Прототип vs Продакшен

| Компонент | Продакшен (реальный ВТБ) | Прототип (наш код) |
|-----------|------------------------|-------------------|
| STT | Собственный STT-сервис ВТБ | MediaRecorder (запись) — распознавание в Phase 2 |
| NLP | NLP/Chatbot Service ВТБ (gRPC) | FastAPI-сервис (`ml/mock_api/`) |
| Core Banking API | Внутренний REST ВТБ | Mock-данные (data.py) |
| Биометрия | Android BiometricPrompt | UI-заглушка — кнопка «Подтвердить» |
| JWT-сессия | Сессия VTB Online App | SharedPreferences (SessionManager) |
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
│  │  └───────────────────────── ┘    │                        ││
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
