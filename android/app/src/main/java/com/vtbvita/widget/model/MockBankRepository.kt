package com.vtbvita.widget.model

import androidx.annotation.DrawableRes
import com.vtbvita.widget.R

data class MockAccount(
    val id: String,
    val name: String,
    val mask: String,
    val balance: Double,
    val paymentSystem: PaymentSystem,
    val type: AccountType
)

enum class PaymentSystem(@DrawableRes val logoRes: Int) {
    MIR(R.drawable.ps_mir),
    VISA(R.drawable.ps_visa),
    MASTERCARD(R.drawable.ps_mastercard)
}

enum class AccountType {
    DEBIT,
    SAVINGS,
    CREDIT
}

data class MockOperator(
    val name: String,
    val prefixes: List<String>,
    @DrawableRes val logoRes: Int
)

val MOCK_ACCOUNTS = listOf(
    MockAccount("acc_rub", "Мастер-счёт в рублях", "·· 3178", 5.0, PaymentSystem.MIR, AccountType.DEBIT),
    MockAccount("acc_usd", "Мастер-счёт в долларах", "·· 4521", 120.0, PaymentSystem.VISA, AccountType.DEBIT),
    MockAccount("acc_savings", "Накопительный счёт", "·· 8001", 50000.0, PaymentSystem.MIR, AccountType.SAVINGS),
)

val MOCK_OPERATORS = listOf(
    MockOperator("МТС", listOf("910","911","912","913","914","915","916","917","918","919","980","981","985","986"), R.drawable.operator_mts),
    MockOperator("МегаФон", listOf("920","921","922","923","924","925","926","927","928","929","930","931","932","933","934","935","936","937","938","939"), R.drawable.operator_megafon),
    MockOperator("Билайн", listOf("903","905","906","909","960","961","962","963","964","965","966","967","968","969"), R.drawable.operator_beeline),
    MockOperator("Tele2", listOf("900","901","902","904","908","950","951","952","953","958","971","977","978"), R.drawable.operator_tele2),
    MockOperator("Yota", listOf("999","996","995"), R.drawable.operator_yota),
)

fun detectOperator(phone: String): MockOperator? {
    val digits = phone.filter { it.isDigit() }
    val prefix = if (digits.startsWith("7") || digits.startsWith("8")) {
        digits.drop(1).take(3)
    } else {
        digits.take(3)
    }
    return MOCK_OPERATORS.find { prefix in it.prefixes }
}

object AvatarPalette {
    data class ColorPair(val bg: androidx.compose.ui.graphics.Color, val text: androidx.compose.ui.graphics.Color)

    val pairs = listOf(
        ColorPair(com.vtbvita.widget.ui.theme.MoonGray.v100, com.vtbvita.widget.ui.theme.MoonGray.v800),
        ColorPair(com.vtbvita.widget.ui.theme.UranianAqua.v100, com.vtbvita.widget.ui.theme.UranianAqua.v800),
        ColorPair(com.vtbvita.widget.ui.theme.AmaltheanPink.v100, com.vtbvita.widget.ui.theme.AmaltheanPink.v800),
        ColorPair(com.vtbvita.widget.ui.theme.OberonianYellow.v100, com.vtbvita.widget.ui.theme.OberonianYellow.v800),
        ColorPair(com.vtbvita.widget.ui.theme.AndromedanLime.v100, com.vtbvita.widget.ui.theme.AndromedanLime.v800),
        ColorPair(com.vtbvita.widget.ui.theme.ArielRuby.v100, com.vtbvita.widget.ui.theme.ArielRuby.v800),
    )

    fun forName(name: String): ColorPair {
        val index = Math.abs(name.hashCode()) % pairs.size
        return pairs[index]
    }
}