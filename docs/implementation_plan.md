# KNet Implementation Plan [COMPLETED] - Feature Module: `inspector` (Transaction Inspector)

This document outlines the end-to-end, phase-by-phase plan to build KNet, a modern, Kotlin-first HTTP/HTTPS debugging proxy. The objective is to achieve feature parity with industry standards like Charles Proxy and mitmproxy while utilizing Netty, Kotlin Coroutines, and Compose Multiplatform.

---

## Features to Replicate (KNet Feature Scope)

To ensure nothing is missed, KNet will support:
1. **Traffic Decryption (MITM)**: On-the-fly leaf certificate generation, Root CA installer, client certificate support.
2. **Visual Inspection**: Flow (list) view, Structure (domain tree) view, and rich viewers (JSON, XML, Protobuf, Hex, Multipart, Cookies, Headers).
3. **Interception & Breakpoints**: Asynchronously pausing requests/responses to manually edit headers, parameters, and bodies.
4. **Traffic Rules**: Rewrite rules (regex find-and-replace), Map Local (serving local files), Map Remote (redirecting upstream).
5. **Network Simulation**: Bandwidth throttling, latency injection, packet loss simulation.
6. **Advanced Protocols**: HTTP/1.x, HTTP/2, WebSockets frame inspection, and gRPC/Protobuf decoding.
7. **Replay & Repeat**: Retransmit requests, batch replays, and concurrent request stress testing.
8. **Extensibility**: Kotlin scripting (.kts rules) and a modular plugin API.
9. **Network Integration**: Upstream proxying (VPN/corporate proxy chaining) and reverse proxy support.

---

## Architectural Decisions and Recommendations

### 1. Database and Payload Storage Policy
To prevent SQLite database bloat when capturing massive amounts of traffic:
* **Metadata**: All HTTP request/response metadata (methods, headers, URLs, status codes, timestamps, connection metrics) will be persisted in the Room SQLite database.
* **Payload Bodies**: Request and response body bytes will be saved as separate files in a dedicated temporary cache directory. The Room database will only store the file path reference to these payloads.

### 2. HTTP/3 (QUIC) Scope
* **Decision**: HTTP/3 support will be deferred and treated as a future research extension. Netty support for HTTP/3 requires experimental native libraries (`netty-incubator-codec-http3`) that introduce platform-specific compiling issues, which conflicts with standard cross-platform JVM packaging. The engine will fallback to HTTP/2 or HTTP/1.1 for HTTP/3 requests.

### 3. Non-Blocking Pipeline Interception
* **Decision**: To avoid blocking Netty event loop threads during traffic interception, KNet will use a coroutine suspension pipeline. When an interception rule triggers, the Netty handler will turn off auto-read on the connection (`context.channel().config().setAutoRead(false)`) and wait on a suspended `CompletableDeferred` state. Once the user acts in the UI, the state is resolved, auto-read is re-enabled, and the packet is processed.

### 4. Modular Initialization Strategy
* **Decision**: We will not initialize all modules at the start of the project. To keep build configurations minimal, easy to manage, and avoid IDE indexing overhead, we will create and register Gradle modules iteratively phase-by-phase as we reach their corresponding development stages. We will reuse the existing `shared` module in the project root as our common layer, rather than creating a new `common` module. We will use a dedicated `:logger` module to centralize KMP-wide logging.

---

## Phase-by-Phase Development Plan

### Phase 1: Cryptography & CA Management (certificateManager) [COMPLETED]
Establish the security baseline required to decrypt SSL/TLS traffic.
* **Modules modified/created**: `shared` (reused as common DTO/core layer), `certificateManager` (new)
* **Tasks**:
  * Generate a Root CA certificate and private key (RSA 2048/4096-bit). [COMPLETED]
  * Build a dynamic leaf certificate generator using BouncyCastle based on the client's requested SNI (Server Name Indication). [COMPLETED]
  * Implement an in-memory CA certificate cache to minimize dynamic signing latency. [COMPLETED]
  * Add automatic OS trust store installation support for Windows, macOS, and Linux using platform-specific shell scripts. [COMPLETED]

---

### Phase 2: Core Asynchronous Proxy Engine (proxyEngine) [COMPLETED]
Build the foundation of the Netty server capable of forwarding plain HTTP and decrypting HTTPS traffic.
* **Modules created**: `proxyEngine` (new), `logger` (new)
* **Tasks**:
  * Implement Netty ServerSocketChannel to accept incoming proxy requests. [COMPLETED]
  * Handle the HTTP CONNECT tunnel handshake for HTTPS. [COMPLETED]
  * Integrate Netty SSL handlers (SslContext, SslHandler) using dynamically generated certificates from certificateManager. [COMPLETED]
  * Create core models (HttpRequest, HttpResponse) in `shared` module. [COMPLETED]
  * Build non-blocking client connection logic using Netty to forward traffic to remote servers. [COMPLETED]

---

### Phase 3: Non-Blocking Interception & Pipelines (interceptor) [COMPLETED]
Introduce the ability to catch traffic and halt it safely without blocking Netty event loop threads.
* **Modules created**: `interceptor` (new)
* **Tasks**:
  * Implement a Netty channel handler that intercepts requests/responses. [COMPLETED]
  * Build the Coroutines-based pause system using CompletableDeferred and setAutoRead(false) backpressure. [COMPLETED]
  * Define the Breakpoint engine that flags matching requests and routes them to a state holder. [COMPLETED]
  * Enable manual editing of headers, cookies, query parameters, and request/response bodies. [COMPLETED]

---

### Phase 4: Storage, Workspaces & Sessions (storage & sessionManager) [COMPLETED]
Provide persistence so users can save, export, load, and analyze historical proxy sessions.
* **Modules created**: `storage` (new), `sessionManager` (new)
* **Tasks**:
  * Configure Android Room for SQLite storage on the JVM desktop target. [COMPLETED]
  * Build schema to serialize HTTP requests, responses, headers, and metadata. [COMPLETED]
  * Implement sessionManager to manage in-memory capture buffers, session states, and disk flushing. [COMPLETED]
  * Implement HAR (HTTP Archive) import/export and cURL command generators. [COMPLETED]

---

### Phase 5: Traffic Rules (trafficModifier) [COMPLETED]
Implement automated rules to alter traffic on the fly. See design details in [docs/phase_5_plan.md](file:///c:/Users/Anant.gupta/IdeaProjects/KNet/docs/phase_5_plan.md).
* **Modules created**: `trafficModifier` (new)
* **Tasks**:
  * **Map Local**: Intercept requests matching a regex and return a local file as the response body. [COMPLETED]
  * **Map Remote**: Intercept requests and forward them to a different host/port/protocol. [COMPLETED]
  * **Rewrite Rules**: Define rules to modify headers, status codes, query parameters, or body contents based on regex match-and-replace patterns. [COMPLETED]

---

### Phase 6: Network Simulation & Throttling (networkSimulator) [COMPLETED]
Simulate mobile and poor network conditions. See design details in [docs/phase_6_plan.md](file:///c:/Users/Anant.gupta/IdeaProjects/KNet/docs/phase_6_plan.md).
* **Modules created**: `networkSimulator` (new)
* **Tasks**:
  * Bandwidth throttling via Netty `ChannelTrafficShapingHandler` (per-channel). [COMPLETED]
  * Latency injection via event-loop scheduled delays. [COMPLETED]
  * Packet loss simulation via random drop percentage. [COMPLETED]

---

### Phase 7: Advanced Protocol Support (protocolInspector) [COMPLETED]
Expand KNet's parsing capabilities to modern protocols. See design details in [docs/phase_7_plan.md](file:///c:/Users/Anant.gupta/IdeaProjects/KNet/docs/phase_7_plan.md).
* **Modules created**: `protocolInspector` (new)
* **Tasks**:
  * **WebSockets**: Intercept the HTTP upgrade handshake and inject frame handlers (WebSocketFrameDecoder/Encoder) to log, inspect, and replay individual frames. [COMPLETED]
  * **HTTP/2**: Decode multiplexed HTTP/2 streams using Netty's codec-http2, preserving stream frames. [COMPLETED]
  * **gRPC & Protobuf**: Integrate Protobuf parser to decode binary payloads when schema files (.proto) are provided by the user. [COMPLETED]

---

### Phase 8: Compose Desktop Presentation Layer (desktopApp) [COMPLETED]
Create a high-fidelity visual layout based on a dynamic modular grid architecture. Each visual feature acts as an independent widget inside a layout container that supports dynamic addition, removal, and future resizing. See design details in [docs/phase_8_plan.md](file:///c:/Users/Anant.gupta/IdeaProjects/KNet/docs/phase_8_plan.md).
* **Modules modified**: `shared` (common UI packages), `desktopApp` (JVM runner)
* **Tasks**:
  * **Widget Catalog**: Create WidgetType, WidgetFrame, and SubFrame definitions. [COMPLETED]
  * **Live Traffic Feed Widget**: List table view grouped by date chips. [COMPLETED]
  * **Selected Transaction Overview Widget**: Metadata headers and Forward/Drop/Edit action triggers. [COMPLETED]
  * **Request Details & JSON Widgets**: Query parameters tree view, raw request headers, and request body JSON code block formatting. [COMPLETED]
  * **Response Inspector Widgets**: Header collections, cookie maps, and response body pretty code block. [COMPLETED]
  * **Connection Timings Widget**: Visual timing bar graphs for DNS, TCP, and TLS. [COMPLETED]
  * **Rules Console Widget**: Interactive table list of breakpoint and header rewrite parameters. [COMPLETED]
  * **Quick Replay & Tags Widgets**: Replay batch trigger inputs, tags list, and notes comments section. [COMPLETED]
  * **Dynamic Grid Coordinator**: Assemble and coordinate the dynamic widget state visibility dashboard in App.kt. [COMPLETED]

---

### Phase 9: Replay, Stress Testing & Scripting (replayEngine & pluginApi) [IN PROGRESS]
Add client-side generation and programmability.
* **Modules created**: `replayEngine` (new), `pluginApi` (new)
* **Tasks**:
  * **Replay**: Resend a request exactly as it was captured.
  * **Advanced Replay**: Loop requests, specify concurrency, and analyze response statistics (load testing).
  * **Kotlin Scripting**: Execute custom .kts scripts dynamically to intercept, validate, or modify traffic (e.g. onRequestHandler { request -> ... }).
  * **Plugin API**: Provide a stable API for loading jar-based plugins.

---

### Phase 10: Upstream Proxies & Port Forwarding [IN PROGRESS]
Ensure KNet integrates into complex enterprise network topologies.
* **Modules modified**: `proxyEngine`
* **Tasks**:
  * **Upstream Proxy Chaining**: Route KNet's outgoing connections through an upstream HTTP/SOCKS5 proxy.
  * **Reverse Proxy**: Listen on custom ports and map them to local servers.

---

### Phase 11: API Studio Dual-Engine Scripting Platform v2.0 (scriptEngine) [COMPLETED]
Build a language-agnostic scripting platform supporting JavaScript (GraalJS) and native Kotlin (Kotlin JVM Scripting) with a unified Script SDK, 1-click snippets, and live test assertions.
* **Modules created**: `scriptEngine` (new)
* **Tasks**:
  * **Unified Script SDK**: Create `ScriptContext`, `ScriptRequestModel`, `ScriptResponseModel`, `ScriptTestResult`, and `ScriptExecutionResult`. [COMPLETED]
  * **Security Sanitizer**: Enforce runtime-based and pre-execution security sandboxing blocking forbidden keywords (`launch`, `async`, `Thread`, `System.exit`). [COMPLETED]
  * **GraalJS Engine**: Execute JavaScript with Postman `pm.*` API compatibility. [COMPLETED]
  * **Kotlin JVM Script Engine**: Execute native Kotlin `.kts` scripts via `BasicJvmScriptingHost` compiling directly into in-memory JVM Bytecode. [COMPLETED]
  * **1-Click Snippet Registry**: Provide template helpers (`Status 200`, `Latency < 500ms`, `JSON Value`, `Set Env Var`, `Generate UUID`). [COMPLETED]
  * **UI Integration**: Add Language Toggle (`[JavaScript]` vs `[Kotlin]`) and Snippet Bar in script editors, and live `✔ PASS` / `✖ FAIL` test cards in Response Inspector. [COMPLETED]

---

### Phase 12: Spring Boot WebFlux Testing Server (testingServer) [COMPLETED]
Build a standalone feature-modular Spring Boot WebFlux test server for offline testing of all KNet HTTP verbs, authentication modes, status codes, and latency delays.
* **Modules created**: `testingServer` (new)
* **Tasks**:
  * **Feature Directory Structure**: Create `get/`, `post/`, `put/`, `patch/`, `delete/`, `authentication/`, `status/`, `delay/`, `headers/`, `cookies/` feature packages. [COMPLETED]
  * **WebFlux Functional Routers**: Build `coRouter` and `suspend Handler` for each feature package. [COMPLETED]
  * **Separate Router Configurations**: Maintain separate router files per directory. [COMPLETED]

---

### Phase 13: 2-Category Collection System (UNSAVED vs SAVED) [COMPLETED]
Implement a 2-category architecture in API Studio separating temporary ad-hoc sessions (`UNSAVED`) from saved collections (`SAVED`), featuring reusable collapsible sections and auto-session creation.
* **Modules modified**: `sharedUI`
* **Tasks**:
  * **Reusable CollapsibleSection Component**: Build `CollapsibleSection.kt` with animated expansion, badge counters, and header action slots. [COMPLETED]
  * **Auto-Creation of Unsaved Sessions**: Auto-create `Unsaved Request 1`, `Unsaved Request 2` when sending or opening new tabs without selecting a saved collection. [COMPLETED]
  * **Save to Collection Action**: Move unsaved requests into permanent saved collections via a single click modal. [COMPLETED]

---

### Phase 14: Dedicated Code Editor Module (codeEditorUI) [COMPLETED]
Extract the code editor engine into a standalone, modularized Kotlin Multiplatform Gradle library module.
* **Modules created**: `codeEditorUI` (new)
* **Tasks**:
  * Extract code editor composables (`KNetCodeEditor`), FSM tokenizers (`TokenMaker`), AST fold engines (`FoldManager`), highlighters, and tokens into `:codeEditorUI`. [COMPLETED]
  * Update `:sharedUI` to depend on `api(project(":codeEditorUI"))` and update consumer imports. [COMPLETED]

---


## Verification & Testing Strategy
* **testingServer**: A standalone Spring Boot WebFlux server that simulates every HTTP/HTTPS behavior (authentication, long polling, SSE, chunked upload/download, websocket echoes).
* **integrationTests**:
  * Direct integration tests where Ktor Client routes requests through our Netty proxyEngine to target the testingServer.
  * Validate rule matching, certificate verification, and throttling under automated assertions.
* **Modules created**: `testingServer` (new), `integrationTests` (new)

---

## Proposed Module Structure

The final target structure will consist of these modules, created and configured incrementally:
1. `shared` (Existing common DTO and library module)
2. `logger` (Centralized logging library wrappers using Touchlab Kermit)
3. `certificateManager`
4. `interceptor`
5. `proxyEngine`
6. `storage`
7. `sessionManager`
8. `rewriteEngine`
9. `replayEngine`
10. `websocketInspector`
11. `pluginApi`
12. `desktopApp` (Existing Compose UI module)
13. `testingServer`
14. `integrationTests`
