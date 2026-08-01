package com.devuloopers.knet.ui.desktop.traffic.table

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.traffic.model.TrafficItemUiState
import com.devuloopers.knet.ui.core.theme.KNetColors

/**
 * Virtualized live traffic feed table composable.
 */
@Composable
public fun TrafficTable(
    transactions: List<TrafficItemUiState>,
    selectedId: String?,
    autoScroll: Boolean,
    onSelectTransaction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (transactions.isEmpty()) {
        EmptyTrafficView(modifier = modifier)
        return
    }

    val listState = rememberLazyListState()

    LaunchedEffect(transactions.size, autoScroll) {
        if (autoScroll && transactions.isNotEmpty()) {
            listState.animateScrollToItem(transactions.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Sticky Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KNetColors.BackgroundDark)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrafficColumn.entries.forEach { col ->
                Text(
                    text = col.label,
                    color = KNetColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(col.width)
                )
            }
        }

        // Virtualized Feed List
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f)
        ) {
            items(transactions, key = { it.transactionId }) { tx ->
                TrafficRow(
                    transaction = tx,
                    isSelected = tx.transactionId == selectedId,
                    onClick = { onSelectTransaction(tx.transactionId) }
                )
            }
        }
    }
}
