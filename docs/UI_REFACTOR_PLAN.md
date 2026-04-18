# UI Refactor Plan — VTB Vita → VTB Design

**Цель:** Привести визуал прототипа к стилистике ВТБ Онлайн  
**Дедлайн:** 19 апреля 2026  
**Источник:** `docs/DESIGN_GUIDE.md` (анализ скриншотов приложения ВТБ)

---

## Принципы

- Транзакционные экраны (шиты, флоу) → **тёмная тема** как в ВТБ
- Виджет + InputActivity → **градиент** (синий→фиолетовый→тёмно-синий, фирменный ВТБ)
- Градиент используется стратегически, не везде
- Никаких светлых фонов на транзакционных экранах

---

## PR 1 — Foundation (тема и цвета)

**Статус:** `[x] done`  
**Файлы:** `Color.kt`, `Theme.kt`, `colors.xml`, `themes.xml`, `build.gradle.kts`, `libs.versions.toml`

### Задачи
- [x] `Color.kt`: добавить тёмную палитру (`DarkBg`, `DarkSurface`, `DarkSurfaceAlt`, `DarkChip`)
- [x] `Color.kt`: добавить VTB-градиент главного экрана как `Brush`-константы (`VtbHomeGradient`, `VtbHomeGradientH`)
- [x] `Color.kt`: legacy-алиасы сохранены для обратной совместимости (не ломают остальные файлы)
- [x] `Theme.kt`: `lightColorScheme` → `darkColorScheme`, переназначены все роли
- [x] `colors.xml`: добавлены тёмные цвета + gradient start/mid/end для XML-разметки
- [x] `themes.xml`: `Theme.Material3.Dark.NoActionBar` + statusBar/navBar тёмные
- [x] `libs.versions.toml` + `build.gradle.kts`: добавлена зависимость `com.google.android.material:material:1.12.0`
- [x] `BUILD SUCCESSFUL` — clean build прошёл без ошибок

### Ожидаемый результат
Все Compose-экраны сразу становятся тёмными. Появятся артефакты (цветные кнопки, белые карточки) — исправляются в PR 2.

---

## PR 2 — Компоненты

**Статус:** `[x] done`  
**Файлы:** `VitaComponents.kt`

### Задачи
- [x] `GradientButton`: добавлен `VtbButtonStyle` (White / Gradient), высота 56dp, SemiBold текст
- [x] `BankCarousel / BankCard → BankChip`: редизайн — тёмный pill `#2A2A2A`, активный = белый border 1dp, без тени/elevation
- [x] `SheetGradientHeader → VtbSheetHeader`: тёмный header `#1A1A1A`, серый handle-bar `#3A3A3A`, белый Bold заголовок; старый оставлен как `@Deprecated`-обёртка
- [x] Новый `VtbInfoCard`: Caption(серый) → value(белый), фон `#1A1A1A`, radius 16dp, опциональный trailing и onClick
- [x] Новый `VtbTextField`: `BasicTextField` без outline, фон `#1A1A1A`, radius 12dp, серый placeholder
- [x] Новый `VtbQuickAmountChip`: тёмный pill `#2A2A2A`, активный = border + SemiBold
- [x] Новый `VtbWarningCard`: коричневый фон `#3D2010`, radius 12dp (для экрана подтверждения)
- [x] Новый `VtbSuccessIcon`: синий круг + зелёный чекмарк (для Success Screen)
- [x] `BUILD SUCCESSFUL` — компиляция чистая

---

## PR 3 — Экраны транзакций

**Статус:** `[x] done`  
**Файлы:** `TransferDetailsContent.kt`, `TransferFlowActivity.kt`, `TopupInputActivity.kt`, `ConfirmActivity.kt`, `ContactPickerActivity.kt`, `ContactDisambiguationActivity.kt`

### TransferDetailsSheet ✓
- [x] `OutlinedTextField` → `VtbTextField`, `TransferDetailRow` → `VtbInfoCard`
- [x] Добавлены chips быстрых сумм (+500, +1000, +2000, +5000)
- [x] Кнопка «Перевести» → `GradientButton(style=White)` full-width + «Отмена» текстом
- [x] `TransferAccountDropdown` → `VtbInfoCard` с `▼` и тапом-переключением
- [x] Фон шита: `DarkSurface`, кнопка прибита к bottom

### ContactSelectionSheet ✓
- [x] `Card(white)` → `Column(DarkSurface)`
- [x] `OutlinedTextField` → `VtbTextField`
- [x] Аватары: `DarkChip` фон, белый текст (убраны `VtbBluePale` / `VtbBlue`)
- [x] Убраны `HorizontalDivider` — только padding
- [x] `VtbSheetHeader` вместо ручного handle-bar

### TopupSheet ✓
- [x] `OutlinedTextField` → `VtbTextField`
- [x] `FilterChip` → `VtbQuickAmountChip`
- [x] Оператор вынесен в `VtbInfoCard`
- [x] «Оплатить ₽» → `GradientButton(style=White)` full-width + «Отмена» текстом
- [x] «Комиссия 0 ₽» зелёным trailing в поле суммы

### ConfirmActivity ✓
- [x] `Card(white)` → `Column(DarkSurface)`
- [x] `DetailRow` → `VtbInfoCard`, `SheetGradientHeader` → `VtbSheetHeader`
- [x] Добавлен `VtbWarningCard` для переводов
- [x] `AccountDropdown(OutlinedTextField)` → `VtbInfoCard` с тапом
- [x] `OutlinedButton` → текстовая ссылка «Отмена»

### ContactPickerActivity ✓
- [x] `Card(white)` → `Column(DarkSurface)`, `VtbSheetHeader`
- [x] `OutlinedTextField` → `VtbTextField`
- [x] `OutlinedButton` → `GradientButton(style=White)`, аватары → `DarkChip`
- [x] Убраны `HorizontalDivider`

### ContactDisambiguationActivity ✓
- [x] `Card(white)` → `Column(DarkSurface)`, `VtbSheetHeader`
- [x] Аватары → `DarkChip`, `HorizontalDivider` убраны
- [x] `OutlinedButton "Другой получатель"` → `GradientButton(style=White)`
- [x] `BUILD SUCCESSFUL` — компиляция чистая

---

## PR 4 — Success Screen + виджет-градиент

**Статус:** `[x] done`  
**Файлы:** `VitaSuccessScreen.kt` (новый), `TransferFlowActivity.kt`, `TopupInputActivity.kt`, `widget_bg.xml`, `widget_bg_dark.xml` (новый)

### Success Screen ✓
- [x] Новый `VitaSuccessScreen.kt` — полноэкранный composable
  - `VtbSuccessIcon` (синий круг + зелёный чекмарк) со scale-in анимацией
  - Заголовок Bold, серый subtitle, Hero-сумма 36sp
  - Список действий в `VtbInfoCard`-стиле (`DarkSurface`, radius 16dp)
  - «На главную» — `GradientButton(style=White)` прибит к bottom
- [x] `TransferFlowActivity`: добавлен `Screen.Success`, back заблокирован на success
- [x] `TopupInputActivity`: добавлен `successData` state, `onSuccess` теперь передаёт сумму

### Виджет ✓
- [x] `widget_bg.xml` → трёхцветный VTB-градиент (синий `#1A3A8C` → фиолетовый `#2D1B69` → тёмно-синий `#0D1B4B`, 135°)
- [x] `widget_bg_dark.xml` — новый тёмный вариант `DarkSurface #1A1A1A`
- [x] `BUILD SUCCESSFUL` — полный assembleDebug прошёл

---

## Карта цветов (Dark Theme)

| Константа | HEX | Роль |
|-----------|-----|------|
| `DarkBg` | `#0D0D0D` | Основной фон |
| `DarkSurface` | `#1A1A1A` | Карточки, шиты, поля |
| `DarkSurfaceAlt` | `#222222` | Bottom Sheet |
| `DarkChip` | `#2A2A2A` | Chips, аватары |
| `TextPrimary` | `#FFFFFF` | Заголовки, суммы |
| `TextSecondary` | `#8A8A8A` | Лейблы, подписи |
| `TextHint` | `#5C5C5C` | Placeholder |
| `AccentBlue` | `#4A7FFF` | Ссылки, акценты |
| `AccentGreen` | `#34C759` | Успех, комиссия 0₽ |
| `VtbBrand` | `#0061D5` | Лого, Мир-карта |
| `WarningBg` | `#3D2010` | Предупреждение |

## Градиент ВТБ (главный экран)

```kotlin
val VtbHomeGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF1A3A8C),  // синий
        Color(0xFF2D1B69),  // фиолетовый
        Color(0xFF0D1B4B),  // тёмно-синий
    )
)
```
