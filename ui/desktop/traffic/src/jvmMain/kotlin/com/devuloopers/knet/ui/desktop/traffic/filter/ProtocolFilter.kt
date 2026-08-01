package com.devuloopers.knet.ui.desktop.traffic.filter

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.input.KNetDropdown

/**
 * Protocol filter dropdown (ALL, HTTP/1.1, HTTP/2, HTTP/3, WebSocket, gRPC).
 */
@Composable
public fun ProtocolFilter(
    selectedProtocol: String,
    onProtocolSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    KNetDropdown(
        items = listOf("ALL", "HTTP/1.1", "HTTP/2", "HTTP/3", "WebSocket", "gRPC"),
        selectedItem = selectedProtocol,
        itemLabel = { it },
        onItemSelected = onProtocolSelected,
        modifier = modifier
    )
}
