package com.devuloopers.knet.ui.core.input

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.icon.KNetIcons
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.util.KNetClipboard
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reusable copy button component that triggers an [onCopy] callback and copies text to clipboard
 * while displaying a brief "Copied!" visual feedback state.
 *
 * Fully decoupled from platform-specific APIs.
 *
 * @param textToCopy Text string to copy to the clipboard.
 * @param label Text label displayed next to icon. Defaults to "Copy".
 * @param onCopy Optional callback triggered when clicked.
 * @param modifier Layout parameters.
 */
@Composable
public fun CopyActionButton(
    textToCopy: String,
    label: String = "Copy",
    onCopy: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
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
                    KNetClipboard.copyToClipboard(clipboardManager, textToCopy)
                    onCopy?.invoke()
                    isCopied = true
                }
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (isCopied) KNetIcons.Checkmark else KNetIcons.Copy,
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
