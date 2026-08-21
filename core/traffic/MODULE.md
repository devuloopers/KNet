# `:core:traffic`

## Responsibility

Defines KNet's canonical, platform-neutral traffic language. The same HTTP request, response, exchange, body-reference, identity, and ingress models are shared by Traffic UI, API Studio, breakpoints, persistence, export, scripting, protocol inspectors, and future companions.

## Owns

- `HttpRequestSnapshot`, `HttpResponseSnapshot`, and `HttpExchangeSnapshot`.
- The extension-safe `HttpMethod` used by capture, API Studio, collections, breakpoints, and outbound execution.
- Typed HTTP heads, repeated ordered headers, targets, status, `ExchangeTimings`, protocol, and identifiers.
- Optional native `StreamId` plus independently ordered request and response trailers for multiplexed transports.
- Canonical HTTP content-encoding tokens used by capture, storage, and body decoding.
- Body metadata and `BodyRef`; large body bytes are owned by a body store, not these snapshots.
- Immutable ordered capture events, capture directions/endpoints, ingress, and the extension-safe
  `TrafficOrigin` attribution shared by capture, persistence, and presentation.
- One-shot, bounded ingress-attribution contracts so paired devices can be recorded without connectivity-specific traffic models.
- Versioned protocol-neutral inspection documents and annotations.

## Does not own

- Mutable editor drafts, breakpoint patches, UI state, database entities, Netty objects, or body-store implementations.
- Protocol-specific decoded views such as GraphQL operations or WebSocket frames.

## Dependency rule

This is a leaf contract module and has no production project dependencies.

## Current state

Traffic UI, API Studio recording/replay preparation, breakpoints, storage adapters, semantic inspectors, export/replay boundaries, and paired ingress share these models. Request and response heads independently retain their observed application-protocol versions, while `TrafficOrigin` distinguishes an API Studio submission from an ordinary proxy client without conflating it with transport ingress. Response panels and timelines consume `ResponseHead`/`ExchangeTimings` directly. Feature-local mutable editor/UI state remains separate by design; no feature may fork canonical request/response, timing, origin, or content-encoding models.
HTTP/2 aliases normalize to the canonical `HTTP/2` token. Stream identity and request/response trailers remain
transport-neutral values, so future gRPC and HTTP/3 adapters can reuse them without importing Netty frames.
