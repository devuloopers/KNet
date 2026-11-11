# Network Conditions and Protected Traffic completion plan

Current scope update (2026-11-10): Smart Compatibility was removed by user decision. Explicit destination rules,
saved selection and TLS diagnostics remain; automatic learning/trial/Forget requirements are superseded. See
[the retirement record](smart_compatibility_retirement.md). Historical execution counts below predate this removal;
the broader outstanding Network Conditions/platform work is not closed by it.


Created: 2026-11-10

Status: **IN PROGRESS**

Scope: complete the implementation and non-physical qualification of Phases 133–136, including Smart Compatibility,
so the remaining acceptance work is explicitly physical testing. This plan follows the
[readiness audit](network_conditions_protected_traffic_readiness_audit.md); it does not mark the findings fixed.

## Completion contract

“Software complete; physical qualification pending” requires all of the following:

- Every accepted requirement has a production implementation, platform capability state, relevant positive,
  negative, boundary and lifecycle tests, and retained passing evidence from an enforced CI gate.
- All audit findings A1–A6 are closed. All applicable non-physical assertions in the 114-case Network Conditions
  ledger, Protected Traffic acceptance gates and Smart Compatibility matrix have evidence. Previously passing
  cases remain regression requirements; the superseded two-card layout is not reintroduced.
- Installable products, native adapters, migrations, authenticated control paths and operational diagnostics are
  implemented. Native compilation, linking, packaging and signing checks are software delivery work.
- Scenarios, profile import/export and authenticated automation are delivered, rather than deferred behind the
  physical-testing milestone. Existing user-facing controls keep the KNet theme and shared components.
- No mandatory software case is skipped, mocked into a passing platform claim, or left as a TODO. Test counts
  and line coverage do not substitute for requirement coverage.
- Physical promotion remains separate. Encrypted flow progress never proves a purchase, subscription lookup,
  login, pinning, decrypted HTTP/3 or application identity that the provider does not expose.

Smart remains enabled by default. Explicit policy remains authoritative. The service-preset catalogue stays
removed, and imported historical exceptions retain their tested migration and precedence behavior.

Existing non-goals remain: media/UDP payload decoding, generic QUIC decryption, semantic HTTP/3 inspection,
radio emulation and exact kernel TCP congestion-control emulation are not added by this completion plan. Platform
attribution/routing limitations must have implemented fallback and capability states rather than invented evidence.

## Platform scope and prerequisites

| Target | Software delivery included | Physical work left for the final stage |
| --- | --- | --- |
| Desktop JVM on macOS, Windows and Linux | Conditions/protected proxy behavior, protocols, UI, persistence, packaging and CI | Packaged-client, OS integration, streaming and recovery acceptance |
| Android Companion | Live packet conditions, packet rules, original-flow owner resolution where supported, opaque flow reporting, installable APK | VPN/radio/Wi-Fi lifecycle, real streaming, Play Billing and other protected clients |
| iOS/iPadOS Companion | Native TCP/UDP packet-provider integration, shaping, metadata, capability-based attribution, signed app/extension build | Entitled device routing, StoreKit, streaming and lifecycle acceptance |
| Desktop packet mode | Permission-managed native packet/flow adapters for supported desktop products; macOS signed Network Extension plus authenticated JVM bridge | Real activation/authorization, competing VPNs, routing, sleep/wake and crash recovery |

Before adapter work, record the exact backend and supported OS/version/architecture matrix for macOS, Windows
and Linux packet mode. This is an implementation deliverable under NC-DESK-001, not permission to silently exclude
an existing desktop target from the completion goal. Reuse established platform facilities rather than inventing
a new packet driver; verify distribution terms, packaging, signing and required privileges before integration.

Apple accounts, entitlements, development/distribution profiles, native toolchains and any driver/helper signing
dependencies are prerequisites. Missing prerequisites are recorded as **external delivery blockers**, with the
specific unfinished build/check identified. They cannot be relabelled “physical test pending.” Credentials stay
in the platform keychain/CI secret system. Installation, VPN consent and system-extension activation are physical
acceptance actions; preparing and validating their packages and state machines is software work.

## Execution order and tracking

Work packages C00–C04, C08, C11 and C12 are **IN PROGRESS**; the other packages remain pending. Implement each production slice with its regression tests and update
the master phase board immediately. Preserve the current working tree and previously qualified behavior.

| Order | Work package | Depends on | Primary coverage |
| --- | --- | --- | --- |
| C00 | Requirement ledger, platform decisions and prerequisites | Existing audit | All requirements and deployment blockers |
| C01 | Android UDP association ownership and teardown | C00 | A1; packet lifecycle |
| C02 | Smart TLS evidence and invalidation | C00 | A2; Phase 136 evidence/lifecycle |
| C03 | Atomic policy, storage and shared packet contracts | C00 | NC-DOM/DB; versioned policy |
| C04 | Live Android policy and packet rule execution | C01, C03 | A3; Phase 134 |
| C05 | Native source attribution, opaque reporting and measured telemetry | C01–C04 | A4; Phase 135.3/4/7 |
| C06 | Complete iOS/iPadOS provider implementation | C03–C05 contracts | Phase 134/135.5 |
| C07 | Desktop native packet/system-capture adapters | C00, C03–C05 contracts | Phase 134/135.6 |
| C08 | Complete protocol and product integration | C02–C07, where applicable | Phase 133.4; protected transport matrix |
| C09 | Scenarios, profile exchange and automation | C03–C07 | A5; Phase 133.5 |
| C10 | Persistence, Traffic, UI and accessibility closure | Relevant earlier slices | Remaining user workflows and evidence |
| C11 | Performance, load, soak and resource qualification | C08–C10 | Current shapers and new native paths |
| C12 | CI enforcement, packaging and independent completion audit | All software packages | A6; final software exit gate |
| P01 | Physical acceptance only | C00–C12 verified | Independent device/OS promotion |

C02 and C03 are independent after C00; native adapters can consume stable contracts while protocol work progresses.
This dependency table describes sequencing, not a requirement to use parallel agents.

Current implementation evidence is recorded in [the execution record](qualification/network_protection_execution.md).
The Android association owner, supported JSSE certificate-flight observer, live global Android update loop and
new TCP lifetime regressions are implemented. Packet selector/revision contracts and native adapter delivery are
still open. Real 60-second aggregate-rate and 500 MiB incremental-transfer measurements have passed an initial
run; these do not close the complete bidirectional/UDP/soak performance matrix. New CI gate wiring is local and
has not yet been exercised on hosted runners.

### C00 — Establish a complete, reviewable acceptance ledger

- [ ] Create one machine-checkable ledger entry for each of the 114 existing NC IDs, each Protected Traffic
  acceptance bullet and each of the 14 Smart matrix categories. Split mixed software/physical assertions.
- [ ] Assign every open assertion a work package, production path, test fixture, exact task/report and status.
  Retain existing passing evidence; NC-UI-009 remains superseded by the approved workspace.
- [ ] Resolve native adapter/translator integration choices, original-tuple callback strategy, supported TLS
  provider presentation evidence, signing requirements and ownership of shared bandwidth budgets before coding.
- [ ] Specify what “global” shares across connections, streams, TCP/UDP, devices and providers. A shared advertised
  cap must have one budget owner or bounded coordinated allocation; do not silently give each data plane the full
  limit. Document any intentional isolation in existing evidence/diagnostics without adding unrelated controls.
- [ ] Inventory signing/toolchain dependencies and prepare reproducible workloads and physical-test instructions.

Exit: every accepted requirement is assigned; scope and external blockers are explicit, with no unowned backlog.

### C01 — Fix Android UDP teardown before extending the data plane

- [x] Give each SOCKS UDP association an owned child job, queues, timers and terminal registry state. Closing the
  control socket closes admission, cancels delayed upload/download work and prevents registry recreation.
- [x] Make queue-slot/socket release idempotent through cancellation, send failure, eviction, VPN stop and startup
  failure. Reject late callbacks by association/session identity. Keep DNS/control traffic reachable.
- [ ] Convert the temporary audit reproduction into a permanent correctness regression asserting **no** late
  upstream creation/delivery. Add download-delay close, close/create/evict races, repeated close, queue exhaustion
  and gateway shutdown tests. Assert queue counters and owned resources return to baseline.

Exit: A1 closes with an automated regression, not merely a shorter delay or an exception suppressed after close.

### C02 — Prove conservative Smart TLS evidence and lifecycle

- [ ] Replace the SNI-context boolean inference with typed downstream side/stage/presentation evidence. Prove the
  generated certificate flight was submitted successfully to the downstream transport using supported TLS-provider
  instrumentation. Context selection or arbitrary encrypted output alone is insufficient.
- [ ] Establish a working, tested presentation mechanism for supported TLS 1.2 and TLS 1.3 providers. If a provider
  cannot supply reliable evidence, keep it explicitly unknown/suggestion-only; do not call that a completed positive
  automatic-learning path for a provider promised as supported. Treat any required provider change as software work.
- [ ] Cover early certificate alerts before presentation, certificate generation/write failure, unknown provider
  formats, malformed/slow ClientHello, ALPN/version failures, upstream trust/connect/DNS failures, ordinary EOF/reset,
  HTTP errors, cancellation, queue pressure and deliberate simulator faults. None may create an automatic exclusion.
- [ ] Finish CA replacement, pairing/revocation, workspace/session change and policy-generation invalidation, including
  callbacks racing disable/edit/Forget/expiry. Preserve immutable opening evidence for existing flows.
- [ ] Complete trial/cooldown/expiry/setup-burst boundary and concurrent admission tests at production limits. Prove
  one independent retry, no request replay, no application-success claims and bounded observation/history resources.

Exit: A2 closes; supported positive fixtures learn only after the required evidence, and inconclusive cases stay safe.

### C03 — Finish atomic configuration, persistence and packet contracts

- [ ] Add versioned immutable packet policy snapshots with monotonic revision/session identity, enabled state,
  profiles, selector rules, explicit capabilities, applicability evidence and acknowledgements.
- [ ] Support protocol, exact/wildcard domain with reliable attribution only, IPv4/IPv6/IP/CIDR and port matching.
  Specify deterministic precedence and fallback consistent with existing proxy rules. HTTP/GraphQL/gRPC operation
  semantics apply only where decoded; never infer operations from opaque datagrams.
- [ ] Prove normalized selectors, references, profile deletion/reset, built-in evolution and corrupt-row recovery.
  Test concurrent writes, snapshot atomicity and cancellation/close during a transaction.
- [ ] Migrate all supported retained schemas into the new current schema, preserving profiles, rules, Smart settings,
  imported legacy exceptions and captures. Test fresh/migrated equivalence and saved-off restart. Never recreate
  removed preset controls/catalogue or destructively reset a database to make migration pass.
- [ ] Negotiate older peers explicitly: supported legacy behavior with honest status or a clear unsupported-feature
  result. Reject duplicate/unknown invalid fields, stale revisions, replay, oversized input and spoofed identities.

Exit: one valid policy revision reaches a consumer atomically; no transient invalid configuration is observable.

### C04 — Implement live Android packet conditions and matching

- [ ] Refresh policy through an authenticated, session-owned bounded update channel with applied-revision feedback.
  Do not fetch once at VPN start or add one polling job per connection. Under normal connectivity, acknowledge a
  live change within one second; tests inject transport timing and cover disconnect/recovery explicitly.
- [ ] Apply live bandwidth, utilization, latency, jitter, offline/recovery, packet loss/duplication/reordering and
  selected-rule changes to active/queued work without restarting the VPN, duplicate delivery or stale credit.
- [ ] Resolve rules using trustworthy destination/transport evidence. Handle DNS association expiry/conflicts,
  shared IPs, absent/ECH-hidden names and migration without turning inferred domains into certain attribution.
- [ ] Qualify independent directions, global/rule sharing, mixed proxy/packet traffic, fairness and exact-once
  shaping. Exclude companion carrier, discovery, pairing, certificates, policy updates and required DNS traffic.
- [ ] Implement/test bounded IPv4/IPv6 forwarding, payload limits, truncation rejection, configured MTU/fragment
  policy, socket protection, loop prevention, shutdown and deterministic seeded distributions.

Exit: desktop controls and rule edits affect an already-running VPN; A3 and applicable NC-AND software cases close.

### C05 — Complete attribution, opaque flow visibility and measured telemetry

- [ ] Preserve original flow tuples at the native translator boundary rather than resolving translated loopback
  sockets. Pin/document any maintained native bridge change and verify its ABI and lifecycle.
- [ ] Wire Android owner resolution for supported APIs; test shared UID, multiple users, unknown/removed/updated
  packages, stale tuple reuse and permission/API failures. Unsupported ownership remains destination-only.
- [ ] Carry verified provider/source identity through authenticated capability-negotiated messages. Caller-supplied
  package/bundle labels are never trusted as OS attribution.
- [ ] Report bounded TCP/UDP flow open/progress/terminal events, applied policy revision, monotonic counters, reason
  and source capability without payload transfer. Handle reconnect/replay/duplication, dropped updates and terminal
  events exactly once. Correlate QUIC migration only with adequate evidence; otherwise retain separate flows.
- [ ] Persist and display opaque UDP and evidence-backed QUIC in Traffic. Complete filtering, paging, clear/retention,
  bounded metadata-only export and quick-add through canonical flow records; never turn an opaque flow into a fake
  HTTP/HAR exchange or fabricate HTTP/3 headers/bodies or StoreKit/Binder/XPC requests.
- [ ] Feed actual locally released UDP bytes into measured telemetry. Keep capture lifecycle separate from rate
  accounting; avoid counting both carrier/proxy and inner traffic. Keep the 60-sample graph bounded, coalesced and
  explicit about source coverage, disconnected/stale counters and configured versus measured speed.

Exit: A4's Android attribution/reporting gap closes, and a UDP-only host fixture updates Traffic and measured rates.

### C06 — Complete the iOS/iPadOS packet provider

- [ ] Implement native opaque UDP forwarding/shaping and live policy application alongside the existing TCP path.
  Reuse shared contracts and retain provider-owned bounded memory, recursion protection, DNS and route continuity.
- [ ] Add original-tuple observation and authenticated flow/telemetry delivery before translation. Preserve verified
  bundle identity only in provider modes that supply it; destination-only is the correct full-device fallback.
  Implement the previously planned optional managed/app-proxy attribution path with explicit capability reporting.
- [ ] Exercise native gateway authentication/pinning, malformed input, protected TCP, UDP, cancellation, memory
  pressure, provider crash/restart, endpoint change and stop during delayed work through injectable native boundaries.
  Expand tests beyond start-options parsing and run them on Simulator where supported.
- [ ] Compile/link device and Simulator targets; prepare and validate signed app/extension archives, entitlement
  contents, embedding, provisioning and packaging. Update the native qualification gate and operating instructions.

Exit: the complete provider code and automated tests exist. Actual entitled-device routing/StoreKit results remain P01.

### C07 — Complete desktop native capture/packet adapters

- [ ] Implement the selected permission-managed adapters from C00 for macOS, Windows and Linux supported targets.
  Keep native ownership outside domain/UI code and preserve the JVM explicit proxy as its existing transport path.
- [ ] On macOS, add the signed native Network Extension/system-capture product and authenticated local JVM bridge.
  Verify peer identity, exposed signing/audit metadata, command/event bounds, replay handling and recursion exclusion.
- [ ] Implement explicit installed/authorized/active/failed/unavailable states, platform routing/exclusions, native
  packet/flow shaping, live updates, counters and deterministic rollback after partial startup or peer loss.
- [ ] Add OS-runner tests using injectable permission/route/flow boundaries, real loopback transport and native unit
  tests. Cover helper/provider crash, IPC loss, adapter/version mismatch, upgrades and clean teardown.
- [ ] Build/package the real native artifacts for each supported architecture. Validate signatures/manifests,
  helper/driver prerequisites, bundled dependencies and install/uninstall scripts. An unavailable placeholder is not
  completion of a promised adapter. Leave actual OS authorization/routing/coexistence exercises to P01.

Exit: NC-DESK-001 and Phase 135.6 software delivery close; platform builds and package checks pass on their own runners.

### C08 — Complete protocol and product matrices

- [ ] HTTP/1.1: upload/download, chunking/trailers, reuse, redirects, ranges, progressive media and parallel HLS/DASH
  segments; assert rate, delay, ordering, cancellation and incremental delivery through real pipelines.
- [ ] HTTP/2: negotiated TLS/ALPN and h2c, concurrent streams, aggregate fairness, flow control, reset, trailers and
  independent destinations. ALPN offers alone do not count as negotiated h2 payload evidence.
- [ ] WebSocket: text/binary, fragmentation, control/close frames, compression, long-lived edits and teardown.
  GraphQL WebSocket: operation correlation, next/error/complete, multiplexing, fragmented semantic messages,
  compression, rapid rule churn, timeout and peer-close races.
- [ ] gRPC: unary, client streaming, server streaming and bidirectional streaming, framing, trailers/status,
  cancellation, multiplexing and semantic rule isolation. SSE: events/comments, idle, reconnect, cancellation and
  live edits without whole-body buffering.
- [ ] Protected TLS/raw relay: TLS 1.2/1.3, mTLS, actual h2 passthrough, absent SNI, IP CONNECT and IPv6; fragmented,
  malformed, oversized, slow/timed-out ClientHello, half-close, backpressure and no duplicate first bytes/replay.
- [ ] API Studio and paired-device execution: selected conditions/evidence across every supported protocol, including
  paused capture, unavailable proxy, live edit and shutdown. Prove no unconditioned fallback or double shaping.
- [ ] Exercise opaque QUIC/WebRTC/custom-UDP fixtures without decoding their media or treating encrypted bytes as
  HTTP/3 transactions. Synthetic transport tests complete software behavior; physical media adaptation remains P01.

Exit: all NC-PROTO-001–014 and relevant protected/Smart transport assertions have condition-specific evidence.

### C09 — Deliver remaining accepted simulator features

- [ ] Named timed scenarios: validated reusable steps, monotonic transitions, start/pause/resume/cancel, active-flow
  updates, restart policy and deterministic seed. Test 5 Mbps → 100 kbps → Offline → recovery without timer leaks.
- [ ] Profile import/export: bounded versioned format, round trip, preview/validation, duplicate IDs/names,
  built-in protection, unknown versions/fields, corrupt/truncated input, atomic changes and no secrets.
- [ ] Authenticated local automation: list/select/enable/disable saved profiles and control saved scenarios through
  application use cases. Test authenticated identity/authorization, replay, size limits, concurrent edits, safe
  errors, shutdown and unthrottled control access. No unauthenticated remote listener.
- [ ] Add only the controls needed for these accepted features in the existing Network Conditions workspace and
  reusable KNet editors; no additional dashboard, service catalogue, graph library or unrelated redesign.

Exit: A5 and NC-FUT-001–003 close with actual production workflows, migrations if needed and integration tests.

### C10 — Finish Traffic, persistence and UI interaction qualification

- [ ] Assert exact global/manual/Smart/imported origin, rule/profile/revision and unavailable/disabled evidence for
  HTTP and opaque rows. Test rotation, clear/retention, immutable history after Forget and terminal persistence races.
- [ ] Complete Traffic navigation/prefill/duplicate-disabled-rule behavior for host/port, HTTP, GraphQL operation,
  gRPC service/method, WebSocket, GraphQL WebSocket and opaque packet scopes using stored trustworthy evidence.
- [ ] Test profile/rule/scenario editors by typing and keyboard/pointer actions: focus stability, visible scrollbar
  spacing, default No throttling, Save/Cancel reachability, validation/retry, draft preservation under external edits,
  deletion confirmation, empty/loading/error and unavailable adapter guidance.
- [ ] Finish graph accessibility and real shaper-to-sampler integration, current-label sizing, theme variants,
  minimum supported window, large text and long/localized labels. Use shared responsive/motion/overflow tokens.
- [ ] Finish Smart inspector rapid selection/resize/close/removal races, tab/scroll preservation, Escape/focus return,
  reduced motion and all action/expiry/cooldown/setup-guidance states. Do not add automatic initial selection.

Exit: remaining NC-TRAF/UI/DB assertions and Smart capture/actions/motion categories close.

### C11 — Prove performance and bounded resource use

- [ ] Run sustained real I/O at 100,000 bit/s for at least 60 measured seconds after startup, in each direction and
  across concurrent TCP/UDP and multiplexed streams. Initial target: payload rate within ±5% over that window.
  Record startup allowance, payload versus wire accounting and scheduler/environment variance; do not weaken a
  failing threshold without a documented contract review.
- [ ] Offer at least 500 MiB incrementally to slow/offline/concurrent consumers without allocating the whole test
  payload. Assert 8 MiB per-flow and 64 MiB aggregate proxy queue limits, datagram limits, deterministic rejection,
  exact byte/hash correctness where delivery is expected and resource release on every terminal path.
- [ ] Record warmed heap/allocations/RSS and retained objects for idle, bypassed, 100 kbps and high-throughput traffic,
  with graph visible/hidden and at maximum bounded Smart history. Set executable budgets from capacity accounting
  and the measured baseline before accepting results; require a stable plateau instead of growth per flow/cycle.
- [ ] Measure disabled-mode latency/throughput against a repeatable plain-proxy baseline and retain the agreed
  regression budget. Run at least a 30-minute scheduled/release soak with repeated edits and long-lived WS/SSE/gRPC
  and UDP flows; fail on increasing queues, timers, listeners, descriptors, sockets or retained payloads.
- [ ] Prove immediate software-scheduled disable/offline/recovery transitions, no stale bursts/starvation and no
  simulated-fault learning. Use deterministic clock tests for arithmetic and real sockets for delivery measurements.

Exit: NC-PERF-001–004 and remaining engine/telemetry resource assertions close with reproducible measured reports.

### C12 — Enforce verification and perform an independent completion review

- [ ] Wire the existing Network Conditions and Smart gates into CI and desktop release checks. Include explicit
  condition-specific SSE tests, native packet-provider tests and new protected-traffic suites in appropriate gates.
- [ ] Add platform jobs for native Android/iOS/macOS/Windows/Linux artifacts and permission/provider state tests;
  keep simulator/loopback results labelled with their actual scope. Separate quick deterministic checks from
  scheduled/release load/soak gates, with required successful evidence for the release commit.
- [ ] Retain JUnit/native reports, coverage reports for critical policy/state/scheduler branches, benchmark/soak
  evidence, manifests, signatures and exact commands. Coverage measures assist review; they cannot close missing
  requirements. Fail mandatory skipped cases or missing expected reports rather than accepting a smaller suite.
- [ ] Rerun the full relevant gates with forced execution against the final tree. Compare fresh distinct task reports,
  excluding stale outputs and duplicate totals. Build desktop distributables, Android packages and Apple archives.
- [ ] Re-audit all A1–A6, 114 NC entries, Protected acceptance assertions and 14 Smart categories against production
  paths and executable evidence. Check documentation, migration compatibility, privacy and architecture boundaries.
- [ ] Update capability states to distinguish software readiness from physical qualification. Complete KDoc/module
  docs and run architecture verification, local document checks and `git diff --check`. Do not launch the desktop
  app in automated agent sessions; package/UI acceptance requiring the live app belongs to P01.

Exit: no non-physical requirement or external delivery blocker is open. Only then record **SOFTWARE COMPLETE;
PHYSICAL QUALIFICATION PENDING**. A current passing count alone cannot authorize this status.

## Traceability to the existing 114-case ledger

The detailed assertions remain in [the original ledger](network_conditions_test_gap_report.md). The C00 execution
ledger must preserve each individual ID and evidence link. This range mapping accounts for all 114 entries.

| Existing IDs | Count | Primary packages | Physical-only remainder |
| --- | ---: | --- | --- |
| NC-DOM-001–010 | 10 | C03, C04 | None |
| NC-DB-001–009 | 9 | C03, C10 | None |
| NC-ENG-001–024 | 24 | C01, C04, C08, C11 | None; real socket tests run without physical clients |
| NC-PROTO-001–014 | 14 | C04–C08 | Real-provider acceptance separately in P01 |
| NC-TRAF-001–009 | 9 | C05, C10 | None |
| NC-UI-001–019 | 19 | C09, C10 | NC-UI-009 remains superseded; native live-app acceptance in P01 |
| NC-TEL-001–005 | 5 | C05, C10, C11 | None |
| NC-PERF-001–004 | 4 | C11 | No deferral of automated soak to physical testing |
| NC-AND-001–013 | 13 | C01, C04, C05 | 010–012 device workloads; real-device portions of 013 |
| NC-IOS-001–002 | 2 | C06, C12 | 002 and actual entitled-device routing in 001 |
| NC-DESK-001–002 | 2 | C07, C12 | 002 real OS routing/authorization/coexistence |
| NC-FUT-001–003 | 3 | C09 | None |

Protected Traffic coverage: 135.1 → C03/C10; 135.2 → C02/C08/C11; 135.3 → C05/C10;
135.4 → C01/C04/C05; 135.5 → C06; 135.6 → C07; 135.7 → C04–C07; 135.8 → C08/C10–C12.
Historical service-group requirements are superseded by approved preset retirement; migration preservation remains
mandatory, while recreating the retired catalogue is excluded.

Smart matrix coverage: defaults/migration and precedence → C02/C03; positive evidence/false positives/uncertainty →
C02/C08; scope/isolation → C02/C05–C07; lifecycle/limits → C01/C02/C11; relay correctness/conditions → C04/C08;
capture/storage and actions/UI/inspector motion → C05/C10; composition → C03/C12.

## P01 — The only work left after software completion

- [ ] Packaged desktop: inspectable, unfamiliar pinned and mTLS clients; HLS/DASH/progressive transfer at 100 kbps;
  live edits, bypass baseline, fairness, long sessions and actual OS networking recovery.
- [ ] Android: VPN consent/start/stop/process death, IPv4/IPv6, Wi-Fi/radio handoff, DNS/MTU/fragmentation, custom UDP,
  WebRTC/STUN/TURN/ICE, QUIC/HTTP/3 streaming/migration, Play Billing license-tester workload and unrelated protected
  clients. Verify actual owner attribution where available and source-unavailable behavior otherwise.
- [ ] iPhone and iPad independently: signed provider installation/VPN routing, StoreKit sandbox/TestFlight loading,
  purchase/cancel/restore/background completion, protected clients, UDP/WebRTC/QUIC and network/lifecycle recovery.
- [ ] Desktop native adapters on each supported OS/architecture: actual install/authorization, transparent system
  traffic, source attribution where exposed, competing VPN/security software, route changes, sleep/wake, crash,
  upgrade and uninstall recovery.

Each run records commit/build, device/OS, provider/mode, workload, policy revisions, offered/delivered bytes, actual
timing, memory and recovery evidence. Signing preparation, missing source code, absent tests or missing build
dependencies cannot be moved into this checklist. Keep platform promotion pending until its own results pass.

## Plan validation

Planning changes only: verify requirement/ID mapping, defaults, local document references, consistent statuses
and `git diff --check`. Do not rerun production test gates until implementation work changes executable behavior.
