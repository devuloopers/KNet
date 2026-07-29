package com.devuloopers.knet.ui.apistudio.view.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.apistudio.model.ApiCollection
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.editor.widget.ContextMenuItem
import com.devuloopers.knet.editor.widget.KNetContextMenuArea
import com.devuloopers.knet.theme.KNetColors
import com.devuloopers.knet.widgets.CollapsibleSection
import com.devuloopers.knet.widgets.WidgetSearchBar

/**
 * Sidebar tree navigation component displaying active saved collections and unsaved session drafts.
 *
 * @param collections List of user-created API collections.
 * @param unsavedRequests Active unsaved request draft sessions.
 * @param selectedRequestId Currently selected active request ID.
 * @param searchQuery Filter query typed into search bar.
 * @param onSearchChange Callback when search query changes.
 * @param onSelectRequest Callback when a request item is clicked.
 * @param onDeleteRequest Callback when a saved request is deleted.
 * @param onAddNewUnsavedRequest Callback when + button is clicked to create a new unsaved session.
 * @param onDeleteUnsavedRequest Callback when an unsaved session is closed.
 * @param onSaveUnsavedToCollection Callback when Save button on an unsaved session is clicked.
 * @param onDeleteCollection Callback when a collection is deleted.
 * @param onRenameCollection Callback when a collection is renamed.
 * @param onRenameRequest Callback when a saved request is renamed.
 * @param onRunCollection Callback to trigger runner suite.
 * @param onCreateCollection Callback to open create collection modal.
 * @param onImportCollection Callback to trigger import modal.
 */
@Composable
fun CollectionsTreeSidebar(
    collections: List<ApiCollection> = emptyList(),
    unsavedRequests: List<SavedApiRequest> = emptyList(),
    selectedRequestId: String,
    searchQuery: String = "",
    onSearchChange: (String) -> Unit = {},
    onSelectRequest: (SavedApiRequest) -> Unit,
    onDeleteRequest: (String) -> Unit = {},
    onAddNewUnsavedRequest: () -> Unit = {},
    onDeleteUnsavedRequest: (String) -> Unit = {},
    onSaveUnsavedToCollection: (SavedApiRequest) -> Unit = {},
    onDeleteCollection: (String) -> Unit = {},
    onRenameCollection: (collectionId: String, currentName: String) -> Unit = { _, _ -> },
    onRenameRequest: (requestId: String, currentName: String) -> Unit = { _, _ -> },
    onRunCollection: () -> Unit,
    onCreateCollection: () -> Unit,
    onImportCollection: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var collectionStateMap by remember { mutableStateOf(collections.associate { it.id to true }) }

    val filteredUnsaved = remember(unsavedRequests, searchQuery) {
        if (searchQuery.isBlank()) unsavedRequests else {
            unsavedRequests.filter {
                it.name.contains(searchQuery, ignoreCase = true) || it.url.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val filteredCollections = remember(collections, searchQuery) {
        if (searchQuery.isBlank()) collections else {
            collections.mapNotNull { col ->
                val matchingFolders = col.folders.mapNotNull { folder ->
                    val matchingReqs = folder.requests.filter {
                        it.name.contains(searchQuery, ignoreCase = true) || it.url.contains(
                            searchQuery,
                            ignoreCase = true
                        )
                    }
                    if (folder.name.contains(searchQuery, ignoreCase = true) || matchingReqs.isNotEmpty()) {
                        folder.copy(requests = matchingReqs.ifEmpty { folder.requests })
                    } else null
                }
                if (col.name.contains(searchQuery, ignoreCase = true) || matchingFolders.isNotEmpty()) {
                    col.copy(folders = matchingFolders.ifEmpty { col.folders })
                } else null
            }
        }
    }

    Box(
        modifier = modifier
            .background(KNetColors.SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            WidgetSearchBar(
                query = searchQuery,
                onQueryChange = onSearchChange,
                placeholder = "Search requests..."
            )

            Spacer(modifier = Modifier.height(10.dp))

            SidebarActionButtons(
                onCreateCollection = onCreateCollection,
                onImportCollection = onImportCollection
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                UnsavedSessionsCategory(
                    filteredUnsaved = filteredUnsaved,
                    selectedRequestId = selectedRequestId,
                    onAddNewUnsavedRequest = onAddNewUnsavedRequest,
                    onSelectRequest = onSelectRequest,
                    onSaveUnsavedToCollection = onSaveUnsavedToCollection,
                    onDeleteUnsavedRequest = onDeleteUnsavedRequest
                )

                SavedCollectionsCategory(
                    filteredCollections = filteredCollections,
                    collectionStateMap = collectionStateMap,
                    selectedRequestId = selectedRequestId,
                    onToggleExpand = { colId, isExp ->
                        collectionStateMap = collectionStateMap.toMutableMap().apply { put(colId, isExp) }
                    },
                    onRenameCollection = onRenameCollection,
                    onDeleteCollection = onDeleteCollection,
                    onRenameRequest = onRenameRequest,
                    onDeleteRequest = onDeleteRequest,
                    onSelectRequest = onSelectRequest
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            SidebarBottomRunButton(onRunCollection = onRunCollection)
        }
    }
}

@Composable
private fun SidebarBottomRunButton(onRunCollection: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(KNetColors.ActiveBlue, RoundedCornerShape(6.dp))
            .clickable { onRunCollection() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Run Collection", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}


@Composable
private fun SidebarActionButtons(
    onCreateCollection: () -> Unit,
    onImportCollection: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                .clickable { onCreateCollection() }
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("+ New Collection", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                .clickable { onImportCollection() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Import", color = KNetColors.TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun UnsavedSessionsCategory(
    filteredUnsaved: List<SavedApiRequest>,
    selectedRequestId: String,
    onAddNewUnsavedRequest: () -> Unit,
    onSelectRequest: (SavedApiRequest) -> Unit,
    onSaveUnsavedToCollection: (SavedApiRequest) -> Unit,
    onDeleteUnsavedRequest: (String) -> Unit
) {
    CollapsibleSection(
        title = "UNSAVED SESSIONS",
        badgeCount = filteredUnsaved.size,
        badgeColor = Color(0xFFF59E0B),
        isExpandedInitially = true,
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(KNetColors.ActiveBlue.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .clickable { onAddNewUnsavedRequest() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add new unsaved request",
                    tint = KNetColors.ActiveBlue,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    ) {
        if (filteredUnsaved.isEmpty()) {
            Text(
                "No active unsaved sessions",
                color = KNetColors.TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 4.dp)
            )
        } else {
            Column(
                modifier = Modifier.padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                filteredUnsaved.forEach { req ->
                    val isSelected = req.id == selectedRequestId
                    UnsavedRequestItemRow(
                        req = req,
                        isSelected = isSelected,
                        onSelectRequest = onSelectRequest,
                        onSaveUnsavedToCollection = onSaveUnsavedToCollection,
                        onDeleteUnsavedRequest = onDeleteUnsavedRequest
                    )
                }
            }
        }
    }
}

@Composable
private fun UnsavedRequestItemRow(

    req: SavedApiRequest,
    isSelected: Boolean,
    onSelectRequest: (SavedApiRequest) -> Unit,
    onSaveUnsavedToCollection: (SavedApiRequest) -> Unit,
    onDeleteUnsavedRequest: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) KNetColors.ActiveBlue.copy(alpha = 0.2f) else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .clickable { onSelectRequest(req) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RequestLabelBadgeRow(
            req = req,
            isSelected = isSelected,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(KNetColors.ActiveBlue.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                    .clickable { onSaveUnsavedToCollection(req) }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Save", color = KNetColors.ActiveBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onDeleteUnsavedRequest(req.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete unsaved request",
                    tint = KNetColors.TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun RequestLabelBadgeRow(
    req: SavedApiRequest,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = req.method.name,
            color = Color(req.method.badgeColorHex),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(34.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = req.name,
            color = if (isSelected) Color.White else KNetColors.TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SavedCollectionsCategory(
    filteredCollections: List<ApiCollection>,
    collectionStateMap: Map<String, Boolean>,
    selectedRequestId: String,
    onToggleExpand: (collectionId: String, isExpanded: Boolean) -> Unit,
    onRenameCollection: (collectionId: String, currentName: String) -> Unit,
    onDeleteCollection: (String) -> Unit,
    onRenameRequest: (requestId: String, currentName: String) -> Unit,
    onDeleteRequest: (String) -> Unit,
    onSelectRequest: (SavedApiRequest) -> Unit
) {
    CollapsibleSection(
        title = "SAVED COLLECTIONS",
        badgeCount = filteredCollections.size,
        badgeColor = KNetColors.ActiveBlue,
        isExpandedInitially = true
    ) {
        if (filteredCollections.isEmpty()) {
            Text(
                "No saved collections yet",
                color = KNetColors.TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 4.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                filteredCollections.forEach { col ->
                    val totalReqs = col.folders.sumOf { it.requests.size }
                    val isExpanded = collectionStateMap[col.id] ?: true
                    val collectionMenuItems = listOf(
                        ContextMenuItem(
                            label = "Rename Collection",
                            onClick = { onRenameCollection(col.id, col.name) }),
                        ContextMenuItem(label = "Delete Collection", onClick = { onDeleteCollection(col.id) })
                    )

                    KNetContextMenuArea(items = collectionMenuItems) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleExpand(col.id, !isExpanded) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = KNetColors.TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${col.name} ($totalReqs)",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (isExpanded) {
                        Column(
                            modifier = Modifier.padding(start = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            col.folders.forEach { folder ->
                                folder.requests.forEach { req ->
                                    val isSelected = req.id == selectedRequestId
                                    SavedRequestItemRow(
                                        req = req,
                                        isSelected = isSelected,
                                        onSelectRequest = onSelectRequest,
                                        onDeleteRequest = onDeleteRequest,
                                        onRenameRequest = onRenameRequest
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

@Composable
private fun SavedRequestItemRow(
    req: SavedApiRequest,
    isSelected: Boolean,
    onSelectRequest: (SavedApiRequest) -> Unit,
    onDeleteRequest: (String) -> Unit,
    onRenameRequest: (requestId: String, currentName: String) -> Unit
) {
    val requestMenuItems = listOf(
        ContextMenuItem(
            label = "Rename Request",
            onClick = { onRenameRequest(req.id, req.name) }),
        ContextMenuItem(label = "Delete Request", onClick = { onDeleteRequest(req.id) })
    )

    KNetContextMenuArea(items = requestMenuItems) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) KNetColors.ActiveBlue.copy(alpha = 0.2f) else Color.Transparent,
                    RoundedCornerShape(4.dp)
                )
                .clickable { onSelectRequest(req) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RequestLabelBadgeRow(
                req = req,
                isSelected = isSelected,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onDeleteRequest(req.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete request",
                    tint = KNetColors.TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
            }

        }
    }
}

