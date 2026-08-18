package com.devuloopers.knet.ui.core.foundation.keyboard

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Key event listener helper triggering action on [Key.Escape] or [Key.Enter].
 */
fun Modifier.onKeyShortcut(
    targetKey: Key,
    onAction: () -> Unit
): Modifier {
    return this.onKeyEvent { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == targetKey) {
            onAction()
            true
        } else {
            false
        }
    }
}
