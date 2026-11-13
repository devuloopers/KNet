# `:engine:protocol`

## Responsibility

Hosts protocol-specific parsers and inspectors that derive structured views or stream events from captured traffic.

## Owns

- GraphQL semantic inspection implemented against the application inspector contract.
- The GraphQL live-breakpoint extension: versioned typed criteria, bounded detection, compiled
  operation matching, compact cross-phase observations, and smart captured-rule suggestions.
- A shared bounded HTTP source decoder and GraphQL AST parser used by live matching and asynchronous
  post-capture inspection.
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

## GraphQL HTTP source ownership

`GraphQLHttpDecoder` owns bounded POST JSON and GET form decoding, strict UTF-8 percent decoding,
operation selection, AST formatting and source-preserving envelope edits. It reuses the existing graphql-java
version with per-call depth/token/character limits; parser defaults are never changed globally.
The common `:core:domain` GraphQL contracts distinguish source, supplied selector, resolved operation and
diagnostic. Breakpoints, Network Conditions and semantic schema version 3 consume these same facts.
Ambiguous/invalid requests cannot produce named operation matches; raw forwarding is unchanged.
The older `GraphQLDocumentParser` API is a compatibility adapter, including GraphQL WebSocket use.

## GraphQL persisted queries

Owns exact UTF-8 SHA-256 validation, APQ metadata extraction, endpoint-scoped resolution and the synchronized
256-entry/4 MiB/30-minute LRU catalog implementing the application contract. Complete captured bodies (including
bounded Content-Encoding decoding) and atomic local manifests can teach source mappings; authoring cannot.
Resolved documents stay separate from captured query fields and drive conservative live operation matching.
Semantic schema version 3 adds persisted hash and source provenance. See `docs/graphql_persisted_queries.md`.
