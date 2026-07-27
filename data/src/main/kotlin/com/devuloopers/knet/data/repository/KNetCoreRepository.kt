package com.devuloopers.knet.data.repository

import com.devuloopers.knet.crypto.CertificateAuthority
import com.devuloopers.knet.crypto.CertificateCache
import com.devuloopers.knet.crypto.TrustStoreInstaller
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.model.HttpRequest
import com.devuloopers.knet.model.HttpResponse
import com.devuloopers.knet.model.HttpTransaction
import com.devuloopers.knet.model.ProxyTrafficListener
import com.devuloopers.knet.session.KNetSession
import com.devuloopers.knet.session.FilePayloadStore
import com.devuloopers.knet.storage.KNetDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Orchestrator repository that manages database storage, CA keys, and the Netty proxy server lifecycle.
 * Coordinates incoming Netty packets and records them to the SQLite persistence tables.
 */
class KNetCoreRepository private constructor(
    baseDir: File
) {

    companion object {
        @Volatile
        private var instance: KNetCoreRepository? = null

        /**
         * Obtains the thread-safe singleton instance of KNetCoreRepository.
         *
         * @param baseDir The root data directory for storing databases and payloads.
         * @return The singleton repository instance.
         */
        fun getInstance(baseDir: File): KNetCoreRepository {
            return instance ?: synchronized(this) {
                instance ?: KNetCoreRepository(baseDir).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val caDir = File(baseDir, "ca").apply { mkdirs() }
    private val caCertFile = File(caDir, "ca.crt")
    private val caKeyFile = File(caDir, "ca.key")

    // 1. Initialize Root CA Manager
    private val ca: CertificateAuthority = if (caCertFile.exists() && caKeyFile.exists()) {
        CertificateAuthority.loadFromPem(caCertFile, caKeyFile)
    } else {
        val generatedCa = CertificateAuthority.generate()
        generatedCa.saveToPem(caCertFile, caKeyFile)
        generatedCa
    }
    private val certCache: CertificateCache = CertificateCache()

    private val payloadStore: FilePayloadStore
    private val database: KNetDatabase
    private val session: KNetSession

    private var proxyServer: KNetProxyServer? = null

    init {

        // 2. Initialize Payload Storage and Metadata DB
        val payloadsDir = File(baseDir, "payloads").apply { mkdirs() }
        payloadStore = FilePayloadStore(payloadsDir)
        
        val dbFile = File(baseDir, "knet.db")
        database = KNetDatabase.create(dbFile)
        session = KNetSession(database, payloadStore)

        // 3. Automatically trigger OS Root CA trust registration on launch
        scope.launch {
            try {
                TrustStoreInstaller.install(ca.certificate)
            } catch (_: Exception) {}
        }
    }

    /**
     * Cold stream returning the chronologically descending transactions list.
     * Automatically maps database updates to domain DTOs.
     */
    val transactionsFlow: Flow<List<HttpTransaction>>
        get() = session.transactionsFlow

    /**
     * Spawns and runs the local proxy server on the designated port.
     *
     * @param port The target local socket port (default: 8888).
     */
    @Synchronized
    fun startProxy(port: Int = 8888) {
        if (proxyServer?.isRunning() == true) return

        val listener = object : ProxyTrafficListener {
            override fun onRequestCaptured(request: HttpRequest) {
                scope.launch {
                    session.recordRequest(request)
                }
            }

            override fun onResponseCaptured(
                transactionId: String,
                response: HttpResponse,
                durationMs: Long,
                timings: com.devuloopers.knet.model.HttpTimings
            ) {
                scope.launch {
                    session.recordResponse(
                        transactionId = transactionId,
                        response = response,
                        durationMs = durationMs,
                        timings = timings
                    )
                }
            }
        }

        proxyServer = KNetProxyServer(
            port = port,
            ca = ca,
            certCache = certCache,
            listener = listener
        ).apply {
            start()
        }
    }

    /**
     * Stops the running proxy server and releases socket connections.
     */
    @Synchronized
    fun stopProxy() {
        proxyServer?.stop()
        proxyServer = null
    }

    /**
     * Clears all transactions and payload files.
     */
    fun clearSession() {
        scope.launch {
            session.clearSession()
        }
    }

    /**
     * Triggers dynamic Root CA registration into the operating system's trust store.
     */
    fun trustRootCertificate() {
        TrustStoreInstaller.install(ca.certificate)
    }

    /**
     * Evaluates whether the proxy server is running.
     *
     * @return True if server is active.
     */
    fun isProxyRunning(): Boolean {
        return proxyServer?.isRunning() == true
    }
}
