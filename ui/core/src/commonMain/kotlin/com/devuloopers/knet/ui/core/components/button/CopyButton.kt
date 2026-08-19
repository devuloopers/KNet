package com.devuloopers.knet.ui.core.components.button

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.clipboard.setPlainText
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private class CopyJobController {
    var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
    }
}

/**
 * Shared Copy Button primitive encapsulating the modern Compose Multiplatform Clipboard API.
 * Features a sleek, layout-stable icon crossfade with emerald spring scale animation upon copy.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun KNetCopyButton(
    textToCopy: String,
    modifier: Modifier = Modifier,
    contentDescription: String = "Copy to clipboard",
    copiedText: String? = null,
    size: Dp = KNetTheme.dimensions.iconSizeMedium,
    tint: Color = KNetTheme.colors.textSecondary,
    onCopied: (() -> Unit)? = null
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val motion = KNetTheme.motion
    val copyJobController = remember { CopyJobController() }

    var isCopied by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose(copyJobController::cancel) }

    val activeColor by animateColorAsState(
        targetValue = if (isCopied) themeColors.semantic.success else tint,
        animationSpec = tween(
            durationMillis = if (motion.animationsEnabled) motion.durationNormal else motion.durationInstant
        )
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCopied && !copiedText.isNullOrBlank()) {
            Text(
                text = copiedText,
                style = typography.caption.copy(
                    color = activeColor,
                    fontWeight = FontWeight.SemiBold
                )
            )
        } else {
            KNetIconButton(
                onClick = {
                    copyJobController.cancel()
                    copyJobController.job = coroutineScope.launch {
                        runCatching { clipboard.setPlainText(textToCopy) }
                            .onSuccess {
                                isCopied = true
                                onCopied?.invoke()
                                delay(motion.durationFeedback.toLong())
                                isCopied = false
                            }
                    }
                },
                icon = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = if (isCopied) "Copied" else contentDescription,
                tint = activeColor,
                size = size
            )
        }
    }
}

/**
 * Data model defining a single copy format option in a copy dropdown menu.
 *
 * @property label User-facing option label.
 * @property getTextToCopy Callback returning the string formatted according to this option.
 */
@Immutable
data class KNetCopyOption(
    val label: String,
    val getTextToCopy: () -> String
)

/**
 * Split/Dropdown Copy Button supporting instant direct copy click and dynamic multi-format selection.
 *
 * @param primaryTextToCopy Default string provider copied when clicking the copy icon directly.
 * @param options List of custom format [KNetCopyOption] choices shown in the dropdown menu.
 * @param modifier Composable layout modifier.
 * @param size Icon size constraint.
 * @param tint Icon tint color.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun KNetCopyDropdownButton(
    primaryTextToCopy: () -> String,
    options: List<KNetCopyOption>,
    modifier: Modifier = Modifier,
    size: Dp = KNetTheme.dimensions.iconSizeMedium,
    tint: Color = KNetTheme.colors.textSecondary
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val themeColors = KNetTheme.colors
    val motion = KNetTheme.motion
    val copyJobController = remember { CopyJobController() }

    var isCopied by remember { mutableStateOf(false) }
    var isMenuExpanded by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose(copyJobController::cancel) }

    fun performCopy(text: String) {
        copyJobController.cancel()
        copyJobController.job = coroutineScope.launch {
            runCatching { clipboard.setPlainText(text) }
                .onSuccess {
                    isCopied = true
                    delay(motion.durationFeedback.toLong())
                    isCopied = false
                }
        }
    }

    val iconColor by animateColorAsState(
        targetValue = if (isCopied) themeColors.semantic.success else tint,
        animationSpec = tween(
            durationMillis = if (motion.animationsEnabled) motion.durationNormal else motion.durationInstant
        )
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Direct Copy Action Button
        KNetIconButton(
            onClick = { performCopy(primaryTextToCopy()) },
            icon = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = if (isCopied) "Copied" else "Copy to clipboard",
            tint = iconColor,
            size = size
        )

        if (options.isNotEmpty()) {
            Box {
                KNetIconButton(
                    onClick = { isMenuExpanded = true },
                    icon = Icons.Default.ArrowDropDown,
                    contentDescription = "Copy format options",
                    tint = tint,
                    size = size
                )

                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
                                    style = KNetTheme.typography.bodySmall.copy(color = themeColors.textPrimary)
                                )
                            },
                            onClick = {
                                isMenuExpanded = false
                                performCopy(option.getTextToCopy())
                            }
                        )
                    }
                }
            }
        }
    }
}
