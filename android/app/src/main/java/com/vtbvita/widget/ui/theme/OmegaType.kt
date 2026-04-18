package com.vtbvita.widget.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vtbvita.widget.R

val VtbFontFamily = FontFamily(
    Font(R.font.vtb_light,     FontWeight.Light),
    Font(R.font.vtb_book,      FontWeight.Normal),
    Font(R.font.vtb_demi_bold, FontWeight.SemiBold),
    Font(R.font.vtb_bold,      FontWeight.Bold),
)

// ── Omega Typography Scale (Mobile) ─────────────────────────────────────────
//
// Source 1: Figma REST API, 02-omg-tokens, page "test typography"
// Source 2: Figma, 03-omg-typography — tight/paragraph variants, HeadlineXS/XXS
//
// Font: Omega UI → mapped to VTB Group UI
//   Weight 500 (Medium) → SemiBold (600) as closest available weight.
//   When VTB Group UI Medium (500) becomes available, update Medium variants.
//
// Scale convention:
//   Tight    — compact line-height (≈ 1.25–1.35 ×), for single-line labels & values
//   Paragraph — generous line-height (≈ 1.47–1.67 ×), for multi-line body text
//
// Figma px → Android sp (1:1 at mdpi baseline)

object OmegaType {

    // ── Display ──────────────────────────────────────────────────────────────
    // Weight: SemiBold (600). Tight line-height throughout.

    val DisplayXL = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 48.sp,
        lineHeight = 57.sp,
        letterSpacing = 0.sp,
    )
    val DisplayL = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 45.sp,
        letterSpacing = 0.sp,
    )
    val DisplayM = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    )
    val DisplayS = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 35.sp,
        letterSpacing = 0.sp,
    )

    // ── Headline ──────────────────────────────────────────────────────────────
    // Weight: SemiBold (600 ≈ Medium 500). Tight line-height.

    val HeadlineXL = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    )
    val HeadlineL = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.sp,
    )
    val HeadlineM = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    )
    val HeadlineS = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.sp,
    )
    val HeadlineXS = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp,
    )
    val HeadlineXXS = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp,
    )

    // ── Body Tight ────────────────────────────────────────────────────────────
    // Compact line-height (≈ 1.25–1.35 ×). For single-line labels, values, chips.

    // Tight / Normal (Book 400)
    val BodyTightXL = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.sp,
    )
    val BodyTightL = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    )
    val BodyTightM = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.sp,
    )
    val BodyTightS = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp,
    )

    // Tight / Medium (500 → SemiBold 600 until Medium font is available)
    val BodyTightMediumXL = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.sp,
    )
    val BodyTightMediumL = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    )
    val BodyTightMediumM = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.sp,
    )
    val BodyTightMediumS = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp,
    )

    // Tight / SemiBold (600)
    val BodyTightSemiBoldXL = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.sp,
    )
    val BodyTightSemiBoldL = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    )
    val BodyTightSemiBoldM = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.sp,
    )
    val BodyTightSemiBoldS = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp,
    )

    // ── Body Paragraph ────────────────────────────────────────────────────────
    // Generous line-height (≈ 1.47–1.67 ×). For multi-line body text, descriptions.

    // Paragraph / Normal (Book 400)
    val BodyParagraphXL = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 29.sp,
        letterSpacing = 0.sp,
    )
    val BodyParagraphL = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )
    val BodyParagraphM = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    )
    val BodyParagraphS = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.sp,
    )

    // Paragraph / Medium (500 → SemiBold 600)
    val BodyParagraphMediumXL = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 29.sp,
        letterSpacing = 0.sp,
    )
    val BodyParagraphMediumL = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )
    val BodyParagraphMediumM = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    )
    val BodyParagraphMediumS = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.sp,
    )

    // Paragraph / SemiBold (600)
    val BodyParagraphSemiBoldXL = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 29.sp,
        letterSpacing = 0.sp,
    )
    val BodyParagraphSemiBoldL = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )
    val BodyParagraphSemiBoldM = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    )
    val BodyParagraphSemiBoldS = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.sp,
    )

    // ── Backward-compatible aliases ────────────────────────────────────────────
    // Prefer explicit Tight/Paragraph names in new code.

    @Deprecated("Use BodyTightXL for single-line or BodyParagraphXL for multi-line")
    val BodyXL = BodyTightXL

    @Deprecated("Use BodyTightL for single-line or BodyParagraphL for multi-line")
    val BodyL = BodyTightL

    @Deprecated("Use BodyTightM for single-line or BodyParagraphM for multi-line")
    val BodyM = BodyTightM

    @Deprecated("Use BodyTightS for single-line or BodyParagraphS for multi-line")
    val BodyS = BodyTightS

    @Deprecated("Use BodyTightSemiBoldL")
    val BodySemiBoldL = BodyTightSemiBoldL

    @Deprecated("Use BodyTightSemiBoldM (now 12sp per Omega spec)")
    val BodySemiBoldM = BodyTightSemiBoldM

    @Deprecated("Use BodyTightSemiBoldS (now 9sp per Omega spec)")
    val BodySemiBoldS = BodyTightSemiBoldS

    // ── App-specific (not in Omega scale) ──────────────────────────────────────

    val PinDigit = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    )
    val OverlayInput = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )
    val QuickChip = TextStyle(
        fontFamily = VtbFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    )
}

// ── Material3 Typography (mapped from Omega) ─────────────────────────────────
// bodyLarge/Medium/Small → Paragraph variants (for multi-line text)
// title/headline → Tight variants (for labels, titles, single-line)
// label → Tight/Headline variants (compact)

val OmegaTypography = Typography(
    displayLarge  = OmegaType.DisplayL,
    displayMedium = OmegaType.DisplayM,
    displaySmall  = OmegaType.DisplayS,
    headlineLarge = OmegaType.HeadlineXL,
    headlineMedium = OmegaType.HeadlineL,
    headlineSmall = OmegaType.HeadlineM,
    titleLarge    = OmegaType.BodyTightSemiBoldL,
    titleMedium   = OmegaType.BodyTightSemiBoldM,
    titleSmall    = OmegaType.BodyTightSemiBoldS,
    bodyLarge     = OmegaType.BodyParagraphXL,
    bodyMedium    = OmegaType.BodyParagraphL,
    bodySmall     = OmegaType.BodyParagraphM,
    labelLarge    = OmegaType.BodyTightMediumS,
    labelMedium   = OmegaType.BodyTightM,
    labelSmall    = OmegaType.BodyTightS,
)