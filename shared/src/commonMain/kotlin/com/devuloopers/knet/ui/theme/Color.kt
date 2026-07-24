package com.devuloopers.knet.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Custom color palette mapped exactly from KNet UI reference designs.
 */
object KNetColors {
    /** The ultimate workspace outer background (knet-dark). */
    val BackgroundDark = Color(0xFF0D1117)

    /** Main layout panel backgrounds (knet-panel). */
    val SurfaceDark = Color(0xFF161B22)

    /** Secondary borders, header strips, status bars (knet-border). */
    val BorderDark = Color(0xFF30363D)

    /** Selected list active highlighting (knet-blue with alpha). */
    val SelectedRowHighlight = Color(0x1F2F81F7)

    /** Soft background for text areas / inputs (black/20). */
    val FieldDark = Color(0x33000000)

    // Accent Highlights
    val ActiveBlue = Color(0xFF2F81F7)
    val SuccessGreen = Color(0xFF3FB950)
    val ErrorRed = Color(0xFFF85149)
    val WarningOrange = Color(0xFFF97316)
    val PurpleWS = Color(0xFFC084FC)

    // Text Tones
    val TextPrimary = Color(0xFFC9D1D9)
    val TextSecondary = Color(0xFF8B949E)
    val TextMuted = Color(0xFF484F58)
}
