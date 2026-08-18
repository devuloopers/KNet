package com.devuloopers.knet.ui.core.foundation.theme

import androidx.compose.ui.graphics.Color

/**
 * Standardized HTTP Method color tokens and helper functions for KNet design system.
 */
object HttpMethodColors {
    val GetText: Color = Color(0xFF99D595)
    val GetBackground: Color = Color(0x2699D595)

    val PostText: Color = Color(0xFFE2CC9B)
    val PostBackground: Color = Color(0x26E2CC9B)

    val PutText: Color = Color(0xFF89B4FA)
    val PutBackground: Color = Color(0x2689B4FA)

    val PatchText: Color = Color(0xFFCBA6F7)
    val PatchBackground: Color = Color(0x26CBA6F7)

    val DeleteText: Color = Color(0xFFFFB4AB)
    val DeleteBackground: Color = Color(0x26FFB4AB)

    val DefaultMethodText: Color = Color(0xFFC3C6D2)
    val DefaultMethodBackground: Color = Color(0x26C3C6D2)

    /**
     * Resolves signature vibrant text color for standard HTTP methods.
     */
    fun getMethodTextColor(method: String): Color = when (method.uppercase()) {
        "GET" -> GetText
        "POST" -> PostText
        "PUT" -> PutText
        "PATCH" -> PatchText
        "DELETE" -> DeleteText
        else -> DefaultMethodText
    }

    /**
     * Resolves translucent background color for standard HTTP method badges.
     */
    fun getMethodBackgroundColor(method: String): Color = when (method.uppercase()) {
        "GET" -> GetBackground
        "POST" -> PostBackground
        "PUT" -> PutBackground
        "PATCH" -> PatchBackground
        "DELETE" -> DeleteBackground
        else -> DefaultMethodBackground
    }
}
