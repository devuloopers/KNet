package com.devuloopers.knet.ui.desktop.apistudio.export

import androidx.compose.runtime.*
import com.devuloopers.knet.application.contract.export.*
import com.devuloopers.knet.ui.desktop.httppanel.export.*
import org.koin.compose.viewmodel.koinViewModel

/** Response/session action retaining original execution ownership through subsequent draft edits. */
@Composable
fun StudioExportAction(session: StudioExportSession?) {
    val viewModel: CaptureExportViewModel = koinViewModel(key = "api-studio-export")
    val enabled = session?.eligible?.collectAsState()?.value == true
    CaptureExportButton(enabled = enabled, onClick = {
        if (session != null) viewModel.open(CaptureExportSource.Studio(session.id), "API Studio response")
    })
    CaptureExportDialog(viewModel)
}
