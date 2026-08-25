package com.devuloopers.knet.ui.desktop.apistudio.websocket

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.devuloopers.knet.application.port.apistudio.ApiStudioEditorId
import com.devuloopers.knet.application.port.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.ui.desktop.apistudio.protocol.ApiStudioWorkspaceContribution
import com.devuloopers.knet.ui.desktop.apistudio.protocol.ApiStudioWorkspaceRenderState
import com.devuloopers.knet.ui.desktop.apistudio.websocket.model.WebSocketStudioState
import com.devuloopers.knet.ui.desktop.apistudio.websocket.persistence.WebSocketWorkspaceDraftCodec
import com.devuloopers.knet.ui.desktop.apistudio.websocket.view.WebSocketStudioScreen
import com.devuloopers.knet.ui.desktop.apistudio.websocket.viewmodel.WebSocketStudioViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Product-discoverable WebSocket workspace contribution. */
class WebSocketApiStudioWorkspaceContribution(
    private val draftCodec: WebSocketWorkspaceDraftCodec,
) : ApiStudioWorkspaceContribution() {
    override val editorId: ApiStudioEditorId = ApiStudioEditorId.WEBSOCKET
    override val kind: RequestKindId = RequestKindId.WEBSOCKET
    override val label: String = "WebSocket"

    override fun createInitialDocument(id: String): ApiStudioWorkspaceDocument =
        draftCodec.unsavedDocument(WebSocketStudioState(documentId = id))

    @Composable
    protected override fun rememberWorkspaceContent(
        documentId: String?,
        onDocumentCreated: (String) -> Unit,
    ): ApiStudioWorkspaceRenderState {
        val viewModel: WebSocketStudioViewModel = koinViewModel()
        val state by viewModel.state.collectAsState()
        val currentOnDocumentCreated = rememberUpdatedState(onDocumentCreated)
        LaunchedEffect(documentId) { viewModel.openWorkspaceDocument(documentId) }
        LaunchedEffect(viewModel) {
            viewModel.materializedDocumentIds.collect { id -> currentOnDocumentCreated.value(id) }
        }
        return ApiStudioWorkspaceRenderState(
            activeDocumentId = state.documentId,
            content = { modifier -> WebSocketStudioScreen(viewModel = viewModel, modifier = modifier) },
        )
    }
}
