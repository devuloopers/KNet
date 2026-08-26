package com.devuloopers.knet.ui.desktop.apistudio.protocol

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.application.contract.apistudio.ApiStudioEditorId
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.domain.request.descriptor.RequestKindId

/**
 * Render state supplied by one protocol editor to the common workspace host.
 *
 * The host owns document-identity gating and composition preservation. Contributions only expose their current
 * identity and protocol-specific content, so a new protocol cannot accidentally remount its editor while a transient
 * draft is being materialized.
 */
class ApiStudioWorkspaceRenderState(
    val activeDocumentId: String,
    val content: @Composable (Modifier) -> Unit,
)

/** Additive API Studio workspace renderer contributed by one non-HTTP protocol feature. */
abstract class ApiStudioWorkspaceContribution {
    abstract val editorId: ApiStudioEditorId
    abstract val kind: RequestKindId
    abstract val label: String

    /** Creates an incomplete but persistable draft owned by this editor. */
    abstract fun createInitialDocument(id: String): ApiStudioWorkspaceDocument

    /**
     * Renders either a durable document or this editor's transient blank authoring state.
     *
     * This final host keeps the existing protocol composition alive while the first meaningful edit materializes a
     * transient editor. Implementations cannot bypass the focus-preservation behavior.
     */
    @Composable
    fun Content(
        documentId: String?,
        onDocumentCreated: (String) -> Unit,
        modifier: Modifier,
    ) {
        val renderState = rememberWorkspaceContent(
            documentId = documentId,
            onDocumentCreated = onDocumentCreated,
        )
        ApiStudioWorkspaceContentGate(
            workspaceKey = editorId,
            requestedDocumentId = documentId,
            activeDocumentId = renderState.activeDocumentId,
            modifier = modifier,
        ) {
            renderState.content(Modifier.fillMaxSize())
        }
    }

    /**
     * Connects the protocol ViewModel to the shared host.
     *
     * Implementations call [onDocumentCreated] after persistence assigns the first durable identity and return that
     * identity through [ApiStudioWorkspaceRenderState.activeDocumentId].
     */
    @Composable
    protected abstract fun rememberWorkspaceContent(
        documentId: String?,
        onDocumentCreated: (String) -> Unit,
    ): ApiStudioWorkspaceRenderState
}
