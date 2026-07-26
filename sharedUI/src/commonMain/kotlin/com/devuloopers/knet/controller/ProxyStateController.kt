package com.devuloopers.knet.controller

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.devuloopers.knet.domain.inspector.model.TransactionUiModel
import com.devuloopers.knet.data.repository.KNetCoreRepository
import com.devuloopers.knet.ui.livetraffic.viewmodel.LiveTrafficViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Controller managing the UI representation of the proxy engine's operational states.
 * Collects live captured transaction streams from KNetCoreRepository and updates Compose states.
 *
 * @property repository The underlying core backend data repository.
 * @property scope The coroutine scope used to collect the transaction flows.
 */
class ProxyStateController(
    private val repository: KNetCoreRepository,
    private val scope: CoroutineScope
) : KoinComponent {
    /**
     * Observable Compose state list containing all captured HTTP transactions in the session.
     */
    val transactions = mutableStateListOf<TransactionUiModel>()

    /**
     * Observable state representing whether the local proxy server is running.
     */
    val isProxyRunning = mutableStateOf(repository.isProxyRunning())

    /**
     * Active client connections count. Defaulted to 1 to match desktop mock indicators.
     */
    val activeClientCount = mutableStateOf(1)

    /**
     * Local proxy server port.
     */
    val proxyPort = 8888

    val liveTrafficViewModel: LiveTrafficViewModel by inject()
    val inspectorViewModel: com.devuloopers.knet.ui.inspector.viewmodel.InspectorViewModel by inject()
    val rulesViewModel: com.devuloopers.knet.ui.rules.viewmodel.RulesViewModel by inject()

    init {
        scope.launch(Dispatchers.Main) {
            repository.transactionsFlow.collectLatest { list ->
                transactions.clear()
                val uiList = list.mapIndexed { index, httpTransaction ->
                    val sequentialId = list.size - index
                    httpTransaction.toUiModel(sequentialId)
                }
                transactions.addAll(uiList)
            }
        }
    }

    /**
     * Starts or stops the Netty proxy server.
     */
    fun toggleProxy() {
        if (isProxyRunning.value) {
            repository.stopProxy()
            isProxyRunning.value = false
        } else {
            repository.startProxy(proxyPort)
            isProxyRunning.value = true
        }
    }

    /**
     * Clears all recorded transaction lists and temporary file caches.
     */
    fun clearSession() {
        repository.clearSession()
    }

    /**
     * Installs and registers KNet CA root certificate into the local trust store.
     */
    fun trustRootCertificate() {
        repository.trustRootCertificate()
    }
}
