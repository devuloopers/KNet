package com.devuloopers.knet.ui.desktop.inspector.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.badge.MethodBadge
import com.devuloopers.knet.ui.core.badge.StatusBadge
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.inspector.model.TransactionOverview

/**
 * Status and method badge summary header bar for Inspector.
 */
@Composable
public fun StatusSummary(
    overview: TransactionOverview,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MethodBadge(method = overview.method)
        StatusBadge(statusCode = overview.statusCode)
        Text(text = overview.url, color = KNetColors.TextPrimary, fontSize = 11.sp)
    }
}
