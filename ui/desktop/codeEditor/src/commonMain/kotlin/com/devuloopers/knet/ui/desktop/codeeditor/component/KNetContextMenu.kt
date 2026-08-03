package com.devuloopers.knet.ui.desktop.codeeditor.component

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection

/**
 * Data class representing a single menu item in the editor context menu.
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
 */
@Composable
fun KNetContextMenuArea(
    items: List<ContextMenuItem>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        content()

        if (expanded) {
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
 * Helper function to copy text to system clipboard using modern Compose Multiplatform Clipboard API.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberClipboardCopyAction(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    return remember(clipboard, coroutineScope) {
        { text ->
            coroutineScope.launch {
                clipboard.setClipEntry(ClipEntry(StringSelection(text)))
            }
        }
    }
}
