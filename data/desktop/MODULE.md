# `:data:desktop`

## Responsibility

Adapts desktop engines and storage to existing domain and application-facing repository contracts.

## Owns

- Desktop repository implementations, persistence mappers, and reusable runtime adapters.
- The application `ProxyRuntimePort` and `CaptureSessionControlPort` adapters, including active canonical session rotation for clear.
- The sole production canonical writer/query adapter, streaming proxy capture session, direct-recording session coordination, deletion-outbox reconciler, bounded global retention, and startup recovery.
- Translation between storage/engine models and feature-facing contracts.
- Composition-controlled bounded full-message aggregation when an enabled breakpoint requires editing; ordinary traffic remains streaming.
- Concrete adapters for certificates and script execution; UI sees only application ports.
- Post-capture semantic inspection orchestration and annotation persistence.
- Bounded body-integrity verification in addition to retention/startup recovery.

## Does not own

- Canonical traffic types, UI state, use-case policy, proxy internals, database schema definitions, or dependency-injection composition.

## Dependency rule

May depend on application/core contracts and concrete desktop engines/storage. It exposes adapters for the product composition root and must not depend on Koin or connectivity-product assembly. UI modules consume its capabilities through interfaces, never concrete implementation types.

## Current state

Traffic paging/detail, proxy control, direct recording, traffic clear, certificates, scripts, and inspection use application boundaries. Proxy connections and API Studio publish bounded canonical events directly into schema v13. No old traffic reader or writer remains. All adapter selection and connectivity assembly live in `:products:desktop`.
