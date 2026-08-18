# `:storage`

## Responsibility

Owns KNet's desktop persistence implementation and schema.

## Owns

- Room database, schema-v14 entities, DAOs, and storage data sources.
- Development-stage destructive reset when an older schema is opened; no old-schema import path.
- Durable metadata records and references to externally stored bodies.
- Canonical session/connection/exchange/body/message/annotation/gap/deletion-outbox records and indexed keyset queries.
- Bounded exchange batches used by import and the 100,000-row indexed-query regression fixture.
- Aggregate and oldest-first projections used by bounded global count/byte retention and startup lifecycle/body recovery.
- Non-null opaque body storage keys and indexed ownership queries used for finalized-orphan reconciliation.
- Durable corrupt/missing body states used by bounded integrity verification and fail-safe presentation.

## Does not own

- Canonical traffic models, use cases, UI, engine lifecycle, or unbounded in-memory body retention.

## Dependency rule

Expose storage-oriented APIs to data adapters; do not depend on UI or engine modules.

## Current state

Schema v14 contains canonical capture records and stores each saved API request method as one wire token. The obsolete `CUSTOM` plus `customMethod` column split is removed. Earlier development databases are deliberately reset by Room instead of migrated.
