package com.devuloopers.knet.ui.core.theme

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for color tokens across KNet UI.
 *
 * Mapped to dark theme aesthetics featuring rich contrast, subtle panel borders,
 * status highlights, and text hierarchy tones.
 */
public object KNetColors {
    // Surfaces & Backgrounds
    public val BackgroundDark: Color = Color(0xFF0D1117)
    public val SurfaceDark: Color = Color(0xFF161B22)
    public val BorderDark: Color = Color(0xFF30363D)
    public val FieldDark: Color = Color(0x33000000)
    public val SelectedRowHighlight: Color = Color(0x1F2F81F7)
    public val HoverHighlight: Color = Color(0x12FFFFFF)

    // Accent Highlights
    public val ActiveBlue: Color = Color(0xFF2F81F7)
    public val SuccessGreen: Color = Color(0xFF3FB950)
    public val ErrorRed: Color = Color(0xFFF85149)
    public val WarningOrange: Color = Color(0xFFF97316)
    public val PurpleWS: Color = Color(0xFFC084FC)

    // Text Hierarchy Tones
    public val TextPrimary: Color = Color(0xFFC9D1D9)
    public val TextSecondary: Color = Color(0xFF8B949E)
    public val TextMuted: Color = Color(0xFF484F58)
}
