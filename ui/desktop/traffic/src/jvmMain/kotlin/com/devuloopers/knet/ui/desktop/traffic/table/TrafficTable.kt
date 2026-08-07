package com.devuloopers.knet.ui.desktop.traffic.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.clientNetwork.decoder.BinaryCategory
import com.devuloopers.knet.domain.clientNetwork.decoder.MediaTypeInspector
import com.devuloopers.knet.domain.traffic.model.TrafficItemUiState
import com.devuloopers.knet.ui.core.components.table.KNetCell
import com.devuloopers.knet.ui.core.components.table.KNetRow
import com.devuloopers.knet.ui.core.components.table.KNetTableHeader
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.traffic.model.ColumnVisibilityState
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficColumn

/**
 * High-density virtualized traffic feed table using standardized :ui:core table primitives and tokens.
 */
@Composable
public fun TrafficTable(
    transactions: List<TrafficItemUiState>,
    selectedId: String?,
    autoScroll: Boolean,
    onSelectTransaction: (String) -> Unit,
    formattedTotalSize: String,
    modifier: Modifier = Modifier,
    columnVisibility: ColumnVisibilityState = ColumnVisibilityState()
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.background)
    ) {
        // Sticky Table Header Row
        TableHeaderRow(columnVisibility = columnVisibility)

        val listState = androidx.compose.foundation.lazy.rememberLazyListState()

        androidx.compose.runtime.LaunchedEffect(transactions.size, autoScroll) {
            if (autoScroll && transactions.isNotEmpty()) {
                listState.scrollToItem(0)
            }
        }

        // Table Rows Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No captured network requests",
                        style = typography.bodyMedium.copy(color = themeColors.textMuted)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = transactions,
                        key = { it.transactionId }
                    ) { item ->
                        TableRowItem(
                            item = item,
                            isSelected = item.transactionId == selectedId,
                            columnVisibility = columnVisibility,
                            onClick = { onSelectTransaction(item.transactionId) }
                        )
                    }
                }
            }
        }

        // Bottom Statistics Footer Bar (48dp / h-12)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(themeColors.background)
                .border(width = 1.dp, color = themeColors.border)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${transactions.size} Requests",
                style = typography.caption.copy(color = themeColors.textSecondary)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedTotalSize,
                    style = typography.caption.copy(color = themeColors.textSecondary)
                )
                Text(
                    text = "Duration: 00:01:24",
                    style = typography.caption.copy(color = themeColors.textSecondary)
                )
            }
        }
    }
}

@Composable
private fun TableHeaderRow(columnVisibility: ColumnVisibilityState) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    KNetTableHeader(
        modifier = Modifier
            .height(34.dp)
            .border(width = 1.dp, color = themeColors.border)
    ) {
        if (columnVisibility.isVisible(TrafficColumn.SERIAL_NUMBER)) {
            KNetCell(text = "#", modifier = Modifier.width(48.dp), color = themeColors.textMuted)
        }
        if (columnVisibility.isVisible(TrafficColumn.TIMESTAMP)) {
            KNetCell(text = "Time", modifier = Modifier.width(110.dp), color = themeColors.textMuted)
        }
        Box(modifier = Modifier.width(76.dp), contentAlignment = Alignment.CenterStart) {
            Text(text = "Method", style = typography.codeSmall.copy(color = themeColors.textMuted, fontWeight = FontWeight.Bold))
        }
        Box(modifier = Modifier.width(180.dp), contentAlignment = Alignment.CenterStart) {
            Text(text = "Host", style = typography.codeSmall.copy(color = themeColors.textMuted, fontWeight = FontWeight.Bold))
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Text(text = "Path", style = typography.codeSmall.copy(color = themeColors.textMuted, fontWeight = FontWeight.Bold))
        }
        if (columnVisibility.isVisible(TrafficColumn.STATUS)) {
            Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.CenterStart) {
                Text(text = "Status", style = typography.codeSmall.copy(color = themeColors.textMuted, fontWeight = FontWeight.Bold))
            }
        }
        if (columnVisibility.isVisible(TrafficColumn.SIZE)) {
            KNetCell(text = "Size", modifier = Modifier.width(76.dp), color = themeColors.textMuted)
        }
        if (columnVisibility.isVisible(TrafficColumn.DURATION)) {
            KNetCell(text = "Time", modifier = Modifier.width(76.dp), color = themeColors.textMuted)
        }
        if (columnVisibility.isVisible(TrafficColumn.TYPE)) {
            KNetCell(text = "Type", modifier = Modifier.width(64.dp), color = themeColors.textMuted)
        }
    }
}

@Composable
private fun TableRowItem(
    item: TrafficItemUiState,
    isSelected: Boolean,
    columnVisibility: ColumnVisibilityState,
    onClick: () -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    val methodColor = when (item.method.uppercase()) {
        "GET" -> themeColors.semantic.success
        "POST" -> themeColors.semantic.info
        "OPTIONS" -> themeColors.semantic.warning
        "WS" -> themeColors.semantic.info
        else -> themeColors.textSecondary
    }

    val isCompleted = item.formattedTime != "-"

    val statusColor = when {
        item.status in 200..299 -> themeColors.semantic.success
        item.status in 300..399 -> themeColors.semantic.warning
        item.status in 400..599 -> themeColors.semantic.error
        item.status == 101 -> themeColors.semantic.info
        isCompleted && item.status == 0 -> themeColors.semantic.error
        else -> themeColors.textSecondary
    }

    val contentTypeHeader = item.responseHeaders["Content-Type"]
        ?: item.requestHeaders["Content-Type"]
        ?: item.responseHeaders.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value
        ?: item.requestHeaders.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value

    val inferredType = when (val category = MediaTypeInspector.inspectCategory(contentTypeHeader)) {
        BinaryCategory.OHTTP -> "OHTTP"
        BinaryCategory.PROTOBUF -> "Proto"
        BinaryCategory.CBOR -> "CBOR"
        BinaryCategory.MSGPACK -> "MsgPack"
        BinaryCategory.WASM -> "WASM"
        BinaryCategory.ARCHIVE -> "Zip"
        BinaryCategory.IMAGE -> "IMG"
        BinaryCategory.AUDIO -> "Audio"
        BinaryCategory.VIDEO -> "Video"
        BinaryCategory.FONT -> "Font"
        else -> when {
            contentTypeHeader?.contains("json", ignoreCase = true) == true -> "JSON"
            contentTypeHeader?.contains("html", ignoreCase = true) == true -> "HTML"
            contentTypeHeader?.contains("xml", ignoreCase = true) == true -> "XML"
            contentTypeHeader?.contains("css", ignoreCase = true) == true -> "CSS"
            contentTypeHeader?.contains("javascript", ignoreCase = true) == true || contentTypeHeader?.contains("js", ignoreCase = true) == true -> "JS"
            item.method == "WS" || item.protocol == "WS" -> "WS"
            else -> "Other"
        }
    }

    KNetRow(
        onClick = onClick,
        selected = isSelected,
        modifier = Modifier
            .height(34.dp)
            .then(
                if (isSelected) Modifier.border(width = 2.dp, color = themeColors.accent) else Modifier
            )
    ) {
        // # (Serial Number)
        if (columnVisibility.isVisible(TrafficColumn.SERIAL_NUMBER)) {
            KNetCell(
                text = "${item.id}",
                modifier = Modifier.width(48.dp),
                color = themeColors.textMuted
            )
        }

        // Timestamp
        if (columnVisibility.isVisible(TrafficColumn.TIMESTAMP)) {
            KNetCell(
                text = item.dateGroup.takeLast(12).ifEmpty { "10:24:31.320" },
                modifier = Modifier.width(110.dp),
                color = themeColors.textSecondary
            )
        }

        // Method (Mandatory)
        Box(modifier = Modifier.width(76.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text = item.method,
                style = typography.codeSmall.copy(color = methodColor, fontWeight = FontWeight.Bold)
            )
        }

        // Host (Mandatory)
        Box(modifier = Modifier.width(180.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text = item.host,
                style = typography.codeSmall.copy(color = themeColors.textPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Path (Mandatory)
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Text(
                text = item.path,
                style = typography.codeSmall.copy(color = themeColors.textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Status
        if (columnVisibility.isVisible(TrafficColumn.STATUS)) {
            Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = when {
                        item.status > 0 -> "${item.status}"
                        isCompleted -> "ERR"
                        else -> "-"
                    },
                    style = typography.codeSmall.copy(color = statusColor, fontWeight = FontWeight.Medium)
                )
            }
        }

        // Size
        if (columnVisibility.isVisible(TrafficColumn.SIZE)) {
            KNetCell(
                text = item.formattedSize,
                modifier = Modifier.width(76.dp),
                color = themeColors.textPrimary
            )
        }

        // Time (Duration)
        if (columnVisibility.isVisible(TrafficColumn.DURATION)) {
            KNetCell(
                text = item.formattedTime,
                modifier = Modifier.width(76.dp),
                color = themeColors.textPrimary
            )
        }

        // Type
        if (columnVisibility.isVisible(TrafficColumn.TYPE)) {
            KNetCell(
                text = inferredType,
                modifier = Modifier.width(64.dp),
                color = themeColors.textMuted
            )
        }
    }
}
