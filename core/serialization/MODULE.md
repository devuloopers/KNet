# `:core:serialization`

## Responsibility

Provides shared serialization configuration and serializers for stable KNet data contracts.

## Owns

- `KNetJson`, serialization helpers, and supported common serializers.

## Does not own

- Persistence schema, wire-protocol negotiation, domain models, or transport framing.

## Dependency rule

Remain a low-level core utility without dependencies on feature, UI, data, or engine modules.

## Migration direction

Use explicit versioned DTO adapters for persisted or remote traffic rather than coupling canonical models directly to storage schemas.
