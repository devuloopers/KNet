# `:core:http`

## Responsibility

Provides reusable outbound HTTP-client capabilities used by API Studio and related client-side features.

## Owns

- Request execution, authentication, TLS client configuration, multipart, SSE, and WebSocket client helpers.
- Ktor encoding of the canonical `OutboundRequestBody`, `ApiRequestAuth`, and `ExecutionResult`
  contracts owned by `:core:domain`.
- Ktor adaptation of the canonical, extension-safe `:core:traffic` `HttpMethod`; raw strings are accepted only before this module boundary.
- Client-side network error classification.
- Common HTTP behavior plus narrow JVM adapters for concurrent client caching and platform exception mapping.

## Does not own

- Proxy interception, captured-traffic storage, canonical exchange identity, or UI state.

## Dependency rule

May consume stable domain/core contracts but must remain independent of desktop UI and proxy runtime implementations.
`commonMain` must not import JVM exception or concurrency types.

## Current state

Outbound execution accepts one strongly typed body and authentication value plus the shared
`:core:traffic` method type. Ktor request/body/auth values are created only at the final transport
boundary; the module no longer owns parallel request-kind, authentication, metrics, or execution-result models.
