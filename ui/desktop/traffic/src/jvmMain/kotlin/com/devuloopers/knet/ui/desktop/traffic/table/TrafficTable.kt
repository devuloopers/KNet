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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
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

import com.devuloopers.knet.ui.core.components.menu.ContextMenuItem
import com.devuloopers.knet.ui.core.components.menu.KNetContextMenuArea
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons

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
    columnVisibility: ColumnVisibilityState = ColumnVisibilityState(),
    onSendToApiStudio: (String) -> Unit = {}
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.background)
    ) {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val todayDateState = remember { mutableStateOf(java.time.LocalDate.now()) }

        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000L)
                val currentLocalDate = java.time.LocalDate.now()
                if (todayDateState.value != currentLocalDate) {
                    todayDateState.value = currentLocalDate
                }
            }
        }

        LaunchedEffect(transactions.size, autoScroll) {
            if (autoScroll && transactions.isNotEmpty()) {
                listState.scrollToItem(0)
            }
        }

        // Sticky Table Header Row
        TableHeaderRow(columnVisibility = columnVisibility)

        // Table Content Area
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
                        text = "No captured traffic matching current filters",
                        style = typography.caption,
                        color = themeColors.textSecondary
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
                        val contextMenuItems = remember(item) {
                            listOf(
                                ContextMenuItem(
                                    label = "Send to API Studio",
                                    icon = KNetIcons.Send
                                ) {
                                    onSendToApiStudio(item.transactionId)
                                }
                            )
                        }

                        KNetContextMenuArea(items = contextMenuItems) {
                            TableRowItem(
                                item = item,
                                isSelected = item.transactionId == selectedId,
                                columnVisibility = columnVisibility,
                                todayDate = todayDateState.value,
                                onClick = { onSelectTransaction(item.transactionId) }
                            )
                        }
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
            KNetCell(text = "Timestamp", modifier = Modifier.width(130.dp), color = themeColors.textMuted)
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
            KNetCell(text = "Duration", modifier = Modifier.width(76.dp), color = themeColors.textMuted)
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
    todayDate: java.time.LocalDate,
    onClick: () -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    val displayMethod = when (item.interceptionMetadata) {
        is com.devuloopers.knet.domain.protocol.model.InterceptionMetadata.GraphQL -> "GQL"
        else -> item.method
    }

    val methodColor = when (displayMethod.uppercase()) {
        "GQL" -> androidx.compose.ui.graphics.Color(0xFFCBA6F7)
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
        !isCompleted && item.status == 0 -> androidx.compose.ui.graphics.Color(0xFFFAB387)
        else -> themeColors.textSecondary
    }

    val contentTypeHeader = item.responseHeaders["Content-Type"]
        ?: item.requestHeaders["Content-Type"]
        ?: item.responseHeaders.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value
        ?: item.requestHeaders.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value

    val inferredType = when (val metadata = item.interceptionMetadata) {
        is com.devuloopers.knet.domain.protocol.model.InterceptionMetadata.GraphQL -> "GraphQL"
        is com.devuloopers.knet.domain.protocol.model.InterceptionMetadata.Grpc -> "gRPC"
        is com.devuloopers.knet.domain.protocol.model.InterceptionMetadata.Protobuf -> "Proto"
        else -> when (val category = MediaTypeInspector.inspectCategory(contentTypeHeader)) {
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
    }

    val displayPath = when (val metadata = item.interceptionMetadata) {
        is com.devuloopers.knet.domain.protocol.model.InterceptionMetadata.GraphQL -> {
            val opName = metadata.operationName
            val opType = metadata.operationType
            when {
                !opName.isNullOrBlank() -> "${item.path} • $opName ($opType)"
                else -> "${item.path} ($opType)"
            }
        }
        else -> item.path
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
            val formatted = formatTimestamp(item.timestamp, item.formattedTimestamp, item.dateGroup, todayDate)
            KNetCell(
                text = formatted,
                modifier = Modifier.width(130.dp),
                color = themeColors.textSecondary
            )
        }

        // Method (Mandatory)
        Box(modifier = Modifier.width(76.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text = displayMethod,
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
                text = displayPath,
                style = typography.codeSmall.copy(color = themeColors.textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Status
        if (columnVisibility.isVisible(TrafficColumn.STATUS)) {
            Box(modifier = Modifier.width(84.dp), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = when {
                        item.status > 0 -> "${item.status}"
                        isCompleted -> "ERR"
                        else -> "In Progress"
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

private fun formatTimestamp(
    epochMillis: Long,
    fallbackFormatted: String,
    fallbackGroup: String,
    todayDate: java.time.LocalDate
): String {
    if (epochMillis <= 0L) return fallbackFormatted.ifEmpty { fallbackGroup }
    return try {
        val txInstant = java.time.Instant.ofEpochMilli(epochMillis)
        val txDateTime = java.time.LocalDateTime.ofInstant(txInstant, java.time.ZoneId.systemDefault())

        if (txDateTime.toLocalDate() == todayDate) {
            txDateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        } else {
            txDateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss - dd/MM"))
        }
    } catch (_: Throwable) {
        fallbackFormatted.ifEmpty { fallbackGroup }
    }
}

