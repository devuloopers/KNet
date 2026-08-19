# `:storage`

## Responsibility

Owns KNet's desktop persistence implementation and schema.

## Owns

- Room database, schema-v15 entities, DAOs, and storage data sources.
- The current 14-to-15 migration; unsupported older development schemas may still be reset.
- Durable metadata records and references to externally stored bodies.
- Canonical session/connection/exchange/body/message/annotation/gap/deletion-outbox records and indexed keyset queries.
- Bounded exchange batches used by import and the 100,000-row indexed-query regression fixture.
- Aggregate and oldest-first projections used by bounded global count/byte retention and startup lifecycle/body recovery.
- Non-null opaque body storage keys and indexed ownership queries used for finalized-orphan reconciliation.
- Durable corrupt/missing body states used by bounded integrity verification and fail-safe presentation.
- Registered-device identities, non-plaintext trusted credential material, and one-shot pairing invitations.
- Breakpoint rule rows containing transport filters plus a generic normalized protocol ID and opaque
  extension-owned criteria payload; storage never decodes protocol semantics.

## Does not own

- Canonical traffic models, use cases, UI, engine lifecycle, or unbounded in-memory body retention.

## Dependency rule

Expose storage-oriented APIs to data adapters; do not depend on UI or engine modules.

## Current state

Schema v15 adds registered identities, trusted-device credential digests/public keys, and pairing invitations to the existing canonical capture and API Studio schema. The 14-to-15 auto-migration preserves existing records while adding these tables. Plain pairing secrets and issued credentials are never stored. Unsupported earlier development databases are deliberately reset by Room instead of imported.
