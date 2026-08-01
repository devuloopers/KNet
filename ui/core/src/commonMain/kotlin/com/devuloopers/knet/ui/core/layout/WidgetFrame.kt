package com.devuloopers.knet.ui.core.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes

/**
 * Styled panel container frame with dark background, subtle border, and optional title header bar.
 *
 * @param title Optional title header string.
 * @param modifier Layout modifier.
 * @param headerActions Optional header trailing action controls composable slot.
 * @param content Main panel content slot.
 */
@Composable
public fun WidgetFrame(
    title: String? = null,
    modifier: Modifier = Modifier,
    headerActions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .background(KNetColors.SurfaceDark, KNetShapes.Medium)
            .border(1.dp, KNetColors.BorderDark, KNetShapes.Medium)
    ) {
        if (title != null || headerActions != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(KNetColors.BackgroundDark, KNetShapes.Medium)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        color = KNetColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
                headerActions?.invoke()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            content()
        }
    }
}
