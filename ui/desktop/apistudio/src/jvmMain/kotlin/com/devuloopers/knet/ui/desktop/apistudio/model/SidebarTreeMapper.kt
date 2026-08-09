package com.devuloopers.knet.ui.desktop.apistudio.model

import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarFolderItem
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarRequestItem

/**
 * Presentation mapper transforming domain collection entities into UI sidebar tree models.
 */
object SidebarTreeMapper {

    /**
     * Transforms a list of domain [ApiCollection] models into presentation [SidebarFolderItem] tree models.
     *
     * @param collections List of domain API collection entities.
     * @return List of formatted [SidebarFolderItem] UI items.
     */
    fun toSidebarFolders(collections: List<ApiCollection>): List<SidebarFolderItem> {
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
                                method = req.methodString,
                                url = req.url,
                                headers = req.headers.map { it.key to it.value },
                                bodyPayload = req.body.content,
                                bodyType = req.body.type,
                                preRequestScript = req.scripts.preRequest,
                                testScript = req.scripts.test,
                                authState = AuthDomainMapper.mapDomainAuthToAuthState(req.auth),
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
     * @return List of formatted [SidebarRequestItem] UI items.
     */
    fun toSidebarUnsavedRequests(unsavedRequests: List<SavedApiRequest>): List<SidebarRequestItem> {
        return unsavedRequests.map { req ->
            SidebarRequestItem(
                id = req.id,
                name = req.name,
                method = req.methodString,
                url = req.url,
                headers = req.headers.map { it.key to it.value },
                bodyPayload = req.body.content,
                bodyType = req.body.type,
                preRequestScript = req.scripts.preRequest,
                testScript = req.scripts.test,
                authState = AuthDomainMapper.mapDomainAuthToAuthState(req.auth)
            )
        }
    }
}
