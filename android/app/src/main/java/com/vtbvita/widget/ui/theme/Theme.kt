package com.vtbvita.widget.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VTBColorScheme = lightColorScheme(
    primary              = VtbBlue,
    onPrimary            = Color.White,
    primaryContainer     = VtbBluePale,
    onPrimaryContainer   = VtbBlue,
    secondary            = VtbBlueMid,
    onSecondary          = Color.White,
    background           = Color.White,
    surface              = Color.White,
    surfaceVariant       = VtbSurfaceBg,
    onBackground         = VtbOnSurface,
    onSurface            = VtbOnSurface,
    onSurfaceVariant     = VtbSecondary,
    outline              = VtbDivider,
    error                = VtbRed,
)

@Composable
fun VTBVitaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VTBColorScheme,
        typography  = Typography,
        content     = content
    )
}
