# VTB Vita — Прототип

Голосовой/текстовый виджет на главном экране Android для мгновенных банковских операций.

**Дедлайн:** 5 апреля 2026 (публичный доступ) → 19 апреля 2026 (защита)

---

## Быстрый старт

### NLP-сервис (Яна)
```bash
cd ml
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

### Android-приложение (Денис)
```bash
cd android
./gradlew assembleDebug
# APK: android/app/build/outputs/apk/debug/app-debug.apk
```

> Инструкция обновляется по мере разработки.

---

## Документация

| Документ | Что внутри |
|----------|-----------|
| [BACKLOG.md](BACKLOG.md) | Все задачи, статусы, ветки |
| [docs/PRODUCT.md](docs/PRODUCT.md) | Продуктовая логика, флоу, операции |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Архитектура, API контракт, C4-диаграммы |
| [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) | Что входит в MVP, критерии приёмки |
| [CLAUDE.md](CLAUDE.md) | Контекст для AI-ассистента |

---

## Команда разработки

| Человек | Зона |
|---------|------|
| Денис | Android Widget, интеграция, деплой |
| Яна | NLP/ML сервис (Python) |

---

## Стек

- **Android:** Kotlin + Jetpack Compose + AppWidget API
- **NLP:** Python + FastAPI
- **Деплой:** Railway / Render (NLP) + GitHub Releases (APK)
