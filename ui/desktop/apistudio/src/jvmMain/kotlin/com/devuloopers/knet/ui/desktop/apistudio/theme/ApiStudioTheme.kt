package com.devuloopers.knet.ui.desktop.apistudio.theme

import androidx.compose.ui.graphics.Color
import com.devuloopers.knet.ui.desktop.httppanel.theme.HttpMethodColors

/**
 * Color tokens for HTTP method badges and syntax highlighting in API Studio.
 */
object ApiStudioColors {
    // HTTP Method Colors delegated to :ui:core design system
    val GetText: Color get() = HttpMethodColors.GetText
    val GetBackground: Color get() = HttpMethodColors.GetBackground

    val PostText: Color get() = HttpMethodColors.PostText
    val PostBackground: Color get() = HttpMethodColors.PostBackground

    val PutText: Color get() = HttpMethodColors.PutText
    val PutBackground: Color get() = HttpMethodColors.PutBackground

    val DeleteText: Color get() = HttpMethodColors.DeleteText
    val DeleteBackground: Color get() = HttpMethodColors.DeleteBackground

    val DefaultMethodText: Color get() = HttpMethodColors.DefaultMethodText
    val DefaultMethodBackground: Color get() = HttpMethodColors.DefaultMethodBackground

    // Code Syntax Highlighting Colors
    val CodeKey: Color = Color(0xFF89B4FA)
    val CodeString: Color = Color(0xFF99D595)
    val CodeNumber: Color = Color(0xFFE2CC9B)
    val CodeBoolean: Color = Color(0xFFFFB4AB)
    val CodeNull: Color = Color(0xFFA6ADC8)
    val CodePunctuation: Color = Color(0xFFE2E2E8)
    val LineNumberGutter: Color = Color(0xFF0C0E12)
    val LineNumberText: Color = Color(0xFF424750)

    fun getMethodTextColor(method: String): Color = HttpMethodColors.getMethodTextColor(method)
    fun getMethodBackgroundColor(method: String): Color = HttpMethodColors.getMethodBackgroundColor(method)
}
