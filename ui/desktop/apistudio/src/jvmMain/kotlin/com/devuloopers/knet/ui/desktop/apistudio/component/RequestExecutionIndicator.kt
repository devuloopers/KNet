package com.devuloopers.knet.ui.desktop.apistudio.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors

/**
 * Animated circular loading indicator for executing requests.
 *
 * @param modifier Layout modifier.
 */
@Composable
public fun RequestExecutionIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            color = KNetColors.ActiveBlue,
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp
        )
        Text(
            text = "Sending request...",
            color = KNetColors.TextSecondary,
            fontSize = 11.sp
        )
    }
}
