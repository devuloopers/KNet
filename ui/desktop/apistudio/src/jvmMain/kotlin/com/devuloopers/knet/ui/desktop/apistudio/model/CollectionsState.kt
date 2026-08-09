package com.devuloopers.knet.ui.desktop.apistudio.model

import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarFolderItem
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarRequestItem

/**
 * Top-level UI state DTO for API Studio Collections Sidebar and Save Request dialog.
 */
public data class CollectionsState(
    val collections: List<SidebarFolderItem> = emptyList(),
    val unsavedRequests: List<SidebarRequestItem> = emptyList(),
    val isSaveDialogOpen: Boolean = false,
    val isCreateCollectionDialogOpen: Boolean = false,
    val isRenameDialogOpen: Boolean = false,
    val renamingCollectionId: String? = null,
    val renamingCollectionName: String = "",
    val isRenameRequestDialogOpen: Boolean = false,
    val renamingRequestItem: SidebarRequestItem? = null
)
