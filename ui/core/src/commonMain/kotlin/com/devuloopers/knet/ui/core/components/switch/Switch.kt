package com.devuloopers.knet.ui.core.components.switch

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Accessible compact switch whose entire labelled row is the interaction target. */
@Composable
fun KNetSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true
) {
    val colors = KNetTheme.colors
    val shapes = KNetTheme.shapes
    val typography = KNetTheme.typography
    val motion = KNetTheme.motion
    val duration = if (motion.animationsEnabled) motion.durationNormal else motion.durationInstant
    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.surfaceVariant.copy(alpha = 0.55f)
            checked -> colors.accent
            else -> colors.surfaceVariant
        },
        animationSpec = tween(duration),
        label = "switchTrack"
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) {
            SwitchTrackWidth - SwitchThumbSize - SwitchThumbInset
        } else {
            SwitchThumbInset
        },
        animationSpec = tween(duration),
        label = "switchKnob"
    )

    Row(
        modifier = modifier
            .heightIn(min = 24.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .handCursor(enabled),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(SwitchTrackWidth)
                .height(SwitchTrackHeight)
                .clip(shapes.pill)
                .background(trackColor)
                .border(1.dp, if (checked && enabled) colors.accent else colors.border, shapes.pill),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = knobOffset)
                    .size(SwitchThumbSize)
                    .clip(CircleShape)
                    .background(if (enabled) Color.White else colors.textMuted)
            )
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

private val SwitchTrackWidth = 32.dp
private val SwitchTrackHeight = 18.dp
private val SwitchThumbSize = 14.dp
private val SwitchThumbInset = (SwitchTrackHeight - SwitchThumbSize) / 2
