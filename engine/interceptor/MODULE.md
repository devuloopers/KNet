# `:engine:interceptor`

## Responsibility

Adapts application-owned breakpoint decisions to Netty and safely rebuilds edited messages.

## Owns

- Netty integration with the application-owned bounded `BreakpointGate`.
- Exact Netty message/promise ownership for pause, forward, drop, timeout, disconnect, and handler-removal paths.
- Reconstruction from canonical typed breakpoint edits at the proxy boundary.

## Does not own

- Canonical immutable traffic snapshots, UI dialogs, persistence, or proxy server lifecycle.

## Dependency rule

May depend on the proxy transport and application breakpoint contracts. It must not depend on UI, persistence, or product composition.

## Current state

Coordination, matching, budgets, deadlines, and pending state live in `:application`. This module owns only exact Netty reference/promise handling and conversion between full breakpoint messages and canonical typed edits.
