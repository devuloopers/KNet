package com.devuloopers.knet.ui.core.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes

/**
 * Container slot for hosting feature panels contributed by feature modules.
 *
 * @param modifier Layout modifier.
 * @param content Feature panel composable slot.
 */
@Composable
public fun PanelHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.SurfaceDark, KNetShapes.Medium)
    ) {
        content()
    }
}
