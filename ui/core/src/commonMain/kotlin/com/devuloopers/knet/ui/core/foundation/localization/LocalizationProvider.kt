package com.devuloopers.knet.ui.core.foundation.localization

import androidx.compose.runtime.Immutable

/**
 * Localization configuration holder.
 */
@Immutable
data class LocalizationConfig(
    val languageCode: String = "en",
    val isRtl: Boolean = false
)

val DefaultLocalizationConfig: LocalizationConfig = LocalizationConfig()
