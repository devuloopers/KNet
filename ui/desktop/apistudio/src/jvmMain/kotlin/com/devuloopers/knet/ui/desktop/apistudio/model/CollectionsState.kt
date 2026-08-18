package com.devuloopers.knet.ui.desktop.apistudio.model

import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarFolderItem
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarRequestItem

/**
 * Top-level UI state DTO for API Studio Collections Sidebar and Save Request dialog.
 *
 * @property collections List of saved collection folder items observed from Room DB.
 * @property unsavedRequests List of active unsaved scratch session request items.
 * @property isSaveDialogOpen True if the Save Request modal dialog is open.
 * @property isCreateCollectionDialogOpen True if the Create Collection modal dialog is open.
 * @property isRenameDialogOpen True if the Rename Collection modal dialog is open.
 * @property renamingCollectionId Target collection ID being renamed.
 * @property renamingCollectionName Initial collection name for the rename dialog.
 * @property isRenameRequestDialogOpen True if the Rename Request modal dialog is open.
 * @property renamingRequestItem Target request item being renamed in the collection tree.
 */
data class CollectionsState(
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
