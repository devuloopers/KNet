# `:core:scripting`

## Responsibility

Defines the small, platform-neutral scripting vocabulary shared by authored collections, desktop
editors, application contracts, and concrete script engines.

## Owns

- Supported script languages.
- Script lifecycle phases.
- Reusable snippet metadata and language-specific source templates.
- Immutable assertion results emitted by script execution.

## Does not own

- Script runtime APIs, mutable host objects, engine selection, sandboxing, deadlines, UI state,
  persistence entities, or HTTP execution.

## Dependency rule

This is a leaf contract module with no production project dependencies. Application, domain,
engine, and UI modules may depend on it; it never depends on them.
