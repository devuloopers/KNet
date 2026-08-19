package com.devuloopers.knet.ui.desktop.connectivity.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.port.proxy.ProxyStartResult
import com.devuloopers.knet.application.usecase.connectivity.wifi.ObserveWifiSharingUseCase
import com.devuloopers.knet.application.usecase.proxy.ObserveProxyRuntimeStateUseCase
import com.devuloopers.knet.application.usecase.proxy.StartLoopbackProxyUseCase
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceIntent
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceMessage
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceMessageTone
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceOperation
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/** UI-only state holder for the single-card Wi-Fi proxy setup workflow. */
class ConnectDeviceViewModel(
    private val startLoopbackProxy: StartLoopbackProxyUseCase,
    observeProxyRuntimeState: ObserveProxyRuntimeStateUseCase,
    observeWifiSharing: ObserveWifiSharingUseCase,
    getWorkspaceLayout: GetWorkspaceLayoutUseCase,
) : ViewModel() {
    private val operationMutex = Mutex()
    private val proxyStates = observeProxyRuntimeState.execute()
    private val sharingStates = observeWifiSharing.execute()
    private val mutableUiState = MutableStateFlow(
        ConnectDeviceUiState(
            proxyState = proxyStates.value,
            sharingState = sharingStates.value,
        ),
    )
    val uiState: StateFlow<ConnectDeviceUiState> = mutableUiState.asStateFlow()

    init {
        proxyStates
            .onEach { proxyState -> mutableUiState.update { current -> current.copy(proxyState = proxyState) } }
            .launchIn(viewModelScope)
        sharingStates
            .onEach { sharingState -> mutableUiState.update { current -> current.copy(sharingState = sharingState) } }
            .launchIn(viewModelScope)
        getWorkspaceLayout.execute()
            .map { settings -> settings.proxyPort }
            .distinctUntilChanged()
            .catch { emit(StartLoopbackProxyUseCase.DEFAULT_PORT) }
            .onEach { port ->
                val safePort = port.takeIf { it in 1..65_535 } ?: StartLoopbackProxyUseCase.DEFAULT_PORT
                mutableUiState.update { current -> current.copy(preferredProxyPort = safePort) }
            }
            .launchIn(viewModelScope)
    }

    /** Applies one user interaction without exposing application use cases to Compose. */
    fun processIntent(intent: ConnectDeviceIntent) {
        when (intent) {
            ConnectDeviceIntent.OpenSetup -> mutableUiState.update { it.copy(isSetupDrawerVisible = true) }
            ConnectDeviceIntent.CloseSetup -> mutableUiState.update { it.copy(isSetupDrawerVisible = false) }
            ConnectDeviceIntent.DismissMessage -> mutableUiState.update { it.copy(message = null) }
            ConnectDeviceIntent.StartProxy -> runOperation(ConnectDeviceOperation.STARTING_PROXY, ::startProxy)
        }
    }

    private fun runOperation(
        operation: ConnectDeviceOperation,
        action: suspend () -> Unit,
    ) {
        if (!operationMutex.tryLock()) return
        mutableUiState.update { it.copy(operation = operation, message = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                showFailure("unexpected_connectivity_failure")
            } finally {
                mutableUiState.update { it.copy(operation = null) }
                operationMutex.unlock()
            }
        }
    }

    private suspend fun startProxy() {
        when (val result = startLoopbackProxy.execute(mutableUiState.value.preferredProxyPort)) {
            is ProxyStartResult.Running -> showInfo(
                "Proxy started. Wi-Fi access will become ready automatically.",
            )
            is ProxyStartResult.Failed -> showFailure(result.code)
        }
    }

    private fun showInfo(text: String) {
        mutableUiState.update {
            it.copy(message = ConnectDeviceMessage(text, ConnectDeviceMessageTone.INFO))
        }
    }

    private fun showFailure(code: String) {
        val message = when (code) {
            "wifi_address_unavailable" -> "No supported local Wi-Fi address is currently available."
            "wifi_bind_failed" -> "KNet could not open the Wi-Fi proxy or setup page on this network."
            "certificate_unavailable" -> "The KNet certificate authority is unavailable."
            "unexpected_connectivity_failure" -> "The connectivity operation failed unexpectedly."
            else -> "Connectivity operation failed ($code)."
        }
        mutableUiState.update {
            it.copy(message = ConnectDeviceMessage(message, ConnectDeviceMessageTone.ERROR))
        }
    }
}
