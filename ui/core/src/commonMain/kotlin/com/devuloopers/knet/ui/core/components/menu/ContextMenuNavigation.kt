package com.devuloopers.knet.ui.core.components.menu

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

/** Closed set of menu rows; separators cannot accidentally execute actions. */
sealed interface ContextMenuEntry

/** A child menu opened by hover, click or keyboard; only its leaf actions execute commands. */
@Immutable
data class ContextMenuSubmenu(
    /** Visible parent label. */ val label: String,
    /** Non-empty child entries using the same menu design system. */ val items: List<ContextMenuEntry>,
    /** Whether this submenu can be opened. */ val enabled: Boolean = true,
    /** Optional leading feature icon, rendered identically to a leaf action's icon. */ val icon: ImageVector? = null,
) : ContextMenuEntry {
    init { require(items.isNotEmpty()) { "A submenu requires at least one item." } }
}

/** Non-interactive visual separation between related action groups. */
data object ContextMenuSeparator : ContextMenuEntry

/** Immutable selection and expanded ancestry, shared by pointer and keyboard navigation. */
internal data class ContextMenuNavigation(val active: List<Int> = emptyList(), val opened: List<Int> = emptyList()) {
    /** Pointer entry selects this row and replaces only the descendant expansion. */
    fun hover(entries: List<ContextMenuEntry>, prefix: List<Int>, index: Int): ContextMenuNavigation {
        val path = prefix + index
        val entry = menuAt(entries, prefix)?.getOrNull(index) ?: return this
        return copy(active = path, opened = if (entry is ContextMenuSubmenu && entry.enabled) path else prefix)
    }

    /** Up/down wrap among enabled actions and submenus, skipping separators and disabled rows. */
    fun move(entries: List<ContextMenuEntry>, delta: Int): ContextMenuNavigation {
        val prefix = active.dropLast(1)
        val menu = menuAt(entries, prefix) ?: return ContextMenuNavigation()
        val choices = menu.indices.filter { menu[it].selectable() }
        if (choices.isEmpty()) return copy(active = emptyList(), opened = prefix)
        val previous = active.lastOrNull()?.let(choices::indexOf) ?: -1
        val position = if (previous < 0) { if (delta > 0) 0 else choices.lastIndex }
            else (previous + delta + choices.size) % choices.size
        return copy(active = prefix + choices[position], opened = prefix)
    }

    /** Opens the selected submenu and transfers keyboard selection into its first enabled row. */
    fun enter(entries: List<ContextMenuEntry>): ContextMenuNavigation {
        val submenu = activeEntry(entries) as? ContextMenuSubmenu ?: return this
        if (!submenu.enabled) return this
        val first = submenu.items.indexOfFirst { it.selectable() }
        return copy(opened = active, active = if (first >= 0) active + first else active)
    }

    /** Closes one child level and returns selection to its parent. */
    fun back(): ContextMenuNavigation = when {
        active.size > 1 -> copy(active = active.dropLast(1), opened = active.dropLast(2))
        opened.isNotEmpty() -> copy(active = opened, opened = opened.dropLast(1))
        else -> this
    }

    /** Current leaf/parent entry, or null before initial selection. */
    fun activeEntry(entries: List<ContextMenuEntry>): ContextMenuEntry? =
        active.lastOrNull()?.let { menuAt(entries, active.dropLast(1))?.getOrNull(it) }
}

/** Safely resolves child ancestry without retaining another copy of action callbacks. */
internal fun menuAt(entries: List<ContextMenuEntry>, path: List<Int>): List<ContextMenuEntry>? {
    var current = entries
    for (index in path) current = (current.getOrNull(index) as? ContextMenuSubmenu)?.items ?: return null
    return current
}

private fun ContextMenuEntry.selectable(): Boolean = when (this) {
    is ContextMenuItem -> enabled
    is ContextMenuSubmenu -> enabled
    ContextMenuSeparator -> false
}

/** Pointer-root menus stay inside the window; child menus flip sides at its edges. */
internal class ContextMenuPositionProvider(private val pointer: IntOffset? = null) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
        popupContentSize: IntSize): IntOffset {
        val maximumX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maximumY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        if (pointer != null) return IntOffset((anchorBounds.left + pointer.x).coerceIn(0, maximumX),
            (anchorBounds.top + pointer.y).coerceIn(0, maximumY))
        val right = anchorBounds.right
        val left = anchorBounds.left - popupContentSize.width
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            if (right + popupContentSize.width <= windowSize.width) right else left
        } else if (left >= 0) left else right
        return IntOffset(x.coerceIn(0, maximumX), anchorBounds.top.coerceIn(0, maximumY))
    }
}
