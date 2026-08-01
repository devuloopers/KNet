package com.devuloopers.knet.ui.core.badge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
 * Generic pill badge component for tags and labels.
 *
 * @param tag Display string.
 * @param modifier Layout parameters.
 */
@Composable
public fun TagBadge(
    tag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(KNetColors.FieldDark, KNetShapes.Pill)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tag,
            color = KNetColors.TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
