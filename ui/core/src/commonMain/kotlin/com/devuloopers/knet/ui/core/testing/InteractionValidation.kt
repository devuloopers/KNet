package com.devuloopers.knet.ui.core.testing

/**
 * Validation helpers for UI interaction compliance.
 */
object InteractionValidation {
    fun isKeyShortcutValid(shortcut: String): Boolean {
        return shortcut.isNotBlank()
    }
}
