package com.devuloopers.knet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.devuloopers.knet.controller.ProxyStateController
import com.devuloopers.knet.domain.inspector.model.TransactionUiModel
import com.devuloopers.knet.domain.livetraffic.model.LiveTrafficUiState
import com.devuloopers.knet.theme.KNetTheme
import com.devuloopers.knet.ui.workspace.WorkspaceLayout

/**
 * Main application entry point for the KNet User Interface.
 *
 * Configures application-wide theme, state routing, and delegates primary grid
 * rendering to [WorkspaceLayout].
 *
 * @param controller Central proxy state controller.
 */
@Composable
fun App(controller: ProxyStateController) {
    KNetTheme {
        var currentTab by remember { mutableStateOf("Live Traffic") }

        // Collect live traffic feed state reactively
        val liveTrafficState by controller.liveTrafficViewModel.uiState.collectAsState()

        // Derive selected transaction details reactively
        val selectedTx: TransactionUiModel? =
            (liveTrafficState as? LiveTrafficUiState.Success)
                ?.selectedItem
                ?.let { item ->
                    TransactionUiModel(
                        id = item.id,
                        method = item.method,
                        host = item.host,
                        path = item.path,
                        status = item.status,
                        statusText = item.statusText,
                        time = item.formattedTime,
                        size = item.formattedSize,
                        dateGroup = item.dateGroup,
                        requestBody = item.requestBody,
                        responseBody = item.responseBody,
                        queryParams = item.queryParams,
                        requestHeaders = item.requestHeaders,
                        responseHeaders = item.responseHeaders,
                        timings = item.timings
                    )
                }

        WorkspaceLayout(
            controller = controller,
            currentTab = currentTab,
            onTabSelected = { currentTab = it },
            selectedTx = selectedTx,
            liveTrafficState = liveTrafficState
        )
    }
}