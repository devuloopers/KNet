# Network Conditions test gap and edge case report

Updated: 2026-10-04

Status: **Core automated tests pass; complete feature qualification is not yet achieved**

## Purpose and conclusion

This report identifies missing or partial test evidence for KNet Network Conditions. It covers the desktop
application/proxy data plane, the live throughput monitor, Traffic and API Studio integration, persistence, and the
experimental Android UDP data plane. It also records the physical-device and platform evidence required before
WebRTC, QUIC/HTTP/3, custom UDP, iOS, or desktop packet adapters can be promoted.

The current tests provide a good foundation for matching, migration and persistence round trips, aggregate bandwidth
scheduling, bounded queues, live configuration changes, throughput sampling, and Android host-side UDP policy. They
do not yet satisfy all acceptance gates in the
[Network Conditions implementation plan](network_conditions_implementation_plan.md). In particular, KNet still lacks
condition-specific protocol matrices, production-scale streaming and heap tests, Compose UI assertions, some
lifecycle/concurrency cases, and physical-device packet qualification.

This document is a test backlog and qualification record. A missing test is not automatically a confirmed product
defect. When a proposed test exposes incorrect behavior, the defect should be tracked separately and the regression
case should remain in this matrix.

## Audit baseline

The audit reviewed production and test code in these areas:

- `:core:domain`, `:application:desktop`, `:data:desktop`, and `:storage`;
- `:engine:simulator` and the proxy pipeline;
- `:ui:desktop:networkConditions`, `:ui:desktop:traffic`, `:ui:desktop:apiStudio`, and `:ui:desktop:app`;
- `:core:companion`, `:application:companion`, and `:connectivity:companion`;
- desktop product dependency injection and companion packet-policy mapping;
- the acceptance gates in the [Network Conditions implementation plan](network_conditions_implementation_plan.md).

The following consolidated verification completed successfully on 2026-10-04:

```text
./gradlew networkConditionsQualification --rerun-tasks
```

Result: `BUILD SUCCESSFUL`, 264 actionable tasks. The gate is declared in the root build and includes architecture,
domain, storage, repository, application, simulator, Network Conditions UI, API Studio plus its WebSocket, GraphQL
WebSocket, and gRPC editors, Traffic, desktop app, companion codec/application, Android host, and desktop product
tests.

The repository does not currently configure Kover, JaCoCo, or another executable line/branch coverage threshold.
Passing tests therefore proves the asserted behaviors, but it does not provide a quantitative coverage claim.

## Implementation checkpoint

The original 114-case matrix below is retained as the acceptance ledger. Entries named here as **covered** are no
longer missing; **partial** means the new regression test covers an important slice but not the entire acceptance
case. Everything not named remains open at the priority shown in the matrix.

### Covered by automated tests in this pass

- Domain and matching: NC-DOM-001 through NC-DOM-010, including the invalid-host corpus, normalized profile-token
  validation, complete precedence categories, apex/suffix boundaries, and packet-only profiles.
- Persistence: NC-DB-001, NC-DB-003, and NC-DB-004, including schemas 27-30, disabled-rule protection, and the
  quick-add-only deletion cleanup contract.
- Engine: NC-ENG-004, NC-ENG-005, NC-ENG-007 through NC-ENG-019, NC-ENG-022, NC-ENG-023, and NC-ENG-024. This
  includes latency/jitter edits, timeout/reset
  lifecycle, zero-byte trailers, exact 8 MiB per-flow and 64 MiB aggregate boundaries, isolated rejection,
  handler removal, bidirectional queues, and repeated enable/disable cycles.
- API Studio and Traffic: NC-PROTO-008, NC-PROTO-009, NC-TRAF-001, and NC-TRAF-002. HTTP, WebSocket, GraphQL
  WebSocket, and gRPC now share one conditions-aware, fail-closed proxy-routing decision.
- ViewModel/presentation: NC-UI-001, NC-UI-002, NC-UI-003, NC-UI-006, NC-UI-012, NC-UI-013, and NC-UI-015.
- Telemetry and Android host policy: NC-TEL-002, NC-TEL-003, NC-TEL-004, NC-TEL-005, NC-AND-001, NC-AND-002,
  and NC-AND-003.

The new tests also validate packet-policy wire-format versioning, duplicate/missing/unknown fields, size limits,
packet mapper fail-closed behavior, and `NetworkConditionConfiguration` rejection of custom IDs that collide with
built-ins.

### Partially advanced, with remaining assertions still open

- NC-DB-005 now has corrupt settings and wire-format cases; the complete corrupt-row matrix and diagnostic behavior
  remain open.
- NC-DB-006 now proves concurrent independent settings/rule writes serialize without lost updates; explicit
  transient-emission instrumentation remains open.
- NC-ENG-001, NC-ENG-002, NC-ENG-006, and NC-ENG-021 now cover a simulated one-minute 100 kbps schedule, shared
  budgets, representative payload/MTU behavior, and ordered multi-item disable plus live rate/latency/jitter edits.
  Real sustained I/O, `FileRegion` extremes, full multi-item rule/profile edit permutations, and fairness under
  pressure remain open.
- NC-ENG-020 now proves handler removal unregisters live edits, cancels queued work, and releases accounting; an
  application-shutdown integration assertion remains open.
- NC-PROTO-003 has generic binary WebSocket frame identity/order coverage. Its complete frame/control/lifecycle
  matrix remains open.
- NC-TEL-001 now proves the sampler derives exactly 100 kbps from released byte deltas, but a single real shaper-to-
  graph integration test remains open.

### Production defects fixed while adding the tests

- Flow handles now close idempotently, so a repeated close cannot decrement another active flow.
- Delay/deadline arithmetic saturates at `Long.MAX_VALUE` instead of wrapping into an immediate or negative delay.
- API Studio fails explicitly when conditions require the local proxy but that proxy is unavailable; it no longer
  silently executes an unconditioned direct request.
- Custom profiles can no longer replace a built-in profile by reusing its ID.

## Existing evidence that should be retained

The following behavior already has focused automated evidence and should not be duplicated without adding a new
boundary or failure mode:

- exact, wildcard, optional-port, IDNA, IPv4, and IPv6 normalization and rule precedence;
- duplicate normalized target rejection and master-disabled matching;
- profile, rule, activation, and quick-add preference restart round trips;
- referenced-profile deletion protection and corrupt-settings fail-closed behavior;
- canonical Traffic quick-add preparation, duplicate handling, and refusal to reparse display-only URLs;
- aggregate 100 kbps reservation, shared budgets, and independent upload/download scheduling;
- live bandwidth changes and immediate queue release when conditions are disabled;
- global and per-flow queue accounting, virtual-MTU splitting, trailers, and exact forwarded-byte counting;
- failed payload exclusion from throughput and subscriber-driven runtime publication;
- raw CONNECT tunnel use of the shared 100 kbps budget;
- elapsed-time-aware throughput calculation, counter-reset safety, long-gap handling, and 60-sample history bounds;
- ViewModel throughput collection and stopping monitoring when the screen leaves composition;
- packet-policy serialization and authenticated desktop-to-companion retrieval;
- Android host-side aggregate UDP bandwidth, independent directions, deterministic loss/duplication/reordering,
  duplicate-aware bandwidth accounting, bounded queues, stable protected UDP flows, and idle socket eviction;
- navigation presence and desktop product packet-policy mapping.

## Priority definitions

| Priority | Meaning | Release implication |
| --- | --- | --- |
| P0 | A core implemented path can regress, corrupt data, leak resources, or bypass the requested condition without a reliable test. | Required before calling the desktop proxy feature fully qualified. |
| P1 | Important correctness, UI, persistence, or observability coverage is missing, but the basic data plane has focused evidence. | Required before broad release support or Charles-parity claims. |
| P2 | Performance, soak, physical-device, or experimental packet evidence is missing. | Required before promoting the applicable adapter or transport. |
| P3 | Coverage for a planned feature that is not implemented yet. | Add with the feature; do not write a test that silently emulates unavailable behavior. |

## Domain model and matching gaps

| ID | Priority | Missing or partial case | Required assertion |
| --- | --- | --- | --- |
| NC-DOM-001 | P1 | Directional rate and utilization boundaries | Accept minimum/maximum rates and 1/100 percent utilization; reject values immediately outside every boundary. Verify the effective rate never becomes zero. |
| NC-DOM-002 | P1 | Profile delay, MTU, and packet-percentage boundaries | Cover zero, maximum, and out-of-range latency, jitter, virtual MTU, loss, duplication, and reordering values. |
| NC-DOM-003 | P1 | Identifier and name validation | Reject blank, untrimmed, uppercase, and otherwise invalid profile IDs; reject blank rule IDs and profile names. |
| NC-DOM-004 | P1 | Configuration reference invariants | Reject duplicate profile IDs, duplicate rule IDs, custom profiles claiming built-in ownership, built-in ID collisions, and missing global, quick-add, or rule profile references. |
| NC-DOM-005 | P1 | Invalid destination corpus | Cover blank hosts, malformed DNS names, wildcard IP literals, malformed/bracket-mismatched IPv6, port 0/65536, Unicode normalization failures, and whitespace around wildcard targets. |
| NC-DOM-006 | P0 | Disabled domain rule fallback | A disabled exact or wildcard rule must be ignored, allowing the next eligible rule or global profile to apply. |
| NC-DOM-007 | P0 | Explicit domain bypass over a throttled global profile | A matching `No throttling` rule must pass through while unmatched destinations retain the global condition. |
| NC-DOM-008 | P1 | Complete precedence permutation | Exercise exact with port, exact without port, longest wildcard with port, longest wildcard without port, global, and no match in one deterministic table, including different declaration orders. |
| NC-DOM-009 | P1 | Apex and nested wildcard boundaries | Confirm `*.example.com` excludes the apex, includes deeper subdomains, and does not suffix-match `notexample.com`. |
| NC-DOM-010 | P1 | Packet-only profile activity | A profile containing only packet loss, duplication, or reordering must remain active for packet mapping without incorrectly shaping proxy payload as packet loss. |

## Persistence and migration gaps

| ID | Priority | Missing or partial case | Required assertion |
| --- | --- | --- | --- |
| NC-DB-001 | P0 | Room migration into current schema 31 | Open representative databases from every supported upgrade origin, especially the schema where Network Conditions tables first appeared and versions 29, 30, and 31. Preserve valid settings, profiles, rules, and indexes. |
| NC-DB-002 | P0 | Fresh database versus migrated database equivalence | Both paths must expose equivalent defaults, constraints, indexes, and repository snapshots. |
| NC-DB-003 | P0 | Atomic profile deletion protection | A profile referenced by the global selection or any enabled/disabled rule must not be deleted. A quick-add-only reference must be cleared safely when its profile is deleted, without exposing an invalid snapshot. |
| NC-DB-004 | P1 | Reset persistence contract | Reset must disable shaping and clear the global selection while retaining saved custom profiles and domain rules across restart. |
| NC-DB-005 | P1 | Corrupt row matrix | Fail closed for malformed profile values, invalid targets, duplicate IDs/targets, and missing references—not only a missing global profile. Surface a recoverable diagnostic without deleting unrelated valid data. |
| NC-DB-006 | P1 | Concurrent repository writes | Concurrent enable, profile, and rule edits must serialize without lost updates or transient invalid snapshots. |
| NC-DB-007 | P1 | Immutable snapshot emission | One logical transaction must not expose a configuration containing a rule before its referenced profile or after that profile has disappeared. |
| NC-DB-008 | P1 | Built-in catalog evolution | Adding or changing versioned built-ins must not overwrite custom profiles or break persisted references on restart. |
| NC-DB-009 | P1 | Interrupted write and reopen | Simulate cancellation or database close during an edit and confirm the next startup returns the previous or complete new transaction, never partial state. |

## Desktop shaping engine gaps

### Bandwidth and fairness

| ID | Priority | Missing or partial case | Required assertion |
| --- | --- | --- | --- |
| NC-ENG-001 | P0 | Sustained 100 kbps transfer | Forward a sufficiently long stream and verify measured delivery remains within a documented tolerance around 100,000 bit/s after startup, with no multi-second catch-up burst. |
| NC-ENG-002 | P0 | Concurrent connection fairness | Multiple channels sharing one effective rule must share the aggregate budget without starvation or multiplying the configured rate. |
| NC-ENG-003 | P0 | HTTP/2 stream fairness | Concurrent child streams on one connection must share the applicable aggregate budget without blocking unrelated destinations or exceeding the limit. |
| NC-ENG-004 | P1 | Utilization percentage | Verify the effective rate and actual schedule at representative percentages, including rounding at the minimum rate. |
| NC-ENG-005 | P1 | Idle credit behavior | Long idle periods must not accumulate an unbounded token burst. The next payload should receive only the documented bounded startup allowance. |
| NC-ENG-006 | P1 | Very small and very large payloads | Cover one-byte units, virtual-MTU boundaries, large `ByteBuf` values, and `FileRegion` counts without overflow or negative delays. |
| NC-ENG-007 | P1 | Monotonic time extremes | Cover a clock near `Long.MAX_VALUE`, backward or repeated test-clock values, and duration arithmetic overflow with fail-safe behavior. |
| NC-ENG-008 | P1 | Rule budget isolation | Different rules and the global profile must not accidentally share a bucket; flows using the same effective rule must share it. |

### Latency, jitter, and failures

| ID | Priority | Missing or partial case | Required assertion |
| --- | --- | --- | --- |
| NC-ENG-009 | P0 | Fixed latency semantics | Prove first-byte and subsequent-unit delay semantics with a monotonic test clock. Latency must not compound incorrectly for each streaming chunk. |
| NC-ENG-010 | P0 | Deterministic jitter bounds | A fixed seed and sequence must reproduce the same values, every result must remain within the configured signed bound, and byte/frame order must remain intact. |
| NC-ENG-011 | P0 | Live latency and jitter edit | Queued work must be safely replanned and active flows must observe the new policy without duplicate or lost delivery. |
| NC-ENG-012 | P0 | Live offline and recovery | Switching an active stream to Offline and back must produce the documented failure/recovery behavior immediately without stale budget credit. |
| NC-ENG-013 | P0 | Timeout behavior | Verify whether the message remains pending, the promise outcome, channel state, cleanup, and recovery. The test must match the documented timeout contract rather than only checking that bytes were not counted. |
| NC-ENG-014 | P0 | Reset-flow behavior | The flow must close once, fail outstanding promises, release queued buffers, and update fault telemetry without double release. |
| NC-ENG-015 | P1 | Seeded proxy fault distribution | Over a fixed sequence, verify determinism and expected count/tolerance for intermediate probabilities—not only always-fail behavior. |
| NC-ENG-016 | P1 | Zero-byte protocol units | Headers, trailers, and control frames with no payload must preserve ordering and must not consume bandwidth bytes or become stuck behind a zero-byte queue entry. |

### Queue, ordering, and lifecycle

| ID | Priority | Missing or partial case | Required assertion |
| --- | --- | --- | --- |
| NC-ENG-017 | P0 | Per-flow queue boundary | Exercise exactly 8 MiB, one byte below, and one byte above. Overflow must use the selected/documented fault behavior and release every reference-counted message. |
| NC-ENG-018 | P0 | Aggregate queue boundary | Exercise exactly 64 MiB across multiple flows and verify one flow cannot corrupt another flow's accounting on rejection. |
| NC-ENG-019 | P0 | Channel close before scheduled release | Cancelling an active flow must cancel timers, release every buffer once, fail promises, decrement active flows, and return queued bytes to zero. |
| NC-ENG-020 | P0 | Handler removal and application shutdown | Removing the handler or closing the proxy must unregister configuration listeners and leave no scheduled tasks, open flows, or retained payloads. |
| NC-ENG-021 | P0 | Replanning multiple queued items | Disable, rate edit, rule edit, and profile edit with multiple upload/download entries must preserve direction ordering and exact-once forwarding. |
| NC-ENG-022 | P1 | Queue rejection telemetry | Rejected work must not be counted as forwarded; queued, delayed, and fault counters must remain internally consistent. |
| NC-ENG-023 | P1 | Bidirectional interleaving | Upload and download queues must progress independently without corrupting direction-specific deadlines or counters. |
| NC-ENG-024 | P1 | Repeated enable/disable cycles | Rapid cycles must not duplicate listeners, timers, or releases and must converge to the latest configuration. |

## Protocol and product integration gaps

Protocol qualification tests currently validate their protocol implementations, but they do not provide a complete
Network Conditions matrix. Every case below must assert the applied condition, timing/throughput behavior, ordering,
and cleanup—not merely that the protocol still works while the handler exists.

| ID | Priority | Missing or partial case | Required assertion |
| --- | --- | --- | --- |
| NC-PROTO-001 | P0 | HTTP/1.1 streaming matrix | Cover request upload, response download, chunked bodies, trailers, keep-alive reuse, redirects, byte ranges, progressive downloads, and cancellation. |
| NC-PROTO-002 | P0 | HTTP/2 matrix | Cover concurrent streams, flow-control/backpressure, reset/cancel, trailers, streaming bodies, and aggregate fairness. |
| NC-PROTO-003 | P0 | WebSocket matrix | Cover text, binary, fragmented messages, ping/pong, close frames, long-lived live edits, and exact ordering. |
| NC-PROTO-004 | P0 | GraphQL WebSocket matrix | Cover connection setup, subscribe, next/error/complete, multiplexed operations, cancellation, and long-lived policy changes. |
| NC-PROTO-005 | P0 | gRPC matrix | Cover unary, client streaming, server streaming, bidirectional streaming, trailers, cancellation, and uncorrupted framing. |
| NC-PROTO-006 | P0 | SSE matrix | Cover incremental events, comments, reconnect, long idle gaps, cancellation, and absence of whole-body buffering. |
| NC-PROTO-007 | P0 | CONNECT/TLS and opaque tunnel matrix | Cover authority parsing, IPv4/IPv6, early close, bidirectional traffic, TLS passthrough, applied evidence, and aggregate sharing. |
| NC-PROTO-008 | P0 | API Studio conditions-only routing | When capture is paused but Network Conditions is enabled, HTTP, WebSocket, GraphQL WebSocket, and gRPC execution must use the local proxy and receive the selected condition. |
| NC-PROTO-009 | P0 | API Studio unavailable proxy | If conditions are enabled but the proxy is stopped or unavailable, return an explicit safe error or documented fallback; never silently claim the condition was applied. |
| NC-PROTO-010 | P0 | Paired-device proxied traffic | Eligible paired traffic must use the same rule policy and applied evidence as desktop captured traffic. |
| NC-PROTO-011 | P0 | Control-plane exclusion | Pairing, certificate retrieval, condition-policy fetch, discovery, and the disable path must remain reachable and unthrottled. |
| NC-PROTO-012 | P0 | No double shaping | Traffic already shaped in the proxy must bypass packet-level shaping, so 100 kbps does not become an effective 50 kbps or worse. |
| NC-PROTO-013 | P1 | Destination continuity after upgrade | WebSocket and opaque upgraded flows must retain the validated destination and effective rule for their entire lifetime. |
| NC-PROTO-014 | P1 | Unknown destination | Traffic without a trustworthy host/port must pass through with explicit unavailable evidence rather than matching display text or a stale previous destination. |

## Traffic quick-add and evidence gaps

| ID | Priority | Missing or partial case | Required assertion |
| --- | --- | --- | --- |
| NC-TRAF-001 | P0 | Missing exchange result | A removed or unknown Traffic ID must produce the defined unavailable state and must not open an empty editor. |
| NC-TRAF-002 | P0 | Opaque-flow quick-add | Valid protected/opaque flow destination metadata must prefill exact host and port; absent or untrusted metadata must disable the action. |
| NC-TRAF-003 | P1 | Default and explicit ports | HTTP, HTTPS, IPv4, bracketed IPv6, and explicit non-default ports must produce the correct canonical target. |
| NC-TRAF-004 | P1 | Existing disabled rule | Quick-add must open the existing equivalent rule without creating a duplicate or silently enabling it. |
| NC-TRAF-005 | P0 | Navigation and prefill integration | The Traffic action must navigate once, preserve the selected transaction, and open the fully prefilled Network Conditions editor. |
| NC-TRAF-006 | P1 | Action availability and reason | Rows without a trustworthy destination must expose a safe disabled state and user-facing reason without reparsing redacted URLs. |
| NC-TRAF-007 | P0 | Applied evidence matrix | Verify global, domain-rule, No-throttling override, disabled, and unavailable evidence for HTTP exchanges and opaque flows. |
| NC-TRAF-008 | P1 | Live edit evidence | Existing flows retain documented evidence semantics while new connections/streams record the newly selected rule or profile. |
| NC-TRAF-009 | P1 | Sensitive-data boundary | Runtime snapshots and applied evidence must never contain paths, query strings, headers, tokens, payloads, or full URLs. |

## ViewModel and Compose UI gaps

### State and actions

| ID | Priority | Missing or partial case | Required assertion |
| --- | --- | --- | --- |
| NC-UI-001 | P0 | Master enable and disable | The ViewModel persists the requested state, reports repository failures, and does not discard saved profiles or rules. |
| NC-UI-002 | P0 | Global profile selection | Selecting Off, No throttling, each built-in, and a custom profile produces the expected persisted global state. |
| NC-UI-003 | P0 | Reset action | Reset bypasses shaping without deleting saved profiles or rules and updates the effective-state summary. |
| NC-UI-004 | P1 | Complete profile CRUD | Create, edit, and delete custom profiles with asymmetric/unlimited rates, utilization, delay, jitter, MTU, proxy failure, and packet fields. |
| NC-UI-005 | P1 | Profile validation errors | Every invalid field must remain editable, display a precise error, and avoid partially saving the profile. |
| NC-UI-006 | P1 | Complete rule CRUD | Toggle, edit target/profile, delete, cancel, and duplicate-conflict paths must preserve IDs and enabled state correctly. |
| NC-UI-007 | P1 | Error recovery | Repository and validation errors must clear at the documented time, permit retry, and not dismiss unrelated drafts. |
| NC-UI-008 | P1 | Concurrent configuration update | An external repository update while a dialog is open must not overwrite a user's draft or save stale references. |

### Layout, graph, and accessibility

| ID | Priority | Missing or partial case | Required assertion |
| --- | --- | --- | --- |
| NC-UI-009 | P1 | Equal desktop overview cards | At supported wide window sizes, Global condition and Live application cards must have equal width and equal measured height. |
| NC-UI-010 | P1 | Compact responsive layout | Cards must stack at full width without clipping, overlapping controls, inaccessible scrolling, or dialog overflow. |
| NC-UI-011 | P1 | Theme regression | Controls, surfaces, focus indication, typography, and semantic colors must use KNet theme tokens in dark and supported alternate themes. |
| NC-UI-012 | P0 | Graph reference state | Disabled, no global limit, unlimited, offline, a single limited global profile, and enabled domain-rule/mixed states must show the correct label and only honest reference lines. |
| NC-UI-013 | P0 | Graph scaling boundaries | Zero, sub-kbps, exactly 100 kbps, Mbps, large peaks, and sudden drops must produce finite coordinates, readable ticks, and stable headroom. |
| NC-UI-014 | P1 | Graph empty and idle behavior | Before valid traffic, show `Waiting for traffic` without a fabricated colored throughput line; after traffic stops, current rates fall to zero while bounded history remains. |
| NC-UI-015 | P1 | Graph sampling restart | Leaving the screen stops collection; returning starts a fresh bounded window without retaining an obsolete rate baseline. Multiple observers must not create multiple samplers. |
| NC-UI-016 | P1 | Accessibility semantics | The graph description must include current upload/download rates, honest configured references, active flows, and queued bytes while excluding destinations and payload data. Color must not be the only series distinction. |
| NC-UI-017 | P1 | Keyboard and focus behavior | All chips, switches, dialogs, editors, and destructive actions must be reachable in a predictable order; text-field focus must not be lost after each keystroke. |
| NC-UI-018 | P1 | Empty, loading, and error states | Profile/rule empty states, repository loading, recoverable errors, and unavailable advanced adapters must remain readable and actionable. |
| NC-UI-019 | P1 | Long content and localization stress | Long profile names, domains, large formatted rates, and increased font scale must not overlap or truncate essential controls. |

## Throughput telemetry and performance gaps

| ID | Priority | Missing or partial case | Required assertion |
| --- | --- | --- | --- |
| NC-TEL-001 | P0 | End-to-end measured throughput | The graph sampler must derive approximately 100 kbps from bytes actually released by the shaper, not from the configured profile value. |
| NC-TEL-002 | P0 | Pass-through and bypass counting | Master-bypassed, unlimited, and No-throttling rule traffic crossing the handler must be counted once; queued, failed, abandoned, and rejected bytes must remain excluded. |
| NC-TEL-003 | P1 | Counter extremes | Cover very large cumulative totals, signed overflow/reset, repeated timestamps, delayed ticks, and process/session restart without negative spikes. |
| NC-TEL-004 | P1 | Active-flow exactness | Channel activation, inactive, handler removal, failed setup, and repeated close calls must never leave a negative or leaked active-flow count. |
| NC-TEL-005 | P1 | Publication rate | High packet rates must not publish per-packet UI state. With observers, publication remains coalesced to the documented interval; without observers, periodic work stops. |
| NC-PERF-001 | P0 | 500 MiB bounded-memory test | Offer at least 500 MiB to slow and offline consumers. Heap and queued payload must remain within documented bounds, with deterministic overflow behavior. |
| NC-PERF-002 | P1 | Graph allocation baseline | Compare idle, 100 kbps, and high-throughput runs with the graph visible and hidden. History remains fixed at 60 samples and no per-packet presentation objects are retained. |
| NC-PERF-003 | P1 | Disabled-mode regression | Measure throughput and latency with the handler installed but conditions bypassed against an agreed proxy baseline. |
| NC-PERF-004 | P2 | Long-running soak | Repeated enable/edit/disable cycles and long-lived WebSocket/SSE/gRPC streams must not grow timers, listeners, queues, histories, sockets, or heap over time. |

## Android UDP and packet-data-plane gaps

Host-side unit tests establish deterministic policy behavior. They do not qualify Android `VpnService`, real radio
or Wi-Fi changes, WebRTC, or QUIC on a physical device.

| ID | Priority | Missing or partial case | Required assertion |
| --- | --- | --- | --- |
| NC-AND-001 | P1 | Intermediate loss/duplication/reordering distributions | Fixed seeds at representative percentages must produce reproducible counts within a documented tolerance, not only 0 or 100 percent cases. |
| NC-AND-002 | P1 | Latency and jitter bounds | Verify fixed latency, signed jitter, non-negative final delay, and deterministic results over multiple flows and directions. |
| NC-AND-003 | P0 | Offline mapping and recovery | Desktop Offline/Timeout/Reset policies mapped to packet loss must produce the documented packet behavior and recover after policy removal. |
| NC-AND-004 | P0 | Live packet-policy refresh | An already-running VPN session must fetch/apply a new policy safely without restart, stale budgets, dropped control-plane connectivity, or double shaping. This feature is not implemented yet. |
| NC-AND-005 | P0 | Protocol/IP/CIDR/port rules | Verify precedence, IPv4/IPv6, inferred-domain uncertainty, and fallback when attribution is unavailable. This feature is not implemented yet. |
| NC-AND-006 | P0 | Queue close and cancellation | Closing a UDP association or VPN with delayed datagrams must release every queue slot, timer, socket, and payload exactly once. |
| NC-AND-007 | P0 | DNS and recursion safety | VPN DNS remains available, protected sockets never re-enter the VPN, and desktop control/certificate/pairing endpoints remain reachable. |
| NC-AND-008 | P0 | Fragmentation and MTU | Cover maximum UDP payload, truncation rejection, IPv4/IPv6 fragmentation policy, checksums, and configured MTU transitions without corruption. |
| NC-AND-009 | P1 | Destination registry concurrency | Concurrent create, eviction, expiry, response delivery, and close must not reuse a closed socket or deliver a response to the wrong association. |
| NC-AND-010 | P2 | Physical custom UDP qualification | Sustained IPv4 and IPv6 echo/stream workloads must meet bandwidth/delay/fault tolerances and recover after disable. |
| NC-AND-011 | P2 | Physical WebRTC qualification | Exercise call startup, adaptation, freeze/recovery, STUN/TURN, ICE path changes, offline/recovery, and confirm no SRTP content is inspected. |
| NC-AND-012 | P2 | Physical QUIC/HTTP/3 qualification | Exercise HLS/DASH over HTTP/3 with concurrent streams and connection migration; shape opaque QUIC without falsely presenting decoded HTTP transactions. |
| NC-AND-013 | P2 | Android lifecycle qualification | Cover VPN consent denial, start/stop, process death, network handoff, route change, sleep/wake, companion disconnect, and application shutdown. |

## iOS and desktop packet adapter gaps

These cases are promotion requirements, not current JVM unit-test omissions. The adapters must exist and report
permission/capability state truthfully before transport tests can pass.

| ID | Priority | Missing or partial case | Required assertion |
| --- | --- | --- | --- |
| NC-IOS-001 | P3 | Entitlement-signed packet tunnel | Implement and test the real `NEPacketTunnelProvider` data plane, protected control path, UDP scheduling, teardown, and configuration refresh. |
| NC-IOS-002 | P2 | Physical iOS/iPadOS qualification | Run custom UDP, WebRTC, and QUIC/HTTP/3 workloads on entitled physical devices across route and lifecycle transitions. |
| NC-DESK-001 | P3 | Permission-managed desktop packet adapter | Implement explicit capability/permission reporting, safe routing, control-plane exclusion, and reversible teardown for each supported desktop platform. |
| NC-DESK-002 | P2 | Platform-specific desktop qualification | Verify UDP/QUIC/WebRTC shaping, sleep/wake, route changes, application crash recovery, and coexistence with other VPN/security software. |

## Planned feature test gaps

The implementation plan lists the following features as incomplete. Tests should be delivered with their
implementation rather than mocked into a false supported state.

| ID | Priority | Planned feature | Minimum evidence when implemented |
| --- | --- | --- | --- |
| NC-FUT-001 | P3 | Named timed scenarios | Deterministic transition clock; start, pause, resume, cancel, restart recovery, and active-flow transitions such as 5 Mbps to 100 kbps to Offline to 5 Mbps. |
| NC-FUT-002 | P3 | Profile import/export | Versioned round trip, validation, duplicate IDs/names, unknown future fields, corrupt/truncated input, size bounds, and no secret-bearing data. |
| NC-FUT-003 | P3 | Authenticated automation | Authentication, authorization, replay resistance, bounded input, concurrent edits, enable/disable recovery, control-plane exclusion, and auditable safe errors. |

## Manual qualification matrix

Automated tests should be the default, but these observations require a real application, OS networking stack, or
physical device. Each run must record KNet version/commit, OS/device, source and destination, selected profile,
payload size, expected result, observed result, tolerance, logs, and pass/fail outcome.

### macOS desktop proxy

- HLS master/media playlist and segment download at 100 kbps, including adaptation and recovery.
- DASH manifest and concurrent segment download at 100 kbps.
- Progressive download and HTTP byte-range seek behavior.
- Actual transfer duration and first-byte delay for fixed payload sizes.
- Concurrent requests proving one aggregate rule budget.
- Long-lived WebSocket, SSE, and gRPC behavior during live rate, latency, Offline, and recovery changes.
- API Studio requests with capture running, paused, and stopped while Network Conditions remains enabled.
- Master-disable recovery when the active condition is severe enough to make the application appear offline.
- Disabled-mode throughput and latency comparison against the agreed baseline.
- Heap/allocation capture with the live graph visible and hidden.

### Android physical device

- The cases NC-AND-010 through NC-AND-013 on at least one supported Android API level and one additional vendor or
  API-level combination before broad promotion.
- Google Play and other protected-service traffic must remain reachable; the condition/control path must not be
  recursively tunneled.
- Wi-Fi to cellular and cellular to Wi-Fi handoff, airplane mode, sleep/wake, VPN revoke, process death, and restart.

### iOS/iPadOS and desktop packet adapters

- Do not mark these transports supported from simulator or JVM evidence alone.
- Require entitlement-signed physical iOS/iPadOS results and permission-managed evidence for every supported desktop
  operating system.

## Recommended test implementation order

### Gate 1: Core correctness

1. Add latency/jitter/failure and queue-cleanup tests NC-ENG-009 through NC-ENG-024.
2. Add API Studio conditions-only routing and unavailable-proxy tests NC-PROTO-008 and NC-PROTO-009.
3. Add disabled-rule, explicit bypass, configuration-invariant, and database migration tests.
4. Add end-to-end throughput and sustained 100 kbps tests NC-ENG-001 and NC-TEL-001.

### Gate 2: Protocol and UI qualification

1. Build the Network Conditions protocol matrix NC-PROTO-001 through NC-PROTO-007.
2. Add Traffic navigation/evidence integration tests NC-TRAF-001 through NC-TRAF-009.
3. Extract graph reference/scaling calculations into directly testable presentation functions where appropriate.
4. Add Compose UI tests for layout, focus, keyboard, accessibility, responsive behavior, and error states.

### Gate 3: Performance and platform promotion

1. Add the bounded-memory, disabled-regression, allocation, and soak gates.
2. Complete live Android policy refresh and packet rule scopes before testing them as supported features.
3. Run the physical Android workload matrix and save reproducible evidence.
4. Implement and independently qualify iOS and desktop packet adapters.

## Required continuous integration gates

The release workflow now exposes one focused Network Conditions gate rather than relying on developers to remember
an expanding list of module tasks. CI should run:

```text
./gradlew networkConditionsQualification
git diff --check
```

Add the HTTP/2, WebSocket, GraphQL WebSocket, gRPC, and SSE qualification tasks after they contain explicit Network
Conditions assertions. Passing protocol qualification without condition-specific assertions must not be counted as
passing the Network Conditions protocol matrix.

A coverage tool should report line and branch coverage for the domain matcher, repository mapping, shaping engine,
channel handler, throughput sampler, and ViewModel. The initial threshold must be based on a recorded baseline and
ratchet upward; selecting an arbitrary percentage before measuring the repository would create a misleading gate.

## Completion criteria

The desktop proxy feature can be called fully qualified only when:

- every P0 desktop/domain/persistence/engine/protocol/Traffic/telemetry case has automated evidence;
- the required P1 cases either pass or have a documented, approved release exception;
- protocol-specific tests prove shaping rather than only protocol operation;
- sustained streaming, bounded-memory, cleanup, and disabled-mode regression gates pass;
- the macOS manual matrix has recorded evidence;
- no test or UI implies support for an unavailable packet adapter.

Android packet conditions can be promoted independently only when its P0 and P1 automated cases pass and the
physical Android WebRTC, QUIC/HTTP/3, custom UDP, IPv6, route, lifecycle, and recovery evidence is recorded. iOS and
desktop packet adapters require their own implementations and independent physical/platform qualification.

Until those conditions are met, the accurate status remains: desktop proxy implementation available with partial
qualification, Android UDP experimental, and iOS/desktop packet adapters unavailable.
