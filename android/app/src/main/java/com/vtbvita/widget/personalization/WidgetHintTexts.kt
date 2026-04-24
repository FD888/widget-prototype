package com.vtbvita.widget.personalization

import com.vtbvita.widget.model.HintResult

object WidgetHintTexts {

    private val SENSITIVE_TYPES = setOf("credit_card", "loan")

    fun formatReminder(hint: HintResult): String {
        val name = hint.name ?: "платёж"
        val days = hint.daysUntilDue
        val isOverdue = hint.isOverdue == true
        val isSensitive = hint.paymentType in SENSITIVE_TYPES
        val amountStr = if (!isSensitive && hint.amount != null) ", ${formatAmount(hint.amount)}" else ""

        return when {
            isOverdue -> "платёж по $name просрочен, оплатить?"
            days == 0 -> "сегодня платёж по $name$amountStr"
            days == 1 -> "платёж по $name завтра$amountStr"
            days != null && days in 2..4 -> "платёж по $name через ${days} дня$amountStr"
            days != null && days > 4 -> "платёж по $name через $days дней$amountStr"
            else -> "платёж по $name скоро$amountStr"
        }
    }

    fun formatVygoda(hint: HintResult): String {
        val offerId = hint.offerId ?: return hint.offerText ?: "выгодное предложение"
        val text = hint.offerText ?: return "выгодное предложение"

        return when (offerId) {
            "cashback_multi" -> text
            "autopay_setup" -> text
            "deposit_3m" -> text
            "iis" -> text
            "prime" -> text
            "round_up" -> text
            else -> text
        }
    }

    fun toWidgetText(hint: HintResult): String? {
        return when (hint.type) {
            "custom" -> hint.label ?: hint.name ?: null
            "reminder" -> formatReminder(hint)
            "vygoda" -> formatVygoda(hint)
            else -> null
        }
    }

    private fun formatAmount(amount: Double): String {
        val rounded = amount.toLong()
        return if (rounded >= 1000) {
            "${formatWithSpaces(rounded)} руб"
        } else {
            "$rounded руб"
        }
    }

    private fun formatWithSpaces(n: Long): String {
        val s = n.toString()
        return s.reversed().chunked(3).joinToString(" ").reversed()
    }
}