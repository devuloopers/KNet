package com.devuloopers.knet.ui.desktop.apistudio.model

import com.devuloopers.knet.application.port.apistudio.ApiStudioDocumentLocation
import com.devuloopers.knet.application.port.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptor
import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarFolderItem
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarRequestItem
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarRequestDescriptor

/**
 * Presentation mapper transforming domain collection entities into UI sidebar tree models.
 */
object SidebarTreeMapper {

    /**
     * Transforms a list of domain [ApiCollection] models into presentation [SidebarFolderItem] tree models.
     *
     * @param collections List of domain API collection entities.
     * @param describeRequest Resolves semantic presentation metadata for each canonical request.
     * @return List of formatted [SidebarFolderItem] UI items.
     */
    fun toSidebarFolders(
        collections: List<ApiCollection>,
        describeRequest: (SavedApiRequest) -> RequestDescriptor
    ): List<SidebarFolderItem> {
        return collections.flatMap { collection ->
            if (collection.folders.isNotEmpty()) {
                collection.folders.map { folder ->
                    val displayName =
                        if (collection.folders.size <= 1 || folder.name == "Requests" || folder.name == collection.name) {
                            collection.name
                        } else {
                            folder.name
                        }
                    SidebarFolderItem(
                        id = folder.id,
                        collectionId = collection.id,
                        name = displayName,
                        isExpanded = folder.isExpanded,
                        requests = folder.requests.map { req ->
                            SidebarRequestItem(
                                id = req.id,
                                name = req.name,
                                document = req,
                                descriptor = describeRequest(req).toSidebarDescriptor(),
                                collectionId = collection.id,
                                folderId = folder.id
                            )
                        }
                    )
                }
            } else {
                listOf(
                    SidebarFolderItem(
                        id = collection.id,
                        collectionId = collection.id,
                        name = collection.name,
                        isExpanded = true,
                        requests = emptyList()
                    )
                )
            }
        }
    }

    /**
     * Transforms a list of domain unsaved [SavedApiRequest] scratch items into presentation [SidebarRequestItem] models.
     *
     * @param unsavedRequests List of domain unsaved scratch request entities.
     * @param describeRequest Resolves semantic presentation metadata for each canonical request.
     * @return List of formatted [SidebarRequestItem] UI items.
     */
    fun toSidebarUnsavedRequests(
        unsavedRequests: List<SavedApiRequest>,
        describeRequest: (SavedApiRequest) -> RequestDescriptor
    ): List<SidebarRequestItem> {
        return unsavedRequests.map { req ->
            SidebarRequestItem(
                id = req.id,
                name = req.name,
                document = req,
                descriptor = describeRequest(req).toSidebarDescriptor()
            )
        }
    }

    /** Adds editor-owned opaque documents to the same collection tree used by HTTP requests. */
    fun mergeWorkspaceDocuments(
        folders: List<SidebarFolderItem>,
        httpDrafts: List<SidebarRequestItem>,
        workspaceDocuments: List<ApiStudioWorkspaceDocument>,
    ): Pair<List<SidebarFolderItem>, List<SidebarRequestItem>> {
        val workspaceItems = workspaceDocuments.map(ApiStudioWorkspaceDocument::toSidebarItem)
        val workspaceDrafts = workspaceItems.filter(SidebarRequestItem::isUnsaved)
        val savedByFolderId = workspaceItems
            .filterNot(SidebarRequestItem::isUnsaved)
            .groupBy { requireNotNull(it.folderId) }
        val mergedFolders = folders.map { folder ->
            folder.copy(requests = folder.requests + savedByFolderId[folder.id].orEmpty())
        }
        return mergedFolders to (httpDrafts + workspaceDrafts)
    }
}

private fun RequestDescriptor.toSidebarDescriptor(): SidebarRequestDescriptor = SidebarRequestDescriptor(
    kind = kind,
    badgeLabel = badgeLabel,
    transportMethod = transportMethod,
)

private fun ApiStudioWorkspaceDocument.toSidebarItem(): SidebarRequestItem {
    val savedLocation = location as? ApiStudioDocumentLocation.Collection
    return SidebarRequestItem(
        id = id,
        name = name,
        workspaceDocument = this,
        descriptor = SidebarRequestDescriptor(
            kind = requestKind,
            badgeLabel = badgeLabel,
        ),
        collectionId = savedLocation?.collectionId,
        folderId = savedLocation?.folderId,
    )
}
