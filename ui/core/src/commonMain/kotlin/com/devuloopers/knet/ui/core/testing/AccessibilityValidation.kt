package com.devuloopers.knet.ui.core.testing

import com.devuloopers.knet.ui.core.foundation.accessibility.AccessibilityDefaults

/**
 * Validation helpers for UI accessibility compliance.
 */
public object AccessibilityValidation {
    public fun validateTouchTargetSize(sizeDp: Int): Boolean {
        return sizeDp >= AccessibilityDefaults.DefaultMinTouchTargetSize
    }
}
