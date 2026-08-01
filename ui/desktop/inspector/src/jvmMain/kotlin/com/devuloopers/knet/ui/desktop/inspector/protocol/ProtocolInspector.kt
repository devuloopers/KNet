package com.devuloopers.knet.ui.desktop.inspector.protocol

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.inspector.model.ProtocolSubTab

/**
 * Protocol Inspector container hosting sub-tabs for HTTP/2, HTTP/3, WebSocket, and gRPC.
 *
 * @param protocol Active network transaction protocol string (e.g. "HTTP/2", "WebSocket").
 * @param modifier Layout parameters.
 */
@Composable
public fun ProtocolInspector(
    protocol: String,
    modifier: Modifier = Modifier
) {
    var activeSubTab by remember(protocol) {
        mutableStateOf(ProtocolSubTab.fromProtocol(protocol))
    }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Text(
            text = "Active Protocol: ${protocol.ifEmpty { "HTTP/1.1" }}",
            color = KNetColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KNetColors.BackgroundDark)
                .padding(vertical = 4.dp)
        ) {
            ProtocolSubTab.entries.forEach { subTab ->
                val isSelected = subTab == activeSubTab
                Text(
                    text = subTab.label,
                    color = if (isSelected) KNetColors.ActiveBlue else KNetColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable { activeSubTab = subTab }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        when (activeSubTab) {
            ProtocolSubTab.HTTP2 -> Http2View(modifier = Modifier.weight(1f))
            ProtocolSubTab.HTTP3 -> Http3View(modifier = Modifier.weight(1f))
            ProtocolSubTab.WEBSOCKET -> WebSocketView(modifier = Modifier.weight(1f))
            ProtocolSubTab.GRPC -> GrpcView(modifier = Modifier.weight(1f))
        }
    }
}
