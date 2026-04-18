package com.vtbvita.widget.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vtbvita.widget.R
import com.vtbvita.widget.ui.theme.OmegaBrandGradientH
import com.vtbvita.widget.ui.theme.OmegaBrandPrimary
import com.vtbvita.widget.ui.theme.OmegaChip
import com.vtbvita.widget.ui.theme.OmegaError
import com.vtbvita.widget.ui.theme.OmegaElevation
import com.vtbvita.widget.ui.theme.OmegaRadius
import com.vtbvita.widget.ui.theme.OmegaSize
import com.vtbvita.widget.ui.theme.OmegaSpacing
import com.vtbvita.widget.ui.theme.OmegaStroke
import com.vtbvita.widget.ui.theme.OmegaSurface
import com.vtbvita.widget.ui.theme.OmegaSurfaceAlt
import com.vtbvita.widget.ui.theme.OmegaSuccess
import com.vtbvita.widget.ui.theme.OmegaType
import com.vtbvita.widget.ui.theme.OmegaWarningBg
import com.vtbvita.widget.ui.theme.OmegaWidgetGradientEnd
import com.vtbvita.widget.ui.theme.OmegaWidgetGradientStart
import com.vtbvita.widget.ui.theme.OmegaBlue
import com.vtbvita.widget.ui.theme.TitanGray
import com.vtbvita.widget.ui.theme.OmegaBackground
import java.util.Locale

data class SuccessAction(
    val icon: String,
    val label: String,
    val onClick: () -> Unit = {}
)

data class BankOption(
    val id: String,
    val label: String,
    @DrawableRes val logoRes: Int?
)

val BANK_OPTIONS = listOf(
    BankOption("ВТБ",          "ВТБ",        R.drawable.bank_vtb),
    BankOption("Сбербанк",     "Сбер",       R.drawable.bank_sber),
    BankOption("Тинькофф",     "Т-Банк",     R.drawable.bank_tbank),
    BankOption("Альфа-банк",   "Альфа",      R.drawable.bank_alfa),
    BankOption("Райффайзен",   "Райффайзен", R.drawable.bank_raiffeisen),
    BankOption("Другой банк",  "Другой",     null),
)

// ── Omega Button Styles ─────────────────────────────────────────────────────

enum class OmegaButtonStyle { Brand, Neutral, Toned, Outlined, Gradient }

// ── OmegaButton — основная CTA-кнопка по Omega Design System ────────────────
//
// Brand     — синяя (omegaBlue/600), белый текст, основная CTA
// Neutral   — серая (titanGray/700), белый текст, вторичная
// Toned     — полупрозрачная синяя (omegaBlue/600 12%), белый текст
// Outlined  — прозрачная с синим контуром, синий текст
// Gradient  — фирменный ВТБ градиент, белый текст
//
// Dark theme specification from Omega:
//   Brand-filled:  #0160EC bg, white text (Normal), #0456D3 (Pressed)
//   Neutral-filled: titanGray/700 bg, white text
//   Toned:          omegaBlue/600 @ 12% bg, white text
//   Outlined:       transparent bg, omegaBlue/600 border & text

@Composable
fun OmegaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    style: OmegaButtonStyle = OmegaButtonStyle.Brand
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.38f,
        animationSpec = tween(150),
        label = "btnAlpha"
    )

    val bgColor: Color = when (style) {
        OmegaButtonStyle.Brand -> OmegaBrandPrimary           // #0160EC
        OmegaButtonStyle.Neutral -> TitanGray.v700            // #3F4650
        OmegaButtonStyle.Toned -> OmegaBrandPrimary.copy(alpha = 0.12f)
        OmegaButtonStyle.Outlined -> Color.Transparent
        OmegaButtonStyle.Gradient -> Color.Transparent       // will use gradient brush
    }

    val bgColorPressed: Color = when (style) {
        OmegaButtonStyle.Brand -> OmegaBlue.v700               // #0456D3
        OmegaButtonStyle.Neutral -> TitanGray.v800              // #2F343C
        OmegaButtonStyle.Toned -> OmegaBrandPrimary.copy(alpha = 0.18f)
        OmegaButtonStyle.Outlined -> Color.Transparent
        OmegaButtonStyle.Gradient -> Color.Transparent
    }

    val bgColorDisabled: Color = when (style) {
        OmegaButtonStyle.Brand -> TitanGray.v700               // #3F4650
        OmegaButtonStyle.Neutral -> TitanGray.v800
        OmegaButtonStyle.Toned -> TitanGray.v700.copy(alpha = 0.5f)
        OmegaButtonStyle.Outlined -> Color.Transparent
        OmegaButtonStyle.Gradient -> TitanGray.v700
    }

    val textColor: Color = when {
        !enabled -> TitanGray.v400                             // #7C8798
        style == OmegaButtonStyle.Outlined -> OmegaBrandPrimary
        else -> Color.White
    }

    val currentBg = when {
        !enabled -> bgColorDisabled
        isLoading -> bgColor
        else -> bgColor
    }

    val borderStroke: BorderStroke? = if (style == OmegaButtonStyle.Outlined && enabled)
        BorderStroke(OmegaStroke.thin, OmegaBrandPrimary)
    else if (style == OmegaButtonStyle.Outlined && !enabled)
        BorderStroke(OmegaStroke.thin, TitanGray.v600)
    else null

    Box(
        modifier = modifier
            .height(OmegaSize.buttonHeight)
            .graphicsLayer(alpha = alpha)
            .clip(OmegaRadius.pill)
            .background(
                if (style == OmegaButtonStyle.Gradient && enabled) OmegaBrandGradientH
                else Brush.horizontalGradient(listOf(currentBg, currentBg))
            )
            .then(if (borderStroke != null) Modifier.border(borderStroke, OmegaRadius.pill) else Modifier)
            .clickable(enabled = enabled && !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(OmegaSize.iconLg),
                strokeWidth = OmegaStroke.regular,
                color = if (style == OmegaButtonStyle.Outlined) OmegaBrandPrimary else Color.White
            )
        } else {
            Text(
                text = text,
                color = textColor,
                style = OmegaType.BodySemiBoldL,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── OmegaBankCarousel — горизонтальный скролл выбора банка ───────────────────

@Composable
fun OmegaBankCarousel(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    banks: List<BankOption> = BANK_OPTIONS
) {
    Column(modifier = modifier) {
        Text(
            "Банк получателя",
            style = OmegaType.BodyM,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(OmegaSpacing.sm))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(OmegaSpacing.sm),
            contentPadding = PaddingValues(horizontal = OmegaSpacing.xxs)
        ) {
            items(banks, key = { it.id }) { bank ->
                OmegaChip(
                    bank = bank,
                    isSelected = bank.id == selected,
                    onClick = { onSelect(bank.id) }
                )
            }
        }
    }
}

@Composable
private fun OmegaChip(
    bank: BankOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderWidth by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "chipBorder"
    )

    Row(
        modifier = Modifier
            .clip(OmegaRadius.xl)
            .background(OmegaChip)
            .then(
                if (isSelected) Modifier.border(
                    BorderStroke(borderWidth.dp, Color.White),
                    OmegaRadius.xl
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = OmegaSpacing.md, vertical = OmegaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OmegaSpacing.xs)
    ) {
        if (bank.logoRes != null) {
            Image(
                painter = painterResource(bank.logoRes),
                contentDescription = bank.label,
                modifier = Modifier.size(OmegaSize.iconMd),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .size(OmegaSize.iconMd)
                    .background(TitanGray.v700, OmegaRadius.xs),
                contentAlignment = Alignment.Center
            ) {
                Text("?", style = OmegaType.HeadlineS, color = TitanGray.v400)
            }
        }
        Text(
            text = bank.label,
            style = OmegaType.BodySemiBoldS,
            color = Color.White,
            maxLines = 1
        )
    }
}

// ── OmegaInfoCard — карточка с лейблом сверху и значением снизу ──────────────

@Composable
fun OmegaInfoCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val baseModifier = modifier
        .fillMaxWidth()
        .clip(OmegaRadius.lg)
        .background(OmegaSurface)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = OmegaSpacing.lg, vertical = OmegaSpacing.md + 2.dp)

    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
style = OmegaType.BodyTightM,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(OmegaSpacing.xxs))
            Text(
                text = value,
                style = OmegaType.BodySemiBoldL,
                color = valueColor
            )
        }
        if (trailingContent != null) {
            Spacer(Modifier.width(OmegaSpacing.sm))
            trailingContent()
        }
    }
}

// ── OmegaTextField — плоское тёмное поле ввода ────────────────────────────────

@Composable
fun OmegaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = OmegaType.BodyM,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(OmegaSpacing.sm - 2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(OmegaRadius.md)
                .background(OmegaSurface)
                .padding(horizontal = OmegaSpacing.lg, vertical = OmegaSpacing.md + 2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    keyboardOptions = keyboardOptions,
                    textStyle = OmegaType.BodyL.copy(color = Color.White),
                    cursorBrush = Brush.verticalGradient(listOf(Color.White, Color.White)),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                style = OmegaType.BodyL,
                                color = TitanGray.v500
                            )
                        }
                        inner()
                    }
                )
                if (trailingContent != null) {
                    Spacer(Modifier.width(OmegaSpacing.sm))
                    trailingContent()
                }
            }
        }
    }
}

// ── OmegaAmountChip — chip быстрого выбора суммы ──────────────────────────────

@Composable
fun OmegaAmountChip(
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(OmegaRadius.xl)
            .background(if (selected) OmegaSurface else OmegaChip)
            .then(
                if (selected) Modifier.border(
                    BorderStroke(OmegaStroke.thin, Color.White),
                    OmegaRadius.xl
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = OmegaSpacing.lg, vertical = OmegaSpacing.md - 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = if (selected) OmegaType.BodySemiBoldM else OmegaType.BodyL,
            color = Color.White
        )
    }
}

// ── OmegaSheetHeader — заголовок bottom sheet ────────────────────────────────

@Composable
fun OmegaSheetHeader(
    title: String,
    subtitle: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OmegaSurface)
            .padding(horizontal = OmegaSpacing.xxl, vertical = OmegaSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(OmegaSize.handleBar)
                .height(OmegaSpacing.xs)
                .background(TitanGray.v700, RoundedCornerShape(OmegaSpacing.xxs))
        )
        Spacer(Modifier.height(OmegaSpacing.lg))
        Text(
            text = title,
            style = OmegaType.HeadlineL,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        if (subtitle != null) {
            Spacer(Modifier.height(OmegaSpacing.xs))
            Text(
                text = subtitle,
                style = OmegaType.BodyL,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── OmegaWarningCard — предупреждение (оранжевый фон) ────────────────────────

@Composable
fun OmegaWarningCard(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(OmegaRadius.md)
            .background(OmegaWarningBg)
            .padding(horizontal = OmegaSpacing.lg, vertical = OmegaSpacing.md)
    ) {
        Text(
            text = text,
            style = OmegaType.BodyL,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

// ── OmegaSuccessIcon — синий круг с зелёным чекмарком ────────────────────────

@Composable
fun OmegaSuccessIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(OmegaSize.successIcon),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(OmegaSize.successIcon)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(OmegaBlue.v900, OmegaBlue.v1000)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(OmegaSize.iconLg + 2.dp)
                .clip(CircleShape)
                .background(OmegaSuccess)
                .align(Alignment.BottomEnd),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", color = Color.White, style = OmegaType.BodySemiBoldM)
        }
    }
}

// ── OmegaSuccessScreen — полноэкранный экран успеха ──────────────────────────

@Composable
fun OmegaSuccessScreen(
    title: String,
    subtitle: String,
    amount: Double,
    actions: List<SuccessAction> = emptyList(),
    onDone: () -> Unit
) {
    val iconScale = remember { androidx.compose.animation.core.Animatable(0.4f) }
    LaunchedEffect(Unit) {
        iconScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.vtbvita.widget.ui.theme.OmegaBackground),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = OmegaSize.successIcon, bottom = OmegaSize.avatarSm + OmegaSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OmegaSpacing.lg)
        ) {
            OmegaSuccessIcon(modifier = Modifier.scale(iconScale.value))

            Text(
                text = title,
                style = OmegaType.HeadlineL,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subtitle,
                style = OmegaType.BodyL,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = formatSuccessRub(amount),
                style = OmegaType.DisplayL,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (actions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OmegaSpacing.lg)
                    .clip(OmegaRadius.lg)
                    .background(OmegaSurface)
            ) {
                actions.forEachIndexed { index, action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = action.onClick)
                            .padding(horizontal = OmegaSpacing.xl, vertical = OmegaSpacing.lg + OmegaSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OmegaSpacing.lg)
                    ) {
                        Text(text = action.icon, style = OmegaType.BodySemiBoldL)
                        Text(text = action.label, style = OmegaType.BodyL, color = Color.White)
                    }
                    if (index < actions.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = OmegaSpacing.xl),
                            thickness = OmegaStroke.hairline,
                            color = OmegaChip
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        OmegaButton(
            text = "На главную",
            style = OmegaButtonStyle.Neutral,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OmegaSpacing.lg)
                .padding(bottom = OmegaSpacing.xxl),
            onClick = onDone
        )
    }
}

private fun formatSuccessRub(amount: Double): String =
    String.format(Locale("ru"), "%,.0f ₽", amount)