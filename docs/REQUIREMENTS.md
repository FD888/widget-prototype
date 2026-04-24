# Требования к прототипу VTB Vita

---

## Функциональные требования (MVP Phase 1)

### FR-01 — Виджет на главном экране
- Виджет добавляется на главный экран Android стандартным способом (AppWidget)
- Форма: горизонтальная капсула (4×1 ячейки, высота 64dp), синий градиент, cornerRadius 32dp
- Содержит: контекстный текст-промпт слева (кликабелен) + кнопка микрофона справа
- В состоянии «нет сессии» — текст «Войдите в аккаунт», оба элемента ведут в MainActivity
- Пока открыт InputActivity — виджет скрывается (INVISIBLE), восстанавливается в onPause

### FR-02 — Ввод команды (InputActivity)
- Открывается как прозрачный overlay поверх лончера (не во весь экран)
- **Текстовый режим (TEXT):** поле ввода + чипы быстрых действий (Перевод / Баланс / Пополнить)
- **Голосовой режим (RECORDING):** визуализация амплитуды (waveform), таймер записи, кнопки «Отмена» / «Готово»
- Режим определяется EXTRA_MODE при запуске: `"text"` / `"recording"`
- При нажатии на микрофон в виджете → InputActivity сразу в режиме RECORDING

### FR-03 — NLP-распознавание
- Распознаёт банковские intent'ы: `transfer`, `balance`, `topup`, `pay_scheduled`
- Для `transfer` — извлекает имя получателя, сумму и опциональный комментарий («за пиццу»)
- Для `topup` — определяет номер телефона
- Для `pay_scheduled` — ID планового платежа из подсказки
- Парсер двухуровневый: L1 regex → L2 LLM cascade (DeepSeek прямой → OpenRouter → unknown)
- Нераспознанные интенты标记 `bot_redirect=true` с оригинальным текстом — для передачи в чат-бот банка
- Mock API (`ml/mock_api/`) + NLP-ядро Яны (`ml/`, C-02)

### FR-04 — Флоу «Перевод»
- Шаг 1: ContactPickerActivity — поиск по реальной телефонной книге (ContactsContract), аватар с инициалами
- Шаг 2: TransferDetailsActivity — получатель зафиксирован, редактируемая сумма, выбор банка (5 вариантов), выбор счёта, опциональный комментарий
- Отображает «Баланс после перевода» в реальном времени
- Подтверждение → Mock API `/confirm` → `VitaWidgetProvider.showStatus()` на 10 сек

### FR-05 — Флоу «Пополнение телефона»
- TopupInputActivity: поле номера телефона + автоопределение оператора по префиксу (МТС/МегаФон/Билайн/Tele2)
- Чипы быстрых сумм: 100 / 200 / 300 / 500 ₽
- Подтверждение → showStatus()

### FR-06 — Флоу «Баланс»
- BalanceActivity: запрос к Mock API `/balance` → отображение суммы и счёта в BottomSheet

### FR-07 — Системные интенты
- Виджет принимает небанковские команды через SystemIntentHandler:
  - «Поставь будильник на 7 утра» → `AlarmClock.ACTION_SET_ALARM`
  - «Таймер на 10 минут» → `AlarmClock.ACTION_SET_TIMER`
  - «Позвони маме» → `ACTION_DIAL` (из контактов или из текста)
  - «Открой YouTube / Spotify / Яндекс карты / …» → `getLaunchIntentForPackage`

### FR-08 — Управление сессией
- MainActivity показывает список профилей (PERSONAS): Denis (доступен), Masha / Yana (скоро)
- PinEntryActivity: 4-значный PIN, визуализация точек, shake при ошибке, подсказка PIN для демо
- После успешного входа: SessionManager.login() → обновление виджета → MockBankActivity
- SessionManager (SharedPreferences): login / logout / isLoggedIn / currentPersona
- Состояние сохраняется между перезапусками приложения

### FR-09 — Mock банковское приложение (MockBankActivity)
- Отображает скриншоты банковского приложения (screen_main, screen_payments, screen_products, screen_history, screen_chat)
- Кликабельный таббар снизу переключает между вкладками
- Баннер «VTB Vita · демо-режим» сверху + кнопка выхода (иконка ExitToApp)
- При выходе: SessionManager.logout() → виджет «Войдите в аккаунт» → MainActivity

### FR-10 — Mock-данные
- История переводов: минимум 5 контактов (имена, карты, суммы)
- Баланс: возвращается из data.py (seed-db через SQLite)
- Все данные вымышленные

### FR-11 — Персонализированные подсказки (hint system)
- GET /hint возвращает одну подсказку: reminder / vygoda / none
- Напоминания: таблица scheduled_payments с payment_type (credit_card/loan/autopayment/subscription)
- Офферы ВЫГОДА: расчёт на лету по сегменту, балансу, истории трат, активным продуктам
- Override через /dashboard/hints: менеджер может форсировать тип подсказки и текст
- На виджете — нейтральная фраза из локального пула (NeutralHints.kt)
- В InputActivity — баннер НАПОМИНАНИЕ или ВЫГОДА

### FR-12 — Аналитический дашборд
- GET /dashboard — HTML-дашборд с Chart.js (Basic Auth: vita/vtb2026)
- GET /dashboard/stats — JSON-эндпоинт с KPI, воронкой, интентами, таблицей транзакций
- GET /dashboard/hints — страница управления подсказками (CRUD hint_overrides)
- Данные: intent_log (каждый /parse логируется) + transactions + users

### FR-13 — LLM cascade (NLP)
- L1: regex-парсер (мгновенный, ~5мс)
- L2: DeepSeek API (прямой) → OpenRouter (deepseek/deepseek-chat) → unknown
- bot_redirect=true для нераспознанных — передача в чат-бот банка
- comment field: «переведи Кате 500 за пиццу» → comment="пиццу"

---

## Нефункциональные требования

### NFR-01 — Платформа
- Android минимум API 26 (Android 8.0)
- Целевой API: 34 (Android 14)

### NFR-02 — Разрешения
- `INTERNET` — обращение к Mock API
- `READ_CONTACTS` — поиск получателей в ContactPickerActivity
- `RECORD_AUDIO` — голосовой ввод (MediaRecorder)

### NFR-03 — Время отклика
- Команда → модал: не более 3 секунд (включая запрос к Mock API)
- Если Mock API недоступен — graceful fallback (ошибка в Toast, чипы остаются активны)

### NFR-04 — Публичный доступ (C-05)
- APK доступен для скачивания по публичной ссылке, ИЛИ
- README позволяет собрать прототип с нуля

### NFR-05 — Воспроизводимость
- Mock API запускается за 3 команды (см. README)
- Android-приложение собирается командой `./gradlew assembleDebug`
- USB-тоннель: `adb reverse tcp:8000 tcp:8000`

---

## Out of scope (не реализуем в прототипе)

- Реальные банковские API и реальные транзакции
- iOS-версия
- Push-уведомления (FCM)
- Сложные операции: инвестиции, кредиты, конвертация
- Отмена транзакции после подтверждения
- Голосовое распознавание (STT) — запись есть, распознавание в Phase 2

---

## Критерии приёмки Phase 1 (C-05 закрыт когда)

- [x] Виджет устанавливается на реальный Android-телефон
- [x] Виджет показывает персонализированный промпт после входа
- [x] Текстовый ввод → чип «Перевод» → выбор контакта → сумма → подтверждение → статус в виджете
- [x] Чип «Баланс» → модал с балансом
- [x] Чип «Пополнить» → TopupInputActivity → статус
- [x] Голосовой режим: waveform + таймер
- [x] Системные команды: будильник, таймер, открыть приложение, позвонить
- [x] Вход / выход из профиля, состояние виджета меняется
- [ ] NLP-сервис доступен по публичному URL (C-05)
- [ ] README позволяет воспроизвести демо с нуля

## Дополнительные критерии (после C-05)

- [x] Персонализированные подсказки: GET /hint (reminder/vygoda/none)
- [x] Dashboard аналитики: /dashboard (Basic Auth)
- [x] Dashboard подсказок: /dashboard/hints (CRUD hint_overrides)
- [x] LLM cascade: DeepSeek → OpenRouter → unknown
- [x] bot_redirect для неизвестных интентов
- [x] comment в переводах: «за пиццу»
- [x] pay_scheduled: оплата плановых платежей из подсказки
