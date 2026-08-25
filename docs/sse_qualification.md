# Server-Sent Events Qualification

- Capability maturity: **EXPERIMENTAL**
- Implemented increment: identity-encoded live SSE over HTTP/1.1 and HTTP/2
- Supported existing capability: bounded post-capture SSE semantic preview

## Proven architecture

- One canonical `HttpExchangeSnapshot` remains the request/response source of truth.
- Live records are bounded generic `ProtocolMessageSnapshot` children stored by the existing Room/body-store path.
- Proxy forwarding owns network backpressure; passive inspection cannot wait on UI or persistence.
- API Studio receives immutable response metadata and defensively owned body chunks through a protocol-neutral
  application contract. SSE semantics remain in `:engine:sse`.
- Traffic, persistence, the proxy, and the shared HTTP editor contain no SSE-specific branch.
- One product-provided `SseLimits` instance configures parsing, capture, API Studio retention, and breakpoint edits.

## Default limits

| Limit | Value |
|---|---:|
| Line bytes | 65,536 |
| Record bytes | 1,048,576 |
| Data characters | 1,048,576 |
| Event-type characters | 1,024 |
| Event-ID characters | 8,192 |
| Captured records per exchange | 10,000 |
| Captured bytes per exchange | 67,108,864 |
| Retained API Studio records | 1,000 |
| Editable breakpoint-record bytes | 1,048,576 |

Limit overflow produces an explicit bounded gap, truncation, bypass, or terminal classification according to the
owning path. It must never delay passive forwarding or allocate in proportion to an untrusted endless stream.

## Local evidence

The 2026-08-25 local gate passed 189 Gradle tasks covering all modules listed below plus desktop composition,
`verifyKotlinFirstSources`, and `verifyArchitectureFoundation`. KNet was not launched by the qualification run.

| Concern | Evidence |
|---|---|
| Incremental parsing, chunk boundaries, BOM, CR/LF variants, malformed input, and limits | `SseIncrementalParserTest` |
| Live passive record capture and bounded ownership | `SseStreamInspectorTest` |
| Historical semantic inspection and Room/body-store path | `SseSemanticInspectorTest`, `SseSemanticInspectionEndToEndTest` |
| Traffic payload decoding and shared formatting | `SseSemanticInspectorTest`, `SseStreamFormatterTest` |
| Smart criteria, match, continue, replace, validation, and terminate | `SseBreakpointRuntimeTest` |
| API Studio semantic record interpretation | `SseHttpResponseStreamInterpreterTest` |
| HTTP application pipeline event ordering | `ExecuteClientApiRequestUseCaseTest`, `ExecuteApiStudioRequestUseCaseTest` |
| Real HTTP/1.1 first-event delivery and cancellation | `ServerSentEventsStreamingTest` |
| Real TLS/ALPN HTTP/2 delayed DATA-frame delivery | `ServerSentEventsStreamingTest` |
| Finite/live/edge fixtures, raw gzip wire, and resume behavior | `ProtocolLabIntegrationTest` |

## Explicit exclusions before Supported

- Incremental gzip and deflate semantic decoding. Encoded streams are forwarded and retain bounded terminal
  handling, but are not advertised as live interpreted SSE.
- Brotli and Zstandard live decoding.
- Automatic `EventSource`-style reconnect policy.
- Browser CORS and browser credential-policy emulation.
- Cross-platform CI evidence for every transport path.
- Android/iOS Wi-Fi proxy evidence, multi-hour stream soak, reconnect storms, and release memory/descriptor gates.
- The complete concurrent HTTP/2 matrix proving one paused or cancelled SSE stream never affects sibling streams.

The capability catalog must remain `EXPERIMENTAL` until these applicable SSE-5 gates pass. None of the exclusions
requires changing the proxy engine, common HTTP model, persistence schema, Traffic architecture, API Studio
collections, PAC/manual Wi-Fi setup, or future companion boundaries.
