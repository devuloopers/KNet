package com.devuloopers.knet.ui.apistudio.view.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.devuloopers.knet.domain.apistudio.model.ApiCollection
import com.devuloopers.knet.domain.apistudio.runner.SuiteRequestResult
import com.devuloopers.knet.domain.apistudio.runner.SuiteRunSummary
import com.devuloopers.knet.theme.KNetColors
import com.devuloopers.knet.ui.apistudio.handler.SuiteExecutionConfig
import com.devuloopers.knet.ui.apistudio.handler.SuiteExecutionScope
import com.devuloopers.knet.widgets.WidgetSearchBar

/**
 * Interactive, modular, 3-Phase Suite Runner Dialog providing responsive setup, live execution, and results logging.
 *
 * @param collections Complete list of workspace [ApiCollection] items.
 * @param initialSelectedCollectionIds Set of collection IDs to pre-select upon opening.
 * @param isRunning Whether execution is currently active in background.
 * @param summary Aggregated execution summary [SuiteRunSummary].
 * @param onExecuteSuite Callback invoked with resolved [SuiteExecutionConfig] when user initiates run.
 * @param onDismiss Callback invoked when dialog is dismissed.
 */
@Composable
fun SuiteRunnerDialog(
    collections: List<ApiCollection>,
    initialSelectedCollectionIds: List<String> = emptyList(),
    isRunning: Boolean,
    summary: SuiteRunSummary?,
    onExecuteSuite: (SuiteExecutionConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val setupState = remember(collections, initialSelectedCollectionIds) {
        SuiteRunnerSetupState(
            collections = collections,
            initialSelectedIds = initialSelectedCollectionIds.toSet()
        )
    }

    var isConfigMode by remember { mutableStateOf(!isRunning && summary == null) }

    LaunchedEffect(isRunning, summary) {
        if (isRunning || summary != null) {
            isConfigMode = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .heightIn(max = 620.dp)
                .background(KNetColors.SurfaceDark, RoundedCornerShape(12.dp))
                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column {
                if (isConfigMode) {
                    // PHASE 1: MODULAR EXECUTION SETUP VIEW
                    SetupHeader(
                        allSelected = setupState.selectedIds.size == setupState.collections.size && setupState.collections.isNotEmpty(),
                        onSelectAll = { setupState.selectAll() },
                        onClearAll = { setupState.clearAll() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    WidgetSearchBar(
                        query = setupState.searchQuery,
                        onQueryChange = { setupState.searchQuery = it },
                        placeholder = "Search collections...",
                        height = 36.dp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Collection List Container (Responsive Dynamic Fill)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(min = 160.dp, max = 320.dp)
                            .background(KNetColors.BackgroundDark, RoundedCornerShape(6.dp))
                            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        val filteredList = setupState.filteredCollections
                        if (setupState.collections.isEmpty()) {
                            EmptyWorkspaceState(message = "No executable collections found. Collections must contain at least 1 request to run in a suite.")
                        } else if (filteredList.isEmpty()) {
                            EmptyWorkspaceState(message = "No collections match '${setupState.searchQuery}'")
                        } else {
                            CollectionList(
                                collections = filteredList,
                                selectedIds = setupState.selectedIds,
                                onToggleSelection = { collectionId -> setupState.toggleSelection(collectionId) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (setupState.selectedIds.isEmpty() && setupState.collections.isNotEmpty()) {
                        EmptySelectionBanner()
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    SetupFooter(
                        setupState = setupState,
                        totalCollectionsCount = setupState.collections.size,
                        onCancel = onDismiss,
                        onRun = {
                            onExecuteSuite(
                                SuiteExecutionConfig(
                                    scope = SuiteExecutionScope.Collections(setupState.selectedIds.toList()),
                                    selectedCollectionIds = setupState.selectedIds.toList()
                                )
                            )
                        }
                    )

                } else {
                    // PHASE 2 & 3: MODULAR RUNNING & RESULTS VIEW
                    ResultsPhaseHeader(isRunning = isRunning)

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isRunning) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = KNetColors.ActiveBlue,
                            trackColor = KNetColors.FieldDark
                        )
                    } else if (summary != null) {
                        SummaryMetricsRow(summary = summary)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "TEST RESULTS LOG",
                        color = KNetColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(min = 200.dp, max = 320.dp)
                            .background(KNetColors.BackgroundDark, RoundedCornerShape(6.dp))
                            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        if (summary?.results.isNullOrEmpty() && isRunning) {
                            Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = KNetColors.ActiveBlue, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Executing suite request queue...", color = KNetColors.TextSecondary, fontSize = 11.sp)
                            }
                        } else if (summary != null) {
                            var expandedResultIds by remember { mutableStateOf(emptySet<String>()) }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(summary.results) { resultItem ->
                                    val requestId = resultItem.request.id
                                    val isExpanded = expandedResultIds.contains(requestId)

                                    SuiteResultLogRow(
                                        resultItem = resultItem,
                                        isExpanded = isExpanded,
                                        onToggleExpand = {
                                            expandedResultIds = if (isExpanded) expandedResultIds - requestId else expandedResultIds + requestId
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    ResultsFooter(
                        onConfigure = { isConfigMode = true },
                        onClose = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupHeader(
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("SUITE RUNNER SETUP", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Text("Select Collections to Execute", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Box(
            modifier = Modifier
                .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                .clickable { if (allSelected) onClearAll() else onSelectAll() }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (allSelected) "Clear All" else "Select All",
                color = KNetColors.ActiveBlue,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CollectionList(
    collections: List<ApiCollection>,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(collections) { collection ->
            val isChecked = selectedIds.contains(collection.id)
            val reqCount = collection.folders.sumOf { it.requests.size }
            val formattedReqText = QuantityFormatter.format(reqCount, "Request")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KNetColors.SurfaceDark, RoundedCornerShape(6.dp))
                    .clickable { onToggleSelection(collection.id) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { onToggleSelection(collection.id) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = KNetColors.ActiveBlue,
                            uncheckedColor = KNetColors.TextSecondary
                        )
                    )
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = collection.name,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = formattedReqText,
                        color = KNetColors.TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyWorkspaceState(message: String) {
    Text(
        text = message,
        color = KNetColors.TextSecondary,
        fontSize = 11.sp,
        modifier = Modifier.padding(20.dp)
    )
}

@Composable
private fun EmptySelectionBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFEF4444).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = Color(0xFFEF4444),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "No collections selected. Select one or more collections to continue.",
            color = Color(0xFFEF4444),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SetupFooter(
    setupState: SuiteRunnerSetupState,
    totalCollectionsCount: Int,
    onCancel: () -> Unit,
    onRun: () -> Unit
) {
    val selectedCount = setupState.selectedIds.size
    val formattedCollectionsText = QuantityFormatter.format(selectedCount, "Collection")
    val formattedTotalReqText = QuantityFormatter.format(setupState.totalSelectedRequests, "Request")
    val hasSelection = selectedCount > 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Selected $selectedCount of $totalCollectionsCount Collections ($formattedTotalReqText)",
            color = KNetColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(KNetColors.FieldDark, RoundedCornerShape(6.dp))
                    .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                    .clickable { onCancel() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .background(
                        if (hasSelection) KNetColors.ActiveBlue else KNetColors.FieldDark.copy(alpha = 0.5f),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable(enabled = hasSelection) { onRun() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (hasSelection) Color.White else KNetColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Run Selected Suite (${setupState.totalSelectedRequests})",
                        color = if (hasSelection) Color.White else KNetColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultsPhaseHeader(isRunning: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("SUITE RUNNER RESULTS", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Text("Execution Log", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Box(
            modifier = Modifier
                .background(
                    if (isRunning) KNetColors.ActiveBlue.copy(alpha = 0.15f) else KNetColors.SuccessGreen.copy(alpha = 0.15f),
                    RoundedCornerShape(4.dp)
                )
                .border(1.dp, if (isRunning) KNetColors.ActiveBlue else KNetColors.SuccessGreen, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isRunning) "RUNNING..." else "COMPLETED",
                color = if (isRunning) KNetColors.ActiveBlue else KNetColors.SuccessGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SummaryMetricsRow(summary: SuiteRunSummary) {
    val totalReqsText = QuantityFormatter.format(summary.totalRequests, "Request")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KNetColors.BackgroundDark, RoundedCornerShape(6.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("TOTAL EXECUTED", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(totalReqsText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Column {
            Text("PASSED", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("${summary.passedCount}", color = KNetColors.SuccessGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Column {
            Text("FAILED", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "${summary.failedCount}",
                color = if (summary.failedCount > 0) Color(0xFFEF4444) else KNetColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column {
            Text("AVG LATENCY", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("${summary.averageLatencyMs} ms", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultsFooter(
    onConfigure: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(KNetColors.FieldDark, RoundedCornerShape(6.dp))
                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                .clickable { onConfigure() }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("Configure Selection", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Box(
            modifier = Modifier
                .background(KNetColors.FieldDark, RoundedCornerShape(6.dp))
                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                .clickable { onClose() }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("Close", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SuiteResultLogRow(
    resultItem: SuiteRequestResult,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val statusCode = resultItem.executionResult.statusCode
    val statusText = resultItem.executionResult.statusText
    val isNetworkSuccess = resultItem.executionResult.isSuccess
    val assertions = resultItem.assertionResults
    val allAssertionsPassed = assertions.all { it.passed }
    val overallSuccess = isNetworkSuccess && allAssertionsPassed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KNetColors.SurfaceDark, RoundedCornerShape(6.dp))
            .border(
                1.dp,
                if (overallSuccess) KNetColors.BorderDark else Color(0xFFEF4444).copy(alpha = 0.3f),
                RoundedCornerShape(6.dp)
            )
            .clickable { onToggleExpand() }
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(
                    text = resultItem.request.method.name,
                    color = Color(resultItem.request.method.badgeColorHex),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = resultItem.request.name,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (assertions.isNotEmpty()) {
                    val passedCount = assertions.count { it.passed }
                    val assertionText = QuantityFormatter.format(assertions.size, "Assertion")
                    Box(
                        modifier = Modifier
                            .background(
                                if (allAssertionsPassed) KNetColors.SuccessGreen.copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$passedCount/$assertionText",
                            color = if (allAssertionsPassed) KNetColors.SuccessGreen else Color(0xFFEF4444),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                StatusBadge(statusCode = statusCode, statusText = statusText, isSuccess = isNetworkSuccess)
                Spacer(modifier = Modifier.width(8.dp))

                Text("${resultItem.executionResult.latencyMs} ms", color = KNetColors.TextSecondary, fontSize = 10.sp)
                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand assertions",
                    tint = KNetColors.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(KNetColors.BackgroundDark, RoundedCornerShape(4.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (assertions.isEmpty()) {
                    Text(
                        text = if (isNetworkSuccess) "No script test assertions configured for this request."
                        else "Network request failed: ${resultItem.executionResult.errorMessage ?: "Connection error"}",
                        color = if (isNetworkSuccess) KNetColors.TextSecondary else Color(0xFFEF4444),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    assertions.forEach { assertion ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (assertion.passed) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = if (assertion.passed) "Passed assertion" else "Failed assertion",
                                tint = if (assertion.passed) KNetColors.SuccessGreen else Color(0xFFEF4444),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = assertion.name,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    statusCode: Int,
    statusText: String,
    isSuccess: Boolean
) {
    val (label, badgeColor) = when {
        statusCode in 200..299 -> Pair("$statusCode $statusText", KNetColors.SuccessGreen)
        statusCode == 0 -> Pair("ERR Offline", Color(0xFFEF4444))
        else -> Pair("$statusCode ${statusText.ifBlank { "Error" }}", Color(0xFFEF4444))
    }

    Box(
        modifier = Modifier
            .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .border(1.dp, badgeColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = badgeColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
