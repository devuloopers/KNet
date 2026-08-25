# `:engine:protocol`

## Responsibility

Hosts protocol-specific parsers and inspectors that derive structured views or stream events from captured traffic.

## Owns

- GraphQL semantic inspection implemented against the application inspector contract.
- The GraphQL live-breakpoint extension: versioned typed criteria, bounded detection, compiled
  operation matching, compact cross-phase observations, and smart captured-rule suggestions.
- A shared Kotlin serialization GraphQL document parser used by both live breakpoint matching and
  asynchronous post-capture inspection.
- Protocol-specific parsing that consumes bounded captured content and emits generic versioned inspection documents.

## Does not own

- Proxy transport lifecycle, canonical HTTP exchanges, storage, UI, or connectivity.

## Dependency rule

Inspectors consume stable traffic contracts and emit protocol-specific derived data. The proxy core must not depend on individual inspectors.

## Current state

GraphQL is registered additively and runs after capture. SSE parsing, historical inspection, live capture,
Traffic decoding, API Studio interpretation, and response-record breakpoints have moved to the independent
`:engine:sse` extension described in `docs/sse_target_and_implementation_plan.md`; this module no longer owns or
duplicates SSE behavior. GraphQL also registers an independent live-breakpoint extension because request-phase
pauses must be decided before
capture completes; that extension receives only the bounded candidate owned by the application gate.
When a breakpoint is created from captured traffic, the same extension reuses the shared parser to select
GraphQL and prefill a single operation name. Anonymous, batched, or body-incomplete GraphQL requests remain
endpoint-scoped rather than guessing one operation.
Dormant WebSocket/protobuf stubs were removed. Experimental HTTP/2 transport lives in `:engine:proxy` and does
not change this semantic-inspection boundary. WebSocket and gRPC now live in independent experimental engine
modules; HTTP/3 remains unavailable until a real implementation passes its conformance gates.
