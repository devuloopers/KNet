package com.devuloopers.knet.ui.desktop.connectivity.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.clipboard.setPlainText
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.connectivity.components.WifiProxySetupCard
import com.devuloopers.knet.ui.desktop.connectivity.components.WifiProxySetupDrawer
import com.devuloopers.knet.ui.desktop.connectivity.components.CompanionConnectionCard
import com.devuloopers.knet.ui.desktop.connectivity.components.CompanionConnectionDrawer
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceIntent
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceUiState
import com.devuloopers.knet.ui.desktop.connectivity.viewmodel.ConnectDeviceViewModel
import kotlinx.coroutines.launch

/** Renders the connectivity-method grid and its mutually exclusive feature drawers. */
@Composable
fun ConnectDeviceScreen(
    viewModel: ConnectDeviceViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    ConnectDeviceScreenContent(
        state = state,
        onIntent = viewModel::processIntent,
        modifier = modifier,
    )
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalComposeApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun ConnectDeviceScreenContent(
    state: ConnectDeviceUiState,
    onIntent: (ConnectDeviceIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KNetTheme.colors
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        FlowRow(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            WifiProxySetupCard(
                state = state,
                onClick = { onIntent(ConnectDeviceIntent.OpenWifiSetup) },
            )
            CompanionConnectionCard(
                state = state,
                onClick = { onIntent(ConnectDeviceIntent.OpenCompanionConnection) },
            )
        }
        WifiProxySetupDrawer(
            state = state,
            onIntent = onIntent,
            onCopy = { value -> coroutineScope.launch { clipboard.setPlainText(value) } },
            modifier = Modifier.fillMaxSize(),
        )
        CompanionConnectionDrawer(
            state = state,
            onIntent = onIntent,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
