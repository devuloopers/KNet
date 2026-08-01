package com.devuloopers.knet.ui.desktop.apistudio.response

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.apistudio.model.ResponsePresentation

/**
 * Metadata inspector view for MIME type and response payload properties.
 */
@Composable
public fun ResponseMetadataView(
    presentation: ResponsePresentation,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(8.dp)) {
        Text("Content-Type: ${presentation.mimeType}", color = KNetColors.TextSecondary, fontSize = 11.sp)
        Text("Payload Size: ${presentation.sizeBytes} bytes", color = KNetColors.TextSecondary, fontSize = 11.sp)
        Text("Roundtrip Duration: ${presentation.durationMs} ms", color = KNetColors.TextSecondary, fontSize = 11.sp)
    }
}
