package com.devuloopers.knet.ui.core.components.button

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import com.devuloopers.knet.ui.core.components.menu.*
import com.devuloopers.knet.ui.core.components.tooltip.KNetTooltipHost
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import kotlinx.coroutines.delay

/**
 * Compact split copy control: labelled direct action and a themed anchored nested menu.
 * Feedback replaces a fixed-size icon; the primary segment fits its visible label and inherits
 * [KNetButton] width animation when the active response tab changes.
 * All sizing, spacing, typography and motion are design-system owned.
 */
@Composable
fun KNetSplitCopyButton(label: String, entries: List<ContextMenuEntry>, ownerKey: Any?, enabled: Boolean,
    copying: Boolean, copiedRevision: Long, error: String?, onCopy: () -> Unit,
    modifier: Modifier = Modifier) {
    val colors = KNetTheme.colors
    var copied by remember(ownerKey) { mutableStateOf(false) }
    val duration = KNetTheme.motion.durationFeedback.toLong()
    LaunchedEffect(ownerKey, copiedRevision) {
        if (copiedRevision > 0) { copied = true; delay(duration); copied = false }
    }
    val description = error ?: if (copied) "Copied" else label
    KNetTooltipHost(description = description, modifier = modifier) { interaction ->
        Row(Modifier.height(KNetTheme.dimensions.buttonHeightStandard).clip(KNetTheme.shapes.small)
            .background(colors.surfaceVariant).border(KNetTheme.dimensions.borderWidth, colors.border, KNetTheme.shapes.small),
            verticalAlignment = Alignment.CenterVertically) {
            KNetButton(onClick = onCopy, enabled = enabled && !copying, variant = ButtonVariant.Ghost,
                size = ButtonSize.Standard, interactionSource = interaction,
                modifier = Modifier.semantics { contentDescription = label; stateDescription = description }) {
                Icon(if (error != null) KNetIcons.Warning else if (copied) KNetIcons.Check else Icons.Default.ContentCopy,
                    null, Modifier.size(KNetTheme.dimensions.iconSizeMedium),
                    tint = if (error != null) colors.semantic.error else if (copied) colors.semantic.success else colors.textSecondary)
                Spacer(Modifier.width(KNetTheme.spacing.sm))
                Text(label, maxLines = 1, softWrap = false)
            }
            Box(Modifier.width(KNetTheme.dimensions.dividerThickness).fillMaxHeight().padding(vertical = KNetTheme.spacing.xs).background(colors.border))
            KNetActionMenu(entries, ownerKey, enabled) { open ->
                KNetIconButton(onClick = open, icon = KNetIcons.ChevronDown, contentDescription = "Copy options", enabled = enabled)
            }
        }
    }
}
