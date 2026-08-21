# `:core:domain`

## Responsibility

Holds non-traffic feature business models, repository contracts, and use cases used across existing features.

## Owns

- API Studio outbound request bodies/results/authentication, collections, authored breakpoint rules,
  workspace, validated process-level application settings, and structured payload-policy contracts.
- Compose-independent persisted Traffic column-width values, including a nullable Path width that represents
  automatic viewport fill without leaking desktop layout calculations into the domain model.
- `ApplicationSettings`, validated `ProxyPort` and Kotlin `Duration` values, plus the atomic
  `ApplicationSettingsRepository` update contract. Workspace layout remains a separate snapshot and cannot
  overwrite process-level preferences.
- The complete authored API Studio request document, including ordered query/header/cookie rows with enabled
  flags, typed body and raw-format values, structured form fields, authentication, scripts, and generated-versus-
  user-defined request-name ownership.
- The per-request `HttpVersionPreference` (`AUTO`, exact HTTP/1.0, exact HTTP/1.1, or exact HTTP/2), kept separate from the
  actual `ApplicationProtocol` observed on a response or captured exchange.
- The ordered cross-feature request-descriptor contribution contract, HTTP path/host fallback, semantic
  kind/badge metadata, bounded immutable descriptor body, and stable resolver use case under `domain.request`.
- Feature values that are independent from canonical captured traffic.
- Collection and outbound-execution contracts consume the canonical `:core:traffic` `HttpMethod`; they do not define another method enum.
- Breakpoint rules consume that same `HttpMethod` plus one `BreakpointPhase`; application,
  persistence adapters, interceptor, and UI do not define translated rule models.
- Breakpoint rules carry an open typed protocol ID plus an opaque extension-owned criteria payload.
  `BreakpointTransportMatcher` compiles only phase, method, and URL; no protocol-specific hierarchy
  or matcher branch belongs in this module.
- Authored breakpoint rules carry a persisted non-negative priority; lower values are evaluated first
  and rule identity is the deterministic tie breaker.

## Does not own

- New canonical traffic contracts, which belong in `:core:traffic`.
- Platform implementations, Compose screens, or engine internals.
- Scripting vocabulary, which belongs to the leaf `:core:scripting` module.

## Dependency rule

Keep dependencies limited to `:core:traffic`, `:core:scripting`, and low-level core utilities. New code should prefer narrower contract modules and `:application` use cases.

## Current state

Duplicated `HttpRequest`, `HttpResponse`, `HttpTransaction`, `HttpTimings`, proxy-runtime, live-traffic, and interception-session contracts have been removed. Captured traffic uses `:core:traffic`; cross-feature runtime orchestration uses `:application`.
Platform detection is not a domain concern; desktop-only host-platform behavior lives with its consuming certificate UI. Domain-generated identities use Kotlin `Uuid`.
Outbound execution uses `OutboundRequestBody` and `ExecutionResult`; transport timing is the canonical
`:core:traffic` `ExchangeTimings`, and GraphQL UI state composes `StructuredPayloadState.GraphQL`
instead of copying its fields.
`SavedApiRequest` is the persistence and execution source of truth. Storage strings and presentation body
selectors are translated only at their respective module boundaries; the domain body kind is `RequestBodyType`.
API Studio session/request titles, Traffic method labels, and live-interception queue labels resolve through the
same request-descriptor pipeline. Authored requests adapt their canonical document; captured and pending requests
provide canonical HTTP metadata, an optional bounded body, and an optional semantic kind hint. Protocol modules
contribute priority-ordered strategies; collection names remain user-controlled and outside this metadata
pipeline. The request kind identifier is open so future protocols do not modify the stable core contract, and
the actual HTTP method remains independently available as transport metadata.
Shared body decoding enforces an explicit decoded-output ceiling for identity, gzip, deflate, Brotli, and
Zstandard content. Callers receive a typed output-limit result rather than allowing compressed payloads to
expand without a memory bound.
The former closed GraphQL/gRPC/WebSocket breakpoint criteria and interception-metadata hierarchies
were removed. Adding a breakpoint protocol no longer changes `:core:domain`.
Application settings use Kotlin time and a validated proxy-port value at the domain boundary. Consumers observe
or atomically transform the latest application settings through focused use cases; workspace presentation uses a
separate atomic update use case. Neither contract contains platform runtime collaborators.
