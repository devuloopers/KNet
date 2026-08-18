# `:ui:desktop:breakpointManager`

## Responsibility

Owns breakpoint rule management and live intercepted-request/response presentation on desktop.

## Owns

- Breakpoint Manager ViewModel, rules table, intercept queue, editors, and edit-only presentation state.
- Canonical `HttpMethod` selection and token mapping for method-specific rules.
- UI edit state for proposed request/response patches.

## Does not own

- Canonical exchange models, matching/rebuild engine behavior, storage implementation, proxy lifecycle, or product DI bindings.

## Dependency rule

Consume shared `HttpExchangeSnapshot` and submit typed patches through use cases; never mutate shared snapshots in place.

## Current state

Breakpoint access is behind application/domain contracts. The feature renders and edits the canonical
`BreakpointRule`/`BreakpointPhase` directly; the application coordinator, storage adapter, and
interceptor consume the same values. Complete feature assembly lives in `:products:desktop` under
`di/breakpoint`.
