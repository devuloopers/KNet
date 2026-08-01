package com.devuloopers.knet.ui.desktop.codeeditor.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors

public data class ContextMenuItem(
    val label: String,
    val shortcut: String? = null,
    val onClick: () -> Unit
)

@Composable
internal fun rememberClipboardCopyAction(): (String) -> Unit {
    val clipboardManager = LocalClipboardManager.current
    return remember(clipboardManager) {
        { textToCopy: String ->
            clipboardManager.setText(AnnotatedString(textToCopy))
        }
    }
}

@Composable
public fun KNetContextMenuArea(
    items: List<ContextMenuItem>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
        if (items.isNotEmpty()) {
            // Internal ContextMenu wrapper
        }
    }
}
