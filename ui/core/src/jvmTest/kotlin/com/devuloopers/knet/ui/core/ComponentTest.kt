package com.devuloopers.knet.ui.core

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.isKNetButtonClickable
import com.devuloopers.knet.ui.core.components.dropdown.DropdownPopupVerticalPlacement
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdownDefaults
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdownSize
import com.devuloopers.knet.ui.core.components.dropdown.dropdownPopupVerticalPlacement
import com.devuloopers.knet.ui.core.components.dropdown.dropdownPopupProperties
import com.devuloopers.knet.ui.core.components.dropdown.resolvedDropdownPopupWidth
import com.devuloopers.knet.ui.core.components.dropdown.shouldComposeDropdownPopup
import com.devuloopers.knet.ui.core.components.input.isOverflowTextPopupRequired
import com.devuloopers.knet.ui.core.components.scrollbar.shouldShowScrollbar
import com.devuloopers.knet.ui.core.foundation.interaction.DropdownExpansionCoordinator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies on the fast JVM host the stable sizing contracts shared by KNet Design System v3 components.
 */
class ComponentTest {

    @Test
    fun buttonVariantsAndSizesRemainExhaustive() {
        assertEquals(5, ButtonVariant.entries.size)
        assertEquals(3, ButtonSize.entries.size)
    }

    @Test
    fun loadingButtonInteractionRequiresExplicitOptIn() {
        assertTrue(isKNetButtonClickable(enabled = true, loading = false, clickableWhileLoading = false))
        assertFalse(isKNetButtonClickable(enabled = true, loading = true, clickableWhileLoading = false))
        assertTrue(isKNetButtonClickable(enabled = true, loading = true, clickableWhileLoading = true))
        assertFalse(isKNetButtonClickable(enabled = false, loading = true, clickableWhileLoading = true))
    }

    @Test
    fun dropdownUsesCompactUsableDimensions() {
        assertEquals(3, KNetDropdownSize.entries.size)
        assertEquals(26.dp, KNetDropdownDefaults.fieldHeight(KNetDropdownSize.Compact))
        assertEquals(36.dp, KNetDropdownDefaults.FieldHeight)
        assertEquals(36.dp, KNetDropdownDefaults.fieldHeight(KNetDropdownSize.Standard))
        assertEquals(40.dp, KNetDropdownDefaults.LargeFieldHeight)
        assertEquals(40.dp, KNetDropdownDefaults.fieldHeight(KNetDropdownSize.Large))
        assertEquals(34.dp, KNetDropdownDefaults.ItemHeight)
        assertEquals(72.dp, KNetDropdownDefaults.MinimumWidth)
        assertEquals(280.dp, KNetDropdownDefaults.MaximumWidth)
        assertEquals(120.dp, KNetDropdownDefaults.SearchableWidth)
        assertEquals(148.dp, KNetDropdownDefaults.MultiSelectWidth)
        assertEquals(8.dp, KNetDropdownDefaults.AnchorContentSpacing)
        assertEquals(72.dp, KNetDropdownDefaults.contentWidth(0.dp, KNetDropdownSize.Standard))
        assertEquals(98.dp, KNetDropdownDefaults.contentWidth(50.dp, KNetDropdownSize.Standard))
        assertEquals(280.dp, KNetDropdownDefaults.contentWidth(500.dp, KNetDropdownSize.Standard))
        assertEquals(72.dp, KNetDropdownDefaults.menuContentWidth(0.dp))
        assertEquals(93.dp, KNetDropdownDefaults.menuContentWidth(50.dp))
        assertEquals(543.dp, KNetDropdownDefaults.menuContentWidth(500.dp))
        assertTrue(KNetDropdownDefaults.MaxMenuHeight > KNetDropdownDefaults.ItemHeight)
    }

    @Test
    fun dropdownPopupRemainsComposedOnlyThroughItsExitTransition() {
        assertTrue(shouldComposeDropdownPopup(currentVisible = false, targetVisible = true))
        assertTrue(shouldComposeDropdownPopup(currentVisible = true, targetVisible = false))
        assertEquals(false, shouldComposeDropdownPopup(currentVisible = false, targetVisible = false))
    }

    @Test
    fun dropdownPopupDoesNotStealFocusFromTheNextAnchor() {
        assertFalse(dropdownPopupProperties().focusable)
    }

    @Test
    fun dropdownExpansionOwnershipHandsOffInOneOperation() {
        val coordinator = DropdownExpansionCoordinator()
        val firstOwner = Any()
        val secondOwner = Any()
        var firstCloseCount = 0
        var secondCloseCount = 0

        coordinator.open(firstOwner) { firstCloseCount++ }
        assertTrue(coordinator.ownsExpansion(firstOwner))

        assertTrue(coordinator.toggle(secondOwner) { secondCloseCount++ })
        assertEquals(1, firstCloseCount)
        assertTrue(coordinator.ownsExpansion(secondOwner))

        coordinator.release(firstOwner)
        assertTrue(coordinator.ownsExpansion(secondOwner))

        assertFalse(coordinator.toggle(secondOwner) { secondCloseCount++ })
        assertEquals(1, secondCloseCount)
        assertFalse(coordinator.ownsExpansion(secondOwner))
    }

    @Test
    fun dismissedDropdownHeaderReleaseDoesNotReopenTheSameOwner() {
        val coordinator = DropdownExpansionCoordinator()
        val owner = Any()
        var closeCount = 0

        coordinator.open(owner) { closeCount++ }
        coordinator.dismissFromPopup(owner)

        assertFalse(coordinator.toggle(owner) { closeCount++ })
        assertEquals(1, closeCount)
        assertFalse(coordinator.ownsExpansion(owner))
    }

    @Test
    fun dismissedDropdownStillHandsOffImmediatelyToAnotherOwner() {
        val coordinator = DropdownExpansionCoordinator()
        val firstOwner = Any()
        val secondOwner = Any()

        coordinator.open(firstOwner) {}
        coordinator.dismissFromPopup(firstOwner)

        assertTrue(coordinator.toggle(secondOwner) {})
        assertTrue(coordinator.ownsExpansion(secondOwner))
    }

    @Test
    fun dropdownPopupWidthGrowsBeyondAnchorAndClampsToWindow() {
        assertEquals(300, resolvedDropdownPopupWidth(180, 300, 0, 500))
        assertEquals(240, resolvedDropdownPopupWidth(180, 300, 0, 240))
        assertEquals(180, resolvedDropdownPopupWidth(180, 120, 0, 500))
    }

    @Test
    fun scrollbarAppearsOnlyForMeasuredOverflow() {
        assertFalse(
            shouldShowScrollbar(
                viewportSize = 0,
                maximumScrollOffset = Int.MAX_VALUE,
            ),
        )
        assertFalse(shouldShowScrollbar(viewportSize = 100, maximumScrollOffset = 0))
        assertTrue(shouldShowScrollbar(viewportSize = 100, maximumScrollOffset = 1))
        assertFalse(shouldShowScrollbar(canScrollBackward = false, canScrollForward = false))
        assertTrue(shouldShowScrollbar(canScrollBackward = true, canScrollForward = false))
        assertTrue(shouldShowScrollbar(canScrollBackward = false, canScrollForward = true))
    }

    @Test
    fun overflowPreviewRequiresMeasuredTextBeyondUsableWidth() {
        assertFalse(
            isOverflowTextPopupRequired(
                textWidthPx = 200,
                containerWidthPx = 0,
                horizontalContentPaddingPx = 0
            )
        )
        assertFalse(
            isOverflowTextPopupRequired(
                textWidthPx = 100,
                containerWidthPx = 116,
                horizontalContentPaddingPx = 16
            )
        )
        assertTrue(
            isOverflowTextPopupRequired(
                textWidthPx = 101,
                containerWidthPx = 116,
                horizontalContentPaddingPx = 16
            )
        )
    }

    @Test
    fun dropdownPopupPrefersBelowAndUsesAvailableSpaceWhenConstrained() {
        assertEquals(
            DropdownPopupVerticalPlacement.Below,
            dropdownPopupVerticalPlacement(
                anchorBounds = IntRect(left = 20, top = 100, right = 180, bottom = 140),
                windowHeight = 600,
                menuHeight = 220,
                verticalOffset = 4
            )
        )
        assertEquals(
            DropdownPopupVerticalPlacement.Above,
            dropdownPopupVerticalPlacement(
                anchorBounds = IntRect(left = 20, top = 480, right = 180, bottom = 520),
                windowHeight = 600,
                menuHeight = 220,
                verticalOffset = 4
            )
        )
        assertEquals(
            DropdownPopupVerticalPlacement.Below,
            dropdownPopupVerticalPlacement(
                anchorBounds = IntRect(left = 20, top = 170, right = 180, bottom = 210),
                windowHeight = 400,
                menuHeight = 300,
                verticalOffset = 4
            )
        )
    }
}
