# `:testingServer`

## Responsibility

`:testingServer` is KNet's local, deterministic protocol lab. It produces real network traffic that can be sent
through KNet to exercise capture, inspection, formatting, streaming, error handling, and future protocol support.
It is a standalone test application and is never a dependency of a production KNet module.

The stable test contract is versioned under `/lab/v1`. Compatibility with the deleted development-only `/api`
routes is intentionally not retained.

## Runtime ownership

- Spring WebFlux owns HTTP/1.1, clear-text HTTP/2, ordinary payloads, chunked responses, SSE, and raw WebSocket.
- Spring GraphQL owns GraphQL queries, mutations, and WebSocket subscriptions.
- grpc-java owns native gRPC on a separate listener because gRPC is not an ordinary annotated WebFlux route.
- `static/index.html` owns the browser test dashboard; HTML is not embedded in Kotlin source.
- `catalog/LabCatalogController` publishes supported and planned capabilities without claiming fake endpoints.

Default listeners:

- Web and GraphQL: `0.0.0.0:9090`
- Native gRPC: `0.0.0.0:9091`

Automated tests replace both ports with operating-system-selected ephemeral ports.

## Package and resource structure

```text
testingServer/
├── src/main/kotlin/com/devuloopers/knet/testingserver/
│   ├── catalog/       # Versioned machine-readable capability manifest
│   ├── http/          # Methods, metadata, status, delay, redirect, cookies, and auth
│   ├── payload/       # JSON, NDJSON, XML, SOAP, forms, and binary encodings
│   ├── stream/        # SSE and ordinary chunked response fixtures
│   ├── websocket/     # Raw text/binary WebSocket echo
│   ├── graphql/       # Query, mutation, and subscription resolvers
│   └── grpc/          # Native gRPC service and independent server lifecycle
├── src/main/proto/    # Native gRPC service contract
├── src/main/resources/
│   ├── fixtures/      # Resource-backed wire payloads
│   ├── graphql/       # GraphQL schema
│   └── static/        # Browser test dashboard
└── src/test/          # Real-listener integration contract
```

## Dependency direction

Scenario packages may depend on framework/network codecs and generated fixture models. They must not depend on
KNet production modules, storage, proxy internals, UI, or application use cases. KNet consumes this module only
over the network, exactly like an external server.

The gRPC lifecycle depends on the gRPC service implementation. The catalog may read the lifecycle's bound port
for discovery, but HTTP scenario controllers do not depend on gRPC. Protocol packages do not call each other.

## Extension rule

Add a new protocol or wire behavior by adding one cohesive protocol package, its resource/schema when needed,
one catalog capability, and a real integration test. Do not add one handler class per verb, duplicate auth
implementations, or label a simulated HTTP response as a transport the server does not actually speak.

## Supported and deferred transports

Currently executable: HTTP/1.1, H2C, JSON, NDJSON, XML, SOAP, form data, multipart, CBOR, MessagePack, Protobuf
payloads, SSE, chunked text, raw WebSocket, GraphQL HTTP, GraphQL subscriptions, and all four native gRPC RPC
cardinalities.

Explicitly deferred: gRPC-Web, HTTP/3, and WebTransport. These remain `PLANNED` in the manifest until a real
standards-compatible listener and wire-level integration test exist.

The complete endpoint contract and manual testing instructions live in
[`docs/testing_server_protocol_lab.md`](../docs/testing_server_protocol_lab.md).
