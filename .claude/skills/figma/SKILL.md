---
name: figma
description: Работа с дизайн-системой Omega (ВТБ) через Framelink MCP. Использовать при необходимости получить данные из Figma — цвета, типографика, компоненты, иконки, токены. Или когда пользователь упоминает "figma", "дизайн-система", "omega", "компонент", "токены", "palette", "tokens", "дизайн".
version: 1.0.0
---

# Figma — работа с дизайн-системой Omega через Framelink MCP

## Общие правила

- Всегда используй MCP-инструменты (`framelink_get_figma_data`, `framelink_download_figma_images`) для получения данных из Figma
- Не угадывай значения цветов, отступов, шрифтов — всегда проверяй в Figma
- Навигация по файлам — в `docs/FIGMA.md`
- Токен хранится в `.env` (`FIGMA_API_KEY`), НЕ в коде

## MCP-инструменты

### framelink_get_figma_data

Получает структуру, стили и layout узла.

**Параметры:**
- `fileKey` (обязательный) — ключ файла из URL Figma. Примеры:
  - `CdltwQRSK0kgym24ahrocM` — 00-Omega-Cookbook-β
  - `3QWKdMLvlrLW16IH8JEHcJ` — 01-omg-palette
  - `G9S8n1X7BYd4B4tQ0P9Twp` — 00-omg-guidelines
  - `BqHLTGHIZa8Ia7P4fm5iH2` — 02-omg-tokens
  - `MqZnHfCFOCLXXvkix4zheB` — 04-omg-illustrations
  - `eLjs9ILzBJos8XV5MwoWmz` — 05-omg-atoms---layouts
  - `Rnh5b6ky1VfiaKhRwLoGxv` — 06-omg-meta
  - `oYeLsPauzfx4AvQTSScvLq` — 07-omg-base-components
  - `9wceOsl8KOGcD7p87xV7Dy` — 08-omg-templates
- `nodeId` (опциональный) — ID узла для детализации. Формат `1234:5678` (заменить `-` на `:`)

### framelink_download_figma_images

Скачивает PNG/SVG из Figma.

**Параметры:**
- `fileKey` — ключ файла
- `nodes` — массив `{ nodeId, fileName, imageRef?, gifRef? }`
- `localPath` — путь для сохранения относительно корня проекта
- `pngScale` (опциональный) — масштаб PNG, по умолчанию 2

## Типовые сценарии

### Получить цвета (palette)

```
framelink_get_figma_data(fileKey="3QWKdMLvlrLW16IH8JEHcJ")
```

Результат: hex-значения цветов, semantic names. Конвертировать в `Color.kt`.

### Получить типографику (tokens)

```
framelink_get_figma_data(fileKey="BqHLTGHIZa8Ia7P4fm5iH2")
```

Результат: font families, sizes, weights, line heights. Конвертировать в `Type.kt`.

### Получить компонент

```
framelink_get_figma_data(fileKey="oYeLsPauzfx4AvQTSScvLq", nodeId="...")
```

Результат: layout, fills, borderRadius, padding. Использовать для точного воспроизведения в Compose.

### Скачать иконку/иллюстрацию

```
framelink_download_figma_images(
  fileKey="eLjs9ILzBJos8XV5MwoWmz",
  nodes=[{ nodeId: "1234:5678", fileName: "icon_name.svg" }],
  localPath="android/app/src/main/res/drawable"
)
```

## Правила конвертации Figma → Kotlin/Compose

### Цвета
- Figma hex (например `#1A1A2E`) → `val Background = Color(0xFF1A1A2E)`
- Semantic names (например `semantic/text/constantPrimary`) → использовать как имя переменной
- Dark theme: отдельный набор цветов

### Типографика
- Figma `textStyle: headlines/headlineS` → Compose `Typography`
- font-size (px) → sp
- font-weight → `FontWeight`
- line-height → `LineHeight`

### Spacing / Border Radius
- Значения из tokens → `dp` единицы
- borderRadius → `RoundedCornerShape(X.dp)`

### Компоненты
- Анализировать layout (Auto Layout → Column/Row)
- fills → background/color
- strokes → border
- padding → Modifier.padding
- gap → Spacer

## Частые проблемы

- **Timeout при запросе** — файл слишком большой. Запрашивай конкретный nodeId вместо целого файла
- **Пустой результат** — проверить nodeId, заменить `-` на `:`
- **Нет доступа** — проверить FIGMA_API_KEY в `.env`
