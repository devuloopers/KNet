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
  Traffic decoder, and HTTP response-stream interpreter.

## Does Not Own

- HTTP request/response models, proxy forwarding, connectivity, certificates, Room schemas, body storage,
  Compose presentation, or API Studio collection/session persistence.

## Dependency Direction

- Depends on protocol-neutral contracts in `:core:traffic`, `:core:domain`, `:application`, and `:engine:proxy`.
- The proxy, traffic core, persistence, and UI modules never depend on SSE implementation details.
- `:products:desktop` registers SSE contributions at the composition root.

## Extension Rule

Add future SSE content decoders or semantic views inside this module. Add a generic core contract only when the
same behavior is required by multiple protocols; never add an SSE branch to proxy forwarding or Room.

## Current State

The module is registered additively for bounded historical preview, identity-encoded live proxy capture, generic
Traffic presentation, HTTP API Studio live interpretation, and response-record breakpoints. All live capabilities
remain `EXPERIMENTAL` while the supported bounded post-capture preview remains independently `SUPPORTED`.

The current live path deliberately rejects encoded event streams at the semantic boundary. Forwarding and the
bounded terminal response remain available, but gzip/deflate incremental interpretation, cross-platform and
real-device evidence, complete HTTP/2 concurrent-stream isolation, and multi-hour resource soak are qualification
work recorded in `docs/sse_qualification.md`.
