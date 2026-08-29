# KNet Deep Architecture, Scalability, and Engineering Audit

- **Audit date:** 2026-08-18
- **Repository scope:** all Gradle modules, production source sets, tests, build configuration, and current documentation
- **Method:** static dependency reconstruction, end-to-end runtime tracing, ownership and failure-mode analysis, and a clean `check`/desktop assembly verification
- **Production code changed:** none

> Historical baseline: this document records the repository as audited on 2026-08-18. It is intentionally not
> rewritten as implementation work lands. Current capability and migration status live in
> `docs/target_architecture_and_implementation_plan.md`, `docs/implementation_plan.md`, and the protocol-specific
> rollout documents; HTTP/2 is now `EXPERIMENTAL` rather than absent.

## A. Executive summary

### Is the current KNet architecture scalable?

**NO.**

KNet has a promising desktop shell, a workable feature-module layout, and several useful foundations: Netty is an appropriate transport, Room metadata is separated from file-backed bodies, Compose uses a virtualized traffic list, repository ports and use cases exist, and protocol inspection has the beginning of a strategy interface. Those strengths make incremental repair possible.

The current proxy runtime, however, is not safe enough to be the foundation of a professional debugging proxy. The decisive problems are in the hot path rather than in cosmetic layering:

- every HTTP request and response is aggregated into memory with a 10 MiB ceiling, so large downloads, SSE, WebSocket upgrades, and genuine streaming do not work;
- certificate generation, file writes, parsing, listener delivery, network-interface enumeration, and logging can run synchronously on Netty event loops;
- interception retains reference-counted messages incorrectly and can leak up to the whole aggregated payload per intercepted event;
- persistence performs two independently scheduled `REPLACE` writes for one transaction, allowing a late request write to overwrite a completed response;
- multiple pipelined HTTP/1.1 requests can be forwarded concurrently and associated through one channel attribute, allowing response reordering and wrong request/response correlation;
- capture has no bounded queue, admission policy, retention policy, or overload state;
- the proxy listens on every interface with no authentication, upstream TLS verification is disabled by default, and the root CA private key and captured secrets are stored as ordinary plaintext files;
- lifecycle owners exist in name but production resources are not registered with them; partial starts, concurrent start/stop calls, and normal window closure can orphan resources;
- the planned connectivity-provider and PAC architecture is not implemented at all.

The result is suitable for controlled development traffic consisting of small, ordinary HTTP/1.1 exchanges. It is not yet suitable for hostile networks, large payloads, sustained high-rate capture, long-running sessions, or Charles/mitmproxy-class protocol breadth.

### Principal conclusion

The repository can evolve without a whole-product rewrite **only if the transport/capture seam is corrected before more features are added**. The UI, certificate primitives, inspectors, persistence technology, and much of the feature packaging can be retained. The request/response hot path, traffic event model, session writer, security defaults, and lifecycle orchestration require substantial redesign. Adding HTTP/2, WebSocket, SSE, or plugins on top of the current `FullHttpRequest`/`FullHttpResponse` model would lock in an architectural dead end.

## Audit basis and limitations

This is a source audit, not a throughput benchmark. Scaling numbers below are architectural estimates derived from buffer ceilings, object lifetimes, algorithms, and task creation behavior. They are deliberately labeled as estimates. The build verification establishes compilation/test status, not production capacity.

The repository currently contains 28 included Gradle modules and roughly 67,000 lines of Kotlin including tests. Most modules apply Kotlin Multiplatform but declare only a JVM target. Claims in `README.md` about mobile support, HTTP/2, WebSocket, SSE, a bounded session manager, and an 11-module architecture describe intent or dormant code, not the observed production path.

## B. Current architecture

The actual dependency and runtime shape is not a clean `UI -> Domain -> Engine -> Infrastructure` stack. It is closer to this:

```text
Compose feature UIs
  |       |             |                     |
  |       +-----------> engine:script         +--> engine:certificate
  |       +-----------> engine:formatter      +--> engine:portal (transitive/use)
  +-------------------> engine:interceptor
  |
  v
core:domain  <--------------------------- data:desktop composition/repositories
  |   ^                                         |
  |   |                                         +--> core:http / storage
  |   |                                         +--> engine:proxy
  |   |                                         +--> engine:certificate
  |   |                                         +--> engine:interceptor
  |   |                                         +--> engine:portal
  |   |                                         +--> engine:protocol
  |   |                                         +--> engine:formatter
  |   |
  +--> core:logger                         apps:desktop
                                              |
                                              +--> Koin composition
                                              +--> Compose window

Runtime hot path:

client socket
  -> KNetProxyServer / Netty HTTP aggregation
  -> KNetProxyHandler
  -> optional KNetInterceptorHandler / InterceptCoordinator
  -> one new upstream channel per request
  -> KNetOutboundHandler
  -> synchronous ProxyTrafficListener callback
  -> ProxyEngineRepositoryImpl
       -> synchronous payload files and protocol parsing
       -> unbounded Dispatchers.IO Room writes
  -> Room SELECT * Flow
  -> GetLiveTrafficUseCase full-list mapping
  -> TrafficViewModel StateFlow
  -> LazyColumn + lazy body viewer/cache
```

This creates three different architectures in one codebase:

1. a feature-oriented Compose application with Koin and use cases;
2. a callback-driven Netty proxy with static pipeline composition;
3. a partially duplicated session/traffic subsystem, where `engine:session` contains bounded-session concepts that the production `data:desktop` capture path does not use.

There are no detected Gradle dependency cycles. The problem is direction and abstraction leakage, not a literal build graph cycle.

## C. Module responsibility and dependency graph

### Module-by-module model

| Module | Observed responsibility | Direct project dependencies | Important reverse dependencies | Assessment |
|---|---|---|---|---|
| `:products:desktop` | Process bootstrap, Koin composition, window creation, nominal shutdown hook | data, domain, logger, UI app/features | executable root | **GOOD as composition root; P1 lifecycle wiring is incomplete** |
| `:core:logger` | Kermit facade and unused logger configuration holder | none | domain and most engines | **ACCEPTABLE**, but global logging and ineffective configuration weaken replacement/testing |
| `:core:domain` | Domain entities, repositories, use cases, plus UI states, formatting helpers, decoding | logger | data, HTTP, engines, UI | **ARCHITECTURAL VIOLATION:** “domain” depends outward and contains presentation/platform concepts |
| `:core:http` | Ktor API Studio client/execution abstractions | domain, logger; certificate on JVM | data, portal, API Studio | **SUSPICIOUS:** application transport is called core and exposes overlapping domain models |
| `:core:pairing` | Minimal pairing placeholder | none/minimal | little or none | **P3 premature module** |
| `:core:serialization` | Collection/request serialization | domain | data | **ACCEPTABLE**, though persistence DTO ownership should be explicit |
| `:storage` | Room database, entities, DAOs, migrations; preferences support | domain/logger/Room | data, session | **ACCEPTABLE infrastructure**, with destructive migration and indexing defects |
| `:data:desktop` | Concrete repositories, runtime assembly, payload persistence, proxy-to-DB bridge | domain, HTTP, serialization, storage, most engines, Netty | desktop app | **SUSPICIOUS/God integration module:** owns orchestration that belongs in an application layer |
| `:engine:proxy` | Netty listener, CONNECT MITM, HTTP/1 forwarding, DNS, channel registry | domain, certificate, logger, Netty | data, interceptor | **Correct conceptual boundary, incorrect implementation API and hot-path ownership** |
| `:engine:certificate` | Root/leaf certificates, trust, imported mTLS material | domain, logger, crypto/Netty TLS | proxy, portal, data, certificate/settings UI | **Mostly cohesive**, but filesystem/security and UI exposure should move behind ports |
| `:engine:portal` | Netty setup/certificate route handler and profile generation | domain, HTTP, certificate, logger, Netty | data, certificate UI | **ARCHITECTURAL VIOLATION:** injected into proxy pipeline; does discovery/template/business work |
| `:engine:interceptor` | Breakpoint matching, pausing, editing, forwarding | domain, logger, proxy, traffic | data, app UI | **High-risk coupling:** static registries and Netty buffers cross into global UI-visible state |
| `:engine:traffic` | Rule/traffic transformation concepts | domain | interceptor/data | **SUSPICIOUS/dormant overlap** with breakpoint and repository behavior |
| `:engine:session` | Payload file/mapper utilities used by data, plus bounded session buffer and HAR/export concepts | domain, logger, storage/Room | data uses payload/header helpers; bounded `SessionManager` is not wired | **Useful pieces, wrong layer and partly dormant duplicate** |
| `:engine:protocol` | Inspector strategy, GraphQL, WebSocket parser/handler, gRPC decoder | domain, logger, Netty | data | **Partly extensible**, but only GraphQL inspector is wired; transport features are dormant |
| `:engine:formatter` | Content detection and JSON/XML/HTML/image/etc. formatting | domain, logger, format libraries | traffic/http-panel UI, data | **Useful but mislabeled engine; UI directly depends on implementation** |
| `:engine:script` | GraalJS and Kotlin script execution | domain/logger/scripting runtimes | API Studio and scripting UI | **Cohesive capability with P1 sandbox/timeout issues** |
| `:engine:simulator` | Network/traffic simulation utilities | domain | no important production path | **P3 dormant/premature** |
| `:ui:core` | Compose design system/components | domain and Compose | all desktop UI | **GOOD overall**, though domain dependency should be minimized |
| `:ui:desktop:app` | Shell, workspace routing, cross-feature coordination | domain, interceptor, UI features | desktop app | **SUSPICIOUS:** shell coordinates feature ViewModels and engine-backed breakpoint state |
| `:ui:desktop:workspace` | Workspace layout feature | domain/UI core | app/desktop | **ACCEPTABLE**, with overlap in app shell |
| `:ui:desktop:traffic` | Traffic list, filtering, selection, body inspection, proxy controls | domain, formatter, UI core | app | **Functional but oversized state/ViewModel and direct engine dependency** |
| `:ui:desktop:apiStudio` | API request authoring, collections, execution | domain, HTTP, script, UI core/editor/panel | app | **Feature-cohesive**, but has large ViewModel and overlapping models |
| `:ui:desktop:httpPanel` | Request/response editors and viewers | domain, formatter, script, UI core/editor | API Studio/traffic | **ACCEPTABLE presentation component with implementation leakage** |
| `:ui:desktop:breakpointManager` | Breakpoint rules and live interception drawer | domain, interceptor, UI core/panel | app | **Boundary violation:** presentation directly understands engine/global sessions |
| `:ui:desktop:certificate` | CA/imported certificate management and onboarding | domain, certificate, portal, UI core | app | **Boundary violation:** presentation directly depends on concrete engines |
| `:ui:desktop:settings` | Desktop settings UI | domain, certificate, UI core | app | **SUSPICIOUS concrete certificate dependency** |
| `:ui:desktop:scripting` | Script editor/result UI | domain, script, UI core/editor | HTTP panels/API Studio | **ACCEPTABLE feature, subject to engine safety** |
| `:ui:desktop:codeEditor` | Reusable code editor | UI core/Compose/editor libs | multiple UI features | **GOOD reusable presentation module** |
| `:testingServer` | Local Spring-based manual/integration test server | Spring/testing libraries | tests/manual verification | **GOOD test support**, but it has no tests of its own and is underused for proxy E2E |

### Condensed actual Gradle graph

```text
:products:desktop
  -> :data:desktop, :core:domain, :core:logger
  -> :ui:desktop:app and feature modules

:data:desktop
  -> :core:domain, :core:http, :core:serialization, :storage
  -> :engine:{proxy,certificate,interceptor,portal,protocol,formatter,session/traffic as configured}

:engine:proxy       -> :core:domain, :core:logger, :engine:certificate, Netty
:engine:interceptor -> :core:domain, :core:logger, :engine:proxy, :engine:traffic
:engine:portal      -> :core:domain, :core:http, :engine:certificate, :core:logger, Netty
:engine:protocol    -> :core:domain, :core:logger, Netty
:engine:session     -> :core:domain, :core:logger, :storage, Room
:engine:formatter   -> :core:domain, :core:logger, parser/formatter libraries
:engine:script      -> :core:domain, :core:logger, GraalJS/Kotlin scripting

:core:http          -> :core:domain, :core:logger, :engine:certificate (JVM)
:core:serialization -> :core:domain
:storage            -> :core:domain, :core:logger
:core:domain        -> :core:logger

:ui:desktop:app                -> feature UIs + :engine:interceptor
:ui:desktop:traffic            -> :core:domain + :engine:formatter
:ui:desktop:certificate        -> :core:domain + :engine:certificate + :engine:portal
:ui:desktop:settings           -> :core:domain + :engine:certificate
:ui:desktop:apiStudio          -> :core:domain + :core:http + :engine:script
:ui:desktop:httpPanel          -> :core:domain + :engine:formatter + :engine:script
:ui:desktop:breakpointManager  -> :core:domain + :engine:interceptor
```

### Dependency classification

| Relationship | Classification | Reason |
|---|---|---|
| app -> data/domain/presentation | GOOD | composition root is allowed to know implementations |
| feature UI -> domain contracts/use cases | GOOD | stable presentation dependency when models are presentation-neutral |
| engine -> domain value/port API | ACCEPTABLE | appropriate only for truly stable transport-neutral types |
| domain -> logger | SUSPICIOUS | domain is not dependency-inward/pure and logging is a side effect |
| domain contains `TrafficItemUiState` and UI intents | ARCHITECTURAL VIOLATION | presentation changes force core/domain changes |
| common domain imports `java.net.URI` | ARCHITECTURAL VIOLATION | platform API leaks into a common source set and makes MPP claims misleading |
| UI -> concrete certificate/portal/interceptor/formatter/script engines | ARCHITECTURAL VIOLATION | engine replacement and UI tests require implementation modules |
| session engine -> Room/storage | ARCHITECTURAL VIOLATION | an “engine” owns infrastructure implementation details |
| portal -> broad domain/HTTP/certificate and proxy pipeline registration | ARCHITECTURAL VIOLATION | connectivity delivery is not isolated from interception transport |
| proxy exports Netty types/dependency as API | SUSPICIOUS | Netty becomes a transitive architectural contract |
| data desktop -> all implementations | ACCEPTABLE at composition boundary | but orchestration and hot-path capture behavior need a dedicated application service |

### Public API observations

Most Kotlin declarations use default `public`, including concrete engine handlers, registries, mutable models, and implementation utilities. `KNetProxyServer.pipelineInitializers` exposes process-wide mutable pipeline composition; Netty messages cross the interceptor boundary; and engine modules expose implementation dependencies through `api(...)`. Conversely, there is no small, versionable proxy SPI describing lifecycle, streaming exchanges, capture admission, or protocol extensions.

The public surface should be reduced to module facades and stable ports. Netty handlers, `ByteBuf` ownership, certificate generator details, Room entities/DAOs, static registries, and UI-specific state should be `internal` or implementation-scoped. Public API binary compatibility is not currently checked.

## D. Actual runtime traffic flow

### Plain HTTP

```text
client
  -> KNetProxyServer.start()
       ServerBootstrap + NioEventLoopGroup
       bind(0.0.0.0, port)
  -> HttpServerCodec
  -> HttpObjectAggregator(10 MiB)
  -> MobilePortalHandler                    [same pipeline]
  -> KNetInterceptorHandler                [optional breakpoint path]
  -> KNetProxyHandler.channelRead0(FullHttpRequest)
       resolve URI/Host
       copy ByteBuf -> domain ByteArray
       listener.onRequestCaptured()         [synchronous]
       DNS on Dispatchers.IO
       new Bootstrap / new upstream channel
  -> HttpClientCodec
  -> HttpObjectAggregator(10 MiB)
  -> KNetOutboundHandler
       copy FullHttpResponse body
       listener.onResponseCaptured()        [synchronous]
       write retained response to client
       close upstream channel
```

### HTTPS CONNECT and MITM

```text
CONNECT host:port
  -> split authority on ':'
  -> reply 200 Connection Established
  -> CertificateCache.get(host)
       RSA-2048 leaf generation on cache miss [Netty event loop]
  -> build server SslContext
  -> replace CONNECT handler with SslHandler + HTTP codec + aggregator
  -> decrypted requests follow the same plain-HTTP path
  -> upstream TLS uses client SslContext
       strictSsl=false by default -> trust-all manager
```

### Capture, storage, and UI

```text
Netty event loop
  -> ProxyEngineRepositoryImpl callback
       pendingRequests[id] = full HttpRequest ByteArray
       Files.write(request body)                 [synchronous]
       GraphQL/Jackson inspection                [synchronous]
       scope.launch(Dispatchers.IO) { DAO REPLACE(pending) }
  -> response callback
       pendingRequests.remove(id)
       Files.write(request and response bodies)  [synchronous]
       protocol inspection                       [synchronous]
       scope.launch(Dispatchers.IO) { DAO REPLACE(complete) }
  -> Room observes SELECT * ORDER BY timestamp
  -> LiveTrafficRepository maps every row
  -> GetLiveTrafficUseCase filters/maps every row
  -> TrafficViewModel publishes full list in StateFlow
  -> Compose LazyColumn renders visible rows
  -> selected body: File.readBytes() -> decode -> format -> 64-entry LRU
```

### Ownership answer

- `KNetProxyServer` creates and nominally owns boss/worker groups, server channel, a private IO scope, and active channels.
- Each request creates a separate upstream channel; the present `ProxyConnectionPoolManager` is not used by production code.
- `KNetProxyHandler` and `KNetOutboundHandler` share correlation through channel attributes and closures, not a connection/exchange state machine.
- `ProxyEngineRepositoryImpl` owns a process-lifetime IO scope and pending full request models but has no close/cancel method.
- Room owns the metadata stream; payload files have no effective production retention owner.
- feature ViewModels own full metadata snapshots; selected decoded/formatted bodies live in `TrafficViewModel`'s cache.
- root and leaf key material live for the process or indefinitely on disk; cache eviction is a wholesale clear.

### Implemented portal/connectivity surface

| Route/capability | Actual status |
|---|---|
| `/setup` and `/` on a recognized local host | setup HTML from `MobilePortalHandler` |
| `/knet-ca.crt` and `/ca` | root CA certificate download (`/ca` can select Apple profile by user agent) |
| `/knet-ca.mobileconfig` | Apple configuration profile |
| `/favicon.ico` | 204 response |
| `/ca.crt` | not implemented under this name |
| `/proxy.pac`, `/pac` | not implemented |
| PAC/manual/ADB/pairing/VPN/relay provider registry | not implemented |

Because `isPortalRequest()` treats `/setup`, `/ca`, and the certificate paths as portal traffic without first requiring the local portal authority, those paths also create the route-collision finding below.

### Current traffic-model inventory

| Concern | Current representation | Layer ambiguity / limitation |
|---|---|---|
| request | domain `HttpRequest`: ID, method string, URL string, protocol string, header pairs, optional `ByteArray`, timestamp, interception flags | used as Netty mapping output, pending persistence value, breakpoint DTO, and UI/domain input |
| response | domain `HttpResponse`: status, reason, header pairs, optional `ByteArray`, timestamp | no protocol, trailers, provisional responses, verification state, truncation, or terminal error |
| exchange | domain `HttpTransaction`: request/response, filesystem body paths/sizes, timings, semantic metadata | domain knows persistence paths and assumes one request/one response |
| headers | generally ordered `List<Pair<String,String>>`, but several inspection paths call `toMap()` | duplicate names collapse in those paths; persistence formats differ between mappers |
| cookies | ordinary `Cookie`/`Set-Cookie` headers | no parsed cookie model; repeated `Set-Cookie` is vulnerable to map conversion in consumers |
| query parameters | embedded in URL and parsed opportunistically with `URI` for UI details/routing | no canonical lossless query representation; Java URI leaks into common domain code |
| protocol | free-form request string; persistence mapper restores `HTTP/1.1` | not a connection/stream protocol model |
| timing | `HttpTimings` for DNS/TCP/TLS/TTFB/download plus total duration | no queue/interception/write timing or monotonic clock semantics |
| connection/TLS | not first-class in stored traffic | cannot group/reason about socket reuse, peer certificates, cipher, SNI, verification, or H2 streams |
| errors | generic logs, synthetic 502s, or dropped reason callbacks | not a typed terminal exchange state; partial bodies are not represented |
| WebSocket | dormant protocol-engine frame/parser types | not connected to traffic repository/storage/UI or proxy upgrade lifetime |
| GraphQL | sealed `InterceptionMetadata.GraphQL` derived from request body | wired, but parsing is synchronous and extensions require core/schema awareness |

## E. Architecture strengths

1. **Appropriate core transport choice.** Netty is a credible foundation for a desktop interception proxy. The server uses separate acceptor/worker groups and keeps connection work on channel event loops. The defect is how KNet layers buffering and callbacks on top of it, not the choice of Netty.
2. **Metadata/body separation exists.** `HttpTransactionEntity` stores body paths, and `LiveTrafficRepositoryImpl.loadTransactionBody()` loads content on demand. This is materially better than placing every captured body in each list row.
3. **Virtualized list rendering.** `TrafficTable.kt` uses a keyed `LazyColumn`; Compose therefore does not instantiate 100,000 row composables at once. Data acquisition and state rebuilding—not row virtualization—are the dominant list problem.
4. **Ports and use cases provide migration seams.** Repository interfaces and narrowly named use cases let the UI avoid constructing Room and proxy classes directly. These seams can host a new application layer with limited UI churn.
5. **Feature UI packaging is understandable.** Traffic, API Studio, certificates, settings, breakpoints, scripting, editor, and HTTP panels are distinguishable modules rather than a single desktop monolith.
6. **Certificate SAN construction handles basic DNS/IP forms.** `LeafCertificateGenerator` distinguishes IP addresses and DNS names, includes SANs, and has a finite validity period.
7. **Protocol inspection starts with a strategy.** `ProtocolInspectorRegistry` accepts inspectors and the production composition registers GraphQL without putting GraphQL branches directly in the proxy handler. This is the right direction for semantic inspectors.
8. **JavaScript host access is explicitly restricted.** `GraalJsScriptEngine` disables host, native, process, thread, and ordinary IO access. This is not sufficient for enforceable timeouts, but it is a valuable default.
9. **Bounded-session concepts already exist.** `engine:session/SessionBuffer.kt` implements a bounded metadata buffer and payload pruning. It is not wired into production, but it provides tested vocabulary that can be moved rather than reinvented.
10. **The build is modular and testable at unit level.** A clean full `check` traverses JVM tests across KMP modules; formatters, domain logic, certificates, storage, and UI state have meaningful unit coverage even though proxy integration/stress coverage is weak.

## F. Critical and high-severity findings

### F-01 — Unauthenticated LAN-wide open proxy

**Finding:** The proxy is reachable on every network interface, and there is no client authentication, pairing token, ACL, per-client policy, or connection limit.

**Evidence:** `engine/proxy/src/main/kotlin/com/devuloopers/knet/engine/proxy/KNetProxyServer.kt:108` binds `0.0.0.0`; `KNetProxyHandler.kt:82-121` accepts CONNECT and ordinary proxy requests without an authorization gate. The portal shares this listener. No production type implements a proxy-access policy.

**Why it matters:** Any device able to reach the laptop can consume its network identity and bandwidth, probe destinations through it, and cause KNet to capture or persist attacker-controlled traffic. CONNECT makes it a general TCP tunnel to permitted target ports.

**Impact:** Security vulnerability, resource exhaustion, attribution risk, and exposure of the developer workstation as an open proxy.

**Severity:** **P0 — Critical**

**Recommendation:** Default to loopback. Make LAN exposure an explicit session mode requiring short-lived pairing credentials, source-client identity, visible UI status, revocation, rate/connection limits, and an allow policy. Keep the onboarding portal and proxy authorization policies separate even if they temporarily share a port.

### F-02 — Full-message aggregation makes streaming and large traffic fail

**Finding:** Both sides of the proxy aggregate complete HTTP messages with a 10 MiB maximum. The advertised chunked-response path is placed after the upstream aggregator and is therefore unreachable for normal decoded responses.

**Evidence:** `KNetProxyServer.kt:93-97` installs `HttpObjectAggregator(MAX_CONTENT_LENGTH_BYTES)` on the client side; `PipelineHandlerNames.kt:45-48` defines 10 MiB; `KNetProxyHandler.kt:267-278` installs the same aggregator before `KNetOutboundHandler`. The handler accepts `FullHttpRequest` and its full-response branch is at `KNetProxyHandler.kt:388-425`.

**Why it matters:** A 500 MiB download does not stream through KNet or truncate capture—it fails when aggregation crosses 10 MiB. SSE never reaches the client incrementally, request uploads are bounded, and WebSocket upgrade semantics are not preserved.

**Impact:** Proxy failure for ordinary large payloads; SSE/WebSocket incompatibility; direct-memory and heap-copy spikes near the limit.

**Severity:** **P0 — Critical**

**Recommendation:** Forward `HttpMessage`/`HttpContent` incrementally. Tee body chunks into a bounded/asynchronous body sink with explicit `Complete`, `Truncated`, `Skipped`, and `Failed` outcomes. Aggregation should be an opt-in inspector policy for small bodies, never a transport requirement.

### F-03 — Blocking and CPU-heavy operations execute on Netty event loops

**Finding:** The event-loop callback chain performs leaf RSA generation, synchronous payload file writes, GraphQL/JSON parsing, body decoding/rule matching, network-interface enumeration, template loading, and per-message logging.

**Evidence:** `KNetProxyHandler.kt:103-110` calls the certificate cache during CONNECT; `LeafCertificateGenerator.kt:87-95` generates RSA-2048 keys. `KNetProxyHandler.kt:185-186` synchronously logs and invokes the listener. `ProxyEngineRepositoryImpl.kt:83-183` writes payloads and invokes protocol inspectors before launching only the DAO operation. `KNetInterceptorHandler.kt:59-64` copies/decodes/matches bodies. `KNetProxyHandler.kt:336-351` and `MobilePortalHandler.kt:186-216` enumerate interfaces. `TemplateLoader.kt:21-29` performs first-load classpath IO.

**Why it matters:** One slow disk, cache miss, complex JSON document, regex, logger writer, or interface call stalls every channel assigned to that event loop. Load increases latency nonlinearly and defeats Netty's concurrency model.

**Impact:** Event-loop starvation, head-of-line blocking across unrelated clients, timeouts, and proxy-wide slowdown.

**Severity:** **P0 — Critical**

**Recommendation:** Make event-loop work limited to protocol state and buffer handoff. Preload immutable portal assets; cache interface state off-loop; move certificate generation to a bounded crypto executor with per-host single-flight; publish capture metadata/chunks to a bounded application-owned queue; parse/format only on dedicated bounded workers. Measure event-loop task latency.

### F-04 — Interception leaks reference-counted Netty messages

**Finding:** Paused requests/responses are retained once in addition to their original reference, but all resume/drop/timeout paths release or transfer only one reference.

**Evidence:** `InterceptCoordinator.kt:45-50` and `124-130` call `ReferenceCountUtil.retain(msg)` and create a new scope. `KNetInterceptorHandler.kt:85-86` returns without forwarding or releasing the original request; response interception does the same at `KNetInterceptorHandler.kt:131-133`. Resume/drop paths in `InterceptCoordinator.kt:62-113` and `141-186` account for only one release/transfer.

**Why it matters:** `ChannelDuplexHandler` does not auto-release the unforwarded original. An intercepted aggregated body can therefore remain in Netty direct memory indefinitely. Closing the channel does not magically release an application-retained object no longer owned by a pipeline stage.

**Impact:** Severe direct-memory leak proportional to intercepted message sizes; long sessions can end in allocator pressure or `OutOfDirectMemoryError`.

**Severity:** **P0 — Critical**

**Recommendation:** Define and test exact ownership for every branch. Either transfer the original reference into the deferred session without an extra retain, or retain and immediately release the handler's original ownership. Use `try/finally`/promise listeners, release on cancellation and handler removal, and add leak-detector E2E tests covering resume, modify, drop, timeout, disconnect, and write failure.

### F-05 — Independently scheduled `REPLACE` writes can regress completed transactions

**Finding:** Request and response callbacks launch separate unsequenced database writes for the same primary key. Both use Room `REPLACE`, so the late request write can overwrite a previously completed response with a pending/null response.

**Evidence:** `ProxyEngineRepositoryImpl.kt:83-123` schedules the request insert; `ProxyEngineRepositoryImpl.kt:125-183` separately schedules completion. `HttpTransactionDao.kt:16-17` uses `OnConflictStrategy.REPLACE`. Both launches share an unconstrained `Dispatchers.IO` scope and no per-transaction ordering primitive.

**Why it matters:** A fast response or a busy IO pool can reverse commit order. `REPLACE` replaces the row rather than conditionally advancing a transaction state.

**Impact:** Persistent data corruption: completed traffic can revert to pending and lose status, response paths, size, timing, and protocol metadata.

**Severity:** **P0 — Critical**

**Recommendation:** Route transaction lifecycle events through one ordered session writer. Insert once and use conditional `UPDATE ... WHERE state/version` transitions, or maintain a monotonic version/sequence. A completion must be idempotent and may never transition backward. Add a deterministic reordering test.

### F-06 — HTTP/1.1 pipelining is not serialized or correctly correlated

**Finding:** Every client request creates an independent upstream connection, while one client-channel attribute stores the “current” request. Independent upstream responses may complete out of order and interceptor response matching reads whichever request most recently overwrote the attribute.

**Evidence:** `KNetProxyHandler.kt:179-217` maps one request and creates a new `Bootstrap`; `KNetProxyHandler.kt:282-307` connects it independently. `KNetInterceptorHandler.kt:36-60` overwrites `REQUEST_ATTR` on each request and `KNetInterceptorHandler.kt:108-132` reads it for responses. `KNetOutboundHandler.kt:418-425` writes each result to the shared client channel as it arrives.

**Why it matters:** HTTP/1.1 pipelined responses must retain request order. A slower first upstream can be overtaken by the second. Correlation-dependent persistence and response breakpoints can then operate on the wrong request.

**Impact:** Wire-protocol corruption, incorrect captured records, wrong breakpoint rules, and potentially a response delivered to the wrong logical request.

**Severity:** **P0 — Critical**

**Recommendation:** Introduce a per-client connection state machine with an ordered exchange queue. Either serialize HTTP/1 upstream exchanges per client connection or buffer completion and drain responses in request order. Correlation must be an immutable exchange object, never one mutable channel attribute. HTTP/2 later needs stream-ID keyed state instead.

### F-07 — Root CA and captured secrets are persisted without adequate protection

**Finding:** The root CA private key, imported client keys, captured bodies, and captured headers/cookies/authorization values are ordinary plaintext files/database fields under `~/.knet`; file permissions are not hardened, sensitive values are not redacted/encrypted, and production clear does not remove payload files.

**Evidence:** `CertificateRuntimeRepository.kt:17-26` loads/saves `~/.knet/ca/ca.key`; `CertificateAuthority.kt:220-249` writes PEM with ordinary file writers. `CertificateManagerImpl.kt:120-147` copies client keys under `~/.knet/certificates/keys`. `FilePayloadStore.kt:30-39` writes raw bodies. `LiveTrafficRepositoryImpl.kt:99-103` clears only database rows. `HttpTransactionEntity.kt` persists serialized headers and body paths.

**Why it matters:** A root CA private key is equivalent to a local interception authority. Captured traffic routinely includes passwords, bearer tokens, cookies, and personal data. Default filesystem semantics are not an adequate secrets boundary.

**Impact:** Compromise can persist beyond the app session, enable certificate impersonation, and expose captured credentials even after the UI says traffic was cleared.

**Severity:** **P0 — Critical**

**Recommendation:** Generate/store CA keys in an OS keystore where feasible; otherwise use strict owner-only permissions, authenticated encryption backed by an OS-protected key, explicit export, rotation, and secure deletion policy. Sanitize imported-key aliases before path use. Add redaction policies, encrypted session storage, bounded retention, and a clear operation that transactionally removes DB rows, files, UI caches, exports, and pending state.

### F-08 — Capture ingestion has no backpressure or overload policy

**Finding:** Netty synchronously calls a repository listener; the repository keeps full pending requests in an unbounded map and launches an unbounded IO coroutine for each insert/update. Each Room change triggers a full-table query and full-list mapping.

**Evidence:** `KNetProxyHandler.kt:185-186` and `409-416` call the listener directly. `ProxyEngineRepositoryImpl.kt:41-46` owns a never-cancelled IO scope and `ConcurrentHashMap`; callbacks launch at `110-122` and `170-181`. `HttpTransactionDao.kt:19-20` selects all rows without a limit. `LiveTrafficRepositoryImpl.kt:31-37` maps all rows, and `GetLiveTrafficUseCase.kt:42-68` filters/maps the full list again.

**Why it matters:** The UI is not directly blocking Netty rendering, but storage and parsing are in the callback path, while all work after it grows without a bound. When arrival rate exceeds disk/Room/UI transformation rate there is no load shedding, sampling, pause, or capture-degraded state.

**Impact:** Unbounded task/map growth, IO contention, allocation storms, UI lag, and eventual memory exhaustion during bursts or slow disks.

**Severity:** **P1 — High**

**Recommendation:** Add a bounded multi-producer capture channel owned by an application `SessionWriter`. Define capacity in events and bytes, preserve lifecycle ordering, and choose explicit overflow policies (metadata-only, truncate body, sampling, pause capture, or fail session visibly). Batch DB writes and emit compact change notifications rather than requerying every row.

### F-09 — Request-only breakpoints can lose response capture and retain pending requests

**Finding:** A request breakpoint marks the request intercepted and captures it before pause; after resume the proxy captures the request again. `KNetOutboundHandler` suppresses ordinary response capture for every intercepted request, even when no response breakpoint handles it.

**Evidence:** `KNetInterceptorHandler.kt:69-85` tags and calls `onRequestCaptured`. `KNetProxyHandler.kt:179-186` reuses the tagged request and calls it again. `KNetOutboundHandler.kt:406-416` skips `onResponseCaptured` whenever `isIntercepted` is true. Only `InterceptCoordinator.coordinateResponse()` at `148-153` persists a response, and that path runs only when a response rule matches.

**Why it matters:** “Intercepted request” and “response interception is currently responsible for capture” are not the same state. The boolean conflates two phases.

**Impact:** Duplicate pending writes, missing completed responses, full pending request bodies retained in `pendingRequests` until stop, and inaccurate UI history.

**Severity:** **P1 — High**

**Recommendation:** Use an exchange lifecycle with distinct request-paused/forwarded and response-paused/forwarded states. Capture each phase once by exchange ID. Response completion must always be emitted unless the exchange has a terminal dropped/failed state.

### F-10 — Proxy lifecycle is non-atomic and not process-owned

**Finding:** Start/stop is guarded by ad hoc state checks rather than a serialized state machine. A bind failure can leave event loops and a scope alive; graceful shutdown is initiated but not awaited; production resources are never registered with `ApplicationLifecycle`; normal window closure only calls `exitApplication`.

**Evidence:** `KNetProxyServer.kt:70-110` marks/allocates resources before bind and `124-142` does not await group termination. `ProxyRuntimeRepository.kt:33-45` stores the server only after `start()` returns. `ProxyEngineRepositoryImpl.kt:50-80` and `272-289` have no mutex. `ApplicationLifecycle.kt:10-44` offers registration, but repository-wide usage of `registerResource` exists only in tests. `DesktopBootstrap.kt:60-70` closes the Compose application without an explicit application shutdown sequence.

**Why it matters:** `start/start/stop/start`, port-in-use failure, cancellation, or app closure can leave Netty threads, DNS resolver, Ktor client, database, or repository scopes alive. Non-daemon event loops can delay termination.

**Impact:** Port leaks, hung shutdowns, inconsistent UI state, callbacks after stop, and flaky restart behavior.

**Severity:** **P1 — High**

**Recommendation:** Introduce an application-owned, mutex-serialized lifecycle controller with explicit `Stopped/Starting/Running/Stopping/Failed` states. Allocate into locals, publish only after complete success, roll back in reverse order, await shutdown, and register proxy, DNS, HTTP client, DB, body store, executors, and scopes with normal window close as well as the JVM hook.

### F-11 — Upstream TLS verification is disabled by default

**Finding:** The MITM proxy accepts upstream server certificates through a trust-all manager unless `strictSsl` is explicitly enabled; production construction uses the default false value.

**Evidence:** `KNetProxyHandler.kt:59-61` defaults `strictSsl` to false; `KNetProxyHandler.kt:226-230` selects `ProxyTrustManager.getTrustManagerFactory(strictSsl)`. The production `KNetProxyServer`/runtime construction does not override it.

**Why it matters:** KNet itself becomes vulnerable to upstream interception and can present captured content to the client as if the remote peer were authenticated by KNet's trusted CA.

**Impact:** Silent loss of server authenticity and misleading security behavior in a security-sensitive tool.

**Severity:** **P1 — High**

**Recommendation:** Verify upstream TLS by default. Make bypass host-scoped, time-limited, conspicuous, auditable, and visible on each transaction. Record TLS version, cipher, SNI, peer certificate chain, verification result, and override reason.

### F-12 — Portal routes collide with proxied origin routes

**Finding:** The onboarding portal is installed directly in every proxy pipeline and recognizes setup/certificate paths largely independent of the request authority. Requests meant for an external origin can therefore be intercepted as KNet portal requests. Its setup page also interpolates a Host-derived value without HTML escaping.

**Evidence:** `ProxyRuntimeRepository.kt:33-39` clears the global initializer list and registers portal/interceptor handlers. `MobilePortalHandler.kt:53-66` matches `/setup`, `/ca`, and certificate paths, while host checks also accept local aliases. `MobilePortalHandler.kt:200-205` accepts a non-local Host value as the desktop address and `PortalHtmlRenderer.kt:17-23` inserts it with raw string replacement. The same server listens on all interfaces.

**Why it matters:** A request such as an absolute-form proxy request to an external host with `/setup` can receive local KNet content. The handler also performs network discovery and template substitution, so it is not a thin delivery adapter.

**Impact:** Incorrect proxy semantics, route hijacking, reflected HTML injection in the setup page, local information disclosure, and tight coupling between onboarding and interception availability.

**Severity:** **P1 — High**

**Recommendation:** Give portal routing an explicit authority and preferably a separate listener/virtual host. Dispatch only after strict host/port validation. Move discovery and setup generation to application services returning response DTOs; escape all template substitutions and authenticate sensitive setup actions.

### F-13 — Database upgrade paths can destructively erase data

**Finding:** Registered Room migrations omit version steps and enable destructive fallback.

**Evidence:** `DatabaseFactory.kt:18-35` registers migrations 1→2, 3→4, 4→5, 5→6, 7→8, and 8→9, omitting 2→3 and 6→7, then calls `fallbackToDestructiveMigration()`.

**Why it matters:** Users upgrading from an uncovered schema can lose traffic, collections, certificates metadata, and rules without an explicit consent or export path.

**Impact:** User-data loss and untrustworthy upgrade behavior.

**Severity:** **P1 — High**

**Recommendation:** Supply every migration path and schema identity test, remove destructive fallback in production, and provide an explicit recovery/export flow for incompatible development schemas.

### F-14 — Script execution is not reliably bounded or uniformly sandboxed

**Finding:** Coroutine timeout wraps a synchronous Graal evaluation and cannot preempt it; the timeout close hook is not provided by the manager. The Kotlin JSR-223 engine executes with normal JVM privileges. A security validator exists but is not used in production, and some request/response values are interpolated into generated JavaScript source rather than bound as data.

**Evidence:** `TimeoutExecutor.kt:22-34` uses `withTimeoutOrNull` around the blocking action. `ScriptEngineManager.kt:57-75` does not close the active Graal context on timeout. `GraalJsScriptEngine.kt:58-65` configures sandbox flags but evaluates synchronously, and `78-135` builds source using request URL/method and response status text. The Kotlin runtime invokes `engine.eval(fullScript)` in its execution path. Repository search finds `ScriptSecurity.validate` only in tests.

**Why it matters:** An infinite loop can permanently occupy a shared IO thread, and Kotlin scripts can access filesystem, network, reflection, processes, and application classes. Future traffic-transform hooks would put this in a critical proxy path.

**Impact:** Denial of service and arbitrary local code execution when a user runs an untrusted/imported script.

**Severity:** **P1 — High**

**Recommendation:** Treat Kotlin scripting as explicitly trusted local code or remove it from untrusted workflows. Run untrusted transformations in a separate constrained process with hard CPU/wall/memory limits and killability. For Graal, retain and close the context from outside the worker on timeout. Validate inputs before execution and never run scripts on a Netty event loop.

### F-15 — API Studio capture can duplicate events and leak an internal header upstream

**Finding:** `KNetApiClient` pre-records a request and then sends it through KNet's proxy, whose listener records the same transaction again. If the proxied attempt fails and the client falls back to direct transport, the internal transaction header remains on the outbound request.

**Evidence:** `core/http/src/commonMain/kotlin/com/devuloopers/knet/core/http/client/KNetApiClient.kt:270-311` assigns and reports the transaction ID before proxy execution; the proxy reports again in `KNetProxyHandler.kt:179-186`. The fallback reuses the prepared request headers, including `X-KNet-Transaction-Id`.

**Why it matters:** Capture-source semantics are implicit. Internal instrumentation must never become part of the remote HTTP contract, and a retry/fallback must not masquerade as an independent capture phase.

**Impact:** Duplicate traffic rows/events and disclosure of tool-specific identifiers to remote servers.

**Severity:** **P1 — High**

**Recommendation:** Carry correlation in an out-of-band execution context, strip it at the proxy boundary, and make the capture source/idempotency key explicit. Record fallback as an attempt within one logical exchange.

## G. Medium- and low-severity findings

### F-16 — The planned connectivity and PAC architecture does not exist

**Finding:** There is no production `ConnectivityProvider`, registry/context, availability/capability model, PAC configuration/repository/use case, or `/proxy.pac`/`/pac` route.

**Evidence:** Repository-wide searches for the planned type names and PAC endpoints return no implementation. `MobilePortalHandler` serves setup and certificate artifacts only.

**Why it matters:** The intended proxy/portal/connectivity boundary cannot be assessed as working code. Manual proxy, PAC, Apple profiles, ADB reverse, companion pairing, VPN, and relay have materially different activation and health semantics; forcing them into a single future interface without modeling those differences would be premature.

**Impact:** Connectivity additions currently require composition, domain, portal, UI, security, and lifecycle changes rather than adding an isolated provider.

**Severity:** **P2 — Medium now; becomes P1 before adding another connectivity mechanism**

**Recommendation:** Introduce separate concepts for (a) descriptive setup artifact generation and availability, and (b) active mechanisms with lifecycle/health. Model availability (`Supported`, `PlatformUnsupported`, `DependencyMissing`, `PermissionRequired`, `NetworkUnavailable`) separately from runtime state (`Inactive`, `Activating`, `NeedsUserAction`, `Active`, `Deactivating`, `Failed`). PAC generation should be pure/deterministic; interface discovery and cache invalidation belong in platform/application adapters.

### F-17 — Certificate cache concurrency and eviction are inefficient

**Finding:** Cache lookup is a check-then-generate sequence, and reaching the entry limit clears the entire cache.

**Evidence:** `CertificateCache.kt:27-41` performs `cache[host]` followed by generation and assignment rather than atomic single-flight; `CertificateCache.kt:33-36` calls `cache.clear()` at 1,000 entries.

**Why it matters:** Concurrent CONNECTs to one new host can all perform expensive RSA generation. Wholesale clearing creates a thundering herd and discards hot entries. Cache size counts hosts, not key/certificate weight, and there is no expiration refresh.

**Impact:** Latency spikes, CPU bursts, and avoidable heap/key-material retention.

**Severity:** **P2 — Medium**

**Recommendation:** Use per-host single-flight generation off-loop, a weighted LRU/TTL cache, proactive expiry refresh, observable hit/miss/generation latency, and deterministic zeroization/release where practical.

### F-18 — Traffic queries and UI state are O(n) per database change

**Finding:** Every insert invalidates a `SELECT *` stream; repository/use case/ViewModel rebuild full lists, while UI-derived counts and totals repeatedly scan them. Filtering and selection are client-side.

**Evidence:** `HttpTransactionDao.kt:19-20` has an unbounded ordered query and `HttpTransactionEntity` declares no supporting indexes. `GetLiveTrafficUseCase.kt:42-68` maps the entire list. `TrafficState.kt:56-59`, `82-85`, and `90-135` hold/scan full lists. `TrafficViewModel.kt:234-264` collects with `conflate`, which skips UI emissions but does not prevent database requery/mapping.

**Why it matters:** Total work grows approximately O(number of captured rows × number of writes). LazyColumn reduces rendering, not upstream allocation or query cost.

**Impact:** Increasing GC and UI lag around thousands of rows; 100,000-row sessions are not viable.

**Severity:** **P1 — High**

**Recommendation:** Use indexed, paged metadata queries and database-side filtering/sorting. Maintain incremental session counters. Expose stable page keys and compact change events. Put a default bounded retention policy in front of the database.

### F-19 — Body loading and UI body cache can retain very large strings

**Finding:** Selected bodies are read without a size limit, decoded/formatted into strings, and retained in a 64-entry LRU. Clearing the feed does not clear this cache or current prepared state.

**Evidence:** `LiveTrafficRepositoryImpl.kt:55-74` and `119-131` use `File.readBytes()`. `TrafficViewModel.kt:51-55` creates a 64-entry cache and `144-199` loads/decodes/formats bodies. `TrafficViewModel.kt:280-288` clears traffic without clearing prepared bodies.

**Why it matters:** A raw string and pretty-printed representation can each be roughly one-to-several times the source bytes. At the current 10 MiB cap, 64 cached formatted bodies can approach or exceed a gigabyte depending on encoding/format expansion. Future streaming support would make the unbounded read still more dangerous.

**Impact:** Heap pressure, sensitive-data retention after “Clear,” long formatting stalls, and possible OOM.

**Severity:** **P1 — High**

**Recommendation:** Enforce preview byte/character limits, page/stream large bodies, cache by total weight rather than entry count, cancel stale formatting, and wipe body/cache state on session clear. Make “load full body” an explicit guarded action.

### F-20 — Traffic models collapse transport, domain, persistence, and presentation concerns

**Finding:** `HttpRequest`, `HttpResponse`, and `HttpTransaction` carry mutable `ByteArray` bodies and flow through Netty mapping, interception, repositories, storage mappers, export, and UI. Domain also contains presentation-specific traffic states and formatting.

**Evidence:** domain request/response models are imported by `HttpMapper`, `KNetInterceptorHandler`, `ProxyEngineRepositoryImpl`, Room mappers, and UI use cases. `GetLiveTrafficUseCase` returns `TrafficItemUiState` from `core:domain`. Persistence embeds filesystem body paths. `HttpTransactionMapper.kt:19-24` reconstructs protocol as HTTP/1.1 rather than preserving it.

**Why it matters:** The model cannot naturally express a connection, multiple HTTP/2 streams, provisional responses, trailers, truncation, streaming progress, TLS verification, WebSocket messages, or terminal errors. Any protocol or UI evolution changes “core” types everywhere.

**Impact:** High feature coupling and a forced rewrite when multiplexed/long-lived protocols arrive.

**Severity:** **P1 — High**

**Recommendation:** Separate immutable transport events, application capture records, persistence DTOs, and presentation rows. Introduce `ConnectionId`, `ExchangeId`, optional `StreamId`, `BodyRef`/capture outcome, timings, TLS metadata, and monotonic lifecycle events. Keep body bytes outside metadata events.

### F-21 — Header and protocol persistence is lossy

**Finding:** Header utilities convert repeated headers to a map, persistence uses a custom serialized form, and mapped rows default protocol/timing fields rather than preserving complete wire semantics.

**Evidence:** header `toMap()` consumers collapse repeated names such as `Set-Cookie`; `HttpTransactionEntity` stores serialized header strings; `HttpTransactionMapper.kt:19-24` hardcodes HTTP/1.1. The transaction schema lacks connection/TLS/trailer/error/stream fields.

**Why it matters:** Debugging proxies must preserve ordering and duplicate headers and distinguish what was observed from what was inferred. Lossy persistence makes captures unreliable evidence.

**Impact:** Incorrect cookie display/export, inaccurate protocol labels, and blockers for HTTP/2/gRPC/trailers.

**Severity:** **P2 — Medium**

**Recommendation:** Preserve headers as an ordered multi-value list in every wire-facing layer and use a versioned normalized persistence schema. Record original protocol/version, trailers, content encoding, and observation/capture status explicitly.

### F-22 — Request/response rebuilding can produce invalid framing

**Finding:** Modified messages set `Content-Length` without consistently removing `Transfer-Encoding` or preserving chunk/trailer/content-encoding semantics.

**Evidence:** `RequestRebuilder.kt` and `ResponseRebuilder.kt` rebuild full bodies and rewrite length, while the upstream architecture assumes aggregated messages.

**Why it matters:** HTTP/1.1 forbids ambiguous framing combinations; editing compressed or chunked content requires clear policy for decode/re-encode and trailer handling.

**Impact:** Rejected requests, response splitting ambiguity at strict peers, or changed payload semantics.

**Severity:** **P1 — High**

**Recommendation:** Centralize framing normalization: remove conflicting transfer headers, recompute content length only for fully buffered replacements, explicitly re-encode or remove content encoding, and test chunked bodies/trailers/compression/multipart/binary edits end to end.

### F-23 — Network change detection is IPv4 polling and blunt channel invalidation — Resolved

**Original finding:** KNet polled for a local IPv4 address independently from the connectivity subsystem and closed all channels when it changed. It did not model interface identity, interface kind, VPN state, or a shared advertised LAN endpoint.

**Resolution:** `DesktopNetworkSnapshotMonitor` now publishes one immutable network snapshot with typed physical, virtual, VPN, and unknown interfaces plus one route-aware preferred LAN address. Proxy UI, Wi-Fi sharing, and companion discovery consume that same selection. The selector considers the OS default route, multicast capability, and Windows/macOS/Linux virtual-adapter identities without rejecting legitimate private address ranges such as `172.16.0.0/12`.

**Remaining scope:** The unified selector is intentionally IPv4 for the current proxy and LAN-discovery protocols. IPv6 endpoint publication and finer-grained channel preservation remain future work.

**Former impact:** Stale setup instructions, missed interface transitions, and inconsistent proxy/Wi-Fi/companion endpoint state.

**Original severity:** **P2 — Medium**

**Recommendation:** Keep endpoint derivation centralized in the platform network monitor and extend its typed snapshot when IPv6 publication or additional connectivity artifacts are introduced.

### F-24 — Logging configuration is nominal and hot-path logging is excessive

**Finding:** Logger configuration is stored but not consumed by `KNetLogger`; bootstrap reports a configured directory without installing a file writer. Proxy hot paths create INFO messages per request/response and a DEBUG Netty logging handler is installed.

**Evidence:** `KNetLogger.kt` calls global Kermit `Logger`; `LoggerFactory.kt` holds configuration but `KNetLogger` never reads it. `DesktopBootstrap.kt:34-39` only logs that configuration occurred. `KNetProxyServer.kt:87` adds `LoggingHandler(DEBUG)` and `KNetProxyHandler.kt:185`, `400`, and `433` log per message.

**Why it matters:** Logging behavior does not match configuration/docs, can add synchronization/allocation in the event loop, and has no structured correlation, retention, or central secret-redaction policy.

**Impact:** Performance noise, difficult diagnostics, misleading configuration, and possible future secret leakage.

**Severity:** **P2 — Medium**

**Recommendation:** Install an actual asynchronous bounded logging pipeline at bootstrap, make traffic-summary logging sampled/DEBUG, add structured connection/exchange IDs, redact sensitive fields centrally, and define file rotation/retention. Keep diagnostic logs separate from captured traffic.

### F-25 — Static mutable registries hide ownership and constrain multiple runtimes

**Finding:** Proxy pipeline initializers, breakpoint rules/sessions, and mutable timeout configuration are process-global singletons.

**Evidence:** `KNetProxyServer.kt:49-55` exposes a companion initializer list; `ProxyRuntimeRepository.kt:33-38` clears and repopulates it. `BreakpointRuleRegistry`, `InterceptSessionManager`, and `InterceptCoordinator.timeoutMs` are global mutable objects.

**Why it matters:** Tests and runtime instances affect each other, state survives feature recomposition, multiple proxy sessions cannot coexist safely, and shutdown ownership is unclear.

**Impact:** Order-dependent tests, cross-session leakage, and brittle plugin/provider composition.

**Severity:** **P2 — Medium**

**Recommendation:** Construct immutable pipeline factories and session-scoped rule/interception services through the composition root. Inject clocks/timeouts and close scopes with the session.

### F-26 — The implementation contains dormant duplicate capabilities and documentation overclaims

**Finding:** Connection pooling, bounded `SessionManager`, WebSocket handlers, gRPC decoding, traffic simulation/modification, and several protocol formatters exist but are not connected to the production proxy. README claims them as available and describes an obsolete module count.

**Evidence:** Production reference searches find no use of `ProxyConnectionPoolManager`, `SessionManager`, WebSocket proxy handler/parser, or gRPC decoder in pipeline composition. The active upstream path always constructs `Bootstrap` in `KNetProxyHandler.kt:217`. `settings.gradle.kts` includes 28 modules while README describes 11 and calls the system cross-platform despite JVM-only targets.

**Why it matters:** File presence is not architectural support. Dormant implementations increase maintenance and give reviewers/users false confidence, especially for streaming protocols.

**Impact:** Feature sprawl, misleading product promises, and effort spent maintaining code that does not prove integration.

**Severity:** **P2 — Medium**

**Recommendation:** Label capabilities as implemented, experimental, or planned. Either wire each through a real E2E test and ownership model or remove/quarantine it. Generate module/capability documentation from tested registrations where practical.

### F-27 — Tests provide false confidence in proxy stress and integration behavior

**Finding:** Several tests named stress, lifecycle, connection reuse, performance, HTTPS, or pipeline integration exercise only isolated objects or server start/stop, not traffic through a real proxy.

**Evidence:** `LargePayloadTest.kt:7-16` allocates/releases one 10 MiB `ByteBuf`; `ConnectionReuseTest.kt` and `ConnectionLifecycleTest.kt` close empty `EmbeddedChannel`s; `PerformanceRegressionTest.kt` asserts certificate duration is non-negative; `HttpsConnectTest` checks a CONNECT 200 in an embedded pipeline without a full TLS/upstream exchange; `ProxyPipelineIntegrationTest` primarily starts/stops the server. Certificate concurrency tests check cache size, not single generation or returned identity.

**Why it matters:** The exact failures found here—buffer ownership, pipelining, event-loop blocking, bind rollback, backpressure, response loss, large-body behavior—require integrated concurrency and failure tests.

**Impact:** Regressions can ship behind a green suite; capacity and safety claims are unsupported.

**Severity:** **P1 — High**

**Recommendation:** Add real loopback E2E fixtures and test byte-for-byte forwarding, TLS verification/failure, concurrent same-host CONNECT, pipelining order, request-only/response breakpoints, disconnects at each phase, slow upstream/client/disk, 10 MiB boundary, streaming larger than memory budget, disk-full behavior, lifecycle races, leak detection, and bounded overload. Add measured JMH/macrobenchmark/soak jobs separately from correctness tests.

### F-28 — Domain purity and Kotlin Multiplatform portability are overstated

**Finding:** Most “multiplatform” modules declare only JVM; common domain code imports Java APIs; concrete desktop engine types are used by feature UIs.

**Evidence:** module build scripts use `jvm()` without other targets; `core/domain/src/commonMain/kotlin/com/devuloopers/knet/domain/traffic/usecase/GetLiveTrafficUseCase.kt:9-13` imports `java.net.URI` from common source. UI dependency relationships listed above expose certificate/interceptor/formatter/script engines.

**Why it matters:** Android/iOS companion apps could share selected value models and serialization, but the current common graph is not a portable engine/application architecture.

**Impact:** Platform expansion requires source-set repair and API separation rather than simply adding targets.

**Severity:** **P2 — Medium**

**Recommendation:** Be explicit that the proxy is JVM desktop. Move portable DTOs/pure domain logic into genuinely common modules, platform URI/network/filesystem behavior behind expect/actual or ports, and do not make UI/application code portable without a real consumer.

### F-29 — CONNECT/Host authority parsing is not robust for IPv6 and malformed input

**Finding:** CONNECT and Host parsing split strings on `:`, assuming `host:port` with a single colon; invalid ports can throw before a controlled proxy response.

**Evidence:** `KNetProxyHandler.kt:85-90` and `127-177`, plus `KNetInterceptorHandler.kt:51-54`, use string splitting rather than an RFC-aware authority parser.

**Why it matters:** Bracketed IPv6 literals, userinfo-like malformed targets, empty hosts, and invalid ports require explicit rejection/normalization.

**Impact:** IPv6 failures, inconsistent routing, and abrupt connection closure on malformed traffic.

**Severity:** **P2 — Medium**

**Recommendation:** Use one validated authority parser covering absolute-form, origin-form plus Host, CONNECT authority-form, IDNA, bracketed IPv6, default ports, and controlled 400/502 results. Fuzz it.

### F-30 — Some abstraction and use-case/module proliferation is premature

**Finding:** Thin one-method use cases, placeholder modules, duplicate HTTP/script/traffic models, and dormant engines add indirection without establishing stronger ownership or invariants.

**Evidence:** `DesktopDataModule.kt:128-152` registers a large set of pass-through factories; `core:pairing`, `engine:simulator`, `engine:traffic`, and parts of `engine:session/protocol` have little or no active runtime role. Conversion code in `KNetApiClient.kt:153-239` bridges overlapping HTTP models.

**Why it matters:** Modularity is valuable when boundaries encapsulate policy. Naming every repository call a use case does not compensate for the missing session/lifecycle orchestration layer.

**Impact:** Cognitive load, duplicated conversion defects, and slower refactoring.

**Severity:** **P3 — Low**

**Recommendation:** Keep use cases that express policy, authorization, transactionality, or orchestration; allow cohesive query/application services for simple reads. Merge or quarantine unused modules after compatibility review. Do not collapse feature UI modules that already have clear ownership.

## H. Scalability analysis

### Connection scalability

These are architectural estimates, not measured limits. A “connection” here can generate multiple requests; body size, TLS cache hit rate, disk speed, logging, and UI retention dominate a raw socket count.

| Concurrent client connections | Expected current behavior | Limiting mechanisms | Assessment |
|---:|---|---|---|
| 10 | Small HTTP/1.1 requests should usually work in controlled tests. Concurrent new HTTPS hosts can visibly pause on RSA generation. | synchronous listener/file/parse work; one upstream TCP/TLS setup per request | development-usable, not latency-predictable |
| 100 | Small bodies may work, but bursts create DB tasks and full-table invalidations. Ten-megabyte bodies can consume hundreds of MiB to GiB-scale transient memory. | event-loop stalls, connection setup, file descriptors, direct buffers, unbounded IO jobs | unreliable under realistic mixed traffic |
| 1,000 | Netty can accept this order of sockets in principle, but this architecture creates per-request upstream sockets with no active pooling, no connection quotas, no capture admission, and no bounded writer. | OS descriptors, handshakes, disk/Room backlog, logging, heap/direct memory | likely collapse or extreme latency; not production-ready |
| 10,000 | No design provision exists for client quotas, idle eviction, write watermarks, slow-client policy, session overload, or operational metrics. | all preceding limits plus security exposure | unsuitable |

No responsible requests-per-second number can be inferred from source. KNet needs benchmark workloads split by: metadata-only HTTP, 1/10/100 MiB bodies, TLS cache hit/miss, slow client, slow upstream, slow disk, interception, and UI open/closed. The current callback/writer path has no stable saturation point because backlog is unbounded.

### Captured-request/UI scalability

| Rows in session | Expected current behavior | Dominant complexity | Assessment |
|---:|---|---|---|
| 100 | Responsive for ordinary metadata and a few opened bodies. | small full-list queries/maps | acceptable |
| 1,000 | Generally usable on a fast machine, with increasing allocations on every write. | O(n) query + repository map + use-case map per invalidation | conditional |
| 10,000 | Frequent capture is likely to cause jank, GC pressure, filtering delays, and Room backlog even though rows are virtualized. | approximately O(n × writes), in-memory filtering/counts | poor |
| 100,000 | Full-table flow snapshots and repeated mapping are architecturally untenable; body files and unindexed search further worsen it. | huge snapshots, allocation rate, disk retention | unsupported |

### Large-body scenarios

**500 MiB response:** current behavior is failure around the 10 MiB upstream aggregator ceiling, not streaming, lazy persistence, or capture truncation. This protects KNet from holding 500 MiB only by ceasing to function as a transparent proxy.

**10 concurrent × 50 MiB:** none can complete through the normal path; each can allocate toward the 10 MiB aggregator limit before rejection. Approximately 100 MiB of direct aggregate buffers can coexist, plus heap copies, error/callback objects, allocator capacity, and client-side/request buffers. The correct conclusion is “fails with substantial transient memory,” not “uses 500 MiB.”

**100 concurrent × 10 MiB:** near the accepted boundary, response aggregation alone is about 1,000 MiB of Netty buffers. `HttpMapper` creates heap `ByteArray` copies, persistence/callbacks retain bodies, and client writes retain messages. A plausible transient range is **2–3 GiB plus JVM/Netty overhead**, depending on phase overlap. This is a lifetime-based estimate, not a measurement. Requests can add similar pressure.

### Performance bottleneck ranking

1. **Transport buffering/copying:** O(total body bytes) direct buffer plus at least one heap copy; formatted views add more strings.
2. **Capture persistence invalidation:** O(session row count) work per write, currently approximately two writes per exchange.
3. **Event-loop synchronous work:** latency couples unrelated channels sharing an event loop.
4. **One-shot upstream connections:** DNS/TCP/TLS cost per request and no reuse despite an unused pool class.
5. **UI filtering/derived state:** repeated O(n) scans and full state snapshots.
6. **RSA leaf generation:** expensive on new hosts and duplicate under concurrent cache misses.
7. **Logging:** per-message construction/dispatch on event loops.

## I. Memory and body model

### Object lifetime table

| Object/data | Created at | Strongly retained by | Released/evicted when | Risk |
|---|---|---|---|---|
| inbound request `ByteBuf` | client aggregator | `FullHttpRequest`, retained outbound request; interception may add extra ref | downstream consumption/write completion; interception currently leaks one ref | up to 10 MiB direct memory each |
| domain request body `ByteArray` | `HttpMapper.mapRequest` | `HttpRequest`, `pendingRequests`, DB-launch closure, interception event | response removes pending; stop clears; intercept leak/lost response can extend it | full heap copy per request |
| upstream response `ByteBuf` | upstream aggregator | `FullHttpResponse`, retained client write | handler auto-release + write completion; interception currently leaks one ref | up to 10 MiB direct memory each |
| domain response body `ByteArray` | `HttpMapper.mapResponse` | callback locals, DB task/transaction | callback/task completion | full heap copy per response |
| payload file | capture callback | filesystem, DB path | no automatic production pruning; DB clear does not delete | unbounded disk and secret lifetime |
| Room entity/list snapshot | every invalidation | Room Flow, repository collector, use-case map, ViewModel state | next emission/VM destruction | O(all rows) repeated allocations |
| traffic UI rows | use case | `TrafficState.transactions` and usually same-reference filtered list | next state/session/VM | all metadata retained |
| selected raw/formatted body | body loader/formatter | prepared state and 64-entry LRU | weighted-unaware entry eviction/VM destruction; not Clear | potentially >1 GiB, sensitive |
| intercepted event body | request/response mapper | global `InterceptSessionManager` StateFlow plus Netty retained message | user action/timeout; buffer bug leaves Netty ref | full body duplicated across models/buffers |
| leaf certificates/private keys | certificate generator | `CertificateCache` map | all 1,000 entries cleared at once/process exit | key material and heap bursts |
| root CA/private key | startup | singleton repository/process and disk | process exit/manual file removal | security-critical indefinite lifetime |
| Flow state | Room/StateFlow | most recent full value per active layer | replacement/cancellation | no large replay buffer, but one large snapshot per layer |

### Body-type behavior

| Body kind | Current transport/capture behavior | Architectural issue |
|---|---|---|
| text/JSON/XML/HTML | fully aggregates, copies, persists; selected body fully reads/decodes/formats | synchronous GraphQL parse; unbounded formatting/read; multiple copies |
| binary/images | fully aggregates/copies/persists; UI may preview/format based on type | `ByteArray` everywhere; no safe preview budget; exports may treat bytes as text |
| compressed | proxy forwards encoded bytes; editing/formatting policy is not a robust decode/re-encode pipeline | content encoding and length can become inconsistent after modification |
| chunked | aggregator removes streaming advantage and produces a full message | nominal chunk branch is shadowed by aggregator |
| SSE | waits for completion that normally never occurs | live event streaming unsupported |
| multipart/form | full aggregation; no streaming part model | large uploads fail; mutation semantics unclear |
| protobuf/gRPC | formatter/decoder artifacts exist | no HTTP/2 transport or message/trailer pipeline |
| unknown | full aggregation and raw persistence | no metadata-only/body-skip policy |

The target body abstraction should not be `ByteArray?`. It should be a reference with observable capture status, for example:

```text
BodyRef(
  storageKey,
  observedBytes,
  storedBytes,
  contentEncoding,
  digest,
  outcome = Complete | Truncated(limit) | Skipped(policy) | Failed(reason)
)
```

Chunks should flow network-to-network independently of whether capture succeeds. Inspectors receive a bounded preview or an explicit streaming subscription, never implicit ownership of the transport buffer.

## J. Concurrency, backpressure, and event architecture

### Actual thread/dispatcher crossings

```text
Netty worker EventLoop
  |-- parse/aggregate up to 10 MiB
  |-- certificate generation on CONNECT miss             DANGER: CPU blocking
  |-- body map/copy, rule regex/body decode               DANGER: CPU/allocation
  |-- listener callback
  |     |-- Files.write payload                           DANGER: blocking IO
  |     |-- GraphQL/Jackson inspect                       DANGER: CPU
  |     +-- launch on process-lifetime Dispatchers.IO
  |            +-- Room REPLACE (unbounded jobs/order race)
  |
  |-- launch proxyScope(Dispatchers.IO) for InetAddress lookup
  |     +-- schedule continuation back on EventLoop
  |
  +-- interception CoroutineScope(EventLoop + new SupervisorJob)
         +-- await UI decision while autoRead=false
         +-- buffer reference retained incorrectly

Room invalidation/query (Room/IO)
  -> repository maps complete table
  -> use case flowOn(Dispatchers.IO) maps/filter complete table
  -> TrafficViewModel collector / Main state mutation
  -> Compose snapshot + LazyColumn

Body selection
  -> ViewModel coroutine
  -> File.readBytes + decode + formatter
  -> Main StateFlow + 64-entry cache
```

### Shared-state inventory

- `KNetProxyServer.pipelineInitializers`: unsynchronized global mutable list, cleared/repopulated during runtime construction.
- `ProxyEngineRepositoryImpl.pendingRequests`: thread-safe map operations, but lifecycle semantics and body bound are unsafe.
- proxy state: StateFlow plus check-then-act start/stop operations, not serialized.
- `CertificateCache`: concurrent map with non-atomic generation sequence.
- imported-client-certificate lists: synchronized wrappers but some compound iteration/update sequences are not atomic.
- `BreakpointRuleRegistry`, `InterceptSessionManager`, interception timeout: global process state.
- `TrafficViewModel` LRU: effectively ViewModel-confined in expected use but sensitive to async completion and lacks weighted bounds.

No `GlobalScope` was found, but process-lifetime, never-cancelled custom scopes in repositories are equivalent ownership smells.

### Is there a central event bus?

No. Traffic propagation is a single synchronous `ProxyTrafficListener` callback followed by database invalidation flows. That is not inherently wrong: a general-purpose event bus would add ambiguity. What is missing is a **session-scoped ordered capture ingress** with explicit capacity, lifecycle, and subscriber contracts.

Recommended event separation:

```text
transport-only state (never exposed outside proxy implementation)
  ConnectionOpened / Closed / TLS / stream state

bounded application capture ingress
  ExchangeStarted
  RequestHeadersObserved
  RequestBodyProgress/Completed
  ResponseHeadersObserved
  ResponseBodyProgress/Completed
  ExchangeFailed/Dropped

persistent metadata query API
  paged rows + indexed filters + aggregate counters

body API
  BodyRef + bounded range/preview/stream reads

presentation events
  selection, filter edits, dialogs, notifications
```

Transport forwarding must not wait for UI or persistence. Capture should preserve per-exchange ordering, allow concurrent exchanges, and report overload/truncation instead of silently growing or dropping.

## K. Security assessment

### Threat summary

| Surface | Current control | Risk | Required posture |
|---|---|---|---|
| proxy listener | `0.0.0.0`, no auth | critical open proxy and resource abuse | loopback default; authenticated paired LAN mode; quotas |
| onboarding portal | same listener, path-based dispatch, no auth | route collision, host/template injection, information disclosure | dedicated authority/listener, escaped templates, tokenized setup |
| PAC | not implemented | no current endpoint, but plan lacks deployed controls | signed/versioned or tokenized URL as appropriate; no secrets in script; strict escaping |
| upstream TLS | trust-all default | KNet accepts spoofed remote peers | strict verification default, scoped override, visible result |
| root CA key | plaintext PEM | local compromise enables interception | OS keystore or encrypted/0600 storage, rotation/export controls |
| imported mTLS keys | copied by alias to plaintext path | secret theft and possible path manipulation | sanitize alias, secure key store, passphrase lifecycle |
| captured headers/bodies | plaintext DB/files, no redaction, no retention | credential/privacy disclosure | encrypted sessions, redaction, limits, complete clear |
| logs | global/unstructured; current proxy mostly logs URL/status | future secret leakage and noisy hot path | centralized redaction, structured IDs, retention |
| scripts | JS partial sandbox, non-preemptive timeout; Kotlin trusted JVM | denial of service/arbitrary code when untrusted | process isolation or trusted-only designation |
| outbound destinations | general proxy behavior, no policy | SSRF-like reachability of local/private services by LAN clients | bind/auth policy plus optional destination restrictions |

The correct default policy is not “LAN always” or “localhost always.” It is:

- ordinary desktop inspection: loopback-only proxy and portal;
- explicit device onboarding session: selected LAN interfaces, paired client identity/token, short expiry, visible status, revocation, and rate limits;
- future remote relay: a separate authenticated transport/service, not an exposed local proxy port;
- certificate download can be public only within a deliberately scoped onboarding session; key material is never served.

## Failure-mode and resilience assessment

| Failure | Current behavior | Needed behavior |
|---|---|---|
| proxy port unavailable | start throws after groups/scope allocation; runtime may lose handle | atomic rollback, release/await groups, `Failed(PortInUse)` state |
| certificate generation fails | exception closes channel through generic handler | bounded off-loop task, per-host failure cache/backoff, controlled TLS error |
| DNS/connect fails | creates 502 and generally closes client | typed failure, release outbound request, preserve keep-alive only when safe |
| upstream TLS fails | 502 path exists; trust-all masks most verification failures | strict result metadata, scoped override, no double response after handshake/connect races |
| client disconnects | client channel closes; pending map/session/async writes may survive | cancel exchange/upstream/capture chunks and terminally reconcile record |
| upstream disconnects mid-body | generic close/error; aggregator may discard partial data | forward correct terminal semantics, store partial/truncated status |
| slow client/upstream | no explicit idle/read/write timeout or watermark policy | phase-specific timeouts, write-buffer watermarks, backpressure/cancellation |
| disk full/permission error | synchronous callback throws/logs; capture/forwarding coupling unclear | network continues when policy allows; body marked failed; visible storage health |
| memory pressure | no capture adaptation | switch to metadata-only/truncation, evict cache, surface overload |
| DB writer lag | unbounded coroutine backlog | bounded queue/batches, metrics, overload policy |
| IP/interface/VPN change | IPv4 poll; close all channels if value changes | snapshot-based reconcile, artifact versioning, targeted connection policy |
| UI/ViewModel disappears | proxy/repository scopes continue, which avoids UI dependence, but state ownership remains global | engine continues by application policy; UI dynamically subscribes to paged store |
| app window closes | no explicit resource closure | ordered, awaited normal shutdown with deadline and final session flush |
| traffic limit reached | no production limit; files persist | deterministic eviction/retention with pinned/exported session semantics |
| certificate cache limit | clear all entries | weighted LRU/TTL, no thundering herd |

## L. Protocol and extensibility assessment

### HTTP protocol readiness

**HTTP/1.1:** only fully aggregated, mostly one-request/one-upstream-connection exchanges are supported. Keep-alive is accepted on the client, but upstream reuse is absent and pipelining ordering is unsafe. Chunked forwarding is neutralized by aggregation. Upgrade and trailers are not modeled robustly.

**HTTP/2:** not supported. No ALPN negotiation, HTTP/2 codec/multiplex handler, stream IDs, pseudo-headers, flow control, server push policy, stream cancellation, or per-stream capture exists. The single mutable `REQUEST_ATTR`, `FullHttpRequest`/`FullHttpResponse` domain shape, and request-per-upstream-channel assumption cannot represent multiplexing. Adding a Netty H2 codec would not be sufficient; the connection/exchange and body/event architecture must change first.

**HTTP/3/QUIC:** not supported and would require a QUIC-capable transport, UDP lifecycle, certificate/ALPN handling, connection migration semantics, and multiplexed stream capture. With a protocol-neutral connection/exchange core it can be an additional adapter. With today's handler/domain models it is a rewrite.

### WebSocket readiness

The repository contains WebSocket parsing/handler code, but the production pipeline never performs a complete upgrade and switches to a long-lived frame pipeline. `KNetOutboundHandler` treats a 101 like an ordinary full response and closes the one-shot upstream channel. Existing parser limits are applied after converting text frames to strings, and fragmentation/continuations and retention policies are incomplete.

The right model is a `DuplexConnection` with directional frame/message events, not a completed `HttpTransaction`. It needs connection lifetime, upgrade exchange link, opcode, direction, fragmentation, ping/pong/close, binary preview/body reference, sequence/timestamp, bounded retention, and independent network/UI backpressure.

### GraphQL readiness

GraphQL is the best-factored protocol capability: a registry invokes a semantic inspector without branching in the proxy handler. However, inspection runs synchronously in the listener, metadata is a closed/sealed domain hierarchy, and persistence/UI schemas must know the result. Adding another bounded HTTP semantic inspector is moderately feasible; adding third-party inspectors is not.

Inspectors should consume immutable headers plus a bounded body view after capture, declare media/protocol predicates and resource budgets, and return versioned open metadata. Inspector failure must never affect forwarding.

### Feature-by-feature change test

| Feature | Modules/classes that must change now | Independence | Rewrite risk / recommendation |
|---|---|---|---|
| WebSocket inspector | proxy pipeline/handler, interceptor, domain models, storage schema, data repository, traffic UI, protocol engine | low | **major refactor**; first introduce connection/frame model and streaming proxy |
| GraphQL inspector | protocol registry/inspector, capture worker, metadata persistence/UI | medium | incremental after moving parse off-loop and opening metadata extension format |
| gRPC inspector | proxy H2 transport, stream/message decoder, body store, protocol engine, storage/UI | very low | **major refactor** because H2/stream/trailer model is prerequisite |
| SSE inspector | proxy aggregation/forwarding, timeline event store, UI | very low | **major refactor**; requires true streaming and bounded event retention |
| HTTP/2 | proxy TLS/ALPN/pipeline, connection/exchange correlation, traffic model, interception, storage/UI | none | **transport-core refactor**, not an incremental handler insertion |
| HTTP/3 | new QUIC engine/adapter plus same protocol-neutral core changes | none | new transport after HTTP/2-grade model; current architecture requires rewrite |
| custom inspectors/plugins | domain sealed metadata, registry construction, classloading/security, persistence/UI | low | introduce a versioned inspector SPI only after resource isolation and open metadata |
| mobile companion | network/connectivity domain, application lifecycle, portal, pairing/auth, UI/platform modules | low | significant new architecture; share portable setup/session DTOs, not desktop engine |
| remote relay | proxy access policy, authenticated relay transport, session identity, security/storage | none | separate service/adapter; never expose current open proxy remotely |
| HAR export | dormant session/export code, paged repository/body reader, UI | medium | incremental if export streams rows/bodies and supports redaction/truncation |
| request rewrite rules | traffic/interceptor duplication, framing/body policy, storage/UI | medium-low | consolidate rule engine and execute within safe streaming/full-body policy |
| response rewrite rules | same plus response streaming/content encoding | low | significant until body/framing pipeline is corrected |
| breakpoints | current interceptor/coordinator/session/UI | already present but unsafe | repair ownership, lifecycle, phase state, correlation, and capture semantics first |
| script transformations | script engine, proxy/capture workers, rules, security UI | low | separate-process sandbox and explicit body budget required before hot-path use |

### Connectivity-provider abstraction evaluation

The planned vocabulary is directionally useful but one uniform provider lifecycle would hide real differences:

- PAC and Apple profiles primarily **generate/distribute artifacts**; they are not continuously active mechanisms inside KNet.
- manual proxy is chiefly instructions plus an externally observed client connection.
- ADB reverse executes and monitors an OS process/command with device state.
- companion pairing is an authenticated multi-step session with user action and expiry.
- upstream VPN owns a long-lived privileged tunnel with routes and health.
- remote relay owns authentication, reconnect, server availability, and remote session identity.

Use two small contracts rather than a “god provider”:

```text
SetupDescriptorProvider
  id, platform support, capabilities
  availability(context): Flow<Availability>
  describe(context): SetupDescriptor / artifacts

ManagedConnectivityMechanism (only where activation exists)
  state: Flow<LifecycleState>
  activate(context)
  deactivate()
  health: Flow<Health>
```

Composition can expose both through a registry keyed by stable IDs; UI renders capabilities/actions, not enum-specific screens. Artifact providers need no fake `activate()` method.

Availability, lifecycle, and health should be separate:

```text
Availability = Supported | PlatformUnsupported | DependencyMissing |
               PermissionRequired | NetworkUnavailable | PolicyDisabled

Lifecycle = Inactive | Activating | NeedsUserAction | Active |
            Deactivating | Failed

Health = Unknown | Healthy | Degraded(reason) | Unreachable(reason)
```

### PAC design assessment

Because no PAC code exists, the following is a target contract rather than a finding about current implementation:

- `PacConfiguration` is a validated domain value: proxy endpoints, bypass rules, selected domains, direct-fallback policy, and version.
- `GeneratePacScript` is a pure deterministic function with correct JS escaping, IDNA/IPv4/bracketed-IPv6 normalization, explicit `DIRECT` policy, exact/suffix/wildcard matching rules, localhost/private-address policy, and golden/fuzz tests.
- `PacRepository` stores user configuration/version; it should not discover interfaces or render HTTP responses.
- application orchestration combines network snapshot + configuration into a versioned `PacArtifact`, caches by inputs, and invalidates when either changes.
- portal delivers `/proxy.pac` and optional `/pac` only under a validated authority/session and emits `ETag`/cache policy. It does not invent IP addresses.
- malformed configuration fails closed with a visible error rather than serving a syntactically valid but unsafe script.

Generating on every HTTP request is unnecessary. Cache by `(configurationVersion, networkSnapshotVersion, accessPolicyVersion)`. Network changes should publish a new URL/artifact version where clients might otherwise keep stale content.

## M. Recommended target architecture

This is an incremental target. It does not require replacing Compose, Koin, Netty, Room, Ktor, or the existing feature UI modules.

### KEEP

- Netty as the JVM proxy transport.
- Compose Desktop and the feature-oriented UI modules.
- Koin at the desktop composition root.
- Room for indexed traffic metadata and collections.
- File/disk-backed lazy body storage as a concept.
- repository ports and policy-bearing use cases.
- the GraphQL inspector strategy direction.
- certificate generation primitives and SAN handling after concurrency/security repair.
- `LazyColumn` traffic rendering and stable row IDs.
- bounded-session ideas from `engine:session`, moved to the production path.

### MODIFY

- `:engine:proxy` into a streaming, protocol-aware transport adapter with private Netty types and per-connection/per-exchange state.
- capture callbacks into a non-blocking, bounded `CaptureSink` contract with explicit admission results.
- traffic storage into ordered/batched session writing, indexed pagination, byte/count/time retention, and complete cleanup.
- TLS into verified-by-default upstream behavior, off-loop single-flight leaf generation, and observable TLS metadata.
- breakpoints into phase-specific exchange state with correct buffer ownership and transport backpressure.
- body inspection into bounded previews/streaming body references with explicit truncation and decode policy.
- ViewModels into small paged metadata state plus independently cancellable detail state.
- logging into asynchronous bounded diagnostics with correlation/redaction.

### MOVE

- proxy/portal/connectivity/certificate orchestration from `ProxyEngineRepositoryImpl`, `ProxyRuntimeRepository`, ViewModels, and handlers into an application-owned session/lifecycle service.
- `TrafficItemUiState`, UI intents, display sizes/colors/formatting out of `:core:domain` and into presentation modules.
- Room dependencies and database-specific session behavior out of `:engine:session` and into desktop infrastructure.
- filesystem CA/key/body operations behind infrastructure ports; secure-key policy into a desktop security adapter.
- portal interface discovery and setup decisions into network-monitor/application services.
- concrete engine construction out of `:data:desktop` internals and exclusively into the app composition root/factories.

### REMOVE OR QUARANTINE

- static `pipelineInitializers` and other process-global session registries.
- full-message aggregators from the default transport path.
- the duplicate request/response `REPLACE` writer and unbounded pending full-body map.
- trust-all as the default.
- dormant capability claims until an E2E registration proves them.
- unused `ProxyConnectionPoolManager` or, after correctness work, replace it with a production-tested upstream connection manager.
- duplicate transport/domain/UI HTTP models where they do not express different semantics.
- pass-through use cases that add no policy after API compatibility review.

### INTRODUCE

- a desktop application layer owning `CaptureSessionController`, `ProxyLifecycleController`, `ConnectivityCoordinator`, export/clear policies, and structured application errors;
- immutable `ConnectionId`, `ExchangeId`, `StreamId?`, connection/TLS snapshots, exchange lifecycle events, and `BodyRef`;
- a bounded byte-aware capture ingress and one ordered `SessionWriter` per session;
- a streaming `BodyStore` with per-body/per-session/global quotas and encryption/redaction options;
- indexed paged `TrafficQuery` plus incremental session metrics;
- `ProxyAccessPolicy`/pairing, interface binding policy, client quotas, and destination policy;
- a platform `NetworkMonitor` producing versioned snapshots;
- separated artifact-provider and managed-connectivity contracts;
- metrics for active connections/streams, event-loop lag, capture queue depth/bytes, truncations, dropped metadata, DB batch latency, body-store bytes, TLS generation, and UI query latency;
- a small versioned semantic-inspector SPI running outside the transport event loop.

### Target dependency model

```text
                      :products:desktop
                    composition + process lifecycle
                       /         |          \
                      v          v           v
           presentation UIs   application   desktop infrastructure
                 |               |  ^        | Room/body/key/network
                 | queries/cmds  |  | ports  |
                 +-------------->|  +--------+
                                 |
                         stable domain values/policy
                                 ^
                                 |
             +-------------------+-------------------+
             |                   |                   |
       :engine:proxy       :engine:portal     semantic inspectors
       Netty internal      HTTP delivery       GraphQL/etc.
             |                   |                   |
             +---------- application contracts ---------+

Rules:
- engine modules do not depend on UI, Room, filesystem implementations, or one another's internals;
- UI does not depend on concrete engines;
- domain has no logger, Java URI, Compose, filesystem path, Netty, or Room type;
- application owns ordering, policy, lifecycle, and cross-engine orchestration;
- infrastructure implements persistence, secure key, network, and OS ports.
```

Do not create every box as a Gradle module immediately. First establish package/API ownership in `:data:desktop` and `:core:domain`; extract a new `:application:desktop` only when the contracts stabilize. Module count is not the objective—enforced dependency direction is.

### Target runtime and backpressure model

```text
client channel                                  upstream channel
     |                                                ^
     | headers/content                                |
     v                                                |
per-connection protocol state ---- forward immediately/with Netty watermarks
     |
     +-- create immutable small capture event
     |       |
     |       v
     |   CaptureSink.tryPublish(event)
     |       | accepted / metadata-only / truncate / overloaded
     |       v
     |   bounded byte-aware queue
     |       v
     |   ordered SessionWriter -> transaction-safe Room batches
     |                     \----> streaming encrypted BodyStore
     |
     +-- breakpoint policy -> bounded pause state with timeout/cancellation

Room indexed pages -> TrafficQuery -> ViewModel page state -> LazyColumn
BodyRef -> bounded preview/range reader -> detail ViewModel -> formatter worker
```

Network forwarding and capture durability are separate failure domains. A body-store failure marks capture as failed but does not corrupt or silently hang the proxied exchange. For a breakpoint, deliberate user pause is a transport policy and may apply Netty backpressure, but it remains bounded by connection count, bytes, and timeout.

### Target lifecycle model

```text
Stopped
  -> Starting (allocate local resources)
       -> Running(sessionId, bindings, health) only after full success
       -> Failed(reason) after reverse-order rollback
  -> Stopping (reject new work, close listeners, drain/cancel capture, flush DB)
       -> Stopped
```

All commands are serialized by one mutex/actor. Repeated start/stop is idempotent; cancellation is handled as a state transition; no partially constructed resource becomes globally visible. Shutdown has a deadline and reports any forced close.

### Target traffic and protocol model

```text
ConnectionRecord
  id, transport, local/remote endpoints, opened/closed, TLS snapshots, error

ExchangeRecord
  id, connectionId, streamId?, protocol, request/response metadata,
  lifecycle state, timings, body refs, semantic annotations, error

DuplexMessageRecord
  connectionId, sequence, direction, kind/frame metadata, bodyRef, timestamp
```

HTTP/1 maps one ordered exchange sequence to a connection. HTTP/2 and HTTP/3 map concurrent `streamId`s. WebSocket maps its upgrade exchange to a duplex connection and frame/message records. SSE maps a response body to a live event stream/timeline without pretending the exchange completed. gRPC maps H2 data/trailers to message annotations without replacing the underlying exchange.

### Target UI state

```text
TrafficListState
  session summary + query + loaded pages + selection ID

TrafficDetailState
  selected metadata + bounded body previews + loading/error/truncation

LiveMetricsState
  rates, active connections, capture backlog/overload
```

The ViewModel never owns every body. It may own a bounded window of row metadata and one or a few weighted previews. Filtering, sorting, search, protocol/status counts, and total bytes should be indexed/aggregated by the store. Navigation should create feature ViewModels only when needed and cancel detail work on selection changes.

### Testing architecture for the target

- **Unit:** authority parsing/fuzzing, PAC generation/golden JS, rule matching, lifecycle reducer, body policy, capture admission, state transitions, redaction.
- **Component:** streaming proxy codecs with `EmbeddedChannel`, exact `ByteBuf` ref-count assertions, session writer reordering/idempotency, migrations, weighted caches.
- **Loopback integration:** real client -> KNet -> controllable origin for HTTP/1 keep-alive/pipelining, CONNECT/TLS, slow/chunked/large/compressed/trailer/malformed traffic, breakpoint branches, portal authority/auth.
- **Concurrency:** same-host certificate single-flight; simultaneous streams/connections; start/stop races; network snapshot changes; slow disk/collector/client/upstream.
- **Stress/soak:** thousands of small requests, connection churn, multi-hour capture, bounded 100+ MiB pass-through, UI open/closed, queue saturation, file-descriptor and Netty leak monitoring.
- **Failure:** port unavailable, DNS/TLS errors, half-close, disk full, DB failure, body quota, script timeout/kill, app shutdown deadline.
- **Performance:** JMH for parsers/mappers/cache and a repeatable macrobenchmark reporting throughput, p50/p95/p99 latency, heap/direct memory, allocations, queue depth, DB latency, and UI frame time.

The 2026-08-18 verification run executed `./gradlew check :products:desktop:assemble --rerun-tasks --no-daemon`: **BUILD SUCCESSFUL**, 232 actionable tasks executed in 1m 51s. Parsed JUnit XML reports contain **662 tests, 0 failures, 0 errors**. This is a healthy baseline for refactoring, not a proxy capacity certification.

## N. Migration plan

Each phase has a completion gate. Do not begin protocol or connectivity expansion while a preceding safety/data gate is open.

### Phase 0 — Safety / critical fixes

**Exact changes**

1. Change default binding to loopback; add an explicit temporary LAN mode and visible warning while full pairing is developed.
2. Make upstream TLS strict by default and expose an explicit per-host override.
3. Correct `InterceptCoordinator` reference ownership for every terminal path and add Netty leak tests.
4. Replace dual unsequenced `REPLACE` launches with monotonic/idempotent transaction updates; fix request-only breakpoint response completion.
5. Serialize start/stop, roll back partial start, await Netty termination, and wire normal app close to resource shutdown.
6. Add missing Room migrations and remove destructive fallback.
7. Apply owner-only permissions immediately to CA/imported-key/body/database directories; sanitize certificate aliases; make Clear delete payloads and body caches.
8. Add robust authority parsing and framing normalization for edited messages.

**Affected modules:** `:engine:proxy`, `:engine:interceptor`, `:engine:certificate`, `:engine:session`, `:storage`, `:data:desktop`, `:products:desktop`, traffic UI tests.

**Dependencies:** none; this is the prerequisite for all later work.

**Risk:** high because buffer ownership, persistence, and shutdown touch active behavior. Keep patches small and guarded by real loopback tests.

**Expected benefit/gate:** no known direct-memory leak in breakpoint branches; no backward DB transition; secure default listener/TLS; deterministic start/stop/upgrade/clear. All P0 correctness/security findings must be closed or explicitly feature-disabled.

### Phase 1 — Boundary corrections

**Exact changes**

1. Add application-owned `ProxyLifecycleController` and `CaptureSessionController` (initially inside `:data:desktop` if minimizing modules).
2. Replace global pipeline initializer/rule/session registries with session-scoped injected factories/services.
3. Define small proxy, certificate, network monitor, portal delivery, and traffic query ports without Netty/Room/file/UI types.
4. Route feature ViewModels only through application commands/queries; remove direct certificate/portal/interceptor construction from UI.
5. Move UI state/format values out of `:core:domain`; remove Java URI/logger dependencies from pure domain paths.
6. Reduce public engine API and hide concrete handlers/Netty types.

**Affected modules:** `:core:domain`, `:data:desktop`, `:products:desktop`, all engine facades, `:ui:desktop:{app,traffic,certificate,settings,breakpointManager}`.

**Dependencies:** Phase 0 stable lifecycle tests.

**Risk:** medium; mostly compile-time/API churn, with DI migration risk.

**Expected benefit/gate:** enforced dependency direction, independently testable application orchestration, no UI-to-concrete-engine edge, and no process-global proxy session state.

### Phase 2 — Traffic / data architecture

**Exact changes**

1. Introduce IDs, connection/exchange lifecycle, TLS/error/timing metadata, ordered multivalue headers, and `BodyRef` with capture outcome.
2. Version/migrate the Room schema; index session/timestamp, URL/host, method, status, protocol, and connection/stream IDs.
3. Implement one ordered/idempotent `SessionWriter` with batched transactions.
4. Implement streaming body storage with atomic finalize, byte/count/time quotas, redaction/encryption policy, orphan reconciliation, and complete clear.
5. Add paged/indexed traffic queries and incremental aggregate counters.
6. Convert export to stream pages/body references and honor missing/truncated/redacted bodies.

**Affected modules:** `:core:domain` or a narrowed traffic-model package, `:storage`, `:engine:session` (utilities moved), `:data:desktop`, export consumers.

**Dependencies:** Phase 1 ports/ownership.

**Risk:** high due to schema migration and compatibility. Dual-read/dual-write only if needed for a short migration window; never maintain two authoritative writers.

**Expected benefit/gate:** bounded disk/memory retention, monotonic records, lossless metadata semantics, fast paged queries at 100,000 rows in a dedicated benchmark fixture.

### Phase 3 — Concurrency and backpressure

**Exact changes**

1. Replace default full aggregators with streaming forwarders and per-connection/exchange state.
2. Add a byte-aware bounded capture queue with observable admission/overflow decisions.
3. Move file/parser/certificate/template/network-discovery work off Netty event loops onto bounded executors.
4. Add Netty write watermarks, phase timeouts, slow-client/upstream policy, cancellation propagation, and terminal reconciliation.
5. Implement correct HTTP/1 ordering and only then evaluate upstream connection pooling/reuse.
6. Make certificate generation single-flight and cache weighted/expiring.
7. Add event-loop-lag, queue, memory/body, connection, and writer metrics.

**Affected modules:** `:engine:proxy`, `:engine:interceptor`, `:engine:certificate`, application/session services, `:core:logger`.

**Dependencies:** Phase 2 event/body contracts.

**Risk:** very high; this is the central transport refactor. Use side-by-side loopback correctness suites and packet-level comparisons.

**Expected benefit/gate:** a 500 MiB pass-through succeeds with bounded capture memory; queue saturation produces a visible defined outcome; pipelining order is correct; event-loop blocking detector and leak tests stay clean.

### Phase 4 — UI scalability

**Exact changes**

1. Replace full-session StateFlow with paged list state and database-side search/filter/sort.
2. Split list, selected-detail, breakpoint, and metrics state/owners.
3. Add weighted bounded body preview cache, byte/character limits, range loading, stale-job cancellation, and cache wipe on clear.
4. Compute counts/bytes/rates incrementally in storage/application queries.
5. Ensure feature ViewModels are destination-scoped where appropriate and release body work when hidden/destroyed.
6. Add Compose performance tests at 1k/10k/100k metadata rows.

**Affected modules:** `:ui:desktop:traffic`, `:ui:desktop:app`, `:ui:desktop:breakpointManager`, `:data:desktop`, `:storage`, `:engine:formatter` facade.

**Dependencies:** Phase 2 paged queries and body refs; can overlap late Phase 3.

**Risk:** medium; user-visible state and selection behavior can regress.

**Expected benefit/gate:** UI memory is proportional to loaded pages plus weighted previews, not total session; filtering remains responsive at 100,000 metadata rows.

### Phase 5 — Protocol extensibility

**Exact changes**

1. Stabilize protocol-neutral connection/exchange/duplex-message contracts and semantic-inspector SPI.
2. Implement WebSocket upgrade plus bidirectional frames with bounded retention first.
3. Implement SSE event/timeline inspection on the streaming body path.
4. Add HTTP/2 ALPN/multiplexing/flow-control adapter and H2-aware breakpoints.
5. Add gRPC message/trailer inspector on proven H2 transport.
6. Evaluate HTTP/3/QUIC only after H2/duplex models prove transport independence.
7. Version inspector metadata and isolate parser resource budgets/failures.

**Affected modules:** `:engine:proxy`, `:engine:protocol`, `:engine:interceptor`, traffic data/storage/UI, certificate TLS negotiation.

**Dependencies:** Phases 2–4, especially streaming and stream-aware IDs.

**Risk:** high per protocol; ship independently behind capability flags and E2E conformance suites.

**Expected benefit/gate:** protocols add adapters/inspectors without changing generic capture/storage/UI contracts; advertised capability equals a registered, tested runtime path.

### Phase 6 — Connectivity extensibility

**Exact changes**

1. Implement platform network snapshots and endpoint selection.
2. Add separate setup-descriptor and managed-mechanism contracts with stable IDs/capabilities.
3. Implement pure validated PAC configuration/generation, versioned cache, and `/proxy.pac` delivery.
4. Separate portal authority/listener from proxy dispatch and add tokenized onboarding sessions.
5. Add manual proxy and Apple profile as artifact providers; ADB as a managed mechanism; then pairing with client identity/auth.
6. Make UI render descriptors/actions/capabilities rather than switch on provider type.
7. Design remote relay/VPN as distinct authenticated long-lived adapters, not forced PAC-shaped providers.

**Affected modules:** `:core:domain` pure setup values, application layer, `:engine:portal`, a desktop network/security adapter, certificate UI/settings/app shell.

**Dependencies:** Phases 0–1 access/lifecycle boundaries; network snapshots may begin earlier, public rollout waits for secure pairing.

**Risk:** medium-high due to platform/network/security behavior.

**Expected benefit/gate:** PAC/manual/profile mechanisms can be added without proxy-core edits; network changes version artifacts and surface lifecycle/health accurately; LAN onboarding is authenticated.

### Phase 7 — Performance / stress testing

**Exact changes**

1. Establish reproducible connection/body/TLS/disk/UI workloads and hardware/JVM profiles.
2. Set budgets for p95/p99 added latency, throughput, event-loop lag, heap/direct memory, allocation rate, queue depth, disk growth, UI frame time, and shutdown duration.
3. Run 10/100/1,000 connection tests, connection churn, 10k/100k-row UI tests, 500 MiB pass-through, 100 × 10 MiB concurrency, and multi-hour soak.
4. Add slowloris, slow client/upstream, malformed/fuzzed HTTP, disk-full, TLS/DNS failure, network transition, and queue-saturation tests.
5. Add CI correctness subsets and scheduled dedicated-host stress/soak jobs with regression thresholds.
6. Profile before tuning; then adjust event-loop count, allocator/watermarks, batch size, cache weights, indexes, and body policies from evidence.

**Affected modules:** all runtime modules plus `:testingServer` and CI/benchmark projects.

**Dependencies:** representative Phase 3–6 capabilities.

**Risk:** low code risk, high infrastructure effort; test instability must be controlled through dedicated hosts and statistical thresholds.

**Expected benefit/gate:** documented, repeatable capacity envelope and long-run stability evidence replace architectural guesses and misleading “stress” test names.

## Architecture scorecard

| Area | Score | Explanation |
|---|---:|---|
| Module boundaries | 4/10 | Feature packaging is clear, but domain/UI/engine/infrastructure responsibilities cross repeatedly and several modules are dormant/duplicated. |
| Dependency direction | 4/10 | No Gradle cycles, yet domain depends on logging/platform APIs, UI depends on concrete engines, and session depends on Room. |
| Proxy engine isolation | 3/10 | Netty lives in a distinct module, but static pipeline composition, portal injection, domain models, listener persistence, and interceptor coupling pierce it. |
| Netty architecture | 3/10 | Appropriate library and basic groups/pipelines; full aggregation, one-shot upstreams, weak state/correlation, missing timeouts, and ref leaks dominate. |
| Concurrency | 3/10 | Some concurrent collections and event-loop rescheduling exist, but state transitions, DB ordering, cache generation, scopes, and pipelining are unsafe. |
| Backpressure | 2/10 | Netty socket mechanics exist, but capture/storage/UI work has no bounded ingress or overload policy; breakpoints pause whole channels with leaked buffers. |
| Memory management | 3/10 | Body files and lazy detail loading are good concepts; aggregate buffers, heap copies, pending maps, UI cache, unbounded rows/files, and ByteBuf leak are severe. |
| Traffic model | 4/10 | Basic HTTP metadata/timings/semantic metadata exist, but one cross-layer model cannot represent connection/stream/error/TLS/truncation/duplex lifecycles. |
| Body handling | 2/10 | File-backed bodies are positive, but transport is fully buffered and capped, reads/previews are unbounded, capture outcomes and streaming semantics are absent. |
| TLS architecture | 4/10 | CA/leaf/SAN/mTLS primitives exist; event-loop generation, race/eviction, trust-all default, key storage, and missing TLS observations are serious. |
| Connectivity architecture | 1/10 | The planned provider/state/capability system is absent; current behavior is a portal plus IPv4 polling. |
| PAC architecture | 1/10 | PAC domain/generation/repository/routes do not exist. |
| Portal architecture | 3/10 | Certificate/setup delivery works, but it shares the proxy pipeline, overmatches paths, discovers interfaces, renders templates, and lacks access control. |
| UI architecture | 5/10 | Feature modules, Compose, keyed LazyColumn, and ViewModels are sound foundations; direct engine dependencies and oversized cross-feature coordination remain. |
| State management | 4/10 | StateFlow gives observable unidirectional state, but full-list snapshots, global interception state, derived O(n) scans, and oversized ViewModels limit it. |
| Lifecycle management | 2/10 | A lifecycle utility and stop methods exist, but production resources are unregistered, starts are non-atomic, scopes leak, and shutdown is not awaited. |
| Error handling | 4/10 | Some 502/error states/logging exist; errors are often generic, partial state is not reconciled, and disk/overload/stream failures are not modeled. |
| Security | 2/10 | Some JS sandbox and certificate mechanics exist; open LAN proxy, plaintext CA/traffic, trust-all TLS, portal exposure, and scripts make defaults unsafe. |
| Testing | 5/10 | 662 passing tests provide a broad unit baseline; names overstate proxy integration/stress depth and critical E2E/concurrency/failure/soak cases are missing. |
| Extensibility | 3/10 | Feature modules and inspector registry help; transport/domain/storage assumptions make H2/H3/WebSocket/SSE/gRPC/plugins major cross-cutting changes. |
| Performance | 3/10 | Netty/LazyColumn/lazy bodies are appropriate choices, but per-message aggregation/copying/logging, no reuse, and O(n)-per-write traffic processing dominate. |
| Long-running stability | 2/10 | Unbounded files/jobs/rows, a direct-memory leak, orphan scopes/resources, and no quotas/soak evidence preclude confidence. |
| **Overall architecture** | **4/10** | A recoverable product foundation surrounds a transport/capture core that needs substantial safety and scalability correction. |

## Final verdict

```text
Architecture Status:
████░░░░░░ 4/10

Scalable:
NO

Main Architectural Risk:
The full-message, callback-driven proxy/capture model cannot represent streaming,
multiplexed, or long-lived protocols and couples transport correctness to storage work.

Main Performance Risk:
Event-loop blocking plus 10 MiB aggregation/heap copies and O(n) full-session
reprocessing per database write.

Main Security Risk:
An unauthenticated 0.0.0.0 proxy combined with plaintext CA/captured secrets and
trust-all upstream TLS.

Main Maintainability Risk:
Cross-layer models, direct UI-to-engine dependencies, global registries, duplicate
dormant subsystems, and absent application-owned lifecycle/order invariants.

Most Important Change:
Build a streaming proxy-to-bounded-session-writer seam with immutable exchange
lifecycle and BodyRef semantics, while immediately closing the P0 security,
buffer-ownership, persistence-ordering, and HTTP-ordering defects.

What Should NOT Be Changed:
Do not replace Netty, Compose, Koin, Room, the feature UI split, or disk-backed lazy
bodies merely to make the architecture look new. Repair ownership and contracts
around those technologies.
```

If KNet grows into a serious Charles/mitmproxy-class application, the current architecture **cannot simply accumulate features**. Without the Phase 0–3 corrections it will eventually require a disruptive proxy/data rewrite, and HTTP/2 or WebSocket work will accelerate that collision. If the team performs the transport/capture refactor now, the broader application can evolve incrementally: most UI features, storage technology, certificate primitives, DI, and semantic inspectors can survive. The honest characterization is **targeted major refactoring of the core, not a whole-product rewrite—but it is mandatory, not optional**.
