# WebSocket Qualification

## Status

KNet's HTTP/1.1 WebSocket increment is locally qualified as `EXPERIMENTAL`. It is not promoted to `SUPPORTED`
until the desktop operating-system/device matrix and release-soak gates pass.

## Delivered boundaries

- `:engine:proxy` validates a matching HTTP/1.1 `101 Switching Protocols` response and transfers the two live
  channels to a protocol-neutral, backpressured duplex relay. It contains no WebSocket frame logic.
- `:engine:websocket` recognizes RFC 6455 handshakes, incrementally parses direction-correct frames, reconstructs
  logical messages, captures child-message records, renders payloads, contributes request descriptors, and owns
  message-level breakpoint matching/transformation.
- `:ui:desktop:apiStudio:websocket` contributes a transient blank editor, versioned saved/unsaved workspace
  documents, handshake authoring, an interactive text/binary composer, explicit connect/close/cancel controls,
  and a bounded event timeline. It depends only on common application authoring/session ports; the engine adapter
  owns conversion into strict WebSocket execution documents.
- `:testingServer` exposes `/lab/v1/websocket/echo` as the deterministic local raw WebSocket fixture.

## Local evidence

| Area | Evidence |
|---|---|
| Upgrade ownership and raw duplex delivery | `DuplexUpgradeIntegrationTest` |
| Invalid/mismatched switching response | `DuplexUpgradeIntegrationTest` |
| Incremental framing, extended lengths, masking, limits | `WebSocketFrameCodecTest` |
| Fragmentation, both directions, control messages, canonical message capture | `WebSocketDuplexInspectorTest` |
| Message pause/replacement and wire-order preservation | `WebSocketBreakpointRuntimeTest` |
| Direct interactive and one-shot client lifecycle | `WebSocketApiStudioExecutorTest` |
| Protocol-neutral UI authoring to strict engine draft | `WebSocketApiStudioAuthoringAdapterTest` |
| Versioned transient/saved workspace state | `WebSocketWorkspaceDraftCodecTest` |
| Local protocol fixture | `ProtocolLabIntegrationTest` |
| Product composition and registry uniqueness | `DesktopModulesTest` |

Run the complete non-UI-launching local gate with:

```bash
./gradlew webSocketQualification
```

## Supported experimental envelope

- HTTP/1.1 Upgrade over locally real-socket-qualified `ws://`; `wss://` is implemented over the existing
  CONNECT/TLS interception path and remains part of the external qualification matrix.
- Text and binary messages, fragmented data messages, interleaved ping/pong/close control frames, and payloads
  using 7-bit, 16-bit, and 64-bit WebSocket length encodings within configured bounds.
- Directional masking validation: client frames are masked and server frames are not.
- Transparent forwarding under downstream/upstream backpressure.
- Canonical message rows associated with the parent HTTP Upgrade exchange.
- Message breakpoints by direction, semantic kind, requested subprotocol, and one-based direction sequence.
- Editing of complete uncompressed messages while preserving fragmentation and control-frame placement.
- API Studio direct execution and capture-aware routing through the running local proxy.

## Explicitly not claimed

- RFC 8441 WebSocket over HTTP/2 and WebSocket over HTTP/3.
- Semantic decoding or mutation of negotiated `permessage-deflate` payloads. Compressed frames remain transparent,
  capture records compression metadata, and breakpoint mutation fails closed instead of corrupting the wire.
- Browser/mobile/device-matrix qualification, large concurrency soak, or production maturity.
- Application-specific subprotocol semantics such as GraphQL subscription messages; those remain additive semantic
  inspectors above the WebSocket message model.

These exclusions do not require changes to the stable proxy relay, canonical traffic storage, shared API Studio
workspace contracts, or breakpoint registry. They require additive transport or semantic contributions.
