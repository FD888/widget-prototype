# План переделки фронтенда под дизайн-систему Omega ВТБ

## Контекст

Сейчас UI виджета использует самодельные цвета (`DarkBg`, `VtbBlue`, `TextPrimary`), approximate типографику и хардкод. Дизайн-система Omega (Figma) предоставляет точную палитру, токены, типографику и компоненты. Задача — полностью привести фронтенд в соответствие с Omega, чтобы приложение визуально совпадало с ВТБ Онлайн.

Подход: **снизу вверх** — сначала фундамент (цвета/токены), затем компоненты, затем экраны. Так экраны автоматически подхватят новые стили через тему.

---

## Шаг 1 — OmegaColor.kt: палитра Omega

**Что:** Создать `ui/theme/OmegaColor.kt` с полной палитрой Omega из Figma (01-omg-palette).

**Почему:** Все текущие цвета (`DarkBg`, `DarkSurface`, `VtbBlue`, `AccentGreen` и т.д.) — приблизительные. Omega даёт точные hex-значения в виде масштабов (omegaBlue/50–1000, titanGray/0–1000, spaceGraphite/0–900 и т.д.). Нам нужно:
- Перенести все шкалы цветов
- Определить семантические маппинги для тёмной темы (background → `spaceGraphite/600`, surface → `titanGray/900`, primary → `omegaBlue/600` и т.д.)
- Заменить хардкод `Color(0xFF...)` во всех файлах на семантические токены

**Ключевые маппинги тёмной темы:**
| Семантика | Токен | Hex |
|-----------|-------|-----|
| Background | `spaceGraphite/600` | `#1E2024` |
| Surface | `titanGray/900` | `#22252B` |
| Surface variant | `titanGray/800` | `#2F343C` |
| Chip/outline | `titanGray/700` | `#3F4650` |
| Brand primary | `omegaBlue/600` | `#0160EC` |
| Brand deep | `omegaBlue/900` | `#00358A` |
| Text primary | `constantPrimary` | `#FFFFFF` |
| Text secondary | `titanGray/400` | `#7C8798` |
| Text hint | `titanGray/500` | `#677283` |
| Success | `callistianGreen/200` | `#4ED52F` |
| Error | `martianRed/500` | `#E6163E` |
| Warning | `venusOrange/500` | `#C45D02` |

---

## Шаг 2 — OmegaType.kt: типографика Omega

**Что:** Обновить `ui/theme/Type.kt` → переименовать в `OmegaType.kt`. Перенести стили типографики из Omega (02-omg-tokens).

**Почему:** Шрифты `vtb_light/book/demi_bold/bold` уже подключены — это правильный VTB Group UI. Но размеры/веса взяты «на глаз». Omega даёт чёткие стили: Headline L/M/S, Body XL/L/M/S, Caption. Нужно маппнуть их на Material `Typography` с точными значениями из Figma.

---

## Шаг 3 — OmegaDimensions.kt: spacing, border-radius, elevation

**Что:** Создать `ui/theme/OmegaDimensions.kt` с точными размерностями из Omega.

**Почему:** Сейчас `RoundedCornerShape(12.dp)`, `RoundedCornerShape(16.dp)`, `RoundedCornerShape(20.dp)` и padding-значения разбросаны по файлам. Omega задаёт систему через базовую единицу (4dp = 1xBase). Border-radius: 0x, 2x(8dp), 3x(12dp), 4x(16dp) и т.д. Централизация убирает магические числа.

**Пример маппинга border-radius:**
| Omega | Значение |
|-------|----------|
| 0x | 0dp |
| 2x | 8dp |
| 3x | 12dp |
| 4x | 16dp |
| infinity | full (пилл) |

---

## Шаг 4 — Theme.kt: обновить VTBVitaTheme

**Что:** Переписать `VTBVitaTheme` для использования `OmegaColor`, `OmegaType`, `OmegaDimensions`.

**Почему:** Сейчас `darkColorScheme()` собирается из устаревших цветов. Новая тема должна использовать семантические токены из OmegaColor, что автоматически обновит все экраны, использующие `MaterialTheme.colorScheme.*`.

---

## Шаг 5 — OmegaComponents.kt: переописать компоненты

**Что:** Переписать `VitaComponents.kt` → `OmegaComponents.kt`. Каждый компонент (кнопки, поля, карточки, chips) описать по спецификации из 07-base-components.

**Почему:** Текущие компоненты (`GradientButton`, `VtbInfoCard`, `VtbTextField`, `VtbQuickAmountChip`, `VtbSheetHeader`, `VtbWarningCard`, `VtbSuccessIcon`) — самописные, с approximate размерами и цветами. Omega даёт точные спецификации: цвета, padding, border-radius, типографика для каждого состояния.

**Компоненты для переописания:**
- `OmegaButton` (Type=Brand/Neutral, Size=L/M/S — из Omega Buttons)
- `OmegaTextField` (из Omega Input/Text Field)
- `OmegaInfoCard` (из Omega Cell/ListItem)
- `OmegaChip` / `OmegaQuickAmountChip` (из Omega Chips)
- `OmegaSheetHeader` (из Omega Header/Navigation Bar)
- `OmegaWarningCard` (из Omega Alert/Informer)
- `OmegaSuccessIcon`
- `OmegaBankCarousel` → `OmegaChipsRow`

---

## Шаг 6 — Иконки

**Статус:** ✅ Завершён

23 SVG-иконки скачаны из `04-omg-icons` через Figma REST API и конвертированы в Android VectorDrawable XML с `fillColor="#FFFFFFFF"` (белый для тёмной темы). Иконки: arrow_up, arrow_down, close, check, mic, phone, search, fingerprint, exit, cart, wallet, home, train, bus, globe, card_arrow_right, card_line, vtb_check, vtb_arrow_right, repeat, chart_zigzag, shield_fingerprint, magnifier + outline-версии.

Все экраны мигрированы с `Icons.Default.*` / `Icons.AutoMirrored.*` на `painterResource(R.drawable.ic_*)`.

---

## Шаг 7 — Переписать Activity экраны

**Статус:** ✅ Завершён

Все 13 Activity/компонентов мигрированы:
- BalanceActivity, ConfirmActivity, TransferDetailsContent, VitaSuccessScreen →deprecated-обёртка на OmegaSuccessScreen
- PhoneVerificationActivity, PinEntryActivity, InputActivity, MainActivity
- MockBankActivity, TopupInputActivity, ContactDisambiguationActivity, ContactPickerActivity
- TransferFlowActivity, AuroraEffect (оставлен как есть — AGSL-шейдер)

Замены: `VtbBlue`→`OmegaBrandPrimary`, `DarkSurface`→`OmegaSurface`, `TextPrimary`→`OmegaTextPrimary`, `Icons.Default.*`→`painterResource(R.drawable.ic_*)`, `GradientButton`→`OmegaButton`, `VtbSheetHeader`→`OmegaSheetHeader`, и т.д.

---

## Шаг 8 — Cleanup: удалить старые файлы и импорты

**Статус:** ✅ Завершён

- ✅ `Color.kt` удалён — все цвета переехали в `OmegaColor.kt`
- ✅ `VitaComponents.kt` удалён — все компоненты переехали в `OmegaComponents.kt`
- ✅ `VitaSuccessScreen.kt` удалён — `SuccessAction` перенесён в `OmegaComponents.kt`, вызовы обновлены на `OmegaSuccessScreen`
- ✅ `Type.kt` удалён — deprecated-обёртка, делегировавшая к `OmegaTypography`
- ✅ Все импорты обновлены (старые не используются)

---

## Шаг 9 — (Опционально) Светлая тема

**Что:** Добавить `lightColorScheme()` на основе Omega светлых токенов (titanGray/0 для background, omegaBlue/600 для primary, и т.д.).

**Почему:** Omega поддерживает обе темы. Если останется время — добавим. Не критично для демо-защиты.

---

## Файлы, которые будут затронуты

### Новые:
- `ui/theme/OmegaColor.kt`
- `ui/theme/OmegaType.kt` (замена Type.kt)
- `ui/theme/OmegaDimensions.kt`
- `ui/components/OmegaComponents.kt`

### Изменённые:
- `ui/theme/Theme.kt`
- Все Activity-экраны (9 файлов)
- `ui/effects/AuroraEffect.kt` (обновить цвета на Omega)
- `VitaWidgetProvider.kt` (обновить RemoteViews-цвета если нужно)

### Удалённые:
- `ui/theme/Color.kt` (после шага 8)
- `ui/components/VitaComponents.kt` (после шага 8)

---

## Зависимости между шагами

```
Шаг 1 (OmegaColor)
  └→ Шаг 2 (OmegaType)
      └→ Шаг 3 (OmegaDimensions)
          └→ Шаг 4 (Theme.kt)
              └→ Шаг 5 (OmegaComponents)
                  └→ Шаг 6 (Иконки)
                      └→ Шаг 7 (Экраны)
                          └→ Шаг 8 (Cleanup)
                              └→ Шаг 9 (Light theme, опционально)
```

Каждый шаг зависит от предыдущего. Шаги 1-4 — фундамент (можно за 1 итерацию). Шаг 5 — самые объёмный. Шаги 6-7 — итеративные, можно делать по экрану.

---

## Статус

- [x] Шаг 1 — OmegaColor.kt ✓
- [x] Шаг 2 — OmegaType.kt ✓ (обновлено: tight/paragraph вариантность, HeadlineXS/XXS, Medium/SemiBold разделение)
- [x] Шаг 3 — OmegaDimensions.kt ✓
- [x] Шаг 4 — Theme.kt ✓
- [x] Шаг 5 — OmegaComponents.kt ✓
- [x] Шаг 6 — Иконки ✓ (23 иконки из Omega, Material Icons заменены)
- [x] Шаг 7 — Activity экраны ✓ (все 13 файлов мигрированы)
- [x] Шаг 8 — Cleanup ✓ (Color.kt, VitaComponents.kt, VitaSuccessScreen.kt, Type.kt удалены)
- [ ] Шаг 9 — Светлая тема (опционально)