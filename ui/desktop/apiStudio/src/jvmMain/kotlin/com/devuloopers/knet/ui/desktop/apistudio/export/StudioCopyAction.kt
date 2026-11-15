package com.devuloopers.knet.ui.desktop.apistudio.export

import androidx.compose.runtime.Composable
import com.devuloopers.knet.application.contract.export.StudioExportSession
import com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.ui.desktop.httppanel.copy.StudioExecutionCopyAction

/** Public editor-extension slot for execution-owned native copy actions, beside the existing export slot. */
@Composable
fun StudioCopyAction(session: StudioExportSession?, contextKey: String, kind: RequestKindId,
    primary: TrafficCopyPreset, label: String) {
    StudioExecutionCopyAction(session, contextKey, kind, primary, label)
}
