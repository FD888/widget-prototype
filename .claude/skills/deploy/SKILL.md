---
name: deploy
description: This skill should be used when the user asks to deploy, push, install, upload the server, rebuild the container, install APK, connect the phone via adb, or mentions "задеплоить", "залить на сервер", "установить APK", "собрать и поставить", "adb install", "пересобрать контейнер".
version: 1.0.0
---

# Deploy — сервер и телефон

## Инфраструктура

### Сервер
- **SSH alias:** `tgdom-server`
- **IP:** `45.150.9.210`
- **Файлы:** `/opt/vita-api/`
- **URL:** `https://vtb.vibefounder.ru`
- **Проверка:** `curl -s https://vtb.vibefounder.ru/health` → `{"status":"ok",...}`

### ADB (Android)
- **Путь:** `/home/volter596/Android/Sdk/platform-tools/adb` (не в системном PATH)
- **APK:** `android/app/build/outputs/apk/debug/app-debug.apk`
- **Проверка устройства:** `/home/volter596/Android/Sdk/platform-tools/adb devices`

---

## Деплой сервера

```bash
# Залить изменённые файлы
rsync -avz ml/mock_api/main.py tgdom-server:/opt/vita-api/main.py

# Если изменилось несколько файлов (кроме .env и __pycache__)
rsync -avz ml/mock_api/ tgdom-server:/opt/vita-api/ --exclude='.env' --exclude='__pycache__'

# Пересобрать и перезапустить контейнер
ssh tgdom-server "cd /opt/vita-api && sudo docker-compose down && sudo docker-compose up -d --build"

# Проверить
curl -s https://vtb.vibefounder.ru/health
```

## Сборка и установка APK

```bash
# Сборка (запускать из android/)
./gradlew assembleDebug

# Установка на подключённый телефон
/home/volter596/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> **adb reverse НЕ нужен** — mock API работает на удалённом сервере (`https://vtb.vibefounder.ru`),
> телефон обращается к нему напрямую. Туннель `adb reverse tcp:8000 tcp:8000` был актуален
> только при локальном запуске API (`ml/mock_api/` через uvicorn/docker на машине разработчика).

## Диагностика

```bash
# Логи контейнера
ssh tgdom-server "sudo docker logs vita_api --tail 100"

# Перезапуск без пересборки
ssh tgdom-server "cd /opt/vita-api && sudo docker-compose restart"

# ADB: переподключить устройство
/home/volter596/Android/Sdk/platform-tools/adb kill-server && /home/volter596/Android/Sdk/platform-tools/adb start-server
```
