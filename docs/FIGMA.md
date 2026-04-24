# Figma Design System — Omega (ВТБ)

Дизайн-система **Omega** — единый источник стилей и компонентов для фронтенда уровня ВТБ Онлайн.

Доступ через **Framelink MCP** (opencode.json). Токен в `.env` (`FIGMA_API_KEY`).

---

## Карта проектов Figma

### 00 — Omega Cookbook β
**URL:** https://www.figma.com/design/CdltwQRSK0kgym24ahrocM/00-Omega-Cookbook-β
**File Key:** `CdltwQRSK0kgym24ahrocM`

Обзорный файл дизайн-системы. Навигация, статус компонентов, naming conventions.

| Страница | Node ID | Содержимое |
|----------|---------|------------|
| read me | `7062:2140` | Введение |
| архитектура | `7062:13476` | Структура DS |
| библиотека компонетов | `7062:14322` | Перечень всех компонентов со статусами |
| нейминг конвенция | `7062:16176` | Правила именования |
| android | `7062:16392` | Android-специфичные компоненты |
| [dep]Руководство v 1.2 | `0:1` | Документация для разработчиков |

---

### 01 — omg-palette
**URL:** https://www.figma.com/design/3QWKdMLvlrLW16IH8JEHcJ/01-omg-palette
**File Key:** `3QWKdMLvlrLW16IH8JEHcJ`

Цветовая палитра. Все brand colors, semantic colors, gradients, dark/light theme.

**Использование:** генерация `Color.kt`, цветовая тема Compose.

---

### 00 — omg-guidelines
**URL:** https://www.figma.com/design/G9S8n1X7BYd4B4tQ0P9Twp/00-omg-guidelines
**File Key:** `G9S8n1X7BYd4B4tQ0P9Twp`

Гайдлайны — правила использования компонентов, spacing, padding, accessibility.

---

### 02 — omg-tokens
**URL:** https://www.figma.com/design/BqHLTGHIZa8Ia7P4fm5iH2/02-omg-tokens
**File Key:** `BqHLTGHIZa8Ia7P4fm5iH2`

Design tokens — типографика (font size, weight, line height), spacing, border-radius, shadows.

**Использование:** генерация `Type.kt`, `Theme.kt`, dimensions.

---

### 04 — omg-illustrations
**URL:** https://www.figma.com/design/MqZnHfCFOCLXXvkix4zheB/04-omg-illustrations
**File Key:** `MqZnHfCFOCLXXvkix4zheB`

Иллюстрации. SVG/PNG ассеты для внедрения в Android (drawable).

---

### 05 — omg-atoms & layouts
**URL:** https://www.figma.com/design/eLjs9ILzBJos8XV5MwoWmz/05-omg-atoms---layouts
**File Key:** `eLjs9ILzBJos8XV5MwoWmz`

Атомарные элементы (иконки, микрокомпоненты) и layout-шаблоны.

---

### 06 — omg-meta
**URL:** https://www.figma.com/design/Rnh5b6ky1VfiaKhRwLoGxv/06-omg-meta
**File Key:** `Rnh5b6ky1VfiaKhRwLoGxv`

Мета-компоненты: badge, link, status, meta-информация.

---

### 07 — omg-base-components
**URL:** https://www.figma.com/design/oYeLsPauzfx4AvQTSScvLq/07---omg---base-components
**File Key:** `oYeLsPauzfx4AvQTSScvLq`

Базовые компоненты: Button, Input, Checkbox, Dropdown, Card и т.д.

**Использование:** основа для Kotlin/Compose UI-компонентов.

---

### 08 — omg-templates
**URL:** https://www.figma.com/design/9wceOsl8KOGcD7p87xV7Dy/08-omg-templates
**File Key:** `9wceOsl8KOGcD7p87xV7Dy`

Готовые шаблоны экранов и страниц.

---

## Приоритет извлечения для нашего проекта

| Приоритет | Файл | Что берём | Куда идёт в Android |
|-----------|-------|-----------|---------------------|
| 1 | 01-palette | Цвета | `ui/theme/OmegaColor.kt` |
| 2 | 02-tokens | Типографика, spacing | `ui/theme/OmegaType.kt`, `ui/theme/OmegaDimensions.kt` |
| 3 | 03-omg-typography | Дополнительная типографика | `ui/theme/OmegaType.kt` |
| 4 | 04-omg-icons | Иконки 24px (filled + outline) | `res/drawable/` → VectorDrawable XML |
| 5 | 04-admiral-icons | Иконки (альтернативный набор) | Справочно |
| 6 | 07-omg-base-components | Кнопки, инпуты, карточки | `ui/components/OmegaComponents.kt` |
| 7 | 07---omg---controls | Checkbox, switch, radio, tabs | `ui/components/` |
| 8 | 07---omg---cards---cells | Карточки, ячейки, списки | `ui/components/` |
| 9 | 07-omg-constructors | Конструкторы экранов | Справочно |
| 10 | 07-contactCenter-components | Контакт-центр | Справочно |
| 11 | 05-atoms-layouts | Layouts, iconContainer | Справочно (иконки извлечены из 04-omg-icons) |
| 12 | 08-omg-templates | Шаблоны экранов | Справочно |
| 13 | 04-illustrations | Иллюстрации | `res/drawable/` |
| 14 | 00-cookbook | Навигация, статусы | Справочно |
| 15 | 00-guidelines | Правила использования | Справочно |

---

## Новые файлы дизайн-системы (добавлены 17.04.2026)

### 04 — omg-icons
**URL:** https://www.figma.com/design/zmylYdDg4tUJrNWGcHCnPX/04-omg-icons
**File Key:** `zmylYdDg4tUJrNWGcHCnPX`

Полная библиотека иконок Omega. Страницы: `16 px`, `24 px`, `32 px`, `40 px`, `other`.
Иконки хранятся как COMPONENT (не swappable), можно скачивать через REST API.

**Использование:** REST API `GET /files/{key}/images?ids={nodeIds}&format=svg` → SVG → конвертация в VectorDrawable XML.

**Скачанные иконки (24px, filled, в `res/drawable/`):**

| Файл | Описание | Заменяет |
|------|----------|----------|
| `ic_arrow_up.xml` | Стрелка вверх | `ic_arrow_up.xml` (старый), `Icons.Default.ArrowUp` |
| `ic_arrow_down.xml` | Стрелка вниз | — |
| `ic_close.xml` | Крестик (закрыть) | `ic_close.xml` (старый), `Icons.Default.Close` |
| `ic_check.xml` | Галочка в круге | `ic_check.xml` (старый) |
| `ic_mic.xml` | Микрофон | `ic_mic.xml` (старый), `Icons.Default.Mic` |
| `ic_phone.xml` | Телефон | `Icons.Default.Phone` |
| `ic_search.xml` | Лупа (поиск) | `Icons.Default.Search` |
| `ic_fingerprint.xml` | Отпечаток | `Icons.Default.Fingerprint` |
| `ic_exit.xml` | Выход | `Icons.AutoMirrored.Filled.ExitToApp` |
| `ic_cart.xml` | Корзина | `Icons.Default.ShoppingCart` |
| `ic_wallet.xml` | Кошелёк | — |
| `ic_home.xml` | Дом | `Icons.Default.Home` |
| `ic_train.xml` | Поезд | `Icons.Default.Train` |
| `ic_bus.xml` | Автобус | `Icons.Default.DirectionsBus` |
| `ic_globe.xml` | Глобус | `Icons.Default.Wifi` (для интернета) |
| `ic_card_arrow_right.xml` | Карта со стрелкой | `Icons.Default.CreditCard` |
| `ic_card_line.xml` | Карта | — |
| `ic_vtb_check.xml` | ВТБ галочка | — |
| `ic_vtb_arrow_right.xml` | ВТБ стрелка вправо | `Icons.AutoMirrored.Filled.Send` |
| `ic_repeat.xml` | Повтор/обновление | `Icons.Default.Refresh` |
| `ic_chart_zigzag.xml` | График зигзаг | `Icons.Default.TrendingUp` |
| `ic_shield_fingerprint.xml` | Щит+отпечаток | — |
| `ic_magnifier.xml` | Лупа (альтернативная) | `Icons.Default.Search` |

Также скачаны outline-версии: `ic_arrow_up_outline.xml`, `ic_mic_outline.xml`, `ic_check_outline.xml`, `ic_phone_outline.xml`.

**Цвет иконок:** Все иконки имеют `fillColor="#FFFFFFFF"` (белый для тёмной темы). Для светлой темы — заменить на `#FF1E2024` (OmegaBackground).

### 04 — admiral-icons
**URL:** https://www.figma.com/design/YQbOw5OzeHnKRWsBWngR13/04-admiral-icons
**File Key:** `YQbOw5OzeHnKRWsBWngR13`

Альтернативный набор иконок (Admiral DS). Если Omega не имеет нужной иконки — можно взять из Admiral.

### 07 — omg-controls
**URL:** https://www.figma.com/design/kfP2U0odvbK2tUNfJQI6d5/07---omg---controls
**File Key:** `kfP2U0odvbK2tUNfJQI6d5`

Компоненты управления: checkbox, switch, radio, tabs, segment control, feedback, suggest, pagination, chips.
Страницы: `chips`, `checkbox`, `switch`, `radio`, `tabs`, `segmentControl`, `feedback`, `suggest`, `pagination`.

### 07 — omg-cards-cells
**URL:** https://www.figma.com/design/weKvtBOImfkskqX9lWfqaB/07---omg---cards---cells
**File Key:** `weKvtBOImfkskqX9lWfqaB`

Карточки, ячейки, списки. Страницы: `cell`, `сards`, `dataRow`, `list`, `tree`.

### 07 — omg-constructors
**URL:** https://www.figma.com/design/F1E3ez5mruuOuYZ5fwnif1/07-omg-constructors
**File Key:** `F1E3ez5mruuOuYZ5fwnif1`

Конструкторы: calendar, cardsConstructor, cellConstructor, content/section, table, tree.

### 07 — contactCenter-components
**URL:** https://www.figma.com/design/W6y3PgiiWeKLCyRjYo4VZA/07-contactCenter-components
**File Key:** `W6y3PgiiWeKLCyRjYo4VZA`

Контакт-центр: button, bubble, informers, calendar, hint, stepper.

### 03 — omg-typography
**URL:** https://www.figma.com/design/qhGjpoZXwz6GcoMHXg8J35/03-omg-typography
**File Key:** `qhGjpoZXwz6GcoMHXg8J35`

Дополнительная типографика. Страницы: `Typography`, `Sizes`, `sandbox`.

---

## Как работать с Framelink MCP

MCP-инструменты доступны через opencode:
- `framelink_get_figma_data` — получить структуру и стили узла
- `framelink_download_figma_images` — скачать изображения/SVG

См. скилл `figma` для инструкций.

---

## Извлечение данных через Figma REST API

Framelink MCP **не отдаёт** текстовые стили (fontSize, fontWeight, lineHeight, letterSpacing) — только цвета и layout. Для типографики и точных параметров компонентов используйте REST API:

```
API_KEY=$(grep FIGMA_API_KEY .env | cut -d= -f2)
curl -s -H "X-Figma-Token: $API_KEY" \
  "https://api.figma.com/v1/files/<FILE_KEY>/nodes?ids=<NODE_IDS>&depth=6"
```

### Типографика

Страница `test typography` (id: `4154:540`) в файле `02-omg-tokens` содержит все стили. Пример запроса:

```bash
curl -s -H "X-Figma-Token: $API_KEY" \
  "https://api.figma.com/v1/files/BqHLTGHIZa8Ia7P4fm5iH2/nodes?ids=4154:540&depth=6" \
  | python3 -c "
import json, sys
data = json.load(sys.stdin)
def find_text(n, depth=0):
    if depth > 10: return
    if n.get('type') == 'TEXT' and n.get('style', {}).get('fontSize'):
        s = n['style']
        print(f\"{n['name']}: size={s.get('fontSize')} weight={s.get('fontWeight')} lh={s.get('lineHeightPx')} ls={s.get('letterSpacing')} font={s.get('fontFamily')}\")
    for ch in n.get('children', []): find_text(ch, depth+1)
for nid, nd in data['nodes'].items():
    find_text(nd['document'])
"
```

Текстовые стили хранятся **не** в `/styles` (там только FILL и EFFECT), а в дочерних узлах компонентов.

### Шрифты Omega

Omega использует два шрифта:
- **Omega UI** — основной UI-шрифт (weight 400/500). Это кастомный шрифт ВТБ.
- **Omega UI Geometric VF** — для числовых дисплеев (счётчики, суммы).
- **VTB Group UI** — legacy-шрифт, в текущем приложении используется (vtb_light/book/demi_bold/bold.ttf).

> **⚠️ Omega UI — отсутствует в проекте.** Нужно скачать и добавить в `res/font/`.
> Шрифт распространяется внутри ВТБ, скорее всего доступен через дизайн-команду.

### Извлечение палитры

Framelink MCP хорошо отдаёт палитру (см. `01-omg-palette`), но для точных hex через API:

```bash
curl -s -H "X-Figma-Token: $API_KEY" \
  "https://api.figma.com/v1/files/3QWKdMLvlrLW16IH8JEHcJ?depth=2" \
  | python3 -c "
import json, sys
data = json.load(sys.stdin)
# Цвета хранятся как fill стили
for s in data.get('meta',{}).get('styles',[]):
    if s['style_type'] == 'FILL':
        print(f\"{s['name']}\")
"
```

### Извлечение компонентов

Страница `component tokens` (id: `1:42`) в `02-omg-tokens` — спецификации кнопок, полей, карточек. Component Set node ID содержат варианты (type=Brand/Neutral, size=L/M/S, state=Normal/Disabled).

Страница `semantic palette` (id: `1:41`) — семантические цвета (bg, surface, text).

### Извлечение иконок

Иконки в `05-omg-atoms` как SVG. Скачивать через `framelink_download_figma_images`.

---

## Извлечённые данные: Типографика Omega (Mobile)

Источник 1: Figma REST API, `02-omg-tokens`, страница `test typography`.
Источник 2: Figma, `03-omg-typography` — tight/paragraph варианты, HeadlineXS/XXS.
Шрифт: **Omega UI** (weight 500 = Medium для headline, 400 = Regular для body).
Для Android: значения px → sp 1:1 (mdpi baseline).

### Концепция Tight vs Paragraph

Omega разделяет body-стили на два варианта:
- **Tight** (компактный межстрочный интервал ≈ 1.25–1.35×) — для однострочных меток, значений, чипов, кнопок
- **Paragraph** (увеличенный ≈ 1.47–1.67×) — для многострочного текста, описаний, параграфов

Каждый вариант имеет 3 веса: Normal (400), Medium (500→600), SemiBold (600).

### Мобильный масштаб — Display & Headline (Tight)

| Omega Token      | Size (sp) | Weight | Line Height (sp) | Letter Sp. | Применение                  |
|------------------|-----------|--------|-------------------|-------------|-----------------------------|
| display XL       | 48        | 500→600 | 57               | 0           | Заголовки экранов           |
| display L        | 36        | 500→600 | 45               | 0           | Hero-суммы                  |
| display M        | 32        | 500→600 | 40               | 0           | Крупные заголовки           |
| display S        | 28        | 500→600 | 35               | 0           | Средние заголовки           |
| headline XL      | 24        | 500→600 | 30               | 0           | Section заголовки           |
| headline L       | 20        | 500→600 | 27               | 0           | Sub-section заголовки       |
| headline M       | 16        | 500→600 | 21               | 0           | Малые заголовки, имена      |
| headline S       | 12        | 500→600 | 15               | 0           | Лейблы, подписи             |
| headline XS      |  9        | 500→600 | 12               | 0           | Micro-labels                |
| headline XXS     |  9        | 500→600 | 12               | 0           | Micro-labels (то же что XS) |

### Мобильный масштаб — Body Tight (≈ 1.25–1.35×)

| Omega Token              | Size (sp) | Weight | Line Height (sp) |
|--------------------------|-----------|--------|-------------------|
| bodyXL/tight/normal      | 20        | 400    | 27                |
| bodyL/tight/normal       | 16        | 400    | 21                |
| bodyM/tight/normal       | 12        | 400    | 15                |
| bodyS/tight/normal       |  9        | 400    | 12                |
| bodyXL/tight/medium      | 20        | 500→600 | 27               |
| bodyL/tight/medium       | 16        | 500→600 | 21               |
| bodyM/tight/medium       | 12        | 500→600 | 15               |
| bodyS/tight/medium       |  9        | 500→600 | 12               |
| bodyXL/tight/semiBold    | 20        | 600    | 27                |
| bodyL/tight/semiBold     | 16        | 600    | 21                |
| bodyM/tight/semiBold     | 12        | 600    | 15                |
| bodyS/tight/semiBold     |  9        | 600    | 12                |

### Мобильный масштаб — Body Paragraph (≈ 1.47–1.67×)

| Omega Token                  | Size (sp) | Weight | Line Height (sp) |
|------------------------------|-----------|--------|-------------------|
| bodyXL/paragraph/normal      | 20        | 400    | 29                |
| bodyL/paragraph/normal       | 16        | 400    | 24                |
| bodyM/paragraph/normal       | 12        | 400    | 18                |
| bodyS/paragraph/normal       |  9        | 400    | 15                |
| bodyXL/paragraph/medium     | 20        | 500→600 | 29                |
| bodyL/paragraph/medium      | 16        | 500→600 | 24                |
| bodyM/paragraph/medium      | 12        | 500→600 | 18                |
| bodyS/paragraph/medium      |  9        | 500→600 | 15                |
| bodyXL/paragraph/semiBold   | 20        | 600    | 29                |
| bodyL/paragraph/semiBold     | 16        | 600    | 24                |
| bodyM/paragraph/semiBold     | 12        | 600    | 18                |
| bodyS/paragraph/semiBold     |  9        | 600    | 15                |

### Планшетный масштаб (Tablet)

| Omega Token | Size (sp) | Weight | Line Height (sp) |
|-------------|-----------|--------|-------------------|
| display L   | 48        | 500    | 60                |
| display M   | 43        | 500    | 52                |
| display S   | 37        | 500    | 44                |
| headline XL | 32        | 500    | 40                |
| headline L  | 27        | 500    | 36                |
| headline M   | 21        | 500    | 28                |
| headline S   | 16        | 500    | 20                |
| headline XS  | 12        | 500    | 16                |
| body XL      | 27        | 400    | 36                |
| body L       | 21        | 400    | 28                |
| body M       | 16        | 400    | 20                |
| body S       | 12        | 400    | 16                |

### Маппинг Weight: Omega → VTB Group UI

| Omega Weight | Numeric | VTB Group UI Font    |
|-------------|---------|---------------------|
| Light       | 300     | vtb_light           |
| Regular/Book| 400     | vtb_book            |
| Medium      | 500     | ⚠️ нет → SemiBold(600)/vtb_demi_bold |
| SemiBold    | 600     | vtb_demi_bold       |
| Bold        | 700     | vtb_bold            |

> **Проблема:** Omega UI weight 500 (Medium) не совпадает ни с одним из имеющихся .ttf.
> Ближайший: vtb_demi_bold (600/SemiBold). Для pixel-perfect нужен файл Omega UI Medium.
> В `OmegaType.kt` Medium и SemiBold маппятся на один шрифт, но семантически разделены
> через отдельные алиасы (`BodyTightMedium*` vs `BodyTightSemiBold*`) для будущей миграции.

### Реализация в `OmegaType.kt`

Все стили реализованы в объекте `OmegaType`:
- `DisplayXL/L/M/S` — заголовки экранов
- `HeadlineXL/L/M/S/XS/XXS` — секционные заголовки, лейблы
- `BodyTight{XL,L,M,S}/{Normal,Medium,SemiBold}` — однострочный текст
- `BodyParagraph{XL,L,M,S}/{Normal,Medium,SemiBold}` — многострочный текст
- Deprecated-алиасы: `BodyXL→BodyTightXL`, `BodySemiBoldL→BodyTightSemiBoldL`, etc.
- App-specific: `PinDigit`, `OverlayInput`, `QuickChip`

Material3 маппинг (`OmegaTypography`):
- `bodyLarge/Medium/Small` → Paragraph варианты (многострочный текст)
- `titleLarge/Medium/Small` → Tight SemiBold варианты (заголовки, кнопки)
- `labelLarge/Medium/Small` → Tight Normal/Medium варианты (лейблы, чипы)

---

## Извлечённые данные: Палитра Omega

Источник: `framelink_get_figma_data(fileKey="3QWKdMLvlrLW16IH8JEHcJ")` + Figma REST API.
Полная палитра реализована в `ui/theme/OmegaColor.kt`.

### Ключевые семантические цвета (тёмная тема)

Источник: Figma REST API, `01-omg-palette` (palette page, background rectangle) + `02-omg-tokens` (constant/inverted page).
Верифицировано 19.04.2026 напрямую через API.

| Семантика         | Токен Omega              | Hex       | Примечание |
|-------------------|--------------------------|-----------|------------|
| **Background**    | spaceGraphite/1001       | `#101114` | ⚠️ ранее ошибочно документировался как spaceGraphite/600 (#1E2024) |
| Surface            | titanGray/1001           | `#22252B` | bottom sheet, sheet scaffold |
| Surface alt        | titanGray/800            | `#2F343C` | карточки внутри sheet (требует уточнения) |
| Chip/outline       | titanGray/700            | `#3F4650` | чипы, теги |
| Brand primary      | omegaBlue/600            | `#0160EC` | основной brand-синий |
| Brand dark         | omegaBlue/900            | `#00358A` | глубокий синий |
| Brand deep         | omegaBlue/1000           | `#001644` | самый тёмный синий |
| Text primary       | light/text/constant      | `#FFFFFF` | заголовки, суммы |
| Text secondary     | titanGray/400            | `#7C8798` | лейблы, подписи |
| Text hint          | titanGray/500            | `#677283` | placeholder |
| Success            | callistianGreen/200      | `#4ED52F` | успех |
| Error              | martianRed/500           | `#E6163E` | ошибка |
| Warning            | venusOrange/500          | `#C45D02` | предупреждение |

> **Примечание по нумерации titanGray в Figma:**
> Figma называет шаги нестандартно: titanGray/1001=#22252B, titanGray/900=#1B1E22, titanGray/1000=#16191D.
> В нашем OmegaColor.kt используется собственная нумерация: v900=#22252B, v950=#1B1E22, v1000=#16191D — значения совпадают, только названия шагов разные.

---

## Извлечение размерностей (spacing, border-radius, elevation)

Источник: страница `component tokens` (id: `1:42`) в `02-omg-tokens`.

Omega использует базовую единицу **1xBase = 4dp**:

### Border Radius

Источник: Figma `02-omg-tokens`, radii demo page + component specs (button, card).

| Omega Token | Значение  | Compose                         | Применение (из Figma) |
|-------------|-----------|---------------------------------|-----------------------|
| 0x          | 0dp       | `RoundedCornerShape(0.dp)`      | — |
| 2x / tiny   | 8dp       | `RoundedCornerShape(8.dp)`      | мелкие элементы |
| 3x / small  | 12dp      | `RoundedCornerShape(12.dp)`     | **кнопки (metaButton r=12)**, chip cards |
| 4x / medium | 16dp      | `RoundedCornerShape(16.dp)`     | **карточки (card r=16)** |
| 6x / large  | 24dp      | `RoundedCornerShape(24.dp)`     | bottom sheet top corners |
| infinity    | full/pill | `RoundedCornerShape(50%)`       | аватары, pill-чипы |

> **⚠️ Ранее задокументировано неверно:** кнопки использовали pill (28dp). По Figma button component: `metaButton r=12dp`.

### Shadows (Elevation)

| Omega Token | Shadow                                                          |
|-------------|-----------------------------------------------------------------|
| xxs         | minimal shadow                                                  |
| xs          | small shadow                                                    |
| sm          | standard card shadow                                             |
| md          | elevated card shadow                                             |
| lg          | modal/bottom sheet shadow                                        |

(Точные dp-значения shadow извлечены ниже.)

### Анализ хардкод-размерностей в текущем коде

Проведён полный grep всех `.dp` значений в `ui/` и Activity-файлах. Ниже — частотный анализ и маппинг на Omega-токены.

#### Самые частые значения (по убыванию встречаемости)

| Значение | Частота | Контекст | Omega Token |
|----------|---------|----------|-------------|
| 16dp | ~30+ | Горизонтальные отступы, padding карточек | `OmegaSpacing.lg` |
| 8dp | ~20+ | Иконки gap, мелкие спейсеры | `OmegaSpacing.sm` |
| 12dp | ~15+ | Section spacing, card padding | `OmegaSpacing.md` |
| 4dp | ~15+ | Микро-gap, handle bar | `OmegaSpacing.xs` |
| 24dp | ~8+ | Sheet corners, header padding | `OmegaSpacing.xxl` |
| 20dp | ~8+ | Icon sizes, action rows | `OmegaSpacing.xl` |
| 2dp | ~5+ | Stroke/divider | `OmegaSpacing.xxs` |
| 1dp | ~5+ | Card elevation, border | `OmegaElevation.xs` / `OmegaStroke.thin` |
| 44dp | ~4 | Avatar sizes | `OmegaSize.avatarMd` |
| 56dp | ~3 | Button heights | `OmegaSize.buttonHeight` |
| 40dp | ~3 | Large spacers, buttons | `OmegaSize.avatarSm` |
| 0.5dp | 1 | Divider thickness | `OmegaStroke.hairline` |

#### Маппинг Border Radius

| Текущее значение | Частота | Контекст | Omega Token |
|------------------|---------|----------|-------------|
| 16dp | ~8 | InfoCard, PersonaCard, AccountCard, actions | `OmegaRadius.lg` |
| 12dp | ~5 | TextField, WarningCard, PayCategory, Tx card | `OmegaRadius.md` |
| 24dp | ~6 | Sheet top corners, chat input | `OmegaRadius.xxl` |
| 20dp | ~4 | BankChip, QuickAmountChip, PIN overlay | `OmegaRadius.xl` |
| 28dp | 1 | GradientButton (pill) | `OmegaRadius.pill` |
| 8dp | ~2 | PIN hint, small buttons | `OmegaRadius.sm` |
| 4dp | ~2 | "?" fallback, badge | `OmegaRadius.xs` |
| CircleShape | ~10 | Avatars, round buttons, PIN dots | `OmegaRadius.full` |
| 40dp | 1 | Success icon circle | (app-specific) |
| 14dp | 1 | Balance card | (app-specific, близко к 12=md) |
| 32dp | 1 | Widget corners | `OmegaRadius.xxxl` |
| 36dp | 1 | Sheet handle width | (app-specific) |
| 13dp | 1 | Check badge radius | (app-specific, близко к 12=md) |
| 18dp | 1 | QuickChip radius | (app-specific, близко к 20=xl) |
| 22dp | 2 | Text-mode buttons | (app-specific, ~pill) |

#### Маппинг Elevation

| Текущее значение | Контекст | Omega Token |
|------------------|----------|-------------|
| 1dp | Card, PayCategory, Tx card | `OmegaElevation.xs` |
| 2dp | AccountCard | `OmegaElevation.sm` |
| 0dp | Flat chat bubble | `OmegaElevation.none` |

#### Маппинг Stroke

| Текущее значение | Контекст | Omega Token |
|------------------|----------|-------------|
| 0.5dp | Divider thickness | `OmegaStroke.hairline` |
| 1dp | Chip border, card border | `OmegaStroke.thin` |
| 1.5dp | Ripple ring width | `OmegaStroke.medium` |
| 2dp | Progress indicator | `OmegaStroke.regular` |

#### Маппинг Icon/Element Sizes

| Текущее значение | Контекст | Omega Token |
|------------------|----------|-------------|
| 16dp | Small icons | `OmegaSize.iconXs` |
| 18dp | Search, mic icons | `OmegaSize.iconSm` |
| 20dp | Standard icons | `OmegaSize.iconMd` |
| 22dp | Category/product icons | `OmegaSize.iconLg` |
| 24dp | Fingerprint, nav icons | (close to `iconLg`) |
| 28dp | Back button icon | `OmegaSize.iconXl` |
| 36dp | Handle bar width | (app-specific) |
| 40dp | Small avatar | `OmegaSize.avatarSm` |
| 44dp | Contact avatar | `OmegaSize.avatarMd` |
| 48dp | Persona avatar | `OmegaSize.avatarLg` |
| 56dp | CTA button height | `OmegaSize.buttonHeight` |
| 64dp | Input pill height | `OmegaSize.pillHeight` |
| 72dp | PIN key size | `OmegaSize.pinKeySize` |
| 80dp | Success icon | `OmegaSize.successIcon` |
