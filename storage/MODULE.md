# `:storage`

## Responsibility

Owns KNet's desktop persistence implementation and schema.

## Owns

- Room database, schema-v25 entities, DAOs, and storage data sources.
- The current 14-to-15, 15-to-16, 16-to-17, and 17-to-18 migrations; unsupported older development schemas may
  still be reset.
- Durable metadata records and references to externally stored bodies.
- Canonical session/connection/exchange/body/message/annotation/gap/deletion-outbox records and indexed
  single-session or global keyset queries.
- SQLite-generated immutable exchange capture sequences, exact filtered-count queries, and sequence-keyed
  paging indexes that keep ordering stable as pages are loaded.
- Single-exchange detail and bounded multi-exchange `Flow` queries for semantic annotations, allowing list
  presentation to react to post-capture inspection without reading message bodies.
- Bounded exchange batches used by import and the 100,000-row indexed-query regression fixture.
- Aggregate and oldest-first projections used by bounded global count/byte retention and startup lifecycle/body recovery.
- Non-null opaque body storage keys and indexed ownership queries used for finalized-orphan reconciliation.
- Durable corrupt/missing body states used by bounded integrity verification and fail-safe presentation.
- Registered-device identities, non-plaintext trusted credential material, and one-shot pairing invitations.
- Breakpoint rule rows containing transport filters plus a generic normalized protocol ID and opaque
  extension-owned criteria payload and deterministic priority; storage never decodes protocol semantics.
- Complete API Studio authored-request rows, including JSON-encoded query/header/cookie/body-field state.
- Opaque API Studio workspace documents and imported/reflected schema sources. Storage retains editor identity,
  semantic request kind, location, name ownership, badge, payload version, and bytes, but never decodes protobuf,
  gRPC, WebSocket, or another editor document.

## Does not own

- Canonical traffic models, use cases, UI, engine lifecycle, or unbounded in-memory body retention.

## Dependency rule

Expose storage-oriented APIs to data adapters; do not depend on UI or engine modules.

## Current state

Schema v15 adds registered identities, trusted-device credential digests/public keys, and pairing invitations
to the existing canonical capture and API Studio schema. Schema v16 adds the global timestamp/ID index used by
cross-session traffic history. Its DAO performs keyset paging plus host/path/method/status search and typed
method/status/scheme/effective-protocol filtering without materializing a session. Both auto-migrations preserve
supported existing records. Plain pairing secrets and issued credentials are never stored. Unsupported earlier
development databases are deliberately reset by Room instead of imported.
Schema v17 completes API Studio request persistence with query parameters, cookies, raw-body format, and
structured form fields. Draft promotion uses one DAO transaction so the saved row and draft deletion cannot
be observed partially.
Schema v18 adds request-title ownership. Existing rows default to `USER_DEFINED`, preventing generated naming
from changing any request title created before this capability.
Schema v19 makes the generated capture sequence the exchange row key and keeps canonical `ExchangeId` under
a unique index. KNet is still in development, so the existing destructive-development fallback recreates an
unsupported v18 database instead of carrying a compatibility migration; the exported v19 schema is canonical.
Schema v20 adds the API Studio request's HTTP-version preference. Existing v19 authored requests migrate to
`AUTO`; future unknown stored tokens are also interpreted as `AUTO` by the data adapter.
Schema v21 adds canonical traffic-origin attribution to exchanges. Existing captures migrate to
`proxy-client`; request and response protocol columns continue to preserve the two observed protocol legs.
Schema v22 adds independently encoded request and response trailer columns. Existing captures default to empty
trailers; the already-canonical stream ID and two protocol-leg columns require no compatibility model.
Schema v23 activates durable, sequence-keyed duplex protocol messages with direction, compression facts, bounded
body ownership, terminal state, and error metadata.
Schema v24 introduced the first opaque API Studio protocol document and schema-source records. Schema v25 replaces
that prototype document row with the neutral `api_studio_workspace_documents` table, allowing incomplete drafts and
saved-collection placement for any contributed editor while keeping schema sources independent. This development
schema intentionally requires no compatibility model for the discarded v24 prototype rows.
Breakpoint queries return priority then ID order so persistence and live rule evaluation share one stable order.

## Retired automatic compatibility storage

Schema 33 added the automatic setting and nullable origin/reason/temporary-identity fields; schema 34 imported enabled
legacy presets as saved exceptions. These schema versions and columns remain for compatible upgrades and historical
captures. Runtime policy ignores the retired automatic setting; retained historical SMART evidence does not create
rules. New captures have no temporary decision identity. No destructive migration or history rewrite is required.

Opaque progress updates existing counters/version only while ACTIVE, with monotonic counters and terminal-state guards. Opaque feed invalidation uses the sum of versions so another flow cannot hide an update. No schema migration is required.

Schema 35 separately adds nullable closure owner/reason/observation-time columns. Migration 34 to 35 preserves
retained captures without fabricating close evidence. The guarded opaque terminal update commits provenance with
final counters atomically; later progress or terminal events cannot replace it. See `docs/opaque_flow_closure_attribution.md`.

Schema 36 adds the default-enabled automaticTlsFallbackEnabled preference independently of the retired Smart flag.
Migration 35 to 36 retains existing actions/rules and history. Temporary decisions are never persisted; new AUTOMATIC
provenance uses the existing opaque evidence fields. See `docs/automatic_tls_fallback.md`.

## Multi-device workspace and control sessions (2026-11-13)

Schema 37 adds the verified ingress/client/connection lookup index. Migration 36-to-37 preserves all prior records. Source predicates run before paging/counting; shared sessions and bodies are retained when another source still owns them.

Device tab membership is a Room-observed registered-identity projection with indexed connection/exchange/opaque-flow existence probes. Empty connections cannot retain a tab. Source deletion and retention invalidate membership automatically; no schema change is required.
