package com.devuloopers.knet.companion.connectivity.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.application.contract.CompanionInspectionPreparationResult
import com.devuloopers.knet.companion.application.contract.CompanionInspectionStartResult
import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.model.CompanionNetworkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Android VPN consent check kept outside shared state so no Intent crosses the KMP boundary. */
fun interface AndroidVpnConsent {
    fun isGranted(): Boolean
}

/** Production Android consent adapter. The product handles the actual Intent returned by VpnService.prepare. */
class PlatformAndroidVpnConsent(context: Context) : AndroidVpnConsent {
    private val applicationContext = context.applicationContext
    override fun isGranted(): Boolean = VpnService.prepare(applicationContext) == null
}

/** Result returned by a qualified Android VPN/packet backend. */
sealed interface AndroidInspectionBackendResult {
    data object Started : AndroidInspectionBackendResult
    data class Failed(val failure: CompanionFailure) : AndroidInspectionBackendResult
}

/** Replaceable Android backend responsible for VpnService, TUN ownership, and bounded packet translation. */
interface AndroidInspectionBackend {
    suspend fun start(configuration: CompanionInspectionConfiguration): AndroidInspectionBackendResult
    suspend fun stop()
}

/** Deterministic Android implementation of the shared inspection lifecycle contract. */
class AndroidCompanionInspectionController(
    private val consent: AndroidVpnConsent,
    private val backend: AndroidInspectionBackend,
    private val nowEpochMillis: () -> Long,
) : CompanionInspectionController {
    private val lifecycleLock = Mutex()
    private val mutableState = MutableStateFlow<CompanionInspectionState>(CompanionInspectionState.Stopped)
    override val state: StateFlow<CompanionInspectionState> = mutableState.asStateFlow()

    override suspend fun prepare(): CompanionInspectionPreparationResult = lifecycleLock.withLock {
        if (consent.isGranted()) {
            if (mutableState.value !is CompanionInspectionState.Running) {
                mutableState.value = CompanionInspectionState.Stopped
            }
            CompanionInspectionPreparationResult.Ready
        } else {
            mutableState.value = CompanionInspectionState.AwaitingVpnConsent
            CompanionInspectionPreparationResult.ConsentRequired
        }
    }

    override suspend fun start(configuration: CompanionInspectionConfiguration): CompanionInspectionStartResult =
        lifecycleLock.withLock {
            if (mutableState.value is CompanionInspectionState.Running) {
                return@withLock CompanionInspectionStartResult.Started
            }
            if (!consent.isGranted()) {
                mutableState.value = CompanionInspectionState.AwaitingVpnConsent
                return@withLock CompanionInspectionStartResult.Failed(
                    CompanionFailure(CompanionFailureCode.VPN_PERMISSION_DENIED, "Android VPN permission is required.", true),
                )
            }
            mutableState.value = CompanionInspectionState.Preparing
            return try {
                when (val result = backend.start(configuration)) {
                    AndroidInspectionBackendResult.Started -> {
                        mutableState.value = CompanionInspectionState.Running(
                            mode = configuration.mode,
                            startedAtEpochMillis = nowEpochMillis(),
                            fullHttpsInspection = configuration.fullHttpsInspection,
                        )
                        CompanionInspectionStartResult.Started
                    }
                    is AndroidInspectionBackendResult.Failed -> {
                        mutableState.value = CompanionInspectionState.Failed(result.failure)
                        CompanionInspectionStartResult.Failed(result.failure)
                    }
                }
            } catch (cancelled: CancellationException) {
                mutableState.value = CompanionInspectionState.Stopped
                throw cancelled
            } catch (_: Throwable) {
                val failure = CompanionFailure(
                    CompanionFailureCode.VPN_START_FAILED,
                    "Android VPN could not be started.",
                    true,
                )
                mutableState.value = CompanionInspectionState.Failed(failure)
                CompanionInspectionStartResult.Failed(failure)
            }
        }

    override suspend fun stop(): Unit = lifecycleLock.withLock {
        if (mutableState.value == CompanionInspectionState.Stopped) return@withLock
        mutableState.value = CompanionInspectionState.Stopping
        try {
            backend.stop()
        } finally {
            mutableState.value = CompanionInspectionState.Stopped
        }
    }
}

/** Callback-driven Android network availability adapter with explicit callback ownership. */
class AndroidCompanionNetworkObserver(context: Context) : CompanionNetworkObserver, AutoCloseable {
    private val connectivityManager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val mutableState = MutableStateFlow<CompanionNetworkState>(CompanionNetworkState.Unknown)
    private var closed = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish()
        override fun onLost(network: Network) = publish()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = publish()
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

    override fun observe(): StateFlow<CompanionNetworkState> = mutableState.asStateFlow()

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
