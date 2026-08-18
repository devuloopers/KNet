# `:core`

## Responsibility

Groups stable, platform-neutral contracts and low-level utilities. It owns no code directly.

## Dependency rule

Child modules stay narrowly scoped and must not depend outward on UI, application composition, data adapters, or platform implementations.
