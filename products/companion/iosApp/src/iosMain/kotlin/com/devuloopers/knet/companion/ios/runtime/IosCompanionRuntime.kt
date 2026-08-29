package com.devuloopers.knet.companion.ios.runtime

import com.devuloopers.knet.companion.ios.bootstrap.IosCompanionBootstrap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Process-scoped asynchronous bootstrap owned by one iOS application composition. */
internal class IosCompanionRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableBootstrap = MutableStateFlow<IosCompanionBootstrapState>(
        IosCompanionBootstrapState.Loading,
    )

    val bootstrap: StateFlow<IosCompanionBootstrapState> = mutableBootstrap.asStateFlow()

    init {
        scope.launch {
            mutableBootstrap.value = runCatching { IosCompanionBootstrap.create(scope) }
                .fold(
                    onSuccess = IosCompanionBootstrapState::Ready,
                    onFailure = { IosCompanionBootstrapState.Failed },
                )
        }
    }

    fun close() {
        scope.cancel()
    }
}

/** Stable bootstrap states rendered without constructing a partial dependency graph. */
internal sealed interface IosCompanionBootstrapState {
    data object Loading : IosCompanionBootstrapState
    data class Ready(val value: IosCompanionBootstrap) : IosCompanionBootstrapState
    data object Failed : IosCompanionBootstrapState
}
