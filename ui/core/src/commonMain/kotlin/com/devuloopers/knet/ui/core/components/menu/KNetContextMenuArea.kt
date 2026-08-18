package com.devuloopers.knet.ui.core.components.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
data class ContextMenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val shortcut: String? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * Reusable Right-Click Context Menu Wrapper.
 *
 * Wraps any target composable with right-click tap detection and displays a styled
 * dropdown context menu with icon badges and keyboard shortcuts using [KNetTheme].
 *
 * @param items List of context menu action items.
 * @param modifier Custom layout modifier.
 * @param content Wrapped target composable content.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun KNetContextMenuArea(
    items: List<ContextMenuItem>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var popupOffset by remember { mutableStateOf(IntOffset.Zero) }
    
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Box(
        modifier = modifier.pointerInput(items) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.button == PointerButton.Secondary) {
                        val change = event.changes.firstOrNull()
                        if (change != null) {
                            popupOffset = IntOffset(change.position.x.toInt(), change.position.y.toInt())
                            expanded = true
                            change.consume()
                        }
                    }
                }
            }
        }
    ) {
        content()

        if (expanded && items.isNotEmpty()) {
            Popup(
                onDismissRequest = { expanded = false },
                offset = popupOffset,
                properties = PopupProperties(focusable = true)
            ) {
                Box(
                    modifier = Modifier
                        .shadow(8.dp, RoundedCornerShape(8.dp))
                        .background(themeColors.surfaceVariant, RoundedCornerShape(8.dp))
                        .border(1.dp, themeColors.border, RoundedCornerShape(8.dp))
                        .widthIn(min = 120.dp, max = 220.dp)
                        .padding(vertical = 4.dp)
                ) {
                    Column {
                        for (item in items) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = item.enabled) {
                                        expanded = false
                                        item.onClick()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (item.icon != null) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.label,
                                            tint = if (item.enabled) themeColors.accent else themeColors.textMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = item.label,
                                        style = typography.bodySmall,
                                        color = if (item.enabled) themeColors.textPrimary else themeColors.textMuted
                                    )
                                }

                                if (!item.shortcut.isNullOrEmpty()) {
                                    Text(
                                        text = item.shortcut,
                                        color = themeColors.textMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


