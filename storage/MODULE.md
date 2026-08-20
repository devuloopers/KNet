# `:storage`

## Responsibility

Owns KNet's desktop persistence implementation and schema.

## Owns

- Room database, schema-v18 entities, DAOs, and storage data sources.
- The current 14-to-15, 15-to-16, 16-to-17, and 17-to-18 migrations; unsupported older development schemas may
  still be reset.
- Durable metadata records and references to externally stored bodies.
- Canonical session/connection/exchange/body/message/annotation/gap/deletion-outbox records and indexed
  single-session or global keyset queries.
- Bounded exchange batches used by import and the 100,000-row indexed-query regression fixture.
- Aggregate and oldest-first projections used by bounded global count/byte retention and startup lifecycle/body recovery.
- Non-null opaque body storage keys and indexed ownership queries used for finalized-orphan reconciliation.
- Durable corrupt/missing body states used by bounded integrity verification and fail-safe presentation.
- Registered-device identities, non-plaintext trusted credential material, and one-shot pairing invitations.
- Breakpoint rule rows containing transport filters plus a generic normalized protocol ID and opaque
  extension-owned criteria payload; storage never decodes protocol semantics.
- Complete API Studio authored-request rows, including JSON-encoded query/header/cookie/body-field state.

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
