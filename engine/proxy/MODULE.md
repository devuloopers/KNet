# `:engine:proxy`

## Responsibility

Implements the high-throughput proxy transport: listeners, channels, TLS interception integration, upstream connections, and raw HTTP exchange flow.

## Owns

- Netty proxy server and channel pipeline lifecycle.
- Connection-scoped resources, enforced timeout/admission limits, bounded HTTP/1 exchange ordering, transport backpressure, and mapping at transport boundaries.
- Instance-owned extension points, a persistence-neutral streaming capture sink, and protocol transport adapters.
- Streaming requests and responses with bidirectional writability coupling, bounded capture reservations, typed cancellation, and constant-time event-loop lag metrics.
- A listener-preserving child-connection close boundary for real network transitions and terminal shutdown.
- Protocol-neutral pre-forward exchange admission and one-shot capture handoff across optional forwarding gates.
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
Strict upstream TLS uses the host JVM trust roots. KNet's interception CA is downstream identity material and is
never added to upstream trust; insecure upstream validation remains an explicit runtime policy.
