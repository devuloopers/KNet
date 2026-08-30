# Server-Sent Events Qualification

- Capability maturity: **EXPERIMENTAL**
- Locally implemented profile: identity, gzip, and deflate live SSE over HTTP/1.1 and HTTP/2
- Supported independent capability: bounded post-capture SSE semantic preview
- Local qualification date: 2026-08-26

## Proven architecture

- One canonical `HttpExchangeSnapshot` remains the request/response source of truth.
- Live records are bounded generic `ProtocolMessageSnapshot` children stored by the existing Room/body-store path.
- Proxy forwarding owns network backpressure; passive inspection cannot wait on UI or persistence.
- The encoded parent HTTP body remains raw evidence. Captured child records are decoded, owned, bounded bytes.
- API Studio receives immutable response metadata and defensively owned body chunks through a protocol-neutral
  application contract. SSE parsing and content codecs remain in `:engine:sse`.
- Breakpoints decode, decide, and re-encode through the same codec registry; protocol-neutral proxy code only
  sanitizes invalid representation headers after any payload transformation.
- Traffic, persistence, the proxy, and the shared HTTP editor contain no SSE-specific branch.
- One product-provided `SseLimits` instance configures parsing, capture, API Studio, breakpoints, and codecs.

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
| Content-encoding layers | 2 |
| Decoder input bytes per call | 4,194,304 |
| Decoder retained bytes | 65,536 |
| Decoded bytes per call | 4,194,304 |
| Decoder expansion ratio | 256:1 |
| Decoder expansion grace bytes | 1,048,576 |

Limit overflow produces an explicit bounded gap, truncation, bypass, or terminal classification according to the
owning path. It must never delay passive forwarding or allocate in proportion to an untrusted endless stream.

## Local evidence

The root `sseQualification` gate passed 207 Gradle tasks on macOS. It covers the affected engines, proxy, common
contracts, application orchestration, persistence, protocol lab, API Studio, Traffic, breakpoint UI, desktop
composition, Kotlin-first source checks, module boundaries, and architecture verification. KNet was not launched.

| Concern | Evidence |
|---|---|
| Incremental parsing, chunk boundaries, BOM, CR/LF variants, malformed input, and limits | `SseIncrementalParserTest` |
| Incremental identity/gzip/zlib-deflate/raw-deflate, stacked encodings, CRC/trailer validation, and abuse limits | `SseContentCodecsTest` |
| Encoded-stream churn and deterministic cleanup | `SseCodecStressTest` |
| Live passive capture, decoded child ownership, and failure detachment | `SseStreamInspectorTest` |
| Historical semantics and canonical Room/body-store path | `SseSemanticInspectorTest`, `SseSemanticInspectionEndToEndTest` |
| Traffic payload decoding and shared formatting | `SseSemanticInspectorTest`, `SseStreamFormatterTest` |
| Identity/gzip decisions, replacement, re-encoding, validation, and termination | `SseBreakpointRuntimeTest` |
| Protocol-neutral transformed-response header sanitation | `PayloadTransformationHeadersTest` |
| API Studio encoded record interpretation and typed gaps | `SseHttpResponseStreamInterpreterTest`, `LiveHttpResponseViewTest` |
| HTTP application pipeline event ordering | `ExecuteClientApiRequestUseCaseTest`, `ExecuteApiStudioRequestUseCaseTest` |
| Real HTTP/1.1 first-event delivery and cancellation | `ServerSentEventsStreamingTest` |
| Real TLS/ALPN HTTP/2 encoded DATA-frame delivery | `Http2TlsLabIntegrationTest`, `ServerSentEventsStreamingTest` |
| Paused SSE breakpoint isolation from an HTTP/2 sibling stream | `HttpTwoBreakpointIsolationTest` |
| Finite/live/gzip/deflate/corrupt/expansion fixtures and resume behavior | `ProtocolLabIntegrationTest` |
| Product registration and capability-catalog composition | `DesktopModulesTest` |

The module also exposes `sseReleaseSoak`, defaulting to 10,800 seconds and configurable with
`-Pknet.sse.soak.seconds=<seconds>`. A one-second task smoke run passed locally; this does not count as the required
three-hour release-soak evidence.

## Evidence still required before Supported

- Execute the checked-in `sseQualification` CI matrix successfully on macOS, Windows, and Linux. Adding the
  workflow is not evidence that every operating-system run passed.
- Run the default three-hour release soak and record heap, direct-memory, thread, socket, file-descriptor, and
  coroutine-job recovery measurements.
- Record physical Android and iOS Wi-Fi proxy evidence for certificate trust, identity and compressed streams,
  cancellation, Traffic persistence, and HTTP/2 behavior.
- Complete any remaining real-socket lifecycle rows not already covered, including reset/goaway, network-change,
  application-shutdown, slow persistence/UI, and reconnect-storm recovery evidence.

## Explicit additive exclusions

- Brotli and Zstandard live decoding.
- Automatic `EventSource`-style reconnect policy.
- Browser CORS and browser credential-policy emulation.
- HTTP/3 transport support.

The live capability catalog remains `EXPERIMENTAL` until the applicable evidence rows pass. None of the pending
evidence or exclusions requires changing the proxy engine, common HTTP model, persistence schema, Traffic
architecture, API Studio collections, PAC/manual Wi-Fi setup, or companion boundaries.
