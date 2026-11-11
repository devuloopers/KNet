# Network Conditions and Protected Traffic readiness audit

Date: 2026-11-10

Scope: the current working tree, including the uncommitted Smart Compatibility, preset retirement, and Network
Conditions UI work. This is an implementation/test audit; it does not constitute physical-device qualification.
No production implementation was changed during the audit.

Follow-up: the [completion plan](network_conditions_protected_traffic_completion_plan.md) maps these findings and
the full accepted scope to software work packages, with physical acceptance retained as a separate final stage.

## Verdict

1. **Ready for limited exploratory testing of implemented proxy paths; not ready for full planned-feature physical
   acceptance.** The JVM proxy can be exercised with real HTTP/TLS workloads, condition profiles/rules and Smart
   reconnects. Android global UDP is experimental. App attribution, live packet-policy updates, opaque UDP reporting,
   and the additional Darwin packet adapters still have implementation gaps.
2. **All necessary non-physical tests have not been added or completed.** Passing the current gates does not close
   the existing acceptance matrix. Software defects, missing condition-specific protocol tests, performance tests,
   native packet-provider tests, and some lifecycle/security boundary cases remain.

These conclusions supersede any interpretation of the earlier 954-test result as “only physical testing remains.”
The earlier qualification report already limits that count to the assertions actually present in those suites.

## Readiness by path

| Path | Current readiness | Boundary |
| --- | --- | --- |
| Desktop explicit proxy, HTTP/TLS conditions and manual protected tunnels | Suitable for controlled exploratory tests | Complete protocol, load, shutdown and baseline qualification remains open. |
| Smart Compatibility on routed TCP/TLS | Suitable for controlled exploratory tests with the evidence limitation below | Recognizes supported downstream alert strings; does not detect every pinned client or guarantee a business operation succeeds. |
| Android Companion proxied TCP/TLS | Destination-based exploratory tests | Original application tuple is not supplied by the active translator; app/package policy cannot be accepted as implemented end to end. |
| Android global opaque UDP shaping | Experimental; address association-close cleanup before acceptance tests | Configuration is a VPN-start snapshot. No live refresh or packet selector rules. |
| Android opaque UDP/QUIC Traffic records | Not ready | Local UDP forwarding has no authenticated desktop flow lifecycle/counter publication. |
| iOS/iPadOS proxied TCP/TLS | Limited destination-only path; entitled deployment must first be available | Previous physical build attempts failed for missing Xcode account/profiles. Simulator/shared tests cannot replace signed device execution. |
| iOS/iPadOS packet conditions, WebRTC/QUIC UDP | Not ready | Native SOCKS gateway accepts CONNECT only; packet scheduling/UDP implementation is still missing. |
| macOS transparent system capture/packet conditions | Not ready | Signed native Network Extension and authenticated IPC adapter remain planned work. The JVM explicit proxy is a separate working path. |

## Implementation findings

### A1 — P1: delayed Android UDP work can outlive its association

`LocalSocks5Gateway.handleUdpAssociation` closes the association relay on control-channel close, while upload and
download delay jobs are children of the gateway-wide scope. `forwardUdp` waits and then acquires/creates an upstream
socket without checking whether that association has closed. `UdpUpstreamFlowRegistry.closeAll` clears current
sockets but does not prevent a subsequent `getOrCreate` from opening another one.

Consequences include queued work surviving association teardown and attempts to create/use upstream resources after
the association is gone. This is a software lifecycle issue, not something that requires a physical device to test.

Sources:

- [association and job ownership](../connectivity/companion/src/androidMain/kotlin/com/devuloopers/knet/companion/connectivity/transport/LocalSocks5Gateway.kt#L233)
- [delayed upstream creation](../connectivity/companion/src/androidMain/kotlin/com/devuloopers/knet/companion/connectivity/transport/LocalSocks5Gateway.kt#L298)
- [registry cleanup](../connectivity/companion/src/androidMain/kotlin/com/devuloopers/knet/companion/connectivity/transport/LocalSocks5Gateway.kt#L458)

Required regression: queue delayed packets, close the SOCKS control connection before their deadlines, and assert
no late upstream creation/delivery plus immediate release of queue slots and association resources. Repeat for VPN
shutdown, download delays and concurrent close/eviction.

**Reproduced:** a temporary Android-host test queued a UDP packet with 1,000 ms latency, closed its association
control socket after 200 ms, and observed the protected upstream-socket creation afterward. The probe completed
successfully because it asserted reproduction of the defect; that is not a passing correctness test. The temporary
source was removed after execution. Its source and result are retained at `/tmp/knet-audit-UdpAssociationCloseProbeTest.kt`
and `/tmp/knet-audit-udp-close-probe.xml` for this audit session.

### A2 — P1: Smart certificate-presentation evidence is weaker than the approved contract

`TlsInspectionOutcomeHandler` sets a flag on successful `SniCompletionEvent`. The classifier turns a recognized
received certificate alert plus that flag into `certificatePresented = true`. A selected/installed TLS context
does not establish that a generated certificate was actually sent to the client. The existing embedded-channel
test sets this flag without any certificate bytes being written.

This is a confirmed evidence/contract gap. It is not a claim that every normal certificate rejection is wrongly
classified, and the implemented reason correctly avoids claiming confirmed pinning. The plan requires actual
presentation evidence and suggestion-only handling when that evidence is unavailable.

Sources:

- [SNI flag](../engine/proxy/src/main/kotlin/com/devuloopers/knet/engine/proxy/tls/TlsInspectionOutcomeHandler.kt#L15)
- [eligibility evidence conversion](../engine/proxy/src/main/kotlin/com/devuloopers/knet/engine/proxy/tls/TlsInspectionFailure.kt#L17)
- [current synthetic-event assertion](../engine/proxy/src/test/kotlin/com/devuloopers/knet/engine/proxy/tls/TlsInspectionFailureClassifierTest.kt#L25)
- [approved evidence contract](smart_compatibility_implementation_plan.md#evidence-and-eligibility)

Required regression: certificate context installed but no certificate flight sent must remain inconclusive; cover
early peer alerts, failed certificate generation/write, supported real downstream rejection, and simulated faults.

### A3 — P1: Android packet policy does not support live edits or destination selectors

The start-inspection use case fetches `PacketConditionConfiguration` once. The gateway constructs one runtime with
an immutable configuration. The wire model contains only one global profile's numeric settings, and the desktop
mapper reads only `globalProfileId`; it does not carry domain/IP/CIDR/port rules.

Therefore changing the desktop global speed/Off/Offline controls does not update UDP conditions in an already
running VPN. Current UDP tests must set the desired global profile before starting/restarting the session.

Sources: [startup fetch](../application/companion/src/commonMain/kotlin/com/devuloopers/knet/companion/application/usecase/InspectionUseCases.kt#L92),
[immutable runtime](../connectivity/companion/src/androidMain/kotlin/com/devuloopers/knet/companion/connectivity/transport/AndroidPacketConditionRuntime.kt#L28),
[global-only mapping](../products/desktop/src/jvmMain/kotlin/com/devuloopers/knet/products/desktop/di/networkconditions/NetworkConditionsBindings.kt#L58).

This is explicitly unfinished planned work, tracked as NC-AND-004/005, rather than a missing unit test alone.

### A4 — P1: universal protected-flow visibility and attribution are unfinished

The production Android forwarder reports `TRANSLATED_SOCKET_ONLY`; the owner resolver is not connected to a native
original-tuple flow-open callback. The iOS packet translator has the same original-source limitation. Android UDP
uses protected local sockets and does not publish its lifecycle to desktop Traffic. The iOS SOCKS gateway rejects
non-CONNECT commands, and the macOS transparent Network Extension remains unimplemented.

The desktop throughput graph observes the desktop proxy engine's released-byte counters. Android's locally
forwarded UDP bytes are not fed into that telemetry, so this graph cannot currently validate a QUIC/WebRTC-only
device workload's actual rate. Packet telemetry integration needs its own implementation and tests.

Sources: [Android capability](../connectivity/companion/src/androidMain/kotlin/com/devuloopers/knet/companion/connectivity/transport/AndroidTunForwarder.kt#L57),
[Android local UDP path](../connectivity/companion/src/androidMain/kotlin/com/devuloopers/knet/companion/connectivity/transport/LocalSocks5Gateway.kt#L298),
[iOS command restriction](../products/companion/iosPacketTunnel/src/iosMain/kotlin/com/devuloopers/knet/companion/packettunnel/network/LocalSocks5Gateway.kt#L177),
[remaining platform delivery](protected_traffic_passthrough_implementation_plan.md#delivery-slices).

Domain-scoped TCP/TLS learning can still be tested. It cannot establish per-package handling, UDP visibility,
StoreKit routing coverage, or capture of system services outside the selected provider's routing path.

### A5 — P2: additional accepted Network Conditions features remain planned

Named timed scenarios, profile import/export and authenticated automation have no completed implementation/test
delivery. They are listed as outstanding in the implementation checkpoint and NC-FUT-001/002/003. They must remain
separate from the currently implemented global/rule/profile simulator when describing feature completeness.

### A6 — P1: dedicated feature qualification is not enforced by the current CI workflows

The root build declares both `networkConditionsQualification` and `smartCompatibilityQualification`, but neither
is invoked by the checked-in CI or desktop-release workflows. Existing protocol/product jobs overlap with some
of their suites; this does not establish execution of the dedicated Network Conditions/Protected Traffic UI suites
or the complete intended feature gate on every change. SSE has a separate CI gate, but that still lacks the
condition-specific SSE matrix described below.

Sources: [feature task definitions](../build.gradle.kts#L841), [CI protocol job](../.github/workflows/ci.yml#L54),
[desktop CI job](../.github/workflows/ci.yml#L94), [release gates](../.github/workflows/release-desktop.yml#L83).

Required follow-up: wire the feature gates into the appropriate platform jobs and retain their test reports,
including native packet tests once their linkage and implementation are ready.

## Remaining non-physical test evidence

The table lists material gaps; it does not imply that every behavior in each row is untested. Existing tests should
be retained, and missing boundary assertions added rather than duplicating their implementation.

| Area | Existing evidence | Still required |
| --- | --- | --- |
| Sustained speed and resource bounds | Virtual-clock one-minute schedules, exact queue bounds, small real-socket 100 kbps regressions | At least 500 MiB offered to slow/offline/concurrent consumers; sustained real I/O tolerance and fairness; heap/allocation measurements; disabled baseline; long-lived soak. |
| HTTP/1 and HTTP/2 | Host/semantic matching, HTTP/2 metadata, incremental unit scheduling | Condition-specific uploads/downloads, byte ranges and parallel HLS/DASH segments, multiplexed fairness, backpressure, cancellation and trailers over real protocol pipelines. |
| WebSocket and GraphQL WebSocket | Reassembly/identity, operation isolation, compression, timeout and cancellation slices | Full frame/control/close matrix, fragmented semantic messages, error completion, long-running rate edits and peer-close/rule-churn races. |
| gRPC and SSE | gRPC service/method matching; general protocol tests | Condition-specific unary/client/server/bidirectional gRPC streaming and SSE reconnect/idle/cancel/order/timing matrices. The two feature gates do not include `:engine:sse:test`. |
| Shaper-to-graph integration | Sampler derives 100 kbps from released byte deltas; rendering labels measured throughput | One real shaper-to-sampler/graph assertion, observer restart/lifecycle and allocation evidence under traffic. |
| Persistence | Migrations, normal restart, selector validation, referenced-profile protection, settings concurrency | Complete corrupt-row/diagnostic matrix; instrument transaction snapshot emissions and built-in catalogue evolution. |
| Companion packet lifecycle | Seeded distributions, budget sharing, bounds, stable protected UDP sockets | Association-close reproduction above, queue cleanup during delays, registry close/create races, DNS/control path under adverse conditions, fragmentation/truncation/IPv6 policy and live refresh once implemented. |
| Protected TLS/relay | TLS 1.2/1.3 and mTLS Smart reconnects; origin certificate equality; byte counters; ALPN metadata | Actual h2 negotiation/payload passthrough, absent SNI, IP-only and IPv6 socket matrix, fragmented/malformed/oversized/slow ClientHello through the complete route, 500 MiB concurrent/half-close/shutdown stress. ALPN offers alone do not prove h2 carriage. |
| Smart evidence and invalidation | Known alert strings, generic/setup exclusions, scope, concurrent trial admission, TTL/clock, policy disable/stop | Actual certificate-flight evidence; explicit simulated-fault and upstream-failure cases; CA replacement and pairing lifecycle tests; sustained churn/resource bounds. |
| Capture lifecycle | Nullable reason/origin migration and restart round trip; canonical opaque chronology | Smart evidence across rotation/clear/retention, immutable historical evidence after Forget and exactly-once terminals under shutdown races. |
| Interaction/accessibility | Compact enlarged-font renders, bounded lists, pointer/keyboard inspector behavior, reduced motion | Full profile/rule editor typing and focus traversal, error/retry/draft concurrency, theme matrix, rapid inspector resize/close/removal races and native-window dialogs. |
| Native iOS provider | Shared/transport foundation tests and start-options parser cases exist | Packet-runtime gateway/teardown/authentication/UDP behavior tests; explicitly include packet-module test/link tasks in a relevant gate. Current feature gates and companion product gate omit the packet module. |

See [the original Network Conditions ledger](network_conditions_test_gap_report.md#implementation-checkpoint) for
covered versus partial/open IDs. In particular, original matrix rows are not all open: its checkpoint explicitly
closes many matching, engine, routing, telemetry and Android policy cases.

No line/branch coverage threshold is configured. A passing count describes executed assertions, not quantitative
coverage or complete platform qualification.

## Verification evidence

The combined current-tree command passed, including desktop bundle construction:

```sh
./gradlew networkConditionsQualification smartCompatibilityQualification :products:desktop:createDistributable --console=plain
```

Result: **BUILD SUCCESSFUL in 11 seconds**, 290 actionable tasks (60 executed, one from cache, 229 up to date).

The feature gates were then run with forced task execution:

```sh
./gradlew networkConditionsQualification smartCompatibilityQualification --rerun-tasks --console=plain
```

Result: **BUILD SUCCESSFUL in 5 minutes 1 second**, all 286 actionable tasks executed. Fresh XML reports for the
24 distinct test-task outputs contain **868 tests, zero failures/errors, zero skips**. This is the current audited
count, superseding the previously reported 954 total; the earlier count is not repeated as current evidence.
The count covers whole affected modules, not 868 cases dedicated exclusively to these two features.

The temporary association-close reproduction also passed in its reproduction sense:

```sh
./gradlew :connectivity:companion:testAndroidHostTest --tests '*AuditUdpAssociationCloseProbeTest' --console=plain
```

Result: **BUILD SUCCESSFUL in 11 seconds**, one reproduction test. No permanent test or production fix was added
for A1 during this audit.

Additional companion product and native boundary checks passed:

```sh
./gradlew companionAndroidProductQualification companionIosProductQualification \
  :products:companion:iosPacketTunnel:iosSimulatorArm64Test \
  :products:companion:iosPacketTunnel:linkDebugFrameworkIosSimulatorArm64 \
  :engine:sse:test --continue --console=plain
```

Result: **BUILD SUCCESSFUL in 6 minutes 36 seconds**, 485 actionable tasks (159 executed, two from cache, 324 up
to date). This includes Android product tests/lint/debug APK assembly, companion foundation tests, iOS transport
device-target compilation, Simulator tests, app-framework linking and packet-module Simulator framework linking.
The explicit packet-module suite reports **seven passing start-options parser tests**; it has no gateway/UDP
behavior matrix. The SSE suite reports **27 passing tests**, without the missing condition-specific SSE matrix.
The iOS connectivity and DI Simulator reports contain 23 and one passing tests respectively. None of these counts
are added to 868 as if they were disjoint; several companion suites overlap the feature gates. Normal Android host
tests were rerun after removing the temporary probe and report 66 passing cases.

`git diff --check` and local audit-document reference validation passed. Logs and the fresh feature-count inventory
are retained for this session at `/tmp/knet-network-protected-audit.log`,
`/tmp/knet-network-protected-audit-rerun.log`, `/tmp/knet-audit-feature-test-counts.json`,
`/tmp/knet-audit-udp-close-probe.log`, and `/tmp/knet-audit-companion-products.log`.

Physical-device tests were intentionally excluded. No desktop application was launched. Simulator framework
linking does not resolve the previously recorded physical-device account/provisioning blockers.

## Completion order

1. Fix and permanently regress A1; resolve A2's actual-presentation contract.
2. Close the existing proxy feature's condition-specific protocol, capture, lifecycle and performance cases.
3. Add native packet-module qualification to the appropriate gate; finish Android live refresh/rules and original
   tuple/opaque UDP integration before claiming those paths ready.
4. Keep new platform adapters and advanced planned features explicitly incomplete until implemented and tested.
5. Run physical acceptance independently for desktop explicit proxy, Android TCP/UDP, iOS/iPadOS and any future
   macOS transparent adapter. Record actual client retries and business results without deriving them from encrypted
   byte progress.
