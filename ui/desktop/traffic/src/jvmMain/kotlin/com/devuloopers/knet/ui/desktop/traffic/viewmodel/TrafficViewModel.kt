@file:OptIn(ExperimentalCoroutinesApi::class)

package com.devuloopers.knet.ui.desktop.traffic.viewmodel

import com.devuloopers.knet.traffic.model.absoluteUrl
import com.devuloopers.knet.application.usecase.traffic.LoadTrafficExchangeDetailsResult
import com.devuloopers.knet.application.usecase.traffic.LoadTrafficExchangeDetailsUseCase
import com.devuloopers.knet.application.usecase.traffic.ClearTrafficHistoryUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveLatestTrafficSessionUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficGenerationsUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveProtocolMessageChangesUseCase
import com.devuloopers.knet.application.usecase.traffic.QueryProtocolMessagesUseCase
import com.devuloopers.knet.application.usecase.traffic.LoadProtocolMessageBodyUseCase
import com.devuloopers.knet.application.usecase.traffic.LoadProtocolMessageBodyResult
import com.devuloopers.knet.application.usecase.traffic.PrepareCapturedNetworkRequestResult
import com.devuloopers.knet.application.usecase.traffic.PrepareCapturedNetworkRequestUseCase
import com.devuloopers.knet.application.usecase.traffic.QueryTrafficPageUseCase
import com.devuloopers.knet.application.usecase.traffic.QueryTrafficFacetsUseCase
import com.devuloopers.knet.application.usecase.traffic.TrafficBodyPreview
import com.devuloopers.knet.application.usecase.traffic.PauseTrafficCaptureUseCase
import com.devuloopers.knet.application.usecase.traffic.ResumeTrafficCaptureUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficCaptureStateUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ObservePendingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ObservePendingProtocolMessageBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.PrepareBreakpointRuleDraftResult
import com.devuloopers.knet.application.usecase.breakpoint.PrepareBreakpointRuleDraftUseCase
import com.devuloopers.knet.application.contract.breakpoint.PendingBreakpoint
import com.devuloopers.knet.application.contract.breakpoint.PendingProtocolMessageBreakpoint
import com.devuloopers.knet.application.usecase.inspection.ObserveInspectionAnnotationsUseCase
import com.devuloopers.knet.application.contract.traffic.TrafficSortDirection
import com.devuloopers.knet.application.contract.traffic.TrafficSource
import com.devuloopers.knet.application.contract.traffic.TrafficPageQuery
import com.devuloopers.knet.application.contract.traffic.TrafficRecordFilter
import com.devuloopers.knet.application.contract.traffic.TrafficFacetQuery
import com.devuloopers.knet.application.contract.traffic.TrafficFacetCounts
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePageQuery
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.contract.proxy.ProxyStopReason
import com.devuloopers.knet.application.contract.traffic.CaptureSessionState
import com.devuloopers.knet.application.usecase.proxy.ObserveProxyRuntimeStateUseCase
import com.devuloopers.knet.application.usecase.proxy.StartLoopbackProxyUseCase
import com.devuloopers.knet.application.usecase.proxy.StopProxyRuntimeUseCase
import com.devuloopers.knet.core.logger.KNetLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.network.usecase.ObserveLocalIpUseCase
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptor
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorBody
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorInput
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.domain.request.usecase.DescribeRequestUseCase
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.domain.settings.usecase.ObserveApplicationSettingsUseCase
import com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollMode
import com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollSettings
import com.devuloopers.knet.domain.workspace.model.TrafficTableColumnWidths
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.domain.workspace.usecase.UpdateWorkspaceLayoutUseCase
import com.devuloopers.knet.application.contract.apistudio.CapturedApiStudioRequest
import com.devuloopers.knet.application.contract.apistudio.CapturedApiStudioMessage
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName

import com.devuloopers.knet.ui.desktop.traffic.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds
import com.devuloopers.knet.domain.rules.usecase.ObserveRulesUseCase
import com.devuloopers.knet.application.usecase.graphql.ObserveGraphQLPersistedQueriesUseCase
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.http.HttpMethod as CanonicalHttpMethod
import com.devuloopers.knet.traffic.model.http.HttpStatus as CanonicalHttpStatus
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.StandardHttpScheme
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.StandardApplicationProtocol
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ViewModel managing live traffic feed state, proxy engine lifecycle observation, filtering, and inspection selection.
 */
class TrafficViewModel(
    private val observeLatestTrafficSessionUseCase: ObserveLatestTrafficSessionUseCase,
    private val queryTrafficPageUseCase: QueryTrafficPageUseCase,
    private val resolveTrafficSourceUseCase: com.devuloopers.knet.application.usecase.traffic.ResolveCapturedTrafficSourceUseCase,
    private val initialSource: TrafficSource,
    private val queryTrafficFacetsUseCase: QueryTrafficFacetsUseCase,
    private val observeTrafficGenerationsUseCase: ObserveTrafficGenerationsUseCase,
    private val observeProtocolMessageChangesUseCase: ObserveProtocolMessageChangesUseCase,
    private val queryProtocolMessagesUseCase: QueryProtocolMessagesUseCase,
    private val loadProtocolMessageBodyUseCase: LoadProtocolMessageBodyUseCase,
    private val clearTrafficHistoryUseCase: ClearTrafficHistoryUseCase,
    private val startLoopbackProxyUseCase: StartLoopbackProxyUseCase,
    private val stopProxyRuntimeUseCase: StopProxyRuntimeUseCase,
    observeProxyRuntimeStateUseCase: ObserveProxyRuntimeStateUseCase,
    private val pauseTrafficCaptureUseCase: PauseTrafficCaptureUseCase,
    private val resumeTrafficCaptureUseCase: ResumeTrafficCaptureUseCase,
    observeTrafficCaptureStateUseCase: ObserveTrafficCaptureStateUseCase,
    private val loadTrafficExchangeDetailsUseCase: LoadTrafficExchangeDetailsUseCase,
    observeLocalIpUseCase: ObserveLocalIpUseCase,
    private val observeApplicationSettingsUseCase: ObserveApplicationSettingsUseCase,
    private val prepareCapturedNetworkRequestUseCase: PrepareCapturedNetworkRequestUseCase,
    private val observeInspectionAnnotationsUseCase: ObserveInspectionAnnotationsUseCase,
    private val describeRequestUseCase: DescribeRequestUseCase,
    observeGraphQLPersistedQueriesUseCase: ObserveGraphQLPersistedQueriesUseCase,
    private val prepareBreakpointRuleDraftUseCase: PrepareBreakpointRuleDraftUseCase,
    observeRulesUseCase: ObserveRulesUseCase,
    observePendingBreakpointsUseCase: ObservePendingBreakpointsUseCase,
    observePendingProtocolMessageBreakpointsUseCase: ObservePendingProtocolMessageBreakpointsUseCase,
    getWorkspaceLayoutUseCase: GetWorkspaceLayoutUseCase,
    private val updateWorkspaceLayoutUseCase: UpdateWorkspaceLayoutUseCase,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<TrafficState> = _uiState.asStateFlow()
    private val proxyRuntimeStates = observeProxyRuntimeStateUseCase.execute()
    private val trafficCaptureStates = observeTrafficCaptureStateUseCase.execute()

    private val preparedStateCache = InspectorPreparedCache(
        maximumEntries = MAX_PREPARED_INSPECTOR_ENTRIES,
        maximumRetainedBytes = MAX_PREPARED_INSPECTOR_BYTES,
    )

    private val captureControlIntent = MutableStateFlow(
        CaptureControlIntent(
            shouldCapture = trafficCaptureStates.value is CaptureSessionState.Capturing,
            revision = 0L,
        ),
    )
    private var sourceGeneration = 0L
    private var clearingSource: TrafficSource? = null
    private val sourceWorkspaces = object : LinkedHashMap<TrafficSource, TrafficState>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TrafficSource, TrafficState>?): Boolean = size > 3
    }
    // Reading checkpoints survive eviction of expensive dormant row windows.
    private val sourceReadings = mutableMapOf<TrafficSource, TrafficReadingState>()
    private val sourceViewports = mutableMapOf<TrafficSource, TrafficViewport>()
    private val readingRefreshSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    private var scrollPreferenceRevision = 0L
    private var scrollPreferenceJob: Job? = null
    private var pendingSources: Map<String, TrafficSource?> = emptyMap()

    /** Switches views synchronously, retaining at most four body-free row windows and bounded shared prepared data. */
    fun selectSource(source: TrafficSource) {
        val previous = _uiState.value
        if (source == previous.source) return
        sourceGeneration++
        sourceReadings[previous.source] = previous.reading
        sourceViewports[previous.source] = previous.viewport
        // Remove the target before admitting the departing view, preserving three dormant windows plus the active one.
        val restored = sourceWorkspaces.remove(source) ?: TrafficState(source = source, reading = sourceReadings[source] ?: TrafficReadingState(), viewport = sourceViewports[source] ?: TrafficViewport())
        sourceWorkspaces[previous.source] = previous.copy(preparedState = InspectorPreparedState(),
            protocolMessages = ProtocolMessagesUiState(), activeBreakpointRules = emptyList(),
            prefilledBreakpointRule = null, prefilledBreakpointProtocolValues = emptyList(), isBreakpointDrawerVisible = false)
        filterRefreshJob?.cancel()
        breakpointDraftJob?.cancel()
        durableAnnotationJob?.cancel()
        durableTransactions = restored.transactions.filter { row -> row.interception !is TrafficInterceptionUiState.Paused }
        _uiState.value = restored.copy(source = source, sessionId = previous.sessionId, captureState = previous.captureState,
            engineState = previous.engineState, localIpAddress = previous.localIpAddress,
            engineErrorMessage = previous.engineErrorMessage, activeBreakpointRules = previous.activeBreakpointRules,
            columnWidths = previous.columnWidths, columnVisibility = previous.columnVisibility,
            scrollSettings = previous.scrollSettings, readingEpoch = previous.readingEpoch + 1,
            newerPageBlocked = false,
            preparedState = InspectorPreparedState(), protocolMessages = ProtocolMessagesUiState(),
            isPageLoading = false, isClearingHistory = previous.isClearingHistory)
        publishTrafficProjection()
        filterRefreshJob = viewModelScope.launch { loadTrafficPage(TrafficPageLoadMode.REFRESH_NEWEST) }
    }

    /**
     * Clears the explicit source captured by Clear or a confirmed background-tab Close.
     * The target never follows selection changes; duplicate clicks during deletion are ignored.
     * Storage owns capture rotation and full-history deletion, independent of the active search filters.
     */
    fun clearSource(clearedSource: TrafficSource) {
        if (_uiState.value.isClearingHistory) return
        clearingSource = clearedSource
        if (clearedSource == TrafficSource.All || _uiState.value.source == clearedSource) {
            sourceGeneration++ // Invalidate a page started before this clear; it cannot republish old rows.
            filterRefreshJob?.cancel()
        }
        _uiState.update { state -> state.copy(isClearingHistory = true, trafficErrorMessage = null) }
        viewModelScope.launch {
            try {
                clearTrafficHistoryUseCase.execute(clearedSource)
                if (clearedSource == TrafficSource.All) {
                    sourceReadings.clear()
                    sourceViewports.clear()
                    sourceWorkspaces.clear()
                } else {
                    sourceReadings.remove(clearedSource)
                    sourceViewports.remove(clearedSource)
                    sourceWorkspaces.remove(clearedSource)
                }
                val clearedCurrent = pageLoadMutex.withLock {
                    if (clearedSource != TrafficSource.All && _uiState.value.source != clearedSource) return@withLock false
                    durableAnnotationJob?.cancel()
                    durableTransactions = emptyList()
                    interceptionHistory.clear()
                    _uiState.update { state ->
                        state.copy(
                            transactions = emptyList(),
                            filteredTransactions = emptyList(),
                            totalAvailableCount = 0L,
                            selectedTransactionId = null,
                            preparedState = InspectorPreparedState(),
                            nextPageCursor = null,
                            olderPageAnchor = null,
                            pageGeneration = 0L,
                            facetCounts = TrafficFacetCounts(),
                            viewport = TrafficViewport(),
                            readingEpoch = state.readingEpoch + 1,
                            newerPageBlocked = false,
                            reading = TrafficReadingState(jumpRevision = state.reading.jumpRevision + 1),
                        )
                    }
                    true
                }
                // The bounded shared cache has no source index. Release stale preview references while
                // keeping a sibling workspace's directly held inspector state and row window unchanged.
                preparedStateCache.clear()
                if (clearedCurrent) {
                    publishTrafficProjection()
                    loadTrafficPage(TrafficPageLoadMode.REPLACE, allowDuringClear = true)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                KNetLogger.error("TrafficViewModel", error) { "Traffic history clear failed." }
                _uiState.update { state ->
                    state.copy(trafficErrorMessage = "Traffic history could not be cleared.")
                }
                // Rotation may already have changed the catalog, and earlier metadata batches may
                // have committed. Reconcile the retained window/count once after a failed clear too.
                if (_uiState.value.source == clearedSource) {
                    loadTrafficPage(TrafficPageLoadMode.REFRESH_NEWEST, expectedSource = clearedSource, allowDuringClear = true)
                }
            } finally {
                clearingSource = null
                _uiState.update { state -> state.copy(isClearingHistory = false) }
            }
        }
    }

    /**
     * Retains a source's viewport. Only an actual scroll operation changes Smart's live-edge intent;
     * stable-key relocation caused by incoming data cannot switch Smart off.
     */
    fun retainViewport(source: TrafficSource, viewport: TrafficViewport, userScrolled: Boolean = false) {
        sourceViewports[source] = viewport
        val current = _uiState.value
        if (current.source != source) {
            sourceWorkspaces[source]?.let { sourceWorkspaces[source] = it.copy(viewport = viewport) }
            return
        }
        val reading = current.reading
        // Only a measured newest row may resume Smart. The top of an old frozen window is not the live edge.
        val leftTop = userScrolled && !isTrafficAtTop(viewport.row, viewport.offset)
        val updated = reading.copy(atTop = if (leftTop) false else reading.atTop,
            windowThrough = if (leftTop && reading.windowThrough == null) reading.observed else reading.windowThrough)
        _uiState.update { it.copy(viewport = viewport, reading = updated,
            newerPageBlocked = if (userScrolled && viewport != current.viewport) false else it.newerPageBlocked) }
    }

    /**
     * Advances a source's last-seen position from measured, fully visible canonical rows.
     * Seeing a newer row acknowledges preceding arrivals. Only the actual newest row at the loaded
     * top, or explicit Jump to latest, acknowledges the entire known insertion snapshot.
     */
    fun observeVisibleRows(source: TrafficSource, epoch: Long, visible: TrafficVisibleRows) {
        val current = _uiState.value
        if (current.source != source || current.readingEpoch != epoch || clearingSource == source || clearingSource == TrafficSource.All) return
        val reading = current.reading
        if (reading.acknowledged == null) return
        val observed = reading.observed ?: return
        val visibleIds = visible.transactionIds.toHashSet()
        val points = current.filteredTransactions.asSequence().filter { it.transactionId in visibleIds }
            .mapNotNull(TrafficRowUiState::readPoint).toList()
        val reachedLatest = visible.atWindowTop && reading.latest != null && points.any { it == reading.latest }
        if (reachedLatest) {
            if (reading.atTop && reading.acknowledged == observed && reading.seenThrough == null && reading.newCount == 0L) return
            _uiState.update { state -> state.copy(reading = reading.copy(atTop = true, acknowledged = observed,
                seenThrough = null, windowThrough = null, newCount = 0, revision = reading.revision + 1)) }
            return
        }
        val seen = reading.nextSeenCheckpoint(points) ?: return
        _uiState.update { state -> state.copy(reading = reading.copy(
            seenThrough = seen, revision = reading.revision + 1)) }
        // Conflate progress updates independently of packet/lifecycle invalidations; never count per pixel.
        readingRefreshSignals.tryEmit(Unit)
    }

    private fun changeScrollSettings(settings: TrafficAutoScrollSettings) {
        val wasFollowing = _uiState.value.autoScroll
        scrollPreferenceRevision++
        _uiState.update { it.copy(scrollSettings = settings) }
        // One writer serializes rapid clicks; every transform uses DataStore's latest workspace.
        scrollPreferenceJob?.cancel()
        scrollPreferenceJob = viewModelScope.launch {
            updateWorkspaceLayoutUseCase.execute { it.copy(trafficAutoScroll = settings) }
        }
        if (!wasFollowing && _uiState.value.autoScroll) jumpToLatest()
        else if (!_uiState.value.autoScroll) _uiState.update {
            it.copy(reading = it.reading.copy(windowThrough = it.reading.observed))
        }
    }

    private fun jumpToLatest() {
        sourceGeneration++
        val target = _uiState.value.source
        _uiState.update { state -> state.copy(reading = state.reading.copy(atTop = true, windowThrough = null), readingEpoch = state.readingEpoch + 1) }
        filterRefreshJob?.cancel()
        filterRefreshJob = viewModelScope.launch {
            loadTrafficPage(TrafficPageLoadMode.REPLACE, expectedSource = target, acknowledge = true)
        }
    }

    private val pageLoadMutex = Mutex()
    private val messagePageLoadMutex = Mutex()
    private var filterRefreshJob: Job? = null
    private var breakpointDraftJob: Job? = null
    private var pendingDescriptorJob: Job? = null
    private var durableAnnotationJob: Job? = null
    private var durableTransactions: List<TrafficRowUiState> = emptyList()
    private var pendingBreakpoints: List<PendingBreakpoint> = emptyList()
    private var pendingProtocolMessages: List<PendingProtocolMessageBreakpoint> = emptyList()
    private var pendingRequestDescriptors: Map<String, RequestDescriptor> = emptyMap()
    private val interceptionHistory = object :
        LinkedHashMap<String, TrafficInterceptionUiState.Matched>(64, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, TrafficInterceptionUiState.Matched>?,
        ): Boolean = size > MAX_TRAFFIC_ROWS
    }

    /**
     * Asynchronously prepares a canonical captured request under a bounded whole-body budget and
     * maps it into the current API Studio contract.
     *
     * @param transactionId Unique UUID of the target transaction.
     * @param onSpecReady Callback executed on main thread with the constructed request specification.
     */
    fun exportToStudioSpec(
        transactionId: String,
        onSpecReady: (CapturedApiStudioRequest) -> Unit
    ) {
        val ownerGeneration = sourceGeneration
        viewModelScope.launch(backgroundDispatcher) {
            val spec = when (val result = prepareCapturedNetworkRequestUseCase.execute(ExchangeId(transactionId))) {
                is PrepareCapturedNetworkRequestResult.Found -> result.spec
                PrepareCapturedNetworkRequestResult.Missing,
                PrepareCapturedNetworkRequestResult.BodyUnavailable,
                PrepareCapturedNetworkRequestResult.IncompleteTarget,
                is PrepareCapturedNetworkRequestResult.BodyTooLarge -> null
            }
            if (spec != null) {
                val descriptor = describeRequestUseCase.execute(
                    RequestDescriptorInput(
                        transportMethod = spec.method,
                        absoluteUrl = spec.url,
                        headers = spec.headers.mapNotNull { (name, value) ->
                            name.trim().takeIf(String::isNotEmpty)?.let { HeaderField(HeaderName(it), value) }
                        },
                        body = spec.bodyPayload.encodeToByteArray()
                            .takeIf(ByteArray::isNotEmpty)
                            ?.copyOf(minOf(spec.bodyPayload.encodeToByteArray().size, RequestDescriptorBody.MAXIMUM_BYTES))
                            ?.let(::RequestDescriptorBody),
                        bodyComplete = spec.bodyPayload.encodeToByteArray().size <= RequestDescriptorBody.MAXIMUM_BYTES,
                    ),
                )
                KNetLogger.info("TrafficViewModel") {
                    "[EXPORT TO STUDIO] Successfully built spec for transactionId=$transactionId " +
                        "method=${spec.method} kind=${descriptor.kind.value}"
                }
                val capturedMessages = if (
                    descriptor.kind == RequestKindId.WEBSOCKET ||
                    descriptor.kind == RequestKindId.GRAPHQL_WEBSOCKET
                ) {
                    recoverStudioMessages(ExchangeId(transactionId))
                } else {
                    emptyList()
                }
                withContext(Dispatchers.Main) {
                    if (ownerGeneration != sourceGeneration) return@withContext
                    onSpecReady(
                        CapturedApiStudioRequest(
                            sourceExchangeId = transactionId,
                            kind = descriptor.kind,
                            spec = spec,
                            messages = capturedMessages,
                        ),
                    )
                }
            } else {
                KNetLogger.warn("TrafficViewModel") {
                    "[EXPORT TO STUDIO FAILED] Spec could not be resolved for transactionId=$transactionId"
                }
            }
        }
    }

    /** Recovers a small complete message prefix without making API Studio depend on Traffic storage. */
    private suspend fun recoverStudioMessages(exchangeId: ExchangeId): List<CapturedApiStudioMessage> {
        val page = runCatching {
            queryProtocolMessagesUseCase.execute(
                ProtocolMessagePageQuery(exchangeId = exchangeId, limit = CapturedApiStudioRequest.MAXIMUM_MESSAGES),
            )
        }.getOrNull() ?: return emptyList()
        return page.items.mapNotNull { message ->
            when (val body = runCatching { loadProtocolMessageBodyUseCase.execute(message) }.getOrNull()) {
                is LoadProtocolMessageBodyResult.Available -> body
                    .takeUnless { it.truncated }
                    ?.let {
                        CapturedApiStudioMessage(
                            kind = message.kind,
                            direction = message.direction,
                            payload = it.bytes,
                        )
                    }
                else -> null
            }
        }
    }

    init {
        readingRefreshSignals.conflate().onEach { delay(75.milliseconds) }
            .onEach { loadTrafficPage(TrafficPageLoadMode.REFRESH_NEWEST) }.launchIn(viewModelScope)
        val preferenceRevisionAtStart = scrollPreferenceRevision
        getWorkspaceLayoutUseCase.execute()
            .distinctUntilChanged()
            .onEach { settings ->
                _uiState.update { current ->
                    val scrollSettings = if (preferenceRevisionAtStart == scrollPreferenceRevision)
                        settings.trafficAutoScroll else current.scrollSettings
                    current.copy(columnWidths = settings.trafficTableColumnWidths, scrollSettings = scrollSettings,
                        reading = if (!scrollSettings.follows(current.reading) && current.reading.windowThrough == null)
                            current.reading.copy(windowThrough = current.reading.observed) else current.reading)
                }
            }
            .launchIn(viewModelScope)

        // Reactively observe active breakpoint rules from domain UseCase
        observeRulesUseCase()
            .onEach { rules ->
                _uiState.update { it.copy(activeBreakpointRules = rules) }
                schedulePendingRequestDescriptors()
            }
            .launchIn(viewModelScope)

        // Pending breakpoint candidates are a bounded live projection, not a second traffic
        // repository. They decorate or temporarily supply the canonical exchange row by the same
        // ExchangeId while the network is suspended, and remain independent from body storage.
        observePendingBreakpointsUseCase.execute()
            .onEach { events ->
                pendingBreakpoints = events
                events.forEach(::rememberInterception)
                publishTrafficProjection()
                schedulePendingRequestDescriptors()
            }
            .launchIn(viewModelScope)

        // Framed protocol-message pauses decorate the same canonical parent exchange. They do not
        // create a second traffic row or own another copy of the HTTP body.
        observePendingProtocolMessageBreakpointsUseCase.execute()
            .onEach { events ->
                pendingProtocolMessages = events
                events.forEach(::rememberInterception)
                publishTrafficProjection()
                schedulePendingRequestDescriptors()
            }
            .launchIn(viewModelScope)

        // Serialize desired capture state and dynamic port changes without cancelling lifecycle work.
        viewModelScope.launch {
            val proxyPorts = observeApplicationSettingsUseCase.execute()
                .map { settings -> settings.proxyPort.value }
                .distinctUntilChanged()
            combine(captureControlIntent, proxyPorts) { intent, port ->
                CaptureCommand(
                    shouldCapture = intent.shouldCapture,
                    port = port,
                    revision = intent.revision,
                )
            }
                .distinctUntilChanged()
                .collect { command ->
                    if (command.shouldCapture) {
                        startOrResumeCapture(command.port)
                    } else {
                        pauseTrafficCaptureUseCase.execute()
                    }
                }
        }

        // Reactive Transaction Payload Preparation via flatMapLatest
        viewModelScope.launch {
            combine(
                _uiState.map { it.selectedTransaction }.distinctUntilChanged(),
                observeGraphQLPersistedQueriesUseCase.execute().onEach { preparedStateCache.clear() },
            ) { transaction, _ -> transaction }
                .flatMapLatest { tx ->
                    if (tx == null) {
                        return@flatMapLatest flowOf(InspectorPreparedState())
                    }
                    val preparation = flow {
                        val cached = preparedStateCache[tx.transactionId]
                        if (cached != null) {
                            emit(cached)
                            return@flow
                        }

                        // A same-item refresh retains its own prepared detail. Replacing it with an
                        // empty loading state would clear the body/overview between every live update.
                        emit(_uiState.value.preparedState.forSelection(tx.transactionId)
                            ?: InspectorPreparedState.loading(tx.transactionId))

                        val pendingExchange = pendingBreakpoints
                            .firstOrNull { event -> event.candidate.exchangeId.value == tx.transactionId }
                            ?.toTrafficExchangeSnapshot()
                            ?: pendingProtocolMessages
                                .firstOrNull { event -> event.candidate.exchangeId.value == tx.transactionId }
                                ?.toTrafficExchangeSnapshot()

                        val prepared = withContext(backgroundDispatcher) {
                            val detailResult = loadTrafficExchangeDetailsUseCase.execute(
                                ExchangeId(tx.transactionId),
                            )
                            val details = (detailResult as? LoadTrafficExchangeDetailsResult.Found)?.details
                            val exchange = details?.exchange ?: pendingExchange
                            if (exchange == null) {
                                return@withContext InspectorPreparedState(
                                    transactionId = tx.transactionId,
                                    loadState = InspectorLoadState.MISSING,
                                )
                            }
                            val requestBody = (details?.requestBody as? TrafficBodyPreview.Available)
                                ?.chunk
                            val responseBody = (details?.responseBody as? TrafficBodyPreview.Available)
                                ?.chunk
                            val requestHeaders = exchange.request.head.headers.map { header ->
                                header.name.value to header.value
                            }
                            val responseHeaders = exchange.response?.head?.headers.orEmpty().map { header ->
                                header.name.value to header.value
                            }

                            // Single pass: decode bytes + resolve BodyFormat off-thread together.
                            val requestBodySpec = PayloadInspectionSpec.fromRequest(
                                method = exchange.request.head.method.token,
                                url = exchange.request.absoluteUrl(),
                                body = requestBody?.copyBytes(),
                                headers = requestHeaders,
                                bodyComplete = requestBody?.endOfBody != false,
                                learnCaptured = true,
                            )
                            val responseBodySpec = PayloadInspectionSpec.fromBytes(
                                responseBody?.copyBytes(),
                                responseHeaders,
                            )

                            InspectorPreparedState(
                                transactionId = tx.transactionId,
                                exchange = exchange,
                                requestPayloadSpec = requestBodySpec,
                                requestParameterSpec = PayloadInspectionSpec.fromGraphQLParameters(
                                    exchange.request.head.method.token, exchange.request.absoluteUrl(), learnCaptured = true),
                                responsePayloadSpec = responseBodySpec,
                                requestBodyTruncated = requestBody?.endOfBody == false,
                                responseBodyTruncated = responseBody?.endOfBody == false,
                                loadState = InspectorLoadState.READY,
                            )
                        }

                        emit(prepared)
                    }
                    combine(
                        preparation,
                        observeInspectionAnnotationsUseCase.execute(ExchangeId(tx.transactionId))
                            .onStart {
                                emit(_uiState.value.preparedState.forSelection(tx.transactionId)?.annotations
                                    ?: preparedStateCache[tx.transactionId]?.annotations.orEmpty())
                            },
                    ) { prepared, annotations ->
                        prepared.copy(annotations = annotations)
                    }
                }
                .collect { prepared ->
                    val owner = prepared.transactionId.takeIf(String::isNotBlank)
                    if (_uiState.value.selectedTransactionId != owner) return@collect
                    // Retain annotations together with terminal payloads, inside the existing LRU
                    // byte budget. Active streams must continue loading current capture progress.
                    if (prepared.loadState == InspectorLoadState.READY && prepared.exchange?.terminalOutcome != null) {
                        preparedStateCache.put(prepared)
                    }
                    _uiState.update { current ->
                        if (current.selectedTransactionId == owner) current.copy(preparedState = prepared) else current
                    }
                }
        }

        // Framed protocol messages are queried only for the selected canonical exchange. Storage
        // emits a compact generation, while every refresh remains a bounded indexed page query.
        viewModelScope.launch {
            _uiState
                .map { state -> state.selectedTransactionId }
                .distinctUntilChanged()
                .flatMapLatest { transactionId ->
                    if (transactionId == null) {
                        flowOf(ProtocolMessagesUiState())
                    } else {
                        val exchangeId = ExchangeId(transactionId)
                        observeProtocolMessageChangesUseCase.execute(exchangeId)
                            .onStart { emit(0L) }
                            .conflate()
                            .mapLatest {
                                val page = withContext(backgroundDispatcher) {
                                    queryProtocolMessagesUseCase.execute(
                                        ProtocolMessagePageQuery(
                                            exchangeId = exchangeId,
                                            limit = PROTOCOL_MESSAGE_PAGE_SIZE,
                                        ),
                                    )
                                }
                                val selected = _uiState.value.protocolMessages.selectedMessageId
                                    ?.takeIf { messageId -> page.items.any { it.id == messageId } }
                                    ?: page.items.firstOrNull()?.id
                                ProtocolMessagesUiState(
                                    exchangeId = transactionId,
                                    items = page.items,
                                    totalCount = page.totalCount,
                                    nextCursor = page.nextCursor,
                                    selectedMessageId = selected,
                                    isBodyLoading = selected != null,
                                )
                            }
                            .catch { error ->
                                if (error is CancellationException) throw error
                                KNetLogger.error("TrafficViewModel", error) {
                                    "Protocol message query failed for exchange=$transactionId"
                                }
                                emit(ProtocolMessagesUiState(exchangeId = transactionId))
                            }
                    }
                }
                .collect { messages ->
                    _uiState.update { current ->
                        if (current.selectedTransactionId != messages.exchangeId.takeIf(String::isNotBlank)) current
                        else current.copy(protocolMessages = messages.retainPreparedSelection(current.protocolMessages))
                    }
                }
        }

        // A payload is loaded only for the selected child message and is capped by the application
        // use case. Scrolling a large stream therefore never hydrates every protobuf message.
        viewModelScope.launch {
            _uiState
                .map { state -> Triple(
                    state.protocolMessages.exchangeId,
                    state.protocolMessages.selectedMessage,
                    state.preparedState.exchange,
                ) }
                .distinctUntilChanged()
                .mapLatest { selection ->
                    val (_, message, parentExchange) = selection
                    val result = message?.let {
                        withContext(backgroundDispatcher) { loadProtocolMessageBodyUseCase.execute(it, parentExchange) }
                    }
                    selection to result
                }
                .collect { (selection, result) ->
                    _uiState.update { current ->
                        val messages = current.protocolMessages
                        if (messages.exchangeId != selection.first || messages.selectedMessage != selection.second ||
                            current.preparedState.exchange != selection.third) return@update current
                        val updated = when (result) {
                            null -> messages.copy(
                                selectedBodyBytes = null,
                                selectedBodyPresentation = null,
                                selectedBodyUnavailable = false,
                                selectedBodyTruncated = false,
                                isBodyLoading = false,
                            )
                            LoadProtocolMessageBodyResult.Empty -> messages.copy(
                                selectedBodyBytes = ByteArray(0),
                                selectedBodyPresentation = null,
                                selectedBodyUnavailable = false,
                                selectedBodyTruncated = false,
                                isBodyLoading = false,
                            )
                            LoadProtocolMessageBodyResult.Unavailable -> messages.copy(
                                selectedBodyBytes = null,
                                selectedBodyPresentation = null,
                                selectedBodyUnavailable = true,
                                selectedBodyTruncated = false,
                                isBodyLoading = false,
                            )
                            is LoadProtocolMessageBodyResult.Available -> messages.copy(
                                selectedBodyBytes = result.bytes,
                                selectedBodyPresentation = result.presentation,
                                selectedBodyUnavailable = false,
                                selectedBodyTruncated = result.truncated,
                                isBodyLoading = false,
                            )
                        }
                        current.copy(protocolMessages = updated)
                    }
                }
        }

        // 1. Observe Proxy Engine State
        viewModelScope.launch {
            proxyRuntimeStates.collect { runtimeState ->
                _uiState.update { current ->
                    val errorMessage = when (runtimeState) {
                        is ProxyRuntimeState.Failed -> runtimeState.code
                        is ProxyRuntimeState.Running,
                        ProxyRuntimeState.Starting -> null
                        else -> current.engineErrorMessage
                    }
                    current.copy(
                        engineState = runtimeState,
                        engineErrorMessage = errorMessage
                    )
                }
            }
        }

        // 2. Observe capture attachment independently from the persistent forwarding listener.
        viewModelScope.launch {
            trafficCaptureStates.collect { captureState ->
                _uiState.update { current ->
                    current.copy(
                        captureState = when (captureState) {
                            CaptureSessionState.Inactive -> CaptureState.STOPPED
                            CaptureSessionState.Starting -> CaptureState.STARTING
                            is CaptureSessionState.Capturing -> CaptureState.CAPTURING
                            CaptureSessionState.Paused -> CaptureState.PAUSED
                            is CaptureSessionState.Failed -> CaptureState.STOPPED
                        },
                        trafficErrorMessage = when (captureState) {
                            is CaptureSessionState.Failed -> "Capture unavailable (${captureState.code})"
                            CaptureSessionState.Starting,
                            is CaptureSessionState.Capturing,
                            CaptureSessionState.Paused -> null
                            CaptureSessionState.Inactive -> current.trafficErrorMessage
                        },
                    )
                }
            }
        }

        // 3. Observe Reactive Host Local IP Address
        viewModelScope.launch {
            observeLocalIpUseCase.execute().collect { ip ->
                _uiState.update { current ->
                    current.copy(localIpAddress = ip)
                }
            }
        }

        // 4. Observe only a compact session ID and store generations; rows are fetched as bounded keyset pages.
        viewModelScope.launch {
            observeLatestTrafficSessionUseCase.execute()
                .distinctUntilChanged()
                .collectLatest { sessionId ->
                    _uiState.update { current ->
                        current.copy(
                            sessionId = sessionId,
                            totalAvailableCount = 0L,
                            pageGeneration = 0L,
                        )
                    }
                    if (sessionId == null) return@collectLatest
                    loadTrafficPage(if (_uiState.value.reading.observed == null) TrafficPageLoadMode.REPLACE else TrafficPageLoadMode.REFRESH_NEWEST)
                    observeTrafficGenerationsUseCase.execute()
                        .filter { generation -> generation.sessionId == sessionId }
                        .conflate()
                        .onEach { delay(75.milliseconds) }
                        .collect { generation ->
                            // A pause can be published before its asynchronous canonical admission commits.
                            // Retry only unresolved owners on durable changes; never guess a source from headers.
                            if ((pendingBreakpoints.map { it.candidate.exchangeId.value } +
                                    pendingProtocolMessages.map { it.candidate.exchangeId.value }).any { pendingSources[it] == null }) {
                                schedulePendingRequestDescriptors()
                            }
                            if (generation.generation > _uiState.value.pageGeneration) {
                                loadTrafficPage(TrafficPageLoadMode.REFRESH_NEWEST)
                            }
                        }
                }
        }
    }

    fun processIntent(intent: TrafficIntent) {
        when (intent) {
            is TrafficIntent.StartCapture -> {
                captureControlIntent.update { current ->
                    CaptureControlIntent(shouldCapture = true, revision = current.revision + 1L)
                }
            }

            is TrafficIntent.PauseCapture -> {
                captureControlIntent.update { current ->
                    CaptureControlIntent(shouldCapture = false, revision = current.revision + 1L)
                }
            }

            is TrafficIntent.ClearFeed -> clearSource(_uiState.value.source)

            is TrafficIntent.ToggleAutoScroll -> {
                val settings = _uiState.value.scrollSettings
                changeScrollSettings(settings.copy(manualEnabled = !settings.manualEnabled))
            }

            is TrafficIntent.SetAutoScrollMode -> changeScrollSettings(_uiState.value.scrollSettings.copy(mode = intent.mode))
            TrafficIntent.JumpToLatest -> jumpToLatest()

            is TrafficIntent.DismissEngineError -> {
                _uiState.update { it.copy(engineErrorMessage = null, trafficErrorMessage = null) }
            }

            is TrafficIntent.LoadNextPage -> {
                viewModelScope.launch { loadTrafficPage(TrafficPageLoadMode.LOAD_OLDER) }
            }

            TrafficIntent.LoadNewerPage -> {
                if (!_uiState.value.isPageLoading && _uiState.value.hasNewerTraffic)
                    viewModelScope.launch { loadTrafficPage(TrafficPageLoadMode.LOAD_NEWER) }
            }

            is TrafficIntent.Search -> {
                _uiState.update { current -> current.copy(searchQuery = intent.query) }
                publishTrafficProjection()
                scheduleFilteredPageRefresh()
            }

            is TrafficIntent.FilterByType -> {
                _uiState.update { current -> current.copy(selectedTypeFilter = intent.type) }
                publishTrafficProjection()
                scheduleFilteredPageRefresh()
            }

            is TrafficIntent.FilterByScheme -> {
                _uiState.update { current -> current.copy(selectedSchemeFilter = intent.scheme) }
                publishTrafficProjection()
                scheduleFilteredPageRefresh()
            }

            is TrafficIntent.FilterByHttpVersion -> {
                _uiState.update { current -> current.copy(selectedHttpVersionFilter = intent.version) }
                publishTrafficProjection()
                scheduleFilteredPageRefresh()
            }

            is TrafficIntent.FilterByMethod -> {
                _uiState.update { current -> current.copy(selectedMethodFilter = intent.method) }
                publishTrafficProjection()
                scheduleFilteredPageRefresh()
            }

            is TrafficIntent.FilterByStatus -> {
                _uiState.update { current -> current.copy(selectedStatusFilter = intent.status) }
                publishTrafficProjection()
                scheduleFilteredPageRefresh()
            }

            is TrafficIntent.SelectTransaction -> {
                _uiState.update { current ->
                    val preparedState = preparedForSelection(current, intent.id)
                    current.copy(
                        selectedTransactionId = intent.id,
                        preparedState = preparedState,
                    )
                }
            }

            is TrafficIntent.SelectInspectorTab -> {
                _uiState.update { it.copy(activeInspectorTab = intent.tab) }
            }

            is TrafficIntent.SelectProtocolMessage -> {
                _uiState.update { current ->
                    if (current.protocolMessages.selectedMessageId == intent.id) return@update current
                    current.copy(
                        protocolMessages = current.protocolMessages.copy(
                            selectedMessageId = intent.id,
                            selectedBodyBytes = null,
                            selectedBodyPresentation = null,
                            selectedBodyUnavailable = false,
                            selectedBodyTruncated = false,
                            isBodyLoading = true,
                        ),
                    )
                }
            }

            TrafficIntent.LoadNextProtocolMessagePage -> {
                viewModelScope.launch { loadNextProtocolMessagePage() }
            }

            is TrafficIntent.SelectRequestSubTab -> {
                _uiState.update { it.copy(activeRequestSubTab = intent.subTab) }
            }

            is TrafficIntent.SelectResponseSubTab -> {
                _uiState.update { it.copy(activeResponseSubTab = intent.subTab) }
            }

            is TrafficIntent.ToggleColumn -> {
                _uiState.update { current ->
                    current.copy(columnVisibility = current.columnVisibility.toggle(intent.column))
                }
            }

            is TrafficIntent.ResizeColumn -> {
                _uiState.update { current ->
                    current.copy(
                        columnWidths = current.columnWidths.withColumnWidth(intent.column, intent.widthDp),
                    )
                }
            }

            TrafficIntent.CommitColumnWidths -> persistColumnWidths(_uiState.value.columnWidths)

            is TrafficIntent.ResetColumnWidth -> {
                val widths = _uiState.value.columnWidths.resetColumn(intent.column)
                _uiState.update { current -> current.copy(columnWidths = widths) }
                persistColumnWidths(widths)
            }

            TrafficIntent.ResetColumnWidths -> {
                val widths = TrafficTableColumnWidths()
                _uiState.update { current -> current.copy(columnWidths = widths) }
                persistColumnWidths(widths)
            }
        }
    }

    private fun persistColumnWidths(widths: TrafficTableColumnWidths) {
        viewModelScope.launch {
            updateWorkspaceLayoutUseCase.execute { settings ->
                settings.copy(trafficTableColumnWidths = widths)
            }
        }
    }

    private suspend fun loadNextProtocolMessagePage() {
        messagePageLoadMutex.withLock {
            val before = _uiState.value.protocolMessages
            val cursor = before.nextCursor ?: return
            val exchangeId = before.exchangeId.takeIf(String::isNotBlank)?.let(::ExchangeId) ?: return
            _uiState.update { current ->
                current.copy(protocolMessages = current.protocolMessages.copy(isLoading = true))
            }
            try {
                val page = withContext(backgroundDispatcher) {
                    queryProtocolMessagesUseCase.execute(
                        ProtocolMessagePageQuery(
                            exchangeId = exchangeId,
                            cursor = cursor,
                            limit = PROTOCOL_MESSAGE_PAGE_SIZE,
                        ),
                    )
                }
                _uiState.update { current ->
                    val messages = current.protocolMessages
                    if (messages.exchangeId != exchangeId.value) return@update current
                    val existingIds = messages.items.asSequence().map { it.id }.toHashSet()
                    current.copy(
                        protocolMessages = messages.copy(
                            items = messages.items + page.items.filterNot { it.id in existingIds },
                            totalCount = page.totalCount,
                            nextCursor = page.nextCursor,
                            isLoading = false,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                KNetLogger.error("TrafficViewModel", error) {
                    "Protocol message page query failed for exchange=${exchangeId.value}"
                }
                _uiState.update { current ->
                    current.copy(protocolMessages = current.protocolMessages.copy(isLoading = false))
                }
            }
        }
    }

    /** Reconciles capture intent while preserving the listener unless its configured port changed. */
    private suspend fun startOrResumeCapture(port: Int) {
        val runtimeState = proxyRuntimeStates.value
        if (runtimeState is ProxyRuntimeState.Running) {
            val activePort = runtimeState.handle.endpoints.endpoints.singleOrNull()?.port
            if (activePort == port) {
                resumeTrafficCaptureUseCase.execute()
                return
            }
            stopProxyRuntimeUseCase.execute(ProxyStopReason.CONFIGURATION_CHANGED)
        }
        startLoopbackProxyUseCase.execute(port)
    }

    private fun scheduleFilteredPageRefresh() {
        sourceGeneration++ // Discard any response for the previous filter before rebasing arrivals.
        _uiState.update { it.copy(reading = TrafficReadingState(jumpRevision = it.reading.jumpRevision + 1), viewport = TrafficViewport(), readingEpoch = it.readingEpoch + 1, newerPageBlocked = false) }
        filterRefreshJob?.cancel()
        filterRefreshJob = viewModelScope.launch {
            delay(150.milliseconds)
            loadTrafficPage(TrafficPageLoadMode.REPLACE)
        }
    }

    /** Retains a bounded process-session marker after an active pause has been resolved. */
    private fun rememberInterception(event: PendingBreakpoint) {
        val exchangeId = event.candidate.exchangeId.value
        val previous = interceptionHistory[exchangeId]
        interceptionHistory[exchangeId] = TrafficInterceptionUiState.Matched(
            ruleIds = previous?.ruleIds.orEmpty() + event.ruleId,
            phases = previous?.phases.orEmpty() + event.candidate.phase,
        )
    }

    private fun rememberInterception(event: PendingProtocolMessageBreakpoint) {
        val exchangeId = event.candidate.exchangeId.value
        val previous = interceptionHistory[exchangeId]
        interceptionHistory[exchangeId] = TrafficInterceptionUiState.Matched(
            ruleIds = previous?.ruleIds.orEmpty() + event.ruleId,
            phases = previous?.phases.orEmpty() + event.candidate.phase,
        )
    }

    /** Select cached detail atomically with the row, never displaying another capture's body while loading. */
    private fun preparedForSelection(state: TrafficState, id: String?): InspectorPreparedState =
        id?.let { state.preparedState.forSelection(it) ?: preparedStateCache[it] ?: InspectorPreparedState.loading(it) }
            ?: InspectorPreparedState()

    /**
     * Joins durable page metadata with the bounded pending-breakpoint projection by ExchangeId.
     *
     * Pending rows win only while suspended, are always visible despite ordinary traffic filters,
     * and never own body bytes. Once the canonical row arrives it replaces the provisional row
     * without changing identity, ordering, or sequence numbering.
     */
    private fun publishTrafficProjection() {
        val source = _uiState.value.source
        val visiblePending = pendingBreakpoints.filter { event -> source == TrafficSource.All || pendingSources[event.candidate.exchangeId.value] == source }
        val visibleMessages = pendingProtocolMessages.filter { event -> source == TrafficSource.All || pendingSources[event.candidate.exchangeId.value] == source }
        val pendingByExchange = visiblePending.associateBy { event -> event.candidate.exchangeId.value }
        val pendingMessagesByExchange = visibleMessages
            .groupBy { event -> event.candidate.exchangeId.value }
            .mapValues { (_, events) -> events.first() }
        val durableByExchange = durableTransactions.associateBy(TrafficRowUiState::transactionId)
        val maximumDurableSequence = durableTransactions.maxOfOrNull(TrafficRowUiState::sequenceNumber) ?: 0L
        val provisionalCandidates = buildList {
            visiblePending.forEach { event ->
                add(Triple(event.candidate.exchangeId.value, event.candidate.startedAtEpochMillis, event.id))
            }
            visibleMessages.forEach { event ->
                add(Triple(event.candidate.exchangeId.value, event.candidate.startedAtEpochMillis, event.id))
            }
        }
            .filterNot { (exchangeId) -> exchangeId in durableByExchange }
            .distinctBy { (exchangeId) -> exchangeId }
            .sortedWith(compareBy<Triple<String, Long, String>> { it.second }.thenBy { it.first })
        val provisionalSequences = provisionalCandidates
            .mapIndexed { index, (exchangeId) ->
                exchangeId to maximumDurableSequence + index + 1L
            }
            .toMap()
        val projectedDurable = durableTransactions.map { row ->
            val historical = interceptionHistory[row.transactionId]
            if (historical == null) row else row.copy(interception = historical)
        }
        val pendingRows = pendingByExchange.map { (exchangeId, event) ->
            val paused = TrafficInterceptionUiState.Paused(
                pendingId = event.id,
                ruleId = event.ruleId,
                phase = event.candidate.phase,
            )
            durableByExchange[exchangeId]?.copy(
                status = 0,
                statusText = "In Progress",
                formattedTime = "-",
                interception = paused,
            )?.let { row ->
                pendingRequestDescriptors[event.id]?.let(row::withDescriptor) ?: row
            } ?: event.toTrafficRowUiState(pendingRequestDescriptors[event.id]).copy(
                sequenceNumber = provisionalSequences.getValue(exchangeId),
            )
        }
        val messagePendingRows = pendingMessagesByExchange
            .filterKeys { exchangeId -> exchangeId !in pendingByExchange }
            .map { (exchangeId, event) ->
                val paused = TrafficInterceptionUiState.Paused(
                    pendingId = event.id,
                    ruleId = event.ruleId,
                    phase = event.candidate.phase,
                )
                durableByExchange[exchangeId]?.copy(
                    status = 0,
                    statusText = "In Progress",
                    formattedTime = "-",
                    interception = paused,
                ) ?: event.toTrafficRowUiState(
                    describeRequestUseCase.execute(event.candidate.request),
                ).copy(sequenceNumber = provisionalSequences.getValue(exchangeId))
            }
        val pendingIds = pendingByExchange.keys + pendingMessagesByExchange.keys
        val sortedRows = (pendingRows + messagePendingRows + projectedDurable.filterNot { row -> row.transactionId in pendingIds })
            .sortedWith(
                compareByDescending<TrafficRowUiState> { row -> row.sequenceNumber }
                    .thenByDescending { row -> row.timestamp }
                    .thenByDescending(TrafficRowUiState::transactionId),
            )
            .take(MAX_TRAFFIC_ROWS)
        val rows = sortedRows
        val current = _uiState.value
        val ordinarilyFiltered = applyFilters(
            transactions = rows,
            query = current.searchQuery,
            type = current.selectedTypeFilter,
            scheme = current.selectedSchemeFilter,
            httpVersion = current.selectedHttpVersionFilter,
            method = current.selectedMethodFilter,
            status = current.selectedStatusFilter,
        )
        val activeRows = rows.filter { row -> row.interception is TrafficInterceptionUiState.Paused }
        val visibleRows = (activeRows + ordinarilyFiltered)
            .distinctBy(TrafficRowUiState::transactionId)
            .sortedWith(
                compareByDescending<TrafficRowUiState> { row -> row.sequenceNumber }
                    .thenByDescending { row -> row.timestamp }
                    .thenByDescending(TrafficRowUiState::transactionId),
            )
        val selectedId = current.selectedTransactionId
            ?.takeIf { id -> visibleRows.any { row -> row.transactionId == id } }
            ?: visibleRows.firstOrNull()?.transactionId
        _uiState.update { state ->
            val preparedState = preparedForSelection(state, selectedId)
            state.copy(
                transactions = rows,
                filteredTransactions = visibleRows,
                selectedTransactionId = selectedId,
                preparedState = preparedState,
            )
        }
    }

    /** Loads one keyset page and never retains more than [MAX_TRAFFIC_ROWS] body-free rows. */
    private suspend fun loadTrafficPage(
        mode: TrafficPageLoadMode,
        expectedSource: TrafficSource? = null,
        allowDuringClear: Boolean = false,
        acknowledge: Boolean = false,
    ) {
        pageLoadMutex.withLock {
            val before = _uiState.value
            // Batch invalidations during clear must not repeatedly count/filter the disappearing
            // history. The explicit post-commit refresh includes new traffic in the replacement writer.
            if (!allowDuringClear && (clearingSource == before.source || clearingSource == TrafficSource.All)) return
            if (expectedSource != null && before.source != expectedSource) return
            val requestedSourceGeneration = sourceGeneration
            val sessionId = before.sessionId ?: return
            val cursor = when (mode) {
                TrafficPageLoadMode.REPLACE,
                TrafficPageLoadMode.REFRESH_NEWEST,
                TrafficPageLoadMode.LOAD_NEWER -> null
                TrafficPageLoadMode.LOAD_OLDER -> if (before.olderPageAnchor != null) null else before.nextPageCursor ?: return
            }
            _uiState.update { it.copy(isPageLoading = true) }
            try {
                val page = queryTrafficPageUseCase.execute(
                    TrafficPageQuery(
                        source = before.source,
                        sessionId = null,
                        cursor = cursor,
                        limit = TRAFFIC_PAGE_SIZE,
                        direction = if (mode == TrafficPageLoadMode.LOAD_NEWER) TrafficSortDirection.OLDEST_FIRST else TrafficSortDirection.NEWEST_FIRST,
                        newerThan = if (mode == TrafficPageLoadMode.LOAD_NEWER) durableTransactions.mapNotNull(TrafficRowUiState::readPoint).maxOrNull() else null,
                        olderThan = if (mode == TrafficPageLoadMode.LOAD_OLDER) before.olderPageAnchor else null,
                        seenThrough = before.reading.seenThrough,
                        searchContains = before.searchQuery.trim().takeIf { it.isNotBlank() },
                        methods = before.selectedMethodFilter.toCanonicalMethods(),
                        statuses = before.selectedStatusFilter.toCanonicalStatuses(),
                        schemes = before.selectedSchemeFilter.toCanonicalSchemes(),
                        protocols = before.selectedHttpVersionFilter.toCanonicalProtocols(),
                        recordFilter = before.selectedTypeFilter.toCanonicalRecordFilter(),
                        arrivalsAfter = before.reading.acknowledged,
                        windowThrough = if (!before.autoScroll && (mode == TrafficPageLoadMode.LOAD_OLDER ||
                            (mode == TrafficPageLoadMode.REFRESH_NEWEST && durableTransactions.isEmpty()))) before.reading.windowThrough else null,
                    ),
                )
                val facetCounts = if (mode == TrafficPageLoadMode.LOAD_OLDER) {
                    null
                } else {
                    queryTrafficFacetsUseCase.execute(
                        TrafficFacetQuery(
                            source = before.source,
                            sessionId = null,
                            searchContains = before.searchQuery.trim().takeIf { it.isNotBlank() },
                            methods = before.selectedMethodFilter.toCanonicalMethods(),
                            statuses = before.selectedStatusFilter.toCanonicalStatuses(),
                            protocols = before.selectedHttpVersionFilter.toCanonicalProtocols(),
                        ),
                    )
                }
                val latestState = _uiState.value
                if (latestState.sessionId != sessionId || latestState.source != before.source || sourceGeneration != requestedSourceGeneration) return
                val httpRows = page.items.map { item ->
                    item.exchange
                        .toTrafficRowUiState(item.historySequence.value).copy(readPoint = item.readPoint)
                        .withDescriptor(describeRequestUseCase.execute(item.exchange.request))
                }
                val opaqueRows = page.opaqueItems.map { item ->
                    item.flow.toTrafficRowUiState(item.historySequence.value).copy(readPoint = item.readPoint)
                }
                val refreshedRows = (httpRows + opaqueRows).sortedWith(
                    compareByDescending<TrafficRowUiState>(TrafficRowUiState::timestamp)
                        .thenByDescending(TrafficRowUiState::sequenceNumber)
                        .thenByDescending(TrafficRowUiState::transactionId),
                )
                // Paused windows never admit new rows, even after more than MAX_TRAFFIC_ROWS arrive.
                // Refresh overlapping lifecycles and keep the reader's stable keys and older-page cursor.
                val preserveWindow = mode == TrafficPageLoadMode.REFRESH_NEWEST && !latestState.autoScroll && !acknowledge
                val trimmedOlderTail = !preserveWindow && mode != TrafficPageLoadMode.LOAD_OLDER && mode != TrafficPageLoadMode.REPLACE &&
                    (refreshedRows.asSequence().map(TrafficRowUiState::transactionId) + durableTransactions.asSequence().map(TrafficRowUiState::transactionId))
                        .distinct().take(MAX_TRAFFIC_ROWS + 1).count() > MAX_TRAFFIC_ROWS
                val sortedRows = if (preserveWindow && durableTransactions.isNotEmpty()) {
                    val refreshedById = refreshedRows.associateBy(TrafficRowUiState::transactionId)
                    durableTransactions.map { refreshedById[it.transactionId] ?: it }
                } else mergePageRows(mode, refreshedRows)
                val filtersStillMatch = latestState.searchQuery == before.searchQuery &&
                    latestState.selectedTypeFilter == before.selectedTypeFilter &&
                    latestState.selectedSchemeFilter == before.selectedSchemeFilter &&
                    latestState.selectedHttpVersionFilter == before.selectedHttpVersionFilter &&
                    latestState.selectedMethodFilter == before.selectedMethodFilter &&
                    latestState.selectedStatusFilter == before.selectedStatusFilter
                if (!filtersStillMatch) return
                durableTransactions = sortedRows
                observeDurableRequestKinds()
                _uiState.update { current ->
                    val arrivals = page.arrivals
                    val acknowledgeAll = acknowledge || current.reading.acknowledged == null
                    val sameProgress = before.reading.revision == current.reading.revision
                    val reading = current.reading.copy(
                        observed = arrivals?.checkpoint ?: current.reading.observed,
                        latest = page.latest,
                        atTop = if (!current.autoScroll && page.latest != null &&
                            page.latest != sortedRows.mapNotNull(TrafficRowUiState::readPoint).maxOrNull()) false else current.reading.atTop,
                        acknowledged = if (acknowledgeAll) arrivals?.checkpoint ?: current.reading.acknowledged else current.reading.acknowledged,
                        seenThrough = if (acknowledgeAll) null else current.reading.seenThrough,
                        newCount = if (acknowledgeAll) 0 else if (sameProgress) arrivals?.count ?: current.reading.newCount else current.reading.newCount,
                        windowThrough = if (acknowledgeAll && current.autoScroll) null else if (mode == TrafficPageLoadMode.LOAD_NEWER || acknowledge)
                            arrivals?.checkpoint else current.reading.windowThrough ?: arrivals?.checkpoint,
                        jumpRevision = current.reading.jumpRevision + if (acknowledge) 1 else 0,
                        revision = current.reading.revision + if (acknowledgeAll) 1 else 0,
                    )
                    current.copy(
                        reading = reading,
                        olderPageAnchor = when {
                            mode == TrafficPageLoadMode.REPLACE || mode == TrafficPageLoadMode.LOAD_OLDER -> null
                            trimmedOlderTail -> sortedRows.mapNotNull(TrafficRowUiState::readPoint).minOrNull()
                            else -> current.olderPageAnchor
                        },
                        newerPageBlocked = if (page.latest != null && page.latest != current.reading.latest) false else current.newerPageBlocked,
                        nextPageCursor = when (mode) {
                            TrafficPageLoadMode.REFRESH_NEWEST -> if (preserveWindow) current.nextPageCursor else current.nextPageCursor ?: page.nextCursor
                            TrafficPageLoadMode.LOAD_NEWER -> current.nextPageCursor
                            TrafficPageLoadMode.REPLACE,
                            TrafficPageLoadMode.LOAD_OLDER -> page.nextCursor
                        },
                        totalAvailableCount = when (mode) {
                            TrafficPageLoadMode.REPLACE,
                            TrafficPageLoadMode.REFRESH_NEWEST,
                            TrafficPageLoadMode.LOAD_NEWER -> page.totalCount
                            TrafficPageLoadMode.LOAD_OLDER -> current.totalAvailableCount
                        },
                        pageGeneration = maxOf(current.pageGeneration, page.generation),
                        facetCounts = facetCounts ?: current.facetCounts,
                    )
                }
                publishTrafficProjection()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (mode == TrafficPageLoadMode.LOAD_NEWER) _uiState.update { it.copy(newerPageBlocked = true) }
                KNetLogger.error("TrafficViewModel", error) {
                    "Traffic page query failed for session=${sessionId.value} mode=$mode"
                }
            } finally {
                _uiState.update { it.copy(isPageLoading = false) }
            }
        }
    }

    /** Merges a page according to its navigation purpose while retaining a bounded rolling window. */
    private fun mergePageRows(
        mode: TrafficPageLoadMode,
        pageRows: List<TrafficRowUiState>,
    ): List<TrafficRowUiState> {
        if (mode == TrafficPageLoadMode.REPLACE) return pageRows.take(MAX_TRAFFIC_ROWS)
        val pageIds = pageRows.asSequence().map(TrafficRowUiState::transactionId).toHashSet()
        val sorted = (pageRows + durableTransactions.filterNot { row -> row.transactionId in pageIds })
            .sortedWith(
                compareByDescending<TrafficRowUiState> { row -> row.timestamp }
                    .thenByDescending { row -> row.sequenceNumber }
                    .thenByDescending(TrafficRowUiState::transactionId),
            )
        return when (mode) {
            TrafficPageLoadMode.REPLACE -> error("Replace pages are handled before merging.")
            TrafficPageLoadMode.REFRESH_NEWEST,
            TrafficPageLoadMode.LOAD_NEWER -> sorted.take(MAX_TRAFFIC_ROWS)
            TrafficPageLoadMode.LOAD_OLDER -> sorted.takeLast(MAX_TRAFFIC_ROWS)
        }
    }

    private fun applyFilters(
        transactions: List<TrafficRowUiState>,
        query: String,
        type: TrafficTypeFilter,
        scheme: SchemeFilter,
        httpVersion: HttpVersionFilter,
        method: MethodFilter,
        status: StatusFilter
    ): List<TrafficRowUiState> {
        return transactions.filter { item ->
            val matchesQuery = query.isBlank() ||
                    item.host.contains(query, ignoreCase = true) ||
                    item.path.contains(query, ignoreCase = true) ||
                    item.method.contains(query, ignoreCase = true) ||
                    item.displayMethod.contains(query, ignoreCase = true) ||
                    item.status.toString().contains(query) ||
                    item.sourceApplicationId?.contains(query, ignoreCase = true) == true ||
                    item.policyRuleId?.contains(query, ignoreCase = true) == true ||
                    item.policyAction?.name?.contains(query, ignoreCase = true) == true ||
                    item.policyGroupId?.contains(query, ignoreCase = true) == true ||
                    item.opaqueTransport?.name?.contains(query, ignoreCase = true) == true ||
                    item.opaqueSecurity?.name?.contains(query, ignoreCase = true) == true

            val matchesScheme = scheme.matches(item)
            val matchesType = type.matches(item)
            val matchesHttpVersion = httpVersion.matches(item)

            val matchesMethod = when (method) {
                MethodFilter.ALL -> true
                else -> item.method.equals(method.name, ignoreCase = true)
            }

            val matchesStatus = when (status) {
                StatusFilter.ALL -> true
                else -> status.range?.contains(item.status) ?: true
            }

            matchesQuery && matchesType && matchesScheme && matchesHttpVersion && matchesMethod && matchesStatus
        }
    }

    /**
     * Resolves semantic identities for pending rows away from Compose and retains no body bytes.
     *
     * The queue is published first with its HTTP fallback. Descriptor completion then decorates the
     * same exchange rows without delaying the interception drawer.
     */
    private fun schedulePendingRequestDescriptors() {
        pendingDescriptorJob?.cancel()
        val events = pendingBreakpoints
        val pendingExchangeIds = (events.map { event -> event.candidate.exchangeId } +
            pendingProtocolMessages.map { event -> event.candidate.exchangeId }).distinct()
        if (pendingExchangeIds.isEmpty()) {
            pendingRequestDescriptors = emptyMap()
            pendingSources = emptyMap()
            return
        }
        val rulesById = _uiState.value.activeBreakpointRules.associateBy { it.id }
        pendingDescriptorJob = viewModelScope.launch {
            val sources = withContext(backgroundDispatcher) { pendingExchangeIds.associate { id -> id.value to resolveTrafficSourceUseCase.execute(id) } }
            val descriptors = withContext(backgroundDispatcher) {
                events.associate { event ->
                    val requestBody = event.candidate.requestBody
                    val retainedBody = requestBody?.let { body ->
                        RequestDescriptorBody(body.copyBytes(RequestDescriptorBody.MAXIMUM_BYTES))
                    }
                    val bodyComplete = when {
                        requestBody == null -> event.candidate.requestObservedBodyBytes == 0L
                        requestBody.size > RequestDescriptorBody.MAXIMUM_BYTES -> false
                        else -> event.candidate.requestObservedBodyBytes == requestBody.size.toLong()
                    }
                    val kindHint = rulesById[event.ruleId]
                        ?.protocolCriteria
                        ?.protocolId
                        ?.value
                        ?.toRequestKindIdOrNull()
                    event.id to describeRequestUseCase.execute(
                        request = event.candidate.request,
                        body = retainedBody,
                        bodyComplete = bodyComplete,
                        semanticKindHint = kindHint,
                    )
                }
            }
            if ((pendingBreakpoints.map { it.candidate.exchangeId } + pendingProtocolMessages.map { it.candidate.exchangeId }).distinct() == pendingExchangeIds) {
                pendingRequestDescriptors = descriptors
                pendingSources = sources
                publishTrafficProjection()
            }
        }
    }

    /**
     * Observes persisted semantic kinds for the bounded retained Traffic window as one Room flow.
     *
     * Semantic inspectors parse bodies once after capture. This projection consumes only their small
     * annotations and therefore keeps row rendering body-free even for large traffic histories.
     */
    private fun observeDurableRequestKinds() {
        durableAnnotationJob?.cancel()
        val exchangeIds = durableTransactions.asSequence()
            .filter { row -> row.rowKind == TrafficRowKind.HTTP_EXCHANGE }
            .take(MAXIMUM_ANNOTATION_OBSERVATION_ROWS)
            .map { ExchangeId(it.transactionId) }
            .toSet()
        if (exchangeIds.isEmpty()) return

        durableAnnotationJob = viewModelScope.launch {
            observeInspectionAnnotationsUseCase.execute(exchangeIds).collect { annotationsByExchange ->
                durableTransactions = durableTransactions.map { row ->
                    val kindHint = annotationsByExchange[ExchangeId(row.transactionId)]
                        .orEmpty()
                        .asSequence()
                        .mapNotNull { it.document?.kind?.toRequestKindIdOrNull() }
                        .firstOrNull { it != RequestKindId.HTTP }
                        ?: return@map row
                    row.withDescriptor(
                        describeRequestUseCase.execute(
                            RequestDescriptorInput(
                                transportMethod = CanonicalHttpMethod.fromToken(row.method),
                                absoluteUrl = row.fullUrl,
                                semanticKindHint = kindHint,
                            ),
                        ),
                    )
                }
                publishTrafficProjection()
            }
        }
    }

    /**
     * Prepares a protocol-aware breakpoint rule draft from a captured transaction.
     *
     * Semantic detection runs off the presentation thread through the application use case. The
     * drawer opens only after one complete immutable draft is available, avoiding an HTTP-to-protocol
     * visual transition while body inspection finishes.
     *
     * @param transactionId Target transaction UUID.
     */
    fun createBreakpointFromTransaction(transactionId: String) {
        if (uiState.value.transactions.none { it.transactionId == transactionId }) return
        val pendingCandidate = pendingBreakpoints.firstOrNull {
            it.candidate.exchangeId.value == transactionId
        }?.candidate
        breakpointDraftJob?.cancel()
        breakpointDraftJob = viewModelScope.launch {
            val result = withContext(backgroundDispatcher) {
                prepareBreakpointRuleDraftUseCase.execute(
                    exchangeId = ExchangeId(transactionId),
                    pendingCandidate = pendingCandidate,
                )
            }
            val draft = (result as? PrepareBreakpointRuleDraftResult.Found)?.draft ?: return@launch
            _uiState.update { state ->
                state.copy(
                    isBreakpointDrawerVisible = true,
                    prefilledBreakpointRule = draft.rule,
                    prefilledBreakpointProtocolValues = draft.protocolValues,
                )
            }
        }
    }

    /**
     * Closes the traffic workspace breakpoint rule drawer.
     */
    fun closeBreakpointDrawer() {
        _uiState.update {
            it.copy(
                isBreakpointDrawerVisible = false,
                prefilledBreakpointRule = null,
                prefilledBreakpointProtocolValues = emptyList(),
            )
        }
    }

    private fun createInitialState(): TrafficState {
        return TrafficState(source = initialSource,
            transactions = emptyList(),
            filteredTransactions = emptyList(),
            selectedTransactionId = null,
            captureState = CaptureState.STOPPED,
            engineState = ProxyRuntimeState.Stopped,
            searchQuery = "",
            selectedTypeFilter = TrafficTypeFilter.ALL,
            selectedSchemeFilter = SchemeFilter.ALL,
            selectedHttpVersionFilter = HttpVersionFilter.ALL,
            selectedMethodFilter = MethodFilter.ALL,
            selectedStatusFilter = StatusFilter.ALL,
            activeInspectorTab = InspectorTab.OVERVIEW,
        )
    }

    private companion object {
        private const val TRAFFIC_PAGE_SIZE = 200
        private const val PROTOCOL_MESSAGE_PAGE_SIZE = 100
        private const val MAX_TRAFFIC_ROWS = 1_000
        private const val MAX_PREPARED_INSPECTOR_ENTRIES = 8
        private const val MAX_PREPARED_INSPECTOR_BYTES = 16L * 1_024L * 1_024L
        private const val MAXIMUM_ANNOTATION_OBSERVATION_ROWS = 900
    }

    /** Purpose of a persisted page query and its bounded-window merge policy. */
    private enum class TrafficPageLoadMode {
        LOAD_NEWER,
        REPLACE,
        REFRESH_NEWEST,
        LOAD_OLDER,
    }

    /** Conflatable desired capture command derived from settings and toolbar state. */
    private data class CaptureCommand(
        val shouldCapture: Boolean,
        val port: Int,
        val revision: Long,
    )

    /** User capture intent with a revision so retries remain observable without transient states. */
    private data class CaptureControlIntent(
        val shouldCapture: Boolean,
        val revision: Long,
    )
}

private fun String.toRequestKindIdOrNull(): RequestKindId? =
    runCatching { RequestKindId(trim().lowercase()) }.getOrNull()

private fun MethodFilter.toCanonicalMethods(): Set<CanonicalHttpMethod> = when (this) {
    MethodFilter.ALL -> emptySet()
    else -> setOf(CanonicalHttpMethod.fromToken(name))
}

private fun StatusFilter.toCanonicalStatuses(): Set<CanonicalHttpStatus> =
    range?.map(::CanonicalHttpStatus)?.toSet().orEmpty()

private fun TrafficTypeFilter.toCanonicalRecordFilter(): TrafficRecordFilter = when (this) {
    TrafficTypeFilter.ALL -> TrafficRecordFilter.ALL
    TrafficTypeFilter.DECRYPTED -> TrafficRecordFilter.DECRYPTED
    TrafficTypeFilter.TLS_TUNNEL -> TrafficRecordFilter.TLS_TUNNEL
    TrafficTypeFilter.OPAQUE_TCP -> TrafficRecordFilter.OPAQUE_TCP
    TrafficTypeFilter.UDP_QUIC -> TrafficRecordFilter.UDP_OR_QUIC
    TrafficTypeFilter.PROTECTED -> TrafficRecordFilter.PROTECTED
    TrafficTypeFilter.FAILED -> TrafficRecordFilter.FAILED
}

private fun SchemeFilter.toCanonicalSchemes(): Set<HttpScheme> = when (this) {
    SchemeFilter.HTTP -> setOf(HttpScheme.Standard(StandardHttpScheme.HTTP))
    SchemeFilter.HTTPS -> setOf(HttpScheme.Standard(StandardHttpScheme.HTTPS))
    SchemeFilter.ALL -> emptySet()
}

private fun HttpVersionFilter.toCanonicalProtocols(): Set<ApplicationProtocol> = when (this) {
    HttpVersionFilter.HTTP_1_0 -> setOf(ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_0))
    HttpVersionFilter.HTTP_1_1 -> setOf(ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_1))
    HttpVersionFilter.HTTP_2 -> setOf(ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_2))
    HttpVersionFilter.HTTP_3 -> setOf(ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_3))
    HttpVersionFilter.ALL -> emptySet()
}

private fun SchemeFilter.matches(row: TrafficRowUiState): Boolean = when (this) {
    SchemeFilter.ALL -> true
    SchemeFilter.HTTP -> row.scheme == HttpScheme.Standard(StandardHttpScheme.HTTP)
    SchemeFilter.HTTPS -> row.scheme == HttpScheme.Standard(StandardHttpScheme.HTTPS)
}

private fun HttpVersionFilter.matches(row: TrafficRowUiState): Boolean = when (this) {
    HttpVersionFilter.ALL -> true
    HttpVersionFilter.HTTP_1_0 -> row.clientProtocol == ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_0) ||
        row.upstreamProtocol == ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_0)
    HttpVersionFilter.HTTP_1_1 -> row.clientProtocol == ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_1) ||
        row.upstreamProtocol == ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_1)
    HttpVersionFilter.HTTP_2 -> row.clientProtocol == ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_2) ||
        row.upstreamProtocol == ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_2)
    HttpVersionFilter.HTTP_3 -> row.clientProtocol == ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_3) ||
        row.upstreamProtocol == ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_3)
}
