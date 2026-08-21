package com.devuloopers.knet.ui.desktop.traffic.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.clientNetwork.decoder.BinaryCategory
import com.devuloopers.knet.domain.clientNetwork.decoder.MediaTypeInspector
import com.devuloopers.knet.domain.workspace.model.TrafficTableColumnWidths
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficRowUiState
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficInterceptionUiState
import com.devuloopers.knet.ui.core.components.table.KNetCell
import com.devuloopers.knet.ui.core.components.table.KNetRow
import com.devuloopers.knet.ui.core.components.table.KNetTableHeader
import com.devuloopers.knet.ui.core.components.table.KNetColumnResizeHandle
import com.devuloopers.knet.ui.core.components.scrollbar.KNetHorizontalScrollbar
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.core.foundation.time.KNetDateTime
import com.devuloopers.knet.ui.desktop.traffic.model.ColumnVisibilityState
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficColumn
import com.devuloopers.knet.ui.desktop.traffic.model.ResolvedTrafficColumnLayout
import com.devuloopers.knet.ui.desktop.traffic.model.resolveTrafficColumnLayout
import com.devuloopers.knet.ui.desktop.traffic.model.toTrafficHostLabel

import com.devuloopers.knet.ui.core.components.menu.ContextMenuItem
import com.devuloopers.knet.ui.core.components.menu.KNetContextMenuArea

private object TrafficTableMetrics {
    val rowHeight = 34.dp
    val footerHeight = 48.dp
}

/**
 * Cohesive callbacks for caller-owned Traffic column sizing state.
 *
 * @property onResize Receives an absolute proposed width while one separator is dragged.
 * @property onResizeFinished Signals that the current width snapshot can be persisted.
 * @property onReset Requests restoration of one column's default sizing mode.
 */
data class TrafficTableColumnResizeActions(
    val onResize: (TrafficColumn, Float) -> Unit = { _, _ -> },
    val onResizeFinished: () -> Unit = {},
    val onReset: (TrafficColumn) -> Unit = {},
)

/**
 * High-density virtualized traffic feed table using standardized UI-core table primitives and tokens.
 *
 * @param transactions Current bounded page window in newest-first order.
 * @param selectedId Selected canonical exchange identifier, or null.
 * @param autoScroll Whether a genuinely newer exchange should move the viewport to the first row.
 * @param onSelectTransaction Invoked with the selected canonical exchange identifier.
 * @param formattedVisibleSize Human-readable size of the currently visible rows.
 * @param totalAvailableCount Exact matching count reported by persistent storage.
 * @param modifier Modifier applied to the table workspace.
 * @param columnVisibility Typed visibility state for optional columns.
 * @param columnWidths Persisted logical column widths; a null Path width means automatic fill.
 * @param columnResizeActions Caller-owned resize, commit, and per-column reset callbacks.
 * @param onSendToApiStudio Requests export of one canonical exchange to API Studio.
 * @param onAddBreakpointRule Requests creation of a rule draft from one canonical exchange.
 * @param activeRules Current authored breakpoint rules used for row decoration.
 * @param canLoadMore Whether another keyset page is available.
 * @param onLoadMore Requests the next keyset page.
 */
@Composable
fun TrafficTable(
    transactions: List<TrafficRowUiState>,
    selectedId: String?,
    autoScroll: Boolean,
    onSelectTransaction: (String) -> Unit,
    formattedVisibleSize: String,
    totalAvailableCount: Long,
    modifier: Modifier = Modifier,
    columnVisibility: ColumnVisibilityState = ColumnVisibilityState(),
    columnWidths: TrafficTableColumnWidths = TrafficTableColumnWidths(),
    columnResizeActions: TrafficTableColumnResizeActions = TrafficTableColumnResizeActions(),
    onSendToApiStudio: (String) -> Unit = {},
    onAddBreakpointRule: (String) -> Unit = {},
    activeRules: List<com.devuloopers.knet.domain.rules.model.BreakpointRule> = emptyList(),
    canLoadMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.background)
    ) {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val horizontalScrollState = rememberScrollState()
        val todayDateState = remember { mutableStateOf(KNetDateTime.currentDateKey()) }
        val lastAutoScrollSequence = remember { mutableStateOf<Long?>(null) }
        val lastAutoScrollEnabled = remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000L)
                val currentDateKey = KNetDateTime.currentDateKey()
                if (todayDateState.value != currentDateKey) {
                    todayDateState.value = currentDateKey
                }
            }
        }

        val newestSequence = transactions.maxOfOrNull(TrafficRowUiState::sequenceNumber)
        LaunchedEffect(newestSequence, autoScroll) {
            val previousSequence = lastAutoScrollSequence.value
            val wasEnabled = lastAutoScrollEnabled.value
            lastAutoScrollEnabled.value = autoScroll
            if (newestSequence == null) {
                lastAutoScrollSequence.value = null
                return@LaunchedEffect
            }
            if (previousSequence == null || newestSequence > previousSequence) {
                lastAutoScrollSequence.value = newestSequence
            }
            if (
                shouldAutoScrollToNewest(
                    previousSequence = previousSequence,
                    currentSequence = newestSequence,
                    wasEnabled = wasEnabled,
                    isEnabled = autoScroll,
                )
            ) {
                listState.scrollToItem(0)
            }
        }

        LaunchedEffect(listState, transactions.size, canLoadMore) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                .distinctUntilChanged()
                .collect { lastVisibleIndex ->
                    if (canLoadMore && lastVisibleIndex != null && lastVisibleIndex >= transactions.lastIndex - 12) {
                        onLoadMore()
                    }
                }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val columnLayout = remember(columnWidths, columnVisibility, maxWidth) {
                resolveTrafficColumnLayout(
                    widths = columnWidths,
                    visibility = columnVisibility,
                    viewportWidthDp = maxWidth.value,
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TrafficTableMetrics.rowHeight)
                        .horizontalScroll(horizontalScrollState),
                ) {
                    TableHeaderRow(
                        columnVisibility = columnVisibility,
                        columnLayout = columnLayout,
                        onColumnResize = columnResizeActions.onResize,
                        onColumnResizeFinished = columnResizeActions.onResizeFinished,
                        onResetColumnWidth = columnResizeActions.onReset,
                        modifier = Modifier.width(columnLayout.tableWidthDp.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    if (transactions.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No captured traffic matching current filters",
                                style = typography.caption,
                                color = themeColors.textSecondary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(horizontalScrollState),
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .width(columnLayout.tableWidthDp.dp)
                                    .fillMaxHeight(),
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
                                            },
                                            ContextMenuItem(
                                                label = "Add Breakpoint Rule",
                                                icon = KNetIcons.Pause
                                            ) {
                                                onAddBreakpointRule(item.transactionId)
                                            }
                                        )
                                    }

                                    KNetContextMenuArea(items = contextMenuItems) {
                                        TableRowItem(
                                            item = item,
                                            isSelected = item.transactionId == selectedId,
                                            columnVisibility = columnVisibility,
                                            columnLayout = columnLayout,
                                            todayDate = todayDateState.value,
                                            activeRules = activeRules,
                                            onClick = { onSelectTransaction(item.transactionId) }
                                        )
                                    }
                                }
                            }
                        }
                        KNetVerticalScrollbar(
                            lazyListState = listState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight(),
                        )
                        KNetHorizontalScrollbar(
                            scrollState = horizontalScrollState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // Bottom Statistics Footer Bar (48dp / h-12)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TrafficTableMetrics.footerHeight)
                .background(themeColors.background)
                .border(width = 1.dp, color = themeColors.border)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = requestCountLabel(
                    loadedCount = transactions.size,
                    totalAvailableCount = totalAvailableCount,
                ),
                style = typography.caption.copy(color = themeColors.textSecondary),
                maxLines = 1,
                softWrap = false
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedVisibleSize,
                    style = typography.caption.copy(color = themeColors.textSecondary),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

private fun requestCountLabel(loadedCount: Int, totalAvailableCount: Long): String {
    val effectiveTotal = maxOf(loadedCount.toLong(), totalAvailableCount)
    return if (loadedCount.toLong() < effectiveTotal) {
        "$loadedCount of $effectiveTotal Requests"
    } else {
        "$effectiveTotal Requests"
    }
}

/** Returns true only for initial display, explicit enablement, or a genuinely newer capture. */
internal fun shouldAutoScrollToNewest(
    previousSequence: Long?,
    currentSequence: Long?,
    wasEnabled: Boolean,
    isEnabled: Boolean,
): Boolean = isEnabled && currentSequence != null && (
    !wasEnabled || previousSequence == null || currentSequence > previousSequence
)

@Composable
private fun TableHeaderRow(
    columnVisibility: ColumnVisibilityState,
    columnLayout: ResolvedTrafficColumnLayout,
    onColumnResize: (TrafficColumn, Float) -> Unit,
    onColumnResizeFinished: () -> Unit,
    onResetColumnWidth: (TrafficColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    KNetTableHeader(
        modifier = modifier
            .height(TrafficTableMetrics.rowHeight)
            .border(width = 1.dp, color = themeColors.border)
    ) {
        val visibleColumns = TrafficColumn.entries.filter(columnVisibility::isVisible)
        visibleColumns.forEachIndexed { index, column ->
            val widthDp = columnLayout.widthDp(column)
            Box(
                modifier = Modifier
                    .width(widthDp.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = column.headerLabel,
                    modifier = Modifier.padding(
                        start = spacing.xs,
                        end = spacing.md,
                    ),
                    style = typography.codeSmall.copy(
                        color = themeColors.textMuted,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                KNetColumnResizeHandle(
                    widthDp = widthDp,
                    onWidthChange = { requestedWidth -> onColumnResize(column, requestedWidth) },
                    onResizeFinished = onColumnResizeFinished,
                    onReset = {
                        onResetColumnWidth(column)
                    },
                    idleIndicatorVisible = index < visibleColumns.lastIndex,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TableRowItem(
    item: TrafficRowUiState,
    isSelected: Boolean,
    columnVisibility: ColumnVisibilityState,
    columnLayout: ResolvedTrafficColumnLayout,
    todayDate: String,
    activeRules: List<com.devuloopers.knet.domain.rules.model.BreakpointRule>,
    onClick: () -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    val isPausedByBreakpoint = item.interception is TrafficInterceptionUiState.Paused
    val isMatchedByBreakpoint = item.interception !is TrafficInterceptionUiState.None

    val displayMethod = item.displayMethod

    val methodColor = when (item.requestKind) {
        com.devuloopers.knet.domain.request.descriptor.RequestKindId.GRAPHQL ->
            androidx.compose.ui.graphics.Color(0xFFCBA6F7)

        else -> when (item.method.uppercase()) {
            "GET" -> themeColors.semantic.success
            "POST" -> themeColors.semantic.info
            "OPTIONS" -> themeColors.semantic.warning
            else -> themeColors.textSecondary
        }
    }

    val isCompleted = item.formattedTime != "-"

    val statusColor = when {
        item.status in 200..299 -> themeColors.semantic.success
        item.status in 300..399 -> themeColors.semantic.warning
        item.status in 400..599 -> themeColors.semantic.error
        item.status == 101 -> themeColors.semantic.info
        item.statusText.equals("Dropped", ignoreCase = true) -> themeColors.semantic.error
        item.statusText.equals("Timed Out", ignoreCase = true) -> themeColors.textMuted
        isCompleted && item.status == 0 -> themeColors.semantic.error
        !isCompleted && item.status == 0 -> androidx.compose.ui.graphics.Color(0xFFFAB387)
        else -> themeColors.textSecondary
    }

    val inferredType = when (val category = MediaTypeInspector.inspectCategory(item.contentType)) {
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
                item.contentType?.contains("json", ignoreCase = true) == true -> "JSON"
                item.contentType?.contains("html", ignoreCase = true) == true -> "HTML"
                item.contentType?.contains("xml", ignoreCase = true) == true -> "XML"
                item.contentType?.contains("css", ignoreCase = true) == true -> "CSS"
                item.contentType?.contains("javascript", ignoreCase = true) == true ||
                    item.contentType?.contains("js", ignoreCase = true) == true -> "JS"
                else -> "Other"
            }
    }

    val displayPath = item.path

    val rowBackgroundModifier = when {
        isPausedByBreakpoint -> Modifier.background(themeColors.semantic.warning.copy(alpha = 0.18f))
        isMatchedByBreakpoint -> Modifier.background(themeColors.semantic.warning.copy(alpha = 0.10f))
        else -> Modifier
    }

    KNetRow(
        onClick = onClick,
        selected = isSelected,
        modifier = Modifier
            .height(TrafficTableMetrics.rowHeight)
            .then(rowBackgroundModifier)
            .then(
                if (isSelected) Modifier.border(width = 2.dp, color = themeColors.accent) else Modifier
            )
    ) {
        // # (Serial Number)
        if (columnVisibility.isVisible(TrafficColumn.SERIAL_NUMBER)) {
            KNetCell(
                text = "${item.sequenceNumber}",
                modifier = Modifier
                    .width(columnLayout.widthDp(TrafficColumn.SERIAL_NUMBER).dp)
                    .padding(start = spacing.xs, end = spacing.md),
                color = themeColors.textMuted
            )
        }

        // Timestamp
        if (columnVisibility.isVisible(TrafficColumn.TIMESTAMP)) {
            val formatted = formatTimestamp(item.timestamp, item.formattedTimestamp, item.dateGroup, todayDate)
            KNetCell(
                text = formatted,
                modifier = Modifier
                    .width(columnLayout.widthDp(TrafficColumn.TIMESTAMP).dp)
                    .padding(start = spacing.xs, end = spacing.md),
                color = themeColors.textSecondary
            )
        }

        // Method (Mandatory)
        Box(
            modifier = Modifier
                .width(columnLayout.widthDp(TrafficColumn.METHOD).dp)
                .padding(start = spacing.xs, end = spacing.md),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = displayMethod,
                style = typography.codeSmall.copy(color = methodColor, fontWeight = FontWeight.Bold),
                maxLines = 1,
                softWrap = false
            )
        }

        // Effective response protocol, falling back to the client request protocol while pending.
        if (columnVisibility.isVisible(TrafficColumn.PROTOCOL)) {
            KNetCell(
                text = item.upstreamProtocol?.token ?: item.clientProtocol.token,
                modifier = Modifier
                    .width(columnLayout.widthDp(TrafficColumn.PROTOCOL).dp)
                    .padding(start = spacing.xs, end = spacing.md),
                color = themeColors.textSecondary,
            )
        }

        if (columnVisibility.isVisible(TrafficColumn.STREAM)) {
            KNetCell(
                text = item.streamId?.toString() ?: "-",
                modifier = Modifier
                    .width(columnLayout.widthDp(TrafficColumn.STREAM).dp)
                    .padding(start = spacing.xs, end = spacing.md),
                color = themeColors.textSecondary,
            )
        }

        // Capture origin is independent from protocol and connectivity ingress.
        if (columnVisibility.isVisible(TrafficColumn.SOURCE)) {
            KNetCell(
                text = item.origin.displayName,
                modifier = Modifier
                    .width(columnLayout.widthDp(TrafficColumn.SOURCE).dp)
                    .padding(start = spacing.xs, end = spacing.md),
                color = themeColors.textSecondary,
            )
        }

        // Host (Mandatory)
        Box(
            modifier = Modifier
                .width(columnLayout.widthDp(TrafficColumn.HOST).dp)
                .padding(start = spacing.xs, end = spacing.md),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = item.host.toTrafficHostLabel(item.scheme),
                style = typography.codeSmall.copy(color = themeColors.textPrimary),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Path (Mandatory)
        Box(
            modifier = Modifier
                .width(columnLayout.widthDp(TrafficColumn.PATH).dp)
                .padding(start = spacing.xs, end = spacing.md),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = displayPath,
                style = typography.codeSmall.copy(color = themeColors.textSecondary),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Status
        if (columnVisibility.isVisible(TrafficColumn.STATUS)) {
            Box(
                modifier = Modifier
                    .width(columnLayout.widthDp(TrafficColumn.STATUS).dp)
                    .padding(start = spacing.xs, end = spacing.md),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = when {
                        item.status > 0 -> "${item.status}"
                        item.statusText.equals("Dropped", ignoreCase = true) -> "Dropped"
                        item.statusText.equals("Timed Out", ignoreCase = true) -> "Timed Out"
                        isCompleted -> "ERR"
                        else -> "In Progress"
                    },
                    style = typography.codeSmall.copy(color = statusColor, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Size
        if (columnVisibility.isVisible(TrafficColumn.SIZE)) {
            KNetCell(
                text = item.formattedSize,
                modifier = Modifier
                    .width(columnLayout.widthDp(TrafficColumn.SIZE).dp)
                    .padding(start = spacing.xs, end = spacing.md),
                color = themeColors.textPrimary
            )
        }

        // Time (Duration)
        if (columnVisibility.isVisible(TrafficColumn.DURATION)) {
            KNetCell(
                text = item.formattedTime,
                modifier = Modifier
                    .width(columnLayout.widthDp(TrafficColumn.DURATION).dp)
                    .padding(start = spacing.xs, end = spacing.md),
                color = themeColors.textPrimary
            )
        }

        // Type
        if (columnVisibility.isVisible(TrafficColumn.TYPE)) {
            KNetCell(
                text = inferredType,
                modifier = Modifier
                    .width(columnLayout.widthDp(TrafficColumn.TYPE).dp)
                    .padding(start = spacing.xs, end = spacing.md),
                color = themeColors.textMuted
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

private fun formatTimestamp(
    epochMillis: Long,
    fallbackFormatted: String,
    fallbackGroup: String,
    todayDate: String
): String {
    if (epochMillis <= 0L) return fallbackFormatted.ifEmpty { fallbackGroup }
    return try {
        if (KNetDateTime.dateKey(epochMillis) == todayDate) {
            KNetDateTime.time(epochMillis)
        } else {
            KNetDateTime.timeAndDayMonth(epochMillis)
        }
    } catch (_: Exception) {
        fallbackFormatted.ifEmpty { fallbackGroup }
    }
}
