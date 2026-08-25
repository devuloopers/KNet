# API Studio WebSocket

## Responsibilities

- Own the WebSocket authoring state and its versioned workspace payload.
- Render the WebSocket URL, handshake options, outbound composer, and live event timeline.
- Use the common resizable authoring/result geometry so connection controls remain with authoring and the session
  timeline occupies the complete result-pane height.
- Materialize an unsaved workspace document only after the first meaningful edit.
- Open, send through, close, and cancel a WebSocket session through application use cases.

## Boundaries

- Depends on the common API Studio contribution contract; the shell contains no WebSocket-specific branch.
- Does not depend on `engine:websocket`; execution documents are created through the common
  protocol-authoring port and validated by the engine-owned adapter.
- Does not own sockets, proxy routing, traffic persistence, breakpoint matching, or certificates.
- ViewModels depend on application use cases and receive their dependencies from product DI.
- New editors keep the optional timeout visually blank; the 30-second operational default is applied only when a
  connection starts.

## Extension notes

Additional WebSocket authoring options belong in this module and its versioned codec. New API Studio protocols should add sibling contribution modules instead of modifying this editor.
