package com.devuloopers.knet.ui.desktop.apistudio.grpc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.devuloopers.knet.application.port.apistudio.ApiStudioEditorId
import com.devuloopers.knet.application.port.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.ui.desktop.apistudio.protocol.ApiStudioWorkspaceContribution
import com.devuloopers.knet.ui.desktop.apistudio.grpc.view.GrpcStudioScreen
import com.devuloopers.knet.ui.desktop.apistudio.grpc.viewmodel.GrpcStudioViewModel
import com.devuloopers.knet.ui.desktop.apistudio.grpc.persistence.GrpcWorkspaceDraftCodec
import org.koin.compose.viewmodel.koinViewModel

/** Product-discoverable gRPC workspace; API Studio itself contains no gRPC branch. */
class GrpcApiStudioWorkspaceContribution(
    private val draftCodec: GrpcWorkspaceDraftCodec = GrpcWorkspaceDraftCodec(),
) : ApiStudioWorkspaceContribution {
    override val editorId: ApiStudioEditorId = ApiStudioEditorId.GRPC
    override val kind: RequestKindId = RequestKindId.GRPC
    override val label: String = "gRPC"

    override fun createInitialDocument(id: String): ApiStudioWorkspaceDocument {
        val initialState = com.devuloopers.knet.ui.desktop.apistudio.grpc.model.GrpcStudioState(documentId = id)
        return draftCodec.unsavedDocument(initialState)
    }

    @Composable
    override fun Content(
        documentId: String?,
        onDocumentCreated: (String) -> Unit,
        modifier: Modifier,
    ) {
        val viewModel: GrpcStudioViewModel = koinViewModel()
        val currentOnDocumentCreated = rememberUpdatedState(onDocumentCreated)
        LaunchedEffect(documentId) {
            viewModel.openWorkspaceDocument(documentId)
        }
        LaunchedEffect(viewModel) {
            viewModel.materializedDocumentIds.collect { documentId ->
                currentOnDocumentCreated.value(documentId)
            }
        }
        GrpcStudioScreen(viewModel = viewModel, modifier = modifier)
    }
}
