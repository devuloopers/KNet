package com.devuloopers.knet.ui.desktop.workspace.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Tree node model for generic hierarchical tree views in KNet explorers.
 */
data class TreeNode<T>(
    val id: String,
    val label: String,
    val data: T? = null,
    val icon: ImageVector? = null,
    val children: List<TreeNode<T>> = emptyList()
)

/**
 * Reusable tree view composable supporting expand/collapse, node selection, and nested rendering.
 *
 * @param nodes List of root tree nodes.
 * @param expandedNodeIds Set of currently expanded node IDs.
 * @param selectedNodeId Currently selected node ID.
 * @param onNodeToggle Callback when expand arrow is toggled.
 * @param onNodeSelect Callback when a node is selected.
 * @param modifier Layout modifier.
 */
@Composable
fun <T> ExplorerTree(
    nodes: List<TreeNode<T>>,
    expandedNodeIds: Set<String>,
    selectedNodeId: String?,
    onNodeToggle: (String) -> Unit,
    onNodeSelect: (TreeNode<T>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        nodes.forEach { node ->
            TreeNodeRow(
                node = node,
                depth = 0,
                expandedNodeIds = expandedNodeIds,
                selectedNodeId = selectedNodeId,
                onNodeToggle = onNodeToggle,
                onNodeSelect = onNodeSelect
            )
        }
    }
}

@Composable
private fun <T> TreeNodeRow(
    node: TreeNode<T>,
    depth: Int,
    expandedNodeIds: Set<String>,
    selectedNodeId: String?,
    onNodeToggle: (String) -> Unit,
    onNodeSelect: (TreeNode<T>) -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    val isExpanded = expandedNodeIds.contains(node.id)
    val isSelected = node.id == selectedNodeId
    val hasChildren = node.children.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isSelected) themeColors.interaction.selectedOverlay else themeColors.surface,
                shape = shapes.small
            )
            .handCursor()
            .clickable { onNodeSelect(node) }
            .padding(start = (depth * 12 + 6).dp, top = 4.dp, bottom = 4.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (hasChildren) {
            Icon(
                imageVector = if (isExpanded) KNetIcons.ChevronDown else KNetIcons.ChevronRight,
                contentDescription = "Expand tree node",
                tint = themeColors.textSecondary,
                modifier = Modifier
                    .size(10.dp)
                    .handCursor()
                    .clickable { onNodeToggle(node.id) }
            )
        } else {
            Box(modifier = Modifier.size(10.dp))
        }

        if (node.icon != null) {
            Icon(
                imageVector = node.icon,
                contentDescription = node.label,
                tint = if (isSelected) themeColors.accent else themeColors.textSecondary,
                modifier = Modifier.size(12.dp)
            )
        }

        Text(
            text = node.label,
            style = typography.caption.copy(
                color = if (isSelected) themeColors.accent else themeColors.textPrimary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (hasChildren && isExpanded) {
        node.children.forEach { child ->
            TreeNodeRow(
                node = child,
                depth = depth + 1,
                expandedNodeIds = expandedNodeIds,
                selectedNodeId = selectedNodeId,
                onNodeToggle = onNodeToggle,
                onNodeSelect = onNodeSelect
            )
        }
    }
}

