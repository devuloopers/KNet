# `:engine:interceptor`

## Responsibility

Adapts application-owned breakpoint decisions to Netty and safely rebuilds edited messages.

## Owns

- Netty integration with the application-owned bounded `BreakpointGate`.
- Exact Netty message/promise ownership for pause, forward, drop, timeout, disconnect, and handler-removal paths.
- Reconstruction from canonical typed breakpoint edits at the proxy boundary.
- Admission of protocol-neutral capture metadata before suspension and one-shot handoff to the forwarding handler.

## Does not own

- Canonical immutable traffic snapshots, UI dialogs, persistence, or proxy server lifecycle.

## Dependency rule

May depend on the proxy transport and application breakpoint contracts. It must not depend on UI, persistence, or product composition.

## Current state

Coordination, matching, budgets, deadlines, and pending state live in `:application`. Before opening the gate, the handler uses the proxy-owned connection capture side output to admit the canonical exchange and stores a one-shot handle for forwarding. It terminates that handle if a drop or disconnect prevents ownership transfer. It does not know Room, Traffic UI, or protocol-specific matching. The module otherwise owns only exact Netty reference/promise handling and conversion between full breakpoint messages and canonical typed edits.
