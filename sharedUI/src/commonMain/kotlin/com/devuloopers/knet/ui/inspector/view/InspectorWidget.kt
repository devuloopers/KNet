package com.devuloopers.knet.ui.inspector.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.inspector.model.InspectorIntent
import com.devuloopers.knet.domain.inspector.model.InspectorTab
import com.devuloopers.knet.domain.inspector.model.InspectorUiState
import com.devuloopers.knet.theme.KNetColors
import com.devuloopers.knet.widgets.RequestBodyWidget
import com.devuloopers.knet.widgets.ResponseBodyWidget
import com.devuloopers.knet.widgets.TransactionOverviewWidget

/**
 * Pure layout Composable view representing the Inspector panel.
 * Adheres strictly to Clean Architecture by rendering pre-formatted UI states
 * and emitting user actions as [InspectorIntent]s.
 *
 * @param state Immutable [InspectorUiState] emitted by ViewModel.
 * @param onIntent Callback lambda emitting user intents.
 * @param modifier Layout modifiers.
 */
@Composable
fun InspectorWidget(
    state: InspectorUiState,
    onIntent: (InspectorIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.BackgroundDark)
    ) {
        // --- Inspector Header Tabs Strip ---
        val activeTab = when (state) {
            is InspectorUiState.Success -> state.activeTab
            else -> InspectorTab.OVERVIEW
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(KNetColors.SurfaceDark)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InspectorTab.entries.forEach { tab ->
                val isSelected = tab == activeTab
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .background(
                            color = if (isSelected) KNetColors.ActiveBlue else KNetColors.SurfaceDark,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onIntent(InspectorIntent.SelectTab(tab)) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = tab.displayName,
                        color = if (isSelected) Color.White else KNetColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // --- Body Content Rendering ---
        Box(modifier = Modifier.weight(1f)) {
            when (state) {
                is InspectorUiState.NoSelection -> {
                    Text(
                        text = "Select a transaction to inspect details",
                        color = KNetColors.TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is InspectorUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(24.dp),
                        color = KNetColors.ActiveBlue,
                        strokeWidth = 2.dp
                    )
                }

                is InspectorUiState.Success -> {
                    val tx = state.transaction
                    when (state.activeTab) {
                        InspectorTab.OVERVIEW -> TransactionOverviewWidget(transaction = tx)
                        InspectorTab.HEADERS -> HeadersInspectorView(tx = tx)
                        InspectorTab.REQUEST_BODY -> RequestBodyWidget(transaction = tx)
                        InspectorTab.RESPONSE_BODY -> ResponseBodyWidget(transaction = tx)
                        InspectorTab.TIMING -> TimingWaterfallView(tx = tx)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadersInspectorView(tx: com.devuloopers.knet.domain.inspector.model.TransactionUiModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text("Request Headers", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        tx.requestHeaders.forEach { (key, value) ->
            HeaderRow(key = key, value = value)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Response Headers", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        tx.responseHeaders.forEach { (key, value) ->
            HeaderRow(key = key, value = value)
        }
    }
}

@Composable
private fun HeaderRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = key,
            color = KNetColors.ActiveBlue,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(2f)
        )
    }
}

@Composable
private fun TimingWaterfallView(tx: com.devuloopers.knet.domain.inspector.model.TransactionUiModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text("Timing Breakdown", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        TimingBar("DNS Lookup", tx.timingDnsMs, KNetColors.ActiveBlue)
        TimingBar("TCP Connect", tx.timingTcpMs, KNetColors.SuccessGreen)
        TimingBar("TLS Handshake", tx.timingTlsMs, KNetColors.PurpleWS)
        TimingBar("TTFB", tx.timingTtfbMs, KNetColors.WarningOrange)
        TimingBar("Content Download", tx.timingDownloadMs, KNetColors.ErrorRed)
    }
}

@Composable
private fun TimingBar(label: String, durationMs: Long, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = KNetColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.width(130.dp))
        Box(
            modifier = Modifier
                .height(8.dp)
                .width((durationMs * 3).toInt().dp.coerceIn(10.dp, 200.dp))
                .background(color, shape = RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "$durationMs ms", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}
