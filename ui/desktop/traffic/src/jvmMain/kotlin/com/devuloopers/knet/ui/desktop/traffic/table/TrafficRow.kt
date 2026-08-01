package com.devuloopers.knet.ui.desktop.traffic.table

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.traffic.model.TrafficItemUiState
import com.devuloopers.knet.ui.core.badge.MethodBadge
import com.devuloopers.knet.ui.core.badge.StatusBadge
import com.devuloopers.knet.ui.core.theme.KNetColors

/**
 * Single captured transaction row view in traffic table.
 */
@Composable
public fun TrafficRow(
    transaction: TrafficItemUiState,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isSelected) KNetColors.ActiveBlue.copy(alpha = 0.2f) else KNetColors.SurfaceDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusBadge(statusCode = transaction.status)
        MethodBadge(method = transaction.method)
        TrafficCell(text = transaction.path, column = TrafficColumn.URL)
        TrafficCell(text = transaction.host, column = TrafficColumn.HOST)
        TrafficCell(text = "HTTP/1.1", column = TrafficColumn.TYPE)
        TrafficCell(text = transaction.formattedTime, column = TrafficColumn.TIME)
        TrafficCell(text = transaction.formattedSize, column = TrafficColumn.SIZE)
    }
}
