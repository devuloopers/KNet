# `:engine:sse`

## Responsibilities

- Parse Server-Sent Events incrementally according to the WHATWG event-stream rules.
- Capture bounded SSE records as generic child protocol messages of the canonical HTTP exchange.
- Decode captured SSE records for Traffic without adding SSE fields to persistence or UI core.
- Contribute response-event breakpoint criteria and bounded wire transformation.
- Interpret live HTTP response chunks for API Studio while keeping SSE inside the HTTP workspace.
- Publish evidence-backed SSE runtime capabilities and reusable limits.

## Owns

- SSE media-type recognition, record parsing, event semantics, limits, capture adapter, breakpoint extension,
  Traffic decoder, and the HTTP response-stream interpreter under `integration/apistudio`.

## Does Not Own

- HTTP request/response models, proxy forwarding, connectivity, certificates, Room schemas, body storage,
  Compose presentation, or API Studio collection/session persistence.

## Dependency Direction

- Depends on protocol-neutral contracts in `:core:traffic`, `:core:domain`, `:application:desktop`, and `:engine:proxy`.
- The proxy, traffic core, persistence, and UI modules never depend on SSE implementation details.
- `:products:desktop` registers SSE contributions at the composition root.

## Extension Rule

Add future SSE content decoders or semantic views inside this module. Add a generic core contract only when the
same behavior is required by multiple protocols; never add an SSE branch to proxy forwarding or Room.

## Current State

The module is registered additively for bounded historical preview and identity-, gzip-, and deflate-encoded live
proxy capture, generic Traffic presentation, HTTP API Studio live interpretation, and response-record
breakpoints. Passive capture retains the encoded parent response as evidence and stores decoded SSE child records;
breakpoint transformation re-encodes altered records using the supported original representation.

The supported bounded post-capture preview remains independently `SUPPORTED`. Live capabilities remain
`EXPERIMENTAL` until the configured release soak, cross-platform CI execution, and physical Android/iOS Wi-Fi
proxy evidence are recorded in `docs/sse_qualification.md`. Brotli and Zstandard decoding, automatic reconnect,
browser `EventSource` policy emulation, and HTTP/3 remain additive future capabilities.
