# Traffic Clear performance and deferred body cleanup

Status: **COMPLETED — optimization, migration, regression, scale and packaging gates passed**.

## Clear completion and ownership

Clear still rotates/drains capture before removing terminal history. It commits metadata deletion and durable
body-cleanup operations, then refreshes the selected Traffic window. File removal continues in the background;
Clear completion does not mean every body file has already been unlinked. The capture-writer barrier is
preserved, so a busy writer or a slow storage device can still add legitimate completion latency. New active capture and other sources'
rows remain available. A background tab Close keeps its original source even if the selected device changes.

The application use case and its serialization boundary remain unchanged. The desktop maintenance adapter
runs bounded metadata transactions on IO. Global Clear selects closed session IDs directly, avoiding retention
count/byte/GROUP BY work and whole-session Kotlin body-ID lists. Source Clear retains its 128-exchange bound
and queues arbitrarily large message-body sets inside SQLite. Deletion admission and metadata removal commit
atomically; cancelled/failed operations leave durable, retryable work.

Traffic suppresses redundant page/count/facet reads for the source being cleared. It invalidates pre-clear page
results and performs one explicit post-commit refresh, including replacement-session traffic. Sibling workspaces
remain readable during a background source clear. This avoids hiding history before its deletion is durable.

## Schema v38

The additive 37-to-38 migration preserves capture, policies, credentials and pending cleanup:

- Index request-body, response-body and duplex-message body references. Shared-body safety guards now use
  indexed probes rather than correlated full-table scans for each candidate body.
- Add `deletion_outbox.nextAttemptAtEpochMillis`, default zero so existing unattempted work is immediately due.
- Index `(nextAttemptAtEpochMillis, id)` for bounded due selection and the next retry deadline.

Index creation incurs a one-time migration cost proportional to retained history. No destructive reset is used.
All retained migration generations, including v37, are exercised against the current schema.

## Background convergence

One `BodyDeletionWorker` starts in the existing process-owned IO policy scope. It observes committed outbox
changes, including work queued by retention, recovery and a partially completed/cancelled Clear. A conflated
notification avoids unbounded jobs and lost wakeups; an empty queue sleeps without periodic polling.

Each pass retains at most 256 operations. Success/missing-file acknowledgements use one database statement per
batch, instead of one Room write per file. Failed files receive durable 5/10/20/40/60-second retry deadlines;
newly queued healthy work can pass them. Database-level failures retry without discarding outbox entries.
Startup recovery and the worker share a serialized reconciler in product composition.

Cancellation propagates. A crash/cancellation after unlink but before acknowledgement leaves work that safely
replays as an already-missing file. Scope shutdown cancels and drains the worker and its Room observer before
storage teardown. Normal cleanup does not read/decode payload contents, create a thread pool, or retain body
byte arrays. Direct low-level session-maintenance APIs retain their explicitly bounded immediate-reconcile
behavior; the interactive Clear path delegates file convergence to the worker.

## Measured results

The original audit used generated fixtures with two sources, two distinct bodies per exchange and one message
reference per exchange. The same SQL workload using the actual exported v38 schema produced:

| Exchanges cleared / total | Original metadata SQL | v38 metadata SQL |
|---|---:|---:|
| 500 / 1,000 | 313.07 ms | 11.13 ms |
| 2,500 / 5,000 | 7,348.91 ms | 54.38 ms |
| 5,000 / 10,000 | 29,719.61 ms | 108.73 ms |

These are isolated in-memory SQLite stages, excluding Room, writer rotation and filesystem deletion. They do
not claim a whole-app speedup by the same factor. EXPLAIN changes both reference guards from `SCAN kept` to
indexed searches.

The targeted real Room/SQLite fixture cleared 5,000 exchanges and queued 10,000 bodies in **291 ms**
(**258 ms** during the full data suite), preserving
the other source's 5,000 exchanges/10,000 bodies. Its regression budget is five seconds. A separate test queues
5,002 bodies belonging to one stream and verifies that the background worker drains every batch. A further
mixed-device SQL fixture removed 25,000 interleaved exchanges from a 50,000-exchange/100,000-body history in
1,202.82 ms while preserving its sibling source. A blocked-file
fixture proves metadata Clear completes while filesystem cleanup is still suspended.

## Regression and qualification

- Migration: retained generations and v37 pending cleanup survive, with the new default deadline and indexes.
- Worker: startup backlog, newly committed work while idle, more than one batch, actual FileBodyStore removal,
  single start, batched acknowledgements, failed-file fairness/deadline, acknowledgement failure and cancellation replay.
- Clear: distinct-body scale, large streams, shared bodies, global/selected source, active capture preservation,
  slow files, partial generations, duplicate clicks, device switching and failure/retry after catalog rotation.
- UI: batch invalidations do not reload disappearing history; one final refresh displays replacement traffic.

The full feature qualification passed **1,560 tests across 30 tasks**, with zero failures/errors/skips.
The separate 100,000-row query/storage baseline passed as well: source-filtered query 738 ms, filtered page
652 ms, fixture construction 1,513 ms, and database/WAL footprint 50,486,352 bytes (within the existing
5,000-ms query / 256-MiB storage budgets). Total: **1,561 tests across 31 tasks**. Fourteen regression methods
were added for this repair. All architecture and Network Protection ledger checks passed.

The desktop distributable built successfully. Its packaged storage (150 classes), data (334), Traffic UI (249)
and product (68) class files match the tested outputs. The updated bundle is:
`/Users/devuloopers/.codex/verification/knet-clear-fix-2026-11-14/build/products/desktop/compose/binaries/main/app/KNet.app`.
Quit the previous desktop build before opening the updated app so the normal v38 migration and worker startup
run in one process. The UI was not physically automated; the measured times describe generated storage fixtures,
not a guarantee of identical interactive latency under every capture load or disk condition.

Evidence is under `/Users/devuloopers/.codex/verification/knet-clear-fix-2026-11-14/`; original audit data is under
`/Users/devuloopers/.codex/verification/knet-clear-audit-2026-11-14/`. Tests use disposable databases/files. The
running user's database was neither migrated nor cleared during implementation, and the desktop was not
relaunched. Rebuild/restart is required to activate v38 and the process worker.
