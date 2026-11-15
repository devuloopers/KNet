package com.devuloopers.knet.ui.core.components.menu

import androidx.compose.ui.unit.*
import kotlin.test.*

/** Shared navigation and positioning preserve the same submenu behavior across pointer and keyboard. */
class ContextMenuNavigationTest {
    private val entries = listOf(ContextMenuItem("Disabled", enabled = false) {}, ContextMenuSeparator,
        ContextMenuSubmenu("Protected Traffic", listOf(ContextMenuItem("Tunnel") {}, ContextMenuItem("Inspect") {}, ContextMenuItem("Block") {})),
        ContextMenuItem("Copy") {})

    @Test fun `keyboard wraps enabled items and skips separators and disabled actions`() {
        var state = ContextMenuNavigation().move(entries, 1)
        assertEquals(listOf(2), state.active)
        state = state.move(entries, 1); assertEquals(listOf(3), state.active)
        state = state.move(entries, 1); assertEquals(listOf(2), state.active)
        state = state.move(entries, -1); assertEquals(listOf(3), state.active)
    }

    @Test fun `enter back and escape ancestry preserve the parent selection`() {
        val parent = ContextMenuNavigation().move(entries, 1)
        val child = parent.enter(entries)
        assertEquals(listOf(2), child.opened); assertEquals(listOf(2, 0), child.active)
        assertEquals("Inspect", (child.move(entries, 1).activeEntry(entries) as ContextMenuItem).label)
        assertEquals(parent, child.back())
    }

    @Test fun `hover switches child paths without executing actions and disabled submenu remains closed`() {
        var state = ContextMenuNavigation().hover(entries, emptyList(), 2)
        assertEquals(listOf(2), state.opened)
        state = state.hover(entries, listOf(2), 1)
        assertEquals(listOf(2), state.opened)
        assertEquals(listOf(2, 1), state.active)
        state = state.hover(entries, emptyList(), 3)
        assertTrue(state.opened.isEmpty())
        val disabled = listOf(ContextMenuSubmenu("Disabled", listOf(ContextMenuItem("Leaf") {}), enabled = false))
        assertTrue(ContextMenuNavigation().hover(disabled, emptyList(), 0).opened.isEmpty())
        assertNull(menuAt(entries, listOf(3)))
        assertFailsWith<IllegalArgumentException> { ContextMenuSubmenu("Empty", emptyList()) }
    }

    @Test fun `root pointer and oversized panels stay bounded at window edges`() {
        val window = IntSize(800, 600)
        assertEquals(IntOffset(480, 400), ContextMenuPositionProvider(IntOffset(90, 25)).calculatePosition(
            IntRect(700, 560, 780, 590), window, LayoutDirection.Ltr, IntSize(320, 200)))
        assertEquals(IntOffset.Zero, ContextMenuPositionProvider(IntOffset(-10, -10)).calculatePosition(
            IntRect(0, 0, 20, 20), IntSize(100, 100), LayoutDirection.Ltr, IntSize(320, 200)))
    }

    @Test fun `submenu opens adjacent and flips left near the right edge including RTL`() {
        val provider = ContextMenuPositionProvider()
        assertEquals(IntOffset(330, 80), provider.calculatePosition(IntRect(10, 80, 330, 112), IntSize(900, 600), LayoutDirection.Ltr, IntSize(320, 120)))
        assertEquals(IntOffset(250, 480), provider.calculatePosition(IntRect(570, 550, 890, 582), IntSize(900, 600), LayoutDirection.Ltr, IntSize(320, 120)))
        assertEquals(IntOffset(250, 80), provider.calculatePosition(IntRect(570, 80, 890, 112), IntSize(900, 600), LayoutDirection.Rtl, IntSize(320, 120)))
    }
}
