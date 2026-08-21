package com.devuloopers.knet.ui.desktop.apistudio.model

import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarFolderItem
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarRequestItem

/**
 * Top-level UI state DTO for the API Studio Collections sidebar and its rename dialogs.
 *
 * @property collections List of saved collection folder items observed from Room DB.
 * @property unsavedRequests List of active unsaved scratch session request items.
 * @property isLoading True until both Room-backed collection streams have emitted.
 * @property errorMessage Non-null when sidebar persistence observation fails.
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
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isCreateCollectionDialogOpen: Boolean = false,
    val isRenameDialogOpen: Boolean = false,
    val renamingCollectionId: String? = null,
    val renamingCollectionName: String = "",
    val isRenameRequestDialogOpen: Boolean = false,
    val renamingRequestItem: SidebarRequestItem? = null
)
