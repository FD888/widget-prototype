package com.vtbvita.widget.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OmegaDarkColorScheme = darkColorScheme(
    primary            = OmegaBrandPrimary,       // #0160EC — brand blue
    onPrimary          = Color.White,              // white on blue
    primaryContainer   = OmegaBrandDark,           // #00358A — deep blue container
    onPrimaryContainer = Color.White,              // white on deep blue

    secondary          = OmegaBrandAccent,          // #3E84FF — lighter blue accent
    onSecondary        = Color.White,
    secondaryContainer = OmegaChip,                 // #3F4650 — chip background
    onSecondaryContainer = Color.White,

    tertiary           = OmegaSuccess,             // #4ED52F — green for success states
    onTertiary         = Color.Black,
    tertiaryContainer = OmegaSuccessBg,            // #DDFFD6 — light green bg
    onTertiaryContainer = Color.Black,

    background         = OmegaBackground,           // #1E2024 — screen background
    onBackground       = Color.White,
    surface            = OmegaSurface,              // #22252B — card/sheet surface
    onSurface          = Color.White,
    surfaceVariant     = OmegaSurfaceAlt,          // #2F343C — elevated surface
    onSurfaceVariant   = OmegaTextSecondary,       // #7C8798 — secondary text

    outline            = OmegaChip,                 // #3F4650 — dividers, borders
    outlineVariant    = OmegaSurfaceAlt,           // #2F343C — subtle borders

    error              = OmegaError,                // #E6163E — error red
    onError            = Color.White,
    errorContainer    = OmegaErrorBg,              // #FFEFEF — light error bg
    onErrorContainer   = OmegaError,                // #E6163E — error text on light bg

    scrim              = Color.Black,

    surfaceTint        = OmegaBrandPrimary,         // #0160EC — ripple tint
    inverseSurface     = Color.White,              // inverse: white surface
    inverseOnSurface   = OmegaBackground,           // #1E2024 — dark text on white
    inversePrimary     = OmegaBrandDark,            // #00358A — dark blue on light bg
)

@Composable
fun VTBVitaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OmegaDarkColorScheme,
        typography  = OmegaTypography,
        content     = content
    )
}