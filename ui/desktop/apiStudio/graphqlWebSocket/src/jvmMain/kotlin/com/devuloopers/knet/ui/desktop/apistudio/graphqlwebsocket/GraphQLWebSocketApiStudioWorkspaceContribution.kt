package com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.devuloopers.knet.application.contract.apistudio.ApiStudioEditorId
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.model.GraphQLWebSocketStudioState
import com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.persistence.GraphQLWebSocketWorkspaceDraftCodec
import com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.view.GraphQLWebSocketStudioScreen
import com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.viewmodel.GraphQLWebSocketStudioViewModel
import com.devuloopers.knet.ui.desktop.apistudio.protocol.ApiStudioWorkspaceContribution
import com.devuloopers.knet.ui.desktop.apistudio.protocol.ApiStudioWorkspaceRenderState
import org.koin.compose.viewmodel.koinViewModel

/** Product-discoverable modern GraphQL WebSocket workspace contribution. */
class GraphQLWebSocketApiStudioWorkspaceContribution(
    private val draftCodec: GraphQLWebSocketWorkspaceDraftCodec,
) : ApiStudioWorkspaceContribution() {
    override val editorId: ApiStudioEditorId = ApiStudioEditorId.GRAPHQL_WEBSOCKET
    override val kind: RequestKindId = RequestKindId.GRAPHQL_WEBSOCKET
    override val label: String = "GraphQL WS"

    override fun createInitialDocument(id: String): ApiStudioWorkspaceDocument =
        draftCodec.unsavedDocument(GraphQLWebSocketStudioState(documentId = id))

    @Composable
    protected override fun rememberWorkspaceContent(
        documentId: String?,
        onDocumentCreated: (String) -> Unit,
    ): ApiStudioWorkspaceRenderState {
        val viewModel: GraphQLWebSocketStudioViewModel = koinViewModel()
        val state by viewModel.state.collectAsState()
        val currentOnDocumentCreated = rememberUpdatedState(onDocumentCreated)
        LaunchedEffect(documentId) { viewModel.openWorkspaceDocument(documentId) }
        LaunchedEffect(viewModel) {
            viewModel.materializedDocumentIds.collect { id -> currentOnDocumentCreated.value(id) }
        }
        return ApiStudioWorkspaceRenderState(
            activeDocumentId = state.documentId,
            content = { modifier -> GraphQLWebSocketStudioScreen(viewModel = viewModel, modifier = modifier) },
        )
    }
}
