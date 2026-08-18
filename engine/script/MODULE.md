# `:engine:script`

## Responsibility

Provides sandboxed script execution, runtime selection, bindings, timeouts, diagnostics, and result collection.

## Owns

- Script runtime contracts and Kotlin/JavaScript execution implementations.
- Resource limits, timeout enforcement, bindings, and sanitized failures.

## Does not own

- Script editor UI, persisted scripts, proxy lifecycle, or canonical traffic state.

## Dependency rule

Accept explicit inputs and return explicit results; do not reach into UI or global runtime state.

## Migration direction

Build bindings from shared traffic snapshots and typed patches so scripting remains a consumer/extension of stable contracts.
