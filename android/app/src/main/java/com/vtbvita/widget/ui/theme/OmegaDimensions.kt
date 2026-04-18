package com.vtbvita.widget.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// ── Omega Spacing (1xBase = 4dp) ──────────────────────────────────────────

object OmegaSpacing {
    val none  = 0.dp
    val xxs   = 2.dp
    val xs    = 4.dp
    val sm    = 8.dp
    val md    = 12.dp
    val lg    = 16.dp
    val xl    = 20.dp
    val xxl   = 24.dp
    val xxxl  = 32.dp
    val xxxxl = 40.dp
    val xxxxxl = 48.dp
    val huge  = 64.dp
    val massive = 80.dp
}

// ── Omega Border Radius (xBase multiples) ──────────────────────────────────

object OmegaRadius {
    val none = RoundedCornerShape(0.dp)
    val xs   = RoundedCornerShape(4.dp)
    val sm   = RoundedCornerShape(8.dp)
    val md   = RoundedCornerShape(12.dp)
    val lg   = RoundedCornerShape(16.dp)
    val xl   = RoundedCornerShape(20.dp)
    val xxl  = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(28.dp)
    val xxxl = RoundedCornerShape(32.dp)
    val full = RoundedCornerShape(50)
}

// ── Omega Elevation ────────────────────────────────────────────────────────

object OmegaElevation {
    val none = 0.dp
    val xs   = 1.dp
    val sm   = 2.dp
    val md   = 4.dp
    val lg   = 8.dp
}

// ── Omega Stroke Widths ────────────────────────────────────────────────────

object OmegaStroke {
    val hairline = 0.5.dp
    val thin     = 1.dp
    val medium   = 1.5.dp
    val regular  = 2.dp
    val thick    = 3.dp
}

// ── Omega Icon / Element Sizes ─────────────────────────────────────────────

object OmegaSize {
    val iconXs   = 16.dp
    val iconSm   = 18.dp
    val iconMd   = 20.dp
    val iconLg   = 24.dp
    val iconXl   = 28.dp

    val avatarSm  = 40.dp
    val avatarMd  = 44.dp
    val avatarLg  = 48.dp
    val avatarXl  = 72.dp

    val buttonHeight = 56.dp
    val pillHeight   = 64.dp
    val pinKeySize   = 72.dp
    val successIcon  = 80.dp

    val pinDot    = 14.dp
    val handleBar = 36.dp
}

// ── Omega Breakpoints (for responsive) ─────────────────────────────────────

object OmegaBreakpoint {
    val compact  = 360.dp
    val medium   = 600.dp
    val expanded = 840.dp
}