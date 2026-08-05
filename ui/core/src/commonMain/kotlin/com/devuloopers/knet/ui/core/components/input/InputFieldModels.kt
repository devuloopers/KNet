package com.devuloopers.knet.ui.core.components.input

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Behavior, formatting, and overflow popup configuration for KNet input fields.
 *
 * @property placeholder Dim hint text displayed when the input value is empty.
 * @property supportingText Optional supporting caption or error message below the field.
 * @property showHoverPopupOnOverflow True to trigger an anchored overflow popup when text exceeds field bounds.
 * @property hoverDebounceMs Milliseconds of stationary mouse hover required before triggering popup (default: 350ms).
 * @property autoSelectAllOnFocus True to automatically select all text when the field receives focus.
 * @property visualTransformation Visual transformation for text formatting (e.g. PasswordVisualTransformation).
 * @property keyboardOptions Keyboard input options.
 */
@Immutable
public data class InputFieldConfig(
    val placeholder: String = "",
    val supportingText: String? = null,
    val showHoverPopupOnOverflow: Boolean = true,
    val hoverDebounceMs: Long = 350L,
    val autoSelectAllOnFocus: Boolean = false,
    val visualTransformation: VisualTransformation = VisualTransformation.None,
    val keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    public companion object {
        public val Default: InputFieldConfig = InputFieldConfig()
    }
}

/**
 * Interactive & validation state flags for KNet input fields.
 *
 * @property enabled Whether input interaction is enabled.
 * @property readOnly Whether text is read-only (selectable but non-editable).
 * @property isError True to highlight the field with error border color.
 */
@Immutable
public data class InputFieldState(
    val enabled: Boolean = true,
    val readOnly: Boolean = false,
    val isError: Boolean = false
) {
    public companion object {
        public val Default: InputFieldState = InputFieldState()
    }
}

/**
 * Prefix and suffix composable content slots for KNet input fields.
 *
 * @property prefix Optional composable rendered on the left side of the input.
 * @property suffix Optional composable rendered on the right side of the input.
 */
@Immutable
public data class InputFieldSlots(
    val prefix: (@Composable () -> Unit)? = null,
    val suffix: (@Composable () -> Unit)? = null
) {
    public companion object {
        public val Empty: InputFieldSlots = InputFieldSlots()
    }
}
