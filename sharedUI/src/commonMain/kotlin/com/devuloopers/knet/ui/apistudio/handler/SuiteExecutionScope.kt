package com.devuloopers.knet.ui.apistudio.handler

/**
 * Sealed interface representing explicit execution scopes for the KNet Suite Runner.
 * Prevents accidental workspace-wide execution by requiring targeted scope declaration.
 */
sealed interface SuiteExecutionScope {

    /**
     * Executes only the currently focused active request.
     */
    data object CurrentRequest : SuiteExecutionScope

    /**
     * Executes all requests contained within a specific target folder.
     *
     * @property folderId Unique identifier of the target folder.
     */
    data class Folder(val folderId: String) : SuiteExecutionScope

    /**
     * Executes all requests contained within a single target collection.
     *
     * @property collectionId Unique identifier of the target collection.
     */
    data class Collection(val collectionId: String) : SuiteExecutionScope

    /**
     * Executes all requests contained within a specific list of explicitly selected collections.
     *
     * @property collectionIds List of unique collection identifiers.
     */
    data class Collections(val collectionIds: List<String>) : SuiteExecutionScope
}
