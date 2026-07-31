package com.devuloopers.knet.ui.apistudio.handler

import com.devuloopers.knet.domain.apistudio.model.ApiCollection
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest

/**
 * Domain component responsible for resolving a [SuiteExecutionScope] into an ordered execution queue
 * of target [SavedApiRequest] instances.
 */
class SuiteExecutionPlanner {

    /**
     * Resolves the given execution scope against workspace collections into an ordered list of target requests.
     *
     * @param scope Target [SuiteExecutionScope] specifying the execution boundary.
     * @param collections Complete list of workspace [ApiCollection] instances.
     * @param currentRequest Optional currently focused active request for [SuiteExecutionScope.CurrentRequest].
     * @return Ordered list of [SavedApiRequest] instances ready for background suite execution.
     */
    fun planExecutionQueue(
        scope: SuiteExecutionScope,
        collections: List<ApiCollection>,
        currentRequest: SavedApiRequest? = null
    ): List<SavedApiRequest> {
        return when (scope) {
            is SuiteExecutionScope.CurrentRequest -> {
                currentRequest?.let { listOf(it) } ?: emptyList()
            }

            is SuiteExecutionScope.Folder -> {
                collections.flatMap { collection ->
                    collection.folders.filter { folder -> folder.id == scope.folderId }
                        .flatMap { folder -> folder.requests }
                }
            }

            is SuiteExecutionScope.Collection -> {
                collections.filter { collection -> collection.id == scope.collectionId }
                    .flatMap { collection -> collection.folders.flatMap { folder -> folder.requests } }
            }

            is SuiteExecutionScope.Collections -> {
                val targetSet = scope.collectionIds.toSet()
                collections.filter { collection -> targetSet.contains(collection.id) }
                    .flatMap { collection -> collection.folders.flatMap { folder -> folder.requests } }
            }
        }
    }
}
