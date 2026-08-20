package com.devuloopers.knet.ui.desktop.apistudio.usecase

import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.collection.usecase.SaveUnsavedRequestUseCase
import com.devuloopers.knet.domain.collection.usecase.UpdateRequestInCollectionUseCase
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestDomainConverter.toDomainSavedRequest
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestEditorState
import com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext

/**
 * Presentation UseCase responsible for auto-saving API Studio session edits to persistent storage.
 *
 * Encapsulates auto-save routing between unsaved draft sessions ([SaveUnsavedRequestUseCase])
 * and in-place saved collection requests ([UpdateRequestInCollectionUseCase]).
 *
 * @param saveUnsavedRequestUseCase Domain UseCase for saving unsaved request drafts to Room DB.
 * @param updateRequestInCollectionUseCase Domain UseCase for updating saved collection requests in Room DB.
 */
class AutoSaveApiSessionUseCase(
    private val saveUnsavedRequestUseCase: SaveUnsavedRequestUseCase,
    private val updateRequestInCollectionUseCase: UpdateRequestInCollectionUseCase
) {

    /**
     * Executes asynchronous auto-save for the current active request session.
     *
     * @param sessionContext Strongly-typed session context (UnsavedDraft vs SavedRequest).
     * @param documentTitle Stable user-visible document title that must survive auto-save.
     * @param nameOrigin Whether the title can continue following request-derived naming.
     * @param editorState Current [RequestEditorState] containing fields to persist.
     */
    suspend fun execute(
        sessionContext: SessionContext,
        documentTitle: String,
        nameOrigin: RequestNameOrigin,
        editorState: RequestEditorState
    ) {
        when (sessionContext) {
            is SessionContext.SavedRequest -> {
                val savedReq = editorState.toDomainSavedRequest(
                    id = sessionContext.requestId,
                    name = documentTitle,
                    nameOrigin = nameOrigin
                )
                updateRequestInCollectionUseCase.execute(
                    collectionId = sessionContext.collectionId,
                    folderId = sessionContext.folderId,
                    request = savedReq
                )
            }

            is SessionContext.UnsavedDraft -> {
                val savedReq = editorState.toDomainSavedRequest(
                    id = sessionContext.sessionId,
                    name = documentTitle,
                    nameOrigin = nameOrigin
                )
                saveUnsavedRequestUseCase.execute(savedReq)
            }

            SessionContext.None -> Unit
        }
    }
}
