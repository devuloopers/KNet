package com.devuloopers.knet.ui.desktop.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Data holder for navigation destination items including label, vector icon, and optional keyboard shortcut badge.
 *
 * @property destination Target sealed destination screen.
 * @property label Display title text.
 * @property icon Material vector icon.
 * @property shortcut Optional keyboard shortcut string label (e.g. "Ctrl+1").
 */
data class NavigationDestinationInfo(
    val destination: DesktopDestination,
    val label: String,
    val icon: ImageVector,
    val shortcut: String? = null
)

/**
 * Decoupled Row Item composable for the Navigation Rail and Overlay.
 *
 * Handles item-level hover highlight, left vertical 3dp accent indicator strip, label text, and shortcut badge.
 * Has zero control over navigation container expansion/collapse state.
 *
 * @param info Destination metadata info.
 * @param isSelected Whether this destination is currently active.
 * @param isExpanded Whether the overlay panel is currently expanded.
 * @param labelAlpha Animated opacity float for labels and badges (0f to 1f).
 * @param labelOffset Animated horizontal offset Dp for smooth sliding presentation.
 * @param onSelect Callback when item is clicked or selected.
 * @param modifier Layout modifier.
 */
@Composable
fun NavigationRailRowItem(
    info: NavigationDestinationInfo,
    isSelected: Boolean,
    isExpanded: Boolean,
    labelAlpha: Float,
    labelOffset: Dp,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val containerColor = when {
        isSelected -> themeColors.interaction.selectedOverlay
        isHovered || isFocused -> themeColors.interaction.hoverOverlay
        else -> Color.Transparent
    }

    val iconColor = if (isSelected) themeColors.accent else themeColors.textSecondary
    val textColor = if (isSelected) themeColors.textPrimary else themeColors.textSecondary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            )
            .focusable(interactionSource = interactionSource)
            .handCursor(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 3dp Left Vertical Accent Indicator Strip
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                .background(if (isSelected) themeColors.accent else Color.Transparent)
        )

        // Fixed 42dp Icon Box Alignment
        Box(
            modifier = Modifier
                .width(42.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = info.icon,
                contentDescription = info.label,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }

        if (isExpanded) {
            Text(
                text = info.label,
                style = if (isSelected) {
                    typography.bodyMedium.copy(color = textColor, fontWeight = FontWeight.Bold)
                } else {
                    typography.bodyMedium.copy(color = textColor)
                },
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .offset(x = labelOffset)
                    .alpha(labelAlpha)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Right-aligned Keyboard Shortcut Badge (if present)
            if (!info.shortcut.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) themeColors.interaction.selectedOverlay else themeColors.interaction.hoverOverlay)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .alpha(labelAlpha),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = info.shortcut,
                        style = typography.caption.copy(
                            color = if (isSelected) themeColors.accent else themeColors.textMuted,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
