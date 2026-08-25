# `:engine:proxy`

## Responsibility

Implements the high-throughput proxy transport: listeners, channels, TLS interception integration, upstream connections, and raw HTTP exchange flow.

## Owns

- Netty proxy server and channel pipeline lifecycle.
- Connection-scoped resources, enforced timeout/admission limits, bounded HTTP/1 exchange ordering, transport backpressure, and mapping at transport boundaries.
- HTTP/2 downstream negotiation through TLS ALPN, H2C prior knowledge, and H2C Upgrade, with one isolated Netty
  child channel and canonical stream identity per logical exchange.
- A bounded, origin-keyed upstream HTTP/2 pool with TLS ALPN, independent stream leases, global admission,
  connections-per-origin and streams-per-connection limits, server-push refusal, and GOAWAY replacement.
- Instance-owned extension points, a persistence-neutral streaming capture sink, and protocol transport adapters.
- Streaming requests and responses with bidirectional writability coupling, bounded capture reservations, typed cancellation, and constant-time event-loop lag metrics.
- Qualified HTTP/1.0 and HTTP/1.1 wire semantics: version-correct generated responses, explicit downstream
  persistence, proxy-header normalization, and safe streaming translation of HTTP/1.1 chunked responses for
  HTTP/1.0 clients.
- A listener-preserving child-connection close boundary for real network transitions and terminal shutdown.
- Protocol-neutral pre-forward exchange admission and one-shot capture handoff across optional forwarding gates.
- Protocol-neutral validated HTTP/1.1 Upgrade handoff plus backpressured raw duplex relaying, with optional
  direction-aware observers and transformers supplied by protocol modules.
- Boundary-only consumption of local capture-origin metadata before canonical header mapping, breakpoint
  editing, and upstream forwarding.
- Boundary-only containment of Netty HTTP/2 object-bridge extension headers. Scheme and stream identity remain
  typed canonical metadata; bridge fields never appear in captured headers or cross an HTTP/1 wire.
- A reusable protocol-neutral selective HTTP/1 aggregator that falls back to ordered streaming when a
  selected message crosses its bound instead of rejecting otherwise valid traffic.
- The `ServerTlsContextProvider` transport port and scheduling boundary used for CONNECT interception; its implementation is injected by desktop data.

## Does not own

- Compose UI, database persistence, connectivity setup, pairing/credential validation, relay, portal delivery, feature use cases, or protocol-specific inspection policy.

## Dependency rule

Depends inward on stable contracts and injected TLS/traffic extension interfaces. It has no production dependency on `:engine:certificate` and must never depend on `:ui:*`, `:products:*`, `:data:*`, or `:connectivity:*`.

## Current state

The Netty implementation is behind `ProxyRuntimePort`, defaults to loopback plus strict upstream TLS, rolls
back failed starts, and awaits shutdown. A single streaming proxy handler owns production forwarding; the
former duplicate full-message handler is removed. Ordinary traffic streams bidirectionally. Optional
inspection adapters select bounded aggregation per message, and overflow replays the retained head/chunks then
continues streaming. Rule changes therefore need no pipeline mutation or client reconnect. On selected paths,
canonical exchange metadata is admitted before a forwarding gate can pause and the same capture handle is
consumed after resume. Response capture becomes terminal only after final downstream delivery; premature
upstream/downstream closure also releases exchange ownership. Optional ingress attribution consumes a neutral
one-shot socket identity contract.
HTTP/1.0 is a qualified transport path rather than incidental codec compatibility: absolute-form requests do
not require `Host`, request bodies remain content-length delimited, persistence is opt-in through `Connection`
or legacy `Proxy-Connection`, and non-self-delimiting upstream responses are streamed as close-delimited output
without chunk markers or trailers. CONNECT interception returns the client's HTTP/1 version and then reuses the
same TLS interception pipeline as HTTP/1.1. Request and upstream response protocols are captured independently
before any downstream HTTP/1 compatibility rewrite. The local attribution header becomes canonical
`TrafficOrigin` and is then removed, so it never appears in captured headers or on the origin-server wire.
Strict upstream TLS uses the host JVM trust roots. KNet's interception CA is downstream identity material and is
never added to upstream trust; insecure upstream validation remains an explicit runtime policy.
HTTP/2 streams reuse the canonical HTTP object/capture/breakpoint boundary after Netty's frame codec maps
pseudo-headers, DATA, and trailers. Each child has its own forwarding state, body queue, capture handle, timeout,
and breakpoint gate; no multiplexed connection shares an HTTP/1 response deque. TLS origins prefer pooled HTTP/2
and fall back to the existing HTTP/1.1 connector only when ALPN explicitly reports that HTTP/2 is unavailable.
Transport, saturation, TLS, reset, and pool failures remain failures rather than silent downgrades. Request and
response protocols are recorded independently, connection-specific headers are removed before HPACK encoding,
Netty object-bridge headers remain private to HTTP/2 codec conversion, and request/response trailers are captured
separately from initial headers. Netty owns SETTINGS, PING,
WINDOW_UPDATE, CONTINUATION, HPACK, RST_STREAM, and GOAWAY wire conformance at this boundary.
Accepted HTTP/1.1 Upgrade responses must confirm both the requested protocol token and `Connection: Upgrade`
before the HTTP codec is removed. The resulting raw relay owns read/write coupling and channel lifetime while
injected protocol modules optionally inspect or transform owned byte slices; the proxy itself contains no
WebSocket framing or semantic rules.
Real-socket HTTP/2 resilience tests prove malformed connection traffic receives GOAWAY without terminating the
listener, PING acknowledgement preserves stream admission, and repeated parent-connection churn leaves forwarding
healthy. The root `http2Qualification` task composes these transport tests with canonical model, persistence,
protocol-lab, API Studio, Traffic, and desktop composition gates; real-device Wi-Fi and release-soak evidence
remain future maturity work.
