# gRPC Qualification

## Capability state

KNet exposes three independent experimental capabilities:

- `grpc.capture` — native gRPC classification, incremental framing, canonical message persistence, decoding, and
  Traffic presentation.
- `grpc.breakpoints` — service/method/direction/sequence matching plus unchanged, replace, and drop-stream message
  decisions.
- `grpc.apistudio` — descriptor import, explicit server reflection, persisted drafts/schemas, all four RPC
  cardinalities, deadlines, cancellation, metadata, status, and trailers.

They remain `EXPERIMENTAL`. Local automated evidence does not replace real Android/iOS Wi-Fi, native gRPC TLS/ALPN,
long-running soak, or leak qualification.

## Automated gate

Run from the repository root:

```text
./gradlew grpcQualification --no-daemon
```

The gate never launches the desktop application. It includes architecture/Kotlin-first checks and the application,
canonical traffic, storage/data, proxy, gRPC engine, API Studio, breakpoint, Traffic, protocol-lab, and product
composition test suites. CI runs the same task on macOS, Windows, and Linux.

## Current evidence

| Area | Evidence |
|---|---|
| Framing and classification | split/coalesced envelopes, gzip, size bounds, truncation, terminal state, and unrelated-traffic rejection in `GrpcStreamInspectorTest` |
| Durable messages | schema-v23 write/query/recovery/retention/clear coverage in data and storage tests |
| Descriptors | bounded import, dependency resolution, decode, and malformed input in `GrpcDescriptorRegistryTest` |
| Reflection | a real grpc-java v1 reflection service in `GrpcApiStudioReflectionAdapterTest` |
| Breakpoints | same-endpoint method isolation, direction/sequence criteria, split-frame pause, replacement, and stream-local drop in `GrpcBreakpointRuntimeTest` |
| API Studio | every cardinality, bounded events, interactive send/half-close/cancel, deadline expiry, and non-OK status/trailer preservation in `GrpcApiStudioExecutorTest` |
| Restart persistence | incomplete workspace document and descriptor bytes reopen from Room in `RoomApiStudioWorkspaceDocumentStoreTest` |
| Local protocol lab | health/reflection, every cardinality, typed status/trailers, gzip, 512 KiB payload, deadline, cancellation, and 20 concurrent unary calls in `ProtocolLabIntegrationTest` |

## Promotion blockers

Before any capability becomes `SUPPORTED`, record passing evidence for:

1. Native gRPC through KNet over both H2C and TLS/ALPN using generated clients.
2. Android and iOS Wi-Fi proxy capture with trusted KNet CA where the client permits interception.
3. Twenty or more concurrent long-lived HTTP/2 streams while one message is paused, edited, timed out, or dropped.
4. Long-stream memory, file-descriptor, cancellation, and restart soak tests on all desktop platforms.
5. Explicit behavior for certificate pinning and clients that do not honor an HTTP proxy.

Promotion is independent: API Studio success cannot promote capture or breakpoints, and capture success cannot
promote editing.
