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
| C-02 | NLP-ядро: парсинг intent'ов (transfer / balance / topup) | [~] | Яна | `feature/C-02-nlp-intent-parser` |
| C-03 | Mock API: FastAPI-сервис → данные для модалов | [x] | Денис | `feature/C-03-mock-api` |
| C-04 | Android Widget UI: виджет → ввод → модал → статус | [x] | Денис | `feature/C-04-widget-ui` |
| C-05 | Деплой: прототип доступен по публичному URL | [ ] | Денис | `feature/C-05-deploy` |

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
| 2026-04-04 | ContactMemory: boost score на основе истории выборов (count≥3 → +0.5 → auto-resolve) | Позволяет виджету обучаться на предпочтениях пользователя без сервера |
| 2026-04-04 | VoiceStreamingRecorder: WebSocket + PCM 16kHz → Яндекс SpeechKit через прокси-сервер | Потоковый STT вместо batch записи — partial/final результаты в реальном времени |
