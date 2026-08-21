# KNet HTTP/2 Target and Implementation Plan

Status: **IMPLEMENTED — LOCAL JVM QUALIFICATION COMPLETE**  
Current runtime capability: **EXPERIMENTAL**  
Target: production-qualified HTTP/2 proxy interception and API Studio execution without replacing the HTTP/1,
traffic, breakpoint, persistence, connectivity, or protocol-inspection architecture.

## 1. Definition of complete HTTP/2 support

KNet may mark HTTP/2 as `SUPPORTED` only when all of the following work through real sockets:

- Downstream TLS negotiation through ALPN (`h2`, with HTTP/1.1 fallback).
- Clear-text H2C prior knowledge and HTTP/1.1 Upgrade to H2C.
- Upstream TLS HTTP/2 negotiation and bounded reusable connection pooling.
- Concurrent multiplexed streams with stable connection and stream identities.
- HEADERS/CONTINUATION, DATA, SETTINGS, WINDOW_UPDATE, PING, RST_STREAM, GOAWAY, and trailers.
- Standards-compliant HPACK handling through Netty's codec.
- Per-stream flow control, cancellation, timeout, failure, and body ownership.
- HTTP/2-to-HTTP/1 and HTTP/1-to-HTTP/2 translation where either side negotiates a different version.
- Request and response breakpoints that pause only the matched stream.
- Durable capture, restart-safe paging, filtering, export-ready snapshots, and accurate protocol/source reporting.
- API Studio `AUTO` negotiation and an exact `HTTP/2` preference.
- Qualification for ordinary HTTP, streaming bodies, SSE, and the HTTP/2 transport required by future gRPC.

Standards-compliant refusal is allowed where the protocol permits it. In particular, KNet will advertise
`SETTINGS_ENABLE_PUSH = 0` upstream instead of building a server-push product feature. Unknown extension frames
must follow RFC handling rules and must never corrupt another stream.

The following are not HTTP/2 implementation failures:

- Certificate-pinned clients refusing KNet's interception certificate.
- HTTP/3, which uses QUIC and requires its own transport adapter.
- Semantic decoding of gRPC or WebSocket messages; HTTP/2 transports their bytes, while protocol inspectors own
  their meaning.

## 2. Existing repository foundation

### KEEP

- `:core:traffic` canonical request, response, exchange, protocol, origin, body-reference, and `StreamId` models.
- `:engine:proxy` as the only owner of Netty transport and reference-counted buffers.
- The qualified HTTP/1.0/1.1 path (`KNetStreamingProxyHandler`, `KNetOutboundHandler`, and `HttpOneSemantics`).
- `ProxyCaptureSink` and bounded body reservations as the non-blocking capture boundary.
- Protocol-neutral application breakpoint contracts and additive semantic extensions.
- Room canonical traffic persistence, including its existing `streamId`, request protocol, response protocol,
  and origin fields.
- Traffic filters, protocol/source presentation, API Studio response inspection, and connectivity mechanisms.
- `:testingServer` as a network-only protocol lab; production modules never depend on it.

### MODIFY

- Proxy connection setup to negotiate a protocol and select an HTTP/1 or HTTP/2 connection adapter.
- TLS contexts to advertise/consume ALPN without moving certificate ownership into the proxy.
- Capture exchange admission to accept a nullable `StreamId`.
- Canonical snapshots/events and Room schema to preserve request and response trailers independently.
- Breakpoint adaptation so HTTP/2 streams do not enter the ordered HTTP/1 deque.
- API Studio HTTP-version preference and JVM transport selection.
- Runtime capability maturity and module documentation as qualification gates are passed.

### ADD

- A small downstream protocol negotiator.
- An HTTP/2 connection handler and independently owned per-stream bridge.
- HTTP/2 canonical frame/head/trailer mappers.
- A bounded upstream HTTP/2 connection pool keyed by authority and TLS policy.
- H2C and TLS/ALPN test fixtures, fault injectors, and concurrency/backpressure qualification tests.
- Optional Traffic Stream ID presentation and HTTP/2-specific safe diagnostics.

### REMOVE

- Nothing from the working HTTP/1 path during HTTP/2 development.
- Any temporary experimental adapter must be removed before `SUPPORTED`; two production HTTP/2 paths are not
  acceptable.

## 3. Target transport boundaries

```text
TCP connection
    |
    +-- clear text detector ----------------------+-- HTTP/1 connection adapter (existing)
    |                                             |
    |                                             +-- H2C connection adapter (new)
    |
    +-- CONNECT -> KNet TLS -> ALPN --------------+-- HTTP/1 connection adapter (existing)
                                                  |
                                                  +-- HTTP/2 connection adapter (new)

HTTP/2 connection adapter
    |
    +-- connection control owner (SETTINGS/PING/GOAWAY/flow-control budget)
    |
    +-- stream channel N -> HTTP/2 stream bridge
                              |
                              +-- canonical request + StreamId
                              +-- optional per-stream breakpoint gate
                              +-- bounded capture reservations
                              +-- upstream route/stream
                              +-- canonical response/trailers/terminal state
```

The connection owner handles only connection-scoped frames and budgets. Each stream bridge owns exactly one
logical exchange. No connection-wide `activeRequest`, ordered response queue, or mutable body accumulator is
shared by HTTP/2 streams.

## 4. Runtime flow

### TLS interception

1. The existing HTTP/1 proxy receives `CONNECT` and resolves KNet's per-host certificate asynchronously.
2. The downstream server TLS context advertises `h2` and `http/1.1` through ALPN.
3. After the handshake, a protocol negotiator installs either the existing HTTP/1 pipeline or the new HTTP/2
   frame/multiplex pipeline.
4. Every HTTP/2 child stream receives its own stream bridge and canonical `StreamId`.
5. The upstream route negotiates ALPN independently. Therefore Traffic can truthfully show, for example,
   `Client HTTP/2` and `Upstream HTTP/1.1`.

### Clear-text interception

1. Detect the HTTP/2 connection preface without consuming bytes irreversibly.
2. Install the H2C pipeline for prior knowledge.
3. Preserve the existing HTTP/1 path and support a valid H2C Upgrade transition.
4. Invalid prefaces/upgrades fail the connection safely without affecting the listener.

### Per-stream forwarding

1. Validate pseudo-headers, forbidden connection headers, limits, and stream state.
2. Convert the initial header block to canonical `RequestHead` with `ApplicationProtocol.HTTP_2`.
3. Admit capture with connection ID, stream ID, source, and start time.
4. Evaluate request breakpoint policy for that stream only.
5. Forward DATA only as downstream/upstream flow-control credit and writability allow.
6. Map response headers, DATA, and trailers to the same exchange.
7. Publish exactly one terminal exchange state for normal completion, reset, timeout, GOAWAY, or connection loss.

## 5. Memory, concurrency, and backpressure rules

- Netty owns inbound frame buffers until a stream bridge explicitly retains or copies them.
- Capture may copy only bytes admitted by `ProxyBodyReservation`; capture rejection never blocks forwarding.
- Each stream has a small bounded pending-write queue and the connection has a stricter aggregate byte budget.
- A slow stream must not stop reads for unrelated streams while connection-level flow-control credit remains.
- WINDOW_UPDATE is released only after the next owner has accepted the bytes, not immediately upon decoding.
- Breakpoint aggregation is bounded per selected stream. Unmatched streams remain fully streaming.
- Pausing a matched stream must not pause its event-loop connection or another stream.
- RST_STREAM releases that stream's queued frames, reservations, breakpoint ownership, and upstream mapping.
- GOAWAY stops new stream admission, lets eligible existing streams drain, and deterministically fails streams
  that the peer has declared unprocessed.
- Connection-pool count, streams per connection, header-list size, frame size, idle time, and pending bytes are
  explicit runtime-policy values with safe defaults.

## 6. Canonical traffic and persistence changes

`:core:traffic` remains the source of truth.

- Keep request and response protocols independent.
- Normalize equivalent observed tokens such as `HTTP/2.0` to canonical `HTTP/2`.
- Pass `StreamId?` through `ProxyConnectionCapture.startExchange` into `CaptureEvent.ExchangeStarted`.
- Add explicit request/response trailer values and trailer capture events; do not merge trailers into initial
  headers.
- Keep HTTP/2 pseudo-headers out of ordinary header lists after mapping them to method, scheme, authority, path,
  and status.
- Preserve ordered duplicate ordinary headers after HPACK decoding.

Room schema v22 will add dedicated encoded request/response trailer columns. Existing rows default to no trailers;
the existing stream ID and two protocol columns require no redesign. `:data:desktop` remains the only mapper and
writer.

## 7. Breakpoint behavior

- Reuse `BreakpointGate`, `BreakpointCandidate`, typed edits, and protocol extensions.
- Add an HTTP/2 adapter that converts one stream's canonical data into the existing candidate contract.
- Keep raw HTTP/2 frames inside Netty's connection/stream codecs. Each isolated stream child converts its frames
  to the existing HTTP-object breakpoint adapter; the adapter therefore remains protocol-neutral at the
  application boundary while stream ownership stays independent.
- A request breakpoint pauses that stream before its end-of-stream is forwarded.
- A response breakpoint pauses only the selected response stream after bounded aggregation.
- Forwarded edits rebuild valid HTTP/2 header/trailer blocks and recalculate body metadata where necessary.
- Dropping an interception sends the appropriate `RST_STREAM`; it does not close the entire connection.
- Disabling/editing rules continues to affect the next eligible stream without pipeline mutation or reconnect.

GraphQL matching remains unchanged because it consumes canonical requests. Future gRPC matching will be another
protocol extension, not a branch in the HTTP/2 transport.

## 8. API Studio behavior

- Add `HTTP_2` to `HttpVersionPreference`, persistence, editor selection, and request handoff.
- Keep CIO for exact HTTP/1.1 and custom test engines.
- Use one JVM HTTP/2-capable client adapter behind `DomainHttpExecutor`; the adapter supports ALPN, local
  proxy trust, cancellation, streaming, and observed-version reporting.
- `AUTO` may negotiate/fallback according to server capability.
- Exact `HTTP_2` must fail clearly when HTTP/2 cannot be negotiated; silently reporting HTTP/1.1 is incorrect.
- API Studio attribution remains local to the KNet proxy hop.
- The response summary reports the actually negotiated protocol, while a captured Traffic exchange reports both
  client-facing and upstream protocol legs.

## 9. UI behavior

- Existing HTTP/2 count/filter becomes active from canonical data; no UI-side inference.
- Protocol and Source optional Traffic columns continue to use the common column system.
- Add an optional Stream column rather than embedding stream IDs into serial numbers or paths.
- Overview shows connection ID, stream ID, client protocol, upstream protocol, and source.
- Live Intercept shows client/upstream protocol and source using the canonical candidate already supplied.
- RST_STREAM/GOAWAY/timeouts use stable safe diagnostics; raw exceptions and frame dumps are not presentation
  models.
- No HTTP/2-specific branch is added to GraphQL/SSE inspectors or the code editor.

## 10. Implementation phases and gates

### Phase H2-0 — Baseline and fixtures

Status: **COMPLETE — 2026-08-21**

- Freeze passing HTTP/1 regression, capacity, breakpoint, capture, and TLS suites.
- Extend `:testingServer` with deterministic TLS/ALPN HTTP/2 in addition to its verified H2C listener.
- Add raw/fault fixtures for resets, GOAWAY, trailers, large header blocks, slow streams, and interleaved data.

Exit gate: the lab proves H2C and TLS `h2` negotiation independently of KNet.

Verified by the complete `:testingServer:test` contract and the existing certificate, interceptor, proxy,
HTTP-client, and desktop persistence regression suites. No production proxy pipeline changed in this phase.

### Phase H2-1 — Canonical stream/trailer contracts

Status: **COMPLETE — 2026-08-22**

- Add stream admission to the capture boundary.
- Add trailer events/snapshots, Room v22, mapper tests, and restart round trips.
- Normalize HTTP/2 protocol aliases.

Exit gate: synthetic concurrent stream events persist and restore losslessly without Netty dependencies outside
`:engine:proxy`.

### Phase H2-2 — Downstream H2C and stream isolation

Status: **COMPLETE — 2026-08-22**

- Add preface/upgrade negotiation and the HTTP/2 connection/stream adapters.
- Translate downstream HTTP/2 streams to the existing upstream HTTP/1 path first.
- Qualify concurrent requests, streaming bodies, resets, and connection-level flow control.

Exit gate: multiple H2C streams traverse KNet concurrently, appear with stable stream IDs, and cannot corrupt or
block one another. Capability remains `EXPERIMENTAL`.

### Phase H2-3 — Downstream TLS ALPN

Status: **COMPLETE — 2026-08-22**

- Add ALPN configuration to the certificate-backed server TLS context.
- Select HTTP/1 or HTTP/2 only after handshake success.
- Preserve HTTP/1 fallback and certificate-cache behavior.

Exit gate: a real TLS client negotiates `h2` through CONNECT and existing HTTP/1 clients still pass unchanged.

### Phase H2-4 — Upstream HTTP/2 and bounded pooling

Status: **COMPLETE — 2026-08-22**

- Add upstream ALPN negotiation, HTTP/2 client connections, per-origin pooling, stream leases, and graceful
  GOAWAY replacement.
- Keep independent client/upstream protocol reporting and version translation.

Exit gate: H2-to-H2, H2-to-H1, H1-to-H2, and H1-to-H1 matrices pass with accurate protocol values.

### Phase H2-5 — Breakpoints and editing

Status: **COMPLETE — 2026-08-22**

- Add per-stream request/response breakpoint adaptation and rebuild logic.
- Qualify forward unchanged, modified forward, drop, disable rule, timeout, and disconnect races.

Exit gate: pausing one of at least 20 concurrent streams does not delay the other 19.

### Phase H2-6 — API Studio

Status: **COMPLETE — 2026-08-22**

- Add exact HTTP/2 preference, the JVM HTTP/2 client adapter, persistence, cancellation, and UI selection.
- Verify direct and proxy-routed calls, CA trust, negotiation failure, and source attribution.

Exit gate: API Studio exact HTTP/2 fails closed on negotiation downgrade and reports HTTP/2 when negotiated.

### Phase H2-7 — Presentation and diagnostics

Status: **COMPLETE — 2026-08-22**

- Activate HTTP/2 count/filter, optional Stream column, overview metadata, and safe terminal diagnostics.
- Verify paging, restart, Traffic ordering, selection, and live-intercept replacement.

Exit gate: all HTTP/2 presentation derives from canonical stored/live data and survives restart.

### Phase H2-8 — Stress, security, and conformance

Status: **LOCAL AUTOMATED GATE COMPLETE — 2026-08-22**

- Run large-header/HPACK, rapid reset, slow consumer, max-concurrency, malformed frame, GOAWAY, timeout, body-limit,
  and connection-churn suites.
- Establish event-loop lag, retained-buffer, memory, throughput, and fairness baselines.
- Verify no reserved attribution, proxy-only headers, or private certificate material reaches upstream traffic.

Exit gate: no leak-detector failures, unbounded queues, cross-stream data, duplicate terminal events, or HTTP/1
regression.

### Phase H2-9 — Qualification

Status: **PARTIAL — macOS/JVM complete; Windows, Linux, Android, and iOS gates remain**

- Run the complete automated matrix on macOS, Windows, and Linux.
- Run a real Android/iOS Wi-Fi proxy smoke matrix for ALPN and certificate behavior.
- Update module docs, capability evidence, and test strategy.
- Change `http2` from `UNAVAILABLE`/`EXPERIMENTAL` to `SUPPORTED` only after every required gate passes.

The repository now contains real-socket gates for H2C prior knowledge/upgrade, TLS ALPN, 100 concurrent streams,
H2-to-H2 pooling, H1-to-H2 and H2-to-H1 translation, request/response trailers, large HPACK headers, configured
header-list rejection, RST_STREAM, GOAWAY replacement, slow-stream fairness, stream-scoped breakpoint pause/edit/
drop, API Studio exact/AUTO negotiation, proxy CA trust, local attribution, durable stream/trailer round trips,
and Traffic presentation. Extended release soak and the non-macOS/device matrix remain external qualification
work, so the capability intentionally remains `EXPERIMENTAL`.

## 11. Required qualification matrix

| Area | Required cases |
|---|---|
| Negotiation | H2C prior knowledge, H2C upgrade, TLS ALPN h2, HTTP/1 fallback, exact-H2 failure |
| Multiplexing | 1, 20, 100+ concurrent streams; interleaved DATA; independent completion order |
| Metadata | Pseudo-headers, duplicate headers, large CONTINUATION blocks, request/response trailers |
| Bodies | Empty, fixed, streaming, large bounded capture, slow producer, slow consumer, cancellation |
| Control | SETTINGS/ACK, PING/ACK, WINDOW_UPDATE, RST_STREAM, graceful and abrupt GOAWAY |
| Translation | H2-H2, H2-H1.1, H2-H1.0 where valid, H1.1-H2, fallback H1.1-H1.1 |
| Breakpoints | Request/response, one paused among many, edit, unchanged, drop, rule refresh, timeout |
| Capture | Stream ID, two protocol legs, origin, trailers, body refs, restart, paging, clear/cutover |
| Features | Ordinary HTTP, GraphQL over H2, SSE over H2, gRPC-compatible streaming/trailers |
| Platforms | Desktop macOS/Windows/Linux; Android/iOS Wi-Fi proxy smoke tests |
| Safety | Header limits, HPACK abuse, invalid frames, stream floods, leak detection, bounded memory |

## 12. Future protocol effect

- **gRPC:** reuses HTTP/2 streams, flow control, trailers, and capture. Adds message framing/Protobuf inspection in
  `:engine:protocol` and protocol-specific breakpoint criteria.
- **SSE:** already has a semantic inspector; HTTP/2 adds only a new transport source for the same body stream.
- **GraphQL:** existing canonical detection and breakpoint matching continue unchanged over HTTP/2.
- **WebSocket over HTTP/2:** adds RFC 8441 extended-CONNECT semantics and a WebSocket frame adapter after the HTTP/2
  transport is qualified.
- **HTTP/3:** reuses canonical exchanges, stream IDs, capture, persistence, breakpoints, and UI, but requires a
  separate QUIC transport implementation. HTTP/2 Netty frame types must not leak into those contracts.

## Verdict

The architecture now carries HTTP/2 additively from downstream negotiation through forwarding, capture,
breakpoints, persistence, API Studio, and Traffic without replacing the stable HTTP/1 or canonical traffic
paths. The implementation is realistically usable as an experimental desktop capability. KNet must not call it
fully `SUPPORTED` until the remaining Windows/Linux and Android/iOS Wi-Fi qualification plus release soak gates
pass; that maturity gate does not require another architecture migration.
