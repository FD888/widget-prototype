# План развёртывания на VDS

**Целевая дата:** 5 апреля 2026 (C-05)
**Сервер:** VDS Дениса (там же ТГДОМ — требует изоляции)

---

## Два типа запросов — разные правила доступа

Это ключевое архитектурное решение:

| Тип | Эндпоинты | Авторизация | Почему |
|-----|-----------|-------------|--------|
| **NLP-обработка** | `POST /parse` | Только API-key | «Поставь будильник» не требует доступа к банковским данным |
| **Банковский бэкенд** | `GET /balance`, `POST /command`, `POST /confirm/{id}`, `POST /lookup` | API-key + JWT токен | Доступ к счетам, балансам, истории — только авторизованный пользователь |
| **Аутентификация** | `POST /auth` | Только API-key | Выдаёт JWT; сам по себе не требует токена |
| **Служебные** | `GET /health` | Без проверок | Мониторинг |

Логика: виджет всегда может попросить сервер распарсить текст. Но получить баланс или отправить перевод — только с действующим JWT.

---

## Два уровня состояния (важное разграничение)

```
Уровень 1 — Профиль (кто ты)
  Хранится в SharedPreferences (persona_id)
  Живёт до явного выхода из аккаунта
  Влияет на: виджет активен / "Войдите в аккаунт", персонализированный промпт
  НЕ даёт доступа к банковским данным

Уровень 2 — Банковская сессия (JWT)
  Выдаётся только после PIN / биометрии
  Живёт 15 минут
  Нужен для: /balance, /command, /confirm
  НЕ нужен для: /parse (NLP), системных интентов (будильник, звонок)
```

## Флоу авторизации

```
MainActivity: выбор профиля (Денис)
    → SessionManager.savePersona("denis")   ← просто "кто ты", без токена
    → виджет показывает "Как настроение, Денис?" (активен, но без JWT)

Пользователь даёт банковскую команду (баланс / перевод / пополнение):
    → Android проверяет: есть JWT и он не истёк?
        → Да → сразу запрос к бэкенду
        → Нет → показать PinEntryActivity (или BiometricPrompt)
            → Пользователь вводит PIN
            → POST /auth  { persona_id: "denis", pin: "1234" }
            ← { token: "eyJ...", expires_in: 900 }   ← 15 минут
            → SessionManager.saveToken(token, expiresAt)
            → продолжить запрос к бэкенду

Каждый банковский запрос:
    → Header: Authorization: Bearer <token>
    ← данные  /  401 Unauthorized

При 401 или истечении токена:
    → SessionManager.clearToken()   ← профиль остаётся, токен сбрасывается
    → следующая банковская команда снова запросит PIN

InputActivity.onPause() (пользователь ушёл с экрана — свернул, нажал назад):
    → SessionManager.clearToken()   ← сессия использования закончена
    → следующий вход снова потребует PIN
    Логика: за время пока экран закрыт к телефону мог подойти другой человек

Явный выход из аккаунта (кнопка в MockBankActivity):
    → SessionManager.logout()       ← сбрасывает и профиль, и токен
    → виджет "Войдите в аккаунт"
```

## Что требует PIN, что нет

| Действие | PIN нужен | Почему |
|----------|-----------|--------|
| Активировать виджет (выбор профиля) | Нет | Просто выбор "кто ты" |
| «Поставь будильник» | Нет | Системный интент, нет банковских данных |
| «Открой Telegram» | Нет | Нет банковских данных |
| «Переведи Кате 500» | Да | Нужен доступ к счётам |
| «Покажи баланс» | Да | Банковские данные |
| «Пополни телефон» | Да | Нужен доступ к счётам |

---

## Структура сервисов на VDS

```
VDS
├── /projects/tgdom/          ← продакшен ТГДОМ (не трогаем)
└── /projects/vtb-vita/
    └── mock_api/             ← Docker-контейнер, изолирован
        ├── main.py
        ├── data.py
        ├── .env              ← секреты (не в репо)
        ├── .env.example      ← шаблон (в репо)
        ├── Dockerfile
        ├── docker-compose.yml
        └── requirements.txt
```

Nginx роутит:
- `vita-api.твой-домен.ru` → контейнер на порту 8000
- ТГДОМ остаётся на своём домене, изолирован

---

## Безопасность

### 1. Переменные окружения (не в коде, не в репо)

```bash
# .env (только на сервере)
DEEPSEEK_API_KEY=sk-...
APP_API_KEY=vita_demo_2026        # статический ключ, прошивается в APK
JWT_SECRET=случайная_строка_32+символа
JWT_EXPIRE_MINUTES=15
```

```bash
# .env.example (в репо — шаблон без значений)
DEEPSEEK_API_KEY=
APP_API_KEY=
JWT_SECRET=
JWT_EXPIRE_HOURS=24
```

### 2. Статический API-key в APK

Каждый запрос с Android содержит заголовок:
```
X-Api-Key: vita_demo_2026
```
Сервер отклоняет запросы без этого ключа. Отсекает случайных людей, нашедших URL. Не настоящая безопасность — но достаточно для прототипа.

### 3. Rate limiting

```python
# slowapi — 30 запросов/минуту с одного IP
```
Защита от случайного или намеренного сжигания DeepSeek токенов.

### 4. Docker-изоляция

mock_api работает в контейнере — если в FastAPI найдётся уязвимость, из контейнера до файлов ТГДОМ не добраться напрямую.

---

## Шаги деплоя

### Шаг 1 — Dockerfile и docker-compose

```dockerfile
# Dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY . .
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

```yaml
# docker-compose.yml
services:
  vita-api:
    build: .
    restart: unless-stopped
    ports:
      - "127.0.0.1:8001:8000"   # только localhost, nginx проксирует
    env_file: .env
```

### Шаг 2 — requirements.txt

```
fastapi
uvicorn[standard]
pydantic
python-jose[cryptography]   # JWT
passlib[bcrypt]             # хэш PIN (опционально для прототипа)
slowapi                     # rate limiting
httpx                       # запросы к DeepSeek
python-dotenv
```

### Шаг 3 — Добавить эндпоинты в main.py

**`POST /auth`** — принимает persona_id + pin, возвращает JWT:
```python
# Псевдокод
@app.post("/auth")
def auth(req: AuthRequest, api_key: str = Header(..., alias="X-Api-Key")):
    check_api_key(api_key)
    persona = PERSONAS.get(req.persona_id)
    if not persona or persona["pin"] != req.pin:
        raise HTTPException(401, "Неверный PIN")
    token = create_jwt(persona_id=req.persona_id)
    return {"token": token, "expires_in": 3600}
```

**`POST /parse`** — только API-key, без JWT:
```python
@app.post("/parse")
def parse(req: ParseRequest, api_key: str = Header(..., alias="X-Api-Key")):
    check_api_key(api_key)
    result = call_deepseek(req.text)  # DeepSeek разбирает команду
    return result  # {intent, recipient, amount, ...}
```

**Все банковские эндпоинты** — API-key + JWT:
```python
@app.get("/balance")
def balance(api_key=..., token_payload=Depends(require_jwt)):
    ...
```

### Шаг 4 — Nginx конфиг

```nginx
server {
    server_name vita-api.твой-домен.ru;
    location / {
        proxy_pass http://127.0.0.1:8001;
        proxy_set_header Host $host;
    }
}
```

SSL через `certbot --nginx`.

### Шаг 5 — Android: обновить MockApiService

```kotlin
// Было
private const val BASE_URL = "http://10.0.2.2:8000/"  // localhost

// Стало
private const val BASE_URL = "https://vita-api.твой-домен.ru/"
private const val API_KEY = "vita_demo_2026"

// Добавить в каждый запрос:
.addHeader("X-Api-Key", API_KEY)
.addHeader("Authorization", "Bearer ${SessionManager.getToken(context)}")
```

### Шаг 6 — GitHub Releases

```bash
./gradlew assembleRelease
# Подписать APK (debug подпись — ok для прототипа)
# Загрузить на GitHub → Releases → New Release → v1.0.0
```

---

## Флоу для жюри (итог)

1. Перейти по ссылке → скачать APK
2. Установить на Android (разрешить установку из неизвестных источников)
3. Выбрать профиль Денис → PIN 1234
4. Всё работает, сервер на VDS

**Для технических ревьюеров ВТБ** — клонировать репо, запустить локально:
```bash
cd ml/mock_api
cp .env.example .env       # заполнить ключи
docker compose up
# adb reverse tcp:8000 tcp:8000
```

---

## Что НЕ делаем (out of scope прототипа)

- Refresh token (JWT живёт до первого из двух событий: 15 минут по таймеру ИЛИ закрытие InputActivity — симулирует реальный банк)
- Хэширование PIN (в прототипе PIN проверяется открыто — это допустимо, указано в UI)
- HTTPS pinning в APK
- Реальная база данных (всё in-memory, перезапуск сервера сбрасывает pending operations)
