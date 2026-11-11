# Opaque connection closure attribution

Status: completed; automated qualification passed (2026-11-11).

Traffic Technical details shows Closed by, Close reason and Close observed at for terminal opaque connections.
The evidence describes the first observed transport close signal, including an input half-close; it does not
identify a remote app's internal reason or reveal encrypted HTTP/Billing results.

| Owner | Evidence |
|---|---|
| Device | Incoming EOF on the downstream peer connection |
| Server | Incoming EOF on the upstream peer connection |
| KNet | Explicit outbound local close, proxy stop, connection-set reset, inactivity timeout or write timeout |
| Unknown | Generic I/O error, inactive without a signal, opposite EOFs in one clock tick, or absent legacy evidence |

## Ownership and persistence

`RawTunnelLifecycle` serializes bounded evidence before EOF/close propagation. Subsequent local cleanup and
peer callbacks cannot replace the original owner. Opposite EOF observations with the same wall-clock millisecond
are conservatively ambiguous. Final delivery outcome remains independent: a pending write can still fail after
an EOF, and closure evidence does not change that failure into success.

The relay intercepts explicit outbound closes. Proxy shutdown and active-connection reset record their specific
intent on the owning channel event loop before closing sockets. This ordering matters because a channel close
future can end canonical capture before Netty delivers channelInactive. Timeout exceptions explicitly identify
KNet; arbitrary IOException text never proves a remote initiator or TCP reset packet.

Capture retains three small typed metadata values, synchronizes them with terminal counters/version and persists
them in the same terminal update. No payload inspection, packet-capture permission, per-flow coroutine or timer
is added. Capture detachment by itself never claims KNet closed the transport. Already terminated snapshots and
stale progress/terminal events cannot replace closure evidence.

Room schema 35 adds nullable closureInitiator, closureReason and closureObservedAtEpochMillis. Migration 34 to 35
preserves all rows and leaves legacy evidence absent. Existing records show Unknown/Unavailable. Partial/corrupt
stored metadata cannot invent an owner or time. Canonical queries and Traffic row mapping carry the typed evidence;
presentation remains in the existing collapsed Technical details section using KNet's shared inspector layout.

## Automated cases

- Portable contract: every reason's supported owner; negative time and unsupported evidence combinations.
- Relay: device/server EOF, propagated cleanup, explicit local close before close-future callbacks, typed
  inactivity/write timeouts, generic I/O error, ambiguous EOFs, unexplained inactive and late callbacks.
- Real sockets and Room: device/server FIN, proxy stop, active-connection reset with the listener retained,
  actual socket reset kept Unknown, nonzero counters, canonical mapping and database reopen.
- Real TLS: an explicitly configured inactivity deadline retains KNet attribution with its typed timeout.
- Persistence: stale progress and duplicate higher-version terminal events cannot overwrite the initial close;
  legacy/corrupt/partial mapping stays honest; schema 34 upgrades preserve outcome/counters with unknown closure.
- UI: provenance is collapsed initially; each owner, absence of evidence, reason and millisecond timestamp appear
  under Technical details with existing controls and shared scrolling.

## Executed verification

The targeted model, relay, migration, data, UI and desktop-compile build passed in 1 minute 18 seconds. The full
feature gates then passed with the additional concurrent-EOF test and compact/larger-text inspector case:

```sh
./gradlew --init-script /tmp/knet-traffic-inspector-20261110.init.gradle.kts \
  protectedTrafficQualification networkConditionsQualification :products:desktop:compileKotlin \
  --max-workers=3 --no-daemon --no-configuration-cache --console=plain
git diff --check
```

Result: BUILD SUCCESSFUL in 7 minutes 26 seconds; 289 actionable tasks (104 executed, 185 up to date). Independently
counted feature-gate XML evidence contains **1,005 tests across 25 outputs, zero failures/errors/skips**. No mandatory
suite was missing, excluding the separate storage-scale qualification not invoked by these feature gates. Relevant
outputs contain 22 portable traffic-model tests, 5 storage tests, 90 desktop data tests, 154 proxy tests and 79
Traffic UI tests. All six architecture checks and requirements-ledger validation passed.

Device/server FIN, actual reset, proxy stop and explicit connection-set reset cases passed through real sockets,
canonical persistence/mapping and reopening Room. Concurrent EOF callbacks were exercised repeatedly. Standard and
compact/larger-text `close-provenance-*.png` renders under the Traffic module's `build/reports/traffic-protection-ui/`
were inspected. The relay/model/data closure suites are mandatory in the aggregate report gate.

Build outputs were isolated under `/tmp/knet-traffic-inspector-20261110`; the running desktop app was not launched
or overwritten and its database was not upgraded. Execution log and XML-count summary are
`/tmp/knet-close-attribution-qualification.log` and `/tmp/knet-close-attribution-qualification-summary.json`.
Rebuild/relaunch and a new capture are required to observe attribution; historical rows retain Unknown evidence.

Physical-device acceptance remains separate. A TCP peer observed on one connection leg could itself be an
intermediary. Knowing the observed side does not establish the reason within Google Play or any other service.
