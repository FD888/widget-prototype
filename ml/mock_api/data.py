"""
Mock bank data — симуляция серверной базы данных ВТБ.

Структура основана на Open Banking UK v3.1 + ISO 20022 + ЦБ РФ ФАПИ.
Данные полностью вымышленные, для демонстрации прототипа.

Пользователи:
  denis   — Молодой специалист, 24 года, скоринг 680
  (остальные добавляются после ревью детализации)
"""

from datetime import date

# ---------------------------------------------------------------------------
# MCC коды (Merchant Category Codes) — ISO 18245
# ---------------------------------------------------------------------------
MCC = {
    "5411": "Супермаркеты",
    "5812": "Кафе и рестораны",
    "5814": "Фастфуд",
    "5541": "АЗС",
    "5651": "Одежда и обувь",
    "5912": "Аптеки",
    "5815": "Цифровые сервисы / подписки",
    "4111": "Транспорт",
    "7941": "Спорт и фитнес",
    "5999": "Разное",
    "6011": "Снятие наличных",
    "5310": "Магазины электроники",
    "7011": "Отели",
    "4411": "Такси / каршеринг",
    "5047": "Медицина",
}

# ---------------------------------------------------------------------------
# Пул рекомендаций (сервер выбирает на основе скоринга и истории)
# ---------------------------------------------------------------------------
RECOMMENDATIONS = {
    # Для молодых специалистов с активными тратами
    "save_leftovers": {
        "id": "save_leftovers",
        "text": "Перевести остаток на накопительный — +8% годовых",
        "detail": "У тебя обычно остаётся ~12 000 ₽ к концу месяца",
        "cta": "Перевести",
        "action": "transfer_to_savings",
        "segment": ["young_professional"],
        "min_score": 600,
    },
    "optimize_subscriptions": {
        "id": "optimize_subscriptions",
        "text": "3 подписки на сумму 1 490 ₽/мес — есть дубли",
        "detail": "Яндекс.Плюс и отдельный Кинопоиск пересекаются",
        "cta": "Посмотреть",
        "action": "review_subscriptions",
        "segment": ["young_professional", "student"],
        "min_score": 0,
    },
    "cashback_boost": {
        "id": "cashback_boost",
        "text": "Подключи кэшбэк на супермаркеты — ты тратишь там 8 000 ₽/мес",
        "detail": "Категория «Продукты» у тебя топ-1 по расходам",
        "cta": "Подключить",
        "action": "enable_cashback_groceries",
        "segment": ["young_professional", "family"],
        "min_score": 500,
    },
    "deposit_offer": {
        "id": "deposit_offer",
        "text": "Открой вклад на 3 месяца — ставка 18% годовых",
        "detail": "Подходит для суммы от 50 000 ₽",
        "cta": "Открыть",
        "action": "open_deposit",
        "segment": ["investor", "family"],
        "min_score": 700,
    },
    "credit_limit_increase": {
        "id": "credit_limit_increase",
        "text": "Можем увеличить кредитный лимит до 100 000 ₽",
        "detail": "Хорошая кредитная история за 8 месяцев",
        "cta": "Подробнее",
        "action": "increase_credit_limit",
        "segment": ["young_professional"],
        "min_score": 650,
    },
}

# ---------------------------------------------------------------------------
# Пул баннеров (персонализированные продуктовые предложения)
# ---------------------------------------------------------------------------
BANNERS = {
    "deposit_18": {
        "id": "deposit_18",
        "title": "Вклад «Выгодный»",
        "subtitle": "До 18% годовых · от 10 000 ₽ · 3 месяца",
        "action": "open_deposit",
        "segment": ["young_professional", "family", "investor"],
        "min_score": 600,
    },
    "premium_card": {
        "id": "premium_card",
        "title": "Карта ВТБ Мир Supreme",
        "subtitle": "Кэшбэк 5% на всё · бесплатное обслуживание",
        "action": "apply_premium_card",
        "segment": ["investor", "family"],
        "min_score": 750,
    },
    "insurance_travel": {
        "id": "insurance_travel",
        "title": "Страховка для путешествий",
        "subtitle": "От 390 ₽ · покрытие до 50 000 €",
        "action": "buy_travel_insurance",
        "segment": ["young_professional", "investor"],
        "min_score": 0,
    },
    "mortgage_refinance": {
        "id": "mortgage_refinance",
        "title": "Рефинансирование ипотеки",
        "subtitle": "Ставка от 9.5% · снизь платёж",
        "action": "refinance_mortgage",
        "segment": ["family"],
        "min_score": 700,
    },
    "student_credit": {
        "id": "student_credit",
        "title": "Кредит на образование",
        "subtitle": "До 500 000 ₽ · ставка 3% · отсрочка платежа",
        "action": "apply_student_credit",
        "segment": ["student"],
        "min_score": 0,
    },
}

# ---------------------------------------------------------------------------
# ПОЛЬЗОВАТЕЛЬ: Денис В. — Молодой специалист
# ---------------------------------------------------------------------------
USERS = {
    "denis": {

        # -- Профиль клиента (Customer Profile / KYC) -----------------------
        "profile": {
            "user_id": "denis",
            "full_name": "Волков Денис Андреевич",
            "display_name": "Денис В.",
            "birth_date": "2001-07-14",
            "age": 24,
            "phone": "+79161234567",
            "email": "denis.volkov@gmail.com",
            "city": "Санкт-Петербург",

            # Сегментация (скрыто от клиента)
            "segment": "young_professional",
            "score": 680,          # Скоринговый балл (300–850), аналог FICO
            "rfm": {               # RFM-модель: Recency / Frequency / Monetary
                "recency_days": 1,     # последняя транзакция 1 день назад
                "frequency_30d": 28,   # операций за последние 30 дней
                "monetary_30d": 42300, # сумма расходов за 30 дней (руб.)
            },
            "is_salary_client": True,
            "salary_day": 10,          # день поступления зарплаты
            "months_as_client": 14,
            "active_products": ["debit_main", "credit_vtb", "savings_kopilka"],
        },

        # -- Счета (Accounts) -----------------------------------------------
        "accounts": [
            {
                "account_id": "debit_main",
                "account_type": "CurrentAccount",
                "name": "Дебетовая карта",
                "pan_masked": "4276 **** **** 5678",   # PAN (маскирован)
                "pan_full": "4276110045785678",          # только сервер
                "balance": 47_230.50,
                "currency": "RUB",
                "status": "Enabled",
                "open_date": "2024-01-15",
                "cashback_category": "Рестораны",        # активная категория кэшбэка
                "cashback_rate": 0.05,
            },
            {
                "account_id": "credit_vtb",
                "account_type": "CreditCard",
                "name": "Кредитная карта ВТБ",
                "pan_masked": "4272 **** **** 9012",
                "pan_full": "4272301189019012",
                "balance": -8_400.00,    # отрицательный = долг
                "credit_limit": 50_000.00,
                "available_credit": 41_600.00,
                "min_payment": 840.00,
                "payment_due_date": "2026-04-20",
                "currency": "RUB",
                "status": "Enabled",
                "open_date": "2024-06-01",
                "cashback_rate": 0.01,
            },
            {
                "account_id": "savings_kopilka",
                "account_type": "SavingsAccount",
                "name": "Накопительный счёт",
                "pan_masked": None,
                "balance": 120_000.00,
                "currency": "RUB",
                "status": "Enabled",
                "open_date": "2024-03-01",
                "interest_rate": 0.08,   # 8% годовых
                "interest_accrued": 3_200.00,  # накоплено за период
            },
        ],

        # -- Контакты / Бенефициары -----------------------------------------
        # Хранятся на сервере в полном виде; клиенту отдаются с маскировкой
        "contacts": {
            "маша": {
                "display_name": "Мария К.",
                "full_name": "Козлова Мария Игоревна",
                "phone": "+79261112233",
                "bank": "ВТБ",
                "available_banks": ["ВТБ", "Сбер"],
                "pan_masked": "4276 **** **** 1234",
                "transfer_count": 12,      # сколько раз переводили
                "last_transfer_date": "2026-03-28",
            },
            "мария": {  # алиас
                "display_name": "Мария К.",
                "full_name": "Козлова Мария Игоревна",
                "phone": "+79261112233",
                "bank": "ВТБ",
                "available_banks": ["ВТБ", "Сбер"],
                "pan_masked": "4276 **** **** 1234",
                "transfer_count": 12,
                "last_transfer_date": "2026-03-28",
            },
            "яна": {
                "display_name": "Яна С.",
                "full_name": "Семёнова Яна Олеговна",
                "phone": "+79031234567",
                "bank": "Т-Банк",
                "available_banks": ["ВТБ", "Т-Банк"],
                "pan_masked": "5536 **** **** 9901",
                "transfer_count": 5,
                "last_transfer_date": "2026-03-20",
            },
            "дидар": {
                "display_name": "Дидар М.",
                "full_name": "Муратов Дидар Аскарович",
                "phone": "+79273456789",
                "bank": "Сбер",
                "available_banks": ["Сбер", "Т-Банк", "Альфа"],
                "pan_masked": "4276 **** **** 7890",
                "transfer_count": 3,
                "last_transfer_date": "2026-03-10",
            },
            "элина": {
                "display_name": "Элина Р.",
                "full_name": "Романова Элина Витальевна",
                "phone": "+79114567890",
                "bank": "Альфа",
                "available_banks": ["ВТБ", "Альфа"],
                "pan_masked": "4279 **** **** 2345",
                "transfer_count": 2,
                "last_transfer_date": "2026-02-14",
            },
            "мама": {
                "display_name": "Светлана В.",
                "full_name": "Волкова Светлана Петровна",
                "phone": "+79055678901",
                "bank": "Сбер",
                "available_banks": ["Сбер"],
                "pan_masked": "4276 **** **** 0011",
                "transfer_count": 8,
                "last_transfer_date": "2026-03-01",
            },
            "папа": {
                "display_name": "Андрей В.",
                "full_name": "Волков Андрей Николаевич",
                "phone": "+79256789012",
                "bank": "ВТБ",
                "available_banks": ["ВТБ", "Сбер"],
                "pan_masked": "4276 **** **** 0022",
                "transfer_count": 4,
                "last_transfer_date": "2026-03-15",
            },
            "саша": {
                "display_name": "Александр П.",
                "full_name": "Петров Александр Дмитриевич",
                "phone": "+79967890123",
                "bank": "Т-Банк",
                "available_banks": ["Т-Банк"],
                "pan_masked": "5536 **** **** 5544",
                "transfer_count": 6,
                "last_transfer_date": "2026-03-22",
            },
            "антон": {
                "display_name": "Антон Г.",
                "full_name": "Григорьев Антон Сергеевич",
                "phone": "+79851234509",
                "bank": "ВТБ",
                "available_banks": ["ВТБ", "Альфа"],
                "pan_masked": "4276 **** **** 6677",
                "transfer_count": 9,
                "last_transfer_date": "2026-03-25",
            },
        },

        # -- Регулярные платежи (Standing Orders / Scheduled Payments) -------
        "scheduled_payments": [
            {
                "id": "SUB_001",
                "name": "Яндекс.Плюс",
                "amount": 399.00,
                "day_of_month": 2,
                "category": "Подписки",
                "mcc": "5815",
                "status": "Active",
            },
            {
                "id": "SUB_002",
                "name": "Кинопоиск",
                "amount": 399.00,
                "day_of_month": 5,
                "category": "Подписки",
                "mcc": "5815",
                "status": "Active",
            },
            {
                "id": "SUB_003",
                "name": "FitnessPark Сенная",
                "amount": 2_900.00,
                "day_of_month": 1,
                "category": "Спорт",
                "mcc": "7941",
                "status": "Active",
            },
            {
                "id": "UTIL_001",
                "name": "ЖКХ — Жилкомсервис",
                "amount": 4_200.00,
                "day_of_month": 25,
                "category": "ЖКХ",
                "mcc": "4900",
                "status": "Active",
            },
        ],

        # -- История транзакций (последние ~2 месяца) -----------------------
        # Поля по ISO 20022 / Open Banking:
        #   id, booking_date, value_date, type, amount, currency,
        #   direction (debit/credit), status, merchant, mcc, category,
        #   description, account_id
        "transactions": [

            # --- МАРТ 2026 ---

            {
                "id": "T2026033001",
                "booking_date": "2026-03-30",
                "value_date": "2026-03-30",
                "type": "Purchase",
                "amount": 1_850.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": {"name": "ВкусВилл", "mcc": "5411", "city": "Санкт-Петербург"},
                "category": "Супермаркеты",
                "description": "Продукты на неделю",
                "account_id": "debit_main",
            },
            {
                "id": "T2026032901",
                "booking_date": "2026-03-29",
                "value_date": "2026-03-29",
                "type": "Transfer",
                "amount": 500.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": None,
                "recipient": {"display_name": "Мария К.", "bank": "ВТБ"},
                "category": "Переводы",
                "description": "Перевод Маше",
                "account_id": "debit_main",
            },
            {
                "id": "T2026032801",
                "booking_date": "2026-03-28",
                "value_date": "2026-03-28",
                "type": "Purchase",
                "amount": 680.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": {"name": "Starbucks Невский", "mcc": "5812", "city": "Санкт-Петербург"},
                "category": "Кафе и рестораны",
                "description": "Кофе с коллегами",
                "account_id": "debit_main",
            },
            {
                "id": "T2026032701",
                "booking_date": "2026-03-27",
                "value_date": "2026-03-27",
                "type": "TopUp",
                "amount": 300.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": None,
                "recipient": {"phone": "+79261112233", "operator": "МегаФон"},
                "category": "Связь",
                "description": "Пополнение телефона",
                "account_id": "debit_main",
            },
            {
                "id": "T2026032601",
                "booking_date": "2026-03-26",
                "value_date": "2026-03-26",
                "type": "Purchase",
                "amount": 2_340.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": {"name": "Burger King", "mcc": "5814", "city": "Санкт-Петербург"},
                "category": "Фастфуд",
                "description": "Обед",
                "account_id": "credit_vtb",
            },
            {
                "id": "T2026032501",
                "booking_date": "2026-03-25",
                "value_date": "2026-03-25",
                "type": "Purchase",
                "amount": 4_200.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": {"name": "Жилкомсервис №2", "mcc": "4900", "city": "Санкт-Петербург"},
                "category": "ЖКХ",
                "description": "Коммунальные услуги март",
                "account_id": "debit_main",
            },
            {
                "id": "T2026032401",
                "booking_date": "2026-03-24",
                "value_date": "2026-03-24",
                "type": "Transfer",
                "amount": 1_500.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": None,
                "recipient": {"display_name": "Антон Г.", "bank": "ВТБ"},
                "category": "Переводы",
                "description": "За билеты в кино",
                "account_id": "debit_main",
            },
            {
                "id": "T2026032301",
                "booking_date": "2026-03-23",
                "value_date": "2026-03-23",
                "type": "Purchase",
                "amount": 890.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": {"name": "Аптека 36.6", "mcc": "5912", "city": "Санкт-Петербург"},
                "category": "Аптеки",
                "description": "Лекарства",
                "account_id": "debit_main",
            },
            {
                "id": "T2026032201",
                "booking_date": "2026-03-22",
                "value_date": "2026-03-22",
                "type": "Transfer",
                "amount": 800.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": None,
                "recipient": {"display_name": "Александр П.", "bank": "Т-Банк"},
                "category": "Переводы",
                "description": "Долг",
                "account_id": "debit_main",
            },
            {
                "id": "T2026032101",
                "booking_date": "2026-03-21",
                "value_date": "2026-03-21",
                "type": "Purchase",
                "amount": 5_600.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": {"name": "Zara", "mcc": "5651", "city": "Санкт-Петербург"},
                "category": "Одежда",
                "description": "Весенняя коллекция",
                "account_id": "credit_vtb",
            },
            {
                "id": "T2026032001",
                "booking_date": "2026-03-20",
                "value_date": "2026-03-20",
                "type": "Purchase",
                "amount": 1_200.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": {"name": "Яндекс.Еда", "mcc": "5812", "city": "Санкт-Петербург"},
                "category": "Доставка еды",
                "description": "Доставка на вечер",
                "account_id": "debit_main",
            },
            {
                "id": "T2026031901",
                "booking_date": "2026-03-19",
                "value_date": "2026-03-19",
                "type": "Purchase",
                "amount": 350.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": {"name": "Самокат", "mcc": "5411", "city": "Санкт-Петербург"},
                "category": "Супермаркеты",
                "description": "Продукты",
                "account_id": "debit_main",
            },
            {
                "id": "T2026031801",
                "booking_date": "2026-03-18",
                "value_date": "2026-03-18",
                "type": "Purchase",
                "amount": 1_980.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": {"name": "СитиМобил / Яндекс Go", "mcc": "4411", "city": "Санкт-Петербург"},
                "category": "Такси",
                "description": "Поездки за неделю",
                "account_id": "debit_main",
            },
            {
                "id": "T2026031501",
                "booking_date": "2026-03-15",
                "value_date": "2026-03-15",
                "type": "Transfer",
                "amount": 3_000.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": None,
                "recipient": {"display_name": "Андрей В.", "bank": "ВТБ"},
                "category": "Переводы",
                "description": "Папе на день рождения",
                "account_id": "debit_main",
            },
            {
                "id": "T2026031001",
                "booking_date": "2026-03-10",
                "value_date": "2026-03-10",
                "type": "Credit",
                "amount": 85_000.00,
                "currency": "RUB",
                "direction": "credit",
                "status": "Posted",
                "merchant": None,
                "sender": {"name": "ООО Ромашка Технологии", "description": "Зарплата за февраль"},
                "category": "Зарплата",
                "description": "Зарплата за февраль 2026",
                "account_id": "debit_main",
            },
            {
                "id": "T2026030501",
                "booking_date": "2026-03-05",
                "value_date": "2026-03-05",
                "type": "Purchase",
                "amount": 399.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": {"name": "Кинопоиск", "mcc": "5815", "city": None},
                "category": "Подписки",
                "description": "Ежемесячная подписка",
                "account_id": "debit_main",
            },
            {
                "id": "T2026030201",
                "booking_date": "2026-03-02",
                "value_date": "2026-03-02",
                "type": "Purchase",
                "amount": 399.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": {"name": "Яндекс.Плюс", "mcc": "5815", "city": None},
                "category": "Подписки",
                "description": "Ежемесячная подписка",
                "account_id": "debit_main",
            },
            {
                "id": "T2026030101",
                "booking_date": "2026-03-01",
                "value_date": "2026-03-01",
                "type": "Purchase",
                "amount": 2_900.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": {"name": "FitnessPark Сенная", "mcc": "7941", "city": "Санкт-Петербург"},
                "category": "Спорт",
                "description": "Абонемент март",
                "account_id": "debit_main",
            },

            # --- ФЕВРАЛЬ 2026 ---

            {
                "id": "T2026022801",
                "booking_date": "2026-02-28",
                "value_date": "2026-02-28",
                "type": "Purchase",
                "amount": 2_100.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": {"name": "Пятёрочка", "mcc": "5411", "city": "Санкт-Петербург"},
                "category": "Супермаркеты",
                "description": "Продукты",
                "account_id": "debit_main",
            },
            {
                "id": "T2026022501",
                "booking_date": "2026-02-25",
                "value_date": "2026-02-25",
                "type": "Purchase",
                "amount": 4_200.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": {"name": "Жилкомсервис №2", "mcc": "4900", "city": "Санкт-Петербург"},
                "category": "ЖКХ",
                "description": "Коммунальные услуги февраль",
                "account_id": "debit_main",
            },
            {
                "id": "T2026022001",
                "booking_date": "2026-02-20",
                "value_date": "2026-02-20",
                "type": "Purchase",
                "amount": 12_500.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": {"name": "DNS Технологии", "mcc": "5310", "city": "Санкт-Петербург"},
                "category": "Электроника",
                "description": "Наушники Sony",
                "account_id": "credit_vtb",
            },
            {
                "id": "T2026021401",
                "booking_date": "2026-02-14",
                "value_date": "2026-02-14",
                "type": "Transfer",
                "amount": 2_000.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": None,
                "recipient": {"display_name": "Мария К.", "bank": "ВТБ"},
                "category": "Переводы",
                "description": "14 февраля",
                "account_id": "debit_main",
            },
            {
                "id": "T2026021001",
                "booking_date": "2026-02-10",
                "value_date": "2026-02-10",
                "type": "Credit",
                "amount": 85_000.00,
                "currency": "RUB",
                "direction": "credit",
                "status": "Posted",
                "merchant": None,
                "sender": {"name": "ООО Ромашка Технологии", "description": "Зарплата за январь"},
                "category": "Зарплата",
                "description": "Зарплата за январь 2026",
                "account_id": "debit_main",
            },
            {
                "id": "T2026020501",
                "booking_date": "2026-02-05",
                "value_date": "2026-02-05",
                "type": "Purchase",
                "amount": 399.00,
                "currency": "RUB",
                "direction": "debit",
                "status": "Posted",
                "merchant": {"name": "Кинопоиск", "mcc": "5815", "city": None},
                "category": "Подписки",
                "description": "Ежемесячная подписка",
                "account_id": "debit_main",
            },
        ],

    }  # end denis
}

# ---------------------------------------------------------------------------
# Phone index (нормализованный номер → user_id + contact_key)
# Строится один раз при старте
# ---------------------------------------------------------------------------
PHONE_INDEX: dict[str, tuple[str, str]] = {}  # norm_phone → (user_id, contact_key)

def _normalize_phone(phone: str) -> str:
    digits = "".join(c for c in phone if c.isdigit())
    if len(digits) == 11 and digits[0] in ("7", "8"):
        return "7" + digits[1:]
    if len(digits) == 10:
        return "7" + digits
    return digits

def build_phone_index() -> None:
    for user_id, user_data in USERS.items():
        for key, contact in user_data["contacts"].items():
            norm = _normalize_phone(contact["phone"])
            if norm and (user_id, key) not in PHONE_INDEX.values():
                PHONE_INDEX[norm] = (user_id, key)

build_phone_index()
