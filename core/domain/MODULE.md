# `:core:domain`

## Responsibility

Holds non-traffic feature business models, repository contracts, and use cases used across existing features.

## Owns

- API Studio outbound request bodies/results/authentication, collections, authored breakpoint rules,
  workspace, and structured payload-policy contracts.
- Feature values that are independent from canonical captured traffic.
- Collection and outbound-execution contracts consume the canonical `:core:traffic` `HttpMethod`; they do not define another method enum.
- Breakpoint rules consume that same `HttpMethod` plus one `BreakpointPhase`; application,
  persistence adapters, interceptor, and UI do not define translated rule models.
- Breakpoint rules carry an open typed protocol ID plus an opaque extension-owned criteria payload.
  `BreakpointTransportMatcher` compiles only phase, method, and URL; no protocol-specific hierarchy
  or matcher branch belongs in this module.

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
The former closed GraphQL/gRPC/WebSocket breakpoint criteria and interception-metadata hierarchies
were removed. Adding a breakpoint protocol no longer changes `:core:domain`.
