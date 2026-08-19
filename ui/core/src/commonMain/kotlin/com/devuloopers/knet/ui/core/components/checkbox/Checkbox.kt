package com.devuloopers.knet.ui.core.components.checkbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Accessible high-density checkbox with an optional label.
 *
 * @param checked Whether the checkbox currently represents the selected state.
 * @param onCheckedChange Invoked with the requested state when the control is activated.
 * @param modifier Modifier applied to the complete interactive row.
 * @param label Optional text rendered after the checkbox indicator.
 * @param enabled Whether the control accepts input and uses enabled presentation colors.
 */
@Composable
fun KNetCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true
) {
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography

    Row(
        modifier = modifier
            .heightIn(min = 24.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .handCursor(enabled),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KNetCheckboxIndicator(checked = checked, enabled = enabled)
        if (label != null) {
            Text(
                text = label,
                style = typography.bodySmall.copy(color = if (enabled) colors.textPrimary else colors.textMuted),
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/** Non-interactive visual indicator used when a parent row owns checkbox semantics and input. */
@Composable
internal fun KNetCheckboxIndicator(
    checked: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = KNetTheme.colors
    val shapes = KNetTheme.shapes
    val containerColor = when {
        !enabled -> colors.surfaceVariant.copy(alpha = 0.55f)
        checked -> colors.accent
        else -> colors.surfaceVariant
    }
    val borderColor = when {
        !enabled -> colors.border.copy(alpha = 0.55f)
        checked -> colors.accent
        else -> colors.border
    }

    Box(
        modifier = modifier
            .size(16.dp)
            .clip(shapes.small)
            .background(containerColor)
            .border(1.dp, borderColor, shapes.small),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = KNetIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (enabled) Color.White else colors.textMuted
            )
        }
    }
}
