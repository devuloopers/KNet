package com.devuloopers.knet.ui.desktop.apistudio.response

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes

/**
 * Formatted HTTP response body preview view composable.
 *
 * @param body Response payload string.
 * @param modifier Layout modifier.
 */
@Composable
public fun ResponseBodyView(
    body: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.SurfaceDark, KNetShapes.Medium)
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = body.ifEmpty { "Empty Response Body" },
            color = KNetColors.TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
    }
}
