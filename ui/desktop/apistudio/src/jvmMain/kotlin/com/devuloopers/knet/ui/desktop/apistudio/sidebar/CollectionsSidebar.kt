package com.devuloopers.knet.ui.desktop.apistudio.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.badge.KNetBadge
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.input.KNetInputField
import com.devuloopers.knet.ui.core.components.treeview.TreeNode
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.theme.ApiStudioColors

public data class SidebarRequestItem(
    val id: String,
    val name: String,
    val method: String,
    val url: String = ""
)

public data class SidebarFolderItem(
    val id: String,
    val name: String,
    val requests: List<SidebarRequestItem> = emptyList(),
    val isExpanded: Boolean = true
)

public data class SidebarCollectionItem(
    val id: String,
    val name: String,
    val folders: List<SidebarFolderItem> = emptyList()
)

public enum class SidebarMode {
    COLLECTIONS,
    ENVIRONMENTS,
    HISTORY
}

/**
 * Leftmost Collections Sidebar component for KNet API Studio.
 */
@Composable
public fun CollectionsSidebar(
    collections: List<SidebarFolderItem>,
    selectedRequestId: String?,
    onRequestSelected: (SidebarRequestItem) -> Unit,
    onImportClicked: () -> Unit = {},
    onNewCollectionClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val spacing = KNetTheme.spacing

    var searchQuery by remember { mutableStateOf("") }
    var expandedFolders by remember {
        mutableStateOf(collections.associate { it.id to it.isExpanded })
    }

    Column(
        modifier = modifier
            .width(256.dp)
            .fillMaxHeight()
            .background(themeColors.surfaceVariant)
            .border(width = 1.dp, color = themeColors.border)
            .padding(vertical = spacing.md)
    ) {
        // 1. Sidebar Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Collections",
                style = typography.titleSmall.copy(color = themeColors.textPrimary, fontWeight = FontWeight.SemiBold)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                KNetIconButton(
                    onClick = onImportClicked,
                    icon = KNetIcons.Download,
                    contentDescription = "Import",
                    tint = themeColors.textSecondary
                )
                KNetIconButton(
                    onClick = onNewCollectionClicked,
                    icon = KNetIcons.Add,
                    contentDescription = "New Collection",
                    tint = themeColors.textSecondary
                )
            }
        }

        // 2. Search Bar
        Box(modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs)) {
            KNetInputField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search...",
                modifier = Modifier.fillMaxWidth()
            )
        }



        // 4. Tree View Area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xs)
        ) {
            val filteredFolders = collections.mapNotNull { folder ->
                val matchingRequests = folder.requests.filter {
                    searchQuery.isBlank() ||
                            it.name.contains(searchQuery, ignoreCase = true) ||
                            it.url.contains(searchQuery, ignoreCase = true)
                }
                if (searchQuery.isBlank() || matchingRequests.isNotEmpty() || folder.name.contains(searchQuery, ignoreCase = true)) {
                    folder.copy(requests = matchingRequests)
                } else null
            }

            if (filteredFolders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(spacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No collections found",
                        style = typography.caption.copy(color = themeColors.textMuted)
                    )
                }
            } else {
                filteredFolders.forEach { folder ->
                    val isExpanded = expandedFolders[folder.id] ?: true

                    // Folder Node using TreeNode
                    TreeNode(
                        label = "${folder.name} (${folder.requests.size})",
                        onClick = {
                            expandedFolders = expandedFolders.toMutableMap().apply {
                                put(folder.id, !isExpanded)
                            }
                        },
                        depth = 0,
                        isExpanded = isExpanded,
                        hasChildren = folder.requests.isNotEmpty(),
                        onToggleExpand = {
                            expandedFolders = expandedFolders.toMutableMap().apply {
                                put(folder.id, !isExpanded)
                            }
                        },
                        icon = if (isExpanded) KNetIcons.FolderOpen else KNetIcons.Folder
                    )

                    // Child Requests
                    if (isExpanded) {
                        folder.requests.forEach { req ->
                            val isSelected = req.id == selectedRequestId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .clip(shapes.small)
                                    .background(if (isSelected) themeColors.interaction.selectedOverlay else Color.Transparent)
                                    .clickable { onRequestSelected(req) }
                                    .handCursor()
                                    .padding(start = 24.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                KNetBadge(
                                    text = req.method,
                                    containerColor = ApiStudioColors.getMethodBackgroundColor(req.method),
                                    contentColor = ApiStudioColors.getMethodTextColor(req.method)
                                )
                                Text(
                                    text = req.name,
                                    style = typography.bodySmall.copy(
                                        color = if (isSelected) themeColors.accent else themeColors.textPrimary,
                                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
