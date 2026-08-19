package com.devuloopers.knet.ui.core

import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdownDefaults
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdownSize
import com.devuloopers.knet.ui.core.components.dropdown.dropdownPopupProperties
import com.devuloopers.knet.ui.core.components.dropdown.shouldComposeDropdownPopup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies the stable sizing contracts shared by KNet Design System v3 components.
 */
class ComponentTest {

    @Test
    fun buttonVariantsAndSizesRemainExhaustive() {
        assertEquals(5, ButtonVariant.entries.size)
        assertEquals(3, ButtonSize.entries.size)
    }

    @Test
    fun dropdownUsesCompactUsableDimensions() {
        assertEquals(2, KNetDropdownSize.entries.size)
        assertEquals(26.dp, KNetDropdownDefaults.fieldHeight(KNetDropdownSize.Compact))
        assertEquals(36.dp, KNetDropdownDefaults.FieldHeight)
        assertEquals(36.dp, KNetDropdownDefaults.fieldHeight(KNetDropdownSize.Standard))
        assertEquals(34.dp, KNetDropdownDefaults.ItemHeight)
        assertEquals(120.dp, KNetDropdownDefaults.DefaultWidth)
        assertEquals(148.dp, KNetDropdownDefaults.MultiSelectWidth)
        assertTrue(KNetDropdownDefaults.MaxMenuHeight > KNetDropdownDefaults.ItemHeight)
    }

    @Test
    fun dropdownPopupRemainsComposedOnlyThroughItsExitTransition() {
        assertTrue(shouldComposeDropdownPopup(currentVisible = false, targetVisible = true))
        assertTrue(shouldComposeDropdownPopup(currentVisible = true, targetVisible = false))
        assertEquals(false, shouldComposeDropdownPopup(currentVisible = false, targetVisible = false))
    }

    @Test
    fun dropdownPopupFocusPolicyMatchesItsAnchorType() {
        assertTrue(dropdownPopupProperties(focusable = true).focusable)
        assertFalse(dropdownPopupProperties(focusable = false).focusable)
    }
}
