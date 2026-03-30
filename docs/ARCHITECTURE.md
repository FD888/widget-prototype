# Архитектура VTB Vita — Прототип

> **Принцип документа:**
> - **Текущее состояние** — только то, что реально есть в репозитории прямо сейчас. Обновляется автоматически при каждом коммите через `/commit`.
> - **Целевой дизайн** — спецификация и план. Меняется вручную при смене архитектурного решения.

---

## Текущее состояние

*Последнее обновление: 2026-03-30*

### Структура репозитория

```
widget-prototype/
├── android/         — пусто (.gitkeep), Kotlin-приложение не начато
├── ml/              — пусто (.gitkeep), Python NLP-сервис не начат
├── docs/
│   ├── ARCHITECTURE.md
│   ├── PRODUCT.md
│   └── REQUIREMENTS.md
├── .claude/
│   └── commands/
│       └── commit.md   — кастомная /commit команда
├── .gitignore
├── BACKLOG.md
├── CLAUDE.md
└── README.md
```

### Компоненты (реализованные)

Пока нет — идёт стадия подготовки (C-01).

### Зафиксированные технические решения

| Дата | Решение | Причина |
|------|---------|---------|
| 2026-03-30 | Android виджет: Kotlin + AppWidget API (RemoteViews) | RemoteViews стабильнее Glance, больше документации, Glance конвертируется в RemoteViews под капотом — лишний слой багов |
| 2026-03-30 | Ввод текста: тап по виджету → InputActivity → результат в виджет | EditText в AppWidget запрещён Android OS — оба подхода (Glance и RemoteViews) имеют это ограничение |
| 2026-03-30 | NLP: DeepSeek API (прототип) → self-hosted на инфраструктуре ВТБ (продакшен) | Трансграничная передача данных: для реального продукта модель разворачивается внутри периметра банка |
| 2026-03-30 | Mock-данные: JSON-файлы или хардкод, без реального API | Критическое ограничение (безопасность/демо) |

---

## Целевой дизайн

> Спецификация того, к чему идём. Не факт — план. При расхождении с реализацией — обновить «Текущее состояние», а не этот раздел.

### Прототип vs Продакшен

| Компонент | Продакшен (реальный ВТБ) | Прототип (наш код) |
|-----------|------------------------|-------------------|
| STT | Собственный STT-сервис ВТБ | Android SpeechRecognizer / пропускаем в Phase 1 |
| NLP | NLP/Chatbot Service ВТБ (gRPC) | Наш FastAPI-сервис (`ml/`) |
| Core Banking API | Внутренний REST ВТБ | Mock-данные (JSON-файлы) |
| Биометрия | Android BiometricPrompt | UI-заглушка (кнопка «Подтвердить») |
| JWT-сессия | Сессия VTB Online App | Захардкоженный флаг `isAuthenticated = true` |
| Push/FCM | Firebase → VTB Platform | Mock-сообщения в Phase 2 |

### Компонентная схема

```
┌─────────────────────────────────────────────────────────┐
│  Android Device                                          │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐│
│  │ VTB Vita App (наш APK)                             ││
│  │                                                      ││
│  │  ┌──────────────────┐   ┌──────────────────────┐   ││
│  │  │  VitaWidget     │   │  ConfirmActivity      │   ││
│  │  │  (AppWidget UI)  │──▶│  (модал подтверждения)│   ││
│  │  │  Kotlin/Compose  │   │  Kotlin/Compose       │   ││
│  │  └────────┬─────────┘   └──────────────────────┘   ││
│  │           │ HTTP                                     ││
│  └───────────┼─────────────────────────────────────────┘│
│              │                                           │
└──────────────┼───────────────────────────────────────────┘
               │ HTTP (localhost или удалённый)
┌──────────────▼───────────────────────────────────────────┐
│  NLP / Mock API  (ml/ — Python FastAPI)                   │
│                                                           │
│  POST /parse   ← текст команды                            │
│  → { intent, recipient, amount, account }                 │
│                                                           │
│  GET /balance  → { balance: 15320 }  (mock)               │
│  POST /confirm → { status: "success" }  (mock)            │
└───────────────────────────────────────────────────────────┘
```

### Целевая структура Android-приложения

```
android/app/src/main/java/com/vtbvita/
├── widget/
│   ├── VitaWidgetProvider.kt   ← AppWidgetProvider (RemoteViews)
│   └── VitaWidgetUpdater.kt    ← логика обновления RemoteViews
├── ui/
│   ├── ConfirmActivity.kt       ← модал подтверждения
│   └── InputActivity.kt         ← экран ввода команды
├── api/
│   ├── VitaApiClient.kt        ← HTTP-клиент → NLP-сервис
│   └── MockData.kt              ← fallback mock-данные
└── model/
    ├── Intent.kt
    └── Transaction.kt
```

### Целевая структура NLP-сервиса

```
ml/
├── main.py              ← FastAPI app
├── parser/
│   ├── intent.py        ← классификация intent'а
│   └── entities.py      ← извлечение сущностей
├── mock/
│   └── contacts.json    ← mock-история переводов
├── requirements.txt
└── README.md
```

### API-контракт (POST /parse)

```json
// Request
{ "text": "Переведи Кате тысячу" }

// Response
{
  "intent": "transfer",
  "recipient": { "name": "Катя Иванова", "card": "•4521" },
  "amount": 1000,
  "account": "основной",
  "confidence": 0.95
}
```

### Деплой (задача C-05)

- **NLP-сервис:** Railway / Render / Fly.io (бесплатный тир)
- **Android APK:** GitHub Releases
- **Демо:** APK установлен → виджет добавлен → 3 операции живьём
