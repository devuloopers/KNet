# `:core:http`

## Responsibility

Provides reusable outbound HTTP-client capabilities used by API Studio and related client-side features.

## Owns

- Request execution, authentication, TLS client configuration, multipart, SSE, and WebSocket client helpers.
- Per-request HTTP wire-version dispatch: Ktor remains the exact HTTP/1.1 transport, while narrow JVM adapters
  own exact HTTP/1.0 wire semantics and HTTP/2-preferred ALPN execution.
- Ktor encoding of the canonical `OutboundRequestBody`, `ApiRequestAuth`, and `ExecutionResult`
  contracts owned by `:core:domain`.
- Ktor adaptation of the canonical, extension-safe `:core:traffic` `HttpMethod`; raw strings are accepted only before this module boundary.
- Client-side network error classification.
- Common HTTP behavior plus narrow JVM adapters for concurrent client caching and platform exception mapping.
- Immutable DER trust material for a local inspecting proxy without filesystem or certificate-engine coupling.
- Optional capture-origin attribution added only on a local-proxy dispatch and removed from direct/fallback
  requests, so API Studio can be identified without leaking internal metadata to origin servers.
- A JVM TLS policy adapter that uses platform trust roots for direct clients, composes those roots with
  one explicitly supplied local-proxy CA only for proxy-configured clients, and uses an explicit
  trust-all manager only when SSL verification is disabled.

## Does not own

- Proxy interception, captured-traffic storage, canonical exchange identity, or UI state.

## Dependency rule

May consume stable domain/core contracts but must remain independent of desktop UI and proxy runtime implementations.
`commonMain` must not import JVM exception or concurrency types.

## Current state

Outbound execution accepts one strongly typed body and authentication value plus the shared
`:core:traffic` method type and the domain-owned `HttpVersionPreference`. Transport request/body/auth values are
created only at the final boundary; the module no longer owns parallel request-kind, authentication, metrics, or
execution-result models. Exact HTTP/1.1 uses Ktor. Exact HTTP/1.0 uses a bounded,
cancellation-aware JVM socket adapter because CIO serializes HTTP/1.1 request lines; Java sockets and TLS are kept
inside that required platform boundary and do not leak into common code.
`AUTO` uses the cancellation-aware JVM ALPN client, prefers HTTP/2, accepts HTTP/1.1 fallback, and reports the
observed response protocol. Exact HTTP/2 uses the same adapter but fails closed if the peer or proxy path does not
negotiate HTTP/2. A bounded configuration/trust-keyed client cache preserves HTTP/2 connection reuse without
unbounded variants. Both modes share authentication, cookie, response decoding, redirect, timeout, proxy routing,
and scoped local-proxy trust preparation. JDK HTTP/TLS types remain private implementation details of `jvmMain`.
It does not generate, locate, persist, or rotate KNet's interception CA and does not depend on
`:engine:certificate`. The product may supply the active CA as typed DER trust material. API Studio supplies
`TrafficOrigin.ApiStudio` at product composition; user-authored and interceptor-authored reserved attribution
headers are discarded, and attribution is added only when an active proxy port is selected. Direct clients
continue to use platform roots only; proxy-configured clients additionally trust that local CA so they can
validate KNet-generated downstream certificates. Origin-server validation remains independently owned by
the proxy engine's upstream TLS policy.
