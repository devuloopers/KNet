package com.devuloopers.knet.ui.core.foundation.localization

import androidx.compose.runtime.Immutable

/**
 * Localization configuration holder.
 */
@Immutable
public data class LocalizationConfig(
    val languageCode: String = "en",
    val isRtl: Boolean = false
)

public val DefaultLocalizationConfig: LocalizationConfig = LocalizationConfig()
