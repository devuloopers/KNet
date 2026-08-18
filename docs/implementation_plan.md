# KNet Implementation Plan

## Phase 1: Packaged Desktop App Runtime Module Fix [COMPLETED]
* Configured `products/desktop/build.gradle.kts` with essential JPMS runtime modules (`jdk.unsupported`, `jdk.crypto.ec`, `jdk.crypto.cryptoki`, `java.sql`, `java.naming`, `java.management`, `java.scripting`, `java.compiler`, `java.instrument`, `java.security.jgss`) to resolve Netty bootstrap (`sun.misc.Unsafe`), SQLite JDBC, and SSL provider dependencies in native `jlink` packaged distributions.

## Phase 2: Engine Startup Diagnostics & Error Presentation [COMPLETED]
* Implemented `TrafficErrorBanner` in `:ui:desktop:traffic` and wired `TrafficIntent.DismissEngineError` and `TrafficState.engineErrorMessage` to present clear, dismissible visual error feedback whenever port binding fails or proxy engine errors occur.

## Phase 3: Distribution Packaging & Verification [COMPLETED]
* Executed full multi-module `./gradlew test check` test suite (232 tasks passed).
* Executed `./gradlew :products:desktop:createDistributable` to verify native `jlink` runtime image generation and distribution assembly.

## Phase 4: Full Multi-Platform Brand & Icon Suite (macOS, Windows, Linux, Web) [COMPLETED]
* Designed mathematically centered master SVG logos, symbols, and wordmarks with balanced 70.92px horizontal and 81.60px vertical margins in `brand/svg/`.
* Generated brand kit via LogoLoom MCP (`export_brand_kit`, `optimize_svg`) with 25 web and social assets in `brand/kit/`.
* Compiled native platform desktop icons: macOS `.icns` (315 KB), Windows 7-layer `.ico` (27 KB), and Linux FreeDesktop `.png` suite in `brand/`.
* Documented brand standards in `docs/brand_guidelines.md`.

## Phase 5: Official Brand & Application Icon Adoption [COMPLETED]
* Replaced legacy application resources in `products/desktop/src/jvmMain/resources/icons/` with the official centered icons (`KNet.icns`, `KNet.ico`, `KNet.png`, `KNet.svg`).
* Verified Compose Desktop window icon (`DesktopBootstrap.kt`) and sidebar branding badge (`NavigationOverlay.kt`).
* Verified native packaging builds with `./gradlew :products:desktop:createDistributable` and `./gradlew :products:desktop:packageDmg` (`products/desktop/build/compose/binaries/main/dmg/KNet-1.0.0.dmg`).

## Phase 6: Multi-Platform Installer Branding & ARM64 Linux Support [COMPLETED]
* Configured `TargetFormat.Exe` alongside `TargetFormat.Msi` in `products/desktop/build.gradle.kts` with consistent `upgradeUuid`, `perUserInstall`, and `shortcut` options so Windows setup executables embed `KNet.ico` directly into the PE header.
* Designed high-DPI Retina installer window background (`brand/dmg/knet-dmg-background.png`, `brand/dmg/knet-dmg-background@2x.png`) with KNet dark theme, logo, and drag-to-Applications layout.
* Configured `.github/workflows/release.yml` with `create-dmg` post-processing and `fileicon` stamping for macOS branded DMG packaging (`KNet-${VERSION}-mac.dmg`).
* Added `Package macOS Portable Zip` step (`KNet-${VERSION}-mac.zip`) using Apple's `ditto` utility to preserve native `.app` bundle icon attributes and permissions directly in Finder upon extraction.
* Added `Package Windows Portable Zip` step (`KNet-${VERSION}-windows-x64.zip`) using PowerShell `Compress-Archive` to provide a zero-installation, non-admin direct-run package for Windows users.
* Embedded `knet.desktop` FreeDesktop launcher entry and `knet.png` application icon into the root of Linux universal portable tarballs (`knet-${VERSION}-linux-x64.tar.gz`, `knet-${VERSION}-linux-arm64.tar.gz`) for single-click GUI execution.
* Added `ubuntu-24.04-arm` native GitHub runner matrix target in `.github/workflows/release.yml` to build Linux ARM64 packages (`.deb`, `.rpm`, and `knet-${VERSION}-linux-arm64.tar.gz`).
* Restructured `.github/workflows/release.yml` into a two-stage pipeline (`build-packages` matrix + single `publish-release` aggregator job) generating a structured Markdown distribution table and publishing all multi-platform assets atomically with a single clean changelog.
* Verified with `./gradlew check :products:desktop:createDistributable` (236 tasks passed).

## Phase 7: Architecture Foundation and Module Ownership [COMPLETED]

Started on 2026-08-18 from the approved target architecture in `docs/target_architecture_and_implementation_plan.md`.

Completed on 2026-08-18:

* Added the stable `:core:traffic`, `:core:connectivity`, `:application`, and `:connectivity:desktop` module boundaries without moving current runtime behavior.
* Established canonical shared `HttpRequestSnapshot`, `HttpResponseSnapshot`, and `HttpExchangeSnapshot` contracts for API Studio, Traffic, Breakpoints, collections/replay, inspectors, and export.
* Kept body bytes out of shared snapshots through `BodyRef`, bounded `BodyRange`/`BodyChunk` access, and explicit capture outcomes.
* Added UI-neutral proxy runtime, paged traffic query, connectivity context, setup descriptor, and managed mechanism contracts.
* Added a `MODULE.md` responsibility contract at all 41 Gradle project roots and the central `docs/module_responsibility_index.md` index.
* Added configuration-cache-compatible `verifyArchitectureFoundation` Gradle checks for module documentation coverage and new inner-layer dependency rules. Every module `check` depends on these guardrails.
* Preserved existing production paths for later compatibility-adapter migrations; no proxy, storage, or UI runtime was switched in this phase.

Verification passed with `./gradlew check :products:desktop:createDistributable`: 258 actionable tasks, 55 executed and 203 up-to-date.

## Phase 8: Canonical Traffic Compatibility Adapters [COMPLETED]

Completed on 2026-08-18:

* Added explicit desktop compatibility mapping from current `HttpTransactionEntity`/domain values to `HttpRequestSnapshot`, `HttpResponseSnapshot`, and `HttpExchangeSnapshot` without introducing a reverse dependency into `:core:traffic`.
* Added opaque versioned legacy `BodyId` and cursor codecs; neither filesystem paths nor cursor internals cross the data adapter boundary.
* Added `BodyAccessPort`, bounded `BodyRange`/`BodyChunk` reads, and `LoadTrafficExchangeDetailsUseCase` with a one-mebibyte preview limit per request or response.
* Implemented `LegacyTrafficQueryAdapter` over the existing Room table with newest/oldest keyset paging, database-side method/status filtering, parsed-host filtering, and compact monotonic invalidation signals.
* Added schema v10 with composite time, method, and response-status traffic indexes plus a non-destructive v9-to-v10 migration.
* Migrated the Traffic inspector detail-loading path to the canonical application use case while leaving the existing live-list and proxy write paths operational.
* Added compatibility parity, paging, filtering, invalidation, bounded-range, and payload-root containment tests.
* Updated affected module ownership contracts and the central responsibility index.

Verification passed with `./gradlew check :products:desktop:createDistributable`: 258 actionable tasks, 67 executed and 191 up-to-date.

## Full Target Architecture Delivery Board

The architecture program did not stop at Phase 8: those phases established only the additive foundation and one canonical Traffic detail vertical slice. The board below records the later completed mandatory phases and explicitly gated optional product/protocol increments.

The source of truth for boundaries, invariants, future-feature isolation, and detailed exit criteria is `docs/target_architecture_and_implementation_plan.md`. This board tracks implementation status against that target.

| Target architecture phase | Repository delivery phase | Current state |
|---|---|---|
| Target 0: contracts, baselines, guardrails | Phase 7 and Phase 9 | Complete: module/dependency guardrails, ADRs, deterministic fixtures, and measured proxy/storage resource baselines are present |
| Target 1: P0 security/correctness containment | Phase 9 | Complete: listener/trust/authority/lifecycle/ordering/reference/migration/connection-policy containment and measured resource recovery gates pass |
| Target 2: stable core/application seams | Phases 7, 8, 9, and 10 | Traffic query/detail, proxy control, capture ingress, body maintenance, direct HTTP recording, and clear/session-rotation seams are present; remaining UI and breakpoint seams remain |
| Target 3: canonical session writer/store/query | Phase 10 | Complete: canonical contracts, schema v12, body store, bounded writer, indexed query, gaps, retention/recovery, finalized-orphan reconciliation, migrating reads, bounded live compatibility, active clear rotation, direct recording, restart-boundary recovery, legacy-writer removal, and the 100,000-row gate are present |
| Target 4: streaming proxy/bounded capture | Phase 11 | Complete: request/response streaming, bidirectional backpressure, bounded canonical capture, typed cancellation, compatibility gates, runtime metrics, and capacity/churn gates pass |
| Target 5: breakpoints/UI/dependency migration | Phase 12 | Complete |
| Target 6: semantic inspector architecture | Phase 13 | Complete; GraphQL and SSE supported, other capabilities truthful |
| Target 7: connectivity and portal isolation | Phase 14 | Complete for manual/PAC/Apple/ADB and the isolated setup listener |
| Target 8: pairing/mobile-ready ingress | Phase 15 | Complete |
| Target 9: companion/relay product | Phase 16 | Optional product scope not activated; foundations complete and capability unavailable |
| Target 10: protocol increments | Phase 17 | SSE increment complete; WebSocket/H2/gRPC/H3 remain independent unavailable increments |
| Target 11: performance/production gates | Phase 18 | Standard gate complete; extended-duration soak is a release operation |

## Phase 9: Baselines, Security, Correctness, and Application Control [COMPLETED]

This is the next mandatory phase. It completes the unimplemented parts of target architecture Phases 0–2 before deeper capture and proxy replacement.

Started on 2026-08-18.

Completed in the first containment slice:

* Added focused accepted ADRs for dependency direction, traffic/body ownership, listener access, connectivity/companion isolation, and protocol capability truth.
* Made loopback and strict upstream TLS the default production path; application startup rejects unauthenticated LAN/wildcard exposure before binding.
* Added strict authority parsing for CONNECT/Host and prevented upstream hosts or unknown local routes from invoking the setup portal.
* Made proxy start atomic and retryable, made stop await listener/event-loop termination, and registered process-owned proxy/writer shutdown before database close.
* Enforced configured TCP-connect, TLS-handshake, read-idle, write-idle, graceful-shutdown, total/per-client downstream, and total upstream limits inside Netty.
* Fixed breakpoint request/response reference and promise ownership across forward, drop, timeout, disconnect, error, and handler-removal paths.
* Preserved HTTP/1 response ordering with a bounded compatibility queue until the streaming transport replaces aggregation.
* Replaced racing callback persistence with one bounded ordered compatibility writer; late pending callbacks cannot regress complete rows and orphan responses cannot fabricate requests or bodies.
* Removed destructive Room fallback, supplied missing historical migrations, and proved v1-to-current-schema data preservation.
* Secured certificate and payload directories/files, made writes atomic and filenames opaque/contained, and made clear-traffic await both file and database removal.
* Routed the production Traffic start/stop/state path through `:application` use cases and `ProxyRuntimePort`.
* Added executable resource gates for 24 concurrent loopback clients, an 8 MiB aggregated response, slow upstream peers, abrupt downstream disconnects, ten listener lifecycle repetitions, heap/direct-memory peaks, file-descriptor recovery, and canonical metadata storage growth.
* Added a 100,000-row schema-v11 fixture proving database-side host/method/status filtering returns a bounded 100-item keyset page without full-session materialization.

Measured on 2026-08-18 using a Macmini9,1 (Apple M1, 8 GiB), macOS Darwin 24.6.0, and OpenJDK 21.0.8:

* 24 clients × 256 KiB: 31 ms measured transfer window, 202,950,193 bytes/second, 10,336,088-byte peak heap delta, zero-byte measured pooled-direct delta, 118 peak descriptor delta, and descriptors recovered within the declared allowance.
* 8 MiB response: 29 ms, 8,725,912-byte peak heap delta, and 16,777,216-byte pooled-direct delta.
* Six slow upstream peers plus 24 abrupt disconnects: 1,583 ms with descriptor recovery; ten start/stop repetitions: 2,145 ms with descriptor recovery.
* 100,000 canonical exchanges: fixture creation 1,201 ms, filtered 100-row page 2 ms, and 46,242,224 bytes across SQLite database/WAL/sidecars.

Verification checkpoint on 2026-08-18: `./gradlew check :products:desktop:createDistributable` passed after the in-progress Phase 10 additions with 258 actionable tasks (59 executed, 199 up-to-date). The desktop distribution was produced successfully.

At the Phase 9 checkpoint the existing capture path remained authoritative. Phase 10 subsequently switched proxy callbacks to one exclusively selected canonical writer while preserving legacy rows through read compatibility.

Exit criteria: the target Phase 0–2 security, lifecycle, ordering, leak, and dependency tests pass; fresh runtime exposure is safe by default; one application-owned proxy-control path is in production use.

## Phase 10: Canonical Session Writer, Body Store, and Recovery [COMPLETED]

Started on 2026-08-18. Implemented additively with production registration delayed until its safety gates passed:

* Added portable monotonic connection/exchange/body/gap capture events and application `CaptureIngressPort`/`BodyStorePort` contracts.
* Added pre-allocation body-byte reservations, a bounded metadata channel, explicit degraded health, compact durable saturation gaps, and one ordered writer per test session.
* Added `FileBodyStore` with opaque hashed paths, owner-only permissions, bounded writes, SHA-256 digesting, explicit truncation, fsync/atomic finalize, range reads, delete, abandoned-temp reconciliation, and bounded opaque finalized-object inventory.
* Added non-destructive schema v11 for sessions, connections, exchanges, body objects, duplex messages, inspection annotations, capture gaps, and deletion outbox with target query indexes. Schema v12 adds nullable/backfillable opaque object keys for safe finalized-orphan reconciliation.
* Added monotonic conditional DAO transitions so late response/body events cannot change a terminal exchange.
* Added a canonical indexed query adapter with database-side host/method/status filtering, keyset cursors, page-batched body metadata, and bounded body access.
* Added deletion-outbox reconciliation and isolated tests for writer ordering, body ownership/truncation, saturation gaps, query mapping, and file/database convergence.
* Added crash-safe closed-session clear: one Room transaction queues body deletions before removing metadata, followed by bounded retryable file reconciliation.
* Added a temporary `LegacyCallbackCaptureAdapter` that maps full request/response callbacks into canonical heads, bounded reservation-before-copy body chunks, explicit orphan/saturation gaps, and monotonic terminal events.
* Cut production composition directly to the canonical writer; integration tests prove current-schema metadata and opaque bodies are written while the legacy table and payload directory remain untouched.
* Added the 100,000-row indexed query/storage regression gate described in Phase 9.
* Added oldest-first global retention with independently enforced terminal-session count and stored-byte limits, bounded eviction passes, and deletion-outbox convergence. Active sessions are never evicted.
* Added bounded startup recovery for interrupted sessions, connections, and exchanges; abandoned temporary objects; pending deletions; and finalized-body metadata whose object is missing.
* Added storage-key backfill plus bounded finalized-object inventory so a body file that survives a crash before metadata attachment is deleted without exposing paths or loading the entire object store.
* Added a migration-period query adapter that keeps legacy schema-v10 rows readable while routing arbitrary schema-v11 sessions, canonical-first exchange lookup, body identifiers, and change generations through the canonical store.
* Migrated the compatibility live repository to a bounded 1,000-row metadata merge of legacy traffic and the newest canonical session. Direct lookup and lazy body loading prefer canonical records, export inherits those repository paths, and clear converges legacy data plus closed canonical sessions.
* Added application-owned traffic clear orchestration. A running proxy swaps to a fresh canonical session first, closes active channels, terminalizes unfinished old exchanges, and only then deletes closed history; an integration test proves capture continues in the replacement session.
* Added `TrafficRecordPort`/`RecordHttpExchangeUseCase` with shared `RequestHead`/`ResponseHead` metadata and defensive-copy body ownership. Direct API Studio executions now use this boundary and reuse the active proxy session or one lifecycle-owned direct session without duplicate proxied records.
* Added direct-to-proxy session handoff tests proving exactly one canonical session remains active, and a reopened-database restart-boundary integration proving interrupted session/connection/exchange ownership is recovered before a new writer admits capture.
* Removed `CaptureWriterSelection`, the proxy repository's pending/replace legacy persistence queue, its payload writer, and the dormant `recordTransaction(HttpTransaction)` feature path. Legacy schema rows remain read-only migration input for query/export/clear compatibility.

Final Phase 10 verification on 2026-08-18 passed `git diff --check && ./gradlew check :products:desktop:createDistributable` with 258 actionable tasks (119 executed, 139 up-to-date). The desktop distributable was produced successfully after direct recording, restart recovery, and legacy-writer removal.

Exit criteria: one monotonic writer owns every new session; saturation and gaps are explicit; clear/retention/recovery converge; 100,000-row paging stays bounded.

## Phase 11: Streaming Proxy and Bounded Capture [COMPLETED]

Completed in the first Phase 11 slice on 2026-08-18:

* Removed default upstream `HttpObjectAggregator`; response heads/content/trailers now flow incrementally through the HTTP/1 transport.
* Coupled upstream reads to completion of downstream writes with manual Netty read advancement, bounding queued data for slow clients.
* Preserved response-breakpoint behavior behind an explicit composition predicate: only an enabled response rule installs the bounded compatibility aggregator, while the proxy engine remains independent from rule types.
* Added an engine-owned, persistence-neutral streaming capture sink with real connection/exchange identities and reservation-before-copy body allocations.
* Cut desktop production capture directly to the canonical ingress/writer and removed `LegacyCallbackCaptureAdapter`; direct API Studio records share the same streaming session owner.
* Added explicit streaming-body completion so canonical `BodyRef` metadata preserves full observed bytes and a typed complete/truncated outcome after copying stops.
* Removed process-global pipeline initializers; each proxy runtime now owns an immutable extension list.
* Replaced clear-all certificate caching with single-flight asynchronous generation, LRU/idle/weight bounds, and a runtime-owned bounded crypto executor.
* Removed the unused competing upstream connection-pool implementation while retaining one-shot ownership as the single current path.
* Added a 500 MiB generated-response qualification that retains no test payload, uses the new capture sink, and asserts bounded heap/direct memory plus a 10 MiB capture limit.
* Replaced default downstream aggregation with incremental request-head/content forwarding and explicit downstream-read-to-upstream-write/writability coupling.
* Kept existing breakpoint behavior behind bounded request/response aggregation predicates supplied by desktop composition; portal routing now consumes request heads without forcing aggregation.
* Added provisional `100 Continue`, request/response trailer, downstream half-close, early-response, and typed source-cancellation handling.
* Added constant-time event-loop lag metrics and removed the proxy engine's final `ProxyTrafficListener` callback surface, leaving `ProxyCaptureSink` as its only capture boundary.
* Added a 128 MiB generated-upload qualification, the declared 100 concurrent × 10 MiB generated-response workload, and a configurable connection-churn soak gate.

Measured qualification: 524,288,000 bytes forwarded in 746 ms, 10,485,760 bytes admitted to capture, 27,557,120-byte peak heap delta, and zero-byte measured pooled-direct delta.

Additional measured gates on 2026-08-18:

* 128 MiB upload: 593 ms, 10 MiB captured, zero-byte measured heap delta, and 4 MiB pooled-direct delta.
* 100 concurrent × 10 MiB responses: 1,704 ms, 59,391,344-byte peak heap delta, and 67,108,864-byte pooled-direct delta.
* 1,000 connection-churn requests across 20 workers: 760 ms with descriptors converging to 142 (inside the declared allowance); `knet.proxy.soak.cycles` scales the same gate for release soak runs.

Current Phase 11 checkpoint on 2026-08-18 passed `git diff --check && ./gradlew check :products:desktop:createDistributable` with 258 actionable tasks (57 executed, 201 up-to-date). The desktop distributable was produced successfully after the response-breakpoint compatibility gate was added.

Exit criteria: a 500 MiB response forwards with bounded memory; capture truncation never breaks forwarding; ordering, slow-peer, disconnect, keep-alive, direct-memory, and soak tests pass.

## Phase 12: Breakpoints, Paged Traffic UI, Export, and Dependency Inversion [COMPLETED]

* Add application-owned breakpoint coordination with compiled rule snapshots and bounded pause time, bytes, and connection count.
* Migrate the Traffic list from `Flow<List<HttpTransaction>>` to canonical keyset pages and generation-based refresh.
* Split list/detail/runtime status ownership and keep only bounded detail previews in UI state.
* Migrate Traffic-to-API-Studio, replay, collections, breakpoints, and export to the shared `HttpRequestSnapshot`/`HttpResponseSnapshot`/`HttpExchangeSnapshot` contracts and bounded `BodyAccessPort`.
* Move presentation models out of core and migrate every desktop ViewModel away from concrete engines, storage, and desktop adapters.
* Consolidate `:engine:traffic` rewrite behavior and retire superseded session/simulator paths only after their callers are gone.

Completed on 2026-08-18: Traffic uses canonical keyset pages with a 1,000-row retained window and bounded detail previews; 100,000-row storage and UI tests pass. `PrepareTrafficRequestUseCase` supplies the common `HttpRequestSnapshot` to replay/export/API Studio and rejects silent body truncation. `BreakpointCoordinator` owns bounded rules, pauses, byte/connection budgets, decisions, patches, cancellation, and deadlines. Presentation-only traffic models moved to UI; legacy live presentation/repositories and the dormant `:engine:traffic` module were removed. Certificate, settings, traffic, and API Studio runtime calls now cross application ports. An executable UI isolation rule prevents concrete runtime/storage imports; the pure formatter library is the narrow documented exception.

Exit criteria: shared canonical HTTP models serve all applicable features; no desktop UI module depends on concrete runtime/storage; Traffic memory follows page and preview limits at 100,000 rows.

## Phase 13: Asynchronous Semantic Inspector Host [COMPLETED]

* Add the budgeted asynchronous inspector scheduler, bounded body access, versioned annotations, persistence/query adapters, and generic UI renderer.
* Adapt GraphQL to the inspector contract without putting parsing on forwarding or capture paths.
* Classify unsupported/dormant WebSocket and gRPC code honestly until their transport prerequisites are delivered.
* Publish a runtime capability catalog backed by production end-to-end tests.

Completed on 2026-08-18: a bounded concurrency/body/deadline scheduler runs after capture, persists generic versioned annotations, isolates cancellation/timeout/failure, and renders generic results in Traffic detail. GraphQL and SSE are registered inspectors with production-path end-to-end tests. Dormant WebSocket/protobuf transport/parser stubs were removed, and unsupported capabilities remain `UNAVAILABLE`.

Exit criteria: inspector failure or timeout cannot affect forwarding/capture; annotations are rerunnable; every Supported claim has an end-to-end test.

## Phase 14: Connectivity Mechanisms and Portal Isolation [COMPLETED]

* Implement the desktop network snapshot monitor and independent manual, PAC, Apple profile, and ADB packages behind `:core:connectivity`/`:application` contracts.
* Add descriptor-driven setup UI, versioned artifact caching, deterministic PAC generation, and golden tests.
* Move portal delivery to a strict-authority separate adapter/listener and remove portal routing from the proxy pipeline.
* Reconcile IPv4, IPv6, Wi-Fi, Ethernet, hotspot, and VPN network changes without unnecessary proxy restarts.

Completed on 2026-08-18: manual proxy, deterministic PAC, deterministic Apple profile, ADB reverse, versioned artifacts, and the IPv4/IPv6/default-route/VPN network monitor register independently. Setup delivery runs on a separate strict-authority loopback listener. The obsolete `:engine:portal` proxy-handler module was removed. Network transitions republish connectivity state without restarting the proxy or rotating capture.

Exit criteria: mechanisms register additively; arbitrary upstream setup-like paths are forwarded normally; network transitions update descriptors/health independently.

## Phase 15: Pairing and Mobile-Ready Authenticated Ingress [COMPLETED]

* Expand portable pairing invitation, handshake, device, credential, revocation, expiry, replay, and scope state.
* Add application pairing coordination and secure trusted-device storage.
* Add loopback-only internal gateway binding, typed ingress identity, device-scoped access policy, and QR/deep-link onboarding descriptors.
* Prove an authenticated standard proxy byte bridge under backpressure and network reconnection without restarting the proxy or capture session.

Completed on 2026-08-18: one-shot expiring invitations, Ed25519 device proof, scoped credentials, replay defense, revocation, and AES-256-GCM owner-only trusted-device storage are implemented. A bounded loopback standard-proxy gateway authenticates and strips local credentials, bridges under stream backpressure, attributes canonical traffic through neutral ingress identity, rejects admission overflow, and terminates active sockets on revocation. QR/deep-link onboarding contains the one-time invitation material without changing proxy, traffic, PAC, or manual-proxy contracts.

Exit criteria: a test device can pair, bridge a scoped proxy stream, appear with the correct ingress identity, and be revoked without changing ordinary PAC/manual/proxy/traffic behavior.

## Phase 16: Mobile Companion and Relay Product [OPTIONAL PRODUCT SCOPE — NOT ACTIVATED]

* Add Android/iOS companion applications and platform VPN adapters only after product scope is approved.
* Add desktop companion connectivity plus direct tunnel transport; add an end-to-end encrypted relay carrier only if required.
* Reuse the authenticated internal proxy binding, canonical traffic/session architecture, pairing identity, and application queries.
* Do not modify the proxy forwarding contract, canonical traffic/store contracts, or existing PAC/manual/Apple implementations.

Exit criteria: direct and relay paths pass the same proxy conformance suite; revocation and network transition behavior remain bounded and deterministic.

No Android/iOS application, VPN packet adapter, direct tunnel, or relay carrier is added without a real platform target and product/security requirements. The shared traffic model, pairing identity, authenticated gateway, application ports, and connectivity registry are complete, so activating this phase is additive. Runtime capability status remains `UNAVAILABLE` for Mobile Companion, VPN, and Relay.

## Phase 17: Protocol Transports and Inspectors [OPTIONAL INCREMENTS — SSE COMPLETED]

Deliver independently after Phases 11 and 13 establish the transport and inspector seams:

1. WebSocket upgrade/frame transport and duplex storage/UI.
2. SSE streaming inspector.
3. HTTP/2 ALPN and multiplexed stream transport.
4. gRPC inspector over supported HTTP/2.
5. HTTP/3/QUIC transport after an explicit library/platform decision.

Each increment must satisfy conformance, failure, backpressure, body-limit, lifecycle, and long-run tests before being marked Supported.

The SSE semantic-inspection increment is complete with an end-to-end test. GraphQL was completed in Phase 13. WebSocket transport, HTTP/2, gRPC, and HTTP/3 are deliberately not marked complete or Supported; each remains an independent future increment requiring its real transport and conformance suite.

## Phase 18: Performance, Soak, Security, and Release Gates [STANDARD GATE COMPLETED]

Performance and security tests are added during every preceding phase; this phase closes the overall capacity program.

* Enforce repeatable 10/100/1,000-connection, churn, 500 MiB, concurrent-body, slow-peer/disk, and capture-saturation workloads.
* Enforce 1,000/10,000/100,000-row storage and UI workloads.
* Run multi-hour heap, direct-memory, thread, file-descriptor, database, and body-directory soak tests.
* Cover disk-full, corrupt body, schema initialization/reset policy, network transitions, pairing/tunnel failure, and shutdown deadlines.
* Publish the supported capacity envelope and align security/protocol capability claims with test evidence.

Exit criteria: no unbounded production path remains and resource use stabilizes under configured retention.

Completed engineering gate on 2026-08-18: `phase18ReleaseGate` aggregates architecture checks, every included module's check task, and desktop packaging. Its final run passed all 254 actionable tasks and produced the desktop distributable. Tests cover 500 MiB response streaming, 128 MiB upload, 100 concurrent 10 MiB responses, 1,000-connection default churn, slow peers, admission saturation, lifecycle/descriptor recovery, 100,000-row indexed paging, the 1,000-row UI window, retention, crash recovery, schema-v13 persistence, disk-exhaustion degradation, corrupt-body integrity marking, network transitions, pairing expiry/replay/scope/revocation, active gateway revocation, and shutdown ordering. Earlier development schemas now reset by policy. The extended release soak remains a deliberately parameterized release operation (`-Dknet.proxy.soak.cycles=...`); no claim is made that a multi-hour run occurred in this implementation session.

Post-phase architecture cleanup on 2026-08-18 moved every Koin binding declaration into `:products:desktop`, organized by product feature. `:data:desktop` and UI feature modules no longer define composition modules; they expose adapters, use cases, ViewModels, and screens for the product root to assemble. `verifyCompositionOwnership` now prevents binding declarations from drifting back into reusable modules.

Post-phase development cleanup on 2026-08-18 removed backward-compatibility code that was no longer justified for an unreleased product. The old transaction table and schema migrations, old payload-path reader, certificate pipe-format importer, duplicate domain `HttpRequest`/`HttpResponse`/`HttpTransaction`/`HttpTimings`, callback traffic listener/internal correlation header, old proxy repository API, interception-session API, unused HTTP executor facade, and dormant session/export implementations are gone. Schema v13 is canonical-only and older local databases intentionally reset. Proxy, breakpoint, Traffic, API Studio, and persistence now share `:core:traffic` snapshots and application ports directly. The cleanup was verified by `phase18ReleaseGate`: all 254 actionable tasks passed and the desktop distributable was produced.

## Phase 19: Kotlin-First Source and Platform Boundaries [COMPLETED]

Started on 2026-08-18 after the Kotlin/JVM usage audit. This phase keeps JVM APIs where Netty,
JCA/JCE, Room, sockets, filesystem permissions, desktop integration, or blocking transport adapters
genuinely require them while removing avoidable Java usage and false portability from shared source
sets.

Completed on 2026-08-18:

* Replaced the authenticated gateway's `runBlocking`, latches, and private executors with one owned coroutine scope, suspend authentication, a bounded elastic IO view, active-socket cancellation, and structured duplex copying.
* Moved clipboard operations to an asynchronous Compose `Clipboard` foundation API. The current Compose Desktop transfer-object adaptation is confined to one `:ui:core` `jvmMain` file; feature modules contain no AWT clipboard code.
* Isolated common/JVM boundaries for HTTP exception classification, proxy-client cache synchronization, pointer cursors, and editor pointer input. Desktop host-platform detection moved out of the domain source set.
* Replaced Java UUID, Base64, standard charset, URL/form decoding, deque, linked-map imports, wall-clock duration measurement, and timestamp-derived identity generation with Kotlin APIs where a portable equivalent exists.
* Replaced direct settings AWT/filesystem access with an injected feature-owned platform-action contract implemented only by the desktop product root.
* Replaced synchronized certificate collection wrappers with an explicit manager state lock covering compound mutations, snapshots, rule resolution, and persistence ordering.
* Added `verifyKotlinFirstSources` to the architecture and release gates. It rejects JVM references in `commonMain`, production `runBlocking`, Java UUID, avoidable Java utility imports, and AWT clipboard use outside the approved platform adapter.
* Updated affected module ownership documents and added regression tests for Unicode form decoding, settings platform delegation, and concurrent certificate-rule snapshots.

Required JVM APIs remain intentionally isolated where Netty, blocking sockets, JCA/JCE, Room and
filesystem operations, desktop shell/trust-store integration, JSR-223, compression streams, or the
Compose Desktop clipboard entry implementation require them. These are implementation details, not
portable contracts.

Final verification on 2026-08-18 passed `git diff --check`, targeted tests (139 actionable tasks),
and `phase18ReleaseGate` (255 actionable tasks). All included module checks passed and the desktop
distributable was produced successfully.

Exit criteria: all designated portable source sets are JVM-import-free, required JVM integrations stay
isolated in implementation modules, no production `runBlocking` or feature-level AWT clipboard path
remains, and `phase18ReleaseGate` passes with the Kotlin-first verification enabled.

## Phase 20: Kotlin Idioms and Visibility Consistency [COMPLETED]

Started on 2026-08-18 to close IDE/compiler hygiene after the Kotlin-first boundary work.

Completed on 2026-08-18:

* Retained explicit `public` visibility in `:application`, `:core:traffic`, `:core:connectivity`,
  and `:connectivity:desktop`, where `explicitApi()` makes the modifier part of the enforced API contract.
* Removed 1,021 redundant `public` modifiers from implementation, persistence, product,
  presentation, and test sources without changing declaration visibility or behavior. The generated
  application-metadata template now follows the same convention.
* Replaced deprecated Compose preview, classpath painter-resource, and non-auto-mirrored directional
  icon APIs. The shared application logo is now owned by `:ui:core` and loaded through Compose
  Multiplatform Resources by both desktop bootstrap and navigation UI.
* Removed every `java.time` import and qualified reference from repository Kotlin source.
  `kotlin.time.Clock` and `kotlin.time.Instant` now own wall-clock acquisition, instant conversion,
  and duration arithmetic. `kotlinx-datetime` 0.8.0 supplies the multiplatform local calendar and
  system-time-zone conversion that deliberately does not belong to the Kotlin standard library.
* Added the shared `KNetDateTime` presentation formatter in `:ui:core` and deterministic UTC tests
  for ISO date keys, local time rendering, compact date rendering, and millisecond padding.
* Extended `verifyKotlinFirstSources` to reject redundant visibility outside explicit-API modules,
  deprecated Compose resource/preview APIs, non-auto-mirrored directional icons, legacy date
  formatting, and any `java.time` use in production or test Kotlin sources.
* Opted the two intentional `expect`/`actual` class boundaries into Kotlin's documented compiler
  flag so clean compilation does not rely on per-source warning suppression.

Required Java/JVM APIs remain in JVM implementation source sets for Netty, sockets, JCA/JCE and
Bouncy Castle certificates, filesystem permissions and atomic replacement, Room/SQLite, desktop
shell/AWT adapters, JavaScript engines, and compression streams. They have no equivalent Kotlin
standard-library implementation that would improve portability or correctness.

Final verification passed `git diff --check`, the extended `verifyKotlinFirstSources` policy,
targeted warning-free affected-module compilations, every included module check and test, desktop
runtime-image creation, and `phase18ReleaseGate` (258 actionable tasks). The desktop distributable
was produced successfully.

Exit criteria: redundant visibility warnings are removed outside explicit-API modules, explicit-API
contracts still compile, Kotlin-first guards pass, deprecated production usages found by the audit are
resolved or documented as platform requirements, and `phase18ReleaseGate` succeeds.

## Phase 21: Cross-Feature Model Ownership and SSOT Convergence [COMPLETED]

Started on 2026-08-18 after the repository-wide duplicate-model audit. This phase removes semantic
translation chains while retaining boundary types that add real transport, persistence, editor, or
rendering behavior.

Implemented:

* Replaced the HTTP client's body-kind/body/form-parameter tuple with one `OutboundRequestBody`
  hierarchy; `ApiRequestAuth`, `ExecutionResult`, and canonical `ExchangeTimings` now cross the
  outbound execution boundary without client-owned duplicates.
* Removed duplicate response/timing specifications. Traffic, API Studio response rendering, and the
  reusable HTTP panel consume canonical `ResponseHead` and `ExchangeTimings`; UI projections add only
  presentation lifecycle, logs, assertions, selection, or formatting.
* Added the evidence-driven leaf `:core:scripting` module and moved shared language, phase, snippet,
  and immutable assertion values there. Collections, application ports, UI editors, and the script
  engine consume those contracts directly.
* Replaced application/UI/interceptor breakpoint copies with the single `BreakpointRule` and
  `BreakpointPhase` in `:core:domain`. Method matching remains typed as canonical `HttpMethod`, and
  protocol criteria now survive repository-to-coordinator flow.
* Consolidated content encoding in `:core:traffic`; capture, storage, proxy, and body decoders use the
  same extensible value.
* Made `StructuredPayloadState.GraphQL` the GraphQL data owner. `GraphQlState` now composes that
  payload and adds only active-tab UI state.
* Removed duplicate Traffic inspector sub-tabs, code-editor context menu items, application menu
  items, unused explorer state, and duplicate API Studio network-error details.
* Removed certificate UI copies. `CertificateAuthoritySummary`, `ClientCertificateSummary`, typed
  `ClientCertificateFormat`, and `MtlsRuleSpec` cross `CertificateManagementPort` directly.
* Extended dependency-boundary verification for `:core:scripting`, `:core:domain`, and the
  application module, and updated affected module responsibility contracts.

Intentionally retained:

* Room entities, Netty/Ktor transport objects, immutable engine observations, mutable editor drafts,
  script sandbox host objects, and UI projections where they add behavior rather than duplicate a
  semantic contract.
* `ResponseInspectorState` for execution/loading/error/assertion presentation, and `GraphQlState` for
  active editor-tab state; neither is accepted by core traffic, persistence, or proxy APIs.

Exit criteria: duplicate-declaration audit finds one owner for every audited semantic cluster;
architecture/Kotlin-first checks pass; all module tests and the packaged desktop release gate pass.

Final verification on 2026-08-18 passed `git diff --check`, the residual duplicate-declaration scan,
targeted migrated-module tests, `verifyArchitectureFoundation`, and `phase18ReleaseGate` (261
actionable tasks). Every included module check passed and the desktop distributable was produced.

## Phase 22: Primary Wi-Fi Device Connectivity [IN PROGRESS — BACKEND FOUNDATION COMPLETE]

Wi-Fi sharing will become the primary stock-phone connection path while the production proxy remains
loopback-only. The approved direction is an explicitly activated, exact-interface LAN gateway in
`:connectivity:desktop` that admits only desktop-approved client addresses and forwards attributed byte
streams to the unchanged internal proxy. It will not introduce LAN behavior into `:engine:proxy`, traffic
storage, body handling, PAC generation, ADB, breakpoints, or inspectors.

The delivery includes truthful endpoint availability, session-bound QR onboarding, a token-protected LAN
setup endpoint, CA/manual/PAC/Apple setup behavior, source approval and immediate revocation, network-change
invalidation, first-class `Connect Device` UI, real Android/iPhone conformance, and bounded security/resource
gates. Wi-Fi sharing remains off after process start and requires explicit activation for each network
session. Stock-phone approval is explicitly network-bound rather than represented as per-connection
cryptographic authentication; future companion/VPN connectivity remains the strong-authentication option
for untrusted networks and apps that ignore system proxy settings.

Detailed architecture, phases, security limitations, module changes, and exit criteria are defined in
`docs/wifi_connectivity_implementation_plan.md`.

Backend checkpoint completed on 2026-08-18:

* Added canonical Wi-Fi sharing models, the application port/use cases, and feature-grouped desktop DI.
* Added the opt-in exact-interface LAN gateway, approved-source registry, expiring source-bound invitations,
  tokenized setup portal, CA/PAC delivery, ingress attribution, quotas, revocation, and metrics.
* Added network/proxy invalidation and atomic listener-start rollback without restarting the loopback proxy
  or clearing captured traffic.
* Corrected setup-provider availability so a loopback address is never advertised as phone-reachable.
* Passed the Kotlin-first/architecture foundation gate and focused application, connectivity, traffic, and
  persistence tests.

The `Connect Device` UI, Apple profile conformance, real Android/iPhone testing, IPv6 validation, and the
final packaged capacity/security gate remain pending. No Compose code was changed in this backend checkpoint.
