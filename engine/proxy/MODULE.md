# `:engine:proxy`

## Responsibility

Implements the high-throughput proxy transport: listeners, channels, TLS interception integration, upstream connections, and raw HTTP exchange flow.

## Owns

- Netty proxy server and channel pipeline lifecycle.
- Connection-scoped resources, enforced timeout/admission limits, bounded HTTP/1 exchange ordering, transport backpressure, and mapping at transport boundaries.
- Instance-owned extension points, a persistence-neutral streaming capture sink, and protocol transport adapters.
- Streaming requests and responses with bidirectional writability coupling, bounded capture reservations, typed cancellation, and constant-time event-loop lag metrics.
- A listener-preserving child-connection refresh boundary for composition-owned per-connection capability changes.
- Protocol-neutral pre-forward exchange admission and one-shot capture handoff across optional forwarding gates.

## Does not own

- Compose UI, database persistence, connectivity setup, pairing/credential validation, relay, portal delivery, feature use cases, or protocol-specific inspection policy.

## Dependency rule

Depends inward on stable contracts and injected certificate/traffic extension interfaces. It must never depend on `:ui:*`, `:products:*`, `:data:*`, or `:connectivity:*`.

## Current state

The Netty implementation is behind `ProxyRuntimePort`, defaults to loopback plus strict upstream TLS, rolls back failed starts, and awaits shutdown. Default full-message aggregation, in-place mutation of established pipelines, portal routing, the duplicate pool, and callback capture are gone. Ordinary traffic streams bidirectionally; enabled request or response breakpoints may select bounded aggregation through injected capability predicates. When composition changes such a predicate, it may close active child connections while keeping the listener available so reconnects receive one internally consistent pipeline. On aggregated paths, canonical exchange metadata is admitted before an optional forwarding gate can pause and the same capture handle is consumed after resume; capture is never restarted for that exchange. Queued handoffs are explicitly cancelled if the downstream closes or exceeds the bounded pipeline limit. Optional ingress attribution consumes a neutral one-shot socket identity contract.
