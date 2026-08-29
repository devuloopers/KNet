# KNet Target Architecture and Implementation Plan

- **Status:** Approved target; mandatory foundation implemented through the standard Phase 18 gate
- **Date:** 2026-08-26
- **Basis:** `docs/deep_architecture_scalability_engineering_audit.md` and the actual 33-module repository
- **Scope:** desktop proxy, capture/storage, protocol inspection, connectivity, security, lifecycle, and future mobile companion/relay boundaries
- **Implementation tracking:** `docs/implementation_plan.md` is the live delivery board

### Implementation progress

This document remains the boundary source of truth. Optional product/transports are not treated as implemented merely because their foundations exist.

As of 2026-08-26:

- the four original foundation modules, the evidence-driven `:core:scripting` contract module, and
  module responsibility contracts are present;
- the canonical HTTP exchange model and bounded body-access boundary are present;
- the Traffic list and detail UI consume canonical keyset pages/body ranges with bounded retained state;
- canonical schema v13, the atomic body store, one ordered writer, explicit gaps, retention, recovery, integrity verification, and direct-recording session coordination are production paths;
- development-era traffic schemas, certificate encodings, duplicate HTTP transaction models, and their compatibility adapters have been removed; older local databases reset destructively;
- request bodies/authentication/results, response heads/timings, authored breakpoint rules, scripting
  values, content encoding, GraphQL payloads, inspector tabs/menus, and certificate summaries/rules
  each have one semantic owner; feature UI models remain only where they add mutable/editor/rendering state;
- HTTP/1 requests/responses stream bidirectionally with bounded capture and application-owned breakpoint pauses;
- GraphQL and SSE run asynchronously after capture and persist generic versioned annotations;
- manual/PAC/Apple/ADB provider foundations, versioned network state, a loopback setup listener, pairing,
  Room-backed companion identity, and loopback authenticated ingress are implemented outside the proxy core;
  the stock-phone Wi-Fi path now adds automatically managed exact-interface reachability, open local-client
  admission, stable setup delivery, canonical ingress attribution, and an application-boundary-only desktop
  Connect Device UI without changing the proxy; real-device gates remain tracked in
  `docs/wifi_connectivity_implementation_plan.md`;
- the standard Phase 18 architecture/test/package gate is implemented; extended-duration soak is a parameterized release operation;
- HTTP/2 is now an additive `EXPERIMENTAL` implementation with downstream H2C/TLS ALPN, upstream pooling,
  stream-scoped capture/breakpoints, API Studio, persistence, and Traffic presentation; Windows/Linux and
  Android/iOS Wi-Fi qualification still gate `SUPPORTED`. Native gRPC, HTTP/1.1 WebSocket, and modern
  `graphql-transport-ws` inspection/breakpoints/API Studio are also additive `EXPERIMENTAL` increments with local
  JVM evidence. The Android-first companion foundation now has portable models, use cases/contracts, versioned
  persistence/control protocol, shared presentation state/ViewModel, Android Keystore/network/VPN-consent
  adapters, and Android/iOS compile gates; product UI, a real VPN/TUN backend, direct tunnel/control server, and
  relay remain explicitly `UNAVAILABLE` until their own gates pass. HTTP/3, WebSocket over HTTP/2, and legacy
  `graphql-ws` also remain unavailable.

The accepted boundary decisions are recorded in `docs/adr/`. Reproducible correctness commands and the distinction between current tests and pending measured capacity gates are recorded in `docs/proxy_test_strategy_and_baselines.md`.

The phase numbers in section 11 describe the architectural sequence. The repository-wide delivery phase numbers in `docs/implementation_plan.md` include earlier packaging and branding work, so the two numbering schemes are related through an explicit mapping table rather than by matching numbers.

## 1. Decision summary

KNet should evolve through a boundary-first, additive migration. Netty, Compose Desktop, Koin, Room, Ktor, the existing feature screens, lazy disk-backed body storage, and the GraphQL inspector direction remain useful. The target introduces stable ownership around them rather than replacing them.

The architecture has three independently evolving planes:

```text
Data plane
  client bytes -> proxy transport -> upstream bytes
  side output -> bounded capture ingress -> session writer/body store

Control plane
  application commands -> proxy/session/connectivity lifecycle
  pairing, setup artifacts, policy, health, and security state

Observation plane
  indexed traffic queries -> UI or future authorized remote client
  metrics/diagnostics -> UI and test harnesses
```

The most important invariant is:

> A client-reachability mechanism may deliver bytes to KNet's authenticated proxy ingress, but it cannot own, call into, or change proxy parsing, traffic storage, body handling, or semantic inspection.

This makes PAC, manual configuration, Apple profiles, ADB reverse, a mobile companion tunnel, VPN capture, and a remote relay alternate ways to reach the same proxy endpoint. It also keeps future remote UI/control APIs above the application query/command layer rather than exposing Netty or Room.

### Near-term module decision

Do not rename or split the entire repository. The foundation migration adds focused architectural
modules only when concrete ownership requires them:

1. `:core:traffic` — stable, portable traffic values and capture contracts.
2. `:core:connectivity` — portable setup/lifecycle/capability values and small mechanism contracts.
3. `:application:desktop` — JVM desktop orchestration, use cases, lifecycle reducers, and technology-neutral ports.
4. `:connectivity:desktop` — current desktop PAC/manual/profile/ADB implementations, isolated by package.
5. `:core:scripting` — the small portable scripting vocabulary shared by collections, application
   ports, editors, and script engines.
6. `:core:identity` — dependency-free registered-device identity shared by connectivity and pairing
   without coupling those sibling modules.
7. `:core:companion`, `:application:companion`, `:data:companion`, `:ui:companion:presentation`, and
   `:ui:companion:sharedUi` — portable companion contracts/workflows/adapters/state/Compose UI now justified by
   Android and iOS compilation.
8. `:connectivity:companion` — KMP platform connectivity boundary; Android owns its network, certificate, and
   VPN-consent adapters in `androidMain`, iOS placeholders remain fail closed in `iosMain`, and concrete packet
   backends remain separate future implementations.
9. `:products:companion:androidApp` — installable Android product shell and composition root for capabilities that now
   have production adapters; it does not simulate unavailable transport, certificate, or VPN behavior.
10. `:ui:core` — the shared Compose Multiplatform design system and adaptive component foundation for JVM desktop,
    Android, and iOS; feature UI owns screens but not duplicate palettes or platform-neutral primitives.

Future runtime modules such as a concrete Android VPN packet backend, desktop companion tunnel/control adapters,
`:connectivity:relay`, or an HTTP/3 transport are added only when real
implementation work begins and their dependencies justify isolation. Current modules keep their names during the
behavioral migration; directory/module renames are optional cleanup after dependency rules are green.

## 2. Architectural objectives and non-goals

### Objectives

- Stream arbitrary-size traffic through the proxy while capturing within explicit memory/disk limits.
- Keep Netty event loops independent from storage, UI, semantic inspection, scripts, and OS discovery.
- Preserve per-connection and per-exchange protocol correctness under concurrency.
- Make capture overload, truncation, partial data, failure, and retention explicit and observable.
- Add semantic inspectors without changing proxy forwarding.
- Add connectivity mechanisms without changing proxy or traffic modules.
- Add a future mobile companion/direct tunnel/relay without changing existing PAC, manual proxy, proxy-engine, or traffic contracts.
- Own every thread, coroutine scope, channel, file, database, key, and cache through a deterministic lifecycle.
- Default to a safe local security posture and require explicit authenticated LAN/remote exposure.
- Keep module/API count proportional to real implementation needs.

### Non-goals

- Replacing Netty, Compose, Koin, Room, Ktor, or the current visual design.
- Making the desktop proxy Kotlin/Native or moving the proxy onto mobile.
- Building a generic event bus or plugin platform before a second real external extension needs it.
- Pretending all connectivity mechanisms share the same lifecycle.
- Supporting transparent arbitrary TCP/UDP interception inside the HTTP proxy. A future VPN/companion may translate captured flows into explicit proxy streams or add a separate transport adapter.
- Moving every file before behavior and contracts are stable.

## 3. Target architecture

### 3.1 Layer and dependency model

```text
                                :products:desktop
                           composition root and process owner
                       /          /         |          \
                      v          v          v           v
              desktop UIs   :application:desktop  runtime     adapters
                  |              |          |           |
                  |              | ports    |           |
                  +--------------+<---------+-----------+
                                 |
                    +------------+-------------+
                    |                          |
             :core:domain              :core:traffic
             :core:connectivity         :core:pairing
                       \                 /
                        :core:identity
             :core:serialization        :core:scripting
                             small pure values/policy

Runtime implementations:
  :engine:proxy, :engine:certificate, :engine:interceptor,
  :engine:protocol, :engine:formatter, :engine:script,
  :core:http (Ktor implementation during migration)

Desktop adapters:
  :storage, :data:desktop, :connectivity:desktop
```

Dependency arrows always point inward:

```text
UI -> application APIs/use cases -> core values/policy
application services -> application contracts -> core values/policy
runtime/adapters -> application contracts + core values
products:desktop -> every concrete implementation strictly for composition
```

There are no reverse arrows from core/application to Netty, Room, Ktor, Graal, Compose, filesystem, OS process, or platform-network implementations.

### 3.2 Module responsibilities

| Module/group | Target responsibility | Must not contain/depend on |
|---|---|---|
| `:products:desktop` | Koin bindings, process start/close, configuration loading, top-level window | business policy, Netty handlers, Room queries, feature coordination |
| `:application:desktop` | JVM desktop session/proxy/connectivity orchestration, command/query use cases, lifecycle state machines, application contracts, typed failures | Netty, Room, files, Compose, Ktor, Graal, OS commands, mobile-companion workflows |
| `:application:companion` | portable companion pairing, registration, connection, certificate, inspection, recovery, and forget workflows plus platform contracts | platform APIs, sockets, persistence implementations, UI state, desktop proxy internals |
| `:core:companion` | validated companion invitation/registration/endpoints and connection/certificate/inspection/network/failure state | credentials, private keys, persistence, sockets, VPN handles, UI |
| `:core:traffic` | connection/exchange/message IDs, header/head/timing/TLS/body-reference models, capture events/admission contract | UI state, Room entities, filesystem paths, Netty buffers |
| `:core:connectivity` | endpoint/setup descriptors, capabilities, availability/lifecycle/health, setup and managed-mechanism contracts | proxy handlers, UI, network-interface discovery, OS commands |
| `:core:identity` | stable registered-device ID, display identity, enrollment kind, last-seen and revocation state | pairing credentials, network addresses, persistence, UI |
| `:core:pairing` | pairing invitation/challenge/session values, trusted-device projection, and cryptographic protocol rules | portal routes, keychain implementation, tunnel implementation, UI |
| `:core:scripting` | script language/phase, reusable snippets, and immutable assertion results | runtime engines, mutable host objects, persistence, UI state |
| `:core:domain` | remaining product policies and stable repository/use-case values for collections, rules, settings, exports | UI models/colors, Java URI, logging side effects, engine types |
| `:engine:proxy` | authenticated listeners, HTTP transport negotiation, MITM, downstream/upstream state, streaming forwarding, timeouts/watermarks, capture/breakpoint ports | persistence, portal, connectivity, semantic parsers, UI, scripts, Room/files |
| `:engine:certificate` | CA/leaf certificate operations, TLS context creation, verification results, cache policy | plaintext key persistence, UI, portal, Room |
| `:engine:interceptor` | compiled rule evaluation, bounded pause/resume/modify/drop mechanics, framing-safe mutation | global state, UI sessions, database writes, semantic inspection |
| `:engine:protocol` | semantic inspector API/host and currently wired built-in inspectors | proxy forwarding, Netty listener ownership, UI state, Room entities |
| `:engine:formatter` | bounded body preview detection/formatting behind an application contract | traffic transport or persistence ownership |
| `:engine:script` | isolated/bounded script execution implementation | Netty event loops, direct traffic persistence, unrestricted untrusted Kotlin execution |
| `:core:http` | transition location for API Studio contracts/Ktor client | proxy traffic models; eventually implementation moves behind application contract |
| `:storage` | Room schema/DAOs/migrations, file body-store implementation, retention/reconciliation primitives | domain use cases, UI models, proxy handlers |
| `:data:desktop` | desktop repository adapters/mappers, secure/network adapters until extracted | cross-engine orchestration, process-lifetime unmanaged scopes, capture hot-path callbacks |
| `:connectivity:desktop` | PAC/manual/Apple/ADB implementations, platform network snapshot, strict setup listener, pairing security, authenticated ingress gateway | proxy parsing, traffic storage, feature UI, protocol inspection |
| `:data:companion` | versioned companion persistence/control protocol and platform secure-storage/key adapters | workflow policy, UI, VPN lifecycle, proxy/traffic ownership |
| `:connectivity:companion` | KMP platform connectivity adapters; Android implementation and fail-closed iOS placeholders | packet-engine implementation, shared policy, UI, desktop proxy/capture |
| `:ui:core` | Compose Multiplatform theme, semantic tokens, resources, and platform-adaptive reusable components | feature state/screens, application policy, runtime adapters, platform APIs in common code |
| `:ui:companion:presentation` | portable companion state/actions/effects and lifecycle-owned ViewModel | Compose/SwiftUI widgets, platform intents, repositories, sockets, VPN handles |
| `:ui:companion:sharedUi` | Compose Multiplatform screens and shared resources using `:ui:core` | duplicate theme palettes, platform lifecycle/effects, repositories, transports, credentials, VPN handles |
| `:ui:*` | presentation state, Compose rendering, typed user actions/navigation | concrete engine/runtime classes, Room, files, OS/process APIs |
| `:testingServer` | deterministic loopback origins and failure fixtures | production behavior |

### 3.3 Enforced dependency rules

1. Only `:products:desktop` may import concrete classes from both UI and runtime/adapter groups.
2. UI modules depend on `:application:desktop`, pure core/UI modules, and the existing pure `:engine:formatter` presentation helper only; never concrete runtimes, `:storage`, or `:data:desktop` classes. The exception is executable and must not expand.
3. The JVM-only `:application:desktop` module depends only on JVM variants of pure core modules and coroutine primitives.
4. Runtime/adapters implement ports declared by `:application:desktop` or `:core:*`.
5. `:engine:proxy` has no project dependency on portal, protocol inspectors, storage, data, connectivity, or UI.
6. `:engine:protocol` consumes captured views and produces annotations; it never receives a Netty `Channel`, `ByteBuf`, or write callback.
7. `:core:*` common source sets contain no JVM-only API unless isolated in `jvmMain` behind a port.
8. Gradle dependencies use `implementation` by default. `api` is limited to deliberately versioned contracts.
9. Public declarations are opt-in and API-reported; implementation packages are `internal` where module boundaries permit.
10. Architecture tests fail CI for forbidden imports and project dependency edges.
11. Koin binding declarations live only in `:products:desktop`, grouped by feature; reusable modules expose constructors/contracts and may use product-provided instances but never define product assembly.

The repository rule that ViewModels receive use cases rather than repositories remains in force. Pure business use cases stay in `:core:domain`; cross-engine/session orchestration use cases live in `:application:desktop`. Both are injected through Koin, and neither permits a ViewModel to inject a repository or concrete runtime directly.

### 3.4 Target runtime flow

```text
Client
  -> authenticated ProxyBinding
  -> Netty connection context
  -> protocol negotiation (HTTP/1 now; H2/H3 adapters later)
  -> immutable ExchangeContext
  -> streaming request forwarder
       +-> CaptureIngress admission and bounded owned chunks
       +-> BreakpointPort only when a compiled rule requires a pause
  -> UpstreamConnectionManager
  -> streaming response forwarder
       +-> CaptureIngress admission and bounded owned chunks
  -> client

CaptureIngress
  -> bounded metadata/body queues
  -> one ordered SessionWriter per capture session
       +-> Room metadata transaction
       +-> BodyStore temporary stream -> atomic BodyRef finalize
       +-> InspectorScheduler after bounded capture availability
  -> indexed/paged TrafficQuery
  -> TrafficViewModel loaded pages + one bounded detail state
  -> Compose LazyColumn
```

Forwarding continues when body capture is truncated or unavailable. A policy may intentionally pause forwarding for a breakpoint, but that pause is separately bounded by time, bytes, and connection count.

## 4. Proposed Gradle modules and package structure

### 4.1 Implemented `settings.gradle.kts` additions

These evidence-backed modules are now included:

```kotlin
include(":application:desktop")
include(":application:companion")
include(":core:traffic")
include(":core:connectivity")
include(":core:identity")
include(":connectivity:desktop")
include(":core:scripting")
```

The application layer is now an explicit namespace with a JVM desktop module and a portable companion module.
This structural split does not change package names or runtime behavior.

Future additions are conditional:

```text
:testing:benchmarks              add with the load harness
:connectivity:companion          add when desktop companion pairing/tunnel begins
:connectivity:relay              add when a real relay transport begins
:products:companion:androidApp   current installable Android product shell/composition
:products:companion:iosApp       current installable iOS product shell/composition
:engine:proxy-http3              add only if QUIC dependencies justify isolation
:inspection:<name>               add only when an inspector needs independent dependencies/release
```

HTTP/2, WebSocket, GraphQL, gRPC, and SSE should begin as isolated packages/providers inside the existing proxy/protocol modules. Extracting a module is justified when there is a second implementation, a heavy optional dependency, a separate platform target, or an independently testable/releasable boundary—not simply because a feature has a name.

### 4.2 Proposed repository tree

```text
KNet/
├── products/
│   └── desktop/
│       └── src/jvmMain/.../products/desktop/
│           ├── bootstrap/
│           ├── composition/
│           └── lifecycle/
│
├── application/
│   ├── desktop/src/main/.../application/             JVM desktop workflows
│   │   ├── contract/                                 adapter-facing interfaces and values
│   │   ├── coordinator/                              stateful application orchestration
│   │   └── usecase/                                  focused commands and queries
│   └── companion/src/commonMain/.../application/     portable Android/iOS workflows
│       ├── contract/                                 platform/data interfaces and values
│       └── usecase/                                  portable companion workflows
│
├── core/
│   ├── traffic/                                      ADD
│   │   └── src/commonMain/.../traffic/
│   │       ├── id/
│   │       ├── model/
│   │       │   ├── connection/
│   │       │   ├── http/
│   │       │   ├── body/
│   │       │   ├── tls/
│   │       │   └── duplex/
│   │       ├── event/
│   │       ├── capture/
│   │       └── policy/
│   ├── connectivity/                                 ADD
│   │   └── src/commonMain/.../connectivity/
│   │       ├── model/
│   │       ├── setup/
│   │       ├── lifecycle/
│   │       └── capability/
│   ├── pairing/                                      KEEP, MODIFY
│   │   └── src/commonMain/.../pairing/
│   │       ├── model/
│   │       ├── protocol/
│   │       └── crypto/
│   ├── scripting/                                    ADD
│   │   └── src/commonMain/.../scripting/model/       shared language/phase/snippet/assertion values
│   ├── domain/                                       KEEP, NARROW
│   ├── http/                                         KEEP DURING MIGRATION
│   ├── logger/                                       KEEP
│   └── serialization/                                KEEP
│
├── connectivity/
│   └── desktop/                                      ADD
│       └── src/jvmMain/.../connectivity/desktop/
│           ├── network/
│           ├── manual/
│           ├── pac/
│           ├── apple/
│           ├── adb/
│           └── registration/
│
├── engine/
│   ├── proxy/                                        KEEP, REWORK INTERNALLY
│   │   └── src/main/.../engine/proxy/
│   │       ├── api/
│   │       ├── runtime/
│   │       ├── connection/
│   │       ├── transport/http1/
│   │       ├── transport/http2/                     IMPLEMENTED INTERNALLY
│   │       ├── duplex/                               IMPLEMENTED PROTOCOL-NEUTRAL UPGRADE RELAY
│   │       ├── upstream/
│   │       ├── tls/
│   │       ├── access/
│   │       └── metrics/
│   ├── certificate/                                  KEEP, MODIFY
│   ├── interceptor/                                  KEEP, CONSOLIDATE
│   ├── portal/                                       KEEP, ISOLATE
│   ├── protocol/                                     KEEP, REFOCUS
│   │   └── src/main/.../engine/protocol/
│   │       ├── api/
│   │       ├── registry/
│   │       ├── graphql/
│   │       ├── grpc/                                 MOVED TO :engine:grpc
│   │       ├── sse/                                  IMPLEMENTED
│   │       └── websocket/                            MOVED TO :engine:websocket
│   ├── grpc/                                         ADDED, EXPERIMENTAL
│   ├── graphqlWebSocket/                             ADDED, EXPERIMENTAL SEMANTIC LAYER
│   ├── websocket/                                    ADDED, EXPERIMENTAL
│   ├── formatter/                                    KEEP
│   ├── script/                                       KEEP, HARDEN
│   ├── session/                                      DEPRECATE/MOVE CONTENT
│   ├── traffic/                                      MERGE/REMOVE
│   └── simulator/                                    MOVE TO TESTING OR REMOVE
│
├── storage/                                          KEEP, EXPAND OWNERSHIP
│   └── src/jvmMain/.../storage/
│       ├── database/
│       ├── traffic/
│       │   ├── entity/
│       │   ├── dao/
│       │   ├── query/
│       │   └── migration/
│       ├── body/
│       │   ├── file/
│       │   ├── retention/
│       │   └── reconciliation/
│       └── pairing/
│
├── data/
│   └── desktop/                                      KEEP, REDUCE
│       └── src/jvmMain/.../data/desktop/
│           ├── repository/
│           ├── mapper/
│           ├── security/
│           └── preferences/
│
├── ui/
│   ├── core/                                         KEEP
│   └── desktop/
│       ├── app/                                      KEEP, SHELL ONLY
│       ├── workspace/                                MERGE WITH SHELL IF USEFUL
│       ├── traffic/                                  KEEP, PAGE
│       ├── apistudio/                                KEEP
│       ├── breakpointManager/                        KEEP
│       ├── certificate/                              KEEP
│       ├── settings/                                 KEEP
│       ├── scripting/                                KEEP
│       ├── codeEditor/                               KEEP
│       └── httpPanel/                                KEEP
│
└── testing/
    ├── server/                                       CURRENT testingServer
    ├── support/                                      ADD WHEN SHARED FIXTURES EXIST
    └── benchmarks/                                   ADD WITH PHASE 0 HARNESS
```

### 4.3 Current-module action map

| Current module | Action | Target change | Audit problem solved |
|---|---|---|---|
| `:products:desktop` | **KEEP, MODIFY** | one visible composition root; normal close invokes ordered application shutdown | unregistered resources and non-deterministic close (F-10) |
| `:core:domain` | **MODIFY, MOVE** | keep product policy; move UI state to UI and traffic values to `:core:traffic`; remove logger/Java URI | cross-layer/domain/platform leakage (F-20, F-28) |
| `:core:scripting` | **ADD** | own the proven cross-feature scripting vocabulary without runtime/UI dependencies | duplicated language, phase, snippet, and assertion values (F-20, F-30) |
| `:core:http` | **MODIFY, LATER MOVE** | expose API Studio through application contract; keep Ktor implementation during migration, then optionally rename | duplicate HTTP models and internal header/capture coupling (F-15, F-30) |
| `:core:pairing` | **KEEP, MODIFY** | make it the portable pairing protocol/value module; no runtime singleton | disconnected pairing and future companion boundary |
| `:core:logger` | **KEEP, MODIFY** | actual configured asynchronous diagnostic adapter; no domain dependency | nominal/hot-path logging (F-24) |
| `:core:serialization` | **KEEP** | retain portable serialization only | preserves a useful stable component |
| `:data:desktop` | **MODIFY, MOVE** | remove proxy callback orchestration and unmanaged scopes; keep desktop repository adapters | unbounded/racy capture and God integration module (F-05, F-08, F-10) |
| `:storage` | **MODIFY** | own schema, indexed queries, body files, retention, reconciliation, and migrations | destructive upgrades, unbounded sessions/files, lossy/full queries (F-07, F-13, F-18, F-21) |
| `:engine:proxy` | **KEEP, MODIFY** | streaming forwarding, per-connection/exchange state, capture/access ports, strict lifecycle; remove full aggregation/static registry | large-body failure, event-loop work, pipelining, backpressure (F-02, F-03, F-06, F-08) |
| `:engine:certificate` | **KEEP, MODIFY, MOVE** | single-flight weighted cache and strict TLS; move key persistence to secure adapter | CA/key exposure, cache race, trust-all default (F-07, F-11, F-17) |
| `:engine:interceptor` | **KEEP, MODIFY** | correct buffer ownership; session-scoped compiled rules; typed phase state; safe framing | direct-memory leak, lost response, globals, invalid framing (F-04, F-09, F-22, F-25) |
| `:engine:traffic` | **REMOVE/MERGE** | merge the one canonical rewrite-rule model/runtime into interceptor/application policy | duplicate dormant traffic path (F-26, F-30) |
| `:engine:portal` | **REMOVE** | replaced by the strict loopback setup listener in `:connectivity:desktop`; no proxy handler remains | proxy route collision, Host injection, missing isolation (F-12) |
| `:engine:protocol` | **KEEP, MODIFY** | asynchronous semantic inspector host; remove dormant transport ownership | protocol claims and closed/coupled metadata (F-20, F-26) |
| `:engine:formatter` | **KEEP, MODIFY** | bounded preview formatter behind application contract | unbounded body read/cache and UI-to-engine edge (F-19) |
| `:engine:script` | **KEEP, MODIFY** | process/context isolation, bound values, hard limits; never proxy-event-loop execution | ineffective timeout/JVM access/source injection (F-14) |
| `:engine:session` | **MOVE, REMOVE LEGACY PATH** | move body store/mappers/retention to storage/application; retire unused `SessionManager` after canonical writer lands | parallel/dormant session architectures (F-05, F-26) |
| `:engine:simulator` | **MOVE/REMOVE** | test support unless a product simulator is approved | premature/dormant module (F-26, F-30) |
| desktop UI modules | **KEEP, MODIFY** | depend on application use cases; page metadata and bound detail bodies; shell stops constructing cross-feature engines | direct engine edges and O(n)/retained state (F-18, F-19, F-20) |
| `:testingServer` | **KEEP, EXPAND** | deterministic real upstream/TLS/slow/failure fixture | shallow proxy integration/stress tests (F-27) |
| `:application:desktop` | **ADD** | lifecycle/order/policy owner between UI and implementations | missing application layer and scattered orchestration |
| `:core:traffic` | **ADD** | stable protocol-neutral capture values/contracts | one mutable cross-layer request/response model (F-20, F-21) |
| `:core:connectivity` | **ADD** | stable setup/lifecycle/capability contracts | absent connectivity/PAC architecture (F-16) |
| `:connectivity:desktop` | **ADD** | independent desktop mechanism implementations | portal/proxy/connectivity coupling and network-state gaps (F-12, F-16, F-23) |

### 4.4 Optional end-state renames

Renaming `engine` to `runtime`, `data` to `adapters`, or `ui:desktop:*` to `features:*` may make the tree read more cleanly, but it does not create architectural value by itself. Defer such renames until imports and dependency rules already express the target. A rename must not be a prerequisite for any correctness, security, streaming, companion, or connectivity feature.

## 5. Traffic architecture

### 5.1 Separate transport events, stored records, and presentation models

The current `HttpRequest`/`HttpResponse`/`HttpTransaction` objects must stop carrying full `ByteArray` bodies across Netty, domain, persistence, interception, export, and UI. Replace them incrementally with four model families:

```text
Transport context (private to :engine:proxy)
  Netty Channel/ByteBuf, codec state, pending writes, flow control

Capture contracts (:core:traffic)
  immutable small events and explicitly owned body chunks

Stored/query records (:core:traffic values; :storage entities)
  connection/exchange/message metadata plus BodyRef

Presentation models (each :ui:desktop feature)
  formatted row/detail state, never transport/persistence models
```

### 5.2 Canonical traffic values

The following is contract-level pseudocode, not implementation code:

```kotlin
@JvmInline value class CaptureSessionId(val value: String)
@JvmInline value class ConnectionId(val value: String)
@JvmInline value class ExchangeId(val value: String)
@JvmInline value class StreamId(val value: Long)
@JvmInline value class BodyId(val value: String)

data class HeaderField(
    val name: HeaderName,
    val value: String,
)

data class RequestHead(
    val method: HttpMethod,
    val target: RequestTarget,
    val protocol: ApplicationProtocol,
    val headers: List<HeaderField>,
)

data class ResponseHead(
    val protocol: ApplicationProtocol,
    val status: HttpStatus,
    val headers: List<HeaderField>,
)

data class BodyRef(
    val id: BodyId,
    val observedBytes: Long,
    val storedBytes: Long,
    val digest: BodyDigest?,
    val contentEncoding: ContentEncoding?,
    val outcome: BodyCaptureOutcome,
)

sealed interface BodyCaptureOutcome {
    data object Complete : BodyCaptureOutcome
    data class Truncated(val limitBytes: Long) : BodyCaptureOutcome
    data class Skipped(val reason: BodySkipReason) : BodyCaptureOutcome
    data class Failed(val reason: BodyFailure) : BodyCaptureOutcome
}
```

Known closed values use enums or sealed types, with a typed `Custom(value)` variant where protocols allow extension. Raw magic strings do not cross module boundaries.

`HeaderField` remains an ordered list throughout mapping, persistence, export, and UI. It is never converted to a single-value map except in a local helper whose duplicate policy is explicit. Cookies remain losslessly represented as repeatable headers; parsed cookie/query views are derived presentation/inspection values rather than canonical replacements.

### 5.2.1 Shared feature-facing HTTP models

KNet retains canonical, immutable HTTP request and response models in `:core:traffic`. They are the shared semantic contract used across API Studio, Traffic, Breakpoints, replay/export, collections, and protocol inspectors:

```kotlin
data class HttpRequestSnapshot(
    val head: RequestHead,
    val body: MessageBodyRef,
)

data class HttpResponseSnapshot(
    val head: ResponseHead,
    val body: MessageBodyRef,
)

data class HttpExchangeSnapshot(
    val id: ExchangeId,
    val connectionId: ConnectionId?,
    val streamId: StreamId?,
    val request: HttpRequestSnapshot,
    val response: HttpResponseSnapshot?,
    val state: ExchangeState,
    val timings: ExchangeTimings,
)
```

`MessageBodyRef` describes `Empty`, `Available(BodyRef)`, or `Unavailable(BodyCaptureOutcome)`; it does not embed an arbitrary `ByteArray`, Netty buffer, or filesystem path. Body content is obtained through bounded `BodyAccess` use cases.

Feature use is consistent:

| Consumer | Uses the common model | Feature-specific state layered around it |
|---|---|---|
| API Studio | common methods, targets, headers, protocol, executed request/response snapshots | mutable editor tabs, validation, auth editor, and a bounded `RequestBodySource` while authoring |
| Traffic UI | `HttpExchangeSnapshot`, request/response snapshots, and body references | row formatting, selection, pagination, and bounded previews |
| Breakpoints | immutable request/response snapshots | `HttpRequestPatch`/`HttpResponsePatch` describing validated modifications rather than mutating the snapshot |
| Collections/replay | common request values and a bounded executable body source | collection identity, variables, environments, and saved-draft metadata |
| Inspectors | common snapshots plus bounded `BodyAccess` | inspector-specific annotations only |
| Export | common snapshots and streamed body access | HAR/cURL/export formatting |

API Studio and Breakpoints still need editable data, but editability is expressed as a feature draft or typed patch over the common model. KNet does not create separate competing definitions of HTTP method, URL/target, headers, status, protocol, or response semantics for every feature.

This preserves the current strength of a shared request/response vocabulary while eliminating the audit problem: the same body-carrying mutable object no longer serves simultaneously as a Netty message, persistence entity, domain record, editor draft, and Compose state.

### 5.2.2 Canonical semantic ownership after migration

Single source of truth does not mean passing a persistence record or immutable captured snapshot into
every text field. It means each semantic value has one owner, while drafts, database entities, engine
host objects, and rendering state exist only when they add behavior required by their layer.

| Semantic contract | Single owner | Direct consumers | Deliberately separate boundary types |
|---|---|---|---|
| Captured HTTP request/response/exchange | `:core:traffic` snapshots and heads | Traffic, API Studio recording/replay, breakpoints, storage, export, inspectors | Room entities, Netty messages, mutable editor drafts |
| HTTP method/status/protocol/headers/timing/content encoding | `:core:traffic` | all HTTP features and adapters | final Ktor/Netty/Room conversions only |
| Outbound authored body/authentication/execution result | `:core:domain` | API Studio use cases and `:core:http` | API Studio editor widgets; Ktor request builders |
| Authored breakpoint rule and phase | `:core:domain` | application coordinator, repository, interceptor, Traffic, Breakpoint Manager | Room entity and mutable edit form only |
| Script language/phase/snippet/assertion | `:core:scripting` | collections, application contracts, editors, engine | mutable sandbox host request/response objects only |
| Structured GraphQL payload | `:core:domain` | HTTP panel and API Studio | UI wrapper adds only active sub-tab |
| Certificate summaries/format/mTLS rule | `:application:desktop` certificate port | certificate UI and desktop adapter | JCA/engine certificate material and persisted representation |
| Shared inspector tabs and menu items | reusable owning UI module | Traffic/API Studio/code editor/app shell | feature-only tabs that have genuinely different behavior |

`ResponseInspectorState`, traffic row/detail state, breakpoint edit state, and request editor drafts are
not alternate HTTP domain models: they add loading, selection, formatting, validation, assertions, or
mutable authoring behavior. They must compose or derive from the canonical values and must not be
accepted by storage, proxy, connectivity, or engine APIs.

### 5.3 Connection, exchange, and duplex records

```text
ConnectionRecord
  id, sessionId, ingress identity/type, downstream endpoints,
  upstream endpoint(s), transport protocol, opened/closed timestamps,
  TLS observations, byte totals, terminal error

ExchangeRecord
  id, connectionId, streamId?, sequence,
  RequestHead, ResponseHead?, request/response BodyRef?,
  state, timings, breakpoint outcome, capture source/outcome,
  semantic annotation summaries, terminal error

DuplexMessageRecord
  id, connectionId, parent exchangeId, streamId?, sequence,
  direction, message kind/opcode, timestamp, BodyRef?, terminal flags
```

This model supports:

- ordered HTTP/1 exchanges on one connection;
- concurrent HTTP/2/HTTP/3 streams using `StreamId`;
- WebSocket upgrade followed by long-lived duplex messages;
- SSE events associated with a streaming response;
- gRPC messages/trailers associated with an H2 stream;
- typed partial/failure/truncation states;
- optional client identity/source for LAN, companion, or relay ingress.

It does not force storage or UI to understand Netty frames. Protocol adapters map wire activity into these stable concepts.

### 5.4 Monotonic exchange lifecycle

An exchange may move only forward:

```text
Admitted
  -> RequestHeaders
  -> RequestStreaming / RequestComplete
  -> WaitingForResponse
  -> ResponseHeaders
  -> ResponseStreaming / ResponseComplete
  -> Completed

Any non-terminal state
  -> Failed | Dropped | Cancelled
```

Body state evolves independently from `NotRequested` to `Capturing` to one terminal `Complete/Truncated/Skipped/Failed` outcome. A late request event cannot replace a completed response. Every event contains a per-exchange sequence/version, and storage updates use conditional monotonic transitions rather than row replacement.

This directly eliminates the two unsequenced `REPLACE` writers and fake `GET http://unknown` recovery in `ProxyEngineRepositoryImpl`.

### 5.5 Capture event flow

```kotlin
sealed interface CaptureEvent {
    val sessionId: CaptureSessionId
    val connectionId: ConnectionId
    val sequence: Long

    data class ConnectionOpened(...)
    data class TlsObserved(...)
    data class ExchangeStarted(...)
    data class RequestHeadObserved(...)
    data class RequestBodyFinished(...)
    data class ResponseHeadObserved(...)
    data class ResponseBodyFinished(...)
    data class ExchangeFinished(...)
    data class ExchangeFailed(...)
    data class DuplexMessageObserved(...)
    data class ConnectionClosed(...)
    data class CaptureGap(...)
}
```

Events contain metadata and body references/status, never a complete arbitrary-size body. They are immutable after publication.

There is no process-wide event bus. `CaptureIngress` is a session-owned, single-purpose boundary with one canonical persistence consumer. Optional metrics and semantic inspection receive their own bounded derived inputs after admission; they do not compete as arbitrary subscribers to Netty.

### 5.6 Body chunk ownership

No Netty `ByteBuf` crosses the proxy boundary.

```text
Netty event loop owns inbound ByteBuf
  -> forward/retain according to Netty pipeline rules
  -> ask CaptureIngress to reserve N capture bytes
       denied: do not copy; record truncation once
       granted: copy only reserved bytes into an owned chunk lease
  -> publish lease; proxy must not access it again
  -> capture worker writes chunk
  -> worker releases byte-budget reservation/chunk
```

The capture API reserves capacity before allocation/copy:

```kotlin
interface CaptureIngress {
    fun admitExchange(head: RequestHead, context: IngressContext): CapturePlan
    fun tryPublish(event: CaptureEvent): PublishResult
    fun tryReserveBody(
        exchangeId: ExchangeId,
        direction: Direction,
        requestedBytes: Int,
    ): BodyChunkReservation?
}

interface BodyChunkReservation {
    val writableBytes: ByteArray
    fun publish(sequence: Long, endOfBody: Boolean)
    fun cancel()
}
```

The initial implementation may allocate a right-sized `ByteArray`; pooling is optional and benchmark-driven. The important properties are pre-reservation, one owner, no Netty reference, explicit publication/cancellation, and byte-budget release in `finally`.

### 5.7 Backpressure and overload policy

Use separate bounded budgets for metadata and bodies:

```text
CaptureLimits
  metadataEventsInFlight
  bodyBytesInFlight
  perBodyStoredBytes
  pausedBreakpointBytes
  pausedBreakpointConnections
  sessionBodyBytes
  sessionTransactions
  sessionAge
```

Policy order under pressure:

1. Preserve forwarding.
2. Preserve terminal metadata for already admitted exchanges using reserved metadata capacity.
3. Stop copying additional body chunks and mark the body `Truncated(CaptureQueueLimit)`.
4. Admit new exchanges as metadata-only if metadata capacity remains.
5. If metadata admission also saturates, enter a visible `CaptureDegraded` state, count a compact `CaptureGap`, and stop admitting new captures until recovery. Do not silently allocate or block a Netty event loop.
6. A user-selected “pause proxy on capture failure” mode, if ever added, is explicit and off by default.

Queue capacity must be measured both in event count and bytes. An “unlimited” option is not supported. Defaults are finalized through the benchmark phase rather than guessed in architecture code.

Slow consumers behave independently:

| Slow/failing component | Result |
|---|---|
| body disk | body truncates/fails; metadata and forwarding continue |
| Room writer | bounded queue rises; bodies truncate first; capture health degrades visibly |
| semantic inspector | annotation becomes incomplete/failed; capture and forwarding continue |
| formatter | selected preview remains loading/error; no proxy effect |
| UI collector | loaded page becomes stale until refreshed; no capture effect |
| remote observer | its bounded stream drops/coalesces updates; no proxy/capture effect |

### 5.8 Canonical session writer

One `SessionWriter` actor/worker owns capture persistence for a session:

```text
CaptureIngress queues
  -> SessionWriter
       -> validate per-connection/per-exchange sequence
       -> apply monotonic in-memory reducer
       -> batch metadata updates in a Room transaction
       -> stream body chunks to temporary BodyStore files
       -> atomically finalize BodyRef
       -> publish compact query invalidation/metrics
       -> schedule semantic inspection when required inputs exist
```

It replaces the `ProxyEngineRepositoryImpl` pending map and parallel `engine:session` writer concepts. A bounded map may temporarily track active exchange reducers, but it is limited by active connection/exchange quotas, evicted on disconnect/timeout, and contains metadata/body writer handles rather than whole bodies.

### 5.9 Persistence schema and query model

Recommended practical tables:

```text
capture_session
connection
exchange
body_object
duplex_message
inspection_annotation
capture_gap
deletion_outbox
trusted_device                         pairing storage, not traffic ownership
```

Request/response headers are stored in a versioned ordered binary/text encoding on the exchange row unless measured search requirements justify normalization. Indexed columns hold the fields actually queried: session/time/stable ID, host, method, status, protocol, connection, stream, capture state, and sizes.

Required indexes begin with:

```text
(session_id, started_at DESC, exchange_id DESC)
(session_id, host, started_at DESC)
(session_id, method, started_at DESC)
(session_id, status_code, started_at DESC)
(session_id, protocol, started_at DESC)
(connection_id, sequence)
```

Add indexes only after checking real query plans. URL/header full-text search can use an FTS table later; body full-text indexing is opt-in because it multiplies sensitive storage and CPU cost.

Traffic queries use keyset/cursor paging, direct ID lookup, and database-side filters. A Room invalidation emits a generation/change signal, not `SELECT *` plus a complete domain/UI list.

### 5.10 Body storage

`BodyStore` is a port; the initial desktop adapter remains file-backed:

```kotlin
interface BodyStore {
    suspend fun openWrite(bodyId: BodyId, policy: BodyPolicy): BodyWriteSession
    suspend fun readRange(bodyId: BodyId, offset: Long, length: Int): BodyChunk
    suspend fun openRead(bodyId: BodyId): BodyReadStream
    suspend fun delete(bodyId: BodyId): DeleteResult
}
```

Desktop behavior:

- generate internal `BodyId` paths; never use host, URL, alias, or user input as a filename;
- write to a temporary file, calculate size/digest, then atomic move/finalize;
- use restrictive directory/file permissions and optional session encryption;
- enforce per-body, per-session, global byte, count, and age limits;
- store original content encoding and capture outcome;
- reconcile temporary/orphan/missing files at startup;
- use a deletion outbox so DB/file cleanup converges after crashes;
- clear session removes rows, bodies, active writers, UI detail/cache state, and derived annotations;
- expose range/preview reads so a 500 MiB body is never loaded by `readBytes()`.

### 5.11 Inspection and formatting flow

```text
captured head/body progress
  -> InspectorScheduler (bounded CPU queue)
  -> matching inspectors selected by declared predicates/capabilities
  -> bounded preview/range/stream access through BodyStore
  -> versioned InspectorAnnotation
  -> annotation persistence/query

selected BodyRef
  -> application LoadBodyPreview use case
  -> bounded bytes/characters and cancellation
  -> formatter worker
  -> weighted preview cache
  -> UI detail state
```

Inspection and formatting never receive transport buffers or block SessionWriter commits. An inspector declares its maximum input bytes, execution deadline, and whether it accepts partial data. Failure is isolated and observable.

### 5.12 Presentation state

`TrafficViewModel` becomes a coordinator over three smaller state owners or reducers:

```text
TrafficListState
  query, loaded keyset pages, live-generation marker, selection ID

TrafficDetailState
  selected ExchangeRecord, bounded request/response previews,
  annotation views, load/truncation/error state

CaptureStatusState
  proxy/session lifecycle, rates, active connections,
  queue/storage health and degraded-capture indicators
```

The list holds a bounded window of metadata. Body previews are weighted by bytes/characters, not entry count, and are cleared on session clear. Counts, totals, protocol/status summaries, sorting, and filtering come from indexed queries/aggregates rather than repeated O(n) UI scans.

### 5.13 API Studio traffic

API Studio records through an application `CaptureSource` adapter using the same exchange lifecycle and idempotency key. It does not pre-call `ProxyTrafficListener`, inject an internal header into a request, and then depend on the proxy to report the same request again. Proxy routing/fallback attempts are child attempts of one logical API execution, and correlation metadata stays out-of-band.

## 6. Proxy engine architecture

### 6.1 Stable boundary

`:engine:proxy` is a runtime adapter. Its externally visible surface is deliberately small:

```kotlin
interface ProxyRuntime {
    val state: StateFlow<ProxyRuntimeState>

    suspend fun start(configuration: ProxyRuntimeConfiguration): ProxyRuntimeHandle
    suspend fun stop(reason: ProxyStopReason): ProxyStopResult
}

data class ProxyRuntimeConfiguration(
    val bindings: List<ProxyBinding>,
    val accessPolicy: ProxyAccessPolicy,
    val upstreamPolicy: UpstreamPolicy,
    val tlsPolicy: TlsInterceptionPolicy,
    val timeouts: ProxyTimeouts,
    val connectionLimits: ConnectionLimits,
)
```

Dependencies arrive through constructor-injected ports:

```text
CaptureIngress                 traffic side output
CertificateMaterialPort       leaf/CA/TLS material; no key files
BreakpointGate                optional pause/modify decisions
ClientIdentityResolver        authenticated ingress identity
ProxyMetricsSink              non-blocking metrics
Clock                         monotonic/wall timestamps
```

The proxy does not import `:storage`, `:data:desktop`, `:engine:portal`, `:engine:protocol`, `:core:connectivity`, or UI modules. If Gradle currently needs `:engine:certificate` or `:engine:interceptor`, compatibility adapters temporarily preserve behavior while the ports replace those direct edges.

### 6.2 Internal components

```text
ProxyRuntime
  ProxyLifecycle                         atomic allocate/publish/rollback/close
  ListenerManager                        loopback/LAN listener bindings
  AccessGate                             authenticate before forwarding
  ConnectionRegistry                     bounded connection ownership
  ProtocolNegotiator                     CONNECT, TLS ALPN, HTTP protocol selection
  Http1Transport                         initial production transport
  TlsMitmCoordinator                     async certificate/TLS context acquisition
  UpstreamConnectionManager              DNS/connect/TLS/reuse/idle eviction
  StreamingForwarder                     headers and content with watermarks
  ExchangeSequencer                      HTTP/1 ordering; stream IDs for multiplexing
  CaptureTap                              metadata/body reservation and publication
  BreakpointTransportGate                bounded intentional pause
  ProxyMetrics                           event-loop/connection/latency counters
```

Each handler performs one transport responsibility. No handler writes files, calls a DAO, parses GraphQL, formats JSON, generates portal HTML, discovers interfaces, calls Compose state, or launches an unowned coroutine.

### 6.3 Event-loop rule

Allowed on a Netty event loop:

- protocol decoding/encoding;
- constant/bounded header/authority validation;
- connection/exchange state transitions;
- Netty buffer forwarding and write-watermark control;
- capture admission and bounded reserved-byte copy;
- non-blocking metric increments;
- scheduling an already-owned worker task and processing its completion.

Not allowed:

- filesystem/Room/key-store access;
- certificate generation;
- blocking DNS or interface enumeration;
- JSON/XML/GraphQL/protobuf parsing;
- decompression for inspection;
- scripts or formatters;
- arbitrary regex compilation/body scans;
- synchronous logger writers;
- waiting for UI or storage.

Add an event-loop blocking detector/latency metric and fail integration tests when test hooks perform prohibited work.

### 6.4 HTTP/1.1 transport

The first transport refactor must be correct before adding reuse or H2:

1. Parse absolute-form, origin-form plus Host, CONNECT authority-form, bracketed IPv6, IDNA, and default ports through one fuzz-tested authority parser.
2. Represent every request as an immutable `ExchangeContext` with its own ID and captured/request state. Never use one mutable `REQUEST_ATTR` as connection-wide correlation.
3. Stream `HttpRequest`/`HttpContent` downstream-to-upstream and `HttpResponse`/`HttpContent` upstream-to-downstream. Do not install a default `HttpObjectAggregator`.
4. Preserve HTTP/1 response order. Initially serialize one active exchange per downstream connection; later allow safe pipelining only with an ordered response drain.
5. Normalize hop-by-hop headers and framing. A modified full body gets one valid framing strategy; `Content-Length` and `Transfer-Encoding` cannot conflict.
6. Preserve trailers, provisional responses, half-close, cancellation, and partial body outcomes.
7. Upgrade WebSocket by replacing HTTP handlers only after both endpoints accept the upgrade; keep both channels paired until close.
8. Close/cancel the paired channel and capture state on disconnect/failure in either direction.

### 6.5 Upstream connection management

Introduce `UpstreamConnectionManager` behind one internal contract:

```text
Phase A: preserve one-shot connections but centralize ownership and cleanup
Phase B: add bounded HTTP/1 keep-alive pools after ordering tests pass
Phase C: add H2 multiplexed sessions keyed by origin/TLS policy
```

Pool keys include scheme, host, port, upstream proxy, client-certificate identity, TLS verification policy, and protocol. Pools have total/per-key/idle bounds and close on policy/network invalidation. Retries are allowed only when replay safety is proven; a request with partially written non-replayable body is never retried automatically.

`ProxyConnectionPoolManager` is either adapted into this canonical path after tests prove it or removed. Two pooling paths cannot remain.

### 6.6 Netty flow control and timeouts

Use channel writability and watermarks to couple each downstream/upstream pair:

```text
upstream not writable  -> pause downstream reads
client not writable    -> pause upstream reads
writable again         -> resume only that paired direction
```

Define phase-specific deadlines:

- request-head/read idle;
- DNS;
- connect;
- upstream TLS handshake;
- downstream MITM TLS handshake;
- request body idle/total policy;
- response first-byte;
- response body idle;
- breakpoint user decision;
- graceful close/drain.

Timeouts become typed terminal exchange/connection errors. They release buffers, body reservations, upstream leases, and breakpoint sessions in `finally`/promise listeners.

### 6.7 TLS and certificate interaction

The proxy requests leaf/TLS material asynchronously:

```text
CONNECT + SNI/authority
  -> validate target/access policy
  -> request CertificateMaterialPort for normalized host
  -> certificate service single-flight generation on bounded crypto executor
  -> resume event-loop pipeline with cached immutable TLS context
```

Requirements:

- strict upstream verification by default;
- host-scoped, time-limited, visible verification overrides;
- SAN support for DNS, IPv4, and bracketed IPv6 normalization;
- atomic same-host generation and weighted LRU/expiry;
- observed SNI, ALPN, TLS version, cipher, peer chain summary, verification result, and override reason recorded in traffic metadata;
- private keys never passed as file paths and never exposed to UI/portal;
- imported client-certificate selection occurs through a typed key-material port.

### 6.8 Breakpoints and mutation

Breakpoints are the one legitimate path where user speed may intentionally pause transport. They are bounded separately:

```text
compiled immutable rule snapshot
  -> cheap method/authority/header match in proxy
  -> optional bounded body-preview match on worker
  -> BreakpointGate.open(exchange phase, bounded editable view)
  -> application-managed decision with deadline
  -> resume unchanged / validated modification / drop
```

`BreakpointGate` is not a UI callback. The application service exposes pending breakpoint records to any authorized presentation and resolves decisions. The proxy owns the Netty message while paused with exactly one documented reference, a timeout, disconnect cancellation, and a maximum paused-byte/connection budget.

Canonical exchange admission precedes the optional forwarding gate. The connection capture side output publishes request metadata and returns a one-shot exchange handle; a matching breakpoint then suspends forwarding, and the proxy handler consumes that same handle after resume. It never starts a second capture. Desktop Traffic joins the bounded pending record to the capture row by `ExchangeId`, temporarily forces `In Progress`, and applies a typed pause marker without changing `HttpRequestSnapshot` or `HttpResponseSnapshot`. The shell reveals the drawer only after that paused row projection exists, preserving deterministic row-first/drawer-second presentation even while Room publication converges through its asynchronous writer.

HTTP/1 forwarding remains streaming for every connection. A protocol-neutral adapter consults the current
immutable `BreakpointGate` transport prefilter for each request and selectively aggregates only request or
response candidates that may require full-body editing. If a selected message crosses the editable bound, the
adapter replays the retained head/chunks in order and continues streaming. Adding, restoring, enabling,
disabling, or globally toggling rules therefore does not mutate established pipelines or disconnect clients.
The proxy engine owns only the generic selective-aggregation mechanism and does not import rule persistence,
application coordination, protocol matchers, or UI state.

Mutation supports explicit modes:

- headers-only streaming mutation;
- bounded full-body replacement;
- streaming transformation only through a separately approved bounded transformer API;
- reject edit when encoding/framing/body size cannot be handled safely.

Post-capture semantic annotation never participates in forwarding. A rule that explicitly targets a
protocol instead uses the separate application `BreakpointProtocolExtension` seam: the coordinator performs
transport filtering first, invokes only the selected registered extension against its bounded candidate,
and evaluates a compiled extension-owned predicate. Request facts needed at response phase are retained as
small typed `ProtocolObservation` values keyed by `ExchangeId`; raw bodies are not retained. Criteria are
persisted as a normalized protocol ID plus an opaque versioned payload. Unknown extensions, invalid payloads,
and extension failures fail closed. The transport never imports inspector or breakpoint-protocol
implementations.

### 6.9 Access policy and ingress identity

Bindings are explicit:

```text
LoopbackBinding
  default, local desktop clients, no LAN reachability

LanBinding
  explicit user action, selected interfaces, required expiring credential,
  per-client identity/quotas, visible status

InternalGatewayBinding
  loopback-only endpoint for authenticated companion/relay gateway bridging,
  accepts only gateway-issued short-lived credentials
```

Authentication is evaluated before general forwarding. `IngressContext` records a typed source (`Local`, `LanPairedDevice`, `AdbDevice`, `CompanionDirect`, `CompanionRelay`, `Custom`) and optional `ClientIdentity`. Traffic storage knows this neutral context; it does not know PAC, ADB commands, mobile UI, or relay protocols.

### 6.10 Proxy lifecycle

`ProxyRuntime` has an internally serialized state machine:

```text
Stopped
  -> Starting(resources held locally)
       -> Running(handle/endpoints) after every required component succeeds
       -> Failed(reason) after reverse-order rollback
  -> Stopping(reject new, close listeners, drain/cancel pairs)
       -> Stopped
```

Start/stop/restart are idempotent and cancellation-safe. Event loops, server channels, client/upstream channels, resolver resources, worker executors, certificate leases, and metrics registration are owned by the returned handle and awaited on close. No companion object registry or process-global mutable pipeline state exists.

## 7. Protocol extensibility

### 7.1 Separate transport protocols from semantic inspectors

Two extension categories must not be conflated:

```text
Transport/framing adapters
  HTTP/1.1, HTTP/2, HTTP/3, WebSocket upgrade/frame transport
  own wire protocol, flow control, streams, connection lifecycle
  live in/next to :engine:proxy

Semantic inspectors
  GraphQL, gRPC message interpretation, SSE event interpretation,
  JSON-RPC, SOAP, custom payload annotations
  consume captured views; never forward bytes
  live in :engine:protocol or future :inspection:* modules
```

HTTP/2 is not a GraphQL-like plugin, and GraphQL must not become a Netty handler. WebSocket has both parts: proxy transport must support upgrade/frames, while optional semantic inspectors can interpret text/binary messages.

### 7.2 Transport adapter seam

The proxy's internal protocol selector operates on a deliberately small provider contract. It may remain package-internal until a second transport exists:

```kotlin
interface ProxyTransportProvider {
    val protocol: ApplicationProtocol
    fun supports(negotiation: NegotiationResult): Boolean
    fun install(connection: TransportConnection, services: TransportServices)
}
```

`TransportServices` exposes only connection/exchange factories, capture tap, upstream manager, breakpoint gate, TLS observations, and metrics. It does not expose storage, UI, connectivity, or inspectors.

Provider registration occurs in `:products:desktop`/proxy factory. Adding a transport means adding its implementation and registration; generic connection/exchange/capture/query contracts do not change.

Do not freeze a public third-party transport SPI during the HTTP/1 refactor. Promote the package contract into a small `:engine:proxy-spi` module only when HTTP/2 or HTTP/3 proves the abstraction with a second implementation.

### 7.3 Semantic inspector contract

Refocus the existing `ProtocolInspectorRegistry` around an asynchronous, budgeted API:

```kotlin
data class InspectorDescriptor(
    val id: InspectorId,
    val version: InspectorVersion,
    val supportedInputs: Set<InspectionInputKind>,
    val maximumPreviewBytes: Int,
    val deadline: Duration,
)

interface TrafficInspector {
    val descriptor: InspectorDescriptor
    fun matches(heads: ExchangeHeads, content: ContentDescriptor): Boolean
    suspend fun inspect(input: InspectionInput, bodyAccess: BoundedBodyAccess): InspectionResult
}
```

`InspectionInput` contains immutable heads, protocol/connection metadata, body capture status, and bounded message/event views. It never contains a filesystem path or unrestricted `ByteArray`.

Inspector output is open without forcing a new sealed domain subtype for every plugin:

```text
InspectorAnnotation
  inspectorId
  schemaVersion
  subjectId (exchange/message/stream)
  summary
  ordered typed attributes
      Text | Integer | Decimal | Boolean | Timestamp | EnumToken | BodyLink
  completeness
      Complete | Partial(reason) | Failed(reason)
```

Built-in inspectors may expose strongly typed internal results and map them to this versioned annotation envelope. A renderer registered by inspector ID can provide richer UI; unknown annotations still render through the generic typed-attribute view. The core traffic schema does not add GraphQL/gRPC-specific columns every time.

### 7.4 Inspector scheduling and isolation

- select inspectors after request/response heads and capture policy are known;
- run them on bounded CPU/IO workers, never a Netty event loop or SessionWriter actor;
- enforce input-byte, wall-time, output-size, and concurrency limits;
- give streaming inspectors a bounded per-inspector queue and explicit dropped/partial result;
- persist annotations independently so inspector failure cannot roll back traffic;
- allow re-inspection from stored `BodyRef` after capture, useful for new inspector versions;
- surface inspector version/completeness in export and UI.

### 7.5 Protocol addition paths

#### GraphQL

Use one bounded Kotlin serialization GraphQL document parser from two independent adapters. The
`SemanticInspector` adapter runs after capture and emits versioned typed annotations. The
`BreakpointProtocolExtension` adapter compiles operation criteria, detects the request before forwarding,
and retains only bounded operation facts for response matching. Both are registered at product composition;
the proxy and canonical HTTP models remain unchanged.

#### Server-Sent Events

HTTP transport already streams response chunks. An SSE streaming inspector incrementally parses bounded lines/events from a derived stream and emits `DuplexMessageRecord` or SSE annotations linked to the parent exchange. Slow parsing truncates inspection without delaying client forwarding. No HTTP proxy handler change is required after streaming capture exists.

#### WebSocket

Add upgrade and bidirectional frame transport inside the proxy transport package. The transport emits `DuplexMessageRecord` with direction, opcode, fragmentation sequence, ping/pong/close, and bounded body references. Optional WebSocket semantic inspectors consume reassembled bounded messages. Storage and UI use the already-defined duplex model.

#### HTTP/2

The experimental transport now provides H2C prior knowledge/upgrade, TLS ALPN, bounded upstream pooling, and H2
connection/stream flow control. Each stream gets `ExchangeId + StreamId`; connection attributes are never used
as a single current request. Netty owns HPACK and control frames while pseudo-headers map into typed request/
response heads. Capture, body store, storage, queries, stream-scoped breakpoints, API Studio, and Traffic reuse
the stable contracts. Platform/device qualification, rather than a redesign, remains before `SUPPORTED`.

#### gRPC

After H2 works, add a gRPC inspector that consumes stream DATA/trailers through bounded message framing. It records compression, method/service, message sequence/direction, status, and trailers as annotations/duplex messages. The H2 transport does not import protobuf descriptors or gRPC UI code.

#### HTTP/3/QUIC

Add a QUIC listener/transport provider, likely in `:engine:proxy-http3` because of optional native/Netty incubator dependencies. It maps QUIC connections and H3 streams into the same connection/exchange/body contracts. Network migration and UDP lifecycle stay private to this transport. Existing HTTP/1/H2, traffic storage, inspectors, connectivity descriptors, and UI do not change; only composition/capabilities add H3.

### 7.6 Capability truth

Expose one runtime `CapabilityCatalog` assembled from registered, tested implementations:

```text
Supported       wired in production and covered by E2E tests
Experimental    explicitly enabled; limitations published
Planned         documentation only; no product claim
Unavailable     dependency/platform requirement missing
```

README/UI capability claims come from or are checked against this catalog. Dormant classes do not constitute support.

## 8. Connectivity architecture

### 8.1 Connectivity does not own the proxy

The application starts the proxy and publishes a read-only `ProxyEndpointSnapshot`. Connectivity mechanisms consume that snapshot to explain or establish reachability. They never call Netty handlers, register proxy pipelines, start capture storage, or decide traffic semantics.

```text
ProxyLifecycleController
  -> ProxyEndpointSnapshot(version, bindings, access requirements)
       -> ConnectivityCoordinator
            -> PAC/manual/profile artifact providers
            -> ADB/companion/VPN managed mechanisms
            -> portal delivery model
            -> UI capability/setup state
```

When proxy endpoints or network state change, the application produces a new versioned context. Each provider recalculates only its descriptors/artifacts/state.

### 8.2 Avoid a god `ConnectivityProvider`

PAC, manual proxy, and Apple profiles generate instructions/artifacts; they do not have meaningful active runtime ownership. ADB, VPN, pairing, and tunnels do. Use two contracts:

```kotlin
interface SetupDescriptorProvider {
    val id: ConnectivityMechanismId
    val capabilities: Set<ConnectivityCapability>
    fun availability(context: ConnectivityContext): Flow<ConnectivityAvailability>
    suspend fun describe(context: ConnectivityContext): SetupDescriptor
}

interface ManagedConnectivityMechanism {
    val id: ConnectivityMechanismId
    val capabilities: Set<ConnectivityCapability>
    val availability: Flow<ConnectivityAvailability>
    val lifecycle: StateFlow<ConnectivityLifecycle>
    val health: StateFlow<ConnectivityHealth>
    suspend fun activate(request: ActivationRequest): ActivationResult
    suspend fun deactivate(reason: DeactivationReason): DeactivationResult
}
```

A mechanism can implement both only when it genuinely generates setup artifacts and owns a runtime process/session. No-op `activate()` methods are prohibited.

### 8.3 Separate availability, lifecycle, and health

```text
ConnectivityAvailability
  Available
  PlatformUnsupported(platform)
  DependencyMissing(dependency)
  PermissionRequired(permission)
  NetworkUnavailable(reason)
  PolicyDisabled(reason)
  TemporarilyUnavailable(reason, retryHint)

ConnectivityLifecycle
  Inactive
  Activating
  NeedsUserAction(action)
  Active(session)
  Deactivating
  Failed(failure, recoverability)

ConnectivityHealth
  Unknown
  Healthy(lastVerifiedAt)
  Degraded(reason)
  Unreachable(reason)
```

This prevents the intended richer availability enum from becoming another overloaded single state. A supported ADB mechanism can be `Available + Active + Degraded(DeviceDisconnected)`, while an Apple profile provider may only expose availability and an artifact with no lifecycle.

### 8.4 Connectivity context and descriptors

```text
ConnectivityContext
  proxyEndpoints: ProxyEndpointSnapshot
  network: NetworkSnapshot
  portal: PortalEndpointSnapshot?
  publicCa: PublicCertificateDescriptor?
  access: SetupAccessPolicy
  platform: HostPlatform
  version: ContextVersion

SetupDescriptor
  mechanismId
  title/summary tokens
  supported client platforms
  ordered steps
  artifacts
  endpoint/access requirements
  expiry/version
  warnings/limitations
```

Steps and artifacts use typed values such as `OpenUrl`, `DownloadCertificate`, `InstallProfile`, `ConfigureProxy`, `ScanQr`, `RunCommand`, and `ConfirmTrust`; they are not arbitrary UI callbacks. UI renders capabilities/steps generically and may add a renderer for a mechanism without switching in the application core.

### 8.5 Connectivity catalog

`ConnectivityCoordinator` in `:application:desktop` receives a list of registered providers/mechanisms from Koin at the composition root. It:

- combines current endpoint/network/security state into `ConnectivityContext`;
- evaluates availability without hard-coding provider IDs;
- serializes activate/deactivate per mechanism;
- exposes descriptor/lifecycle/health flows to UI;
- invalidates artifacts when input versions change;
- orders application shutdown and revokes temporary setup sessions;
- records audit events without logging credentials.

Adding a mechanism changes its implementation package/module and one composition registration. The coordinator, proxy, traffic store, and generic UI state remain unchanged.

### 8.6 PAC

PAC is a pure setup artifact, not a proxy feature.

```text
PacConfiguration (:core:connectivity)
  selected proxy endpoint(s)
  exact/suffix/wildcard domain rules
  localhost/private-network bypass policy
  DIRECT fallback policy
  custom validated clauses if product-approved
  configuration version

GeneratePacArtifact (:connectivity:desktop/pac)
  pure deterministic configuration + endpoint -> script

PacArtifactService (:application:desktop)
  combines versions, caches artifact, authorizes delivery

Portal route (:connectivity:desktop/portal)
  GET /proxy.pac -> application artifact response
```

Requirements:

- deterministic output and stable ETag/digest;
- correct JavaScript escaping and malformed-input rejection;
- explicit IPv4, bracketed IPv6, hostname, localhost, wildcard/suffix, selective-domain, and DIRECT rules;
- no network-interface discovery inside generator/domain code;
- cache key `(PacConfigurationVersion, ProxyEndpointVersion, NetworkSnapshotVersion, AccessPolicyVersion)`;
- invalidate/regenerate when any input changes;
- no bearer secret embedded in broadly shareable PAC unless threat-modelled and short-lived; authenticated proxy access may use paired device credentials instead;
- golden tests execute generated PAC behavior against a JS evaluator for representative URLs.

Do not generate PAC on every portal request. The portal serves the current immutable artifact.

### 8.7 Manual proxy

`ManualProxySetupProvider` converts a reachable `ProxyEndpoint` and access requirements into typed instructions. It has no activate/deactivate lifecycle. It can produce platform-specific guides through renderer data without putting Android/iOS/macOS branching into proxy or domain entities.

### 8.8 Apple profile

`AppleProfileSetupProvider` builds a profile from public CA material, PAC/manual endpoint data, a stable organization/identifier policy, and expiry. Profile generation is deterministic and signed if product security requires it. It never reads CA private-key files or network interfaces and is delivered through the authorized portal/artifact service.

### 8.9 ADB reverse

`AdbReverseMechanism` is managed because it owns an OS process/device mapping:

```text
availability -> adb executable + supported host platform
activate     -> select device, create reverse mapping to loopback proxy endpoint
health       -> device/mapping observation
deactivate   -> remove only mappings owned by this session
```

It runs commands through an injected bounded process runner, records no credentials in logs, and tags authenticated ingress as `AdbDevice`. ADB changes no proxy pipeline or PAC/manual implementation.

### 8.10 VPN

A VPN mechanism has a long-lived lifecycle and platform privileges. On desktop it may eventually produce a transparent-ingress adapter; on a companion it captures device flows and tunnels explicit proxy streams. It implements `ManagedConnectivityMechanism`, but its packet/TUN implementation lives in a platform module. It cannot be forced through setup-artifact methods, and it cannot expose packet buffers to the HTTP traffic store.

### 8.11 Portal

The portal adapter in `:connectivity:desktop` runs on a separate configured loopback listener and strict authority. It maps an HTTP request to application query/use-case calls:

```text
request
  -> validate authority, method, route, setup-session token
  -> GetSetupPage / GetArtifact use case
  -> immutable PortalResponse(status, headers, body producer)
  -> HTTP response
```

It does not register in `KNetProxyServer.pipelineInitializers`, enumerate interfaces, generate PAC/profile policy, access repositories, or interpolate unvalidated Host values. Routes such as `/setup`, `/proxy.pac`, `/knet-ca.crt`, and profiles are authorized and collision-free.

### 8.12 Platform network state

`PlatformNetworkMonitor` in `:connectivity:desktop/network` emits versioned snapshots:

```text
NetworkSnapshot
  interface IDs/types/up state
  scoped IPv4/IPv6 addresses
  default route/interface
  DNS and VPN indicators when available
  reachability/permission state
  observedAt + version
```

The application derives advertised endpoints from snapshots. It does not equate network state to one IPv4 string or close every proxy channel when the string changes.

On a change:

1. existing viable connections continue;
2. new proxy/portal endpoint snapshots are published if binding/reachability changed;
3. PAC/profile/setup artifact versions invalidate;
4. active mechanisms receive the new context and reconcile or report degraded health;
5. UI shows stale/reconfigured setup state;
6. only connections proven invalid are closed.

## 9. Mobile Companion architecture

### 9.1 Required invariant

A future companion must be addable without migrating or redesigning:

- `:engine:proxy` forwarding and protocol state;
- PAC generation or manual-proxy setup;
- capture events, body ownership, SessionWriter, Room schema fundamentals, or traffic UI paging;
- existing desktop connectivity mechanisms.

The companion is therefore a **connectivity ingress adapter**, not a second owner of the proxy or traffic store.

### 9.2 Deployment model

```text
Mobile device                                           Desktop KNet

products:companion-*                                :products:desktop
  companion UI                                            application services
       |                                                        |
  pairing client <--------- authenticated control ----------> PairingCoordinator
       |                                                        |
  local VPN / local proxy                                CompanionGateway
       |                                                        |
  explicit HTTP proxy stream                             InternalGatewayBinding
       |                                                        |
  direct or relay tunnel ===============================> :engine:proxy
                                                                  |
                                                        normal CaptureIngress
                                                                  |
                                                        normal SessionWriter/store/UI
```

The desktop proxy receives the same HTTP proxy request/CONNECT bytes it receives from a manually configured client. The only additional stable metadata is authenticated `IngressContext`/`ClientIdentity`, already part of the target traffic model.

### 9.3 Current foundation and future runtime boundaries

Desktop-side modules added only when implementation begins:

```text
:connectivity:companion
  PairingCoordinator adapter
  CompanionGateway
  direct-tunnel server/client transport
  trusted-device application adapters
  ManagedConnectivityMechanism implementation

:connectivity:relay
  relay discovery/session transport
  end-to-end encrypted tunnel carrier
  relay health/reconnect
  no proxy or traffic dependency
```

Shared/mobile foundation and product modules now present in this repository:

```text
:core:identity                   shared durable registered-device values
:core:pairing                    shared invitation/handshake/credential values
:core:connectivity               shared capability/setup/lifecycle values
:core:companion                  companion registration/state/policy values
:application:companion           portable companion contracts and workflows
:data:companion                  versioned persistence/control plus platform secure-store adapters
:ui:core                         JVM/Android/iOS Compose design system and adaptive components
:ui:companion:presentation       shared UI state/actions/effects/ViewModel
:ui:companion:sharedUi           shared Compose Multiplatform screens/resources using :ui:core
:connectivity:companion          KMP contracts plus Android and iOS connectivity/security implementations
:products:companion:androidApp   installable Android Compose host and product composition root
:products:companion:iosApp       installable SwiftUI host and Kotlin/Native product composition root
```

Future optional product/runtime leaves:

```text
:connectivity:companion:desktop      desktop control/direct-tunnel implementation if isolated
:connectivity:relay                  relay carrier when off-LAN connectivity is authorized
```

The mobile targets do not depend on `:engine:proxy`, the Room schema, `:application:desktop`, Compose Desktop,
Netty, or filesystem body storage.

### 9.4 Pairing control plane

Pairing is independent of traffic tunneling:

```text
Desktop user starts pairing
  -> PairingCoordinator creates one-time invitation
       desktop identity/public key
       nonce and expiry
       direct endpoint candidates
       optional relay rendezvous ID
       requested scopes/capabilities
  -> QR/deep link or tokenized portal delivery
  -> companion verifies invitation and performs authenticated key agreement
  -> both sides display/confirm verification code or approved trust gesture
  -> desktop stores TrustedDevice in secure adapter
  -> device receives scoped credential/certificate and public CA setup artifact
  -> invitation is consumed/revoked
```

`core:pairing` owns typed messages, state transitions, transcript/version rules, and cryptographic algorithm identifiers. `:connectivity:companion` owns sockets/transports and the desktop secure-store adapter owns private keys/trusted-device persistence.

Pairing state is explicit:

```text
Idle -> Inviting -> Handshaking -> AwaitingUserConfirmation
     -> Paired | Expired | Rejected | Failed | Revoked
```

Credentials are device-scoped, revocable, rotatable, and separate from the KNet CA private key. The companion may receive/install only the CA public certificate through the authenticated flow; mobile OS trust installation remains an explicit user action where the platform requires it.

### 9.5 Companion data plane

The companion offers two device-side acquisition modes without changing the desktop protocol:

1. **OS/manual local proxy mode:** the companion configures or exposes a local proxy and forwards explicit HTTP proxy streams.
2. **VPN mode:** a platform VPN/TUN adapter captures supported TCP flows and a local gateway translates HTTP/TLS destinations into explicit proxy requests/CONNECT streams. Unsupported UDP/QUIC behavior is a declared policy (`Direct`, `Block`, or future supported tunnel), never silently described as inspected.

For each proxied flow:

```text
mobile flow
  -> companion explicit-proxy encoder
       adds device-scoped Proxy-Authorization / connection credential
  -> TunnelStream (bounded flow-control window)
  -> CompanionGateway authenticates device/session
  -> loopback TCP connection to InternalGatewayBinding
  -> unchanged proxy bytes
  -> proxy AccessGate maps credential to ClientIdentity
  -> normal HTTP/CONNECT/TLS/traffic flow
```

The gateway does not parse GraphQL, write traffic, generate certificates, call ViewModels, or mutate proxy pipelines. It authenticates, applies tunnel/session quotas, bridges bytes, propagates half-close/reset/backpressure, and reports health.

### 9.6 Direct and relay transports

`TunnelTransport` is below the companion gateway:

```kotlin
interface TunnelTransport {
    val state: StateFlow<TunnelState>
    suspend fun connect(session: PairedDeviceSession): TunnelConnection
}

interface TunnelConnection {
    suspend fun openStream(metadata: TunnelStreamMetadata): TunnelStream
    suspend fun close(reason: TunnelCloseReason)
}
```

Implementations:

- `DirectLanTunnelTransport` connects companion and desktop directly using paired mutual authentication.
- `RelayTunnelTransport` rendezvous through a service when direct reachability fails.
- a future USB transport can implement the same stream carrier.

The relay transports end-to-end encrypted frames whose keys are held by the paired device and desktop. The relay authenticates/rate-limits routing metadata but cannot read proxy bytes, credentials, captured traffic, or CA material. Changing direct-to-relay transport does not restart the proxy; the gateway opens/reconnects loopback streams as needed.

### 9.7 Tunnel flow control and resource limits

Every tunnel level is bounded:

```text
per-device concurrent streams
per-device bytes/sec and burst
per-stream send/receive window
control-message queue
reconnect attempts/backoff
idle/session lifetime
desktop gateway total streams/memory
```

Backpressure propagates:

```text
desktop proxy socket not writable
  -> gateway stops reading tunnel stream
  -> tunnel window closes
  -> companion stops reading local flow/TUN buffer
```

No layer solves a slow peer by buffering without limit. Stream reset/disconnect closes the paired loopback proxy connection and produces the normal typed connection/exchange terminal state.

### 9.8 Optional remote control/observation plane

Traffic tunneling does not imply remote control access. If the product later permits a companion to view traffic or control capture, add an authenticated `RemoteControlApi` adapter over application commands/queries:

```text
authorized remote request
  -> scope/rate validation
  -> application ProxyControl / TrafficQuery / BodyPreview use case
  -> paged/redacted response
```

It never calls Netty handlers or Room DAOs directly. Default pairing scopes can allow only `TunnelTraffic`; `ViewTraffic`, `ReadBodies`, `ControlProxy`, and `InstallCertificate` are distinct explicit grants. Body access uses bounded range/preview APIs and audit logging.

### 9.9 Network transitions

The companion can change Wi-Fi/cellular/VPN addresses or move between direct and relay transport. Those changes affect only tunnel/health state:

```text
Direct tunnel degrades
  -> ManagedConnectivityMechanism health = Degraded
  -> attempt bounded reconnect or relay fallback
  -> preserve paired identity and proxy/session configuration
  -> individual broken streams terminate normally
```

PAC/manual proxy users are unaffected. The desktop proxy remains running; capture storage and UI continue. A relay outage does not invalidate direct LAN endpoints or delete pairing.

### 9.10 Why this does not force future rework

| Stable subsystem | Companion uses | Future companion-specific addition | Stable subsystem change required later |
|---|---|---|---|
| proxy | internal loopback binding, standard proxy bytes, access credential | gateway/tunnel | none beyond the access/ingress seam built in the target proxy |
| traffic model | optional `ClientIdentity` and `IngressKind` | paired-device value supplied at admission | none |
| capture/storage | normal exchange/body/duplex events | no companion writer | none |
| PAC/manual/profile | existing endpoint/setup providers | companion has its own mechanism descriptor | none |
| connectivity coordinator | managed mechanism registration | companion lifecycle/health provider | no coordinator branch |
| pairing core | versioned handshake/device values | transport and secure-store adapters | additive message versions only |
| UI | generic mechanism descriptors/state | optional companion detail renderer | no proxy/traffic UI redesign |
| relay | no existing dependency | alternate `TunnelTransport` | no proxy or companion application rewrite |

This guarantee depends on implementing `IngressContext`, authenticated bindings, bounded capture contracts, and application command/query APIs now. If a future companion is instead allowed to call proxy handlers or write Room rows, the guarantee is lost.

## 10. Lifecycle, concurrency, security, and network-state architecture

### 10.1 Application lifecycle ownership

Replace the shutdown-hook-only pattern with `ApplicationRuntime`, called on normal Compose window close and by the JVM hook as a last resort:

```text
ApplicationRuntime.start
  1. load validated configuration
  2. open secure key/credential adapter
  3. open/migrate Room database
  4. reconcile body store/deletion outbox
  5. start capture/inspection workers
  6. construct stopped proxy/portal/connectivity runtimes
  7. expose Ready state to UI

ApplicationRuntime.close
  1. reject new UI/control commands
  2. deactivate connectivity mechanisms/revoke transient setup sessions
  3. close portal and proxy listeners
  4. drain or terminally cancel active exchanges within deadline
  5. drain capture queue and finalize/fail body writes
  6. flush DB/deletion outbox/diagnostics
  7. close clients, resolvers, workers, DB, secure adapters
  8. cancel application scope
```

Every step is idempotent, timed, and reports typed failures. Resources publish handles only after full initialization; partial failure closes local allocations in reverse order.

### 10.2 Scope hierarchy

```text
ApplicationScope (SupervisorJob, owned by ApplicationRuntime)
  CaptureSessionScope(sessionId)
    SessionWriter
    body workers
    inspector scheduler
  ProxyRuntime-owned Netty groups/executors
  ConnectivityMechanismScope(mechanism/session)
  ApiClient/Script/Export worker scopes

ViewModelScope (owned by destination/presentation)
  paged query collectors
  selected body/format jobs
```

No repository creates `CoroutineScope(Dispatchers.IO)` without a close owner. Dispatchers/executors are injected by role (`StorageIo`, `InspectorCpu`, `Crypto`, `Script`, `Process`) and bounded/configured centrally. Netty event-loop groups are not coroutine dispatchers for application work.

`SupervisorJob` isolates sibling failures, but every launched job has an error policy and owner; supervision is not permission to ignore failure.

### 10.3 Serialized application commands

Proxy, capture session, portal, and each managed connectivity mechanism use a mutex or command actor around lifecycle transitions. State is a reducer output, not check-then-act mutable flags:

```text
command + current state -> accepted transition | idempotent result | typed rejection
```

Examples:

- concurrent `start/start` returns one running handle/result;
- `stop` during `Starting` cancels and rolls back before becoming `Stopped`;
- `start` after `Failed` requires cleanup completion or returns a precise blocked reason;
- app shutdown has priority over new starts;
- network reconciliation changes endpoint versions but does not race proxy start/stop.

### 10.4 Concurrency domains and crossings

| Domain | Owns | Permitted crossing |
|---|---|---|
| Netty event loops | channel/pipeline/buffer/protocol state | non-blocking capture reservation/publication; schedule bounded service future |
| SessionWriter actor | ordered exchange reducers and persistence sequencing | batched suspend calls to storage/body ports |
| storage IO pool | Room and body file IO | typed results back to SessionWriter/application |
| inspector CPU pool | bounded parsers/decoders | bounded BodyAccess and annotations |
| crypto pool | CA leaf/key/TLS material work | immutable result/failure to event loop/application |
| script process/executor | sandboxed script evaluation | bounded request/result DTOs only |
| application command scope | lifecycle/policy orchestration | ports and StateFlow state snapshots |
| Compose main thread | presentation state/rendering | application commands and paged query collection |

Crossing rules:

- mutable Netty buffers never cross;
- file paths never cross into UI/domain; use `BodyId`/`BodyRef`;
- large bodies never travel through `StateFlow`/`SharedFlow`;
- StateFlow values are immutable and bounded;
- one slow Flow collector cannot block a producer; queries are pull/paged and status updates are compact;
- callbacks into a thread-affine owner are scheduled back onto that owner before mutation.

### 10.5 Mutable-state ownership

| State | Single owner |
|---|---|
| channel and HTTP stream state | associated Netty event loop |
| proxy lifecycle/bindings | ProxyRuntime lifecycle actor |
| exchange persistence reducer | SessionWriter |
| body write handle | body worker assigned by SessionWriter |
| breakpoint decision | BreakpointCoordinator entry with atomic terminal resolution |
| leaf cache entry creation | CertificateService single-flight key |
| connectivity lifecycle | one mechanism actor |
| trusted devices/pairing sessions | PairingCoordinator + secure store transaction |
| UI list/detail | destination ViewModel/reducer |

Concurrent maps may implement lookup, but they do not replace an owner or lifecycle invariant.

### 10.6 Security boundaries and defaults

#### Listener and access security

- proxy and portal bind loopback by default;
- LAN listeners are separate explicit bindings with required short-lived/device credentials;
- companion gateway uses a loopback-only internal binding and paired credentials;
- every binding has per-client and global connection/rate/byte limits;
- destination policy can restrict localhost/private ranges for non-local clients;
- setup/portal routes require strict authority and scoped token/session;
- active exposure, paired clients, and revocation are visible in UI.

#### TLS and CA security

- upstream certificate verification is on by default;
- bypass is host-scoped, time-limited, recorded, and displayed;
- root/private client keys use OS-protected storage where possible;
- fallback files are encrypted/owner-only and permission-validated;
- key export/rotation/recovery are explicit audited workflows;
- leaf keys/contexts use bounded expiring caches;
- portal/companion receive public CA material only.

#### Captured-data security

- headers/bodies are considered secrets by default;
- configurable redaction runs before persistence/export/remote observation where policy permits;
- authorization, cookies, tokens, and common secret fields have built-in masking rules;
- DB/body directories have restrictive permissions and optional per-session encryption;
- retention is bounded and clear removes metadata, bodies, preview caches, annotations, and pending work;
- diagnostic/log export excludes bodies/credentials/private keys by default;
- remote body reads require a distinct pairing scope and audit event.

#### Script security

- untrusted scripts run in a killable isolated process/context with CPU/wall/memory/input/output bounds;
- request/response values are bound as data, not interpolated into source;
- Kotlin/JVM scripting is explicitly trusted-local-only or removed from untrusted/imported workflows;
- scripts cannot receive Netty buffers, key material, file paths, repositories, or arbitrary application objects;
- script failure cannot block forwarding or corrupt capture ordering.

#### Pairing/relay security

- invitations are one-time, expiring, and user-confirmed;
- paired device credentials are scoped/revocable/rotatable;
- tunnel endpoints use mutual authentication and replay protection;
- relay data is end-to-end encrypted and relay-visible metadata is minimized;
- direct/relay sessions enforce quotas and bounded reconnect;
- pairing, tunnel, remote query, and CA-install permissions are separate scopes.

### 10.7 Typed error architecture

Avoid generic strings as cross-layer state. Application-visible failures are sealed/typed with a custom/diagnostic fallback:

```text
ProxyFailure
  PortInUse | PermissionDenied | InvalidBinding | TlsMaterialUnavailable |
  UpstreamDns | UpstreamConnect | UpstreamTls | Timeout(phase) |
  ProtocolViolation | ResourceLimit | Custom(code, safeMessage)

CaptureFailure
  QueueOverloaded | BodyLimit | DiskFull | PermissionDenied |
  DatabaseUnavailable | MigrationFailed | ReconciliationFailed | Custom(...)

ConnectivityFailure
  RequirementMissing | UserActionRejected | DeviceDisconnected |
  CommandFailed | TunnelAuthentication | RelayUnavailable | Custom(...)
```

Internal throwable details go to redacted diagnostics with correlation IDs. UI receives safe messages and recovery actions. Every failed exchange/session/mechanism ends in a terminal state rather than remaining in a pending map.

### 10.8 Observability

Expose bounded metrics without coupling logs to captured traffic:

- downstream/upstream connections and streams;
- event-loop task delay/stalls and channel writability;
- DNS/connect/TLS/TTFB/download distributions;
- capture queue event/byte depth, high-water marks, body truncations, metadata gaps;
- SessionWriter batch/commit latency and active exchange reducers;
- body-store bytes/files/write failures/retention progress;
- certificate hit/miss/single-flight/generation/eviction;
- inspector queue/duration/timeout/failure by inspector ID;
- paged-query and body-preview latency;
- connectivity/companion tunnel count, health, reconnect, and quota rejects;
- shutdown duration/forced closures.

Diagnostic logging uses structured connection/exchange/session IDs, sampling, asynchronous bounded writers, centralized redaction, and rotation. Per-chunk/per-message INFO logging is prohibited.

### 10.9 Network reconciliation

One `NetworkStateCoordinator` consumes `PlatformNetworkMonitor` and owns endpoint reconciliation. UI and proxy do not run duplicate polling flows.

```text
new NetworkSnapshot
  -> compare semantic interface/route/address changes
  -> update reachable ProxyEndpointSnapshot/PortalEndpointSnapshot
  -> rebind only listeners whose binding policy requires it
  -> version/invalidate setup artifacts
  -> notify managed connectivity mechanisms
  -> preserve viable active connections
  -> publish health/recovery state
```

Wi-Fi/Ethernet/hotspot/VPN/IPv4/IPv6 changes are data in a snapshot, not special-case branches spread across ViewModels and handlers.

## 11. Incremental migration and implementation plan

### Migration rules

1. No big-bang directory rename or model replacement.
2. Add a new contract and adapter, migrate one caller/path, verify, then remove the legacy path.
3. Never run two authoritative capture writers. A feature/configuration switch may select old or new for a test build, but one session has one writer.
4. Keep old database columns readable until migration and rollback compatibility are proven; new writes use the new schema once cut over.
5. Compatibility adapters require an owner, removal phase, and architecture rule preventing new callers.
6. Every behavior change lands with loopback E2E and failure-path tests before cleanup.
7. Keep KNet usable at the end of every phase.
8. Update `docs/implementation_plan.md` only when a phase actually starts, following repository rules.

### Phase 0 — Approve contracts, baselines, and guardrails

**ADD**

- ADRs for dependency direction, capture/body ownership, listener/access policy, protocol capability truth, connectivity contracts, companion ingress, and retention/security defaults.
- test taxonomy and deterministic loopback benchmark/fixture harness using `:testingServer`.
- Gradle dependency rules and public API reports in reporting mode, then enforced for new edges.
- baseline measurements for current small traffic, 10 MiB boundary, concurrency, memory/direct memory, DB/file growth, UI rows, lifecycle, and leak paths.

**MODIFY**

- rename shallow “stress/integration” tests or strengthen them so their names match behavior.
- document supported/experimental/planned capabilities.

**Affected:** build logic, `:testingServer`, proxy/certificate/interceptor/storage tests, docs/CI.

**Solves/prevents:** false confidence and undocumented dependency/API expansion (F-26, F-27, F-28).

**Exit criteria:** reproducible baseline command/report; a forbidden UI-to-engine edge fails a test; leak-enabled loopback proxy fixture exists; architecture contracts are approved before production edits.

### Phase 1 — Contain current P0 security and correctness failures

Work inside current modules before structural migration.

**MODIFY**

- `KNetProxyServer`: loopback default, explicit LAN mode, atomic start rollback, awaited stop.
- `KNetStreamingProxyHandler`: robust authority parser, strict upstream TLS default, and bounded one-active-request HTTP/1 ordering.
- `InterceptCoordinator`: exact reference-count ownership across resume/modify/drop/timeout/disconnect/removal.
- `KNetInterceptorHandler`/outbound capture: separate request and response breakpoint phase; always terminally capture or drop once.
- `ProxyEngineRepositoryImpl`/DAO: monotonic update or single ordered compatibility writer; no late pending overwrite.
- Room migrations: add every version step; remove production destructive fallback.
- certificate/body/key files: owner-only permissions; sanitize aliases; clear removes payloads and UI body cache.
- `ApplicationLifecycle`: register current resources and invoke on normal window close.

**REMOVE/DISABLE**

- unauthenticated LAN exposure by default;
- trust-all upstream as default;
- portal path interception for arbitrary authorities.

**Affected:** `:engine:proxy`, `:engine:interceptor`, `:engine:certificate`, `:engine:portal`, `:data:desktop`, `:storage`, `:products:desktop`, traffic/certificate UI status.

**Solves:** F-01, F-04, F-05, F-06, F-07, F-09, F-10, F-11, F-12, F-13, F-22, F-29.

**Exit criteria:** no Netty leaks in all breakpoint branches; no state regression under forced write reordering; pipelined test cannot reorder; fresh install is loopback-only/strict TLS; start/stop/port-in-use tests leave no resource; full upgrade suite preserves data.

### Phase 2 — Add stable core/application seams without changing behavior

**ADD**

- `:core:traffic` with IDs, ordered headers, request/response heads, `BodyRef`, lifecycle/events, ingress identity, and compatibility mappers.
- `:application:desktop` with proxy/session/traffic/certificate/breakpoint ports and lifecycle command/state reducers.
- Koin bindings in `:products:desktop` connecting current implementations through adapters.
- architecture rules: new UI code cannot import engines; new proxy code cannot import data/storage/portal/protocol/connectivity.

**MODIFY**

- wrap current `KNetProxyServer` as `ProxyRuntime` without changing its internals yet;
- map current listener callbacks into a temporary `LegacyCaptureIngressAdapter`;
- expose current traffic repository through initial paged/query-shaped application contracts, even if internally still full-query until Phase 3;
- route one low-risk UI control (proxy state/start/stop) through application use cases as a proof.

**Affected:** new modules, `:products:desktop`, `:engine:proxy`, `:data:desktop`, `:core:domain`, `:ui:desktop:traffic`.

**Solves:** missing application layer, direct implementation dependencies, global model coupling (F-08, F-10, F-20, F-25).

**Exit criteria:** new modules contain no forbidden framework/platform dependencies; current behavior/tests remain green; one vertical UI-to-runtime path uses only application/core contracts; legacy adapter has a Phase 4 deletion target.

### Phase 3 — Canonical session writer, body store, and indexed queries

**ADD**

- new Room schema for sessions/connections/exchanges/body objects/annotations/gaps/deletion outbox with monotonic state/version.
- `SessionWriter`, byte-aware capture queues, file `BodyStore`, retention/reconciliation, and paged `TrafficQuery` adapters.
- compatibility mapper/importer for existing transaction rows/body paths.
- session storage/queue health metrics.

**MODIFY**

- choose new writer at composition for new sessions; old sessions remain readable/migrate safely.
- map legacy full request/response callbacks to new heads/body chunks as a temporary source.
- update clear/export/direct lookup to use canonical store and BodyRef.
- preserve duplicate headers and protocol/timing/error fields.

**MOVE**

- `FilePayloadStore`, header mapper, retention, and useful `SessionBuffer` policy concepts from `:engine:session` to storage/application ownership.

**REMOVE after cutover**

- `ProxyEngineRepositoryImpl.pendingRequests` as the authoritative lifecycle;
- dual `REPLACE` insert path;
- production `SELECT *` traffic observation;
- database-only clear.

**Affected:** `:core:traffic`, `:application:desktop`, `:storage`, `:data:desktop`, `:engine:session`, traffic/export tests.

**Solves:** F-05, F-07, F-08, F-13, F-18, F-20, F-21, F-26.

**Exit criteria:** one writer per session; fast responses cannot regress; queue saturation is visible/deterministic; session clear converges DB/files; migration from every supported schema passes; 100,000-row query fixture pages/filters without full memory load.

### Phase 4 — Streaming proxy and bounded capture integration

**ADD/MODIFY**

- replace default client/upstream aggregators with streaming HTTP/1 forwarders.
- create per-connection ordered exchange state and capture tap using reservation-before-copy.
- implement Netty writability coupling, phase timeouts, disconnect/cancellation propagation, terminal reconciliation.
- move certificate generation to bounded single-flight crypto worker; weighted expiring cache.
- centralize upstream connection manager; initially one-shot, then bounded reuse after correctness tests.
- move parsing/file/protocol/logging/discovery work off event loops.
- add event-loop lag/direct-memory/connection/capture metrics.

**REMOVE**

- legacy `ProxyTrafficListener` hot path and `LegacyCaptureIngressAdapter` after the new path proves parity;
- static pipeline initializers;
- dormant or duplicate connection-pool path.

**Affected:** `:engine:proxy`, `:engine:certificate`, `:engine:interceptor`, `:core:traffic`, `:application:desktop`, `:products:desktop`.

**Solves:** F-02, F-03, F-06, F-08, F-17, F-25.

**Exit criteria:** 500 MiB response passes through with bounded memory and configured truncated/complete capture; 100 × 10 MiB workload stays within declared budget; no prohibited event-loop work; HTTP/1 keep-alive/order/slow peer tests pass; 30-minute soak has stable direct memory/threads/active reducers.

### Phase 5 — Breakpoint, UI, and module-boundary migration

**MODIFY**

- replace global breakpoint registries/sessions with application-owned `BreakpointCoordinator` and engine `BreakpointGate`.
- precompile rule snapshots and enforce pause byte/connection/time limits.
- migrate traffic list to keyset pages and detail to bounded range/preview reads.
- split `TrafficViewModel` list/detail/status state and clear weighted caches.
- migrate certificate/settings/breakpoint/API Studio UI modules from direct engine dependencies to application use cases, one feature at a time.
- make `WorkspaceHost` shell coordinate typed navigation results rather than hold engine-aware cross-feature state.
- move `TrafficItemUiState` and other presentation models out of `:core:domain`.
- replace ineffective logging configuration/hot-path logs with bounded structured diagnostics.

**MERGE/REMOVE**

- consolidate `:engine:traffic` rewrite concepts into the canonical breakpoint/rewrite service.
- remove unused pass-through/duplicate models only after all callers use new ports.

**Affected:** `:engine:interceptor`, `:engine:traffic`, `:application:desktop`, `:core:domain`, `:engine:formatter`, `:core:logger`, all desktop feature modules, `:data:desktop`.

**Solves:** F-04, F-09, F-18, F-19, F-20, F-22, F-24, F-25, F-30.

**Exit criteria:** no UI project dependency on concrete engines/storage; inactive features release collectors/detail work; UI memory follows page/preview bounds at 100,000 rows; breakpoint soak has no leaks or unbounded pause state; dependency rules are enforced.

### Phase 6 — Semantic inspector architecture and capability truth

**MODIFY**

- adapt `ProtocolInspectorRegistry` and GraphQL inspector to the budgeted async annotation contract.
- add inspector scheduler, BodyAccess, annotation persistence/query, generic UI renderer.
- classify or remove dormant WebSocket/gRPC handlers until their transport prerequisites exist.
- publish runtime capability catalog and align README/UI/docs.

**Affected:** `:engine:protocol`, `:application:desktop`, `:core:traffic`, `:storage`, `:data:desktop`, traffic/http-panel UI.

**Solves:** synchronous GraphQL parsing, closed metadata coupling, and false protocol claims (F-03, F-20, F-26).

**Exit criteria:** GraphQL failure/timeout cannot affect forwarding/capture; annotations are versioned/re-runnable; unknown annotations render generically; every “Supported” capability has a production E2E test.

### Phase 7 — Connectivity foundation, PAC/manual/profile, and portal isolation

**ADD**

- `:core:connectivity` contracts/values.
- `:connectivity:desktop` network monitor plus manual/PAC/Apple packages.
- application `ConnectivityCoordinator`, endpoint snapshots, artifact caching/versioning.
- deterministic PAC generation and golden behavior tests.

**MODIFY**

- add a strict-authority setup delivery adapter in `:connectivity:desktop` calling application artifact use cases; retire `:engine:portal`.
- update certificate/setup UI to render descriptors/capabilities/state.
- replace duplicate IPv4 polling and blanket channel flush with versioned network reconciliation.

**REMOVE**

- portal insertion into proxy pipeline;
- portal network-interface enumeration/business logic;
- provider-type switches in generic UI/application code.

**Affected:** new connectivity modules, `:application:desktop`, `:engine:proxy` only to expose endpoint snapshot through its port, `:ui:desktop:certificate`, settings/app shell, `:data:desktop` network adapter.

**Solves:** F-12, F-16, F-23 and preserves the intended proxy/portal boundary.

**Exit criteria:** `/proxy.pac` behavior is deterministic/versioned; arbitrary upstream `/setup` paths are forwarded; manual/PAC/Apple providers add through registration; IPv4/IPv6/VPN changes update artifacts/health without unnecessary proxy restart.

### Phase 8 — Pairing and mobile-ready authenticated ingress

This phase creates the stable boundary; it does not require shipping a mobile app.

**MODIFY**

- expand `:core:pairing` with versioned invitation/handshake/device/scope state.
- add application `PairingCoordinator` ports and secure trusted-device storage.
- add loopback-only `InternalGatewayBinding`, typed `IngressContext`, and device-scoped access policy to the proxy target if not already completed.
- add QR/deep-link descriptor support and tokenized onboarding through connectivity/portal.

**ADD tests**

- invitation expiry/replay/revocation;
- credential-to-ingress identity mapping;
- gateway-like loopback byte bridge under backpressure;
- network change/reconnect state without proxy restart.

**Affected:** `:core:pairing`, `:core:connectivity`, `:core:traffic`, `:application:desktop`, secure storage adapter, neutral proxy ingress attribution, connectivity setup UI.

**Solves/prevents:** future companion feature leakage into proxy/traffic/PAC and closes LAN authentication design.

**Exit criteria:** a test client can pair, receive a scoped credential, bridge an authenticated standard proxy stream, appear with correct ingress identity, revoke access, and leave all ordinary proxy/capture behavior unchanged.

### Phase 9 — Companion and relay product work

The portable/Android adapter foundation is implemented. Start each remaining product/runtime leaf only with a real
target and its security/qualification requirements.

**ADD**

- desktop `:connectivity:companion` gateway/direct tunnel and managed mechanism.
- platform companion app/core/VPN modules.
- optional `:connectivity:relay` E2E tunnel carrier.
- device/tunnel UI and security/recovery flows.

**DO NOT MODIFY**

- proxy forwarding/capture contracts;
- SessionWriter/body store/query architecture;
- PAC/manual/Apple implementations;
- existing semantic inspectors.

**Exit criteria:** direct and relay paths pass the same proxy conformance suite; relay cannot decrypt content; tunnel backpressure is bounded; device revocation closes access; companion network transitions do not restart desktop proxy/capture.

### Phase 10 — Protocol transports and inspectors as independent increments

Deliver independently after Phase 4/6 contracts are proven:

1. WebSocket upgrade/frame transport plus duplex UI/storage tests.
2. SSE streaming inspector.
3. HTTP/2 ALPN/multiplex transport and stream-aware breakpoints.
4. gRPC inspector on H2.
5. HTTP/3/QUIC transport only after explicit product/platform decision.

Each feature must meet protocol conformance, failure, backpressure, body limit, lifecycle, and long-run tests before capability status becomes Supported.

### Phase 11 — Performance, soak, and production gates

**ADD/ENFORCE**

- reference hardware/JVM profiles and repeatable workloads;
- 10/100/1,000 connection tests and churn;
- 500 MiB pass-through, 100 × 10 MiB concurrency, slow client/upstream/disk, queue saturation;
- 1k/10k/100k traffic UI/query tests;
- multi-hour soak tracking heap/direct memory/threads/file descriptors/DB/body directory;
- disk-full, corrupt-body, migration, network change, pairing/tunnel failure, and shutdown deadline tests;
- per-phase regression budgets for p95/p99 latency, throughput, event-loop lag, allocation, queue depth, storage, query, and frame time;
- dependency/security/vulnerability/license/SBOM/release checks.

**Exit criteria:** resource usage stabilizes under retention; no unbounded path remains; declared capacity envelope and limitations are published; production capability/security claims match tests.

### Recommended pull-request order

Keep PRs narrow:

1. ADRs, architecture checks, test taxonomy, loopback harness.
2. loopback default, strict portal authority, strict upstream TLS.
3. ByteBuf ownership and HTTP/1 ordering tests/fixes.
4. lifecycle rollback/shutdown and migration completeness.
5. `:core:traffic` and compatibility mappers.
6. `:application:desktop` ports/controllers and proxy-control vertical slice.
7. new schema/body store/SessionWriter and old-data reader.
8. capture queues/legacy source cutover.
9. paged queries/retention/clear/reconciliation.
10. streaming HTTP/1 proxy and new CaptureIngress cutover.
11. certificate single-flight/upstream manager/timeouts/watermarks.
12. breakpoint coordinator and bounded mutation.
13. traffic UI paging/detail and feature dependency inversions.
14. async inspector host/GraphQL migration/capability catalog.
15. `:core:connectivity`, network monitor, manual/PAC/profile.
16. isolated portal/artifact routes and descriptor-driven UI.
17. pairing/authenticated internal ingress foundation.
18. optional companion/relay or protocol feature PRs independently.

## 12. Future-feature change matrix

“Stable core change” means a breaking or responsibility-changing modification to `:core:traffic`, SessionWriter/body store/query contracts, or generic proxy runtime. Composition registration and implementation additions are not counted as core changes.

| Feature | Add | Modify/register | Stable architecture reused | Stable core change after target? | Prerequisites |
|---|---|---|---|---|---|
| Manual proxy | `manual` setup provider | Koin registration; optional platform renderer | endpoint snapshot, setup descriptor, access policy | **None** | connectivity foundation |
| PAC | PAC generator/provider and portal artifact route | Koin registration, settings/UI configuration | endpoint/network versions, artifact service, portal delivery | **None** | connectivity foundation, portal isolation |
| Apple profile | profile provider/renderer | registration and Apple-specific setup renderer | public CA descriptor, PAC/manual endpoint, artifact service | **None** | secure CA public export, connectivity foundation |
| ADB reverse | managed ADB mechanism/process adapter | registration and optional device UI | lifecycle/health, endpoint snapshot, ingress identity | **None** | authenticated internal/loopback endpoint, process runner |
| Desktop VPN ingress | platform TUN/VPN mechanism and flow-to-explicit-proxy adapter | registration; permissions UI | managed lifecycle, internal proxy binding, normal capture | **None** for supported HTTP/TCP flows | platform privileges, explicit protocol/UDP policy |
| Mobile Companion | desktop companion gateway/direct transport plus mobile apps/VPN adapters | registration, pairing/device UI | core pairing/connectivity, internal binding, ingress identity, all traffic/storage/UI | **None** | Phase 8 pairing/authenticated ingress |
| Remote relay | E2E relay `TunnelTransport` | companion transport selection/health registration | paired identity, companion gateway, proxy/internal binding | **None** | companion direct path, relay service/security review |
| Remote traffic viewer | remote control/query API adapter | scope policy and optional companion UI | application paged queries/body preview, redaction/audit | **None** | paired scopes, rate limits, privacy design |
| GraphQL | add shared parser, `SemanticInspector`, `BreakpointProtocolExtension`, optional rich renderer | inspector/extension registration and schema-driven rule fields | HTTP heads, bounded bodies, generic annotations, protocol rule registry | **None** | async inspector host and bounded breakpoint gate |
| SSE | streaming SSE inspector and event renderer | inspector registration | streaming capture, duplex/event record, body budgets | **None** | streaming HTTP/1 capture |
| WebSocket | proxy upgrade/frame transport package, optional message inspectors/renderers | transport and inspector registration | connection/duplex message/body model, breakpoint/capture bounds | **None** | streaming proxy and paired-channel lifecycle |
| HTTP/2 | H2 transport provider | ALPN/provider registration and capability catalog | connection/exchange/StreamId, body/capture/store/query | **None** | streaming proxy, TLS/ALPN, multiplex tests |
| gRPC | gRPC framing/semantic inspector and renderer | inspector registration | H2 stream events, trailers, duplex/body refs, annotations | **None** | supported HTTP/2 |
| HTTP/3 | optional QUIC/H3 transport module | listener/provider registration and capability catalog | connection/exchange/StreamId, capture/store/query | **None** | QUIC library/platform decision, H2-grade model proven |
| JSON-RPC/SOAP/custom HTTP inspector | inspector implementation | inspector registration; optional renderer | bounded inspector API and annotations | **None** | async inspector host |
| Third-party inspectors | signed package loader, permission/resource policy, SDK | registry and UI management | versioned inspector contract/annotations | **None**, but public SPI promotion required | at least two proven internal inspectors and plugin threat model |
| HAR export | streaming exporter | command/UI registration | paged query, ordered headers, BodyRef range streams, redaction | **None** | canonical store/query |
| Request rewrite rules | canonical rule/compiler and header/body transformer | breakpoint/rewrite registration and UI | exchange IDs, bounded body/mutation, framing normalizer | **None** | safe streaming proxy/interceptor migration |
| Response rewrite rules | response transformer | same | streaming response/body policy/framing | **None** | safe response streaming and encoding policy |
| Script transformations | isolated transformer adapter/process | rule/action registration and permissions UI | bounded transformation contract/body refs | **None** | killable script runtime, explicit trust model |
| Desktop/mobile platform addition | platform app and adapters | composition for that platform | portable traffic/connectivity/pairing values only | **None** to desktop proxy; platform adapters are new | real second target and CI |

### 12.1 Expected module touch patterns

```text
New semantic inspector
  add :engine:protocol/<inspector> package or :inspection:<name>
  register in apps:desktop
  optionally add UI renderer
  no proxy/storage schema/core traffic edit

New connectivity mechanism
  add :connectivity:desktop/<mechanism> or dedicated module
  register in apps:desktop
  optionally add specialized UI renderer
  no proxy/capture/PAC/manual edit

New proxy transport
  add :engine:proxy transport provider/package/module
  register in proxy factory/apps:desktop
  add conformance/capability tests
  no traffic storage/query/connectivity/UI contract edit

New companion transport
  add TunnelTransport implementation
  register in companion connectivity module
  no proxy/capture/PAC/manual edit
```

## 13. Decisions requiring approval before implementation

The architecture is ready to implement once these decisions are accepted:

1. Add the four near-term modules and keep current module names elsewhere during migration.
2. Treat `:application:desktop` as the sole cross-engine orchestration layer and enforce UI/runtime dependency rules.
3. Adopt connection/exchange/stream/duplex plus `BodyRef` as the stable traffic model; bodies never live in metadata events/UI rows.
4. Use reservation-before-copy and byte-aware bounded capture queues; forwarding survives capture truncation/failure by default.
5. Make loopback/strict TLS the defaults and require credentials for LAN/internal gateway bindings.
6. Separate setup-artifact providers from managed connectivity mechanisms; availability, lifecycle, and health remain separate.
7. Keep portal on a strict authority/separate listener and out of the proxy pipeline.
8. Make future companion/relay transport standard authenticated proxy streams through a loopback internal gateway binding.
9. Treat HTTP versions/WebSocket as transport work and GraphQL/gRPC/SSE semantics as bounded inspector work.
10. Defer broad module renames, a public third-party plugin SPI, and full companion apps until the foundational contracts have two real implementations/consumers.

## Target Architecture Verdict

**YES — THE FOUNDATION MIGRATION IS NOW IMPLEMENTED.**

This design can realistically scale KNet toward a Charles/mitmproxy-class tool without a later whole-product rewrite. The production foundation has replaced the critical architectural dead ends: default full-message buffering, event-loop side effects, cross-layer body ownership, unordered persistence, global breakpoint/pipeline state, and proxy/connectivity/portal coupling.

The design also gives Mobile Companion, VPN, ADB, PAC, manual setup, Apple profiles, and relay independent additive boundaries. A companion changes how authenticated proxy streams reach the desktop; it does not create a second proxy/capture architecture. Protocol growth is similarly separated into transport adapters and semantic inspectors over stable connection/exchange/body contracts.

The qualification is product capability, not architecture: KNet is not yet Charles/mitmproxy-class in protocol
breadth. HTTP/2, native gRPC, HTTP/1.1 WebSocket, and modern `graphql-transport-ws` semantics now have real
experimental implementations and local real-socket evidence, but still require Windows/Linux, Android/iOS Wi-Fi
where applicable, external `wss`/device matrices, and release-soak evidence before `SUPPORTED`. HTTP/3, WebSocket
over HTTP/2, VPN, relay, companion applications, and legacy `graphql-ws` still require real implementations.
Those additions can reuse the stable proxy ingress, canonical traffic/body/session, application, connectivity,
pairing, and inspector seams rather than migrating them.
