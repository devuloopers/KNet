package com.devuloopers.knet.data.desktop.core

import com.devuloopers.knet.data.desktop.runtime.CertificateRuntimeRepository
import com.devuloopers.knet.data.desktop.runtime.ProxyRuntimeRepository
import com.devuloopers.knet.data.desktop.runtime.SessionRuntimeRepository
import com.devuloopers.knet.domain.network.model.HttpTransaction
import com.devuloopers.knet.domain.network.model.ProxyTrafficListener
import kotlinx.coroutines.flow.Flow

/**
 * High-level core repository coordinating desktop proxy runtime, session streaming, and CA installation.
 * Uses constructor injection instead of static singleton state.
 */
class KNetCoreRepository(
    private val proxyRuntime: ProxyRuntimeRepository,
    private val sessionRuntime: SessionRuntimeRepository,
    private val certificateRuntime: CertificateRuntimeRepository
) {
    val liveTransactions: Flow<HttpTransaction> = sessionRuntime.liveTransactions

    fun startProxy(port: Int = 8080) {
        val listener = object : ProxyTrafficListener {
            override fun onTransactionCaptured(transaction: HttpTransaction) {
                sessionRuntime.recordTransaction(transaction)
            }
        }
        proxyRuntime.startProxy(port, listener)
    }

    fun stopProxy() {
        proxyRuntime.stopProxy()
    }

    fun isProxyRunning(): Boolean = proxyRuntime.isRunning()

    fun installRootCa(): Boolean = certificateRuntime.installRootCa()
}
