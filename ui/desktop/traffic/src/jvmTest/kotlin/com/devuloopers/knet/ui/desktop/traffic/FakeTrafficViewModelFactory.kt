package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction
import com.devuloopers.knet.domain.network.repository.NetworkRepository
import com.devuloopers.knet.domain.network.usecase.ObserveLocalIpUseCase
import com.devuloopers.knet.domain.proxy.model.ProxyEngineState
import com.devuloopers.knet.domain.proxy.repository.ProxyEngineRepository
import com.devuloopers.knet.domain.proxy.usecase.ObserveProxyEngineStateUseCase
import com.devuloopers.knet.domain.proxy.usecase.StartProxyEngineUseCase
import com.devuloopers.knet.domain.proxy.usecase.StopProxyEngineUseCase
import com.devuloopers.knet.domain.rules.model.RuleModel
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import com.devuloopers.knet.domain.rules.usecase.ObserveRulesUseCase
import com.devuloopers.knet.domain.rules.usecase.SaveRuleUseCase
import com.devuloopers.knet.domain.traffic.model.TransactionBody
import com.devuloopers.knet.domain.traffic.repository.LiveTrafficRepository
import com.devuloopers.knet.domain.traffic.usecase.ClearLiveTrafficUseCase
import com.devuloopers.knet.domain.traffic.usecase.ExportTrafficToSpecUseCase
import com.devuloopers.knet.domain.traffic.usecase.GetLiveTrafficUseCase
import com.devuloopers.knet.domain.traffic.usecase.LoadTransactionBodyUseCase
import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

object FakeTrafficViewModelFactory {

    fun create(
        localIp: String = "127.0.0.1",
        customObserveLocalIpUseCase: ObserveLocalIpUseCase? = null
    ): TrafficViewModel {
        val fakeTrafficRepo = object : LiveTrafficRepository {
            override val transactionsFlow: Flow<List<HttpTransaction>> = flowOf(emptyList())
            override suspend fun getTransactionById(transactionId: String): HttpTransaction? = null
            override suspend fun loadTransactionBody(transactionId: String): TransactionBody = TransactionBody.Empty
            override suspend fun recordTransaction(transaction: HttpTransaction) {}
            override fun clearSession() {}
        }

        val fakeProxyRepo = object : ProxyEngineRepository {
            override fun engineState(): Flow<ProxyEngineState> = flowOf(ProxyEngineState.Stopped)
            override suspend fun start(port: Int) {}
            override suspend fun stop() {}
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
            override val rulesFlow: Flow<List<RuleModel>> = flowOf(emptyList())
            override val isGlobalInterceptionEnabled: Flow<Boolean> = flowOf(true)
            override suspend fun saveRule(rule: RuleModel) {}
            override suspend fun deleteRule(ruleId: String) {}
            override suspend fun toggleRule(ruleId: String, enabled: Boolean) {}
            override suspend fun toggleGlobalInterception(enabled: Boolean) {}
        }

        return TrafficViewModel(
            getLiveTrafficUseCase = GetLiveTrafficUseCase(fakeTrafficRepo),
            clearLiveTrafficUseCase = ClearLiveTrafficUseCase(fakeTrafficRepo),
            startProxyEngineUseCase = StartProxyEngineUseCase(fakeProxyRepo),
            stopProxyEngineUseCase = StopProxyEngineUseCase(fakeProxyRepo),
            observeProxyEngineStateUseCase = ObserveProxyEngineStateUseCase(fakeProxyRepo),
            loadTransactionBodyUseCase = LoadTransactionBodyUseCase(fakeTrafficRepo),
            observeLocalIpUseCase = customObserveLocalIpUseCase ?: ObserveLocalIpUseCase(fakeNetworkRepo),
            getWorkspaceLayoutUseCase = GetWorkspaceLayoutUseCase(fakeWidgetRepo),
            exportTrafficToSpecUseCase = ExportTrafficToSpecUseCase(fakeTrafficRepo),
            observeRulesUseCase = ObserveRulesUseCase(fakeRulesRepo),
            saveRuleUseCase = SaveRuleUseCase(fakeRulesRepo)
        )
    }
}
