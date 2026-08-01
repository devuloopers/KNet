package com.devuloopers.knet.ui.core.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Standardized typography hierarchy for KNet UI components.
 */
public object KNetTypography {
    public val Title: TextStyle = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = KNetColors.TextPrimary
    )

    public val Subtitle: TextStyle = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = KNetColors.TextSecondary
    )

    public val Body: TextStyle = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = KNetColors.TextPrimary
    )

    public val BodyMuted: TextStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        color = KNetColors.TextMuted
    )

    public val Label: TextStyle = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = KNetColors.TextSecondary
    )

    public val MonospaceCode: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        color = KNetColors.TextPrimary
    )

    public val MonospaceBold: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = KNetColors.TextPrimary
    )
}
