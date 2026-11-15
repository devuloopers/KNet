package com.devuloopers.knet.ui.core.components.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.pointer.platformContextMenuGesture
import com.devuloopers.knet.ui.core.foundation.pointer.platformHoverEvents
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Data class representing a single action item inside a [KNetContextMenuArea].
 *
 * @param label Human-readable menu item text.
 * @param icon Optional leading icon.
 * @param shortcut Optional keyboard shortcut badge text (e.g. "Ctrl+C").
 * @param enabled True if item is interactive.
 * @param onClick Action callback executed when clicked.
 */
@Immutable
data class ContextMenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val shortcut: String? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit
) : ContextMenuEntry

/**
 * Shared context menu with hover/click submenus, keyboard navigation and window-edge positioning.
 * Opening freezes this invocation's callbacks so incoming Traffic updates cannot replace an open action.
 * All child popups share one keyboard owner; leaf invocation dismisses the complete menu before executing.
 * @param items Typed actions, child menus and separators.
 * @param modifier Context target bounds and caller styling.
 * @param onOpen Updates caller focus/selection synchronously before the menu is displayed.
 * @param content Target receiving secondary-click or touch long-press gestures.
 */
@Composable
fun KNetContextMenuArea(
    items: List<ContextMenuEntry>,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<List<ContextMenuEntry>>(emptyList()) }
    var offset by remember { mutableStateOf(IntOffset.Zero) }
    val currentOnOpen by rememberUpdatedState(onOpen)
    fun dismiss() { expanded = false; snapshot = emptyList() }
    Box(modifier.platformContextMenuGesture(items) { pointer ->
        currentOnOpen()
        snapshot = items; offset = pointer; expanded = true
    }) {
        content()
        if (expanded && snapshot.isNotEmpty()) {
            KNetMenuPopup(snapshot, remember(offset) { ContextMenuPositionProvider(offset) }, ::dismiss)
        }
    }
}

/** Shared popup/navigation owner for secondary-click menus and anchored action dropdowns. */
@Composable
internal fun KNetMenuPopup(snapshot: List<ContextMenuEntry>, position: androidx.compose.ui.window.PopupPositionProvider,
    onDismiss: () -> Unit) {
    var navigation by remember { mutableStateOf(ContextMenuNavigation()) }
    val focus = remember { FocusRequester() }
    fun invoke(item: ContextMenuItem) { if (item.enabled) { onDismiss(); item.onClick() } }
    fun activate() {
        when (val entry = navigation.activeEntry(snapshot)) {
            is ContextMenuItem -> invoke(entry)
            is ContextMenuSubmenu -> navigation = navigation.enter(snapshot)
            else -> Unit
        }
    }
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        ContextMenuPanel(snapshot, emptyList(), navigation, { navigation = it }, ::invoke,
            Modifier.onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                    Key.DirectionDown -> { navigation = navigation.move(snapshot, 1); true }
                    Key.DirectionUp -> { navigation = navigation.move(snapshot, -1); true }
                    Key.DirectionRight -> { navigation = navigation.enter(snapshot); true }
                    Key.DirectionLeft -> { navigation = navigation.back(); true }
                    Key.Enter, Key.Spacebar -> { activate(); true }
                    Key.Escape -> { if (navigation.opened.isNotEmpty()) navigation = navigation.back() else onDismiss(); true }
                    else -> false
                }
            }.focusRequester(focus).focusable(), snapshot)
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}

/** One themed popup surface; nested non-focusable popups keep pointer and keyboard ancestry intact. */
@Composable
private fun ContextMenuPanel(items: List<ContextMenuEntry>, prefix: List<Int>, navigation: ContextMenuNavigation,
    onNavigate: (ContextMenuNavigation) -> Unit, onInvoke: (ContextMenuItem) -> Unit,
    modifier: Modifier = Modifier, root: List<ContextMenuEntry>) {
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    Column(modifier.shadow(KNetTheme.elevation.level3, KNetTheme.shapes.medium)
        .background(colors.surfaceVariant, KNetTheme.shapes.medium)
        .border(KNetTheme.dimensions.borderWidth, colors.border, KNetTheme.shapes.medium)
        .widthIn(min = KNetTheme.dimensions.contextMenuMinWidth, max = KNetTheme.dimensions.contextMenuMaxWidth)
        .padding(vertical = spacing.xs)) {
        items.forEachIndexed { index, entry ->
            if (entry == ContextMenuSeparator) {
                Box(Modifier.fillMaxWidth().padding(vertical = spacing.xs).height(KNetTheme.dimensions.dividerThickness).background(colors.border))
            } else {
                val submenu = entry as? ContextMenuSubmenu
                val action = entry as? ContextMenuItem
                val enabled = submenu?.enabled ?: action!!.enabled
                val label = submenu?.label ?: action!!.label
                val path = prefix + index
                val selected = navigation.active == path || navigation.opened.take(path.size) == path
                Box {
                    Row(Modifier.fillMaxWidth()
                        .background(if (selected) colors.interaction.hoverOverlay else androidx.compose.ui.graphics.Color.Transparent)
                        .platformHoverEvents(onEnter = { onNavigate(navigation.hover(root, prefix, index)) }, onMove = {}, onExit = {})
                        .clickable(enabled = enabled, role = Role.Button) {
                            if (submenu != null) onNavigate(navigation.hover(root, prefix, index)) else onInvoke(action!!)
                        }.handCursor(enabled).padding(horizontal = spacing.md, vertical = spacing.sm),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        (submenu?.icon ?: action?.icon)?.let { icon -> Icon(icon, null, Modifier.size(KNetTheme.dimensions.iconSizeSmall),
                            tint = if (enabled) colors.accent else colors.textMuted) }
                        Text(label, Modifier.weight(1f), style = KNetTheme.typography.bodySmall,
                            color = if (enabled) colors.textPrimary else colors.textMuted,
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        if (submenu != null) Icon(KNetIcons.ChevronRight, null, Modifier.size(KNetTheme.dimensions.iconSizeSmall), tint = colors.textSecondary)
                        else action?.shortcut?.takeIf { it.isNotEmpty() }?.let { shortcut -> Text(shortcut, color = colors.textMuted,
                            fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false) }
                    }
                    if (submenu != null && enabled && navigation.opened.take(path.size) == path) {
                        Popup(popupPositionProvider = remember { ContextMenuPositionProvider() },
                            onDismissRequest = { onNavigate(navigation.copy(opened = prefix, active = path)) },
                            properties = PopupProperties(focusable = false)) {
                            ContextMenuPanel(submenu.items, path, navigation, onNavigate, onInvoke, root = root)
                        }
                    }
                }
            }
        }
    }
}
