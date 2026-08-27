package com.devuloopers.knet.companion.data.device

import com.devuloopers.knet.companion.model.CompanionDeviceDisplayName
import com.devuloopers.knet.identity.RegisteredDeviceId

/** Builds a bounded, recognizable, collision-resistant label from non-secret platform information. */
internal fun formatCompanionDeviceDisplayName(
    platformLabel: String,
    fallbackLabel: String,
    deviceId: RegisteredDeviceId,
): CompanionDeviceDisplayName {
    val suffix = deviceId.value
        .filter(Char::isLetterOrDigit)
        .takeLast(DISPLAY_SUFFIX_LENGTH)
        .uppercase()
        .ifBlank { FALLBACK_SUFFIX }
    val separator = " · "
    val maximumLabelLength = CompanionDeviceDisplayName.MAXIMUM_LENGTH - separator.length - suffix.length
    val label = platformLabel
        .filterNot(Char::isControlCharacter)
        .trim()
        .replace(WHITESPACE, " ")
        .ifBlank { fallbackLabel }
        .take(maximumLabelLength)
        .trim()
        .ifBlank { fallbackLabel.take(maximumLabelLength).trim() }
    return CompanionDeviceDisplayName("$label$separator$suffix")
}

private fun Char.isControlCharacter(): Boolean = code in 0..31 || code == 127

private const val DISPLAY_SUFFIX_LENGTH: Int = 4
private const val FALLBACK_SUFFIX: String = "KNET"
private val WHITESPACE: Regex = Regex("\\s+")
