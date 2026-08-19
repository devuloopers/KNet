package com.devuloopers.knet.ui.core.components.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Accessible high-density radio option with an optional label. */
@Composable
fun KNetRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true
) {
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography
    val borderColor = when {
        !enabled -> colors.border.copy(alpha = 0.55f)
        selected -> colors.accent
        else -> colors.border
    }

    Row(
        modifier = modifier
            .heightIn(min = 24.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .handCursor(enabled),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant.copy(alpha = if (enabled) 1f else 0.55f))
                .border(1.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (enabled) colors.accent else colors.textMuted)
                )
            }
        }
        if (label != null) {
            Text(
                text = label,
                style = typography.bodySmall.copy(color = if (enabled) colors.textPrimary else colors.textMuted),
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}
