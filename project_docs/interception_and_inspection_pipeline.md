# KNet Interception & Body Inspection Pipeline Architecture

This document provides a comprehensive technical reference for the single-pass, zero-duplication payload processing and formatting pipeline across KNet's Live Traffic Inspector and Breakpoint Interception suites.

---

## 1. Architectural Philosophy: "Detect Once, Flow Everywhere"

Before this architecture, format detection and byte decompression were executed multiple times across different layers (Netty interceptor, Domain mapping, Live Intercept Drawer, and Traffic Inspector composables).

The frozen architecture enforces three strict rules:
1. **Single-Pass Resolution**: Raw wire bytes are decompressed (gzip/deflate/br) and format-detected ([`BodyFormat`](file:///Users/devuloopers/Development/KNet/engine/formatter/src/main/kotlin/com/devuloopers/knet/engine/formatter/model/BodyFormat.kt)) **exactly once off-thread**.
2. **Canonical State Carrier**: [`PayloadInspectionSpec`](file:///Users/devuloopers/Development/KNet/ui/desktop/httpPanel/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/httppanel/model/PayloadInspectionSpec.kt) is the sole unified container carrying raw text, headers, resolved `BodyFormat`, and loading state across all presentation layers.
3. **Zero UI-Thread Parsing**: UI Composables never parse bytes or invoke format registries during Compose rendering. They consume pre-resolved state directly.

---

## 2. Complete End-to-End Flow Diagram

```
[ INBOUND NETWORK TRAFFIC ]
           │
           ▼
[ Netty Event Loop ]: KNetProxyHandler / KNetInterceptorHandler
           │
           ├──────────────────────────────┬──────────────────────────────┐
           │ (Matches Breakpoint Rule)    │ (Normal Live Traffic)        │
           ▼                              ▼                              ▼
 [ InterceptSessionManager ]    [ InterceptCoordinator ]       [ LiveTrafficRepositoryImpl ]
           │ (CompletableDeferred)        │                              │
           ▼                              │                              ▼
 [ InterceptionSessionRepositoryImpl ]    │                     [ TrafficViewModel ]
           │ (InterceptedTransaction)     │                              │
           ▼                              │                              │ (Dispatchers.Default)
 [ BreakpointManagerViewModel ]           │                              ▼
           │ (ioDispatcher)               │                 PayloadInspectionSpec.fromBytes()
           ▼                              │                              │
 PayloadInspectionSpec.fromBytes()           │                              ▼
           │                              │                     InspectorPreparedState
           ▼                              │                              │
 BreakpointManagerState.resolvedPayloads  │                              ▼
           │                              │                     TrafficInspectorPanel
           ▼                              │                              │
   LiveInterceptDrawer                    │                     RequestViewPanel / ResponseViewPanel
           │                              │                              │
  RequestBodyState.from()                  │                              ▼
  ResponseBodyState.from()                 │                       SmartBodyViewer (Instant Render)
            │                              │
            ▼ (User Edits / Forwards)      │
  [ Resume / Drop Signal ] ────────────────┘
```

---

## 3. Step-by-Step Flow Details

### Path A: Live Breakpoint Interception (Editable Path)

#### Step 1: Netty Capture & Suspension
- **Class**: [`com.devuloopers.knet.engine.interceptor.KNetInterceptorHandler`](file:///Users/devuloopers/Development/KNet/engine/interceptor/src/main/kotlin/com/devuloopers/knet/engine/interceptor/KNetInterceptorHandler.kt)
- **Action**: Intercepts `FullHttpRequest` or `FullHttpResponse`. Evaluates matching rules via `BreakpointMatcher.matches()`.
- **Class**: [`com.devuloopers.knet.engine.interceptor.InterceptSessionManager`](file:///Users/devuloopers/Development/KNet/engine/interceptor/src/main/kotlin/com/devuloopers/knet/engine/interceptor/InterceptSessionManager.kt)
- **Function**: `suspendRequest(request: HttpRequest)` or `suspendResponse(request: HttpRequest, response: HttpResponse)`
- **Mechanism**: Creates an `InterceptedEvent` with a `CompletableDeferred<InterceptResult>`. Suspends the Netty pipeline coroutine on `context.executor().asCoroutineDispatcher()`.

#### Step 2: Domain Transaction Mapping
- **Class**: [`com.devuloopers.knet.data.desktop.rules.repository.InterceptionSessionRepositoryImpl`](file:///Users/devuloopers/Development/KNet/data/desktop/src/jvmMain/kotlin/com/devuloopers/knet/data/desktop/rules/repository/InterceptionSessionRepositoryImpl.kt)
- **Property**: `activeInterceptions: Flow<List<InterceptedTransaction>>`
- **Action**: Maps in-flight `InterceptedEvent` records into pure domain [`InterceptedTransaction`](file:///Users/devuloopers/Development/KNet/core/domain/src/commonMain/kotlin/com/devuloopers/knet/domain/rules/model/InterceptedTransaction.kt) objects, enriching them with protocol metadata (e.g. GraphQL operation name and type).

#### Step 3: Off-Thread Resolution in ViewModel
- **Class**: [`com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel.BreakpointManagerViewModel`](file:///Users/devuloopers/Development/KNet/ui/desktop/breakpointManager/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/breakpointmanager/viewmodel/BreakpointManagerViewModel.kt)
- **Function**: Listens to `observeActiveInterceptionsUseCase()`.
- **Action**: For every newly arrived transaction ID, executes:
  ```kotlin
  val requestPayloadSpec = PayloadInspectionSpec.fromBytes(
      body = tx.request.body,
      headers = tx.request.headers
  )
  val responsePayloadSpec = tx.response?.let { response ->
      PayloadInspectionSpec.fromBytes(
          body = response.body,
          headers = response.headers
      )
  } ?: PayloadInspectionSpec.EMPTY
  ```
- **State Output**: Stores the pre-resolved results in `BreakpointManagerState.resolvedPayloads: Map<String, ResolvedInterceptPayload>`.

#### Step 4: UI Drawer Presentation
- **Class**: [`com.devuloopers.knet.ui.desktop.breakpointmanager.components.LiveInterceptDrawer`](file:///Users/devuloopers/Development/KNet/ui/desktop/breakpointManager/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/breakpointmanager/components/LiveInterceptDrawer.kt)
- **Action**: Retrieves the pre-computed `ResolvedInterceptPayload` from `resolvedPayloads[eventToRender.id]`.
- **Functions**:
  - `RequestBodyState.from(preResolved.requestPayloadSpec)`
  - `ResponseBodyState.from(preResolved.responsePayloadSpec)`
- **Result**: Editor tabs, syntax highlighters, and form-data tables populate instantly without any blocking or re-parsing.

---

### Path B: Live Traffic Inspector (Read-Only Path)

#### Step 1: Transaction Selection
- **Class**: [`com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel`](file:///Users/devuloopers/Development/KNet/ui/desktop/traffic/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/traffic/viewmodel/TrafficViewModel.kt)
- **Function**: `selectedTransaction` stream triggers `flatMapLatest`.
- **Action**: Calls `loadTransactionBodyUseCase.execute(tx.transactionId)` on `Dispatchers.Default`.

#### Step 2: Single-Pass Resolution
- **Object**: [`com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec`](file:///Users/devuloopers/Development/KNet/ui/desktop/httpPanel/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/httppanel/model/PayloadInspectionSpec.kt)
- **Function**: `PayloadInspectionSpec.fromBytes(body: ByteArray?, headers: List<Pair<String, String>>)`
  1. Decompresses raw bytes via `decodeBodyToText(body, headers)`.
  2. Resolves format via `BodyFormatterRegistry.resolveFormat(headersMap, rawBody)`.
  3. Returns a complete `PayloadInspectionSpec`.

#### Step 3: Prepared State Emission
- **Class**: [`com.devuloopers.knet.ui.desktop.traffic.model.InspectorPreparedState`](file:///Users/devuloopers/Development/KNet/ui/desktop/traffic/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/traffic/model/InspectorPreparedState.kt)
- **Properties**:
  - `requestPayloadSpec: PayloadInspectionSpec`
  - `responsePayloadSpec: PayloadInspectionSpec`
  - `isPreparing: Boolean`

#### Step 4: Rendering in ViewPanels
- **Composables**:
  - [`RequestViewPanel(spec, payloadSpec = preparedState.requestPayloadSpec)`](file:///Users/devuloopers/Development/KNet/ui/desktop/httpPanel/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/httppanel/viewpanels/RequestViewPanel.kt)
  - [`ResponseViewPanel(spec, payloadSpec = preparedState.responsePayloadSpec)`](file:///Users/devuloopers/Development/KNet/ui/desktop/httpPanel/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/httppanel/viewpanels/ResponseViewPanel.kt)
  - [`SmartBodyViewer(spec = effectiveBodySpec)`](file:///Users/devuloopers/Development/KNet/ui/desktop/httpPanel/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/httppanel/components/SmartBodyViewer.kt)
- **Action**: `SmartBodyViewer` reads `spec.resolvedFormat` directly from the pre-computed spec.

---

## 4. Complete Component & API Catalog

| Layer | File / Symbol | Role & Description |
| :--- | :--- | :--- |
| **Engine** | [`BodyFormatterRegistry.kt`](file:///Users/devuloopers/Development/KNet/engine/formatter/src/main/kotlin/com/devuloopers/knet/engine/formatter/registry/BodyFormatterRegistry.kt) | Central registry of formatting strategies (JSON, XML, HTML, GraphQL, FormData, CBOR, Protobuf). |
| **Engine** | [`BodyFormat.kt`](file:///Users/devuloopers/Development/KNet/engine/formatter/src/main/kotlin/com/devuloopers/knet/engine/formatter/model/BodyFormat.kt) | Strongly-typed sealed hierarchy returned by `BodyFormatterRegistry.resolveFormat()`. |
| **Engine** | [`InterceptSessionManager.kt`](file:///Users/devuloopers/Development/KNet/engine/interceptor/src/main/kotlin/com/devuloopers/knet/engine/interceptor/InterceptSessionManager.kt) | In-memory FIFO queue of suspended Netty channels awaiting forward/drop actions. |
| **Domain** | [`BodyUtils.kt`](file:///Users/devuloopers/Development/KNet/core/domain/src/commonMain/kotlin/com/devuloopers/knet/domain/util/BodyUtils.kt) | Pure multiplatform `decodeBodyToText(body, headers)` byte decompressor. |
| **Domain** | [`InterceptedTransaction.kt`](file:///Users/devuloopers/Development/KNet/core/domain/src/commonMain/kotlin/com/devuloopers/knet/domain/rules/model/InterceptedTransaction.kt) | Pure domain model of a suspended in-flight transaction with rich metadata. |
| **UI Model**| [`PayloadInspectionSpec.kt`](file:///Users/devuloopers/Development/KNet/ui/desktop/httpPanel/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/httppanel/model/PayloadInspectionSpec.kt) | Canonical carrier holding `headers`, `rawBody`, `resolvedFormat: BodyFormat?`, and `isPreparing`. |
| **UI Model**| [`RequestBodyModels.kt`](file:///Users/devuloopers/Development/KNet/ui/desktop/httpPanel/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/httppanel/model/RequestBodyModels.kt) & [`ResponseBodyModels.kt`](file:///Users/devuloopers/Development/KNet/ui/desktop/httpPanel/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/httppanel/model/ResponseBodyModels.kt) | `RequestBodyState.from(spec)` & `ResponseBodyState.from(spec)` factories. |
| **UI Model**| [`InspectorPreparedState.kt`](file:///Users/devuloopers/Development/KNet/ui/desktop/traffic/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/traffic/model/InspectorPreparedState.kt) | Traffic inspector state carrying pre-resolved `requestPayloadSpec` and `responsePayloadSpec`. |
| **UI Model**| [`ResolvedInterceptPayload.kt`](file:///Users/devuloopers/Development/KNet/ui/desktop/breakpointManager/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/breakpointmanager/model/ResolvedInterceptPayload.kt) | In-flight transaction payload carrier holding `requestPayloadSpec` and `responsePayloadSpec`. |
| **ViewModel**| [`TrafficViewModel.kt`](file:///Users/devuloopers/Development/KNet/ui/desktop/traffic/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/traffic/viewmodel/TrafficViewModel.kt) | Off-thread payload resolution on transaction selection via `PayloadInspectionSpec.fromBytes()`. |
| **ViewModel**| [`BreakpointManagerViewModel.kt`](file:///Users/devuloopers/Development/KNet/ui/desktop/breakpointManager/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/breakpointmanager/viewmodel/BreakpointManagerViewModel.kt) | Off-thread payload resolution on event arrival via `PayloadInspectionSpec.fromBytes()`. |
| **UI View** | [`LiveInterceptDrawer.kt`](file:///Users/devuloopers/Development/KNet/ui/desktop/breakpointManager/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/breakpointmanager/components/LiveInterceptDrawer.kt) | Master-detail live interception slide-out drawer rendering pre-resolved state. |
| **UI View** | [`RequestViewPanel.kt`](file:///Users/devuloopers/Development/KNet/ui/desktop/httpPanel/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/httppanel/viewpanels/RequestViewPanel.kt) | Universal HTTP request panel consuming `payloadSpec: PayloadInspectionSpec? = null`. |
| **UI View** | [`ResponseViewPanel.kt`](file:///Users/devuloopers/Development/KNet/ui/desktop/httpPanel/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/httppanel/viewpanels/ResponseViewPanel.kt) | Universal HTTP response panel consuming `payloadSpec: PayloadInspectionSpec? = null`. |
| **UI View** | [`SmartBodyViewer.kt`](file:///Users/devuloopers/Development/KNet/ui/desktop/httpPanel/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/httppanel/components/SmartBodyViewer.kt) | Polymorphic body viewer rendering based directly on `spec.resolvedFormat`. |
| **UI Mapper**| [`GraphQlPayloadMapper.kt`](file:///Users/devuloopers/Development/KNet/ui/desktop/httpPanel/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/httppanel/mapper/GraphQlPayloadMapper.kt) | Maps between raw JSON GraphQL wire format and UI `GraphQlState` via `parseToUi()` and `serializeFromUi()`. |

---

## 5. How to Add a New Formatter in the Future

When adding support for a new data format (e.g. `Avro`, `MessagePack`, `NDJSON`, `SSE`):

### Step 1: Create the Formatter
In `:engine:formatter:formatters`, create `MyFormatBodyFormatter.kt` implementing `BodyFormatter`.
Define the corresponding variant in `BodyFormat` sealed class (e.g. `data class MyFormat(val formattedText: String) : BodyFormat()`).

### Step 2: Register in Registry
In [`BodyFormatterRegistry.kt`](file:///Users/devuloopers/Development/KNet/engine/formatter/src/main/kotlin/com/devuloopers/knet/engine/formatter/registry/BodyFormatterRegistry.kt), register your formatter in the detection list.

### Step 3: Add Viewer Branch
In [`SmartBodyViewer.kt`](file:///Users/devuloopers/Development/KNet/ui/desktop/httpPanel/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/httppanel/components/SmartBodyViewer.kt) and [`BodyModels.kt`](file:///Users/devuloopers/Development/KNet/ui/desktop/httpPanel/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/httppanel/model/BodyModels.kt), add a `when` branch for `is BodyFormat.MyFormat`.

**No changes are required in ViewModels, Repositories, or Netty interceptors.**
The pipeline will automatically decode, resolve, cache, and render the new format across both Traffic Inspector and Breakpoint Interception suites.
