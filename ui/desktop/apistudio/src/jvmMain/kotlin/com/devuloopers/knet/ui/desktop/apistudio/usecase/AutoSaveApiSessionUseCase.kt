package com.devuloopers.knet.ui.desktop.apistudio.usecase

import com.devuloopers.knet.domain.apistudio.usecase.ResolveUniqueSessionTitleUseCase
import com.devuloopers.knet.domain.collection.usecase.SaveUnsavedRequestUseCase
import com.devuloopers.knet.domain.collection.usecase.UpdateRequestInCollectionUseCase
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestDomainConverter.toDomainSavedRequest
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestEditorState
import com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext
import kotlin.uuid.Uuid

/**
 * Presentation UseCase responsible for auto-saving API Studio session edits to persistent storage.
 *
 * Encapsulates auto-save routing between unsaved draft sessions ([SaveUnsavedRequestUseCase])
 * and in-place saved collection requests ([UpdateRequestInCollectionUseCase]).
 *
 * @param saveUnsavedRequestUseCase Domain UseCase for saving unsaved request drafts to Room DB.
 * @param updateRequestInCollectionUseCase Domain UseCase for updating saved collection requests in Room DB.
 * @param resolveUniqueSessionTitleUseCase UseCase resolving non-conflicting session titles.
 */
class AutoSaveApiSessionUseCase(
    private val saveUnsavedRequestUseCase: SaveUnsavedRequestUseCase,
    private val updateRequestInCollectionUseCase: UpdateRequestInCollectionUseCase,
    private val resolveUniqueSessionTitleUseCase: ResolveUniqueSessionTitleUseCase = ResolveUniqueSessionTitleUseCase()
) {

    /**
     * Executes asynchronous auto-save for the current active request session.
     *
     * @param sessionContext Strongly-typed session context (UnsavedDraft vs SavedRequest).
     * @param editorState Current [RequestEditorState] containing fields to persist.
     * @param existingUnsavedTitles List of active unsaved session titles used for title collision resolution.
     * @param onLinkedIdAssigned Callback invoked if a new unsaved session ID and title are generated.
     */
    suspend fun execute(
        sessionContext: SessionContext,
        editorState: RequestEditorState,
        existingUnsavedTitles: List<String> = emptyList(),
        onLinkedIdAssigned: ((String, String) -> Unit)? = null
    ) {
        when (sessionContext) {
            is SessionContext.SavedRequest -> {
                val savedReq = editorState.toDomainSavedRequest(
                    id = sessionContext.requestId,
                    name = "Saved Request"
                )
                updateRequestInCollectionUseCase.execute(
                    collectionId = sessionContext.collectionId,
                    folderId = sessionContext.folderId,
                    request = savedReq
                )
            }

            is SessionContext.UnsavedDraft, SessionContext.None -> {
                val draftIdFromContext = (sessionContext as? SessionContext.UnsavedDraft)?.sessionId
                val effectiveLinkedId = editorState.linkedUnsavedId ?: draftIdFromContext ?: Uuid.random().toString()
                
                val sessionName = resolveUniqueSessionTitleUseCase.execute("Untitled Request", existingUnsavedTitles)
                if (editorState.linkedUnsavedId == null) {
                    onLinkedIdAssigned?.invoke(effectiveLinkedId, sessionName)
                }

                val savedReq = editorState.toDomainSavedRequest(
                    id = effectiveLinkedId,
                    name = sessionName
                )
                saveUnsavedRequestUseCase.execute(savedReq)
            }
        }
    }
}
