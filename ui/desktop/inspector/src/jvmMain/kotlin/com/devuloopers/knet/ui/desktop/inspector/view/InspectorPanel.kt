package com.devuloopers.knet.ui.desktop.inspector.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.inspector.component.InspectorToolbar
import com.devuloopers.knet.ui.desktop.inspector.component.StatusSummary
import com.devuloopers.knet.ui.desktop.inspector.model.InspectorIntent
import com.devuloopers.knet.ui.desktop.inspector.model.InspectorTab
import com.devuloopers.knet.ui.desktop.inspector.overview.OverviewPanel
import com.devuloopers.knet.ui.desktop.inspector.protocol.ProtocolInspector
import com.devuloopers.knet.ui.desktop.inspector.request.RequestInspector
import com.devuloopers.knet.ui.desktop.inspector.response.ResponseInspector
import com.devuloopers.knet.ui.desktop.inspector.timing.TimingInspector
import com.devuloopers.knet.ui.desktop.inspector.tls.CertificateInspector
import com.devuloopers.knet.ui.desktop.inspector.viewmodel.InspectorViewModel

/**
 * Top-level read-only Transaction Inspector panel composable.
 *
 * @param viewModel InspectorViewModel managing UDF inspection state.
 * @param modifier Layout modifier.
 */
@Composable
public fun InspectorPanel(
    viewModel: InspectorViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.BackgroundDark)
    ) {
        if (state.overview != null) {
            StatusSummary(overview = state.overview!!)
        }

        InspectorToolbar(
            searchQuery = state.searchQuery,
            bodyMode = state.bodyMode,
            onSearchChanged = { viewModel.processIntent(InspectorIntent.Search(it)) },
            onBodyModeSelected = { viewModel.processIntent(InspectorIntent.SelectBodyMode(it)) },
            onCopyContent = {}
        )

        // Main Tab Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KNetColors.BackgroundDark)
                .padding(vertical = 4.dp)
        ) {
            InspectorTab.entries.forEach { tab ->
                val isSelected = tab == state.activeTab
                Text(
                    text = tab.name,
                    color = if (isSelected) KNetColors.ActiveBlue else KNetColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable { viewModel.processIntent(InspectorIntent.SelectTab(tab)) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Active Tab View Content
        when (state.activeTab) {
            InspectorTab.OVERVIEW -> OverviewPanel(overview = state.overview, modifier = Modifier.weight(1f))
            InspectorTab.REQUEST -> RequestInspector(request = state.request, modifier = Modifier.weight(1f))
            InspectorTab.RESPONSE -> ResponseInspector(response = state.response, modifier = Modifier.weight(1f))
            InspectorTab.TIMING -> TimingInspector(durationMs = state.overview?.totalDurationMs ?: 0, modifier = Modifier.weight(1f))
            InspectorTab.TLS -> CertificateInspector(
                host = state.overview?.host ?: "example.com",
                tlsVersion = state.overview?.tlsVersion ?: "TLSv1.3",
                cipherSuite = state.overview?.cipherSuite ?: "TLS_AES_256_GCM_SHA384",
                modifier = Modifier.weight(1f)
            )
            InspectorTab.PROTOCOL -> ProtocolInspector(protocol = state.overview?.protocol ?: "HTTP/1.1", modifier = Modifier.weight(1f))
        }
    }
}
