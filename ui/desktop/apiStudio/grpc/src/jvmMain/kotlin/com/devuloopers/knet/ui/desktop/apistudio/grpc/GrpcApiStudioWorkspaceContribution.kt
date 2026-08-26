package com.devuloopers.knet.ui.desktop.apistudio.grpc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.devuloopers.knet.application.contract.apistudio.ApiStudioEditorId
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.ui.desktop.apistudio.protocol.ApiStudioWorkspaceContribution
import com.devuloopers.knet.ui.desktop.apistudio.protocol.ApiStudioWorkspaceRenderState
import com.devuloopers.knet.ui.desktop.apistudio.grpc.view.GrpcStudioScreen
import com.devuloopers.knet.ui.desktop.apistudio.grpc.viewmodel.GrpcStudioViewModel
import com.devuloopers.knet.ui.desktop.apistudio.grpc.persistence.GrpcWorkspaceDraftCodec
import org.koin.compose.viewmodel.koinViewModel

/** Product-discoverable gRPC workspace; API Studio itself contains no gRPC branch. */
class GrpcApiStudioWorkspaceContribution(
    private val draftCodec: GrpcWorkspaceDraftCodec = GrpcWorkspaceDraftCodec(),
) : ApiStudioWorkspaceContribution() {
    override val editorId: ApiStudioEditorId = ApiStudioEditorId.GRPC
    override val kind: RequestKindId = RequestKindId.GRPC
    override val label: String = "gRPC"

    override fun createInitialDocument(id: String): ApiStudioWorkspaceDocument {
        val initialState = com.devuloopers.knet.ui.desktop.apistudio.grpc.model.GrpcStudioState(documentId = id)
        return draftCodec.unsavedDocument(initialState)
    }

    @Composable
    protected override fun rememberWorkspaceContent(
        documentId: String?,
        onDocumentCreated: (String) -> Unit,
    ): ApiStudioWorkspaceRenderState {
        val viewModel: GrpcStudioViewModel = koinViewModel()
        val state by viewModel.state.collectAsState()
        val currentOnDocumentCreated = rememberUpdatedState(onDocumentCreated)
        LaunchedEffect(documentId) {
            viewModel.openWorkspaceDocument(documentId)
        }
        LaunchedEffect(viewModel) {
            viewModel.materializedDocumentIds.collect { documentId ->
                currentOnDocumentCreated.value(documentId)
            }
        }
        return ApiStudioWorkspaceRenderState(
            activeDocumentId = state.documentId,
            content = { modifier -> GrpcStudioScreen(viewModel = viewModel, modifier = modifier) },
        )
    }
}
