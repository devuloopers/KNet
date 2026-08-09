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
import com.devuloopers.knet.ui.core.components.menu.ContextMenuItem
import com.devuloopers.knet.ui.core.components.menu.KNetContextMenuArea
import com.devuloopers.knet.ui.core.components.treeview.TreeNode
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.theme.ApiStudioColors

public data class SidebarRequestItem(
    val id: String,
    val name: String,
    val method: String,
    val url: String = "",
    val headers: List<Pair<String, String>> = emptyList(),
    val bodyPayload: String = "",
    val bodyType: String = "NONE",
    val preRequestScript: String = "",
    val testScript: String = "",
    /** Non-null when this item belongs to a saved collection. Used for in-place edit routing. */
    val collectionId: String? = null,
    /** Non-null when this item belongs to a saved collection folder. Used for in-place edit routing. */
    val folderId: String? = null
)

public data class SidebarFolderItem(
    val id: String,
    val collectionId: String = id,
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
 *
 * Organized into two top-level collapsible dropdown sections:
 * 1. **Unsaved Sessions**: Transient ad-hoc scratch requests.
 * 2. **Saved Collections**: Persistent user-created collections and sub-folders.
 *
 * @param unsavedRequests List of active unsaved scratch request items.
 * @param collections List of saved collection folder items.
 * @param selectedRequestId Id of currently selected request item.
 * @param onRequestSelected Callback when a request item is clicked.
 * @param onImportClicked Callback when Import action is triggered.
 * @param onNewCollectionClicked Callback when New Collection action is triggered.
 * @param modifier Composable modifier applied to sidebar layout root.
 */
@Composable
public fun CollectionsSidebar(
    unsavedRequests: List<SidebarRequestItem> = emptyList(),
    collections: List<SidebarFolderItem> = emptyList(),
    selectedRequestId: String?,
    onRequestSelected: (SidebarRequestItem) -> Unit,
    onSaveUnsavedRequest: (SidebarRequestItem) -> Unit = {},
    onDeleteUnsavedRequest: (SidebarRequestItem) -> Unit = {},
    onNewUnsavedSessionClicked: () -> Unit = {},
    onRenameCollection: (SidebarFolderItem) -> Unit = {},
    onDeleteCollection: (SidebarFolderItem) -> Unit = {},
    onRenameSavedRequest: (SidebarRequestItem) -> Unit = {},
    onDeleteSavedRequest: (SidebarRequestItem) -> Unit = {},
    onImportClicked: () -> Unit = {},
    onNewCollectionClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val spacing = KNetTheme.spacing

    var searchQuery by remember { mutableStateOf("") }
    var isUnsavedSectionExpanded by remember { mutableStateOf(true) }
    var isSavedSectionExpanded by remember { mutableStateOf(true) }

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

        HorizontalDivider(color = themeColors.border, modifier = Modifier.padding(vertical = spacing.xs))

        // 3. Dual-Section Tree Area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xs)
        ) {
            // Filter Unsaved Requests
            val filteredUnsaved = unsavedRequests.filter {
                searchQuery.isBlank() ||
                        it.name.contains(searchQuery, ignoreCase = true) ||
                        it.url.contains(searchQuery, ignoreCase = true)
            }

            // Filter Saved Folders
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

            if (filteredUnsaved.isEmpty() && filteredFolders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(spacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No collections or sessions found",
                        style = typography.caption.copy(color = themeColors.textMuted)
                    )
                }
            } else {
                // ─── Dropdown 1: Unsaved Sessions ─────────────────────────────────────
                if (filteredUnsaved.isNotEmpty() || searchQuery.isBlank()) {
                    TreeNode(
                        label = "Unsaved Sessions (${filteredUnsaved.size})",
                        onClick = { isUnsavedSectionExpanded = !isUnsavedSectionExpanded },
                        depth = 0,
                        isExpanded = isUnsavedSectionExpanded,
                        hasChildren = filteredUnsaved.isNotEmpty(),
                        onToggleExpand = { isUnsavedSectionExpanded = !isUnsavedSectionExpanded },
                        icon = if (isUnsavedSectionExpanded) KNetIcons.FolderOpen else KNetIcons.Folder,
                        trailingContent = {
                            KNetIconButton(
                                onClick = onNewUnsavedSessionClicked,
                                icon = KNetIcons.Add,
                                contentDescription = "New Unsaved Session",
                                tint = themeColors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    if (isUnsavedSectionExpanded) {
                        if (filteredUnsaved.isEmpty()) {
                            Text(
                                text = "No active unsaved sessions",
                                style = typography.caption.copy(color = themeColors.textMuted),
                                modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 4.dp)
                            )
                        } else {
                            filteredUnsaved.forEach { req ->
                                val isSelected = req.id == selectedRequestId
                                
                                val contextMenuItems = listOf(
                                    ContextMenuItem(
                                        label = "Save Request",
                                        icon = KNetIcons.Save,
                                        onClick = { onSaveUnsavedRequest(req) }
                                    ),
                                    ContextMenuItem(
                                        label = "Delete Session",
                                        icon = KNetIcons.Delete,
                                        onClick = { onDeleteUnsavedRequest(req) }
                                    )
                                )

                                KNetContextMenuArea(items = contextMenuItems) {
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

                    HorizontalDivider(color = themeColors.border.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = spacing.xs))
                }

                // ─── Dropdown 2: Saved Collections ─────────────────────────────────────
                if (filteredFolders.isNotEmpty() || searchQuery.isBlank()) {
                    TreeNode(
                        label = "Saved Collections (${filteredFolders.size})",
                        onClick = { isSavedSectionExpanded = !isSavedSectionExpanded },
                        depth = 0,
                        isExpanded = isSavedSectionExpanded,
                        hasChildren = filteredFolders.isNotEmpty(),
                        onToggleExpand = { isSavedSectionExpanded = !isSavedSectionExpanded },
                        icon = if (isSavedSectionExpanded) KNetIcons.FolderOpen else KNetIcons.Folder,
                        trailingContent = {
                            KNetIconButton(
                                onClick = onNewCollectionClicked,
                                icon = KNetIcons.Add,
                                contentDescription = "New Collection",
                                tint = themeColors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    if (isSavedSectionExpanded) {
                        if (filteredFolders.isEmpty()) {
                            Text(
                                text = "No saved collections",
                                style = typography.caption.copy(color = themeColors.textMuted),
                                modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 4.dp)
                            )
                        } else {
                            filteredFolders.forEach { folder ->
                                val isExpanded = expandedFolders[folder.id] ?: true

                                val collectionMenuItems = listOf(
                                    ContextMenuItem(
                                        label = "Rename",
                                        icon = KNetIcons.Edit,
                                        onClick = { onRenameCollection(folder) }
                                    ),
                                    ContextMenuItem(
                                        label = "Delete",
                                        icon = KNetIcons.Delete,
                                        onClick = { onDeleteCollection(folder) }
                                    )
                                )

                                // Folder Node inside Saved Collections
                                KNetContextMenuArea(items = collectionMenuItems) {
                                    TreeNode(
                                        label = "${folder.name} (${folder.requests.size})",
                                        onClick = {
                                            expandedFolders = expandedFolders.toMutableMap().apply {
                                                put(folder.id, !isExpanded)
                                            }
                                        },
                                        depth = 1,
                                        isExpanded = isExpanded,
                                        hasChildren = folder.requests.isNotEmpty(),
                                        onToggleExpand = {
                                            expandedFolders = expandedFolders.toMutableMap().apply {
                                                put(folder.id, !isExpanded)
                                            }
                                        },
                                        icon = if (isExpanded) KNetIcons.FolderOpen else KNetIcons.Folder
                                    )
                                }

                                // Nested Requests in Folder
                                if (isExpanded) {
                                    folder.requests.forEach { req ->
                                        val isSelected = req.id == selectedRequestId

                                        val savedRequestMenuItems = listOf(
                                            ContextMenuItem(
                                                label = "Rename",
                                                icon = KNetIcons.Edit,
                                                onClick = { onRenameSavedRequest(req) }
                                            ),
                                            ContextMenuItem(
                                                label = "Delete",
                                                icon = KNetIcons.Delete,
                                                onClick = { onDeleteSavedRequest(req) }
                                            )
                                        )

                                        KNetContextMenuArea(items = savedRequestMenuItems) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(28.dp)
                                                    .clip(shapes.small)
                                                    .background(if (isSelected) themeColors.interaction.selectedOverlay else Color.Transparent)
                                                    .clickable { onRequestSelected(req) }
                                                    .handCursor()
                                                    .padding(start = 36.dp, end = 8.dp),
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
            }
        }
    }
}
