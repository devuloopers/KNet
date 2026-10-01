# Network Conditions implementation plan

Status: **IMPLEMENTED FOR DESKTOP PROXY; ANDROID UDP EXPERIMENTAL; PROMOTION GATES REMAIN**

Created: 2026-09-30

Target phases: Phase 133 for proxy conditions and Phase 134 for packet/UDP conditions

## Implementation checkpoint — 2026-10-01

Delivered:

- dedicated Network Conditions navigation, global selection, exact/wildcard host and optional-port rules, profile
  creation/editing, reset, persistence, restart recovery, and Traffic one-click quick-add;
- application/proxy shaping for captured and API Studio traffic with asymmetric aggregate bandwidth, utilization,
  latency, deterministic jitter/faults, virtual-MTU incremental chunks, bounded queues, runtime counters, live rate
  changes, and immediate safe bypass when disabled;
- canonical per-exchange and per-opaque-flow applied profile/rule/source evidence, persisted through Room schema 30
  and shown in Traffic details;
- a concrete **Streaming 100 kbps** preset with download limited to 100,000 bits/s and upload unlimited;
- authenticated desktop-to-companion packet-policy fetch and experimental Android VPN UDP shaping with stable
  protected sockets, rate, latency/jitter, loss, duplication, bounded reordering, offline/recovery, and queue bounds;
- focused domain, persistence, UI, engine, Android-host, companion-control, WebSocket, GraphQL WebSocket, product-DI,
  architecture, and protocol-qualification coverage.
- post-audit correctness hardening: live queued-byte replanning on disable/profile/rule edits, active MTU-only
  profiles, direct add/edit domain-rule controls, unified HTTP/opaque Traffic chronology and store-side semantic
  filtering, bounded idle-evicted Android UDP destination sockets, and duplicate-aware bandwidth accounting.

Still required before changing the capability from experimental to supported or claiming complete Charles parity:

- named timed scenarios, profile import/export, and authenticated automation;
- manual macOS HLS/DASH/progressive-download measurements, disabled-mode baseline, and the remaining sustained
  large-load/protocol matrix in the acceptance gates below;
- packet protocol/IP/CIDR/port rules and live packet-policy refresh while a VPN session is already running;
- real Android device WebRTC, QUIC/HTTP/3, custom UDP, IPv6, route-change, sleep/wake, fragmentation, and migration
  evidence;
- entitlement-signed iOS packet-tunnel implementation/qualification and a permission-managed desktop packet
  adapter. These platforms remain unavailable, not silently emulated by the JVM proxy.

## Outcome

Add a first-class **Network Conditions** feature to KNet. It will be a separate desktop destination immediately
before **Intercepts** (the Breakpoints destination) in the primary sidebar. Users can apply one global condition,
override it for particular domains, and create a fully prefilled domain rule directly from a selected Traffic row
without copying a URL or typing a host.

This is a network-behaviour feature, not a breakpoint mode. Breakpoints pause and edit messages; Network Conditions
continuously shape eligible traffic and must remain independently enabled, persisted, observable, and testable.
Phase 133 delivers proxy-layer conditions for TCP-based application protocols. Phase 134 extends the same product
model through a qualified VPN/TUN packet data plane for WebRTC/UDP, HTTP/3 over QUIC, and custom UDP streams.

## Product decisions

### Sidebar and screen

The primary navigation order becomes:

1. Traffic
2. API Studio
3. Network Conditions
4. Intercepts

`DesktopDestination.NetworkConditions` will own a dedicated screen in a new `:ui:desktop:networkConditions`
module. The screen contains:

- a master enable switch and an immediately visible effective-state summary;
- a **Global condition** card, which can be Off, No throttling, a built-in preset, or a custom profile;
- a **Domain rules** list with enable state, normalized target, selected profile, precedence, and edit/delete actions;
- a profile editor with separate download/upload rates, latency, jitter, utilization, MTU, and failure behaviour;
- active-flow and recently-applied indicators so users can tell whether a rule is actually taking effect;
- a reset action that disables shaping without deleting saved profiles or rules.

The screen must use KNet's existing design system, MVI conventions, application contracts, and product DI. The UI
must not import proxy, simulator, Netty, or persistence implementations.

### Traffic quick-add

The Traffic row context menu gains **Add to Network Conditions...** next to **Add Breakpoint Rule**. One action:

1. resolves the selected canonical exchange through the application layer;
2. extracts and normalizes its validated destination host and effective port;
3. navigates to Network Conditions;
4. opens a prefilled rule editor for that exact destination.

The user chooses or accepts the remembered quick-add profile and confirms **Add condition**. No URL, scheme, path,
query, or host needs to be copied. Confirmation is deliberate because enabling a rule changes live traffic. Later,
a context submenu may offer literal immediate application of a named preset, but that is not required for the first
release.

If an equivalent rule already exists, quick-add opens that rule for editing instead of creating a duplicate. If
the selected row has no trustworthy network destination, the action is disabled with a presentation-safe reason.
Display-only or redacted URL text must never be reparsed as authority.

### Streaming-first behaviour

The primary product scenario is observing how an active streaming application reacts when available download
bandwidth falls to a value such as `100 kbps` (`12.5 kB/s`). The first proxy release must therefore prioritize:

- independent download and upload limits, with download-only throttling as a first-class workflow;
- aggregate enforcement per effective rule so parallel segment requests or HTTP/2 streams share one configured
  `100 kbps` budget instead of each receiving `100 kbps`;
- smooth bounded token delivery without accumulating multi-second bursts that hide buffering behaviour;
- incremental forwarding without buffering an entire media segment or response body;
- immediate application of safe profile changes to active flows, including bandwidth, latency, jitter, and
  offline/recovery transitions;
- live configured-versus-observed throughput, queued bytes, active-flow count, and applied-rule visibility;
- unambiguous `kbps`/`Mbps` bit-rate units with a secondary byte-rate conversion where helpful;
- HTTP byte-range, redirected CDN, HLS, DASH, progressive download, and concurrent media/resource workloads.

The profile editor should make **Download 100 kbps / Upload unlimited** a direct configuration, not require a
symmetric limit. Repeatable scenarios must support timed transitions such as 5 Mbps, then 100 kbps, then Offline,
then recovery to 5 Mbps, so adaptive bitrate, startup delay, rebuffering, timeout, and recovery can be tested in
one run.

### Scope and matching

The initial proxy release supports:

- a global baseline;
- exact DNS hosts, such as `api.example.com`;
- wildcard subdomains, such as `*.example.com`, which do not implicitly match the apex;
- IPv4 and IPv6 literals;
- an optional exact destination port;
- an explicit **No throttling** profile, allowing a specific domain to bypass a global condition.

Hosts are IDNA-normalized, lower-cased, trailing-dot normalized, and validated before persistence. Paths, queries,
request headers, and response contents are intentionally not match inputs: the destination must be selected before
an HTTP request body is needed, and the same rule must work for HTTP, WebSocket, gRPC, SSE, and tunnels.

Rule precedence is deterministic:

1. exact host plus port;
2. exact host;
3. longest matching wildcard plus port;
4. longest matching wildcard;
5. global condition;
6. unmodified traffic.

Only enabled rules participate. Equal-scope duplicates are rejected rather than resolved by hidden insertion order.
Edits publish an immutable ruleset snapshot so new flows observe the change atomically. Safe changes to the
effective profile's rates, latency, jitter, and offline state must update existing long-lived flows immediately.
Changing a rule's target or moving a flow to a different rule applies on its next connection or stream; the UI
must state this clearly.

## Condition model

Profiles have independent upstream and downstream settings. A profile can define:

- bandwidth limit in bits per second for each direction;
- bandwidth utilization percentage;
- added latency and optional bounded jitter;
- virtual MTU for shaping chunk size, not IP-layer packet rewriting;
- deterministic offline, timeout, or reset behaviour;
- optional seeded fault probability for repeatable test runs.

The initial built-in profiles should include No throttling, Offline, High latency, Slow 3G, Fast 3G, 4G, and
Lossy/unstable. Built-ins are versioned code-owned definitions and cannot be silently mutated; custom profiles are
user-owned and persisted. The UI always shows the concrete values behind a preset.

KNet must not label arbitrary application-message dropping as real packet loss. Where KNet cannot operate at a
true packet layer, the UI and persisted model call the behaviour **fault injection** and state whether it resets a
flow, times out an operation, or drops a protocol-safe unit. Phase 134 packet adapters may expose genuine datagram
loss, duplication, and reordering with clearly separate semantics.

## Architecture and ownership

### `:core:domain`

Own immutable validated values and pure policy:

- `NetworkConditionProfile`, directional limits, jitter, MTU, and fault behaviour;
- `NetworkConditionRule` and normalized exact/wildcard host scopes;
- deterministic rule matching and precedence;
- validation bounds and built-in profile identifiers.

No UI, Netty, SQL, clock, or coroutine runtime type enters these values.

### `:application:desktop`

Own use cases and ports:

- observe and edit global state, profiles, and domain rules;
- prepare a rule from a canonical Traffic transaction;
- expose immutable runtime snapshots to the data plane;
- observe runtime application/statistics without leaking engine types;
- enable, disable, reset, import, and export conditions;
- coordinate navigation intent for Traffic quick-add.

The Traffic feature calls only this application boundary. Host extraction uses captured canonical target metadata,
not a UI-formatted URL string.

### `:data:desktop` and `:storage`

Persist versioned condition state transactionally:

- master enabled state and global profile selection;
- custom profiles;
- normalized domain rules and selected profile references;
- the last quick-add profile preference;
- schema version/migration data.

Deleting a referenced custom profile must either be rejected with its dependants or update all dependants in one
transaction. Startup with invalid persisted data fails closed to no shaping and surfaces a recoverable diagnostic.

### `:engine:simulator`

Replace the current prototype global handler with a bounded, direction-aware shaping engine. The engine owns:

- monotonic-clock scheduling;
- independent upload and download token buckets;
- aggregate fairness across concurrent flows that share a rule/profile;
- bounded queues and explicit overflow behaviour;
- deterministic seeded jitter/fault decisions;
- cancellation, shutdown, and timer cleanup;
- runtime counters that contain no sensitive request payloads.

`NetworkSimulatorManager` and `KNetNetworkSimulatorHandler` are migration inputs, not the final public contract.
The current single symmetric profile, per-channel traffic shaper, and arbitrary Netty-message drop behaviour are
not sufficient for production semantics.

### `:engine:proxy` and protocol engines

The proxy selects an effective condition from the validated destination and applies it at a transport/stream
boundary where ordering and backpressure are preserved. Integration must cover:

- HTTP/1.1 request and response streams;
- HTTP/2 child streams without blocking an unrelated stream on the same connection;
- WebSocket and GraphQL WebSocket for the whole upgraded-flow lifetime;
- gRPC messages/streams without corrupting framing;
- SSE response streams without whole-body buffering;
- CONNECT/TLS destinations after authority validation;
- paired-device proxied traffic under the same rule policy.

KNet control-plane traffic, pairing, certificate retrieval, local persistence, and the desktop UI are always
excluded. A condition cannot throttle the channel used to disable that condition.

Direct API Studio requests must use the same condition service or a deliberate internal proxy route. The final UI
will show whether conditions apply to **Captured traffic**, **API Studio**, or both; the default is both, excluding
KNet's own control plane.

### Phase 134 packet data plane

UDP, QUIC, and WebRTC require a data plane below the HTTP proxy. Add a platform-neutral packet-condition contract
to the application boundary and implement it with qualified platform VPN/TUN adapters. Existing Android
`VpnService`/TUN and iOS `NEPacketTunnelProvider` paths are integration inputs, not proof of generic UDP support.
Desktop support requires its own managed packet-tunnel or operating-system traffic-control adapter with explicit
availability and permission state.

The packet engine owns:

- bounded IPv4/IPv6 and UDP parsing and forwarding without exposing native packet buffers to UI/domain layers;
- per-flow state keyed by protocol, addresses, and ports, with safe expiry and connection-migration handling;
- aggregate bidirectional token buckets shared by all flows selected by the same effective rule;
- monotonic latency/jitter scheduling and bounded datagram queues;
- explicit packet loss, duplication, and bounded reordering policies;
- route, DNS, MTU, checksum, fragmentation, socket-protection, and recursive-tunnel safety;
- runtime counters and capability state without payload logging.

Domain attribution is not always trustworthy for opaque UDP. Packet-mode rules therefore add protocol, IP/CIDR,
and destination-port scopes. DNS observation may associate a domain with a flow when provable, but IP/CIDR/port
matching remains available and the UI must label inferred attribution. Encrypted Client Hello, direct IP use,
shared CDN addresses, and connection migration must not be represented as certain domain matches.

Each flow is shaped by exactly one data plane. Traffic already limited inside the HTTP proxy must be marked or
routed to bypass packet-layer shaping, preventing an intended `100 kbps` limit from being applied twice. KNet's
control, pairing, DNS-policy, and tunnel-carrier traffic must remain reachable and excluded from user conditions.

Transport-specific outcomes are:

- **WebRTC over UDP:** shape STUN/TURN, DTLS, SRTP, and SRTCP flows as opaque datagrams across ICE path changes.
  Bandwidth, latency, jitter, loss, duplication, reordering, offline, and recovery can be tested without decrypting
  or recording media. Media decoding and call-content inspection are a separate capability.
- **HLS/DASH over HTTP/3:** shape the underlying QUIC/UDP flow so application adaptation and buffering can be
  tested. This does not by itself expose HTTP/3 URLs, headers, or media segments in Traffic; semantic HTTP/3
  inspection still requires the separate QUIC/H3 transport work tracked by the protocol roadmap.
- **Custom UDP:** shape arbitrary qualified UDP flows using protocol/IP/CIDR/port rules, while keeping their
  payload opaque unless a separately qualified protocol inspector exists.

## Runtime behaviour and observability

- Disabling Network Conditions atomically bypasses new shaping and releases queued work safely.
- A configured bandwidth is aggregate per effective rule, not multiplied by connection, request, HTTP/2 stream,
  QUIC stream, or UDP flow count.
- Rate changes apply to active flows on the next scheduler decision and do not grant a stale accumulated burst.
- Bandwidth is measured over payload bytes at the documented integration layer; counters do not claim wire-level
  TCP/TLS overhead accuracy. Packet-mode counters separately measure actual forwarded IP/UDP datagram bytes.
- Latency and jitter are additive delays. They never reorder bytes or protocol frames.
- Queues have per-flow and aggregate byte/time limits. Overflow follows the selected explicit fault policy rather
  than consuming unbounded memory.
- Every captured exchange records the condition/rule/profile identity that was selected, including an explicit
  global/default source. Traffic details can therefore explain observed slowness.
- Runtime diagnostics expose bytes delayed, current queue size, applied delay, and faults, but never body contents,
  secrets, or full URLs.

## Delivery slices

### 133.1 — Domain model, matching, and persistence

- Add validated values, built-in definitions, matcher, application ports/use cases, schema, repositories, and DI.
- Add migrations and deterministic exact/wildcard/port precedence tests.
- Keep the runtime disabled; no proxy behaviour changes in this slice.

### 133.2 — Bounded shaping engine and HTTP/1 integration

- Replace the simulator prototype with bidirectional aggregate token buckets, bounded scheduling, and lifecycle
  management.
- Integrate validated destination selection with HTTP/1 and CONNECT/TLS flows.
- Add applied-condition capture metadata and performance counters.

### 133.3 — Desktop destination and Traffic quick-add

- Add `DesktopDestination.NetworkConditions` before Breakpoints/Intercepts.
- Add the dedicated UI module, screen, MVI state, profile editor, global control, and domain-rule management.
- Add the Traffic context-menu action and prefilled, duplicate-aware editor flow.
- Persist master state, rule edits, and last quick-add profile.

### 133.4 — Protocol and API Studio parity

- Qualify HTTP/2, WebSocket, GraphQL WebSocket, gRPC, SSE, and paired-device traffic.
- Apply conditions to direct API Studio execution through the common runtime boundary.
- Prove long-lived flow updates, cancellation, and shutdown.

### 133.5 — Advanced fidelity and automation

- Add virtual MTU, utilization, seeded fault injection, and repeatable named scenarios.
- Add import/export and an authenticated local automation boundary suitable for later CLI use.
- Publish comparison/qualification evidence before marking the feature supported.

### Phase 134 — Packet-level streaming conditions

- Add the packet-condition application contract, flow/rule scopes, capability reporting, and data-plane selection.
- Implement bounded UDP shaping through platform VPN/TUN adapters without double-shaping proxied TCP traffic.
- Qualify WebRTC over UDP, HLS/DASH over HTTP/3/QUIC, and custom UDP streaming independently.
- Add packet loss, duplication, reordering, IP/CIDR/port matching, and packet-mode runtime diagnostics.
- Integrate Android first where the existing `VpnService`/TUN boundary is already physically exercised; promote
  iOS only after an entitlement-signed physical-device packet-tunnel run. Desktop adapters require their own
  platform permission, lifecycle, sleep/wake, route-change, and coexistence evidence.

Slices 133.4, 133.5, and Phase 134 may ship behind experimental capability flags, but unsupported transports must
be shown as such. Presence of a control or an existing VPN class is not evidence of transport qualification.

## Acceptance and qualification gates

The feature is complete only when automated evidence demonstrates:

- matching/normalization for DNS, IDNA, IPv4, IPv6, wildcard, port, duplicates, and precedence;
- persistence round trips, migration, corrupt-state fallback, referenced-profile deletion, and restart recovery;
- global, override, bypass, disabled, and live-update behaviour;
- independent upload/download limits within documented tolerance;
- aggregate rate fairness across concurrent connections and HTTP/2 streams;
- a sustained 100 kbps streaming workload that averages within documented tolerance, does not accumulate
  multi-second bursts, and responds immediately to live rate/offline/recovery changes;
- HLS/DASH byte-range and parallel-segment workloads that remain incremental and bounded;
- latency/jitter bounds with a deterministic monotonic test clock;
- bounded memory under at least 500 MiB of offered traffic and slow/offline consumers;
- byte/frame ordering, no corruption, no duplicate delivery, and cancellation cleanup;
- timer, executor, channel, and queue release across stop/start and application shutdown;
- HTTP/1.1, HTTP/2, WebSocket, GraphQL WebSocket, gRPC, SSE, CONNECT/TLS, paired-device, and API Studio matrices;
- control-plane exclusion and immediate recovery after the master switch is disabled;
- Traffic quick-add host/port extraction, duplicate handling, navigation, prefill, and unavailable-state messaging;
- accessibility, keyboard navigation, empty/error/loading states, and sidebar ordering;
- disabled-mode latency/throughput regression within an agreed baseline;
- affected module tests, protocol qualification tasks, `verifyArchitectureFoundation`, and `git diff --check`.

Manual qualification must also compare actual transfer time, first-byte delay, long-lived streaming behaviour, and
concurrent fairness against the configured values on macOS before capability promotion.

Phase 134 has additional, independent promotion gates:

- UDP upload/download bandwidth, latency, jitter, loss, duplication, and reordering distributions with seeded
  deterministic tests and bounded queues;
- aggregate 100 kbps sharing across multiple UDP flows without double-shaping proxy-owned traffic;
- a real WebRTC call workload proving media startup, degradation, rebuffer/freeze behaviour, ICE path changes,
  offline transition, and recovery without inspecting SRTP content;
- an HTTP/3 HLS/DASH workload proving QUIC traffic is shaped across concurrent streams and connection migration,
  while opaque flows are not falsely shown as decoded HTTP transactions;
- custom UDP echo and sustained-stream workloads over IPv4 and IPv6 with protocol/IP/CIDR/port rule precedence;
- VPN/TUN recursion prevention, DNS continuity, control-plane reachability, MTU/fragment handling, route changes,
  sleep/wake, cancellation, shutdown, and immediate disable/recovery;
- Android physical-device evidence, entitlement-signed iOS physical-device evidence, and platform-specific desktop
  evidence before each adapter is promoted independently.

## Explicit non-goals for the initial proxy release

- kernel-level TCP packet loss, reordering, or congestion-control emulation;
- UDP, QUIC, HTTP/3, and WebRTC shaping inside the HTTP proxy; these are delivered only through Phase 134's
  separately qualified packet data plane;
- matching by full URL/path, payload, account, or secret-bearing header;
- remote unauthenticated control of conditions;
- throttling KNet's pairing, certificate, discovery, or UI control plane;
- silently treating the existing simulator prototype as release-ready.

These boundaries keep the first release honest while Phase 134 adds packet-level streaming conditions without
pretending that an application proxy can provide them.

Phase 134 still does not promise WebRTC media decoding, generic QUIC decryption, semantic HTTP/3 capture, arbitrary
UDP payload inspection, radio/cellular tower emulation, or exact reproduction of a specific operating system's TCP
congestion controller. Those require separate protocol, key-access, or platform capabilities.

## Charles parity commitment

Phase 133 targets feature parity with Charles network throttling for traffic supported by KNet's proxy, followed
by KNet-specific workflow and observability improvements. The parity target includes:

- global throttling and independently enabled selected-host rules;
- exact hosts, wildcard domains, optional ports, and asymmetric upload/download shaping;
- arbitrary bandwidth and added-latency values;
- utilization percentage and virtual MTU controls;
- built-in presets, user-defined profiles, and persisted activation state;
- unstable-network/chaos behaviour through explicitly defined jitter and fault-injection semantics;
- activation at application startup;
- authenticated automation capable of selecting, enabling, and disabling a saved profile.

Phase 133.3 alone is not Charles parity. Phase 133.4 supplies required protocol coverage and Phase 133.5 supplies
the remaining fidelity and automation controls. KNet may claim Charles-level coverage only after every applicable
acceptance gate in this plan passes for KNet-supported proxy traffic.

KNet will exceed the comparable workflow by adding Traffic-row rule preparation without URL copying, explicit
API Studio application, per-exchange applied-rule/profile evidence, protocol-aware stream qualification, and
paired-device integration.

Charles parity does not require kernel-level packet loss, TCP reordering, congestion-control emulation, UDP, QUIC,
or HTTP/3. KNet tracks the requested UDP/QUIC streaming expansion separately in Phase 134 and will claim each
transport only after its lower data plane and qualification gates pass. The comparison baseline is Charles's
official
[throttling documentation](https://www.charlesproxy.com/documentation/proxying/throttling/),
[feature history](https://www.charlesproxy.com/documentation/version-history/old-versions/),
[web interface](https://www.charlesproxy.com/documentation/using-charles/web-interface/), and
[command-line options](https://www.charlesproxy.com/documentation/using-charles/command-line-options/).
