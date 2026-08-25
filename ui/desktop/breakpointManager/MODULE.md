# `:ui:desktop:breakpointManager`

## Responsibility

Owns breakpoint rule management and live intercepted-request/response presentation on desktop.

## Owns

- Breakpoint Manager ViewModel, rules table, intercept queue, editors, and edit-only presentation state.
- Live Intercept drawer content and exit-state retention, hosted in the shared `:ui:core` drawer shell.
- Canonical `HttpMethod` selection and token mapping for method-specific rules.
- Add/Edit rule drawer content hosted in the shared `:ui:core` drawer shell, with a pinned action area and a
  vertically scrollable, responsive form and theme-aware scrollbars that appear only when content overflows.
- Schema-driven protocol selection and standard text/choice criteria fields supplied through the
  application protocol-rule use case; the drawer contains no GraphQL, gRPC, or other engine-specific branch.
- Content-driven protocol choice widths and wrapped supporting descriptions, so contributed labels remain
  readable without protocol-specific presentation fixes.
- UI edit state for proposed request/response patches.
- Immediate pending-event presentation followed by cancellable, off-main preparation of only the selected
  body; resolved-payload memory is bounded to one active transaction.
- Off-main protocol-aware queue descriptors resolved from the same shared request pipeline as API Studio and
  Traffic; the queue retains the HTTP method only as transport truth and renders the semantic badge separately.
- Explicit interception metadata for semantic type, client protocol, observed upstream response protocol,
  payload format, and canonical source attribution; queue rows retain compact protocol/source context.
- Explicit unchanged forwarding that avoids rebuilding wire bodies when no metadata/body edit occurred.

## Does not own

- Canonical exchange models, matching/rebuild engine behavior, storage implementation, proxy lifecycle, or product DI bindings.
- Generic drawer animation, responsive sizing, surface, or edge-placement behavior.

## Dependency rule

Consume shared `HttpExchangeSnapshot` and submit typed patches through use cases; never mutate shared snapshots in place.

## Current state

Breakpoint access is behind application/domain contracts. The feature renders and edits the canonical
`BreakpointRule`/`BreakpointPhase` directly; the application coordinator, storage adapter, and
interceptor consume the same values. Complete feature assembly lives in `:products:desktop` under
`di/breakpoint`.
Protocol modules remain outside the UI dependency graph. Adding a registered extension automatically
adds its protocol option and standard criteria fields to the rule editor.
The live drawer exposes cohesive immutable state and action parameter objects. It can open as soon as a
candidate is published, shows preparation state without blocking metadata actions, and never decodes or
formats payload bytes during Compose rendering. Queue descriptor preparation owns at most a one-mebibyte
defensive body prefix per active request during resolution and retains only descriptor metadata afterward.
The drawer never infers wire protocol from GraphQL, JSON, or another semantic format. It reads client and
upstream protocol directly from canonical heads and source directly from `TrafficOrigin`, keeping future
request kinds additive.
