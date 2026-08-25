# WebSocket Engine

`engine:websocket` owns RFC 6455 handshake recognition, incremental frame parsing, logical-message
capture, message breakpoint transformation, request naming, and Traffic payload presentation.

It depends on the proxy's protocol-neutral duplex inspection SPI. The proxy does not depend on this
module and contains no WebSocket opcode, masking, fragmentation, or breakpoint rules.

## Responsibilities

- Recognize HTTP/1.1 WebSocket Upgrade requests.
- Incrementally validate and parse masked client frames and unmasked server frames.
- Reassemble fragmented data messages while preserving control-frame ordering.
- Publish text, binary, ping, pong, and close messages through common traffic storage.
- Pause and optionally replace complete messages through the common breakpoint gate.
- Contribute WebSocket request labels and payload rendering.
- Validate protocol-neutral API Studio authoring input and create engine-owned execution documents.

## Boundaries

- It does not own sockets, TLS, proxy lifecycle, persistence, or Compose UI.
- Unsupported extensions remain transparent unless a message breakpoint explicitly claims the
  connection; inspection records their compression metadata without changing wire bytes.
- RFC 8441 WebSocket over HTTP/2 requires a future HTTP/2 duplex-stream adapter and does not change
  the APIs in this module.
