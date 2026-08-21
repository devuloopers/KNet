# `:data:desktop`

## Responsibility

Adapts desktop engines and storage to existing domain and application-facing repository contracts.

## Owns

- Desktop repository implementations, persistence mappers, and reusable runtime adapters.
- The application `ProxyRuntimePort` and `CaptureSessionControlPort` adapters, including listener-preserving
  capture pause/resume and active canonical session rotation for clear.
- The sole production canonical writer/query adapter, streaming proxy capture session, deletion-outbox reconciler, bounded global retention, and startup recovery.
- Translation between storage/engine models and feature-facing contracts.
- Composition-controlled selective request/response aggregation using the current immutable breakpoint
  prefilter. Rule changes affect the next request on already-connected clients; ordinary and oversized
  traffic remains streaming.
- Concrete adapters for certificates and script execution; UI sees only application ports.
- Atomic, versioned certificate/rule configuration persistence as one snapshot, plus owner-only desktop Root CA material and OS-specific trust-store detection/installation.
- The bridge from the certificate runtime to the proxy-owned `ServerTlsContextProvider`; proxy code never imports CA, cache, private-key, persistence, or OS-trust implementations.
- Post-capture semantic inspection orchestration, annotation persistence, and grouped bounded annotation
  observation for retained Traffic rows.
- Bounded body-integrity verification in addition to retention/startup recovery.
- Room-backed registered-device and trusted-pairing persistence behind application ports.
- Generic breakpoint criteria-envelope persistence. Room stores a normalized protocol ID and opaque
  versioned payload without GraphQL/gRPC/WebSocket mapping branches.
- Lossless API Studio request mapping and transactional draft promotion through the collection DAO.
- Independent DataStore adapters for process-level application settings and workspace presentation state. Each
  adapter owns only its keys and applies transformations to the latest stored value inside one atomic edit.
- Workspace persistence for Traffic column widths; an absent Path-width key preserves automatic fill mode, while
  malformed non-positive or non-finite values fall back to safe defaults at the adapter boundary.

## Does not own

- Canonical traffic types, UI state, use-case policy, proxy internals, database schema definitions, or dependency-injection composition.

## Dependency rule

May depend on application/core contracts and concrete desktop engines/storage. It exposes adapters for the product composition root and must not depend on Koin or connectivity-product assembly. UI modules consume its capabilities through interfaces, never concrete implementation types.

## Current state

Traffic paging/detail, proxy control, traffic clear, certificates, scripts, inspection, and
device registration use application boundaries. The canonical query adapter supports one-session and
all-retained-session keyset pages, carries the process generation into every result, and delegates search plus
typed method/status/scheme/protocol filtering to Room. It returns SQLite-owned capture sequences and an exact
filtered total, storing that count inside the opaque continuation cursor so later pages do not repeat the count
scan or change the page snapshot. Only admitted proxy connections publish bounded
canonical events into the current Room schema. API Studio has no separate writer: it appears in Traffic only
when its outbound request is routed through the active proxy while capture is attached. A stable switchable capture sink lets traffic clear
or Traffic Stop detach stored capture state without closing client transport channels. Detached writers drain
through one bounded process-owned retirement queue; resume attaches a fresh generation immediately and each
exchange remains owned by the generation where it began. Breakpoint request/response aggregation requirements
are queried per request by the desktop runtime adapter. Adding, disabling, or editing a rule does not mutate
pipelines, restart the listener, rotate capture, or disconnect active Wi-Fi clients.
Registered identity and pairing state share the same Room source of truth; open Wi-Fi clients do not persist
identity or authorization. No old traffic reader or writer remains. All adapter selection and connectivity
assembly live in `:products:desktop`.
API Studio query parameters, headers, cookies, body fields, authentication, and scripts round-trip without
delimiter encoding or loss of disabled-row state. Body type strings exist only in the Room row and are mapped
to canonical domain enums at this boundary. Request title ownership is likewise translated between the Room
token and the domain `RequestNameOrigin` enum without leaking storage strings into presentation code.
Certificate metadata is stored in one versioned `certificate_configuration.json` document through an engine
port; corrupt documents fail visibly and remain untouched instead of being treated as empty. Private identities
are stored separately as normalized owner-only PKCS#12 files. Desktop trust commands and manual Linux guidance
are data adapters, not certificate-engine responsibilities.
The certificate runtime exposes a defensive DER copy of the active Root CA to the desktop composition root;
it does not configure HTTP clients directly. Product composition uses that value for scoped API Studio local-proxy
trust while this module retains certificate generation, persistence, and server-context ownership.
Settings persistence is side-effect free. It does not configure the HTTP client, breakpoint coordinator, proxy,
or UI when a preference flow is collected; runtime propagation is owned by the desktop product composition root.
