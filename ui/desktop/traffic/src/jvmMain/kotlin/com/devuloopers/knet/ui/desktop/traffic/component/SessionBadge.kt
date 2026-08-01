package com.devuloopers.knet.ui.desktop.traffic.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes

/**
 * Capture session badge.
 */
@Composable
fun SessionBadge(
    sessionName: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = sessionName,
        color = KNetColors.TextPrimary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(KNetColors.SurfaceDark, KNetShapes.Small)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
