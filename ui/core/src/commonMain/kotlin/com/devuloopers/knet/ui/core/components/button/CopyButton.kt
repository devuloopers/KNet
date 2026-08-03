package com.devuloopers.knet.ui.core.components.button

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.Dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection

/**
 * Shared Copy Button primitive encapsulating the modern Compose Multiplatform Clipboard API.
 * Features a sleek, layout-stable icon crossfade with emerald spring scale animation upon copy.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun KNetCopyButton(
    textToCopy: String,
    modifier: Modifier = Modifier,
    contentDescription: String = "Copy to clipboard",
    size: Dp = KNetTheme.dimensions.iconSizeMedium,
    tint: Color = KNetTheme.colors.textSecondary,
    onCopied: (() -> Unit)? = null
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val themeColors = KNetTheme.colors

    var isCopied by remember { mutableStateOf(false) }

    val iconColor by animateColorAsState(
        targetValue = if (isCopied) themeColors.semantic.success else tint,
        animationSpec = tween(durationMillis = 200)
    )

    val iconScale by animateFloatAsState(
        targetValue = if (isCopied) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Box(
        modifier = modifier.scale(iconScale),
        contentAlignment = Alignment.Center
    ) {
        KNetIconButton(
            onClick = {
                coroutineScope.launch {
                    val clipEntry = ClipEntry(StringSelection(textToCopy))
                    clipboard.setClipEntry(clipEntry)
                    isCopied = true
                    onCopied?.invoke()
                    delay(2000)
                    isCopied = false
                }
            },
            icon = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = if (isCopied) "Copied" else contentDescription,
            tint = iconColor,
            size = size
        )
    }
}
