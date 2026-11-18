package com.devuloopers.knet.ui.desktop.traffic.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.devuloopers.knet.application.contract.apistudio.CapturedApiStudioRequest
import com.devuloopers.knet.ui.core.components.split.SplitPaneDividerStyle
import com.devuloopers.knet.ui.core.components.split.HorizontalSplitPane
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.traffic.banner.TrafficErrorBanner
import com.devuloopers.knet.ui.desktop.traffic.filter.TrafficFilterBar
import com.devuloopers.knet.ui.desktop.traffic.filter.TrafficFilterBarActions
import com.devuloopers.knet.ui.desktop.traffic.filter.TrafficFilterBarState
import com.devuloopers.knet.ui.desktop.traffic.inspector.TrafficInspectorPanel
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficIntent
import com.devuloopers.knet.ui.desktop.traffic.table.TrafficTable
import com.devuloopers.knet.ui.desktop.traffic.table.TrafficTableColumnResizeActions
import com.devuloopers.knet.ui.desktop.traffic.toolbar.TrafficToolbar
import com.devuloopers.knet.ui.desktop.traffic.toolbar.TrafficToolbarActions
import com.devuloopers.knet.ui.desktop.traffic.toolbar.TrafficToolbarState
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel
import com.devuloopers.knet.application.usecase.protectedtraffic.ProtectedTrafficQuickRuleScope
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.runtime.rememberCoroutineScope
import com.devuloopers.knet.ui.core.foundation.clipboard.setPlainText

/**
 * Top-level Live Traffic Workspace Screen composable bound strictly to :ui:core design tokens and parameter objects.
 * @param throughputIndicator Shell-composed actual throughput display; Traffic owns placement, not measurements.
 * @param onClearFeed Optional shell routing to retain the selected workspace during traffic deletion.
 * @param onImportCapture Opens the app-wide intake dialog without replacing this workspace.
 */
@Composable
fun TrafficScreen(
    viewModel: TrafficViewModel,
    protectionViewModel: com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficProtectionViewModel,
    onSendToApiStudio: (CapturedApiStudioRequest) -> Unit = {},
    onAddNetworkCondition: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    networkConditionsControl: @Composable () -> Unit = {},
    throughputIndicator: (@Composable () -> Unit)? = null,
    onClearFeed: (() -> Unit)? = null,
    onImportCapture: () -> Unit = {},
) {
    val exportViewModel: com.devuloopers.knet.ui.desktop.httppanel.export.CaptureExportViewModel = org.koin.compose.viewmodel.koinViewModel(key = "traffic-export")
    com.devuloopers.knet.ui.desktop.httppanel.export.CaptureExportDialog(exportViewModel)
    val copyViewModel: com.devuloopers.knet.ui.desktop.traffic.copy.TrafficDetailsCopyViewModel = org.koin.compose.viewmodel.koinViewModel()
    val copyState by copyViewModel.state.collectAsState()
    val state by viewModel.uiState.collectAsState()
    androidx.compose.runtime.LaunchedEffect(state.source) { copyViewModel.selectSource(state.source) }
    val protectionState by protectionViewModel.uiState.collectAsState()
    val selectedOpaque = state.selectedTransaction?.capturedOpaqueFlow
    val presentedProtection = protectionViewModel.stateForSelection(selectedOpaque, state.source, protectionState)
    val selectedProtectionId = state.selectedTransaction?.takeIf {
        it.rowKind == com.devuloopers.knet.ui.desktop.traffic.model.TrafficRowKind.OPAQUE_FLOW
    }?.transactionId?.let { com.devuloopers.knet.traffic.id.OpaqueFlowId(it) }
    androidx.compose.runtime.LaunchedEffect(selectedProtectionId, selectedOpaque?.connectionId, selectedOpaque?.serverName,
        selectedOpaque?.destination, selectedOpaque?.sourceApplicationId, selectedOpaque?.transport,
        selectedOpaque?.security, state.source) {
        if (selectedOpaque == null) protectionViewModel.selectFlow(selectedProtectionId)
        else protectionViewModel.selectCapturedFlow(selectedOpaque, state.source)
    }
    val themeColors = KNetTheme.colors
    var inspectorSplitRatio by remember { mutableFloatStateOf(0.65f) }
    val clipboard = LocalClipboard.current

    val toolbarState =
        remember(
            state.source,
            state.captureState,
            state.engineState,
            state.scrollSettings,
            state.localIpAddress,
            state.isClearingHistory,
            state.facetCounts.totalCount,
            state.selection,
        ) {
            TrafficToolbarState(
                captureState = state.captureState,
                engineState = state.engineState,
                scrollSettings = state.scrollSettings,
                localIpAddress = state.localIpAddress,
                isClearingHistory = state.isClearingHistory,
                exportAvailable = state.facetCounts.totalCount > 0 || state.selection.records.isNotEmpty(),
                selectedCount = state.selection.records.size,
                source = state.source,
            )
        }

    val toolbarActions = remember(viewModel, onClearFeed, onImportCapture) {
        TrafficToolbarActions(
            onImport = onImportCapture,
            onExport = {
                val selection = viewModel.exportSelection()
                if (selection != null) exportViewModel.open(selection, "${selection.records.size} selected traffic items")
                else exportViewModel.open(viewModel.exportSource(), "Traffic from selected source")
            },
            onStartCapture = { viewModel.processIntent(TrafficIntent.StartCapture) },
            onPauseCapture = { viewModel.processIntent(TrafficIntent.PauseCapture) },
            onClearFeed = onClearFeed ?: { viewModel.processIntent(TrafficIntent.ClearFeed) },
            onAutoScrollToggle = { viewModel.processIntent(TrafficIntent.ToggleAutoScroll) },
            onAutoScrollModeSelected = { viewModel.processIntent(TrafficIntent.SetAutoScrollMode(it)) },
        )
    }

    val filterBarState = remember(
        state.searchQuery,
        state.selectedTypeFilter,
        state.selectedSchemeFilter,
        state.selectedHttpVersionFilter,
        state.selectedMethodFilter,
        state.selectedStatusFilter,
        state.facetCounts,
        state.columnVisibility
    ) {
        TrafficFilterBarState(
            searchQuery = state.searchQuery,
            selectedType = state.selectedTypeFilter,
            selectedScheme = state.selectedSchemeFilter,
            selectedHttpVersion = state.selectedHttpVersionFilter,
            selectedMethod = state.selectedMethodFilter,
            selectedStatus = state.selectedStatusFilter,
            totalCount = state.facetCounts.totalCount,
            httpCount = state.facetCounts.httpCount,
            httpsCount = state.facetCounts.httpsCount,
            columnVisibility = state.columnVisibility
        )
    }

    val filterBarActions = remember(viewModel) {
        TrafficFilterBarActions(
            onSearchChange = { viewModel.processIntent(TrafficIntent.Search(it)) },
            onTypeSelected = { viewModel.processIntent(TrafficIntent.FilterByType(it)) },
            onSchemeSelected = { viewModel.processIntent(TrafficIntent.FilterByScheme(it)) },
            onHttpVersionSelected = { viewModel.processIntent(TrafficIntent.FilterByHttpVersion(it)) },
            onMethodSelected = { viewModel.processIntent(TrafficIntent.FilterByMethod(it)) },
            onStatusSelected = { viewModel.processIntent(TrafficIntent.FilterByStatus(it)) },
            onToggleColumn = { viewModel.processIntent(TrafficIntent.ToggleColumn(it)) },
            onResetColumnWidths = { viewModel.processIntent(TrafficIntent.ResetColumnWidths) },
        )
    }

    val handleExportToStudio: (String) -> Unit = remember(viewModel, onSendToApiStudio) {
        { transactionId ->
            viewModel.exportToStudioSpec(transactionId) { spec ->
                onSendToApiStudio(spec)
            }
        }
    }

    // Context commands capture only their original source, not the full row window/body state.
    val protectedActionSource = state.source
    val handleProtectedAction = remember(viewModel, protectionViewModel, protectedActionSource) {
        { id: com.devuloopers.knet.application.contract.traffic.TrafficRecordId, action: ProtectedTrafficAction ->
            val transactionId = when (id) {
                is com.devuloopers.knet.application.contract.traffic.TrafficRecordId.Exchange -> id.id.value
                is com.devuloopers.knet.application.contract.traffic.TrafficRecordId.Opaque -> id.id.value
            }
            viewModel.processIntent(TrafficIntent.SelectTransaction(transactionId))
            protectionViewModel.selectFlow((id as? com.devuloopers.knet.application.contract.traffic.TrafficRecordId.Opaque)?.id)
            protectionViewModel.applyRecordAction(id, action) { message ->
                viewModel.processIntent(TrafficIntent.ReportActionError(protectedActionSource, message))
            }
        }
    }
    val copySource = state.source
    val handleCopyDetails: (List<com.devuloopers.knet.application.contract.traffic.TrafficRecordId>, com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset) -> Unit = remember(copyViewModel, clipboard, copySource) {
        { records, preset -> copyViewModel.copy(com.devuloopers.knet.application.contract.export.CaptureExportSource.Selection(records, copySource), preset) { text ->
            clipboard.setPlainText(text)
        } }
    }

    val columnResizeActions = remember(viewModel) {
        TrafficTableColumnResizeActions(
            onResize = { column, widthDp ->
                viewModel.processIntent(TrafficIntent.ResizeColumn(column, widthDp))
            },
            onResizeFinished = {
                viewModel.processIntent(TrafficIntent.CommitColumnWidths)
            },
            onReset = { column ->
                viewModel.processIntent(TrafficIntent.ResetColumnWidth(column))
            },
        )
    }

    KNetSurface(
        modifier = modifier.fillMaxSize(),
        color = themeColors.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Capture controls retain their height; any overflow scrollbar occupies its own row.
            TrafficToolbar(
                state = toolbarState,
                actions = toolbarActions,
                networkConditionsControl = networkConditionsControl,
            )

            // 1.1 Error Banner (Visible when engine error occurs)
            TrafficErrorBanner(
                errorMessage = copyState.error ?: state.engineErrorMessage ?: state.trafficErrorMessage,
                onDismiss = { copyViewModel.clearError(); viewModel.processIntent(TrafficIntent.DismissEngineError) }
            )

            // 2. Quick Filters Row (40dp)
            TrafficFilterBar(
                state = filterBarState,
                actions = filterBarActions
            )

            // 3. Central Workspace (Table + Right Docked Resizable Inspector Split)
            HorizontalSplitPane(
                dividerStyle = SplitPaneDividerStyle.SINGLE_LINE,
                splitRatio = inspectorSplitRatio,
                onSplitRatioChange = { inspectorSplitRatio = it },
                firstPane = { paneModifier ->
                    TrafficTable(
                        selection = state.selection,
                        selectionActions = com.devuloopers.knet.ui.desktop.traffic.model.TrafficSelectionActions(
                            select = { id, gesture -> viewModel.processIntent(TrafficIntent.SelectTraffic(id, gesture)) },
                            selectLoaded = { viewModel.processIntent(TrafficIntent.SelectLoadedTraffic) },
                            clear = { viewModel.processIntent(TrafficIntent.ClearTrafficSelection) },
                            export = { viewModel.exportSelection()?.let { selection -> exportViewModel.open(selection, "${selection.records.size} selected traffic items") } },
                        ),
                onExportItem = { id -> exportViewModel.open(com.devuloopers.knet.application.contract.export.CaptureExportSource.Item(id), "Selected traffic item") },
                        transactions = state.filteredTransactions,
                        selectedId = state.selectedTransactionId,
                        autoScroll = state.autoScroll,
                        columnVisibility = state.columnVisibility,
                        columnWidths = state.columnWidths,
                        columnResizeActions = columnResizeActions,
                        onSelectTransaction = { viewModel.processIntent(TrafficIntent.SelectTransaction(it)) },
                        formattedVisibleSize = state.formattedVisibleSize,
                        totalAvailableCount = state.totalAvailableCount,
                        onSendToApiStudio = handleExportToStudio,
                        onAddBreakpointRule = viewModel::createBreakpointFromTransaction,
                        onAddNetworkCondition = onAddNetworkCondition,
                        onProtectedTrafficAction = handleProtectedAction,
                        onCopyDetails = handleCopyDetails,
                        activeRules = state.activeBreakpointRules,
                        canLoadMore = (state.nextPageCursor != null || state.olderPageAnchor != null) && !state.isPageLoading,
                        onLoadMore = { viewModel.processIntent(TrafficIntent.LoadNextPage) },
                        throughputIndicator = throughputIndicator,
                        viewportKey = state.source.preferenceKey,
                        initialViewport = state.viewport,
                        reading = state.reading,
                        readingEpoch = state.readingEpoch,
                        canLoadNewer = state.hasNewerTraffic && !state.isPageLoading && !state.newerPageBlocked,
                        scrollActions = com.devuloopers.knet.ui.desktop.traffic.table.TrafficTableScrollActions(
                            onUserViewportChanged = { viewModel.retainViewport(state.source, it, userScrolled = true) },
                            onJumpToLatest = { viewModel.processIntent(TrafficIntent.JumpToLatest) },
                            onVisibleRowsChanged = { viewModel.observeVisibleRows(state.source, state.readingEpoch, it) },
                            onLoadNewer = { viewModel.processIntent(TrafficIntent.LoadNewerPage) },
                        ),
                        onRetainViewport = { viewModel.retainViewport(state.source, it) },
                        modifier = paneModifier
                    )
                },
                secondPane = { paneModifier ->
                    TrafficInspectorPanel(
                        protectionState = presentedProtection,
                        protectionActions = com.devuloopers.knet.ui.desktop.traffic.model.TrafficProtectionActions(
                            apply = { action -> protectionViewModel.applyDestinationAction(action, selectedProtectionId) },
                            useDefault = { protectionViewModel.useDefaultHandling(selectedProtectionId) },
                        ),
                        selectedTransaction = state.selectedTransaction,
                        activeTab = state.activeInspectorTab,
                        activeRequestSubTab = state.activeRequestSubTab,
                        activeResponseSubTab = state.activeResponseSubTab,
                        preparedState = state.preparedState,
                        protocolMessages = state.protocolMessages,
                        onTabSelected = { viewModel.processIntent(TrafficIntent.SelectInspectorTab(it)) },
                        onRequestSubTabSelected = { viewModel.processIntent(TrafficIntent.SelectRequestSubTab(it)) },
                        onResponseSubTabSelected = { viewModel.processIntent(TrafficIntent.SelectResponseSubTab(it)) },
                        onProtocolMessageSelected = {
                            viewModel.processIntent(TrafficIntent.SelectProtocolMessage(it))
                        },
                        onLoadMoreProtocolMessages = {
                            viewModel.processIntent(TrafficIntent.LoadNextProtocolMessagePage)
                        },
                        onSendToApiStudio = handleExportToStudio,
                        modifier = paneModifier
                    )
                },
                minSplitRatio = 0.2f,
                maxSplitRatio = 0.85f,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            )
        }
    }
}
