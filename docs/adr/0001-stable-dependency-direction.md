# ADR 0001: Stable Dependency Direction

- Status: Accepted
- Date: 2026-08-18

## Context

The audit found desktop UI, repositories, engines, storage, and feature models coupled in directions that make capture, connectivity, and future remote clients difficult to change independently.

## Decision

Dependencies point inward: `products/ui/connectivity/data/engine/storage -> application -> core contracts`. `:core:traffic` owns immutable shared HTTP snapshots and body references. The JVM-only `:application:desktop` module owns desktop commands, queries, ports, and policies. Engines implement focused capabilities and do not depend on UI, data, storage, connectivity, or composition roots.

Existing code migrates behind adapters. A source move is not required until behavior and callers have crossed the stable boundary. `verifyArchitectureFoundation` enforces the initial module and dependency rules.

## Consequences

Desktop UI, CLI, and desktop-facing remote clients can share application contracts. Mobile companions share selected `:core:*` models and protocols but own separate product workflows. Concrete Netty, Room, Compose, and OS details remain replaceable. Compatibility edges remain visible and time-bounded in `docs/implementation_plan.md` instead of being presented as final architecture.
