package com.devuloopers.knet.ui.core.foundation.extensions

import androidx.compose.ui.Modifier

/**
 * Conditional modifier extension helper.
 */
public inline fun Modifier.thenIf(
    condition: Boolean,
    crossinline block: Modifier.() -> Modifier
): Modifier {
    return if (condition) this.block() else this
}
