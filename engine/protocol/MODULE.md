# `:engine:protocol`

## Responsibility

Hosts protocol-specific parsers and inspectors that derive structured views or stream events from captured traffic.

## Owns

- GraphQL and SSE semantic inspectors implemented against the application inspector contract.
- Protocol-specific parsing that consumes bounded captured content and emits generic versioned inspection documents.

## Does not own

- Proxy transport lifecycle, canonical HTTP exchanges, storage, UI, or connectivity.

## Dependency rule

Inspectors consume stable traffic contracts and emit protocol-specific derived data. The proxy core must not depend on individual inspectors.

## Current state

GraphQL and SSE are registered additively and run after capture, outside Netty event loops. Dormant WebSocket/protobuf stubs were removed. WebSocket transport, HTTP/2, gRPC, and HTTP/3 remain explicitly unavailable until real transport implementations pass conformance gates.
