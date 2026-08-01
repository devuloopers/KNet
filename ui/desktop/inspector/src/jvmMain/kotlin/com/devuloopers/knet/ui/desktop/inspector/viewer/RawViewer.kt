package com.devuloopers.knet.ui.desktop.inspector.viewer

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
 * Raw unformatted payload viewer composable.
 */
@Composable
public fun RawViewer(
    rawContent: String,
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
            text = rawContent,
            color = KNetColors.TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
    }
}
