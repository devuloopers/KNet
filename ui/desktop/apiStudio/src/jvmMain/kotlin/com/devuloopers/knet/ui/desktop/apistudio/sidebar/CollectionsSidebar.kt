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
import com.devuloopers.knet.application.contract.apistudio.ApiStudioDocumentLocation
import com.devuloopers.knet.application.contract.apistudio.ApiStudioEditorId
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.ui.core.components.badge.KNetBadge
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.components.menu.ContextMenuItem
import com.devuloopers.knet.ui.core.components.menu.KNetContextMenuArea
import com.devuloopers.knet.ui.core.components.progress.CircularProgress
import com.devuloopers.knet.ui.core.components.treeview.TreeNode
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.theme.HttpMethodColors

data class SidebarRequestItem(
    val id: String,
    val name: String,
    val document: SavedApiRequest? = null,
    val workspaceDocument: ApiStudioWorkspaceDocument? = null,
    /** Semantic name/badge metadata resolved from the same canonical document. */
    val descriptor: SidebarRequestDescriptor,
    /** Non-null when this item belongs to a saved collection. Used for in-place edit routing. */
    val collectionId: String? = null,
    /** Non-null when this item belongs to a saved collection folder. Used for in-place edit routing. */
    val folderId: String? = null,
) {
    init {
        require((document == null) != (workspaceDocument == null)) {
            "A sidebar request must contain exactly one editor document."
        }
    }

    val editorId: ApiStudioEditorId
        get() = workspaceDocument?.editorId ?: ApiStudioEditorId.HTTP

    val isUnsaved: Boolean
        get() = workspaceDocument?.location is ApiStudioDocumentLocation.Unsaved ||
            (workspaceDocument == null && collectionId == null)

    val searchText: String
        get() = document?.url.orEmpty()
}

/** Minimal protocol-neutral presentation metadata required by the shared sidebar. */
data class SidebarRequestDescriptor(
    val kind: RequestKindId,
    val badgeLabel: String,
    val transportMethod: HttpMethod? = null,
)

data class SidebarFolderItem(
    val id: String,
    val collectionId: String = id,
    val name: String,
    val requests: List<SidebarRequestItem> = emptyList(),
    val isExpanded: Boolean = true
)

/**
 * Cohesive event callbacks parameter object for [CollectionsSidebar].
 */
data class CollectionsSidebarActions(
    val onRequestSelected: (SidebarRequestItem) -> Unit = {},
    val onSaveUnsavedRequest: (SidebarRequestItem) -> Unit = {},
    val onDeleteUnsavedRequest: (SidebarRequestItem) -> Unit = {},
    val onNewUnsavedSessionClicked: () -> Unit = {},
    val onRenameCollection: (SidebarFolderItem) -> Unit = {},
    val onDeleteCollection: (SidebarFolderItem) -> Unit = {},
    val onRenameSavedRequest: (SidebarRequestItem) -> Unit = {},
    val onDeleteSavedRequest: (SidebarRequestItem) -> Unit = {},
    val onSaveActiveRequest: () -> Unit = {},
    val onNewCollectionClicked: () -> Unit = {}
)

/**
 * Cohesive parameter object overload for [CollectionsSidebar].
 */
@Composable
fun CollectionsSidebar(
    state: com.devuloopers.knet.ui.desktop.apistudio.model.CollectionsState,
    actions: CollectionsSidebarActions = CollectionsSidebarActions(),
    selectedRequestId: String?,
    canSaveActiveRequest: Boolean,
    modifier: Modifier = Modifier.width(256.dp)
) {
    val themeColors = KNetTheme.colors
    Box(modifier = modifier.fillMaxHeight()) {
        CollectionsSidebarContent(
            unsavedRequests = state.unsavedRequests,
            collections = state.collections,
            selectedRequestId = selectedRequestId,
            onRequestSelected = actions.onRequestSelected,
            onSaveUnsavedRequest = actions.onSaveUnsavedRequest,
            onDeleteUnsavedRequest = actions.onDeleteUnsavedRequest,
            onNewUnsavedSessionClicked = actions.onNewUnsavedSessionClicked,
            onRenameCollection = actions.onRenameCollection,
            onDeleteCollection = actions.onDeleteCollection,
            onRenameSavedRequest = actions.onRenameSavedRequest,
            onDeleteSavedRequest = actions.onDeleteSavedRequest,
            onSaveActiveRequest = actions.onSaveActiveRequest,
            onNewCollectionClicked = actions.onNewCollectionClicked,
            canSaveActiveRequest = canSaveActiveRequest,
            modifier = Modifier.fillMaxSize()
        )
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(themeColors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgress(modifier = Modifier.size(24.dp))
            }
        }
        state.errorMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeColors.semantic.errorContainer)
                    .padding(horizontal = KNetTheme.spacing.sm, vertical = KNetTheme.spacing.xs),
                style = KNetTheme.typography.caption.copy(color = themeColors.semantic.error)
            )
        }
    }
}

/**
 * Leftmost Collections Sidebar component for KNet API Studio.
 */
@Composable
private fun CollectionsSidebarContent(
    unsavedRequests: List<SidebarRequestItem>,
    collections: List<SidebarFolderItem>,
    selectedRequestId: String?,
    onRequestSelected: (SidebarRequestItem) -> Unit,
    onSaveUnsavedRequest: (SidebarRequestItem) -> Unit = {},
    onDeleteUnsavedRequest: (SidebarRequestItem) -> Unit = {},
    onNewUnsavedSessionClicked: () -> Unit = {},
    onRenameCollection: (SidebarFolderItem) -> Unit = {},
    onDeleteCollection: (SidebarFolderItem) -> Unit = {},
    onRenameSavedRequest: (SidebarRequestItem) -> Unit = {},
    onDeleteSavedRequest: (SidebarRequestItem) -> Unit = {},
    onSaveActiveRequest: () -> Unit = {},
    onNewCollectionClicked: () -> Unit = {},
    canSaveActiveRequest: Boolean,
    modifier: Modifier = Modifier.width(256.dp)
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

            KNetButton(
                onClick = onSaveActiveRequest,
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Compact,
                enabled = canSaveActiveRequest
            ) {
                Text(
                    text = "Save",
                    style = typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }

        // 2. Search Bar
        Box(modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs)) {
            KNetTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                config = InputFieldConfig(placeholder = "Search…"),
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
                        it.searchText.contains(searchQuery, ignoreCase = true)
            }

            // Filter Saved Folders
            val filteredFolders = collections.mapNotNull { folder ->
                val matchingRequests = folder.requests.filter {
                    searchQuery.isBlank() ||
                            it.name.contains(searchQuery, ignoreCase = true) ||
                            it.searchText.contains(searchQuery, ignoreCase = true)
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
                                        val badgeColors = requestBadgeColors(req.descriptor, themeColors.accent)
                                        KNetBadge(
                                            text = req.descriptor.badgeLabel,
                                            containerColor = badgeColors.container,
                                            contentColor = badgeColors.content
                                        )
                                        Text(
                                            text = req.name,
                                            style = typography.bodySmall.copy(
                                                color = if (isSelected) themeColors.accent else themeColors.textPrimary,
                                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                            ),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                                                val badgeColors = requestBadgeColors(req.descriptor, themeColors.accent)
                                                KNetBadge(
                                                    text = req.descriptor.badgeLabel,
                                                    containerColor = badgeColors.container,
                                                    contentColor = badgeColors.content
                                                )
                                                Text(
                                                    text = req.name,
                                                    style = typography.bodySmall.copy(
                                                        color = if (isSelected) themeColors.accent else themeColors.textPrimary,
                                                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                                    ),
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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

private data class RequestBadgeColors(
    val container: Color,
    val content: Color
)

private fun requestBadgeColors(descriptor: SidebarRequestDescriptor, fallbackAccent: Color): RequestBadgeColors =
    when (descriptor.kind) {
        RequestKindId.HTTP -> RequestBadgeColors(
            container = HttpMethodColors.getMethodBackgroundColor(descriptor.transportMethod?.token.orEmpty()),
            content = HttpMethodColors.getMethodTextColor(descriptor.transportMethod?.token.orEmpty())
        )
        RequestKindId.GRAPHQL -> RequestBadgeColors(
            container = Color(0x26E879C6),
            content = Color(0xFFE879C6)
        )
        else -> RequestBadgeColors(
            container = fallbackAccent.copy(alpha = 0.15f),
            content = fallbackAccent
        )
    }
