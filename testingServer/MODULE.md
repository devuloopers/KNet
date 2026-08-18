# `:testingServer`

## Responsibility

Provides deterministic local HTTP endpoints for KNet integration and end-to-end tests.

## Owns

- Test-only routes for methods, payload sizes, statuses, streaming, and protocol scenarios.
- Reusable server fixtures that exercise network behavior.

## Does not own

- Production runtime code, product configuration, or test assertions for consumer modules.

## Dependency rule

Production modules must never depend on this module; it is a test fixture/application only.

## Migration direction

Extend with bounded fixtures for each new protocol and backpressure scenario as those capabilities land.
