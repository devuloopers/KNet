# `:engine:interceptor`

## Responsibility

Adapts application-owned breakpoint decisions to Netty and safely rebuilds edited messages.

## Owns

- Netty integration with the application-owned bounded `BreakpointGate`.
- Exact Netty message/promise ownership for pause, forward, drop, timeout, disconnect, and handler-removal paths.
- Reconstruction from canonical typed breakpoint edits at the proxy boundary.
- Admission of protocol-neutral capture metadata before suspension and one-shot handoff to the forwarding handler.
- A breakpoint-specific request-selection adapter over the proxy's protocol-neutral selective aggregator.
- Deterministic full-message framing for edited requests/responses, including body-forbidden response
  semantics, custom reason phrases, retained trailers, and mutually exclusive length/chunked framing.

## Does not own

- Canonical immutable traffic snapshots, UI dialogs, persistence, or proxy server lifecycle.

## Dependency rule

May depend on the proxy transport and application breakpoint contracts. It must not depend on UI, persistence, or product composition.

## Current state

Coordination, matching, budgets, deadlines, and pending state live in `:application:desktop`. Before opening the gate,
the handler uses the proxy-owned connection capture side output to admit the canonical exchange and stores a
one-shot handle for forwarding. It terminates that handle if a drop or disconnect prevents ownership transfer.
Every forwarded request head participates in the bounded response-order queue, including requests that do not
match a breakpoint, so mixed pipelining cannot consume a later request's correlation. Informational responses do
not consume request correlation. Unchanged forwarding retains original body bytes;
only explicit replacements rebuild body content. Bounded body copies and application protocol inspection run on
Kotlin's shared bounded worker dispatcher while the Netty event loop retains exclusive transport ownership and
applies the eventual decision. The module does not know Room, Traffic UI, or any protocol-specific matcher.
