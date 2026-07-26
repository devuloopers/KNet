package com.devuloopers.knet.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reusable copy button that copies the provided [textToCopy] string to the system clipboard
 * upon being clicked using the modern [LocalClipboard] API, displaying a brief "Copied!" visual
 * feedback state that automatically reverts back to the copy icon after 2 seconds.
 *
 * @param textToCopy The string to copy to the system clipboard.
 * @param label Optional text label displayed next to the icon (e.g. "Copy Path"). Defaults to "Copy".
 * @param modifier Optional [Modifier] for custom layout constraints.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CopyButton(
    textToCopy: String,
    label: String = "Copy",
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000L.milliseconds)
            isCopied = false
        }
    }

    Row(
        modifier = modifier
            .clickable {
                if (textToCopy.isNotEmpty()) {
                    coroutineScope.launch {
                        clipboard.setClipEntry(ClipEntry(StringSelection(textToCopy)))
                        isCopied = true
                    }
                }
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = label,
            tint = if (isCopied) KNetColors.SuccessGreen else KNetColors.ActiveBlue,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = if (isCopied) "Copied!" else label,
            color = if (isCopied) KNetColors.SuccessGreen else KNetColors.ActiveBlue,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
