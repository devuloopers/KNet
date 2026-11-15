package com.devuloopers.knet.ui.desktop.httppanel.copy

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboard
import com.devuloopers.knet.application.contract.export.StudioExportSession
import com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.ui.core.components.button.KNetSplitCopyButton
import com.devuloopers.knet.ui.core.foundation.clipboard.setPlainText
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stable response/session action backed by the last execution owner, independent of the editable draft.
 * [primary] and [label] follow the active inspection tab; contextual menu commands keep their own presets.
 */
@Composable
fun StudioExecutionCopyAction(session: StudioExportSession?, contextKey: String, kind: RequestKindId = session?.copyKind ?: RequestKindId.HTTP,
    primary: TrafficCopyPreset = TrafficCopyPreset.RESPONSE_BODY,
    label: String = "Copy body", eventStream: Boolean = false) {
    val viewModel: StudioDetailsCopyViewModel = koinViewModel(key = "studio-copy-$contextKey")
    val state by viewModel.state.collectAsState()
    val received = session?.responseReceived?.collectAsState()?.value == true
    val owner = session?.id
    LaunchedEffect(viewModel, owner) { viewModel.selectOwner(owner) }
    DisposableEffect(viewModel) { onDispose { viewModel.selectOwner(null) } }
    val clipboard = LocalClipboard.current
    val enabled = owner != null && received
    val http = kind == RequestKindId.HTTP || kind == RequestKindId.GRAPHQL
    val capabilities = remember(kind, enabled, eventStream) { CaptureCopyMenuCapabilities(http, enabled && http, http, buildSet {
        if (kind == RequestKindId.GRAPHQL) add(CaptureCopyProtocol.GRAPHQL)
        if (kind == RequestKindId.WEBSOCKET) add(CaptureCopyProtocol.WEBSOCKET)
        if (kind == RequestKindId.GRAPHQL_WEBSOCKET) add(CaptureCopyProtocol.GRAPHQL_WEBSOCKET)
        if (kind == RequestKindId.GRPC) add(CaptureCopyProtocol.GRPC)
        if (eventStream) add(CaptureCopyProtocol.SSE)
    }) }
    val copy: (TrafficCopyPreset) -> Unit = remember(owner, clipboard, viewModel) { { preset ->
        if (owner != null) viewModel.copy(owner, preset) { clipboard.setPlainText(it) }
    } }
    val entries = remember(capabilities, copy) { captureCopyMenuItems(capabilities, studio = true, copy = copy) }
    KNetSplitCopyButton(label = label, entries = entries, ownerKey = owner, enabled = enabled, copying = state.copying,
        copiedRevision = state.copiedRevision, error = state.error, onCopy = { copy(primary) })
}
