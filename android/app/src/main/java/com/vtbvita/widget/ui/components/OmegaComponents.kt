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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.Dp
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
import com.vtbvita.widget.ui.theme.OmegaTextSecondary
import com.vtbvita.widget.ui.theme.OmegaType
import com.vtbvita.widget.ui.theme.OmegaWarningBg
import com.vtbvita.widget.ui.theme.OmegaWidgetGradientEnd
import com.vtbvita.widget.ui.theme.OmegaWidgetGradientStart
import com.vtbvita.widget.ui.theme.OmegaBlue
import com.vtbvita.widget.ui.theme.TitanGray
import com.vtbvita.widget.ui.theme.OmegaBackground
import java.util.Locale

data class SuccessAction(
    @DrawableRes val iconRes: Int,
    val label: String,
    val onClick: () -> Unit = {}
)

data class BankOption(
    val id: String,
    val label: String,
    @DrawableRes val logoRes: Int?
)

val BANK_OPTIONS = listOf(
    BankOption("ВТБ",          "ВТБ",        R.drawable.merch_vtb),
    BankOption("Сбербанк",     "Сбер",       R.drawable.merch_sberbank),
    BankOption("Тинькофф",     "Т-Банк",     R.drawable.merch_tinkoff),
    BankOption("Альфа-банк",   "Альфа",      R.drawable.merch_alfabank),
    BankOption("Райффайзен",   "Райффайзен", R.drawable.merch_raiffeisen),
    BankOption("Другой банк",  "Другой",     R.drawable.merch_sbp_trans),
)

// ── Omega Button Styles ─────────────────────────────────────────────────────

enum class OmegaButtonStyle { Brand, Neutral, NeutralLight, Toned, Outlined, Gradient }

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
        OmegaButtonStyle.Brand -> OmegaBrandPrimary
        OmegaButtonStyle.Neutral -> TitanGray.v700
        OmegaButtonStyle.NeutralLight -> Color.White
        OmegaButtonStyle.Toned -> OmegaBrandPrimary.copy(alpha = 0.12f)
        OmegaButtonStyle.Outlined -> Color.Transparent
        OmegaButtonStyle.Gradient -> Color.Transparent
    }

    val bgColorPressed: Color = when (style) {
        OmegaButtonStyle.Brand -> OmegaBlue.v700
        OmegaButtonStyle.Neutral -> TitanGray.v800
        OmegaButtonStyle.NeutralLight -> TitanGray.v200
        OmegaButtonStyle.Toned -> OmegaBrandPrimary.copy(alpha = 0.18f)
        OmegaButtonStyle.Outlined -> Color.Transparent
        OmegaButtonStyle.Gradient -> Color.Transparent
    }

    val bgColorDisabled: Color = when (style) {
        OmegaButtonStyle.Brand -> TitanGray.v700
        OmegaButtonStyle.Neutral -> TitanGray.v800
        OmegaButtonStyle.NeutralLight -> TitanGray.v400
        OmegaButtonStyle.Toned -> TitanGray.v700.copy(alpha = 0.5f)
        OmegaButtonStyle.Outlined -> Color.Transparent
        OmegaButtonStyle.Gradient -> TitanGray.v700
    }

    val textColor: Color = when {
        !enabled -> TitanGray.v400
        style == OmegaButtonStyle.Outlined -> OmegaBrandPrimary
        style == OmegaButtonStyle.NeutralLight -> OmegaBackground
        else -> Color.White
    }

    val currentBg = when {
        !enabled -> bgColorDisabled
        isLoading -> bgColor
        else -> bgColor
    }

    val borderStroke: BorderStroke? = if (style == OmegaButtonStyle.Outlined && enabled)
        BorderStroke(OmegaStroke.regular, OmegaBrandPrimary)
    else if (style == OmegaButtonStyle.Outlined && !enabled)
        BorderStroke(OmegaStroke.regular, TitanGray.v600)
    else null

    Box(
        modifier = modifier
            .height(OmegaSize.buttonHeight)
            .graphicsLayer(alpha = alpha)
            .clip(OmegaRadius.md)
            .background(
                if (style == OmegaButtonStyle.Gradient && enabled) OmegaBrandGradientH
                else Brush.horizontalGradient(listOf(currentBg, currentBg))
            )
            .then(if (borderStroke != null) Modifier.border(borderStroke, OmegaRadius.md) else Modifier)
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
    banks: List<BankOption> = BANK_OPTIONS,
    showLabel: Boolean = true
) {
    Column(modifier = modifier) {
        if (showLabel) {
            Text(
                "Банк получателя",
                style = OmegaType.BodyTightM,
                color = com.vtbvita.widget.ui.theme.OmegaTextSecondary
            )
            Spacer(Modifier.height(OmegaSpacing.sm))
        }
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
    Row(
        modifier = Modifier
            .clip(OmegaRadius.md)
            .background(OmegaSurface)
            .border(
                BorderStroke(OmegaStroke.regular, if (isSelected) Color.White else TitanGray.v600),
                OmegaRadius.md
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
                modifier = Modifier.size(OmegaSize.iconMd).clip(CircleShape),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .size(OmegaSize.iconMd)
                    .clip(CircleShape)
                    .background(TitanGray.v700),
                contentAlignment = Alignment.Center
            ) {
                Text("?", style = OmegaType.HeadlineS, color = TitanGray.v400)
            }
        }
        Text(
            text = bank.label,
            style = OmegaType.BodySemiBoldS,
            color = if (isSelected) OmegaBrandPrimary else Color.White,
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
                color = OmegaTextSecondary
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
    showBorder: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = OmegaType.BodyM,
            color = OmegaTextSecondary
        )
        Spacer(Modifier.height(OmegaSpacing.sm - 2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(OmegaRadius.md)
                .background(OmegaSurface)
                .then(if (showBorder) Modifier.border(BorderStroke(OmegaStroke.regular, TitanGray.v600), OmegaRadius.md) else Modifier)
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
            .clip(OmegaRadius.md)
            .background(OmegaSurface)
            .border(
                BorderStroke(OmegaStroke.regular, if (selected) Color.White else TitanGray.v600),
                OmegaRadius.md
            )
            .clickable(onClick = onClick)
            .padding(horizontal = OmegaSpacing.lg, vertical = OmegaSpacing.md - 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = if (selected) OmegaType.BodyTightSemiBoldL else OmegaType.BodyTightL,
            color = if (selected) OmegaBrandPrimary else Color.White
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
                color = OmegaTextSecondary,
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
        Icon(
            painter = painterResource(R.drawable.ic_success_check),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(OmegaSuccess)
                .align(Alignment.TopEnd),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_success_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
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
    content: (@Composable ColumnScope.() -> Unit)? = null,
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
                color = com.vtbvita.widget.ui.theme.OmegaTextSecondary
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
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(OmegaSurfaceAlt),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(action.iconRes),
                                contentDescription = action.label,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(text = action.label, style = OmegaType.BodyL, color = Color.White)
                    }
                    if (index < actions.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = OmegaSpacing.xl),
                            thickness = OmegaStroke.hairline,
                            color = com.vtbvita.widget.ui.theme.OmegaDivider
                        )
                    }
                }
            }
        }

        if (content != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OmegaSpacing.lg)
                    .padding(top = OmegaSpacing.lg)
            ) {
                content()
            }
        }

        Spacer(Modifier.weight(1f))

        OmegaButton(
            text = "На главную",
            style = OmegaButtonStyle.NeutralLight,
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

// ── OmegaSelectableChip — универсальный чип выбора ──────────────────────────────

@Composable
fun OmegaSelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .clip(OmegaRadius.md)
            .background(OmegaSurface)
            .border(
                BorderStroke(OmegaStroke.regular, if (selected) Color.White else TitanGray.v600),
                OmegaRadius.md
            )
            .clickable(onClick = onClick)
            .padding(horizontal = OmegaSpacing.md, vertical = OmegaSpacing.sm + OmegaSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OmegaSpacing.xs)
    ) {
        if (leadingIcon != null) {
            leadingIcon()
        }
        Text(
            text = label,
            style = if (selected) OmegaType.BodyTightSemiBoldL else OmegaType.BodyTightL,
            color = if (selected) OmegaBrandPrimary else Color.White
        )
    }
}

// ── OmegaAvatar — аватар с инициалами на пастельном фоне ────────────────────────

@Composable
fun OmegaAvatar(
    name: String,
    modifier: Modifier = Modifier,
    avatarSize: Dp = OmegaSize.avatarMd,
    bgColor: Color? = null,
    textColor: Color? = null
) {
    val pair = com.vtbvita.widget.model.AvatarPalette.forName(name)
    val initials = name.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
    val bg = bgColor ?: pair.bg
    val fg = textColor ?: pair.text

    Box(
        modifier = modifier
            .size(avatarSize)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = if (avatarSize.value >= OmegaSize.avatarLg.value) OmegaType.BodySemiBoldL else OmegaType.BodySemiBoldM,
            color = fg,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── OmegaSearchField — поле поиска ────────────────────────────────────────────────

@Composable
fun OmegaSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Имя или номер телефона"
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(OmegaSize.searchFieldHeight)
            .clip(OmegaRadius.md)
            .background(OmegaSurface)
            .border(
                BorderStroke(OmegaStroke.regular, TitanGray.v600),
                OmegaRadius.md
            )
            .padding(horizontal = OmegaSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = com.vtbvita.widget.ui.theme.OmegaTextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(OmegaSpacing.sm))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = OmegaType.BodyL,
                    color = com.vtbvita.widget.ui.theme.OmegaTextHint
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = OmegaType.BodyL.copy(color = Color.White),
                cursorBrush = Brush.verticalGradient(listOf(Color.White, Color.White)),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(OmegaSpacing.sm))
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Очистить",
                tint = com.vtbvita.widget.ui.theme.OmegaTextSecondary,
                modifier = Modifier
                    .size(OmegaSize.iconLg)
                    .clickable(onClick = { onValueChange("") })
            )
        }
    }
}

// ── OmegaTopBar — стандартный топ-бар ────────────────────────────────────────────

@Composable
fun OmegaTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = OmegaSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_back),
                    contentDescription = "Назад",
                    tint = Color.White,
                    modifier = Modifier.size(OmegaSize.iconLg)
                )
            }
        } else {
            Spacer(Modifier.size(40.dp))
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = OmegaType.HeadlineL,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }

        if (onClose != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Закрыть",
                    tint = Color.White,
                    modifier = Modifier.size(OmegaSize.iconLg)
                )
            }
        } else if (onInfo != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onInfo),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = "Информация",
                    tint = Color.White,
                    modifier = Modifier.size(OmegaSize.iconLg)
                )
            }
        } else {
            Spacer(Modifier.size(40.dp))
        }
    }
}

// ── OmegaCompactInfoCard — компактная карточка для плашек ────────────────────────

@Composable
fun OmegaCompactInfoCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    valueStyle: androidx.compose.ui.text.TextStyle = OmegaType.BodyTightSemiBoldL,
    subtitleStyle: androidx.compose.ui.text.TextStyle = OmegaType.BodyTightM,
    subtitleColor: Color = com.vtbvita.widget.ui.theme.OmegaTextSecondary,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val baseModifier = modifier
        .fillMaxWidth()
        .clip(OmegaRadius.md)
        .background(OmegaSurfaceAlt)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = OmegaSpacing.md, vertical = OmegaSpacing.sm + OmegaSpacing.xs)

    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = OmegaType.BodyTightM,
                color = com.vtbvita.widget.ui.theme.OmegaTextSecondary
            )
            Spacer(Modifier.height(OmegaSpacing.xxs))
            Text(
                text = value,
                style = valueStyle,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Spacer(Modifier.height(OmegaSpacing.xxs))
                Text(
                    text = subtitle,
                    style = subtitleStyle,
                    color = subtitleColor
                )
            }
        }
        if (trailingContent != null) {
            Spacer(Modifier.width(OmegaSpacing.sm))
            trailingContent()
        }
    }
}

// ── AmountInputCard — карточка ввода суммы ────────────────────────────────────────

@Composable
fun AmountInputCard(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Сумма",
    hint: String = "0 ₽"
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(OmegaRadius.md)
                .background(OmegaSurface)
                .border(BorderStroke(OmegaStroke.regular, TitanGray.v600), OmegaRadius.md)
                .padding(OmegaSpacing.lg)
        ) {
            Column {
                Text(
                    text = label,
                    style = OmegaType.BodyTightM,
                    color = com.vtbvita.widget.ui.theme.OmegaTextSecondary
                )
                Spacer(Modifier.height(OmegaSpacing.xs))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = OmegaType.BodyTightSemiBoldL.copy(color = Color.White),
                    cursorBrush = Brush.verticalGradient(listOf(Color.White, Color.White)),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(
                                text = hint,
                                style = OmegaType.BodyTightSemiBoldL,
                                color = com.vtbvita.widget.ui.theme.OmegaTextHint,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        inner()
                    }
                )
            }
        }
        Spacer(Modifier.height(OmegaSpacing.sm))
        Text(
            text = "Зачисление происходит моментально",
            style = OmegaType.BodyTightM,
            color = com.vtbvita.widget.ui.theme.OmegaTextSecondary
        )
    }
}

// ── AccountPickerSheet — выбор счёта списания ─────────────────────────────────

@Composable
fun AccountPickerSheet(
    accounts: List<com.vtbvita.widget.model.AccountInfo>,
    selectedId: String,
    onSelect: (com.vtbvita.widget.model.AccountInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val rubAccounts = accounts.filter { it.currency == "RUB" }

    OmegaSheetScaffold(
        title = "Откуда",
        onDismiss = onDismiss,
        onBack = onDismiss
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(OmegaSpacing.sm),
            modifier = Modifier.padding(bottom = OmegaSpacing.md)
        ) {
            rubAccounts.forEach { acc ->
                val isSelected = acc.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(OmegaRadius.md)
                        .background(OmegaSurfaceAlt)
                        .clickable { onSelect(acc); onDismiss() }
                        .padding(horizontal = OmegaSpacing.md, vertical = OmegaSpacing.sm + OmegaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VtbCardWithBadge(
                        accountType = acc.type,
                        paymentSystem = acc.paymentSystem,
                        cardSize = 48.dp
                    )
                    Spacer(Modifier.width(OmegaSpacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = acc.name,
                            style = OmegaType.BodySemiBoldL,
                            color = Color.White
                        )
                        Text(
                            text = acc.masked,
                            style = OmegaType.BodyTightM,
                            color = com.vtbvita.widget.ui.theme.OmegaTextSecondary
                        )
                    }
                    Spacer(Modifier.width(OmegaSpacing.sm))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = String.format(java.util.Locale("ru"), "%,.0f ₽", acc.balance),
                            style = OmegaType.BodySemiBoldM,
                            color = Color.White
                        )
                        if (isSelected) {
                            Spacer(Modifier.height(OmegaSpacing.xxs))
                            Text("✓", style = OmegaType.BodyTightM, color = com.vtbvita.widget.ui.theme.OmegaSuccess)
                        }
                    }
                }
            }
        }
    }
}

fun paymentSystemLogoRes(paymentSystem: String): Int =
    when (paymentSystem.lowercase()) {
        "mir"        -> R.drawable.ps_mir
        "visa"       -> R.drawable.ps_visa
        "mastercard" -> R.drawable.ps_mastercard
        else         -> 0
    }

@androidx.annotation.DrawableRes
fun vtbCardRes(type: String): Int = when (type.lowercase()) {
    "currentaccount" -> R.drawable.vtb_card_current
    "savingsaccount" -> R.drawable.vtb_card_savings
    "credit_card"    -> R.drawable.vtb_card_credit
    else             -> R.drawable.vtb_card_current
}

@Composable
fun VtbCardWithBadge(
    accountType: String,
    paymentSystem: String = "mir",
    cardSize: androidx.compose.ui.unit.Dp = 48.dp
) {
    Box(contentAlignment = Alignment.BottomEnd) {
        Image(
            painter = painterResource(vtbCardRes(accountType)),
            contentDescription = null,
            modifier = Modifier.size(cardSize),
            contentScale = ContentScale.Fit
        )
        if (paymentSystem.lowercase() == "mir") {
            Image(
                painter = painterResource(R.drawable.ps_mir_inv),
                contentDescription = "МИР",
                modifier = Modifier.width(20.dp).height(14.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

fun operatorLogoRes(operator: String): Int =
    when (operator) {
        "МТС"     -> R.drawable.merch_mts
        "МегаФон" -> R.drawable.merch_megafon
        "Билайн"  -> R.drawable.merch_beeline
        "Tele2"   -> R.drawable.merch_tele2
        else      -> 0
    }

// ── TransferSummary — строки деталей перевода ─────────────────────────────────

@Composable
fun TransferSummary(
    accountName: String,
    accountMask: String,
    balanceAfter: Double,
    comment: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(OmegaSpacing.sm)) {
        OmegaCompactInfoCard(label = "Откуда", value = "$accountName  $accountMask")
        OmegaCompactInfoCard(
            label = "Остаток после перевода",
            value = String.format(java.util.Locale("ru"), "%,.0f ₽", balanceAfter),
            valueStyle = if (balanceAfter >= 0) OmegaType.BodyTightSemiBoldL
                         else OmegaType.BodyTightSemiBoldL.copy(color = OmegaError)
        )
        if (comment.isNotBlank()) {
            OmegaCompactInfoCard(label = "Сообщение", value = comment)
        }
    }
}

// ── SbpRow — способ перевода через СБП ────────────────────────────────────────

@Composable
fun SbpRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(OmegaRadius.md)
            .background(OmegaSurfaceAlt)
            .padding(horizontal = OmegaSpacing.md, vertical = OmegaSpacing.sm + OmegaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.payment_sbp),
            contentDescription = "СБП",
            modifier = Modifier.size(32.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
        Spacer(Modifier.width(OmegaSpacing.sm))
        Column {
            Text(
                text = "Система быстрых платежей",
                style = OmegaType.BodySemiBoldL,
                color = Color.White
            )
            Text(
                text = "Перевод по номеру телефона",
                style = OmegaType.BodyTightM,
                color = OmegaTextSecondary
            )
        }
    }
}