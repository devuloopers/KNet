# `:data:desktop`

## Responsibility

Adapts desktop engines and storage to existing domain and application-facing repository contracts.

## Owns

- Desktop repository implementations, persistence mappers, and reusable runtime adapters.
- The application `ProxyRuntime` and `CaptureSessionControl` adapters, including listener-preserving
  capture pause/resume and active canonical session rotation for clear.
- The sole production canonical writer/query adapter, streaming proxy capture session, deletion-outbox reconciler, bounded global retention, and startup recovery.
- Translation between storage/engine models and feature-facing contracts.
- Composition-controlled selective request/response aggregation using the current immutable breakpoint
  prefilter. Rule changes affect the next request on already-connected clients; ordinary and oversized
  traffic remains streaming.
- Concrete adapters for certificates and script execution; UI sees only application contracts.
- Atomic, versioned certificate/rule configuration persistence as one snapshot, plus owner-only desktop Root CA material and OS-specific trust-store detection/installation.
- The bridge from the certificate runtime to the proxy-owned `ServerTlsContextProvider`; proxy code never imports CA, cache, private-key, persistence, or OS-trust implementations.
- A process-stable KNet-CA-signed TLS identity for the fixed companion certificate-challenge server name. Private
  key material remains inside the desktop certificate runtime and only the server socket factory plus defensive
  public-root copies reach connectivity composition.
- Post-capture semantic inspection orchestration, annotation persistence, and grouped bounded annotation
  observation for retained Traffic rows.
- Bounded body-integrity verification in addition to retention/startup recovery.
- Room-backed registered-device and trusted-pairing persistence behind application contracts, including atomic
  compare-and-set credential rotation that rejects stale, replayed, revoked, or concurrently replaced digests.
- Generic breakpoint criteria-envelope persistence. Room stores a normalized protocol ID and opaque
  versioned payload without GraphQL/gRPC/WebSocket mapping branches.
- Lossless API Studio request mapping and transactional draft promotion through the collection DAO.
- Independent DataStore adapters for process-level application settings and workspace presentation state. Each
  adapter owns only its keys and applies transformations to the latest stored value inside one atomic edit.
- Workspace persistence for Traffic column widths; an absent Path-width key preserves automatic fill mode, while
  malformed non-positive or non-finite values fall back to safe defaults at the adapter boundary.
- Durable mapping of canonical traffic origin plus separate client-request and upstream-response protocol values.
- Lossless mapping of native stream identity and independently ordered request/response trailers through Room
  schema v22, paging, restart restoration, and selected-exchange detail.
- Canonical framed-message writing, paging, body access, retention, recovery, and clear-traffic cleanup through
  schema v23 without interpreting gRPC or any other framed protocol.
- Room-backed opaque API Studio workspace documents and descriptor/reflection schema sources through schema v25.
  Incomplete drafts, generated-name updates, user-name ownership, and collection promotion cross one neutral adapter;
  byte arrays are defensively copied and never decoded here.

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
when its outbound request is routed through the active proxy while capture is attached, and its canonical origin
survives paging and restart. A stable switchable capture sink lets traffic clear
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
Contributed API Studio editors share `RoomApiStudioWorkspaceDocumentStore`. The adapter understands identity,
placement, naming ownership, and payload version only; gRPC/protobuf and future editor payloads stay opaque.

## Network Conditions snapshots

Network Conditions publishes one committed multi-table read snapshot per invalidation and serializes validated
mutations in SQLite write transactions across repository instances. Its transaction owner retains Room connection
confinement while preserving the caller's cancellation, so interrupted work rolls back. Profile/reference cleanup
is atomic; corrupt policy data still fails closed without deleting saved rows. Packet policy v2 and the remaining
diagnostic/migration matrix have independent completion requirements.

The product supplies the application-owned coroutine scope explicitly; repository observers do not create an
unowned background scope that can survive database/application shutdown.

## Protected TLS handling

The TLS adapter gives saved manual/imported rules priority over the global default. Under automatic Inspect it may
record a temporary exact client/session decision after explicit downstream certificate rejection and proven certificate
presentation. InMemoryAutomaticTlsFallback owns the bounded 30-minute cache and one product-scoped expiry job;
late epochs, unavailable admission, invalid authorities and ordinary failures cannot learn. Session lifecycle clears
ownership and manual changes forget affected entries. Canonical capture records new AUTOMATIC provenance separately
from retired SMART history; no new smartEntryId or permanent rule is created. Room stores only the global preference
and existing rules. The certificate provider validates its root/leaf before TLS context creation. No service catalogue
is injected. See `docs/automatic_tls_fallback.md`.

A single session-owned 500 ms sampler coalesces changed opaque counters into monotonic progress events. It joins before shutdown; non-blocking transport observations and terminal snapshots are serialized per flow. Flush samples counters before its barrier. No payload is retained for opaque traffic.

Real TLS keep-alive qualification crosses a shortened HTTP inactivity deadline before reusing the same connection,
including an SNI-selected manual tunnel. An explicitly configured raw inactivity limit persists a typed timeout
with the final byte counters. Historical generic failures are retained without guessing an unrecorded cause.

Opaque capture retains typed close evidence under the flow's metadata monitor and persists it in its terminal
event. Canonical mapping preserves nullable legacy evidence and safely maps corrupt evidence to Unknown.
Real-socket close qualification also checks database reopening, independent of encrypted application outcomes.
