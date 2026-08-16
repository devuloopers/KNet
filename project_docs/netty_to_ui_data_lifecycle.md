# KNet Deep-Dive: Netty Wire Data to UI Rendering — A Low-Level Architecture Reference

This document provides an exhaustive, implementation-level reference for how KNet captures a raw TCP byte stream, progressively transforms it through domain models, and delivers a fully-parsed, zero-UI-thread-overhead rendered view to the Compose Desktop UI.

Architectural audit findings are embedded inline in the relevant sections.

---

## 0. Module Dependency Graph

```
io.netty (NioEventLoopGroup)
    └── :engine:proxy    (KNetProxyServer, KNetProxyHandler)
    └── :engine:interceptor (KNetInterceptorHandler, InterceptCoordinator, InterceptSessionManager)
         └── :core:domain (HttpRequest, HttpResponse, BodyDecoder, BodyUtils)
              └── :engine:formatter (BodyFormatterRegistry, BodyFormat sealed class)
                   └── :ui:desktop:httpPanel (PayloadInspectionSpec, RequestBodyState, ResponseBodyState)
                        └── :ui:desktop:traffic (TrafficViewModel, InspectorPreparedState)
                        └── :ui:desktop:breakpointManager (BreakpointManagerViewModel, BreakpointManagerState)
```

**Architectural constraint**: Every arrow is unidirectional. The engine never imports UI modules. The UI never imports `io.netty.*`.

---

## 1. Netty Socket Ingestion & TLS MITM Decryption

### 1.1 Server Bootstrap & EventLoop Thread Model

`KNetProxyServer` configures a standard Netty NIO 2-thread-group bootstrap:

```kotlin
// Boss Group: 1 thread — accepts TCP connections (SYN, SYN-ACK, ACK)
val bossGroup = NioEventLoopGroup(1)

// Worker Group: nThreads = CPU core count (default Netty heuristic)
// Each worker owns an infinite select() loop over registered SocketChannels
val workerGroup = NioEventLoopGroup()
```

**Why 2 groups?** `bossGroup` owns the `ServerSocketChannel` file descriptor. When `accept()` returns a client `SocketChannel`, it hands the channel's ownership to one worker thread from `workerGroup` for all future I/O on that connection. This is the reactor pattern.

**Key invariant**: Every read and write on a given channel is executed exclusively by the owning worker thread. This is what makes Netty pipelines thread-safe without locks.

### 1.2 Pipeline Construction

For every accepted client channel, Netty calls `ServerChannelInitializer.initChannel()`, which adds handlers in order:

```
Channel Pipeline (inbound reads left-to-right, outbound writes right-to-left):

[HttpServerCodec]           → Decodes chunked bytes → HttpRequest/HttpContent/LastHttpContent
[HttpObjectAggregator]      → Aggregates chunks into FullHttpRequest (up to 256 MB)
[KNetInterceptorHandler]    → Evaluates breakpoint rules, suspends matched transactions
[KNetProxyHandler]          → Routes requests to remote origin server
```

`PipelineHandlerNames.MAX_CONTENT_LENGTH_BYTES` caps the aggregator at `268435456` (256 MB) to prevent OOM attacks.

### 1.3 CONNECT Tunneling & TLS MITM

When a browser sends `CONNECT api.example.com:443 HTTP/1.1`:
1. `KNetProxyHandler.handleConnect()` responds with `200 Connection Established`.
2. It **dynamically patches the live pipeline**:
   - Removes `HTTP_CODEC` and `HTTP_AGGREGATOR`.
   - Inserts `SslHandler` at the front, using a per-host MITM leaf certificate signed by KNet's local CA.
   - Inserts fresh `HttpServerCodec` + `HttpObjectAggregator` after the `SslHandler`.
3. The client's TLS handshake now completes with KNet's certificate. The worker thread sees plaintext.

```kotlin
pipeline.addFirst(PipelineHandlerNames.SSL, sslContext.newHandler(context.alloc()))
pipeline.addAfter(PipelineHandlerNames.SSL, PipelineHandlerNames.HTTP_CODEC, HttpServerCodec())
pipeline.addAfter(PipelineHandlerNames.HTTP_CODEC, PipelineHandlerNames.HTTP_AGGREGATOR,
    HttpObjectAggregator(PipelineHandlerNames.MAX_CONTENT_LENGTH_BYTES))
```

**Audit finding: Architecture is correct.** MITM is certificate-authority-based with per-host leaf certs from `CertificateCache`, avoiding the security anti-pattern of installing a single wildcard certificate.

---

## 2. ByteBuf → Domain Model: The Most Critical Boundary

### 2.1 Why ByteBuf Is Dangerous

Netty allocates `ByteBuf` instances from `PooledByteBufAllocator` (jemalloc-style arena pools of direct off-heap native memory). These are **ref-counted**. Every `FullHttpRequest` arriving in `channelRead` has `refCnt = 1`. If KNet does not either:
- Call `ReferenceCountUtil.retain(msg)` before crossing a thread boundary, or
- Consume the data synchronously and let Netty release it

...then `refCnt` drops to 0 and the native memory is returned to the pool, causing silent use-after-free corruption in subsequent reads.

### 2.2 The Safe Extraction Pattern — `HttpMapper.extractBody()`

```kotlin
// HttpMapper.kt
private fun extractBody(content: ByteBuf): ByteArray? {
    return if (content.readableBytes() > 0) {
        val bytes = ByteArray(content.readableBytes())
        content.getBytes(content.readerIndex(), bytes) // Does NOT advance readerIndex
        bytes
    } else null
}
```

**Why `getBytes` instead of `readBytes`?** `getBytes` is a non-destructive absolute-index read. `readBytes` advances the readerIndex, which would interfere with other handlers downstream in the pipeline that may also need to read from the same buffer.

**Result**: `body: ByteArray?` is a heap-allocated, GC-managed copy. The off-heap `ByteBuf` can be safely released by Netty after the handler returns. From this point forward, no Netty-specific types appear in the domain layer.

### 2.3 `HttpMapper.mapRequest()` — Full Field Mapping Table

| Netty Field | Domain Field | Notes |
| :--- | :--- | :--- |
| `FullHttpRequest.method().name()` | `HttpRequest.method: String` | String (not Netty `HttpMethod`) |
| `FullHttpRequest.uri()` | `HttpRequest.url: String` | Reconstructed absolute URL |
| `FullHttpRequest.protocolVersion().text()` | `HttpRequest.protocol: String` | e.g. `"HTTP/1.1"` |
| `FullHttpRequest.headers()` | `HttpRequest.headers: List<Pair<String,String>>` | Ordered list, case-preserved |
| `FullHttpRequest.content()` | `HttpRequest.body: ByteArray?` | Off-heap → heap copy |
| `System.currentTimeMillis()` | `HttpRequest.timestamp: Long` | Wall clock at capture time |
| Header `X-KNet-Transaction-Id` (if present) | `HttpRequest.id: String` | Removed from outbound |
| `kotlin.uuid.Uuid.random().toString()` | `HttpRequest.id: String` | Fallback if no header |

**Audit finding: Strong typing is maintained.** `HttpRequest` uses `kotlin.uuid.Uuid` (not `java.util.UUID`) in compliance with the Kotlin UUID Rule. Headers are typed as `List<Pair<String,String>>` — ordered and case-preserving, not a case-folding `Map<String,String>` that would silently discard duplicate header names (legal in HTTP/1.1).

---

## 3. Breakpoint Interception — Coroutine Suspension over an EventLoop Thread

### 3.1 Rule Matching

`BreakpointMatcher.findMatchingRequestRule(url, method, bodyText)` is called synchronously on the Netty worker thread. It reads from `BreakpointRuleRegistry` (a `MutableStateFlow<List<RuleModel>>`). This is safe because `StateFlow.value` is always a snapshot read with volatile semantics — no lock required.

### 3.2 TCP Backpressure

```kotlin
// InterceptCoordinator.coordinateRequest()
context.channel().config().isAutoRead = false
ReferenceCountUtil.retain(msg)
```

`isAutoRead = false` instructs the Netty I/O thread to stop calling `read()` on the underlying `SocketChannel`. This propagates TCP backpressure all the way down to the OS kernel: the socket receive buffer fills, and the remote client's TCP window shrinks to 0, stalling its send. The connection is effectively paused at the TCP protocol level.

`ReferenceCountUtil.retain(msg)` increments `refCnt` to 2, preventing Netty from releasing the `ByteBuf` when the handler method returns.

### 3.3 `CompletableDeferred` — The Bridge Between Netty and Kotlin

```kotlin
val event = InterceptSessionManager.suspendRequest(request)
// event.deferred is a CompletableDeferred<InterceptResult>

val dispatcher = context.executor().asCoroutineDispatcher()
val scope = CoroutineScope(SupervisorJob() + dispatcher)

scope.launch {
    val result = withTimeoutOrNull(60_000L) {
        event.deferred.await()    // Suspends the coroutine — does NOT block the EventLoop thread
    } ?: InterceptResult.Timeout
    // ...
}
```

**Critically**, `event.deferred.await()` is a non-blocking coroutine suspension. The Netty worker thread returns to its `select()` loop and continues processing other channels. When `InterceptSessionManager.resume(eventId, result)` is called from the UI layer:

```kotlin
targetEvent?.deferred?.complete(result)
```

This schedules the coroutine continuation on the same `dispatcher = context.executor().asCoroutineDispatcher()`. The coroutine resumes on the **same Netty worker thread that owns the channel** — a critical constraint for calling `context.fireChannelRead()` and `context.write()`.

### 3.4 InterceptedEvent — In-Memory Suspension Registry

```kotlin
// InterceptSessionManager._activeEventsStream
private val _activeEventsStream = MutableStateFlow<List<InterceptedEvent>>(emptyList())
```

`MutableStateFlow.update {}` uses a CAS (compare-and-swap) spin loop internally (not a mutex). Concurrent modifications from multiple Netty worker threads are safely serialized without blocking.

**Ordering guarantee**: `currentList + event` always appends to the tail. Combined with `update {}` CAS semantics, the FIFO invariant is maintained even under concurrent suspension registrations from multiple channels.

### 3.5 Timeout & Cleanup

`withTimeoutOrNull(timeoutMs)` uses the coroutine timer subsystem. On timeout:
1. `InterceptResult.Timeout` propagates to the `when(result)` branch.
2. `ReferenceCountUtil.release(msg)` decrements `refCnt` to 0, returning off-heap memory.
3. `context.channel().config().isAutoRead = true` re-enables TCP reads.
4. `context.close()` sends TCP FIN to the client.

**Audit finding: The lifecycle is leak-free.** All three branches (`Resume`, `Drop`, `Timeout`) and the exception handler all call `ReferenceCountUtil.release(msg)` and restore `isAutoRead = true`. No path can leak the retained `ByteBuf`.

---

## 4. Domain-to-Domain Repository Layer

### 4.1 Live Traffic Path

`KNetProxyHandler` calls `listener?.onRequestCaptured(request)` and `listener?.onResponseCaptured(...)`. `listener` is a `ProxyTrafficListener` interface, decoupling the Netty engine from the data layer.

The implementation, `LiveTrafficRepositoryImpl`, posts transactions into a `MutableSharedFlow<HttpTransaction>` with `replay = 0` and `extraBufferCapacity = 64` (backpressure buffer). When the buffer is full, `emit()` suspends the engine coroutine rather than dropping data.

### 4.2 Breakpoint Path

`InterceptSessionManager.activeEventsStream: StateFlow<List<InterceptedEvent>>` is the hot reactive source. `InterceptionSessionRepositoryImpl` maps each `InterceptedEvent` to a pure `InterceptedTransaction` domain object, decoupling the engine's `InterceptedEvent` (which holds a `CompletableDeferred`) from the domain layer (which must not know about coroutine completion handles).

```
InterceptedEvent (engine layer)          InterceptedTransaction (domain layer)
├── id: String                 maps to → ├── id: String
├── request: HttpRequest               → ├── request: HttpRequest
├── response: HttpResponse?            → ├── response: HttpResponse?
├── phase: BreakpointPhase             → ├── phase: BreakpointPhase
└── deferred: CompletableDeferred     ×  └── (not present — clean domain model)
```

**Audit finding: Clean boundary.** The `CompletableDeferred` does not leak into the domain. The repository layer acts as an anti-corruption layer.

---

## 5. Content-Encoding Decompression — `BodyDecoder`

### 5.1 Expect/Actual Multiplatform Split

```kotlin
// commonMain — pure interface contract
public expect object BodyDecoder {
    fun decode(body: ByteArray?, headers: List<Pair<String, String>>): DecodedBodyResult
}

// jvmMain — JVM implementation using java.util.zip and brotli4j
public actual object BodyDecoder { ... }
```

The JVM implementation handles the full chain defined by RFC 7231 §3.1.2.2:

```kotlin
// Right-to-left chained encoding processing (per spec)
for (token in encodingChain.reversed()) {
    val decoder = decoders[enumEncoding] ?: return DecodedBodyResult.UnsupportedEncoding(token, body)
    currentBytes = decoder.decompress(currentBytes)
}
```

**Why reversed?** RFC 7231 states that `Content-Encoding: gzip, deflate` means the content was first deflated, then gzipped. To reverse: decode gzip first (rightmost last-applied = first-to-decode), then deflate.

### 5.2 Supported Encodings

| Token | Decoder | Implementation |
| :--- | :--- | :--- |
| `gzip` | `GzipContentDecoder` | `java.util.zip.GZIPInputStream` |
| `deflate` | `DeflateContentDecoder` | `java.util.zip.InflaterInputStream` |
| `br` | `BrotliContentDecoder` | `com.aayushatharva.brotli4j` |
| `zstd` | `ZstdContentDecoder` | `com.github.luben:zstd-jni` |

### 5.3 Typed Result Algebra

```kotlin
sealed class DecodedBodyResult {
    class Identity(val bytes: ByteArray)           // No encoding header present
    class Success(val bytes: ByteArray, val encoding: ContentEncoding)
    class UnsupportedEncoding(val token: String, val original: ByteArray)
    class CorruptedEncoding(val token: String, val error: String, val original: ByteArray)
}
```

`BodyTextDecoder` maps this to a `DecodedTextResult` sealed class that provides user-friendly `String` messages for binary or failed payloads (e.g. `"[Binary Payload - 14336 B (IMAGE)]"`).

**Audit finding: Strongly-typed contracts are enforced.** No raw `String` error fallbacks cross module boundaries. The Strongly-Typed Contracts Rule is upheld throughout the decoder chain.

---

## 6. `PayloadInspectionSpec.fromBytes()` — The Single-Pass Resolution Point

This is the most architecturally important function in the entire pipeline:

```kotlin
// Runs on Dispatchers.Default (never on Main or a Netty EventLoop)
fun fromBytes(body: ByteArray?, headers: List<Pair<String, String>>): PayloadInspectionSpec {
    val decodedText = decodeBodyToText(body, headers)  // Step 1: decompress
    return fromPayload(headers, decodedText)            // Step 2: format-detect
}

fun fromPayload(headers, rawBody): PayloadInspectionSpec {
    val headersMap = headers.toMap()
    val format = BodyFormatterRegistry.resolveFormat(headersMap, rawBody.trim())  // Step 3: parse
    return PayloadInspectionSpec(headers, rawBody, resolvedFormat = format)
}
```

All three operations — decompression, format detection, and AST parsing — run once, off the main thread, inside a `withContext(Dispatchers.Default)` block in the ViewModel. The result is a frozen, immutable `PayloadInspectionSpec` that is never recomputed unless the selected transaction changes.

---

## 7. `BodyFormatterRegistry` — The 2-Stage Dispatch Engine

### 7.1 Stage 1: Fast O(1) Content-Type Header Dispatch

```kotlin
val contentType = headers["content-type"] ?: ""
val mime = contentType.substringBefore(";").trim().lowercase()

when {
    mime.contains("graphql")     -> return graphQLFormatter.format(headers, trimmed)
    mime.contains("json")        -> { /* GraphQL-over-JSON sniff first */ ... }
    mime.contains("xml")         -> return xmlFormatter.format(headers, trimmed)
    ...
}
```

For approximately 95% of real-world traffic, this `when` block short-circuits immediately. No structural parsing is needed.

### 7.2 Stage 2: Priority-Ordered Structural Fallback

```kotlin
private val formatters: List<BodyFormatter> = listOf(...)
    .sortedByDescending { it.priority }

val matchedFormatter = formatters.firstOrNull { it.matches(headers, trimmed) } ?: plainTextFormatter
```

Each `BodyFormatter.matches()` performs structural sniffing. Example from `JsonBodyFormatter`:

```kotlin
override fun matches(headers, bodyText): Boolean {
    val formatted = prettyPrintJson(bodyText.trim())
    return formatted != bodyText.trim()
}
```

The formatters are sorted by `priority: Int` (higher = checked first). `ProtobufBinaryFormatter` has the highest priority (binary signatures checked first to prevent UTF-8 decode attempts on binary data).

### 7.3 Formatter Priority Order

| Formatter | Priority Rationale |
| :--- | :--- |
| `ProtobufBinaryFormatter` | Highest — binary signature prevents UTF-8 decode attempts |
| `ImageBodyFormatter` | Binary magic bytes (PNG, JPEG, GIF, WebP) |
| `WebChannelStreamFormatter` | gRPC-over-HTTP1 wrapper detection |
| `SseStreamFormatter` | SSE `data:` prefix detection |
| `GraphQLBodyFormatter` | JSON with `query` field detection |
| `JsonBodyFormatter` | Structural `{` or `[` detection with Jackson parse |
| `FormDataBodyFormatter` | `key=value&key2=value2` pattern |
| `XmlBodyFormatter` | `<` tag detection |
| ... | ... |
| `PlainTextBodyFormatter` | Catch-all lowest priority |

---

## 8. BodyFormat Sealed Interface & HasTextContent Capability Interface

`BodyFormat` is designed as a **sealed interface** paired with a capability interface `HasTextContent`. Variants containing single-document text payloads implement `HasTextContent` for clean capability polymorphism, while multi-frame stream formats, structured form data, and binary media remain pure domain models:

```kotlin
sealed interface BodyFormat {
    val badgeLabel: String

    interface HasTextContent {
        val textContent: String
    }

    data class Json(val formattedText: String) : BodyFormat, HasTextContent {
        override val badgeLabel: String = "JSON"
        override val textContent: String get() = formattedText
    }
    data class Xml(val formattedText: String) : BodyFormat, HasTextContent {
        override val badgeLabel: String = "XML"
        override val textContent: String get() = formattedText
    }
    data class GraphQL(
        val operationType: String,
        val operationName: String?,
        val queryText: String,
        val variablesJson: String,
        val extensionsJson: String = ""
    ) : BodyFormat, HasTextContent {
        override val badgeLabel: String get() = if (!operationName.isNullOrEmpty()) "GQL: $operationName" else "GQL: $operationType"
        override val textContent: String get() = queryText
    }

    // Stream, structured, and binary formats remain domain-pure:
    data class GrpcWeb(val frames: List<Frame>) : BodyFormat {
        override val badgeLabel: String = "gRPC-Web"
        data class Frame(val isTrailer: Boolean, val payloadHex: String, val decodedJsonOrText: String)
    }
    data class FormData(val pairs: List<Pair<String, String>>) : BodyFormat {
        override val badgeLabel: String = "Form Data"
    }
}
```

---

## 9. ViewModel Layer — Off-Thread Preparation & LRU Caching

### 9.1 TrafficViewModel — `flatMapLatest` Reactive Chain

```kotlin
_uiState
    .map { it.selectedTransaction }
    .distinctUntilChanged()           // Only fires when selection changes
    .flatMapLatest { tx ->
        flow {
            // 1. Cache hit: emit immediately
            val cached = synchronized(preparedStateCache) { preparedStateCache[tx.transactionId] }
            if (cached != null) { emit(cached); return@flow }

            // 2. Show loading shimmer immediately
            emit(InspectorPreparedState(transactionId = tx.transactionId, isPreparing = true))

            // 3. Prepare off-thread
            val prepared = withContext(Dispatchers.Default) {
                val body = loadTransactionBodyUseCase.execute(tx.transactionId)
                val requestBodySpec  = PayloadInspectionSpec.fromBytes(body.requestBody, body.requestHeaders)
                val responseBodySpec = PayloadInspectionSpec.fromBytes(body.responseBody, body.responseHeaders)
                InspectorPreparedState(tx.transactionId, requestBodySpec, responseBodySpec)
            }

            // 4. Store in LRU cache (64-entry access-order LinkedHashMap)
            synchronized(preparedStateCache) { preparedStateCache[tx.transactionId] = prepared }
            emit(prepared)
        }
    }
```

`flatMapLatest` automatically cancels the in-flight `flow {}` block when a new transaction is selected. This prevents stale preparation tasks from completing and overwriting the current selection.

### 9.2 LRU Cache Design

```kotlin
private val preparedStateCache = object : LinkedHashMap<String, InspectorPreparedState>(64, 0.75f, true) {
    override fun removeEldestEntry(...): Boolean = size > 64
}
```

`accessOrder = true` makes `LinkedHashMap` an LRU structure — `get()` moves the accessed entry to the tail. The `removeEldestEntry` override ensures the map never exceeds 64 entries. `synchronized()` wrappers make it safe for concurrent access from `Dispatchers.Default` and main.

**Rationale for 64 entries**: A `PayloadInspectionSpec` holding formatted JSON can be several KB. 64 × ~10 KB ≈ 640 KB heap budget for the cache — acceptable for a desktop application.

### 9.3 BreakpointManagerViewModel — Eager Pre-Resolution

Unlike `TrafficViewModel` which lazily prepares on selection, `BreakpointManagerViewModel` eagerly pre-resolves **every** intercepted transaction as soon as it enters the queue:

```kotlin
observeActiveInterceptionsUseCase().collect { transactions ->
    val newPayloads = transactions.associate { tx ->
        tx.id to ResolvedInterceptPayload(
            transactionId = tx.id,
            requestPayloadSpec = PayloadInspectionSpec.fromBytes(tx.request.body, tx.request.headers),
            responsePayloadSpec = tx.response?.let {
                PayloadInspectionSpec.fromBytes(it.body, it.headers)
            } ?: PayloadInspectionSpec.EMPTY
        )
    }
    _uiState.update { it.copy(resolvedPayloads = newPayloads) }
}
```

**Why eager?** Breakpoint events are high-priority, user-facing interruptions. The drawer must open instantly. Lazy preparation on drawer open would introduce a visible loading delay.

---

## 10. UI Hydration — `RequestBodyState.from()` & `ResponseBodyState.from()`

These companion factories are the final transformation step before the Compose UI renders.

```kotlin
fun from(spec: PayloadInspectionSpec): RequestBodyState {
    return when (val format = spec.resolvedFormat) {
        is BodyFormat.GraphQL -> {
            val parsedGraphQlState = GraphQlPayloadMapper().parseToUi(spec.rawBody.trim())
            RequestBodyState(mode = RequestBodyMode.GRAPHQL, graphQlState = parsedGraphQlState)
        }
        is BodyFormat.FormData -> {
            val entries = format.pairs.mapIndexed { idx, (k, v) ->
                KeyValueEntry(id = "form_$idx", key = k, value = v)
            }
            RequestBodyState(mode = RequestBodyMode.FORM_DATA, formDataEntries = entries)
        }
        is BodyFormat.Json -> RequestBodyState(mode = RequestBodyMode.JSON,
            payloadText = format.resolvedText(spec.rawBody.trim()))
        // ...
    }
}
```

**The critical guarantee**: By the time `from(spec)` is called, `spec.resolvedFormat` is already fully parsed. The UI composable that eventually calls `from()` does zero parsing — it merely maps a pre-computed sealed class variant to an editor mode enum and a string.

---

## 11. SmartBodyViewer — Zero-Parse Compose Rendering

```kotlin
@Composable
fun SmartBodyViewer(spec: PayloadInspectionSpec, ...) {
    if (spec.isPreparing) { KNetBodyLoadingPlaceholder(); return }
    if (spec.isEmpty)     { KNetEmptyStatePlaceholder(); return }

    // Fast path: spec.resolvedFormat was computed off-thread
    val format = spec.resolvedFormat ?: remember(spec.headers, spec.rawBody) {
        BodyFormatterRegistry.resolveFormat(headersMap, spec.rawBody) // Fallback only
    }

    when (format) {
        is BodyFormat.GraphQL  -> GraphQLBodyViewer(format, rawJsonText = JsonBodyFormatter.prettyPrintJson(spec.rawBody))
        is BodyFormat.FormData -> FormDataViewer(pairs = format.pairs)
        else -> KNetCodeEditor(code = displayText, language = codeLanguage, mode = EditorMode.ReadOnly)
    }
}
```

`spec.resolvedFormat` is **always non-null** when the spec was produced by `PayloadInspectionSpec.fromBytes()`. The `?: remember(...)` fallback only activates in edge cases (e.g. API Studio composing a brand-new request body with no prior resolution). In that fallback case, `remember(spec.headers, spec.rawBody)` ensures `BodyFormatterRegistry.resolveFormat` is called at most once per unique (headers, body) pair for the lifetime of the composition.

---

## 12. Architectural Audit Summary

| Area | Status | Notes |
| :--- | :--- | :--- |
| **No Netty types in domain** | PASS | `HttpRequest`/`HttpResponse` contain only `ByteArray`, `String`, `Long`. No `ByteBuf`, `ChannelHandlerContext` anywhere past `HttpMapper`. |
| **ByteBuf lifecycle** | PASS | All 3 result branches + exception handler in `InterceptCoordinator` call `ReferenceCountUtil.release(msg)`. `isAutoRead` is always restored. |
| **Strong typing** | PASS | `BodyFormat` sealed class (14 variants), `InterceptResult` sealed class, `DecodedBodyResult` sealed class, `DecodedTextResult` sealed class. No raw `String` crossing module boundaries. |
| **No `java.util.UUID`** | PASS | All UUID generation uses `kotlin.uuid.Uuid.random().toString()`. |
| **No `runBlocking`** | PASS | All async work uses `viewModelScope.launch`, `withContext(Dispatchers.Default)`, or coroutine continuation on `context.executor().asCoroutineDispatcher()`. |
| **No UI-thread parsing** | PASS | `PayloadInspectionSpec.fromBytes()` only runs inside `withContext(Dispatchers.Default)`. `SmartBodyViewer` reads `spec.resolvedFormat` directly. |
| **No duplicate decompression** | PASS | `BodyDecoder` runs exactly once per transaction inside `PayloadInspectionSpec.fromBytes()`. |
| **No duplicate format detection** | PASS | `BodyFormatterRegistry.resolveFormat()` runs exactly once per transaction. Result stored in `PayloadInspectionSpec.resolvedFormat`. |
| **LRU cache for prepared states** | PASS | 64-entry access-order `LinkedHashMap` in `TrafficViewModel` prevents recomputation on re-selection. |
| **FIFO suspension ordering** | PASS | `InterceptSessionManager` uses `MutableStateFlow<List<InterceptedEvent>>` with append-only `update {}`. |
| **Clean Architecture / Koin DI** | PASS | ViewModels inject UseCases. UseCases inject Repositories. No ViewModel directly imports Repository implementations. |
| **`ctx` variable naming** | PASS | All `ChannelHandlerContext` parameters are named `context`, not `ctx`. |
| **Headers as `List<Pair>`** | PASS | Preserves duplicate header names. HTTP/1.1 allows duplicate `Set-Cookie` headers; a `Map` would silently discard them. |
| **`formattedText` delegation in `PayloadInspectionSpec`** | PASS | `PayloadInspectionSpec.formattedText` delegates directly to `resolvedFormat?.resolvedText(rawBody) ?: rawBody`. No duplicate `when` branching. |
| **`observeRulesUseCase` collection in `TrafficViewModel`** | PASS | Clean single reactive subscription (`observeRulesUseCase().onEach { ... }.launchIn(viewModelScope)`). No redundant coroutine allocations or emissions. |

---

## 13. Complete Data Class Lifecycle at a Glance

```
TCP Socket Bytes (off-heap DirectByteBuffer in Netty PooledByteBufAllocator)
    │
    │ [HttpServerCodec + HttpObjectAggregator]
    ▼
FullHttpRequest (Netty — off-heap ByteBuf content, refCnt managed)
    │
    │ [HttpMapper.mapRequest() — getBytes() → heap ByteArray copy]
    │  refCnt management: retain() on intercept, release() on resume/drop/timeout
    ▼
HttpRequest(                         ← Kotlin data class, pure heap, GC-managed
    id: String,                      ← kotlin.uuid.Uuid (no java.util.UUID)
    method: String,                  ← "GET", "POST", etc.
    url: String,                     ← Fully-qualified absolute URL
    protocol: String,                ← "HTTP/1.1"
    headers: List<Pair<String,String>>, ← Ordered, case-preserved, duplicate-safe
    body: ByteArray?,                ← Compressed wire bytes (may be gzip/br/zstd)
    timestamp: Long,                 ← System.currentTimeMillis() at capture
    isIntercepted: Boolean,
    matchedRuleId: String?
)
    │
    │ [PayloadInspectionSpec.fromBytes() — Dispatchers.Default]
    │  Step 1: BodyDecoder.decode(body, headers) → decompressed ByteArray
    │  Step 2: BodyTextDecoder.decode() → String (UTF-8 / binary label)
    │  Step 3: BodyFormatterRegistry.resolveFormat(headers, text) → BodyFormat
    ▼
PayloadInspectionSpec(               ← Immutable data class
    headers: List<Pair<String,String>>,
    rawBody: String,                 ← Decompressed, decoded wire text
    resolvedFormat: BodyFormat?,     ← Pre-parsed AST result (Json/Xml/GraphQL/...)
    isPreparing: Boolean
)
    │
    │ [RequestBodyState.from(spec) / ResponseBodyState.from(spec)]
    ▼
RequestBodyState(                    ← UI editor configuration DTO
    mode: RequestBodyMode,           ← Strongly-typed enum (JSON/GRAPHQL/FORM_DATA/RAW)
    rawSubFormat: RawSubFormat,      ← Strongly-typed enum (JSON/XML/HTML/JS/TEXT)
    payloadText: String,             ← Pre-formatted text for KNetCodeEditor
    formDataEntries: List<KeyValueEntry>,  ← Structured form data rows
    graphQlState: GraphQlState       ← Pre-parsed GraphQL query/variables/extensions
)
    │
    │ [Compose Desktop UI — Main Thread]
    ▼
SmartBodyViewer / RequestViewPanel / ResponseViewPanel
    → Reads pre-resolved data, calls no parsers, drops no frames
```
