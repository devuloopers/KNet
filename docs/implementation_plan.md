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
* Added application-owned traffic clear orchestration. A running proxy swaps a stable capture sink to a fresh canonical session, terminalizes unfinished old capture state without closing client transport channels, and only then deletes closed traffic metadata and body files; focused tests prove an existing connection captures its next exchange in the replacement session.
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

Completed on 2026-08-18: one-shot expiring invitations, Ed25519 device proof, scoped credentials, replay defense, revocation, and durable trusted-device storage are implemented. The initial standalone encrypted-file adapter was consolidated into the schema-v15 Room registered-device source of truth during Phase 22; only public keys and one-way credential/invitation digests are persisted. A bounded loopback standard-proxy gateway authenticates and strips local credentials, bridges under stream backpressure, attributes canonical traffic through neutral ingress identity, rejects admission overflow, and terminates active sockets on revocation. QR/deep-link onboarding contains the one-time invitation material without changing proxy, traffic, PAC, or manual-proxy contracts.

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

## Phase 22: Primary Wi-Fi Device Connectivity [IN PROGRESS — OPEN WI-FI SETUP IMPLEMENTED, DEVICE GATES PENDING]

Wi-Fi sharing will become the primary stock-phone connection path while the production proxy remains
loopback-only. The approved direction is an automatically managed, exact-interface LAN gateway in
`:connectivity:desktop` that accepts any reachable local-network client and forwards attributed byte streams
to the unchanged internal proxy. It will not introduce LAN behavior into `:engine:proxy`, traffic storage,
body handling, PAC generation, ADB, breakpoints, or inspectors.

The revised delivery includes truthful endpoint availability, a stable QR setup URL, an open LAN setup page,
Android CA and Apple profile downloads, CA/manual/PAC setup behavior, automatic proxy-lifecycle activation,
network-change recovery, a single-card `Connect Device` UI, real Android/iPhone conformance, and bounded
resource gates. No invitation, confirmation, or source approval is required for manual Wi-Fi proxy clients.
Future companion/VPN connectivity remains the authenticated option for remote networks and apps that ignore
system proxy settings.

Detailed architecture, phases, security limitations, module changes, and exit criteria are defined in
`docs/wifi_connectivity_implementation_plan.md`.

Superseded approval prototype checkpoint completed on 2026-08-18 and removed on 2026-08-19:

* Added canonical Wi-Fi sharing models, the application port/use cases, and feature-grouped desktop DI.
* The prototype proved exact-interface LAN bridging, source attribution, quotas, and listener rollback. Its
  approved-source registry, invitations, token routes, and revocation controls were later removed when the
  product requirement changed to open local-client admission.
* Added network/proxy invalidation and atomic listener-start rollback without restarting the loopback proxy
  or clearing captured traffic.
* Corrected setup-provider availability so a loopback address is never advertised as phone-reachable.
* The isolated `:ui:desktop:connectivity` module and `Connect Device` navigation were retained; the prototype
  device-management presentation was replaced by the focused single-card setup flow.
* Added feature-grouped desktop composition bindings and focused UI state/QR tests.
* Passed the Kotlin-first/architecture foundation gate and focused application, connectivity, traffic, and
  persistence tests.

Companion identity persistence checkpoint completed on 2026-08-19. Room schema 15 stores registered companion
identities, trusted-device public keys and credential digests, and one-time pairing invitation digests; its
explicit 14-to-15 auto-migration preserves existing traffic and API Studio records. The temporary association
between those identities and manual Wi-Fi sources was removed. Room identity now remains exclusively behind
the authenticated pairing/companion boundary and is not consulted by the open Wi-Fi gateway.

Open Wi-Fi setup UX checkpoint completed on 2026-08-19. The Connect Device screen was reduced to one
`Wi-Fi Proxy Setup` card whose setup drawer owns the QR code and manual Android/iPhone instructions.
The exact-interface setup listener now uses one stable setup URL and a
resource-backed responsive HTML page with Android DER certificate and Apple configuration-profile downloads.
The obsolete manual Wi-Fi invitation, approval, registration, and known-device presentation were removed;
Room pairing identity remains reserved for the future authenticated companion path. Starting/stopping the
proxy now automatically starts/stops the LAN gateway, and any reachable local source is admitted under global
and per-source connection quotas.

Automated verification on 2026-08-19 passed the focused core-connectivity, application, desktop-connectivity,
desktop-connectivity UI, desktop product, desktop-app, and architecture-foundation test/build gates. The
resource-backed setup page, Android certificate route, Apple profile route, open-client forwarding, automatic
proxy stop/restart recovery, and stale approval-symbol scan were verified without launching the desktop app.

Real Apple profile installation, Android/iPhone testing, IPv6 validation, and the final packaged
capacity/security gate remain pending. These checks intentionally do not change the stable proxy, traffic,
or connectivity application boundaries.

## Phase 23: Traffic Capture Sequence Presentation [COMPLETED]

Completed on 2026-08-19: Traffic remains sorted newest-first, while its visible sequence column now numbers
the oldest retained interception as `1` and the newest interception with the highest number. Each newly
observed interception appears at the top with the next number. The presentation property was renamed from
the ambiguous `id` to `sequenceNumber`, and Traffic-to-API-Studio forwarding now uses the canonical exchange
identifier rather than the visible sequence number.

Verification passed `:ui:desktop:traffic:jvmTest`, `verifyArchitectureFoundation`, and `git diff --check`
without launching the desktop app.

## Phase 24: Shared Desktop Side Drawer [COMPLETED]

Started on 2026-08-19: extract the right-edge animated drawer shell from the breakpoint feature into
`:ui:core`, migrate Live Intercept and Wi-Fi Proxy Setup to that shared primitive, and place connectivity
method cards in a top-left wrapping grid. Feature state and content remain owned by their feature modules;
no global drawer ViewModel or business-state coordinator is introduced.

Completed on 2026-08-19: `KNetSideDrawer` now owns non-modal right-edge placement, responsive standard and
expanded widths, slide animation, surface, and border. Live Intercept retains its breakpoint-specific queue
and editor behavior in `:ui:desktop:breakpointManager`; Wi-Fi retains its QR, endpoint, instructions, and
proxy actions in `:ui:desktop:connectivity`. The former Wi-Fi `Dialog` was removed, and the Wi-Fi card is the
first top-left item in a wrapping connectivity-method grid ready for additive cards.

Verification passed the `:ui:core`, breakpoint-manager, connectivity, desktop-app, and desktop-product test
suites plus `verifyArchitectureFoundation` (158 actionable tasks) and `git diff --check`, without launching
the desktop app.

## Phase 25: Dynamic Breakpoint Pipeline Activation [COMPLETED]

Started on 2026-08-19 after identifying that request aggregation and the streaming/full proxy-handler choice
were frozen when a downstream TCP connection was accepted. Rules added, enabled, disabled, or restored after
that point could update the application coordinator while existing streaming connections continued bypassing
the full-message breakpoint adapter. This phase will add a bounded runtime refresh boundary for breakpoint
requirement changes, preserve streaming when no breakpoint requires aggregation, and add regression coverage
for rule activation after a client connection already exists. The desktop application will not be launched
during implementation verification.

Completed on 2026-08-19: `ProxyRuntimeRepository` now reduces application breakpoint state to distinct
request/response aggregation requirements and observes that state for its process lifetime. A requirement
change closes only active downstream child connections through the neutral engine boundary; it does not stop
the listener, rotate or clear traffic, replace the canonical capture session, or introduce rule knowledge
into `:engine:proxy`. Reconnected clients select the current streaming or bounded full-message pipeline.
Repository shutdown cancels the observer before stopping Netty, and repeated start/stop remains supported
until terminal close.

Two real loopback regression tests start with an already-connected streaming client, enable either a request
or response breakpoint, verify the stale connection is refreshed, reconnect, and prove the corresponding
phase is published as a pending application breakpoint before forwarding resumes. Focused and wider
verification passed `:application:test`, `:engine:proxy:test`, `:engine:interceptor:test`,
`:data:desktop:jvmTest`, `:products:desktop:test`, `verifyArchitectureFoundation`, and `git diff --check`
(149 actionable tasks in the wider gate). The desktop application was not launched.

Superseded on 2026-08-20 by Phase 75: rule changes are now evaluated per message through bounded selective
aggregation. They no longer close established client connections, and the connection-refresh behavior above is
retained only as historical migration context.

## Phase 26: Extensible Protocol Breakpoint Matching [COMPLETED]

Started on 2026-08-19 after the live breakpoint path was found to evaluate only phase, method, and URL while
the domain, Room mapper, and desktop editor each contained separate hardcoded GraphQL/gRPC/WebSocket branches.
This phase replaces those closed branches with an application-owned protocol breakpoint extension registry.
Each extension will own its typed criteria, bounded inspection, compiled matching, and persistence codec;
the coordinator, proxy engine, canonical HTTP request/response models, and Room schema will remain
protocol-neutral. GraphQL will become the first real extension, with its compact request observation retained
by exchange identity for response-phase matching. Missing, invalid, or unavailable extensions will fail
closed instead of matching unrelated traffic. Verification will cover operation-specific request and response
breakpoints, generic HTTP behavior, persistence round trips, unavailable extensions, bounded ownership, and
architecture dependency direction without launching the desktop application.

Completed on 2026-08-19: Breakpoint matching is now split into a stable transport matcher and an
application-owned `BreakpointProtocolExtension` registry. The coordinator, canonical HTTP exchange models,
proxy engine, persistence mapper, and desktop editor no longer branch on GraphQL, gRPC, WebSocket, or other
protocol names. An extension owns its protocol identifier, UI-neutral criteria schema, versioned persistence
payload, bounded inspection, compiled criteria, and semantic matcher. Unknown, unavailable, malformed, and
duplicate extensions fail closed.

GraphQL is the first installed extension. A shared Kotlin-serialization parser serves both traffic semantic
inspection and live breakpoint matching, including bounded single and batch requests. Request observations
contain only compact operation metadata and are retained by canonical exchange identity only when a response
rule needs them; raw bodies are not cached and observations are removed after response evaluation. Both the
Breakpoint Manager and Traffic quick-add flow use the same schema-driven application use case, so adding a
new protocol does not require protocol-specific UI branches or proxy-engine changes. Existing generic HTTP
rules continue through the built-in HTTP extension, while the unchanged Room columns store the open protocol
identifier and opaque versioned payload.

Verification passed focused domain, application, protocol, interceptor, breakpoint-manager, traffic,
persistence, and desktop-composition tests, including a synthetic third-party protocol, GraphQL operation and
batch matching, response correlation and cleanup, missing-extension behavior, Room round trips, and a live
Netty interception test. The final `check verifyArchitectureFoundation` gate passed with 272 actionable tasks,
and `git diff --check` passed. The desktop application was not launched.

## Phase 27: Row-First Live Breakpoint Presentation [COMPLETED]

Started on 2026-08-19 after request-phase breakpoint suspension was confirmed to occur before the canonical
proxy handler admitted the exchange to capture. The live drawer therefore received a pending breakpoint while
Traffic had no row, and the migrated `isIntercepted` presentation fields had no canonical producer. This phase
moved protocol-neutral exchange admission ahead of the forwarding gate, reuses one exchange identity and
capture owner through resume, projects active breakpoint state onto Traffic, and reveals the drawer only after
the corresponding highlighted `In Progress` row is present. Active paused rows remain visible despite ordinary
Traffic filters, and resolution restores the durable response state while retaining a bounded, process-session
matched marker. The provisional request row carries metadata only; breakpoint body ownership remains bounded
and no body copy was added to the Traffic projection.

Request/response models, protocol extensions, Room ownership, and the UI-independent breakpoint coordinator
remain unchanged. Focused proxy, interceptor, data, Traffic, application-shell, and product-composition tests
passed with 157 actionable tasks, including live request/response pauses, exactly-once capture admission, and
pre-forward cancellation on breakpoint drop or disconnect.
The final `check verifyArchitectureFoundation` gate passed with 272 actionable tasks, and `git diff --check`
passed. The desktop application was not launched.

## Phase 28: Highlight-Only Breakpoint Rows [COMPLETED]

Completed on 2026-08-19. Removed the breakpoint warning icon from the Traffic method cell while retaining the
paused and matched row background highlights as the single interception indicator. No capture, breakpoint,
drawer, or traffic-state behavior changed. The desktop application was not launched.

## Phase 29: Timeline HTTP Panel Alignment Restoration [COMPLETED]

Started on 2026-08-19 after comparing the reusable Timeline panel with repository history. The original
waterfall used one 130dp label column, but per-label intrinsic `widthIn` sizing later allowed every timing
track to begin at a different horizontal position. This phase restores one shared responsive column:
130dp at normal inspector widths and one bounded width shared by every row when the inspector is narrow.
Canonical `ExchangeTimings`, Traffic ownership, and the reusable HTTP-panel boundary remain unchanged.

Focused HTTP-panel and Traffic tests passed with 74 actionable tasks, including normal and narrow inspector
width coverage. The final `check verifyArchitectureFoundation` gate passed with 272 actionable tasks, and
`git diff --check` passed. The desktop application was not launched.

## Phase 30: Reusable Kotlin Code Editor Foundation [COMPLETED]

Started on 2026-08-19 after auditing `:ui:desktop:codeEditor` as the shared payload and script editing
surface for Traffic, API Studio, GraphQL, and scripting. This phase will retain the independent editor module
and its KNet-facing facade while replacing closed language selection, full-document edit snapshots, parallel
syntax/folding paths, filtered-line search, and fragmented editor state with a reusable versioned document
and session foundation.

The target provides additive language registration with independent optional tokenization, folding,
indentation, bracket, and comment capabilities; stateful cross-line tokenization; language-aware folding;
operation-based undo/redo; a command dispatcher; real find/replace state; shared viewport behavior; and a
cohesive Compose API. Autocomplete and completion providers are explicitly outside the requested scope.
Language services remain independent from Compose rendering and KNet HTTP semantics so the editor can later
be extracted into a standalone Kotlin code-editor repository without migrating its consumers or core model.

Completed on 2026-08-19. The editor now has one versioned, chunk-sharing document model; one UI-neutral
session for document, directional selection, caret, history, and exact synchronous change events; bounded
delta undo/redo; real find/replace; strongly typed commands; and a cohesive Compose
State/Actions/Configuration API. The viewport composes visible lines only, gives one active line ownership of
text input, shares horizontal scrolling, and avoids document-sized mapping arrays on the normal unfolded path.
Full-string serialization is explicit and optional.

The closed language/highlighter implementation was replaced by an immutable contribution registry with
independent stateful tokenization, folding, indentation, bracket, and comment capabilities. Built-in JSON,
GraphQL, XML, HTML, JavaScript, CSS, and plain-text support use the same public extension boundary as future
languages. Incremental syntax processing retains immutable token chunks, observes multiline lexical state,
and falls back to a correct full pass whenever delta versions are not contiguous. Syntax, folding, and search
run against immutable snapshots with cooperative coroutine cancellation and stale-version rejection.

The built-in Compose surface now provides highlighted literal/regex/case/whole-word search, editable
replace/replace-all, language-driven indentation, bracket closing, comment toggling, folds, modern clipboard
actions, and configurable labels and semantic colors. Autocomplete/completion remains intentionally absent.
All six HTTP-panel consumers were migrated to the new API, unknown valid language identifiers are preserved
as custom contributions, obsolete duplicate buffers/history/highlighters/facades were removed, and the editor
module no longer depends on KNet domain models or Java/AWT APIs. Module responsibility and standalone
extraction guidance are documented in `:ui:desktop:codeEditor/MODULE.md` and
`docs/code_editor_architecture.md`.

Focused code-editor and HTTP-panel suites passed with 47 and 32 tests respectively. The final
`check verifyArchitectureFoundation` gate passed with 269 actionable tasks; Kotlin-first, module boundary,
module documentation, UI runtime-isolation, and composition-ownership checks all passed. `git diff --check`
passed. The desktop application was not launched.

## Phase 31: Symmetric Drag-Selection Auto-Scroll [COMPLETED]

Started on 2026-08-19 after downward drag selection stopped when the pointer entered the horizontal
scrollbar hit zone, while upward drag selection continued normally. This phase will make pointer drag
ownership stable for the complete gesture: a drag that begins in the editor remains a text-selection drag
when it crosses a scrollbar boundary, while a drag that begins on a scrollbar remains scrollbar-owned.
Regression coverage will verify downward and upward ownership behavior. The desktop application will not be
launched.

Completed on 2026-08-19. Pointer ownership is now selected once at primary-button press and retained until
release. Text drags continue selection and auto-scroll after crossing the bottom or side scrollbar hit zone,
while gestures beginning on a scrollbar remain scrollbar-owned. Three ownership regression tests cover
downward boundary crossing, scrollbar departure, and release/reacquisition; the complete code-editor suite
passed with 50 tests. The affected HTTP-panel consumer compiled, `verifyArchitectureFoundation` passed, and
`git diff --check` passed. The desktop application was not launched.

## Phase 32: Stable Drag-Selection Rendering [COMPLETED]

Started on 2026-08-19 after full-width blue selection rows flashed while virtualized lines were composed
during downward drag auto-scroll. This phase will make selection painting identical before and after text
layout becomes available, represent a selected newline with one character cell instead of the viewport
width, suppress zero-width end-line paint, and prevent active-line focus synchronization from clearing an
ongoing viewport drag selection. The desktop application will not be launched.

Completed on 2026-08-19. Empty intermediate lines now represent their selected newline with one character
cell rather than painting the viewport width; zero-column exclusive end lines produce no selection paint;
and fallback geometry matches the native-layout geometry used after a virtualized row is composed. Active
line inputs no longer request focus or publish selection/caret changes while viewport drag selection is
active, preventing the canonical selection from being cleared between pointer frames. Five focused
regression tests were added, the complete code-editor suite passed with 55 tests, the affected HTTP-panel
consumer compiled, `verifyArchitectureFoundation` passed, and `git diff --check` passed. The desktop
application was not launched.

## Phase 33: NDJSON Inspection Presentation [COMPLETED]

Started on 2026-08-19 after Traffic correctly classified a Datadog newline-delimited JSON payload as
`BodyFormat.JsonStream` but discarded its formatted frames before rendering. This phase will retain one JSON
language/editor implementation while distinguishing the multi-record transport shape in the formatter.
Detection will prefer a valid complete JSON value, recognize explicit NDJSON/JSONL media types, and otherwise
require every non-empty line to be an independently valid JSON value. The HTTP-panel presentation bridge will
render formatted frames with visible record separation through the existing read-only JSON editor. The desktop
application will not be launched.

Completed on 2026-08-19. JSON format resolution now validates a complete JSON value before considering
multi-record framing, so pretty-printed objects and arrays remain single documents. Explicit NDJSON/JSONL
media types and implicit payloads whose every non-empty line is independently valid JSON resolve to the
existing `BodyFormat.JsonStream`; each record is formatted by the same JSON formatter. Invalid mixed text
does not become an implicit stream.

The HTTP-panel presentation bridge now joins formatted stream records with a visible blank-line boundary and
passes the result to the existing read-only `CodeLanguage.JSON` editor. No NDJSON language, tokenizer, folding,
search, editor, request model, or response model was added. Module documentation records this syntax-versus-
framing rule so future JSON stream conventions extend formatter detection instead of duplicating JSON support.

The formatter, HTTP-panel, and Traffic suites passed with 46, 33, and 11 tests respectively. The final
`check verifyArchitectureFoundation` gate passed with 269 actionable tasks, and `git diff --check` passed. The
desktop application was not launched.

## Phase 34: Continuous Multi-Line Selection Paint [COMPLETED]

Started on 2026-08-19 after multi-line selection exposed the unpainted leading between the text-sized
selection surface and the taller virtualized row. This phase will retain the existing row height, text line
height, and baseline spacing while making selection paint own the complete visual-line slot. Adjacent selected
lines will therefore meet without dark seams. Horizontal selection bounds, trailing-newline width, wrapping,
fallback geometry, document content, and selection semantics remain unchanged. Regression coverage will
exercise the geometry independently, and the desktop application will not be launched.

Completed on 2026-08-19. Both read-only and editable line content now place selection paint on a full-height
visual-line surface while keeping the text content centred with its existing line height and baseline. Native
text layout still supplies horizontal character bounds. The vertical paint projection assigns outer leading
to the first and final visual slots and splits internal leading at one shared boundary, so ordinary and wrapped
selected lines meet without dark seams. The fallback path, trailing-newline cell width, row height, typography,
document text, and selection coordinates were not changed.

Three focused geometry regressions cover complete single-row paint, shared wrapped-line boundaries, and outer
leading ownership. The code-editor and HTTP-panel suites passed with 58 and 33 tests respectively. The final
`check verifyArchitectureFoundation` gate passed with 269 actionable tasks, and `git diff --check` passed. The
desktop application was not launched.

## Phase 35: Persistent Forwarding with Instant Capture Pause/Resume [COMPLETED]

Started on 2026-08-19 after comparing the migrated proxy lifecycle with commit `6b9fe3c`. The legacy Traffic
buttons appeared fast because one wildcard listener served LAN clients directly and stop returned before
active channels and Netty event loops finished closing. The migrated implementation correctly awaits complete
resource ownership but currently couples frequent capture controls to full loopback proxy, Wi-Fi gateway,
setup portal, connection, and canonical-writer teardown.

This phase will separate the stable forwarding plane from the capture plane. Traffic Start/Stop will attach or
detach versioned canonical capture targets through the existing switchable sink while Netty and Wi-Fi remain
available. A detached session will drain through one bounded retirement owner, allowing a new session to begin
without waiting for old persistence cleanup. Exchanges remain bound to exactly one capture generation; traffic
is never split between sessions. Rapid commands are serialized, Stop Capture never deletes stored traffic,
and breakpoints will not suspend traffic while capture is paused because their required Traffic row would not
exist. Full proxy shutdown remains synchronous and is reserved for application shutdown, explicit connectivity
shutdown, or configuration rebinding. The desktop application will not be launched.

Completed on 2026-08-19. `ProxyRuntimeState` and the new typed `CaptureSessionState` now represent
independent lifecycles. Traffic Stop disables breakpoint admission, atomically replaces the switchable
capture target with a paused generation, and queues the detached canonical writer on one bounded IO
retirement owner. The Netty listener, Wi-Fi gateway, setup portal, downstream connections, and forwarding
path remain active. Traffic Start on the same configured port opens a strictly ordered fresh canonical
session and swaps only the capture target; a port change still performs the required full configuration
shutdown/rebind.

The switchable connection wrapper now survives a paused target, begins capturing its next exchange after
resume, and keeps an already-created exchange attached to its original generation. Breakpoint capture
availability is separate from the user's breakpoint enablement and aggregation requirements: pause continues
pending decisions unchanged and future uncaptured exchanges bypass interception without triggering pipeline
refresh or client disconnects. Repeated UI commands are serialized as revisioned desired state, stored rows
survive pause/resume, and the toolbar reports `Forwarding · Capture paused` while Start/Stop enablement follows
capture state rather than listener state. Full proxy cleanup remains process/configuration owned and drains
both active and asynchronously retired writers before Room closes.

Focused application, data, Traffic, and product composition suites passed, including listener-identity,
generation ownership, paused-connection resume, breakpoint release, and toolbar lifecycle regressions. The
final `check verifyArchitectureFoundation` gate passed with 269 actionable tasks, `git diff --check` passed,
and the desktop application was not launched.

## Phase 36: Traffic Correctness, History, and Scale Remediation [COMPLETED]

Started on 2026-08-19 from the repository-backed Traffic module audit. This phase keeps the canonical
`:core:traffic` snapshots, application ports, Room storage, proxy/capture ownership, typed breakpoint
projection, and product DI boundaries. It incrementally corrects selected-detail identity, repeated
header/query preservation, captured-request URL ownership, decoded-body memory limits, persisted-session
visibility, bounded viewport paging, non-starving live refresh, transport/protocol classification, clear
lifecycle serialization, capture failure presentation, and remaining dead or inconsistent presentation
state. The 1,000-row bound remains a UI memory bound rather than becoming a stored-history limit.

Verification will add focused regressions for rapid selection, empty-body caching, repeated HTTP fields,
captured-query replay, compressed-body expansion limits, cross-session history, continuous generations,
filtered paging, typed HTTPS/HTTP-version classification, clear races, and filtered footer statistics. The
desktop application will not be launched.

Completed on 2026-08-19. Inspector preparation and its bounded cache are now keyed by canonical exchange ID,
so rapid selection cannot publish stale details and empty bodies are cached without sentinel ambiguity. One
application-owned captured-request converter now preserves ordered repeated headers and query parameters for
API Studio replay. Traffic rows retain only table data while the inspector reads the canonical exchange,
removing duplicate header, query, and response projections.

Decoded payloads now have an enforced output-byte ceiling across gzip, deflate, Brotli, and Zstandard paths,
and the inspector cache is bounded by both entry count and estimated retained bytes. Room history queries can
span all retained sessions, use typed scheme and application-protocol criteria, and search host, path, method,
and status. Schema 16 adds the global newest-first timestamp/ID index. The Traffic viewport remains capped at
1,000 rows while older pages continue to be reachable through a rolling window, and conflated generation
refresh prevents a continuous capture stream from starving the UI.

Traffic-specific filters now live in the Traffic presentation module and expose only classifications backed
by canonical data: HTTP/HTTPS scheme and HTTP/1.x, HTTP/2, or HTTP/3 protocol. Unsupported WebSocket, gRPC,
GraphQL, and path-name guesses were removed. Clear History is serialized, clears only persisted traffic,
resets the active viewport/cache, and reloads any surviving history. Automatic startup retention cleanup is
owned once by the desktop process rather than by a screen ViewModel, while capture failures are surfaced
independently from proxy-listener state.

Focused domain, application, data, Traffic, HTTP-panel, and product composition regressions passed. The final
`check verifyArchitectureFoundation` gate passed with 269 actionable tasks, `git diff --check` passed, and the
desktop application was not launched. Module responsibility documents were updated alongside the code and
Room schema migration.

## Phase 37: Traffic Status Column Alignment [COMPLETED]

Started on 2026-08-19 after the Traffic table showed its Status header offset from every status value. The
header reserves 64 dp while data rows reserve 84 dp; because the preceding Path column is weighted, this
causes its allocation to differ between the header and rows and moves row status cells 20 dp to the left.
This phase will give both surfaces one shared status-column width without changing table density, status
content, highlighting, colors, or column visibility behavior. The desktop application will not be launched.

Completed on 2026-08-19. The Status header and row cells now consume the same shared 84 dp width, giving the
weighted Path column identical remaining space in both table surfaces. The header, numeric statuses, and
in-progress/error labels therefore share one left edge, and later width adjustments have a single owner. The
Traffic JVM suite and `verifyArchitectureFoundation` passed with 66 actionable tasks, `git diff --check`
passed, and the desktop application was not launched.

## Phase 38: UI Core Design-System Remediation [COMPLETED]

Started on 2026-08-19 from a repository-wide audit of `:ui:core` and its active desktop consumers. This
phase keeps the existing KMP design-system boundary and reusable primitives, while correcting theme-system
resolution, token adoption, interaction semantics, dialog and split-pane correctness, input behavior,
dropdown presentation, and accessibility. The KNet dropdown family will receive one consistent anchored
field/menu design with selected, hover, disabled, searchable, and keyboard-accessible states rather than
maintaining two visually divergent implementations.

The phase will also remove only repository-proven dead aliases, no-op helpers, sample/catalog production
sources, and abandoned component-framework scaffolding; active consumers will be migrated before an API is
removed. Feature-specific table sizing will gain one Traffic-owned column specification, while protocol-
specific presentation will be kept out of generic UI foundations. Focused common/JVM tests, module
responsibility documentation, architecture verification, and whitespace validation will be run. The desktop
application will not be launched.

Completed on 2026-08-19. `:ui:core` now provides a system-aware Material-backed theme, reduced-motion-aware
animation tokens, native selection/toggle/button semantics, controlled split panes, selection-preserving text
inputs, responsive dialogs, and one visually consistent dropdown family. Both regular and searchable dropdowns
now share compact sizing, matched anchor/menu widths, clear hover/focus/disabled/selected states, truncation,
selected checkmarks, scrolling bounds, and keyboard navigation. Traffic owns its complete table metrics, while
HTTP method colors and status badges moved to `:ui:desktop:httpPanel` instead of leaking protocol concepts into
the generic design system.

Repository-proven dead aliases, duplicate wrappers, no-op helpers, production catalogs/samples, and abandoned
component-framework scaffolding were removed after migrating active consumers. `:ui:core` and HTTP-panel module
responsibility documents now describe the resulting ownership rules. `:ui:core:jvmTest`, the desktop product
compile, the full `check verifyArchitectureFoundation` gate (269 actionable tasks), and `git diff --check`
passed. The desktop application was not launched.

## Phase 39: Intrinsic-Safe Anchored Dropdown Popup [COMPLETED]

Started on 2026-08-19 after opening the searchable scripting-language dropdown raised Compose's
`SubcomposeLayout` intrinsic-measurement exception. The redesigned searchable dropdown placed a lazy list
inside Material `DropdownMenu`; Material measures menu content intrinsically, while lazy layouts deliberately
do not support intrinsic measurement. This phase replaces that incompatible nesting with a KNet-owned anchored
popup that gives both regular and searchable dropdown content explicit bounded constraints, preserves lazy
search results, handles below/above viewport placement, and keeps anchor focus for keyboard input. The desktop
application will not be launched.

Completed on 2026-08-19. Regular and searchable dropdowns now share a KNet-owned popup with an explicit anchor
width, bounded height, viewport-clamped left/right placement, and automatic below/above placement. The regular
menu retains bounded scrolling, while searchable results remain lazy without being measured intrinsically.
The input retains focus so filtering and arrow/Enter/Escape handling continue through the anchor. UI-core tests,
Settings compilation, desktop product compilation, and the full `check verifyArchitectureFoundation` gate
passed with 269 actionable tasks. `git diff --check` passed, and the desktop application was not launched.

## Phase 40: Constraint-Stable Dropdown Anchors [COMPLETED]

Started on 2026-08-19 after the shared dropdown anchor was observed in both constraint extremes. Inside the
horizontally scrollable Traffic filter bar, infinite horizontal constraints collapsed the weighted label and
left only the chevron visible. Inside API Studio's bounded request row, `fillMaxWidth` allowed the unweighted
dropdown to consume the URL field's available width. This phase gives regular and searchable dropdowns a finite
design-system default width while preserving normal caller overrides through `Modifier.width`, `widthIn`,
`weight`, and `fillMaxWidth`. Every active consumer will be checked so full-width dialog fields remain explicit
and compact toolbar/filter fields remain self-contained. The desktop application will not be launched.

Completed on 2026-08-19. Both dropdown variants now start from a finite 120 dp anchor width, making their inner
weighted label measurement deterministic in bounded and unbounded parent layouts. Because the caller modifier
precedes the default sizing modifier, explicit widths, minimum widths, weights, and full-width fields continue
to override the compact default normally. Settings' 160 dp searchable fields now express that width directly,
and all active regular/searchable dropdown consumers were reviewed. UI-core tests, all dropdown-consuming UI
module compilations, desktop product compilation, and the full `check verifyArchitectureFoundation` gate
passed with 269 actionable tasks. `git diff --check` passed, and the desktop application was not launched.

## Phase 41: API Studio UI Regression Remediation [COMPLETED]

Started on 2026-08-19 after fixing the request method dropdown exposed additional empty blocks across API
Studio's request, body-format, and GraphQL tab strips. This phase audits every API Studio surface that consumes
the changed design-system primitives, compares it with the pre-remediation implementation, and corrects shared
component behavior at its owner rather than adding screen-specific visual patches. The scope includes the URL
bar, request tabs, parameter/header/body/auth/script editors, GraphQL controls, collection sidebar, dialogs,
response pane, split layout, empty/loading states, and keyboard/resize behavior. The desktop application will
not be launched.

Completed on 2026-08-19. The empty rectangles across API Studio were one design-system regression rather than
separate screen failures: `KNetTab` gave its label `weight` inside `KNetTabRow`, whose horizontal scrolling
measures content with unbounded width, so Compose reduced every weighted label to zero. Tab labels now retain
their natural width up to a finite design-system maximum and remain single-line/ellipsized. This restores the
request inspector, request/response body-mode, GraphQL authoring/viewing, Traffic inspector, certificate, and
response-inspector tab labels from the shared owner. GraphQL authoring also gives its tab strip the toolbar
space remaining beside the operation-name field, so narrow split positions scroll instead of overlapping.

The API Studio URL bar, open-request tabs, collection sidebar, key/value editors, auth and script editors,
dialogs, response facade, loading/empty/error states, and controlled split pane were reviewed against their
pre-remediation implementations and current design-system constraints. Stale imports from the response facade
were removed, and the UI-core/HTTP-panel module responsibility documents now record the constraint contract.
Focused UI-core, HTTP-panel, and API Studio suites plus desktop product compilation passed with 138 actionable
tasks. The full `check verifyArchitectureFoundation` gate passed with 269 actionable tasks, `git diff --check`
passed, and the desktop application was not launched.

## Phase 42: Constraint-Stable Button Modifier Ordering [COMPLETED]

Started on 2026-08-19 after API Studio's empty parameter editor showed the shared `Add Param` button reduced
to a thin clipped strip. `KNetButton` applied its fixed height before the caller modifier, which placed caller
padding inside the fixed-height measurement and left insufficient height for button content. This phase restores
standard Compose modifier ownership: caller layout modifiers remain outermost while the design-system height
acts as the overridable inner default. The desktop application was not launched.

Completed on 2026-08-19. `KNetButton` now applies the caller modifier before its design-system height. Outer
padding therefore contributes to the component's total layout size instead of consuming the button's content
height, restoring the complete `Add Param`, `Add Header`, and `Add Cookie` buttons. Explicit caller heights,
including API Studio's 40 dp request-bar actions, continue to override the compact default normally. All active
API Studio, HTTP-panel, and UI-core button consumers were reviewed, and no second fixed-height-before-caller
pattern was found.

Focused UI-core, HTTP-panel, and API Studio suites plus desktop product compilation passed with 138 actionable
tasks. The full `check verifyArchitectureFoundation` gate passed with 269 actionable tasks, `git diff --check`
passed, and the desktop application was not launched.

## Phase 43: Flash-Free Incremental Syntax Presentation [COMPLETED]

Started on 2026-08-19 after editable code-editor content visibly flashed on every character mutation. The
document snapshot advances synchronously, while syntax tokenization correctly runs on a background dispatcher;
the viewport currently rejects the complete previous token model during that interval and briefly renders all
visible lines without semantic colors. This phase adds a current-version presentation projection that
synchronously retokenizes only the directly changed lines and structurally reuses the previous prefix and
suffix until authoritative background tokenization converges. Background cancellation and stale-result guards
remain intact, and the desktop application will not be launched.

Completed on 2026-08-19. The editor now keeps separate completed and presentation token models. A normal
session edit synchronously projects the changed line onto the new document version and structurally reuses
unchanged token chunks, so visible content never falls back wholesale to plain text while the worker tokenizer
is running. Consecutive keystrokes chain from the latest presentation model even when earlier background jobs
are cancelled. The completed worker result remains authoritative and replaces the presentation only when its
snapshot version is still current.

Immediate work is bounded to 32 directly changed lines and 32 KiB of changed text. Oversized edited lines are
temporarily unstyled instead of blocking the UI thread, while unaffected lines remain stable. Regression tests
cover ordinary character replacement, structural line splitting, oversized single-line payloads, and rapid
edits before background convergence. The focused editor/HTTP-panel/API-Studio/product verification passed with
139 actionable tasks; the full `check verifyArchitectureFoundation` gate passed with 269 actionable tasks.
`git diff --check` passed, and the desktop application was not launched.

## Phase 44: Wrapped Code-Editor Viewport [COMPLETED]

Started on 2026-08-19 after confirming that every active KNet editor inherits the reusable editor's disabled
word-wrap default. This phase makes visual wrapping the default so long request bodies, responses, GraphQL
documents, and scripts remain inside the viewport without a visible horizontal scrollbar. Wrapping remains a
presentation concern and never inserts document newlines. Wrapped-row keyboard navigation, pointer hit testing,
selection painting, variable-height virtualization, gutter behavior, and vertical scrolling will be verified.
The optional non-wrapped capability remains available for a future standalone editor consumer, and the desktop
application will not be launched.

Completed on 2026-08-19. `CodeEditorConfiguration` now enables word wrapping by default, so all current KNet
request, response, GraphQL, script, and Traffic-inspection editors stay inside their viewport and omit the
horizontal scrollbar without per-feature overrides. Wrapping changes only Compose layout: stored text, logical
line coordinates, gutter numbering, copy operations, and request serialization remain unchanged. Consumers of
a future standalone editor may still explicitly disable wrapping when horizontal navigation is desired.

Up/Down navigation now remains inside an active logical line while another wrapped visual row exists and crosses
logical lines only at the first or final visual row. Pointer ownership now reserves the bottom hit zone only
when a horizontal scrollbar is actually rendered, preserving bottom-edge selection and downward auto-scroll in
wrapped viewports. Regression coverage verifies the wrap default, wrapped-row navigation boundaries, and
conditional scrollbar hit zones. The focused editor/HTTP-panel/API-Studio/product verification passed with
139 actionable tasks; the full `check verifyArchitectureFoundation` gate passed with 269 actionable tasks.
`git diff --check` passed, and the desktop application was not launched.

## Phase 45: Stable Drag-Selection Rendering [COMPLETED]

Started on 2026-08-19 after pointer selection across trailing or empty line space briefly painted the complete
active row before converging to the exact selected characters. Two viewport ownership transitions cause the
flash: pointer-down moves the caret before a non-empty selection exists, so the active-line background is
temporarily eligible; and activating a row swaps its lightweight text renderer for the editable renderer,
discarding measured text geometry until the replacement layout completes. This phase makes pointer gesture
ownership observable from the initial press, retains compatible measured geometry at the keyed logical-row
boundary, and verifies that real whitespace remains selectable while unused viewport space never paints as
selection. The desktop application will not be launched.

Completed on 2026-08-19. `SelectionGestureHandler` now exposes Compose-observable gesture ownership as soon as
the initial text press establishes its anchor. The viewport uses that state together with the canonical
selection to suppress active-line background and native caret/focus behavior throughout the gesture, including
the zero-length interval before the pointer first moves. A normal click restores active-line paint on release.

Each keyed logical row now owns its latest compatible `TextLayoutResult`; the read-only `Text` and active
`BasicTextField` are stateless layout producers. Moving the caret to a row therefore retains exact measured
selection geometry across the renderer swap instead of briefly falling back to estimated character cells.
Compatibility is guarded by the current logical text, so an actual edit never applies geometry from stale
content. Regression coverage verifies press/release ownership, active-row paint policy, and exact document
columns through trailing whitespace. The focused editor/HTTP-panel/API-Studio/product verification passed with
139 actionable tasks; the full `check verifyArchitectureFoundation` gate passed with 269 actionable tasks.
`git diff --check` passed, and the desktop application was not launched.

## Phase 46: Continuous Selection Across Wrapped Logical Rows [COMPLETED]

Started on 2026-08-19 after the wrapped editor exposed vertical gaps between selection rectangles on adjacent
logical lines. Phase 44 correctly removed the fixed row height to permit wrapping, but the wrapped text-content
surface then became shorter than the gutter-owned minimum row height on single-visual-line content. Phase 45's
exact selection painter fills its content surface, not the taller sibling-owned row, so the remaining pixels
appeared as gaps. This phase gives both read-only and editable content the shared logical-line minimum while
preserving natural multi-line expansion, typography, baselines, and document semantics. The desktop application
will not be launched.

Completed on 2026-08-19. Editable and read-only line content now use one shared viewport sizing contract. The
content surface has the same minimum logical-line height as the gutter in both wrapped and unwrapped modes;
unwrapped content still fills its fixed parent, while wrapped content remains unconstrained above the minimum.
The selection painter therefore covers the sibling-owned remainder that previously appeared as a seam, without
changing `TextStyle`, line height, baseline placement, wrapping, or serialized content.

The focused editor/HTTP-panel/API-Studio/product verification passed with 139 actionable tasks; the full
`check verifyArchitectureFoundation` gate passed with 269 actionable tasks. `git diff --check` passed, and the
desktop application was not launched.

## Phase 47: Selection-Independent Active-Line Paint [COMPLETED]

Started on 2026-08-19 after clearing a selection by clicking its existing caret line caused the active-line
background to disappear on press and reappear on release. The caret never left the line; the visual transition
was created solely because active-line paint incorrectly depended on selection and gesture state. This phase
separates the layers: caret ownership alone controls active-line paint, while selection gesture state continues
to control only selection geometry, native caret visibility, and focus publication. The desktop application
will not be launched.

Completed on 2026-08-19. `LazyCodeLineRow` now applies the ambient background directly from `isActiveLine` and
does not consult selection length or pointer-gesture ownership. Selecting text, clicking to collapse it, or
releasing the pointer on the same caret line therefore leaves the background continuously mounted. Moving the
caret to a different logical line remains the only operation that transfers active-line paint.

Selection gesture ownership remains intact for the concerns it actually owns: deterministic range painting,
transparent native selection/caret presentation, and protection from focus-driven caret publication. The
obsolete combined active-line/selection paint policy and its regression assertions were removed, and the module
contract now documents the independent layers. The focused editor/HTTP-panel/API-Studio/product verification
passed with 139 actionable tasks; the full `check verifyArchitectureFoundation` gate passed with 269 actionable
tasks. `git diff --check` passed, and the desktop application was not launched.

## Phase 48: Stable Compact Dropdown Interaction [COMPLETED]

Started on 2026-08-19 after reviewing the Traffic filter toolbar's oversized dropdown anchors, click-through
close behavior, and selection-dependent width concern. The shared popup is non-focusable, so clicking its open
anchor first dismisses the popup as an outside click and then lets the same pointer event toggle the anchor open
again. The shared anchor already has a finite width, but the contract needs to make density and width stability
explicit across selected values. This phase adds a compact density used consistently by Traffic chips and
filters, consumes outside dismissal at the popup boundary, and retains the popup through a short reduced-motion-
aware exit transition. The desktop application will not be launched.

Completed on 2026-08-19. Traffic's count chips and Method, Status, and Protocol anchors now use the same 26 dp
compact-control height. The shared dropdown keeps its finite anchor width outside content measurement, so a
placeholder or newly selected value can truncate but cannot resize the field or popup. Traffic also passes its
typed filter values directly rather than maintaining duplicate label lists and reverse string lookup.

The shared popup now remains composed through a short fade/98%-scale exit and uses the design system's fast
duration and reduced-motion policy. Ordinary selection popups are focusable, so an open-anchor click is consumed
as one outside dismissal instead of reaching the anchor and reopening it. Searchable dropdowns explicitly retain
their non-focusable popup policy so the results window does not steal keyboard focus from the search field.
Regression coverage verifies both density presets, the fixed-width default, popup exit composition, and the two
focus policies. Focused UI-core, Traffic, Settings, Certificate, API Studio, and desktop composition verification
passed with 148 actionable tasks. The full `check verifyArchitectureFoundation` gate passed with 269 actionable
tasks, and the desktop application was not launched.

## Phase 49: Connect Device Navigation Placement [COMPLETED]

Started on 2026-08-20 to move the existing Connect Device destination from the primary navigation group into
the setup/security group. The route, destination identity, icon, and workspace behavior remain unchanged; only
its sidebar placement changes so it appears immediately after the primary-group divider and immediately before
Certificates. Navigation configuration coverage will assert the real section order, and the desktop application
will not be launched.

Completed on 2026-08-20. The navigation overlay now renders Traffic, API Studio, and Intercepts in the primary
group, followed by the divider, Connect Device, and Certificates. Settings and branding retain their existing
bottom placement. The destination route, icon, selection handling, and workspace host were not changed.

Navigation section metadata now has one shared owner used by both composition and regression coverage, so the
test asserts the actual Connect Device-to-Certificates ordering instead of recreating a separate expected list.
The focused app test, desktop product compilation, and `verifyArchitectureFoundation` passed with 133 actionable
tasks. `git diff --check` passed, and the desktop application was not launched.

## Phase 50: Unified Traffic Column Dropdown [COMPLETED]

Started on 2026-08-20 after confirming that Traffic's Columns control still bypasses the KNet dropdown family
and directly composes a Material menu. This produces different anchor height, surface styling, motion, width,
and dismissal behavior beside the Method, Status, and Protocol controls. This phase adds one reusable generic
multi-select dropdown to `:ui:core`, backed by the existing KNet anchor and popup mechanics, and migrates the
active Traffic column selector to it. Multi-selection remains explicit: toggling an item does not close the
menu, and each row owns exactly one toggle action. The desktop application will not be launched.

Completed on 2026-08-20. `KNetDropdown` and the new generic `KNetMultiSelectDropdown` now share one internal
anchor owner for compact/standard height, hover/focus borders, keyboard toggling, stable label measurement, and
chevron motion. Multi-select uses the same bounded animated popup and outside-click ownership as single-select,
but intentionally remains open after a value is toggled. Its 148 dp design-system default accommodates a compact
checkbox indicator and Traffic's longer column labels without allowing content to resize the control.

Traffic's feature-local Material menu, duplicate anchor styling, nested checkbox click handler, and supporting
imports were removed. Traffic now supplies only typed optional `TrafficColumn` values, visibility lookup, toggle
callback, and display labels. The option row owns checkbox semantics and exactly one toggleable interaction while
reusing the shared KNet checkbox indicator, selected surface, hover transition, typography, and ellipsis policy.

UI-core and Traffic module contracts document the new ownership, and component coverage locks both dropdown
width presets. Focused UI-core, Traffic, and desktop product verification passed with 133 actionable tasks. The
full `check verifyArchitectureFoundation` gate passed with 269 actionable tasks, and the desktop application was
not launched.

## Phase 51: API Studio State, Persistence, and Execution Remediation [COMPLETED]

Started on 2026-08-20 after a repository-backed audit found that API Studio document hydration, autosave,
promotion, startup restoration, and request cancellation were coordinated by two ViewModels plus the screen.
That ownership permitted partial or duplicate writes, stale execution results, successful no-op dependency
fallbacks, and lossy request persistence. This phase establishes one active-document owner, one ordered latest-
state autosave path, atomic draft-to-saved transitions, cancellation-safe execution identity, lossless request
round trips, transactional promotions, explicit loading/failure presentation state, and production Koin
verification. Application orchestration will move out of presentation where the existing boundaries support it;
shared mutable editor state will remain a UI projection of canonical authored and observed HTTP values. The
desktop application will not be launched.

Completed on 2026-08-20. `ApiStudioViewModel` is now the sole owner of the active request document while
`CollectionsViewModel` owns only persisted sidebar CRUD. `SavedApiRequest` is the canonical authored-request
model across API Studio and persistence, with typed HTTP methods and body formats plus lossless query-parameter,
header, cookie, body-field, authentication, and script state. Room schema 17 persists that complete model, and
draft promotion is transactional instead of a delete-then-save sequence.

Autosave and workspace selection now pass through ordered, cancellation-aware coordinators, startup restores the
exact saved request, stale executions cannot replace newer editor results, and persistence failures are visible
without falsely updating the UI. Request execution and direct traffic recording moved to the application layer;
the desktop product supplies explicit production bindings for dispatchers and scripting. Dead request tabs,
environment UI, duplicate API Studio theme/sidebar models, and obsolete presentation execution use cases were
removed. Module responsibility documents were updated for each affected boundary.

Focused domain, application, data, storage, API Studio, and desktop-product verification passed. The complete
`check verifyArchitectureFoundation` gate passed with 269 actionable tasks (136 executed, 133 up-to-date), and
the desktop application was not launched.

## Phase 52: API Studio Request Bar Visual Refinement [COMPLETED]

Started on 2026-08-20 to give the request method and URL distinct visual ownership. The method selector will be
a fixed-width standalone dropdown on the left with a centered method label, while the URL remains the flexible
middle field with vertically centered content and a concise empty-state hint. Existing send, cancel, save,
keyboard, overflow, and responsive behavior will remain unchanged. The desktop application will not be launched.

Completed on 2026-08-20. The method dropdown is now a standalone, fixed-width control before the independently
bordered URL field. Its selected method is centered without moving the trailing chevron, and method colors are
applied consistently for every method rather than treating GET as a placeholder selection. The URL field retains
the flexible width, Enter-to-send behavior, overflow preview, and vertically centered single-line text while
showing the compact `Enter request URL` hint only when empty.

The shared dropdown gained opt-in selected-label alignment with start alignment preserved as the default, so no
other KNet dropdown changed visually. UI-core and API Studio module contracts document the ownership. Focused
UI-core tests, API Studio tests, desktop product compilation, and `verifyArchitectureFoundation` passed with 139
actionable tasks. `git diff --check` passed, and the desktop application was not launched.

## Phase 53: API Studio Request Bar Height and Alignment Correction [COMPLETED]

Started on 2026-08-20 after visual verification showed the standalone method dropdown using the shared 36 dp
standard height beside 40 dp URL and action controls. This correction adds an explicit 40 dp dropdown density,
uses it for the request method, and restores normal start alignment so the method retains compact left padding
while remaining vertically centered. The desktop application will not be launched.

Completed on 2026-08-20. API Studio now selects the shared 40 dp large dropdown density, matching the URL, Save,
and Send control heights exactly. The method label again uses the standard 10 dp leading inset and remains
vertically centered; the earlier horizontal-centering option was removed because it no longer had a real caller.
Compact and standard dropdowns remain unchanged. UI-core coverage now locks all three density heights.

Focused UI-core tests, API Studio tests, desktop product compilation, and `verifyArchitectureFoundation` passed
with 139 actionable tasks. `git diff --check` passed, and the desktop application was not launched.

## Phase 54: Content-Responsive Single-Select Dropdown Width [COMPLETED]

Started on 2026-08-20 to replace the single-select dropdown's hardcoded 120 dp default width with a measured,
content-responsive width. The default anchor and popup will size to the widest option plus their required visual
chrome, bounded by design-system minimum and maximum widths. Width will remain stable across selection changes,
and explicit caller sizing will continue to override the calculated default. Verification will be deferred while
the user-owned KNet process is running so its live classpath is not overwritten.

Completed on 2026-08-20. `KNetDropdown` now measures every option label using the active design-system text style
and derives one stable anchor width from the widest result. Anchor and selected-menu chrome are included in the
calculation, with a 72 dp minimum and 280 dp maximum. This removes the former 120 dp hardcoded single-select
width without allowing the control to jump when selection changes or grow without a responsive bound. Explicit
feature widths still win through the caller modifier; searchable and multi-select controls retain their distinct
120 dp and 148 dp defaults because their content and interaction contracts differ.

Pure sizing coverage locks the lower bound, calculated content width, upper bound, density chrome, and specialized
dropdown defaults. `git diff --check` passed. Gradle verification was intentionally not run because the user-owned
KNet process remained active, preventing another live-classpath JAR replacement failure.

## Phase 55: Extensible API Studio Session/Request Naming [COMPLETED]

Started on 2026-08-20 to replace URL-only and ViewModel-owned API Studio document naming with an extensible
request-naming pipeline. This phase applies only to session/request titles; collection names remain entirely
user-controlled. The canonical saved-request model will persist whether a title is generated or user-defined,
HTTP naming will use a meaningful path/host fallback, and GraphQL naming will reuse the existing formatter to
resolve explicit or document operation names. Product DI will compose ordered protocol strategies so future
formats can add naming support without modifying the API Studio ViewModel or stable naming use case.

Completed on 2026-08-20. API Studio session/request titles now resolve from the canonical `SavedApiRequest`
through an ordered contribution pipeline. The terminal HTTP strategy returns a query/fragment-free path or root
host; the GraphQL contribution reuses `GraphQLBodyFormatter` to resolve explicit envelope and named AST
operations, falling back cleanly for anonymous operations. Product DI owns strategy precedence, so another
protocol adds one contribution and one binding without adding protocol branches to the ViewModel. Collection
names remain entirely user-controlled. Phase 56 subsequently widened this same contribution boundary to resolve
the sidebar protocol badge alongside the generated title.

Generated-versus-user-defined ownership is now part of the canonical request and Room schema v18. Generated
titles use a 250 ms latest-wins debounce and perform canonical conversion plus protocol parsing off the UI
dispatcher. Save-dialog changes and sidebar renames become user-defined and cannot be overwritten later; legacy
rows migrate as `USER_DEFINED`. Ordered auto-save snapshots retain this ownership across draft/saved promotion,
proxy/app restarts, and direct request restoration. Domain, formatter, mapper, auto-save, ViewModel, and sidebar
coverage was added. `git diff --check` passed. Gradle execution and the generated schema-v18 export were
intentionally deferred because the user-owned KNet process remained active on the build JAR classpath; the
desktop application was not launched or restarted.

## Phase 56: Unified API Studio Request Descriptor and Protocol Badge [COMPLETED]

Started on 2026-08-20 to evolve the new request-name-only contribution into one extensible descriptor pipeline.
The same protocol recognition will now provide both the generated session/request title and the compact sidebar
badge, preventing duplicate GraphQL/future-protocol parsing. Normal HTTP requests retain their method badge;
GraphQL requests use `GQL` while preserving their actual HTTP method as transport metadata. API Studio's request
bar continues to edit the real HTTP method, and collection names remain outside this feature.

Completed on 2026-08-20. `DescribeRequestUseCase` now resolves a generated title, open semantic kind, compact
badge, and actual HTTP transport method from one canonical `SavedApiRequest`. Ordered product composition installs
the GraphQL contribution before the terminal HTTP contribution. Named GraphQL documents therefore use their
operation name and `GQL`; anonymous GraphQL documents retain `GQL` while borrowing only `/graphql` as the HTTP
name fallback. Ordinary requests continue to show `GET`, `POST`, or their real method.

Both unsaved sessions and saved collection rows receive the descriptor in the Room-backed sidebar projection,
which already runs on the injected I/O dispatcher. The request bar remains unchanged and continues to author the
transport method. Future protocols can contribute a new kind, badge, and optional name through one strategy plus
one product binding; the sidebar renders unknown kinds with its neutral accent fallback without requiring a core
change. Domain, formatter, and sidebar ViewModel coverage locks priority, anonymous-name fallback, parser reuse,
badge identity, and method retention. `git diff --check` passed. Gradle verification was intentionally deferred
because the user-owned KNet process remains active on the build-JAR classpath; the application was not launched,
stopped, or restarted.

## Phase 57: Stable Centered API Studio Method Selector [COMPLETED]

Started on 2026-08-20 after visual review found that the stable method-selector width correctly prevents request-
bar movement but its edge-separated label and chevron create excessive empty space for short methods. The shared
dropdown will gain an opt-in centered anchor-content arrangement. API Studio alone will use it to keep the method
and chevron as one compact group with fixed spacing while preserving the existing widest-option width, popup
animation, keyboard behavior, and unchanged URL/action positions. The desktop application will not be launched.

Completed on 2026-08-20. `KNetDropdown` now offers an opt-in centered anchor-content arrangement while retaining
its existing edge-separated default for Traffic filters, forms, and multi-select controls. API Studio enables the
option only for the HTTP method selector. Its label and rotating chevron now render as one centered group with a
design-system-owned 8 dp gap, while the anchor and popup remain sized from the widest method option. Selecting
`POST`, `OPTIONS`, or another method therefore cannot resize the selector or move the URL, Save, and Send controls.
The existing reduced-motion-aware popup and chevron animations are unchanged. UI-core sizing coverage locks the
new spacing token, and module responsibility documents record the opt-in ownership. `git diff --check` passed.
Gradle verification was intentionally deferred because the user-owned KNet process remains active on the build-
JAR classpath; the application was not launched, stopped, or restarted.

## Phase 58: Interactive API Studio Execution Cancellation [COMPLETED]

Started on 2026-08-20 after runtime verification showed that API Studio correctly changes Send into a loading
Cancel control, but the shared button's default loading policy disables pointer input and the hand cursor. The
button primitive will retain that safe default for ordinary submissions and gain an explicit opt-in for controls
whose loading state represents a valid cancellation action. API Studio will enable the opt-in only while a cancel
callback is available. Existing execution cancellation and keyboard routing remain unchanged, and the desktop
application will not be launched.

Completed on 2026-08-20. `KNetButton` retains its default `enabled && !loading` interaction policy and now exposes
an explicit `clickableWhileLoading` override for genuine cancellation controls. The override feeds the same
resolved interaction state into Compose click handling and the desktop hand cursor, so visual and pointer states
cannot disagree. API Studio opts in only for its Send/Cancel action when a cancellation callback exists; its
spinner and Cancel label remain visible while clicks now invoke the existing revision-safe `cancelExecution()`.
All other loading buttons remain protected from duplicate submission. UI-core policy coverage locks enabled,
disabled, loading, and cancellable-loading combinations, and module documents record the ownership.
`git diff --check` passed. Gradle verification was intentionally deferred because the user-owned KNet process remains
active on the build-JAR classpath; the application was not launched, stopped, or restarted.

## Phase 59: Expanded Dropdown Anchor Pointer Ownership [COMPLETED]

Started on 2026-08-20 after desktop verification showed that opening a focusable dropdown transfers pointer
ownership to the popup layer, causing the still-visible anchor to fall back from the hand cursor to the system
default. The shared popup will include a transparent, anchor-sized interaction proxy only for focusable selection
dropdowns. That proxy will retain the hand cursor and close the dropdown directly, while the same focusable popup
continues to consume the click so the former close-then-reopen defect cannot return. Searchable comboboxes will
retain their non-focusable popup and text cursor contract. The desktop application will not be launched.

Completed on 2026-08-20. Focusable single-select and multi-select dropdowns now measure the complete anchor size
and host a transparent anchor interaction proxy in the same popup layer as the animated menu. The proxy exposes
the hand cursor and directly dismisses the popup, so clicking an expanded header closes exactly once and cannot
reach the underlying anchor. Popup placement accounts for the proxy whether the menu opens above or below and
prefers the side with enough space, while outside-click dismissal and the existing focusable input policy remain
unchanged. Searchable comboboxes continue to use their non-focusable menu-only popup and text cursor. Pure
placement coverage locks below, above, and constrained-side behavior. `git diff --check` passed. Gradle
verification was intentionally deferred because the user-owned KNet process remains active on the build-JAR
classpath; the application was not launched, stopped, or restarted.

## Phase 60: Request Tab Alignment and Select-All Viewport Stability [COMPLETED]

Started on 2026-08-20 after API Studio visual verification found that the primary Params/Auth/Headers/Body tab
strip remained edge-to-edge while the surrounding request-authoring controls use the shared medium horizontal
inset. The request editor will apply that existing spacing token at its call site without imposing fixed tab
widths or changing shared Traffic/response inspector layouts. Code-editor verification also found that Ctrl/Cmd+A
moves the logical caret to the full selection's active document-end boundary, after which the viewport's generic
caret-reveal effect scrolls to the final line. The session will retain that correct directional selection and
caret model, while the viewport will recognize a whole-document selection as a keep-viewport operation. Search,
ordinary navigation, editing, and drag-selection auto-scroll behavior will remain unchanged. The desktop
application will not be launched.

Completed on 2026-08-20. The reusable request-authoring panel now applies the existing medium horizontal spacing
token to its primary Params/Auth/Headers/Body/Cookies/Scripts tab strip. Individual tabs remain content-sized and
the shared row retains horizontal overflow, so counts, future labels, and narrow split panes do not require fixed
widths. Read-only Traffic and response inspector call sites were not changed.

The virtualized code-editor viewport now distinguishes complete-document selection from navigation before its
caret-reveal effect runs. Ctrl/Cmd+A and the shared Select All action therefore preserve the current scroll
position while `EditorSession` continues to own the correct full directional selection and document-end active
caret. Partial selections still reveal their endpoint, preserving search and ordinary selection behavior; pointer
drag auto-scroll remains independently owned by its existing controller. Pure policy coverage locks caret-only,
partial, forward whole-document, and reverse whole-document cases. `git diff --check` passed. Gradle verification
was intentionally deferred because the user-owned KNet process remains active on the build-JAR classpath; the
application was not launched, stopped, or restarted.

## Phase 61: Request Tab Divider Removal [COMPLETED]

Started on 2026-08-20 after visual verification of Phase 60 showed that the request tab surface is correctly
inset but the separate, full-width divider below it remains visible across the authoring pane. That divider will
be removed only from `RequestEditorPanel`; the tab surface and spacing already provide the required grouping.
Traffic and response inspector separators will remain unchanged. The desktop application will not be launched.

Completed on 2026-08-20. The standalone `HorizontalDivider` immediately below the request-authoring primary tab
strip was removed along with its now-unused import. The inset tab surface remains the sole visual grouping, while
the URL-bar divider above and all Traffic/response inspector separators remain unchanged. `git diff --check`
passed. Gradle verification was intentionally deferred because the user-owned KNet process remains active on the
build-JAR classpath; the application was not launched, stopped, or restarted.

## Phase 62: Stable GraphQL Editor Sessions and Toolbar Actions [COMPLETED]

Started on 2026-08-20 after runtime verification showed the editor toolbar flashing when switching among GraphQL
Query, Variables, and Extensions. The active tab currently changes both text and language on one controlled editor
session: external replacement occurs after composition, language changes reset fold regions, and the debounced fold
analysis temporarily removes Expand All/Collapse All, shifting Prettify. GraphQL will retain one `CodeEditorState`
per logical sub-document so undo, caret, selection, and document identity do not leak across tabs. The shared editor
toolbar will reserve its fold-action slot from configured language capability and disable actions while no fold
regions exist, rather than conditionally removing the slot based on transient asynchronous results. The desktop
application will not be launched.

Completed on 2026-08-20. `GraphQlEditor` now unconditionally retains independent `CodeEditorState` instances for
Query, Variables, and Extensions and presents the active state through the existing single editor call site.
User-originated text is marked before updating the controlling GraphQL model, while genuine external changes such
as request restoration, operation-name synchronization, and Prettify results synchronize only the matching
session. Switching tabs no longer performs an asynchronous full-document replacement, clears another tab's undo
history, or shares its caret and selection.

The editor header now reserves Expand All/Collapse All from header configuration plus the registered language's
folding capability. Fold analysis results only control whether those actions are enabled and use a hand cursor;
they never remove the action container or shift Prettify. GraphQL and JSON both contribute folding providers, so
the toolbar structure remains identical throughout GraphQL sub-tab switches. Pure policy coverage locks supported,
disabled-by-configuration, and unsupported-language cases. Module contracts and the code-editor architecture
guide now record per-document state ownership and stable toolbar rules. `git diff --check` passed. Gradle
verification was intentionally deferred because the user-owned KNet process remains active on the build-JAR
classpath; the application was not launched, stopped, or restarted.

## Phase 63: Extensible Code-Editor Header Actions [COMPLETED]

Started on 2026-08-20 after confirming that the editor session and folding architecture is extensible but the
header still exposes a format-specific `onPrettify` callback. The code-editor API will replace that callback with
an ordered list of strongly identified, declarative header actions plus one generic action dispatcher. Existing
HTTP-panel formatting integrations will contribute Prettify through this contract. Folding will remain an
editor-owned capability. The renderer will use stable action identities and preserve disabled action slots, so
future JSON, GraphQL, XML, gRPC, WebSocket, or other format actions can be added without modifying editor core.
The desktop application will not be launched.

Completed on 2026-08-20. `CodeEditorHeaderConfiguration` now accepts an ordered list of callback-free
`CodeEditorHeaderAction` declarations. Each action reuses the existing validated `EditorCommandId.Custom`
identity, and `CodeEditorActions.onCommand` is the single generic interaction boundary. The toolbar renders
contributions with stable Compose keys, preserves declared disabled actions, rejects duplicate identities, and
remains unaware of format semantics. The dedicated `onPrettify` callback and editor-owned Prettify label were
removed rather than retained as compatibility APIs.

The HTTP panel now owns one namespaced Prettify contribution and routes it to the active request, response, or
GraphQL formatter. Future format actions can add declarations and command handling without modifying code-editor
configuration, action callbacks, or rendering. Folding remains editor-owned and continues to reserve its stable
capability-based slot. Pure tests cover duplicate command identities and namespaced Prettify dispatch. Module
contracts and the architecture guide document the extension boundary. `git diff --check` passed. Gradle
verification was intentionally deferred because the user-owned KNet process remains active on the build-JAR
classpath; the application was not launched, stopped, or restarted.

## Phase 64: Wrapped Read-Only Key-Value Content [COMPLETED]

Started on 2026-08-20 after response-cookie inspection showed long values being ellipsized despite available
vertical space. The shared `KNetReadOnlyKeyValueViewer` already contains an opt-in multiline path, but its default
is single-line and no network-inspection caller enables it. Read-only values will wrap by default and grow their
row naturally, while key labels and table headers remain stable single-line columns and the copy action stays at
the row's top edge. Editable key-value tables will remain single-line. Compact read-only consumers may still opt
out explicitly. The desktop application will not be launched.

Completed on 2026-08-20. `KNetReadOnlyKeyValueViewer` now wraps value content by default and uses the resulting
multi-line measurement to grow each row naturally. Key labels and both column headers remain single-line and
ellipsized, while multiline rows top-align the key, value, and existing copy action. The explicit `wrapValues`
parameter retains a compact single-line opt-out and replaces the less precise `allowMultiLine` name. Because all
request-header, response-header, cookie, parameter, and form-data inspection paths already consume this shared
viewer, they inherit the behavior without feature-specific changes. The editable `KNetKeyValueEditor` remains
single-line. The UI-core module contract records the shared policy. `git diff --check` passed. Gradle verification
was intentionally deferred because the user-owned KNet process remains active on the build-JAR classpath; the
application was not launched, stopped, or restarted.

## Phase 65: Shared Editable-Value Overflow Preview [COMPLETED]

Started on 2026-08-20 after choosing the API Studio URL field's measured-overflow preview for compact editable
key/value rows. The existing hover measurement, stationary-pointer delay, popup placement, and styling are
currently private implementation inside `KNetTextField`; copying that behavior into the key/value editor would
create two divergent interaction paths. UI core will extract one internal overflow-popup host and compose it from
both `KNetTextField` and the editable key/value key/value cells. Rows and stored values remain single-line, and a
popup is composed only when the actual rendered text width exceeds its cell. Read-only wrapped values remain
unchanged. The desktop application will not be launched.

Completed on 2026-08-20. UI core now owns one internal `OverflowTextPopupHost` that measures complete text with
the caller's exact inline style, subtracts caller-declared unavailable horizontal space, waits for stationary
hover, and displays the complete value through the existing bounded non-focusable popup placement. The original
`KNetTextField` now composes this host instead of owning a private duplicate implementation, preserving API Studio
URL and other text-field behavior.

Both editable key and value cells now compose the same host around their existing single-line
`BasicTextField`. The table's appearance, row height, editing callbacks, checkbox/delete controls, and stored
values remain unchanged; only genuinely clipped text receives the full-value preview. Password fields continue
to suppress previews, and Phase 64's read-only wrapping remains independent. Pure coverage locks unmeasured,
exact-fit, and overflowing width decisions. The UI-core module contract records the shared ownership.
`git diff --check` passed. Gradle verification was intentionally deferred because the user-owned KNet process
remains active on the build-JAR classpath; the application was not launched, stopped, or restarted.

## Phase 66: Inset API Studio Request Separator [COMPLETED]

Started on 2026-08-20 after the remaining separator above API Studio's primary request tabs was shown extending
past the inset tab surface. The separator is owned by `ApiStudioScreen`, not the reusable HTTP request panel. It
will retain the URL-bar/request-editor boundary while adopting the same shared horizontal spacing token as the
primary tabs. Traffic inspection, response inspection, and the already-removed divider below the tabs will remain
unchanged. The desktop application will not be launched.

Completed on 2026-08-20. The URL-bar separator now uses the shared medium horizontal spacing token already used
by API Studio's primary request-tab surface. The line therefore retains the intended structural separation but
terminates at the same left and right bounds as the tabs instead of reaching the pane edges. The change is local
to `ApiStudioScreen`; Traffic inspection, response inspection, and the reusable HTTP request panel are unchanged.
The API Studio module contract records ownership of this boundary. `git diff --check` passed. Gradle verification
was intentionally deferred because the user-owned KNet process remains active on the build-JAR classpath; the
application was not launched, stopped, or restarted.

## Phase 67: Rounded Shared Tab Container [COMPLETED]

Started on 2026-08-20 after API Studio's inset primary request-tab surface remained square beside rounded inputs,
buttons, and dropdowns. The existing shared `KNetTabRow` will clip its background and scrollable content with the
same small theme shape already used by individual tabs. This keeps feature call sites free of duplicated shaping
and gives all shared tab rows one consistent design-system treatment without changing their sizing, overflow, or
selection behavior. The desktop application will not be launched.

Completed on 2026-08-20. `KNetTabRow` now clips its surface and scrollable children with the design-system small
shape before painting its background. API Studio's primary request tabs therefore receive matching rounded
container corners through the existing shared component, with no feature-specific shape or duplicate wrapper.
Tab height, insets, content-responsive widths, horizontal overflow, and selection behavior are unchanged. The
UI-core module contract records the shared styling policy. `git diff --check` passed. Gradle verification was
intentionally deferred because the user-owned KNet process remains active on the build-JAR classpath; the
application was not launched, stopped, or restarted.

## Phase 68: Visible Shared Tab Corners [COMPLETED]

Started on 2026-08-20 after visual verification showed that the shared 2 dp small shape is effectively
imperceptible on API Studio's wide dark tab surface. `KNetTabRow` will use the design-system medium shape already
used by dropdown surfaces and apply that shape to both clipping and background painting. Tab dimensions, insets,
scrolling, and selection behavior will remain unchanged. The desktop application will not be launched.

Completed on 2026-08-20. The shared tab-row surface now clips with the 4 dp medium theme shape and paints its
background with that same shape, matching KNet dropdown containers and making the outer corners visibly rounded
on wide dark surfaces. The previous 2 dp radius was valid but visually negligible at this scale. No dimensions,
spacing, scrolling, or interaction behavior changed. The UI-core module contract now records the medium-corner
policy. `git diff --check` passed. Gradle verification was intentionally deferred because the user-owned KNet
process remains active on the build-JAR classpath; the application was not launched, stopped, or restarted.

## Phase 69: Remove API Studio Top Tab Divider [COMPLETED]

Started on 2026-08-20 after confirming that API Studio still renders an explicit separator between the URL bar
and its rounded primary request-tab surface. That screen-owned divider and its unused import will be removed so
the inset rounded tab container provides the grouping without a competing line. Traffic and response inspector
dividers will remain unchanged. The desktop application will not be launched.

Completed on 2026-08-20. `ApiStudioScreen` no longer renders the horizontal separator after `RequestUrlBar`, and
the now-unused divider import was removed. The rounded, inset primary request-tab surface is now the only visual
grouping between request actions and request content. Traffic, response inspection, and split-pane dividers were
not changed. The obsolete separator ownership entry was removed from the API Studio module contract.
`git diff --check` passed. Gradle verification was intentionally deferred because the user-owned KNet process
remains active on the build-JAR classpath; the application was not launched, stopped, or restarted.

## Phase 70: Inset Request Key-Value Editors [COMPLETED]

Started on 2026-08-20 after Params, Headers, and Cookies showed their editable table header and divider extending
to the request-pane edges beneath the inset rounded primary tabs. `RequestEditorPanel` will reuse one local
modifier that applies the existing medium horizontal/vertical spacing and medium clipping shape to those three
key-value editors. The domain-agnostic UI-core editor will remain unchanged so scripting, breakpoint, response,
and body-format layouts keep control of their own placement. The desktop application will not be launched.

Completed on 2026-08-20. `RequestEditorPanel` now builds one request-specific key-value editor modifier from the
shared medium spacing and shape tokens and reuses it for Headers, Params, and Cookies. Their table headers, row
dividers, empty states, and controls now sit within the same inset bounds as the primary request tabs, and the
clipped table surface exposes matching rounded corners. Body, Auth, Scripts, and unrelated UI-core editor callers
retain their previous layout. Static call-site inspection confirmed exactly the three intended branches use the
modifier. The HTTP-panel module contract records this placement policy. `git diff --check` passed. Gradle
verification was intentionally deferred because the user-owned KNet process remains active on the build-JAR
classpath; the application was not launched, stopped, or restarted.

## Phase 71: Content-Responsive Dropdown Width Policy [COMPLETED]

Started on 2026-08-20 after the Auth selector truncated “Inherit auth from parent” even though its measured
content-responsive width should accommodate the label. The shared single-select dropdown will separate stable
anchor width from preferred menu width, derive both from the complete option set, animate width only when that
set changes, and allow the menu to grow beyond the anchor while its popup layout clamps to window constraints.
Anchor and menu labels will reuse the existing overflow-preview host only when their rendered allocation still
clips the complete text. The selected-row chrome will remove its duplicate spacing so measurement and rendering
use the same contract. Searchable and multi-select dropdown behavior will remain unchanged unless using the
existing default popup width. The desktop application will not be launched.

Completed on 2026-08-20. Standard single-select dropdowns now measure their complete option label set with the
exact anchor and menu typography, derive independent anchor and menu width targets, and animate those targets
through the shared reduced-motion-aware duration. Selection changes do not participate in the width target. The
anchor remains bounded by its design maximum and incoming layout constraints; the menu may request the complete
widest-row width and the focusable popup layout clamps it to window constraints while keeping its transparent
dismissal proxy aligned over the real anchor, including when the wider menu shifts away from a window edge.

Both anchor labels and ordinary menu labels now use the existing measured overflow-preview host, which remains
dormant unless the rendered text actually exceeds its allocation. The redundant selected-row spacer was removed,
so the menu chrome rendered beside the checkmark now exactly matches the width calculation and “Inherit auth from
parent” no longer truncates unnecessarily. Searchable, multi-select, and custom-rendered menu content retain their
existing contracts. Pure coverage records independent anchor/menu chrome and menu growth, anchor-flooring, and
window clamping. The UI-core module contract records the policy. `git diff --check` passed. Gradle verification
was intentionally deferred because the user-owned KNet process remains active on the build-JAR classpath; the
application was not launched, stopped, or restarted.

## Phase 72: Dropdown Label-to-Chevron Spacing [COMPLETED]

Started on 2026-08-20 after visual verification showed non-centered dropdown labels sitting directly against the
chevron's independently highlighted region. The standard anchor will apply the existing 8 dp shared content gap
as trailing label padding and include it in its content-responsive width calculation. Centered anchors already
use that token and will remain unchanged. Menu sizing, viewport clamping, and selection behavior will remain
unchanged. The desktop application will not be launched.

Completed on 2026-08-20. Non-centered `KNetDropdownAnchor` labels now retain the shared 8 dp trailing gap before
the chevron region, matching the spacing already used by centered label-and-chevron groups. The anchor width
calculator includes that gap, so adding the visual separation does not steal space from the measured label or
introduce new truncation. Menu width, checkmark spacing, viewport clamping, and selection-stable animation remain
unchanged. The sizing assertion and UI-core module contract were updated. `git diff --check` passed. Gradle
verification was intentionally deferred because the user-owned KNet process remains active on the build-JAR
classpath; the application was not launched, stopped, or restarted.

## Phase 73: Session-Owned Selection Deletion [COMPLETED]

Started on 2026-08-20 after multiline and full-document selections failed to clear with Backspace even though
ordinary active-line deletion worked. The viewport currently gates selection deletion on a presentation-derived
selected-text string before asking the session to mutate, allowing a selection transition and the next key event
to fall through to the focused per-line text field. The editor foundation will add one UI-neutral
`EditorSession.deleteSelection` mutation and a typed `EditorCommand.DeleteSelection`. The editable viewport will
dispatch that operation first for Backspace/Delete and consume the key only when the authoritative live session
actually deleted a selection. Select All will use the same built-in dispatcher rather than a duplicated Compose
range calculation. Tests will cover full multiline, reverse partial, no-selection, command dispatch, caret,
selection clearing, and undo restoration. The desktop application will not be launched.

Completed on 2026-08-20. `EditorSession.deleteSelection()` now owns the complete selection-deletion transaction:
it reads the live directional selection, applies one typed deletion edit, clears selection, places the caret at
the normalized range start, preserves the editor's one-empty-line document invariant, and records the operation
for undo. `EditorCommand.DeleteSelection` exposes that behavior through the existing UI-neutral dispatcher, and
Select All now uses the same dispatcher rather than a Compose-local range calculation.

The editable viewport dispatches selection deletion for both Backspace and Delete and consumes the key only when
the session reports that a non-empty selection was removed. With no selection, the event continues to the focused
line input, preserving normal character and line-boundary deletion. Session and dispatcher coverage locks full-
document clearing, reverse multiline ranges, caret and selection state, undo restoration, and no-selection
fall-through semantics. Module and architecture documentation record the reusable ownership boundary.
`git diff --check` passed. Gradle verification was intentionally deferred because the user-owned KNet process
remains active on the build-JAR classpath; the application was not launched, stopped, or restarted.

## Phase 74: Document Selection Input and Multi-Click Ownership [COMPLETED]

Started on 2026-08-20 after repository history and current event-flow inspection confirmed two editing regressions
introduced when the former full-document `BasicTextField` was replaced by virtualized per-line inputs. A custom
viewport double-click can currently establish a word range and then lose it to a native line-field caret callback
later in the same pointer event. Once a viewport range does survive, focus is intentionally withheld from the line
field, but the focusable viewport has no text-input/IME connection; printable input therefore cannot replace a
single-line, multiline, reverse, or whole-document selection.

This phase adds a Compose-only document-selection text-input bridge while retaining `EditorSession` and typed
editor commands as the mutation boundary. Committed keyboard or IME text will dispatch the existing insertion
command, which replaces the session's authoritative range as one undoable edit. Pointer gesture ownership will be
read live during native line callbacks so a same-event double-click cannot publish a competing caret transition.
The virtualized renderer, language SPI, HTTP consumers, and feature modules will remain unchanged. Focused tests
will cover live gesture suppression, committed/composing input policy, word selection persistence, and multiline
replacement semantics. The desktop application will not be launched.

Completed on 2026-08-20. Editable line callbacks now consult live viewport-gesture ownership before publishing a
caret transition, closing the same-pointer-event race that removed double-click word selection. A minimal hidden
Compose text input retains keyboard, dead-key, and IME composition while a document-level selection owns focus;
committed text is forwarded through `EditorCommand.InsertText`, so `EditorSession` replaces the authoritative
range as one undoable operation. The bridge remains focused through the first commit until the visible active line
safely retakes focus, avoiding dropped follow-up characters when the selection endpoint was outside the composed
viewport.

Regression coverage records live gesture suppression, Unicode and multiline committed input, retained IME
composition, empty-input no-op behavior, and reverse multiline replacement with caret, selection, and undo
restoration. Existing gesture tests continue to cover double-click word and triple-click line range calculation.
`git diff --check` passed. Gradle verification was intentionally deferred because the user-owned KNet process
remains active on the build-JAR classpath; the application was not launched, stopped, or restarted.

## Phase 75: Interception Correctness and Scalability Hardening [COMPLETED]

Started on 2026-08-20 after the interception audit found correctness risks in the edit contract, HTTP message
rebuilding, informational-response correlation, disconnect cleanup, response capture ordering, and eager UI body
preparation. This phase will make unchanged forwarding explicitly lossless, replace the nullable cross-phase
resume payload with phase-specific decisions, normalize edited HTTP framing, validate decision phase and edit
bounds at the application boundary, retain request correlation through provisional responses, and release all
request observations when a channel terminates.

The breakpoint drawer will publish a pending interception before potentially expensive body decoding and prepare
only the active payload with bounded retention. Rule evaluation and ordering will remain additive for future
protocol matchers while avoiding transport/UI coupling. Focused tests will cover the new invariants. The desktop
application will not be launched.

Completed on 2026-08-20. The application contract now distinguishes unchanged body ownership from explicit
replacement and exposes phase-specific request/response decisions. It validates phase, body, header count, and
header bytes before resolving a pending event. Rules use persisted deterministic priority, protocol observations
have bounded eviction plus terminal cleanup, and provisional responses retain the request correlation needed by
the final response.

Production forwarding now has one streaming proxy handler. A protocol-neutral selective HTTP aggregator buffers
only transport candidates; messages crossing the edit limit are replayed and continue streaming instead of
failing or imposing an all-traffic memory cost. Live rules are read per request, so existing Wi-Fi clients are not
closed when rule state changes. Edited messages receive deterministic framing, body-forbidden response handling,
custom reason phrases, and safe trailer preservation. Capture completes after downstream acceptance and releases
ownership on premature channel failure. Response correlation tracks every forwarded request head, so mixed
selected/unselected pipelining and provisional responses cannot consume a later exchange's identity. Absolute
proxy targets and tunneled non-default ports retain the same scheme/authority used by forwarding.

The drawer publishes pending metadata immediately, resolves only the active payload off the Compose thread, and
retains at most one resolved payload. An untouched Forward action preserves original wire bytes. Module
responsibility documents were updated. Disabling global interception immediately continues existing pauses
unchanged and closes the admission race for candidates arriving with the toggle. Focused application, proxy,
interceptor, desktop-data, and breakpoint UI tests plus desktop-product compilation pass in an isolated build
directory. The complete `check verifyArchitectureFoundation` gate then passed with 269 actionable tasks. KNet
was not launched, stopped, or restarted.

## Phase 76: Protocol-Aware Breakpoint Rule Drafts [COMPLETED]

Started on 2026-08-20 after captured GraphQL traffic still opened the breakpoint editor as a generic
HTTP/REST rule with no operation criterion. This phase adds a bounded, protocol-neutral suggestion input to
the existing breakpoint extension SPI, lets semantic extensions contribute validated editor criteria in a
deterministic priority order, and introduces an application use case that prepares one immutable rule draft
from canonical captured traffic. GraphQL will reuse its existing parser to select GraphQL and prefill a single
operation name while batched or anonymous requests remain endpoint-scoped. Traffic presentation will pass only
the resulting generic rule and field values to the existing editor. The desktop application will not be launched.

Completed on 2026-08-20. The breakpoint protocol SPI now accepts one bounded captured-rule suggestion input
and validates extension output through the same compiled criteria contract used by live matching. Suggestions
are evaluated by deterministic semantic priority; HTTP remains the safe application fallback. The new
application use case loads no more than the existing one-mebibyte traffic preview, supports not-yet-durable
pending candidates under the same bound, removes volatile query/fragment data from the endpoint pattern, and
returns one canonical rule plus generic editor values.

GraphQL reuses its existing document parser for both live observations and captured-rule suggestions. A single
named operation preselects GraphQL and fills `Operation Name`; anonymous, batched, body-incomplete, or endpoint-
hint-only GraphQL remains GraphQL with an empty operation criterion. Traffic performs preparation off the UI
thread, opens the dialog only when the immutable draft is ready, and passes the values through the desktop
workspace without importing the GraphQL engine. Focused tests cover different operation names on the identical
`/graphql` endpoint, batch fallback, semantic suggestion transport defaults, HTTP fallback, and Traffic state
wiring. Application/protocol checks, Traffic JVM tests, desktop-product compilation, module documentation,
module boundaries, UI runtime isolation, Kotlin-first source verification, composition ownership, and the
complete architecture-foundation gate all pass. KNet was not launched, stopped, or restarted.

## Phase 77: Unified Semantic Request Method Presentation [COMPLETED]

Started on 2026-08-20 after the live-interception queue and Traffic method column continued to render the HTTP
transport verb (`POST`) for GraphQL requests even though API Studio already exposed a semantic `GQL` identity.
This phase will generalize the existing request-descriptor strategy input so authored, pending, and captured
requests use one ordered descriptor pipeline. Traffic rows will retain the real HTTP method for filtering and
transport behavior while rendering a separate semantic method label. Persisted semantic annotation kinds will
be observed in one bounded batch rather than rereading request bodies for every visible row, and pending
breakpoints will use bounded body previews plus the matched rule's protocol identity. New protocol formats will
therefore add a descriptor strategy and semantic inspector/extension without changing either UI renderer. The
desktop application will not be launched.

Completed on 2026-08-20. The request descriptor contract now lives in the neutral `domain.request` boundary and
accepts protocol-neutral request metadata, an owned bounded body preview, and an optional open semantic-kind hint.
API Studio, pending breakpoint cards, and Traffic rows all resolve through the same ordered descriptor strategies.
The canonical HTTP method remains unchanged for transport behavior and method filters, while `displayMethod` renders
the semantic badge (`GQL` for GraphQL today). Captured semantic annotations are observed in one bounded Room query,
and pending breakpoint descriptors are resolved asynchronously so neither list waits for protocol recognition. The
desktop composition root owns the descriptor multibindings, so another format adds a prioritized strategy and its
semantic inspector/extension without changing the Traffic table or intercept queue. Domain, formatter, Traffic,
breakpoint-manager, data-desktop, API Studio, application, product compilation, and all architecture-foundation gates
pass. KNet was not launched, stopped, or restarted.

## Phase 78: Overflow-Aware Traffic and Dropdown Scrollbars [COMPLETED]

Started on 2026-08-20 to make scroll position and overflow discoverable in the Traffic table and every shared KNet
dropdown variant. This phase adds one theme-aware vertical scrollbar primitive in `:ui:core`, renders it only when
the attached scroll state can actually move, and reuses it for single-select, multi-select, searchable dropdowns,
and the virtualized Traffic table. Scrollbars overlay the trailing edge without changing table columns, popup width,
or list ownership. The desktop application will not be launched.

Completed on 2026-08-20. `:ui:core` now owns one theme-aware `KNetVerticalScrollbar` with adapters for finite
`ScrollState` and virtualized `LazyListState` content. Both adapters derive visibility from measured scrollability,
so no scrollbar is composed when content fits. Single-select, multi-select, and searchable KNet dropdowns reuse the
primitive at their popup trailing edge, and the Traffic table overlays the same primitive on its existing lazy list.
Shared UI tests cover the overflow visibility rules; `:ui:core:jvmTest`, `:ui:desktop:traffic:jvmTest`, Traffic
compilation, module boundaries, module documentation, UI runtime isolation, composition ownership, Kotlin-first
sources, and the complete architecture-foundation gate pass. KNet was not launched, stopped, or restarted.

## Phase 79: Single-Click Dropdown Handoff [COMPLETED]

Started on 2026-08-20 after focus-owning dropdown popups required one click to dismiss the active menu and a second
click to open a neighboring dropdown. This phase replaces per-popup pointer ownership with one composition-scoped
dropdown expansion coordinator. Dropdown popups will retain keyboard and outside-click dismissal without stealing
focus from another anchor; an anchor click will atomically close the previous owner and open the new owner. The same
coordinator will cover single-select, multi-select, and searchable KNet dropdowns, enforce one expanded dropdown at a
time, and preserve one-click closing on the currently active anchor. The desktop application will not be launched.

Completed on 2026-08-20. `KNetTheme` now provides one composition-scoped `DropdownExpansionCoordinator`, and each
single-select, multi-select, or searchable dropdown owns a disposable expansion token through the shared
`DropdownExpansionState`. Opening a token atomically closes the previous owner; toggling the active token closes it.
Dropdown popups are non-focus-stealing, allowing the same native pointer event to reach a neighboring anchor. Popup
outside dismissal defers only its identity-checked coordinator release until the next frame, preventing stale
dismissal from closing a newly opened owner while preserving the existing one-click active-anchor close behavior.
The obsolete focusable popup anchor proxy and its custom layout were removed. Shared UI tests cover ownership
handoff, active-owner toggle, and stale-release isolation; all dropdown-consuming desktop modules compile,
`:ui:core:jvmTest`, product compilation, and the complete architecture-foundation gate pass. KNet was not launched,
stopped, or restarted.

## Phase 80: Active Dropdown Header Click-Through Guard [COMPLETED]

Started on 2026-08-20 after the non-focus-stealing popup correctly enabled one-click handoff but exposed a desktop
pointer timing edge case on the active header: outside dismissal occurs on press, while the underlying anchor click
arrives on release, after the one-frame ownership grace period, and reopens the same dropdown. This phase moves the
guard into `DropdownExpansionCoordinator` as an identity- and monotonic-time-based click-through dismissal marker.
The next toggle from the dismissed owner will be consumed as the close action, while a different owner can still open
immediately. All dropdown variants will use coordinator toggling for pointer activation. The desktop application will
not be launched.

Completed on 2026-08-20. Popup dismissal now records an identity-scoped monotonic marker for 500 ms in the shared
`DropdownExpansionCoordinator`. The matching owner's next pointer toggle consumes that marker and remains closed;
a different owner clears the marker and opens immediately. `DropdownExpansionState` no longer relies on a one-frame
coroutine delay, and searchable arrow activation now uses the same coordinator toggle as the standard and
multi-select anchors. Regression tests cover same-owner press/release suppression, different-owner immediate handoff,
active-owner closing, and stale-owner isolation. `:ui:core:jvmTest`, all product UI compilation, product compilation,
and the complete architecture-foundation gate pass. KNet was not launched, stopped, or restarted.
