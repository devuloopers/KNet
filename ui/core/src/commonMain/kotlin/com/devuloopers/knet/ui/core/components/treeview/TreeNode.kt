package com.devuloopers.knet.ui.core.components.treeview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.core.components.button.KNetIconButton

import androidx.compose.foundation.layout.Spacer

/**
 * Selectable tree row with explicit expansion state and indentation.
 *
 * Negative [depth] values are treated as the root depth.
 */
@Composable
fun TreeNode(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    depth: Int = 0,
    isExpanded: Boolean = false,
    hasChildren: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    icon: ImageVector? = null,
    selected: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val bg = if (selected) themeColors.interaction.selectedOverlay else Color.Transparent
    val safeDepth = depth.coerceAtLeast(0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(bg)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .semantics {
                if (hasChildren) stateDescription = if (isExpanded) "Expanded" else "Collapsed"
            }
            .handCursor()
            .padding(start = (safeDepth * 12 + 4).dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasChildren && onToggleExpand != null) {
            KNetIconButton(
                icon = if (isExpanded) KNetIcons.ChevronDown else KNetIcons.ChevronRight,
                contentDescription = if (isExpanded) "Collapse $label" else "Expand $label",
                onClick = onToggleExpand,
                size = 24.dp,
                iconSize = 14.dp,
                tint = themeColors.textSecondary
            )
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 4.dp, end = 6.dp)
                    .size(14.dp),
                tint = themeColors.textSecondary
            )
        }
        Text(
            text = label,
            style = typography.bodySmall.copy(color = themeColors.textPrimary),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailingContent != null) {
            trailingContent()
        }
    }
}
