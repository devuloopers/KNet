package com.devuloopers.knet.ui.core.testing

/**
 * Validation helpers for UI interaction compliance.
 */
public object InteractionValidation {
    public fun isKeyShortcutValid(shortcut: String): Boolean {
        return shortcut.isNotBlank()
    }
}
