package com.devuloopers.knet.ui.core.foundation.animation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

public object FadeAnimations {
    public val In: EnterTransition = fadeIn()
    public val Out: ExitTransition = fadeOut()
}
