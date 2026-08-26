package com.devuloopers.knet.companion.connectivity.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.model.CompanionNetworkState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Callback-driven Android network availability adapter with explicit callback ownership. */
internal class AndroidCompanionNetworkObserver(context: Context) : CompanionNetworkObserver, AutoCloseable {
    private val connectivityManager: ConnectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val mutableState: MutableStateFlow<CompanionNetworkState> =
        MutableStateFlow(CompanionNetworkState.Unknown)
    private var closed: Boolean = false
    private val callback: ConnectivityManager.NetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network): Unit = publish()
        override fun onLost(network: Network): Unit = publish()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities): Unit = publish()

        override fun onUnavailable() {
            mutableState.value = CompanionNetworkState.Unavailable
        }
    }

    init {
        publish()
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
            callback,
        )
    }

    /** Returns callback-driven Android network availability as portable companion state. */
    override fun observe(): StateFlow<CompanionNetworkState> = mutableState.asStateFlow()

    /** Unregisters the process-owned network callback exactly once. */
    override fun close() {
        if (closed) return
        closed = true
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private fun publish() {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
        mutableState.value = if (
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        ) {
            CompanionNetworkState.Available(
                metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            )
        } else {
            CompanionNetworkState.Unavailable
        }
    }
}
