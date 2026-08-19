# `:data:desktop`

## Responsibility

Adapts desktop engines and storage to existing domain and application-facing repository contracts.

## Owns

- Desktop repository implementations, persistence mappers, and reusable runtime adapters.
- The application `ProxyRuntimePort` and `CaptureSessionControlPort` adapters, including listener-preserving
  capture pause/resume and active canonical session rotation for clear.
- The sole production canonical writer/query adapter, streaming proxy capture session, direct-recording session coordination, deletion-outbox reconciler, bounded global retention, and startup recovery.
- Translation between storage/engine models and feature-facing contracts.
- Composition-controlled bounded full-message aggregation when an enabled breakpoint requires editing, including connection refresh when the required pipeline shape changes; ordinary traffic remains streaming.
- Concrete adapters for certificates and script execution; UI sees only application ports.
- Post-capture semantic inspection orchestration and annotation persistence.
- Bounded body-integrity verification in addition to retention/startup recovery.
- Room-backed registered-device and trusted-pairing persistence behind application ports.
- Generic breakpoint criteria-envelope persistence. Room stores a normalized protocol ID and opaque
  versioned payload without GraphQL/gRPC/WebSocket mapping branches.

## Does not own

- Canonical traffic types, UI state, use-case policy, proxy internals, database schema definitions, or dependency-injection composition.

## Dependency rule

May depend on application/core contracts and concrete desktop engines/storage. It exposes adapters for the product composition root and must not depend on Koin or connectivity-product assembly. UI modules consume its capabilities through interfaces, never concrete implementation types.

## Current state

Traffic paging/detail, proxy control, direct recording, traffic clear, certificates, scripts, inspection, and
device registration use application boundaries. The canonical query adapter supports one-session and
all-retained-session keyset pages, carries the process generation into every result, and delegates search plus
typed method/status/scheme/protocol filtering to Room. Proxy connections and API Studio publish bounded
canonical events directly into the current Room schema. A stable switchable capture sink lets traffic clear
or Traffic Stop detach stored capture state without closing client transport channels. Detached writers drain
through one bounded process-owned retirement queue; resume attaches a fresh generation immediately and each
exchange remains owned by the generation where it began. Breakpoint request/response aggregation requirements
are observed by the desktop runtime adapter; a shape change closes only active child connections so reconnects
use the current streaming or full-message pipeline without restarting the proxy listener or capture session.
Registered identity and pairing state share the same Room source of truth; open Wi-Fi clients do not persist
identity or authorization. No old traffic reader or writer remains. All adapter selection and connectivity
assembly live in `:products:desktop`.
