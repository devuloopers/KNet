package com.devuloopers.knet.ui.desktop.traffic.table

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors

/**
 * Traffic table individual cell view.
 */
@Composable
public fun TrafficCell(
    text: String,
    column: TrafficColumn,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = KNetColors.TextPrimary,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.width(column.width)
    )
}
