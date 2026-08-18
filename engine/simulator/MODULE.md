# `:engine:simulator`

## Responsibility

Implements deterministic network-condition simulation for proxied connections.

## Owns

- Latency, bandwidth, packet/connection behavior profiles and runtime statistics.
- Netty handlers that apply selected simulation policy.

## Does not own

- Proxy startup, UI state, persisted preferences, connectivity, or traffic storage.

## Dependency rule

Attach through a proxy extension point and remain independently testable.

## Migration direction

Expose configuration through application commands while keeping transport handlers inside this engine.
