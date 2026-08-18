package com.devuloopers.knet.ui.core.components.treeview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

import androidx.compose.foundation.layout.Spacer

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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(bg)
            .clickable(onClick = onClick)
            .handCursor()
            .padding(start = (depth * 12 + 8).dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasChildren && onToggleExpand != null) {
            Icon(
                imageVector = if (isExpanded) KNetIcons.ChevronDown else KNetIcons.ChevronRight,
                contentDescription = "Expand",
                modifier = Modifier
                    .size(14.dp)
                    .clickable(onClick = onToggleExpand),
                tint = themeColors.textSecondary
            )
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
            style = typography.bodySmall.copy(color = themeColors.textPrimary)
        )
        if (trailingContent != null) {
            Spacer(modifier = Modifier.weight(1f))
            trailingContent()
        }
    }
}
