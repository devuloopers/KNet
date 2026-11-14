package com.devuloopers.knet.companion.connectivity.control

import com.devuloopers.knet.companion.application.contract.*
import com.devuloopers.knet.companion.connectivity.http.*
import com.devuloopers.knet.companion.model.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.URLProtocol
import io.ktor.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Persistent Ktor session using the exact native trust/pin provider already qualified for HTTPS control. */
internal class KtorCompanionSessionTransport(private val provider: CompanionKtorClientProvider) : CompanionSessionTransport {
    override suspend fun connect(access: CompanionSessionAccess, credential: String): CompanionSessionConnection {
        val authorization = CompanionControlAuthorization(access.deviceId, credential)
        val request = CompanionHttpRequest(access.endpoint, CompanionHttpMethod.GET, CompanionSessionProtocol.PATH,
            maximumResponseBytes = CompanionSessionProtocol.MAXIMUM_MESSAGE_BYTES,
            security = CompanionHttpSecurity.PinnedRoot(access.root, access.rootFingerprint, access.transportFingerprint))
        val handle = provider.create(request)
        try {
            val session = handle.client.webSocketSession {
                url {
                    protocol = URLProtocol.WSS
                    host = handle.requestHost ?: access.endpoint.host
                    port = access.endpoint.port
                    encodedPathSegments = CompanionSessionProtocol.PATH.removePrefix("/").split('/')
                }
                header("Authorization", "Bearer ${authorization.deviceId.value}:${authorization.credential()}")
            }
            return Connection(session, handle)
        } catch (_: CancellationException) {
            handle.close()
            currentCoroutineContext().ensureActive()
            throw CompanionSessionException(CompanionSessionFailure.UNAVAILABLE)
        }
        catch (failure: Throwable) {
            handle.close()
            val status = (failure as? WebSocketException)?.message.orEmpty()
            // No raw server/exception text crosses the application boundary or enters logs.
            throw CompanionSessionException(handle.sessionFailure() ?: when {
                "404" in status || "426" in status -> CompanionSessionFailure.UNSUPPORTED
                "401" in status || "403" in status -> CompanionSessionFailure.UNAUTHORIZED
                else -> CompanionSessionFailure.UNAVAILABLE
            })
        }
    }

    private class Connection(private val session: DefaultClientWebSocketSession,
        private val handle: CompanionKtorClientHandle) : CompanionSessionConnection {
        private val closed = kotlinx.coroutines.flow.MutableStateFlow(false)
        override suspend fun send(message: CompanionSessionMessage): Unit = exchange {
            if (closed.value) throw CompanionSessionException(CompanionSessionFailure.UNAVAILABLE)
            session.send(Frame.Text(CompanionSessionProtocol.encode(message)))
        }
        override suspend fun receive(): CompanionSessionMessage {
            val frame = exchange { session.incoming.receive() } as? Frame.Text
                ?: throw CompanionSessionException(CompanionSessionFailure.INVALID_MESSAGE)
            if (frame.data.size > CompanionSessionProtocol.MAXIMUM_MESSAGE_BYTES) {
                throw CompanionSessionException(CompanionSessionFailure.INVALID_MESSAGE)
            }
            return try { CompanionSessionProtocol.decode(frame.readText()) }
            catch (_: Exception) { throw CompanionSessionException(CompanionSessionFailure.INVALID_MESSAGE) }
        }
        private suspend fun <T> exchange(operation: suspend () -> T): T = try {
            operation()
        } catch (_: CancellationException) {
            currentCoroutineContext().ensureActive()
            throw CompanionSessionException(CompanionSessionFailure.UNAVAILABLE)
        } catch (failure: CompanionSessionException) {
            throw failure
        } catch (_: Exception) {
            throw CompanionSessionException(CompanionSessionFailure.UNAVAILABLE)
        }
        override suspend fun close() {
            if (!closed.compareAndSet(false, true)) return
            try {
                withContext(NonCancellable) { withTimeoutOrNull(500L) { session.close() } }
            } finally { session.cancel(); handle.close() }
        }
    }
}
