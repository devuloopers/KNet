@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.devuloopers.knet.companion.connectivity.network

import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.model.CompanionNetworkState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

/** Callback-driven iOS route observer backed by Network.framework. */
internal class IosCompanionNetworkObserver : CompanionNetworkObserver, AutoCloseable {
    private val mutableState = MutableStateFlow<CompanionNetworkState>(CompanionNetworkState.Unknown)
    private val monitor = nw_path_monitor_create()
    private val queue = dispatch_queue_create(NETWORK_QUEUE_LABEL, null)
    private var closed: Boolean = false

    init {
        nw_path_monitor_set_update_handler(monitor) { path ->
            mutableState.value = if (path != null && nw_path_get_status(path) == nw_path_status_satisfied) {
                CompanionNetworkState.Available(metered = nw_path_is_expensive(path))
            } else {
                CompanionNetworkState.Unavailable
            }
        }
        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_start(monitor)
    }

    override fun observe(): StateFlow<CompanionNetworkState> = mutableState.asStateFlow()

    override fun close() {
        if (closed) return
        closed = true
        nw_path_monitor_cancel(monitor)
    }

    private companion object {
        const val NETWORK_QUEUE_LABEL: String = "com.devuloopers.knet.companion.network"
    }
}
