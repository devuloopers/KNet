package com.devuloopers.knet.ui.core.feedback

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.icon.KNetIcons
import com.devuloopers.knet.ui.core.theme.KNetColors

/**
 * Visual placeholder component for empty lists, no traffic captured, or zero search results.
 *
 * @param title Primary heading text (e.g. "No Traffic Captured").
 * @param description Subtitle descriptive text.
 * @param modifier Layout modifier.
 */
@Composable
fun EmptyState(
    title: String = "No Data Available",
    description: String = "Perform an action to view results here.",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = KNetIcons.InfoIcon,
            contentDescription = "Empty state icon",
            tint = KNetColors.TextMuted,
            modifier = Modifier.size(32.dp).padding(bottom = 8.dp)
        )
        Text(
            text = title,
            color = KNetColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            text = description,
            color = KNetColors.TextMuted,
            fontSize = 11.sp
        )
    }
}
