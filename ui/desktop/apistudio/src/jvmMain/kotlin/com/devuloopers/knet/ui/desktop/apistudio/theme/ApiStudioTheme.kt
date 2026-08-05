package com.devuloopers.knet.ui.desktop.apistudio.theme

import androidx.compose.ui.graphics.Color

/**
 * Color tokens for HTTP method badges and syntax highlighting in API Studio.
 */
public object ApiStudioColors {
    // HTTP Method Colors
    val GetText: Color = Color(0xFF99D595)
    val GetBackground: Color = Color(0x2699D595)

    val PostText: Color = Color(0xFFE2CC9B)
    val PostBackground: Color = Color(0x26E2CC9B)

    val PutText: Color = Color(0xFF89B4FA)
    val PutBackground: Color = Color(0x2689B4FA)

    val DeleteText: Color = Color(0xFFFFB4AB)
    val DeleteBackground: Color = Color(0x26FFB4AB)

    val DefaultMethodText: Color = Color(0xFFC3C6D2)
    val DefaultMethodBackground: Color = Color(0x26C3C6D2)

    // Code Syntax Highlighting Colors
    val CodeKey: Color = Color(0xFF89B4FA)
    val CodeString: Color = Color(0xFF99D595)
    val CodeNumber: Color = Color(0xFFE2CC9B)
    val CodeBoolean: Color = Color(0xFFFFB4AB)
    val CodeNull: Color = Color(0xFFA6ADC8)
    val CodePunctuation: Color = Color(0xFFE2E2E8)
    val LineNumberGutter: Color = Color(0xFF0C0E12)
    val LineNumberText: Color = Color(0xFF424750)

    fun getMethodTextColor(method: String): Color = when (method.uppercase()) {
        "GET" -> GetText
        "POST" -> PostText
        "PUT" -> PutText
        "DELETE" -> DeleteText
        else -> DefaultMethodText
    }

    fun getMethodBackgroundColor(method: String): Color = when (method.uppercase()) {
        "GET" -> GetBackground
        "POST" -> PostBackground
        "PUT" -> PutBackground
        "DELETE" -> DeleteBackground
        else -> DefaultMethodBackground
    }
}
