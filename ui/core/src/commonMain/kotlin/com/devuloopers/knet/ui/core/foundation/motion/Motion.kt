package com.devuloopers.knet.ui.core.foundation.motion

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Immutable

/**
 * Immutable motion duration and easing curve tokens.
 */
@Immutable
data class Motion(
    val durationInstant: Int = 0,
    val durationFast: Int = 100,
    val durationNormal: Int = 150,
    val durationSlow: Int = 250,
    val easingStandard: Easing = FastOutSlowInEasing,
    val easingLinear: Easing = LinearEasing
)

/**
 * Default Motion instance.
 */
val KNetMotion: Motion = Motion()
