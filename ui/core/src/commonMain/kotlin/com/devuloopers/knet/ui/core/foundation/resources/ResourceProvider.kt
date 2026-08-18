package com.devuloopers.knet.ui.core.foundation.resources

import androidx.compose.runtime.Immutable

/**
 * Interface defining UI asset and string resource resolution.
 * Strictly limited to UI visual and text resources (Icons, Fonts, Strings, Images).
 */
@Immutable
interface ResourceProvider {
    fun getString(key: String, vararg args: Any): String
}

/**
 * Default implementation of [ResourceProvider] providing fallback strings.
 */
object KNetResourceProvider : ResourceProvider {
    override fun getString(key: String, vararg args: Any): String {
        return if (args.isNotEmpty()) key.format(*args) else key
    }
}
