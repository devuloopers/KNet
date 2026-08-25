package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.application.port.proxy.ProxyRuntimeConfiguration
import com.devuloopers.knet.application.port.proxy.ProxyRuntimePort
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.port.proxy.ProxyStartResult
import com.devuloopers.knet.application.port.proxy.ProxyStopReason
import com.devuloopers.knet.application.port.proxy.ProxyStopResult
import com.devuloopers.knet.application.port.traffic.BodyChunk
import com.devuloopers.knet.application.port.traffic.BodyRange
import com.devuloopers.knet.application.port.traffic.TrafficGeneration
import com.devuloopers.knet.application.port.traffic.TrafficPage
import com.devuloopers.knet.application.port.traffic.TrafficPageQuery
import com.devuloopers.knet.application.port.traffic.TrafficQueryPort
import com.devuloopers.knet.application.port.traffic.TrafficSessionCatalogPort
import com.devuloopers.knet.application.port.traffic.CaptureClearPreparation
import com.devuloopers.knet.application.port.traffic.CapturePauseResult
import com.devuloopers.knet.application.port.traffic.CaptureResumeResult
import com.devuloopers.knet.application.port.traffic.CaptureSessionControlPort
import com.devuloopers.knet.application.port.traffic.CaptureSessionState
import com.devuloopers.knet.application.port.traffic.TrafficMaintenancePort
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePage
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePageQuery
import com.devuloopers.knet.application.port.traffic.ProtocolMessageQueryPort
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePresentationRegistry
import com.devuloopers.knet.application.port.inspection.InspectionAnnotationPort
import com.devuloopers.knet.application.port.inspection.ObserveInspectionAnnotationsUseCase
import com.devuloopers.knet.application.port.breakpoint.BreakpointControlPort
import com.devuloopers.knet.application.port.breakpoint.BreakpointDecision
import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolRegistry
import com.devuloopers.knet.application.port.breakpoint.PendingBreakpoint
import com.devuloopers.knet.application.port.breakpoint.PendingProtocolMessageBreakpoint
import com.devuloopers.knet.application.port.breakpoint.ProtocolMessageBreakpointControlPort
import com.devuloopers.knet.application.port.breakpoint.ProtocolMessageBreakpointDecision
import com.devuloopers.knet.application.usecase.breakpoint.ObservePendingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ObservePendingProtocolMessageBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.PrepareBreakpointRuleDraftUseCase
import com.devuloopers.knet.application.usecase.traffic.ClearTrafficHistoryUseCase
import com.devuloopers.knet.application.usecase.traffic.LoadTrafficExchangeDetailsUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveLatestTrafficSessionUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficGenerationsUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveProtocolMessageChangesUseCase
import com.devuloopers.knet.application.usecase.traffic.QueryProtocolMessagesUseCase
import com.devuloopers.knet.application.usecase.traffic.LoadProtocolMessageBodyUseCase
import com.devuloopers.knet.application.usecase.traffic.PrepareTrafficRequestUseCase
import com.devuloopers.knet.application.usecase.traffic.PrepareCapturedNetworkRequestUseCase
import com.devuloopers.knet.application.usecase.traffic.QueryTrafficPageUseCase
import com.devuloopers.knet.application.usecase.traffic.PauseTrafficCaptureUseCase
import com.devuloopers.knet.application.usecase.traffic.ResumeTrafficCaptureUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficCaptureStateUseCase
import com.devuloopers.knet.application.usecase.proxy.ObserveProxyRuntimeStateUseCase
import com.devuloopers.knet.application.usecase.proxy.StartLoopbackProxyUseCase
import com.devuloopers.knet.application.usecase.proxy.StopProxyRuntimeUseCase
import com.devuloopers.knet.domain.network.repository.NetworkRepository
import com.devuloopers.knet.domain.network.usecase.ObserveLocalIpUseCase
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import com.devuloopers.knet.domain.rules.usecase.ObserveRulesUseCase
import com.devuloopers.knet.domain.settings.model.ApplicationSettings
import com.devuloopers.knet.domain.settings.repository.ApplicationSettingsRepository
import com.devuloopers.knet.domain.settings.usecase.ObserveApplicationSettingsUseCase
import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.domain.workspace.usecase.UpdateWorkspaceLayoutUseCase
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.inspection.InspectionAnnotation
import com.devuloopers.knet.domain.request.descriptor.HttpRequestDescriptorStrategy
import com.devuloopers.knet.domain.request.usecase.DescribeRequestUseCase
import com.devuloopers.knet.engine.formatter.descriptor.GraphQlRequestDescriptorStrategy

object FakeTrafficViewModelFactory {

    fun create(
        localIp: String = "127.0.0.1",
        customObserveLocalIpUseCase: ObserveLocalIpUseCase? = null,
        customTrafficQueryPort: TrafficQueryPort? = null,
        customSessionCatalogPort: TrafficSessionCatalogPort? = null,
        customProxyRuntime: ProxyRuntimePort? = null,
        customCaptureSessionControl: CaptureSessionControlPort? = null,
        customInspectionAnnotationPort: InspectionAnnotationPort? = null,
        customWorkspacePreferencesRepository: WidgetPreferencesRepository? = null,
        pendingBreakpointFlow: StateFlow<List<PendingBreakpoint>> = MutableStateFlow(emptyList()),
    ): TrafficViewModel {
        val fakeProxyRuntime = customProxyRuntime ?: object : ProxyRuntimePort {
            private val mutableState = MutableStateFlow<ProxyRuntimeState>(ProxyRuntimeState.Stopped)
            override val state: StateFlow<ProxyRuntimeState> = mutableState

            override suspend fun start(configuration: ProxyRuntimeConfiguration): ProxyStartResult {
                return ProxyStartResult.Failed("fake-runtime-not-started")
            }

            override suspend fun stop(reason: ProxyStopReason): ProxyStopResult {
                mutableState.value = ProxyRuntimeState.Stopped
                return ProxyStopResult.Stopped
            }
        }

        val fakeTrafficQueryPort = customTrafficQueryPort ?: object : TrafficQueryPort {
            override val generations: Flow<TrafficGeneration> = flowOf()

            override suspend fun query(query: TrafficPageQuery): TrafficPage = TrafficPage(
                items = emptyList(),
                nextCursor = null,
                totalCount = 0L,
                generation = 0L,
            )

            override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? = null

            override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk {
                error("Fake traffic query port has no bodies.")
            }
        }
        val fakeCaptureState = MutableStateFlow<CaptureSessionState>(CaptureSessionState.Inactive)
        val fakeCaptureControl = customCaptureSessionControl ?: object : CaptureSessionControlPort {
            override val captureState: StateFlow<CaptureSessionState> = fakeCaptureState

            override suspend fun pause(): CapturePauseResult {
                fakeCaptureState.value = CaptureSessionState.Inactive
                return CapturePauseResult.PROXY_INACTIVE
            }

            override suspend fun resume(): CaptureResumeResult = CaptureResumeResult.ProxyInactive

            override suspend fun rotateForTrafficClear(): CaptureClearPreparation =
                CaptureClearPreparation.CANONICAL_SESSION_INACTIVE
        }

        val fakeNetworkRepo = object : NetworkRepository {
            override fun observeLocalIp(pollIntervalMs: Long): Flow<String> = flowOf(localIp)
            override suspend fun getLocalIp(): String = localIp
        }

        val fakeApplicationSettings = object : ApplicationSettingsRepository {
            override val settings: Flow<ApplicationSettings> = flowOf(ApplicationSettings())
            override suspend fun update(transform: (ApplicationSettings) -> ApplicationSettings) = Unit
        }

        val fakeRulesRepo = object : RulesRepository {
            override val rulesFlow: Flow<List<BreakpointRule>> = flowOf(emptyList())
            override val isGlobalInterceptionEnabled: Flow<Boolean> = flowOf(true)
            override suspend fun saveRule(rule: BreakpointRule) {}
            override suspend fun deleteRule(ruleId: String) {}
            override suspend fun toggleRule(ruleId: String, enabled: Boolean) {}
            override suspend fun toggleGlobalInterception(enabled: Boolean) {}
        }
        val workspaceSettings = MutableStateFlow(WorkspaceLayoutSettings())
        val workspacePreferencesRepository = customWorkspacePreferencesRepository ?: object :
            WidgetPreferencesRepository {
            override val settingsFlow: Flow<WorkspaceLayoutSettings> = workspaceSettings

            override suspend fun updateSettings(
                transform: (WorkspaceLayoutSettings) -> WorkspaceLayoutSettings,
            ) {
                workspaceSettings.value = transform(workspaceSettings.value)
            }
        }
        val loadTrafficExchangeDetailsUseCase = LoadTrafficExchangeDetailsUseCase(fakeTrafficQueryPort)
        val protocolMessages = object : ProtocolMessageQueryPort {
            override fun observeChanges(exchangeId: ExchangeId): Flow<Long> = flowOf(0L)

            override suspend fun queryMessages(query: ProtocolMessagePageQuery): ProtocolMessagePage =
                ProtocolMessagePage(items = emptyList(), nextCursor = null, totalCount = 0L)

            override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk =
                fakeTrafficQueryPort.readBody(bodyId, range)
        }

        return TrafficViewModel(
            observeLatestTrafficSessionUseCase = ObserveLatestTrafficSessionUseCase(
                customSessionCatalogPort ?: object : TrafficSessionCatalogPort {
                    override val latestSessionId: Flow<CaptureSessionId?> =
                        flowOf(CaptureSessionId("fake-session"))
                },
            ),
            queryTrafficPageUseCase = QueryTrafficPageUseCase(fakeTrafficQueryPort),
            observeTrafficGenerationsUseCase = ObserveTrafficGenerationsUseCase(fakeTrafficQueryPort),
            observeProtocolMessageChangesUseCase = ObserveProtocolMessageChangesUseCase(protocolMessages),
            queryProtocolMessagesUseCase = QueryProtocolMessagesUseCase(protocolMessages),
            loadProtocolMessageBodyUseCase = LoadProtocolMessageBodyUseCase(
                protocolMessages,
                ProtocolMessagePresentationRegistry(),
            ),
            clearTrafficHistoryUseCase = ClearTrafficHistoryUseCase(
                captureSessionControl = fakeCaptureControl,
                trafficMaintenance = object : TrafficMaintenancePort {
                    override suspend fun clearTerminalTraffic() = Unit
                },
            ),
            startLoopbackProxyUseCase = StartLoopbackProxyUseCase(fakeProxyRuntime),
            stopProxyRuntimeUseCase = StopProxyRuntimeUseCase(fakeProxyRuntime),
            observeProxyRuntimeStateUseCase = ObserveProxyRuntimeStateUseCase(fakeProxyRuntime),
            pauseTrafficCaptureUseCase = PauseTrafficCaptureUseCase(fakeCaptureControl),
            resumeTrafficCaptureUseCase = ResumeTrafficCaptureUseCase(fakeCaptureControl),
            observeTrafficCaptureStateUseCase = ObserveTrafficCaptureStateUseCase(fakeCaptureControl),
            loadTrafficExchangeDetailsUseCase = loadTrafficExchangeDetailsUseCase,
            observeLocalIpUseCase = customObserveLocalIpUseCase ?: ObserveLocalIpUseCase(fakeNetworkRepo),
            observeApplicationSettingsUseCase = ObserveApplicationSettingsUseCase(fakeApplicationSettings),
            prepareCapturedNetworkRequestUseCase = PrepareCapturedNetworkRequestUseCase(
                PrepareTrafficRequestUseCase(fakeTrafficQueryPort),
            ),
            observeInspectionAnnotationsUseCase = ObserveInspectionAnnotationsUseCase(
                customInspectionAnnotationPort ?: object : InspectionAnnotationPort {
                    override suspend fun put(sessionId: CaptureSessionId, annotation: InspectionAnnotation) = Unit
                    override suspend fun get(exchangeId: ExchangeId): List<InspectionAnnotation> = emptyList()
                    override fun observe(exchangeId: ExchangeId): Flow<List<InspectionAnnotation>> = flowOf(emptyList())
                    override fun observe(
                        exchangeIds: Set<ExchangeId>,
                    ): Flow<Map<ExchangeId, List<InspectionAnnotation>>> = flowOf(emptyMap())
                },
            ),
            describeRequestUseCase = DescribeRequestUseCase(
                listOf(GraphQlRequestDescriptorStrategy(), HttpRequestDescriptorStrategy()),
            ),
            prepareBreakpointRuleDraftUseCase = PrepareBreakpointRuleDraftUseCase(
                loadTrafficExchangeDetailsUseCase = loadTrafficExchangeDetailsUseCase,
                protocolRegistry = BreakpointProtocolRegistry(),
            ),
            observeRulesUseCase = ObserveRulesUseCase(fakeRulesRepo),
            observePendingBreakpointsUseCase = ObservePendingBreakpointsUseCase(
                object : BreakpointControlPort {
                    override val pendingBreakpoints: StateFlow<List<PendingBreakpoint>> = pendingBreakpointFlow
                    override val isEnabled: StateFlow<Boolean> = MutableStateFlow(true)
                    override fun replaceRules(rules: List<BreakpointRule>) = Unit
                    override suspend fun setEnabled(enabled: Boolean) = Unit
                    override fun setDecisionTimeoutMillis(timeoutMillis: Long) = Unit
                    override suspend fun resolve(pendingId: String, decision: BreakpointDecision): Boolean = false
                    override suspend fun dropMatching(url: String, method: String): Int = 0
                    override suspend fun clear(): Int = 0
                },
            ),
            observePendingProtocolMessageBreakpointsUseCase =
                ObservePendingProtocolMessageBreakpointsUseCase(
                    object : ProtocolMessageBreakpointControlPort {
                        override val pendingProtocolMessages: StateFlow<List<PendingProtocolMessageBreakpoint>> =
                            MutableStateFlow(emptyList())

                        override suspend fun resolveProtocolMessage(
                            pendingId: String,
                            decision: ProtocolMessageBreakpointDecision,
                        ): Boolean = false
                    },
                ),
            getWorkspaceLayoutUseCase = GetWorkspaceLayoutUseCase(workspacePreferencesRepository),
            updateWorkspaceLayoutUseCase = UpdateWorkspaceLayoutUseCase(workspacePreferencesRepository),
            backgroundDispatcher = Dispatchers.Main,
        )
    }
}
