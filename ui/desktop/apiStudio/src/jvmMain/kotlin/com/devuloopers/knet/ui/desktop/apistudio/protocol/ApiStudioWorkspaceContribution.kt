package com.devuloopers.knet.ui.desktop.apistudio.protocol

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.application.port.apistudio.ApiStudioEditorId
import com.devuloopers.knet.application.port.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.domain.request.descriptor.RequestKindId

/** Additive API Studio workspace renderer contributed by one non-HTTP protocol feature. */
interface ApiStudioWorkspaceContribution {
    val editorId: ApiStudioEditorId
    val kind: RequestKindId
    val label: String

    /** Creates an incomplete but persistable draft owned by this editor. */
    fun createInitialDocument(id: String): ApiStudioWorkspaceDocument

    /**
     * Renders either a durable document or this editor's transient blank authoring state.
     *
     * Implementations call [onDocumentCreated] after the first meaningful edit materializes a transient state.
     * The common shell can then select the new document without knowing any protocol-specific draft details.
     */
    @Composable
    fun Content(
        documentId: String?,
        onDocumentCreated: (String) -> Unit,
        modifier: Modifier,
    )
}
