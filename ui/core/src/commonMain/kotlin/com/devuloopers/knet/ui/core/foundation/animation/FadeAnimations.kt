package com.devuloopers.knet.ui.core.foundation.animation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

object FadeAnimations {
    val In: EnterTransition = fadeIn()
    val Out: ExitTransition = fadeOut()
}
