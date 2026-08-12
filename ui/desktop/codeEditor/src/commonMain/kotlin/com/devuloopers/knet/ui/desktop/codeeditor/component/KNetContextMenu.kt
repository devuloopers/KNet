package com.devuloopers.knet.ui.desktop.codeeditor.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection

/**
 * Data class representing a single menu item in the editor context menu.
 *
 * @property label Human-readable menu item title.
 * @property shortcut Optional keyboard shortcut indicator text (e.g. "Ctrl+C").
 * @property icon Optional leading icon vector.
 * @property isEnabled True if menu item can be clicked.
 * @property onClick Interaction callback executed when clicked.
 */
data class ContextMenuItem(
    val label: String,
    val shortcut: String? = null,
    val icon: ImageVector? = null,
    val isEnabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * Custom context menu container for KNetCodeEditor.
 *
 * Listens for right-click (`PointerButton.Secondary`) mouse events and opens a floating
 * [Popup] anchored directly to mouse pointer coordinates when [items] is not empty.
 *
 * @param items List of context menu items to display.
 * @param modifier Layout modifier applied to the container.
 * @param content Wrapped target composable viewport.
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

    Box(
        modifier = modifier.pointerInput(items) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.button == PointerButton.Secondary) {
                        val change = event.changes.firstOrNull()
                        if (change != null && items.isNotEmpty()) {
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
                        .widthIn(min = 180.dp, max = 260.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E2E), shape = RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF313244), shape = RoundedCornerShape(8.dp))
                        .padding(vertical = 4.dp)
                ) {
                    Column {
                        items.forEach { item ->
                            ContextMenuItemRow(item = item, onDismiss = { expanded = false })
                        }
                    }
                }
            }
        }
    }
}

/**
 * Internal composable rendering a single row inside [KNetContextMenuArea].
 * Supports interactive mouse hover highlighting and shortcut badges.
 *
 * @param item Context menu item metadata.
 * @param onDismiss Callback to dismiss the parent popup.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ContextMenuItemRow(
    item: ContextMenuItem,
    onDismiss: () -> Unit
) {
    val isHovered = remember { mutableStateOf(false) }
    val textColor = if (item.isEnabled) {
        if (isHovered.value) Color(0xFFCDD6F4) else Color(0xFFA6ADC8)
    } else {
        Color(0xFF585B70)
    }
    val backgroundColor = if (isHovered.value && item.isEnabled) Color(0xFF45475A) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .onPointerEvent(PointerEventType.Enter) { isHovered.value = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered.value = false }
            .clickable(enabled = item.isEnabled) {
                item.onClick()
                onDismiss()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (item.icon != null) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = textColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = item.label,
                color = textColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        if (item.shortcut != null) {
            Text(
                text = item.shortcut,
                color = Color(0xFF585B70),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Helper function returning a clipboard copy lambda using modern Compose Multiplatform Clipboard API.
 * Asynchronously writes text to system clipboard via coroutines.
 *
 * @return Asynchronous copy lambda accepting target string.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberClipboardCopyAction(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    return remember(clipboard, coroutineScope) {
        { text ->
            if (text.isNotEmpty()) {
                coroutineScope.launch {
                    clipboard.setClipEntry(ClipEntry(StringSelection(text)))
                }
            }
        }
    }
}

/**
 * Helper function returning a clipboard paste lambda.
 * Reads plain text from system clipboard on Desktop JVM.
 */
@Composable
fun rememberClipboardPasteAction(): () -> String? {
    return remember {
        {
            try {
                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                if (clipboard.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                    clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
                } else null
            } catch (_: Throwable) {
                null
            }
        }
    }
}

