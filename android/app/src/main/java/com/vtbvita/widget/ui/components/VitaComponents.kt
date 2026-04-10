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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtbvita.widget.R
import com.vtbvita.widget.ui.theme.VtbBlue
import com.vtbvita.widget.ui.theme.VtbGradientEnd
import com.vtbvita.widget.ui.theme.VtbGradientStart

// ── Карусель банков ───────────────────────────────────────────────────────────

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

@Composable
fun BankCarousel(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "Банк получателя",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(BANK_OPTIONS) { bank ->
                BankCard(
                    bank = bank,
                    isSelected = bank.id == selected,
                    onClick = { onSelect(bank.id) }
                )
            }
        }
    }
}

@Composable
private fun BankCard(
    bank: BankOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val elevation by animateFloatAsState(
        targetValue = if (isSelected) 6f else 1f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "bankCardElevation"
    )

    Box(
        modifier = Modifier
            .width(72.dp)
            .shadow(elevation.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) Color(0xFFEEF3FB) else Color.White
            )
            .border(
                BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) VtbBlue else Color(0xFFE0E4EA)
                ),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (bank.logoRes != null) {
                Image(
                    painter = painterResource(bank.logoRes),
                    contentDescription = bank.label,
                    modifier = Modifier.size(36.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Плейсхолдер для «Другой банк»
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFE8ECF0), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "?",
                        fontSize = 18.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }
            Text(
                text = bank.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) VtbBlue else Color(0xFF6B7280),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

// ── Градиентная кнопка ────────────────────────────────────────────────────────

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.5f,
        animationSpec = tween(150),
        label = "btnAlpha"
    )
    Box(
        modifier = modifier
            .height(48.dp)
            .graphicsLayer(alpha = alpha)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (enabled)
                    Brush.horizontalGradient(listOf(VtbGradientStart, VtbGradientEnd))
                else
                    Brush.horizontalGradient(listOf(Color(0xFF9EB3D4), Color(0xFF9EB3D4)))
            )
            .clickable(enabled = enabled && !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
        } else {
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

// ── Градиентный заголовок шита ────────────────────────────────────────────────

@Composable
fun SheetGradientHeader(
    title: String,
    subtitle: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(VtbGradientStart, VtbGradientEnd)),
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Column {
            // Handle bar (белый)
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
            }
        }
    }
}
