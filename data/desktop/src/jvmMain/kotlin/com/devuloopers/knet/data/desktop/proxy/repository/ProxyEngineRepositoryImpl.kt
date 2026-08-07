package com.devuloopers.knet.data.desktop.proxy.repository

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.data.desktop.mapper.TransactionMapper
import com.devuloopers.knet.data.desktop.runtime.ProxyRuntimeRepository
import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.clientNetwork.model.HttpTimings
import com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction
import com.devuloopers.knet.domain.clientNetwork.model.ProxyTrafficListener
import com.devuloopers.knet.domain.proxy.model.ProxyEngineState
import com.devuloopers.knet.domain.proxy.repository.ProxyEngineRepository
import com.devuloopers.knet.storage.database.KNetDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.devuloopers.knet.engine.session.FilePayloadStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop implementation of [ProxyEngineRepository] managing Netty lifecycle and 2-phase request/response correlation.
 * Correlates intercepted requests and responses into a single evolving [HttpTransaction] and persists to [KNetDatabase].
 */
public class ProxyEngineRepositoryImpl(
    private val proxyRuntimeRepository: ProxyRuntimeRepository,
    private val database: KNetDatabase
) : ProxyEngineRepository, ProxyTrafficListener {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val _engineState = MutableStateFlow<ProxyEngineState>(ProxyEngineState.Stopped)
    private val pendingRequests = ConcurrentHashMap<String, HttpRequest>()
    private val payloadStore = FilePayloadStore(
        File(System.getProperty("user.home"), ".knet/payloads").apply { mkdirs() }
    )

    override suspend fun start(port: Int) {
        if (_engineState.value is ProxyEngineState.Running || _engineState.value is ProxyEngineState.Starting) {
            KNetLogger.warn(tag = "KNet_Proxy_Capture") { "Proxy engine is already starting or running." }
            return
        }

        _engineState.value = ProxyEngineState.Starting
        try {
            proxyRuntimeRepository.startProxy(port = port, trafficListener = this)
            _engineState.value = ProxyEngineState.Running(port)
            KNetLogger.info(tag = "KNet_Proxy_Capture") { "Proxy engine successfully started on port $port." }
        } catch (e: Exception) {
            KNetLogger.error(tag = "KNet_Proxy_Capture", throwable = e) { "Failed to start proxy engine." }
            _engineState.value = ProxyEngineState.Error(e.message ?: "Failed to bind to port $port.")
        }
    }

    override fun onRequestCaptured(request: HttpRequest) {
        println("[ENGINE_DEBUG] 📥 ProxyEngineRepositoryImpl.onRequestCaptured: ${request.method} ${request.url} (id=${request.id})")
        KNetLogger.info(tag = "KNet_Proxy_Capture") {
            "📥 REQUEST [id=${request.id}]: ${request.method} ${request.url}"
        }

        pendingRequests[request.id] = request
        val reqBodyPath = payloadStore.savePayload(request.id, "req", request.body)

        val pendingTx = HttpTransaction(
            id = request.id,
            request = request,
            response = null,
            requestBodyPath = reqBodyPath,
            responseBodyPath = null,
            requestBodySize = request.body?.size?.toLong() ?: 0L,
            responseBodySize = 0L,
            durationMs = 0L,
            timestamp = request.timestamp
        )
        scope.launch {
            try {
                println("[ENGINE_DEBUG] 💾 Room DB inserting pending: id=${pendingTx.id}, url=${pendingTx.request.url}")
                KNetLogger.info(tag = "KNet_Proxy_RoomDB") {
                    "💾 INSERT PENDING [id=${pendingTx.id}]: ${pendingTx.request.method} ${pendingTx.request.url}"
                }
                val entity = TransactionMapper.mapDomainToEntity(pendingTx)
                database.httpTransactionDao().insert(entity)
                println("[ENGINE_DEBUG] ✅ Room DB insert pending SUCCESS: id=${pendingTx.id}")
            } catch (e: Exception) {
                println("[ENGINE_DEBUG] ❌ Room DB insert pending FAILED: ${e.message}")
                KNetLogger.error(tag = "KNet_Proxy_RoomDB", throwable = e) {
                    "Failed to persist pending request: ${e.message}"
                }
            }
        }
    }

    override fun onResponseCaptured(
        transactionId: String,
        response: HttpResponse,
        durationMs: Long,
        timings: HttpTimings
    ) {
        println("[ENGINE_DEBUG] 📤 ProxyEngineRepositoryImpl.onResponseCaptured: Status ${response.statusCode} (id=$transactionId)")
        KNetLogger.info(tag = "KNet_Proxy_Capture") {
            "📤 RESPONSE [id=$transactionId]: Status ${response.statusCode} ${response.statusText} (${durationMs}ms)"
        }

        val request = pendingRequests.remove(transactionId)
        val reqToUse = request ?: HttpRequest(
            id = transactionId,
            method = "GET",
            url = "http://unknown",
            protocol = "HTTP/1.1",
            headers = emptyList(),
            body = null,
            timestamp = System.currentTimeMillis()
        )

        val reqBodyPath = payloadStore.savePayload(transactionId, "req", reqToUse.body)
        val resBodyPath = payloadStore.savePayload(transactionId, "res", response.body)

        val completedTx = HttpTransaction(
            id = transactionId,
            request = reqToUse,
            response = response,
            requestBodyPath = reqBodyPath,
            responseBodyPath = resBodyPath,
            requestBodySize = reqToUse.body?.size?.toLong() ?: 0L,
            responseBodySize = response.body?.size?.toLong() ?: 0L,
            durationMs = durationMs,
            timestamp = reqToUse.timestamp,
            timings = timings
        )

        scope.launch {
            try {
                println("[ENGINE_DEBUG] 💾 Room DB updating completed: id=${completedTx.id}, status=${response.statusCode}")
                KNetLogger.info(tag = "KNet_Proxy_RoomDB") {
                    "💾 UPDATE COMPLETED [id=${completedTx.id}]: Status ${response.statusCode} (${durationMs}ms)"
                }
                val entity = TransactionMapper.mapDomainToEntity(completedTx)
                database.httpTransactionDao().insert(entity)
                println("[ENGINE_DEBUG] ✅ Room DB update completed SUCCESS: id=${completedTx.id}")
            } catch (e: Exception) {
                println("[ENGINE_DEBUG] ❌ Room DB update completed FAILED: ${e.message}")
                KNetLogger.error(tag = "KNet_Proxy_RoomDB", throwable = e) {
                    "Failed to update completed transaction: ${e.message}"
                }
            }
        }
    }

    override fun onTransactionCaptured(transaction: HttpTransaction) {
        scope.launch {
            try {
                val reqBodyPath = payloadStore.savePayload(transaction.id, "req", transaction.request.body)
                val resBodyPath = payloadStore.savePayload(transaction.id, "res", transaction.response?.body)
                val txToSave = transaction.copy(
                    requestBodyPath = reqBodyPath,
                    responseBodyPath = resBodyPath,
                    requestBodySize = transaction.request.body?.size?.toLong() ?: transaction.requestBodySize,
                    responseBodySize = transaction.response?.body?.size?.toLong() ?: transaction.responseBodySize
                )
                val entity = TransactionMapper.mapDomainToEntity(txToSave)
                database.httpTransactionDao().insert(entity)
            } catch (e: Exception) {
                KNetLogger.error(tag = "KNet_Proxy_RoomDB", throwable = e) {
                    "Failed to persist captured transaction: ${e.message}"
                }
            }
        }
    }


    override suspend fun stop() {
        if (_engineState.value is ProxyEngineState.Stopped || _engineState.value is ProxyEngineState.Stopping) {
            return
        }

        _engineState.value = ProxyEngineState.Stopping
        try {
            proxyRuntimeRepository.stopProxy()
            pendingRequests.clear()
            _engineState.value = ProxyEngineState.Stopped
            KNetLogger.info(tag = "KNet_Proxy_Capture") { "Proxy engine successfully stopped." }
        } catch (e: Exception) {
            KNetLogger.error(tag = "KNet_Proxy_Capture", throwable = e) { "Error stopping proxy engine." }
            _engineState.value = ProxyEngineState.Error(e.message ?: "Failed to stop proxy engine.")
        }
    }

    override fun engineState(): Flow<ProxyEngineState> = _engineState.asStateFlow()
}
