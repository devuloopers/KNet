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

## Live throughput monitor implementation checkpoint — 2026-10-04

Status: **IMPLEMENTED; AUTOMATED QUALIFICATION PASSES**

Delivered:

- exact-once post-shaper payload accounting for shaped, pass-through, live-release, and virtual-MTU paths;
- subscriber-driven one-second telemetry publication with no per-packet presentation snapshots;
- an elapsed-time-aware, reset-safe 60-sample primitive ring buffer that stops with the screen lifecycle;
- a KNet-native Compose Canvas graph for actual download/upload throughput and honest configured references;
- equal-width/equal-height desktop overview cards, compact stacking, current rates, runtime metrics, and accessibility;
- engine, channel-handler, rate-calculator, bounded-history, ViewModel lifecycle, product, and architecture coverage.

Physical streaming observation and profiler captures remain part of the broader capability-promotion acceptance gates;
they are not required for retaining the bounded 60-sample graph implementation.

### Decision and measurement contract

KNet will render the Network Conditions graph itself with Compose `Canvas`; no chart dependency will be added.
The required graph is deliberately small: a 60-second window, upload and download paths, a grid, and an optional
configured-rate reference. Keeping this code in `:ui:desktop:networkConditions` preserves the KNet theme, avoids
third-party chart state and animation machinery, and adds no runtime dependency.

The graph shows **observed application payload throughput after KNet releases bytes from the shaping boundary**.
It does not plot the configured rate as if it were measured traffic, and it does not claim NIC throughput, TCP
acknowledgement rate, TLS/IP overhead, or the device's total network usage. A configured bandwidth is drawn only
as a labelled dashed reference. When enabled domain rules mean that the aggregate graph contains multiple limits,
the UI shows `Mixed profiles` and omits a misleading single limit line.

The cumulative runtime counters must first be made complete and unambiguous:

- count upload and download bytes at the common forwarding point, after any shaping delay and immediately before
  KNet releases the unit to the next pipeline stage;
- include shaped, unlimited, global, domain-rule, and master-bypassed proxy traffic that crosses the Network
  Conditions handler, while excluding failed, abandoned, or merely queued bytes;
- separate queue accounting from forwarded-byte accounting so releasing a queued unit cannot double count it;
- update primitive atomic counters per forwarded unit without allocating or publishing a UI model per packet;
- coalesce runtime publication to a one-second monotonic sample for the graph, while preserving correct active-flow,
  queued-byte, fault, and applied-rule totals;
- identify the measurement source as proxy/application payload bytes. Packet/VPN telemetry may later contribute a
  separately labelled series through the same application contract, but incompatible proxy and IP-datagram byte
  layers must not be silently combined.

### Bounded data and lifecycle

The presentation layer retains exactly 60 one-second samples for each direction. It uses fixed-size primitive ring
buffers and replaces only the immutable chart snapshot consumed by Compose. Two 60-entry `LongArray` histories
hold 960 bytes of raw rate data; timestamps or validity flags keep the total raw history below 2 KiB. History is
session-only and is neither written to Room nor retained in Traffic records.

Sampling starts only while the Network Conditions presentation state is observed and stops when the screen leaves
composition, allowing a short sharing timeout to avoid churn during navigation. A delayed tick uses its actual
monotonic elapsed duration instead of assuming exactly one second. Counter resets, process restarts, and unsigned
overflow produce a zero/unknown sample rather than a negative spike. The screen redraws at most once per sample;
packet arrival never directly invalidates the Canvas.

### UI composition

At desktop widths, **Global condition** and **Live application** become equal-width, equal-height cards. At compact
widths they stack at full width. The cards continue to use `KNetSurface`, KNet spacing, typography, semantic colors,
and existing controls.

The Live application card contains:

- current actual download and upload rates with explicit `kbps`/`Mbps` units;
- a KNet-native 60-second Canvas graph with download and upload paths, subtle grid lines, and `60s`/`Now` anchors;
- an optional dashed configured-rate reference only when one honest aggregate reference exists;
- active-flow and queued-byte indicators, while cumulative byte totals move to secondary text/tooltips;
- a quiet zero baseline and `Waiting for traffic` state rather than fabricated activity;
- an accessibility description containing the current upload/download rates, configured reference when present,
  active-flow count, and queued bytes. Color is never the only distinction between upload and download.

Canvas drawing retains reusable paths and the dashed-line effect rather than recreating them for every sample. It
has no zoom, scrolling, point markers, per-point animation, or retained bitmap. Scaling uses the larger of the
visible measured peak and an available configured reference, with stable headroom and human-readable tick labels
to prevent distracting rescaling for small fluctuations.

### Implementation slices

#### 133.6.1 — Correct observed-byte telemetry

- Extend the application telemetry model with an explicit monotonic sample time and documented observed-forwarded
  byte semantics.
- Refactor `NetworkConditionEngine` queue release and byte delivery into separate operations.
- Instrument the shared `NetworkConditionChannelHandler` forwarding path exactly once for shaped and pass-through
  units, including virtual-MTU chunks and live disable/release.
- Coalesce telemetry publication so high-throughput traffic does not allocate a snapshot for every chunk.

#### 133.6.2 — Throughput sampler and presentation state

- Add a clock-injected rate calculator that derives bits per second from consecutive cumulative snapshots.
- Add the bounded 60-sample ring buffer and chart snapshot to `NetworkConditionsState`.
- Derive the configured reference from the effective global profile only when it truthfully represents the graph;
  otherwise expose an explicit unlimited, mixed-profile, or unavailable state.
- Tie collection to the screen/ViewModel subscription lifecycle and leave history unpersisted.

#### 133.6.3 — KNet Canvas graph and equal overview cards

- Change the wide overview layout to equal `1f` weights and a shared measured height; preserve stacked compact mode.
- Build a private reusable `LiveThroughputGraph` from Compose primitives already present in the UI module.
- Integrate current-rate labels, legend, metric tiles, empty state, reference line, and accessibility semantics into
  the Live application card without adding a chart library.
- Preserve the existing KNet dialogs, domain-rule workflow, responsive scrolling, and keyboard behavior.

#### 133.6.4 — Verification and performance gate

- Run affected application, simulator, Network Conditions UI, proxy integration, architecture, and formatting
  checks.
- Add a sustained-stream test and a disabled/pass-through test proving that measured rate follows forwarded bytes,
  not the configured value.
- Compare an idle screen and a 100 kbps stream with the graph visible and hidden; the retained history must remain
  fixed at 60 samples and the sampler must stop after its lifecycle timeout.
- Record a JVM allocation/heap smoke baseline. The graph must introduce no unbounded collections, retained payload
  buffers, per-packet UI objects, or continuously growing history.

### Required automated cases

- shaped download and unlimited upload produce independent observed rates;
- pass-through traffic is counted, while queued, failed, and abandoned bytes are not;
- virtual-MTU splitting and live queue release count every forwarded byte exactly once;
- irregular sampling intervals use elapsed monotonic time and never produce negative rates;
- counter reset, long-idle periods, very large totals, and a zero elapsed interval fail safely;
- the ring buffer remains at 60 samples after long runs and begins with a zero/unknown baseline;
- 100 kbps sustained traffic settles within the shaping tolerance while idle intervals fall to zero;
- configured limits are hidden for unlimited and mixed-profile aggregates and shown with correct units otherwise;
- wide overview cards have equal width/height, while compact layouts stack without clipping;
- Canvas scaling handles zero, sub-kbps, Mbps, and sudden peak values without invalid coordinates;
- leaving the screen stops sampling and returning starts a fresh, bounded monitoring window;
- UI state and accessibility text contain counters and rates only, never destinations, URLs, headers, or payloads.

### Definition of done

This enhancement is complete when the graph displays measured post-shaper payload throughput rather than a
decorative configured curve, the two overview cards are visually equal at desktop widths, runtime telemetry counts
all eligible forwarding paths exactly once, memory remains bounded independently of run duration, and the required
automated and JVM allocation checks pass. No Vico, KoalaPlot, or other chart dependency is introduced.

## HTTP, gRPC, and WebSocket semantic rule checkpoint — 2026-10-04

Status: **IMPLEMENTED; AUTOMATED QUALIFICATION PASSES**

The shared protocol-extension model now covers HTTP method/queryless-path rules, native gRPC service/method rules,
generic WebSocket handshake and logical-message rules, GraphQL HTTP operations, and modern GraphQL WebSocket
operations. The editor presents those scopes in a stable order after **All traffic**, with the shared visible
horizontal scrollbar used by profile and criteria chip strips. Traffic quick-add chooses the highest-confidence
protocol suggestion: GraphQL WebSocket before generic WebSocket, gRPC before HTTP, and GraphQL HTTP before HTTP.

HTTP and gRPC selection is header-only and retained for the correlated response, including an independent HTTP/2
child stream. HTTP supports optional exact method plus exact or segment-aware path-prefix selection. gRPC requires a
native media type, `POST`, and the canonical `/service/method` path. SSE does not need a separate semantic scope;
its HTTP request method/path can be selected while the response remains incrementally streamed.

The WebSocket engine owns one bounded complete-message shaper for every valid upgrade. It reassembles fragments,
decodes negotiated per-message deflate before semantic inspection, retains original wire bytes for forwarding, and
resolves generic path/subprotocol/direction/kind rules together with GraphQL operation rules. Because this one
transformer owns the Network Conditions boundary, overlapping generic and GraphQL rules cannot double-shape a
message. Invalid criteria and unsupported traffic fail closed to the ordinary destination/global fallback.

GraphQL remains the only HTTP condition extension that may request bounded complete-body aggregation. A header
preflight avoids buffering unrelated gRPC, WebSocket, SSE, and explicit non-GraphQL media on the same host. Generic
HTTP and gRPC streams therefore preserve streaming semantics even when a GraphQL condition exists for that domain.

## Protocol-aware GraphQL rule checkpoint — 2026-10-04

Status: **IMPLEMENTED; AUTOMATED QUALIFICATION PASSES**

Delivered:

- independently persisted rule priority and semantic criteria in Room schema 32, while existing schema-31 rules
  migrate to priority `0` and the payload-blind `transport` scope;
- a generic application-layer protocol-extension registry that owns validation, compact editor fields, bounded
  inspection, quick-add suggestions, and fail-closed matching without exposing scheduling or persistence details;
- GraphQL HTTP selectors for operation name and query/mutation/subscription type, resolved once per bounded request
  and retained for its correlated response on HTTP/1.1 and HTTP/2 stream pipelines;
- modern `graphql-transport-ws` selectors for direction, message type, operation name, and operation ID, with
  bounded multiplexed-operation correlation and complete-message shaping after fragmentation/compression decoding;
- one ordered upgraded-connection transform chain, so breakpoint edits run before Network Conditions and GraphQL
  WebSocket traffic is not shaped once by the raw channel and again by the message boundary;
- KNet-themed generic rule controls, friendly protocol badges, explicit priority, and Traffic one-click semantic
  prefill that prefers the completed Traffic annotation before bounded raw-body fallback, without adding
  protocol-specific presentation dependencies;
- domain, application, HTTP runtime, GraphQL HTTP, GraphQL WebSocket, persistence/migration, ViewModel, proxy
  composition, product-DI, and qualification-gate tests.

The semantic claim is intentionally bounded. GraphQL HTTP matching requires a decrypted request whose relevant body
fits the one-mebibyte inspection limit, and GraphQL WebSocket matching requires the negotiated modern
`graphql-transport-ws` subprotocol. Invalid, missing, oversized, encrypted, or unsupported payloads use the normal
destination/global fallback; they never silently turn a semantic rule into a whole-domain rule. Raw WebSocket now
has its own path/subprotocol/direction/kind condition scope. Legacy `graphql-ws` remains eligible for generic
WebSocket matching but does not receive modern GraphQL operation correlation. Opaque TLS, QUIC/HTTP/3, and custom
UDP remain destination/packet-scoped until separately implemented and qualified.

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

Every new rule opens with **No throttling** selected so adding a destination cannot unexpectedly degrade traffic
before the user deliberately chooses a simulation profile. The user chooses a different profile when needed and
confirms **Add condition**. No URL, scheme, path, query, or host needs to be copied. Confirmation is deliberate
because enabling a rule changes live traffic. Later, a context submenu may offer literal immediate application of
a named preset, but that is not required for the first release.

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
- the legacy last quick-add profile value for storage compatibility, although new editors ignore it and default to
  No throttling;
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
- Persist master state and rule edits; preserve the legacy last quick-add profile field for storage compatibility.

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
