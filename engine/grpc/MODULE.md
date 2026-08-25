# `:engine:grpc`

## Responsibility

This JVM module recognizes native gRPC exchanges over HTTP/2, incrementally deframes the five-byte
gRPC message envelope, captures bounded request and response message payloads, and provides
descriptor-driven inspection, breakpoint, reflection, and API Studio execution adapters.

## Owns

- native `application/grpc`, `application/grpc+proto`, and `application/grpc+json` recognition;
- streaming gRPC envelope parsing without aggregating an HTTP body;
- gRPC method identity (`/package.Service/Method`), status, compression, and message metadata;
- protobuf descriptor registries, explicit bounded reflection, and bounded payload decoding;
- protobuf-JSON request document encoding and native execution for all four RPC cardinalities;
- direct and local-proxy grpc-java channel construction with strict TLS ownership;
- bounded interactive client-streaming and bidirectional sessions with explicit send, half-close,
  cancellation, inbound flow control, and terminal status/trailer events;
- gRPC message-breakpoint criteria, inspection, and envelope-preserving replacement.

## Does not own

- sockets, HTTP/2 negotiation, TLS, forwarding, or connection pooling (`:engine:proxy`);
- canonical HTTP exchange or framed-message models (`:core:traffic`);
- Room entities or body files (`:storage` and `:data:desktop`);
- Traffic, breakpoint, or API Studio presentation and persistence;
- generated service-specific client stubs.

## Dependency direction

`products/desktop -> engine/grpc -> application + engine/proxy + core/traffic`

The proxy exposes a protocol-neutral borrowed-payload hook. This module implements that hook and
never makes the proxy depend on protobuf or gRPC libraries.

API Studio and breakpoint presentation consume only application-owned protocol contracts. Descriptor and
grpc-java objects never cross into UI, storage, or the generic application layer.

## Extension rule

WebSocket, SSE, and future framed protocols implement the same proxy stream-inspection boundary in
their own modules. They must not add protocol parsing branches to `:engine:proxy`.

The evidence gate and remaining promotion blockers are recorded in
[`docs/grpc_qualification.md`](../../docs/grpc_qualification.md).
