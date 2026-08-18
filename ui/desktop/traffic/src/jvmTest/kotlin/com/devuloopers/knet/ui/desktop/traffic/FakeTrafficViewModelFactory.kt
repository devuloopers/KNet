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
import com.devuloopers.knet.application.port.traffic.CaptureSessionControlPort
import com.devuloopers.knet.application.port.traffic.TrafficMaintenancePort
import com.devuloopers.knet.application.port.inspection.InspectionAnnotationPort
import com.devuloopers.knet.application.port.inspection.ObserveInspectionAnnotationsUseCase
import com.devuloopers.knet.application.usecase.traffic.ClearTrafficHistoryUseCase
import com.devuloopers.knet.application.usecase.traffic.LoadTrafficExchangeDetailsUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveLatestTrafficSessionUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficGenerationsUseCase
import com.devuloopers.knet.application.usecase.traffic.PrepareTrafficRequestUseCase
import com.devuloopers.knet.application.usecase.traffic.QueryTrafficPageUseCase
import com.devuloopers.knet.application.usecase.proxy.ObserveProxyRuntimeStateUseCase
import com.devuloopers.knet.application.usecase.proxy.StartLoopbackProxyUseCase
import com.devuloopers.knet.application.usecase.proxy.StopProxyRuntimeUseCase
import com.devuloopers.knet.domain.network.repository.NetworkRepository
import com.devuloopers.knet.domain.network.usecase.ObserveLocalIpUseCase
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import com.devuloopers.knet.domain.rules.usecase.ObserveRulesUseCase
import com.devuloopers.knet.domain.rules.usecase.SaveRuleUseCase
import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.inspection.InspectionAnnotation

object FakeTrafficViewModelFactory {

    fun create(
        localIp: String = "127.0.0.1",
        customObserveLocalIpUseCase: ObserveLocalIpUseCase? = null,
        customTrafficQueryPort: TrafficQueryPort? = null,
        customSessionCatalogPort: TrafficSessionCatalogPort? = null,
    ): TrafficViewModel {
        val fakeProxyRuntime = object : ProxyRuntimePort {
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
                generation = 0L,
            )

            override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? = null

            override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk {
                error("Fake traffic query port has no bodies.")
            }
        }

        val fakeNetworkRepo = object : NetworkRepository {
            override fun observeLocalIp(pollIntervalMs: Long): Flow<String> = flowOf(localIp)
            override suspend fun getLocalIp(): String = localIp
        }

        val fakeWidgetRepo = object : WidgetPreferencesRepository {
            override val settingsFlow: Flow<WorkspaceLayoutSettings> = flowOf(WorkspaceLayoutSettings())
            override suspend fun saveSettings(settings: WorkspaceLayoutSettings) {}
        }

        val fakeRulesRepo = object : RulesRepository {
            override val rulesFlow: Flow<List<BreakpointRule>> = flowOf(emptyList())
            override val isGlobalInterceptionEnabled: Flow<Boolean> = flowOf(true)
            override suspend fun saveRule(rule: BreakpointRule) {}
            override suspend fun deleteRule(ruleId: String) {}
            override suspend fun toggleRule(ruleId: String, enabled: Boolean) {}
            override suspend fun toggleGlobalInterception(enabled: Boolean) {}
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
            clearTrafficHistoryUseCase = ClearTrafficHistoryUseCase(
                captureSessionControl = object : CaptureSessionControlPort {
                    override suspend fun rotateForTrafficClear(): CaptureClearPreparation =
                        CaptureClearPreparation.CANONICAL_SESSION_INACTIVE
                },
                trafficMaintenance = object : TrafficMaintenancePort {
                    override suspend fun clearTerminalTraffic() = Unit
                },
            ),
            startLoopbackProxyUseCase = StartLoopbackProxyUseCase(fakeProxyRuntime),
            stopProxyRuntimeUseCase = StopProxyRuntimeUseCase(fakeProxyRuntime),
            observeProxyRuntimeStateUseCase = ObserveProxyRuntimeStateUseCase(fakeProxyRuntime),
            loadTrafficExchangeDetailsUseCase = LoadTrafficExchangeDetailsUseCase(fakeTrafficQueryPort),
            observeLocalIpUseCase = customObserveLocalIpUseCase ?: ObserveLocalIpUseCase(fakeNetworkRepo),
            getWorkspaceLayoutUseCase = GetWorkspaceLayoutUseCase(fakeWidgetRepo),
            prepareTrafficRequestUseCase = PrepareTrafficRequestUseCase(fakeTrafficQueryPort),
            observeInspectionAnnotationsUseCase = ObserveInspectionAnnotationsUseCase(
                object : InspectionAnnotationPort {
                    override suspend fun put(sessionId: CaptureSessionId, annotation: InspectionAnnotation) = Unit
                    override suspend fun get(exchangeId: ExchangeId): List<InspectionAnnotation> = emptyList()
                    override fun observe(exchangeId: ExchangeId): Flow<List<InspectionAnnotation>> = flowOf(emptyList())
                },
            ),
            observeRulesUseCase = ObserveRulesUseCase(fakeRulesRepo),
            saveRuleUseCase = SaveRuleUseCase(fakeRulesRepo)
        )
    }
}
