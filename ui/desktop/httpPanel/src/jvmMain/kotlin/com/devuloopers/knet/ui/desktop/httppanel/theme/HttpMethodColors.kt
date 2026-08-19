package com.devuloopers.knet.ui.desktop.httppanel.theme

import androidx.compose.ui.graphics.Color

/** Protocol-presentation colors shared by desktop features that render HTTP methods. */
object HttpMethodColors {
    val GetText: Color = Color(0xFF5EBB73)
    val GetBackground: Color = Color(0x265EBB73)
    val PostText: Color = Color(0xFFD09A3E)
    val PostBackground: Color = Color(0x26D09A3E)
    val PutText: Color = Color(0xFF5B8DEF)
    val PutBackground: Color = Color(0x265B8DEF)
    val PatchText: Color = Color(0xFFA978D4)
    val PatchBackground: Color = Color(0x26A978D4)
    val DeleteText: Color = Color(0xFFE06C75)
    val DeleteBackground: Color = Color(0x26E06C75)
    val DefaultMethodText: Color = Color(0xFF8A94A6)
    val DefaultMethodBackground: Color = Color(0x268A94A6)

    /** Resolves the method identity color for a display token. */
    fun getMethodTextColor(method: String): Color = when (method.uppercase()) {
        "GET" -> GetText
        "POST" -> PostText
        "PUT" -> PutText
        "PATCH" -> PatchText
        "DELETE" -> DeleteText
        else -> DefaultMethodText
    }

    /** Resolves the translucent method background for a display token. */
    fun getMethodBackgroundColor(method: String): Color = when (method.uppercase()) {
        "GET" -> GetBackground
        "POST" -> PostBackground
        "PUT" -> PutBackground
        "PATCH" -> PatchBackground
        "DELETE" -> DeleteBackground
        else -> DefaultMethodBackground
    }
}
