package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionCommand
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionEvent
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionSession
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutor
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMessage
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMessageDirection
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolOutboundMessage
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolRoute
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolSessionExecutor
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.suspendCancellableCoroutine

/** JVM WebSocket client factory with optional KNet-root trust for local-proxy execution. */
class WebSocketApiStudioClientFactory(
    private val knetRootCertificateDer: ByteArray,
) {
    /** Creates a route-specific Java HTTP client without mutating process-global TLS state. */
    fun create(route: ApiStudioProtocolRoute): HttpClient {
        val builder = HttpClient.newBuilder()
        if (route is ApiStudioProtocolRoute.LocalProxy) {
            builder.proxy(ProxySelector.of(InetSocketAddress(route.host, route.port)))
            builder.sslContext(knetSslContext())
        }
        return builder.build()
    }

    private fun knetSslContext(): SSLContext {
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(knetRootCertificateDer.inputStream())
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("knet-root", certificate)
        }
        val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }
        return SSLContext.getInstance("TLS").apply {
            init(null, trustManagers.trustManagers, null)
        }
    }
}

/** Native JVM WebSocket executor for one-shot and interactive API Studio workflows. */
class WebSocketApiStudioExecutor(
    private val draftCodec: WebSocketRequestDraftCodec,
    private val clients: WebSocketApiStudioClientFactory,
) : ApiStudioProtocolExecutor, ApiStudioProtocolSessionExecutor {
    override val kind: RequestKindId = RequestKindId.WEBSOCKET

    override fun open(command: ApiStudioProtocolExecutionCommand): Result<ApiStudioProtocolExecutionSession> =
        draftCodec.decode(command.document).map { draft ->
            WebSocketExecutionSession(draft, command.route, clients)
        }

    override fun execute(command: ApiStudioProtocolExecutionCommand): Flow<ApiStudioProtocolExecutionEvent> =
        callbackFlow {
            val draft = draftCodec.decode(command.document).getOrElse { error ->
                trySend(ApiStudioProtocolExecutionEvent.Failed(
                    code = "invalid_websocket_document",
                    message = error.message ?: "The WebSocket document is invalid.",
                    retryable = false,
                ))
                close()
                return@callbackFlow
            }
            val session = WebSocketExecutionSession(draft, command.route, clients)
            val relay = launch {
                session.events.collect { event ->
                    trySend(event)
                    if (event is ApiStudioProtocolExecutionEvent.Completed ||
                        event is ApiStudioProtocolExecutionEvent.Failed
                    ) close()
                }
            }
            val sender = launch {
                runCatching {
                    session.awaitConnected()
                    draft.outboundMessages.forEach { message ->
                        session.send(message.toOutbound()).getOrThrow()
                    }
                    session.halfClose().getOrThrow()
                }.onFailure { error ->
                    if (error !is kotlinx.coroutines.CancellationException) {
                        trySend(ApiStudioProtocolExecutionEvent.Failed(
                            code = "websocket_execution_failed",
                            message = error.message ?: "WebSocket execution failed.",
                            retryable = true,
                            actualProtocol = "WebSocket",
                        ))
                        close()
                    }
                }
            }
            awaitClose {
                sender.cancel()
                relay.cancel()
                session.cancel()
            }
        }
}

/** Long-lived WebSocket API Studio session with bounded fragmented-message assembly. */
@OptIn(ExperimentalEncodingApi::class)
private class WebSocketExecutionSession(
    private val draft: WebSocketRequestDraft,
    route: ApiStudioProtocolRoute,
    clients: WebSocketApiStudioClientFactory,
) : ApiStudioProtocolExecutionSession, WebSocket.Listener {
    private val eventFlow = MutableSharedFlow<ApiStudioProtocolExecutionEvent>(
        replay = 1,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val terminal = AtomicBoolean(false)
    private val sequence = AtomicLong(0L)
    private val connected = CompletableFuture<Unit>()
    private val textFragments = StringBuilder()
    private val binaryFragments = ByteArrayOutputStream()
    private val socketFuture: CompletableFuture<WebSocket>

    override val events: Flow<ApiStudioProtocolExecutionEvent> = eventFlow

    init {
        val client = clients.create(route)
        val builder = client.newWebSocketBuilder()
        if (draft.subprotocols.isNotEmpty()) {
            builder.subprotocols(draft.subprotocols.first(), *draft.subprotocols.drop(1).toTypedArray())
        }
        draft.headers.filter(WebSocketHandshakeHeader::enabled).forEach { header ->
            builder.header(header.name, header.value)
        }
        socketFuture = builder.buildAsync(URI.create(draft.url), this)
            .orTimeout(draft.connectTimeoutMillis, TimeUnit.MILLISECONDS)
        socketFuture.whenComplete { _, failure ->
            if (failure != null && terminal.compareAndSet(false, true)) {
                connected.completeExceptionally(failure)
                eventFlow.tryEmit(ApiStudioProtocolExecutionEvent.Failed(
                    code = "websocket_connect_failed",
                    message = failure.message ?: "WebSocket connection failed.",
                    retryable = true,
                ))
            }
        }
    }

    suspend fun awaitConnected() {
        connected.awaitCompletion()
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun send(message: ApiStudioProtocolOutboundMessage): Result<Unit> = runCatching {
        val socket = socketFuture.awaitCompletion()
        val binary = message.contentType.equals("application/octet-stream", ignoreCase = true)
        if (binary) {
            val payload = Base64.decode(message.displayText)
            socket.sendBinary(ByteBuffer.wrap(payload), true).awaitCompletion()
            publishMessage(ApiStudioProtocolMessageDirection.OUTBOUND, "application/octet-stream", message.displayText, payload)
        } else {
            socket.sendText(message.displayText, true).awaitCompletion()
            val payload = message.displayText.encodeToByteArray()
            publishMessage(ApiStudioProtocolMessageDirection.OUTBOUND, "text/plain; charset=UTF-8", message.displayText, payload)
        }
    }

    override suspend fun halfClose(): Result<Unit> = runCatching {
        val socket = socketFuture.awaitCompletion()
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "").awaitCompletion()
    }

    override fun cancel() {
        if (!terminal.compareAndSet(false, true)) return
        connected.completeExceptionally(IllegalStateException("WebSocket session was cancelled."))
        eventFlow.tryEmit(ApiStudioProtocolExecutionEvent.Failed(
            code = "websocket_cancelled",
            message = "WebSocket session was cancelled.",
            retryable = false,
            actualProtocol = "WebSocket",
        ))
        socketFuture.thenAccept { socket -> socket.abort() }
    }

    override fun onOpen(webSocket: WebSocket) {
        eventFlow.tryEmit(ApiStudioProtocolExecutionEvent.Started(
            summary = "WebSocket ${draft.url}",
            negotiatedApplicationProtocol = webSocket.subprotocol.takeIf(String::isNotBlank),
        ))
        connected.complete(Unit)
        webSocket.request(1)
    }

    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
        textFragments.append(data)
        if (textFragments.length > ApiStudioProtocolMessage.MAXIMUM_MESSAGE_BYTES) {
            webSocket.abort()
            onError(webSocket, IllegalStateException("WebSocket message exceeds the API Studio limit."))
            return CompletableFuture.completedFuture(null)
        }
        if (last) {
            val text = textFragments.toString()
            textFragments.clear()
            val payload = text.encodeToByteArray()
            if (payload.size > ApiStudioProtocolMessage.MAXIMUM_MESSAGE_BYTES) {
                webSocket.abort()
                onError(webSocket, IllegalStateException("WebSocket message exceeds the API Studio limit."))
                return CompletableFuture.completedFuture(null)
            }
            publishMessage(
                ApiStudioProtocolMessageDirection.INBOUND,
                "text/plain; charset=UTF-8",
                text,
                payload,
            )
        }
        webSocket.request(1)
        return CompletableFuture.completedFuture(null)
    }

    override fun onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage<*> {
        val payload = ByteArray(data.remaining())
        data.get(payload)
        binaryFragments.write(payload)
        if (binaryFragments.size() > ApiStudioProtocolMessage.MAXIMUM_MESSAGE_BYTES) {
            webSocket.abort()
            onError(webSocket, IllegalStateException("WebSocket message exceeds the API Studio limit."))
            return CompletableFuture.completedFuture(null)
        }
        if (last) {
            val complete = binaryFragments.toByteArray()
            binaryFragments.reset()
            publishMessage(
                ApiStudioProtocolMessageDirection.INBOUND,
                "application/octet-stream",
                Base64.encode(complete),
                complete,
            )
        }
        webSocket.request(1)
        return CompletableFuture.completedFuture(null)
    }

    override fun onPing(webSocket: WebSocket, message: ByteBuffer): CompletionStage<*> {
        val payload = message.copyRemainingBytes()
        publishMessage(
            ApiStudioProtocolMessageDirection.INBOUND,
            "application/websocket-ping",
            Base64.encode(payload),
            payload,
        )
        webSocket.request(1)
        return webSocket.sendPong(ByteBuffer.wrap(payload))
    }

    override fun onPong(webSocket: WebSocket, message: ByteBuffer): CompletionStage<*> {
        val payload = message.copyRemainingBytes()
        publishMessage(
            ApiStudioProtocolMessageDirection.INBOUND,
            "application/websocket-pong",
            Base64.encode(payload),
            payload,
        )
        webSocket.request(1)
        return CompletableFuture.completedFuture(null)
    }

    override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*> {
        if (terminal.compareAndSet(false, true)) {
            eventFlow.tryEmit(ApiStudioProtocolExecutionEvent.Completed(
                statusCode = statusCode.toString(),
                statusMessage = reason.takeIf(String::isNotBlank),
                actualProtocol = "WebSocket",
            ))
        }
        return CompletableFuture.completedFuture(null)
    }

    override fun onError(webSocket: WebSocket, error: Throwable) {
        if (terminal.compareAndSet(false, true)) {
            connected.completeExceptionally(error)
            eventFlow.tryEmit(ApiStudioProtocolExecutionEvent.Failed(
                code = "websocket_session_failed",
                message = error.message ?: "WebSocket session failed.",
                retryable = true,
                actualProtocol = "WebSocket",
            ))
        }
    }

    private fun publishMessage(
        direction: ApiStudioProtocolMessageDirection,
        contentType: String,
        displayText: String,
        payload: ByteArray,
    ) {
        eventFlow.tryEmit(ApiStudioProtocolExecutionEvent.Message(
            ApiStudioProtocolMessage(
                sequence = sequence.incrementAndGet(),
                direction = direction,
                contentType = contentType,
                displayText = displayText,
                payload = payload,
            ),
        ))
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun WebSocketAuthoredMessage.toOutbound(): ApiStudioProtocolOutboundMessage = when (kind) {
    WebSocketAuthoredMessageKind.TEXT -> ApiStudioProtocolOutboundMessage(content, "text/plain; charset=UTF-8")
    WebSocketAuthoredMessageKind.BINARY_BASE64 -> ApiStudioProtocolOutboundMessage(content, "application/octet-stream")
}

private suspend fun <T> CompletableFuture<T>.awaitCompletion(): T = suspendCancellableCoroutine { continuation ->
    whenComplete { value, failure ->
        if (failure == null) continuation.resume(value) else continuation.resumeWithException(failure)
    }
    continuation.invokeOnCancellation { cancel(true) }
}

private fun ByteBuffer.copyRemainingBytes(): ByteArray = ByteArray(remaining()).also(::get)
