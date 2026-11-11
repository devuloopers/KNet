# Opaque TLS lifecycle reporting

Status: completed; lifecycle qualification passed on 2026-11-10 and quiet-tunnel follow-up passed on 2026-11-11.

A tunneled TLS row represents a transport connection, not a decrypted HTTP request. An application can finish an
operation and reuse the same connection. KNet cannot derive HTTP status or business success from encrypted traffic.

## Behavior

- An unterminated opaque flow shows Active in both the table and inspector. An orderly transport completion shows
  Closed; rejection, failure, timeout and cancellation keep their distinct typed outcomes. HTTP request labels and
  status codes retain their existing meanings.
- A single session-owned coroutine samples changed counters every 500 ms. It publishes cumulative metadata only;
  there is no task per connection, per-chunk database write, payload copy or idle-counter update. Explicit flush also
  samples current values before the writer barrier. The sampler is cancelled/joined before capture shutdown drains.
- A short flow-local monitor serializes counter observations, progress snapshots and final state. Versions increase
  across progress and termination, and one terminal snapshot includes the final admitted observations.
- Room accepts newer progress only for ACTIVE rows, never decreases counters, and never reopens terminal rows. The
  existing version/counter columns suffice; no schema migration is needed. The feed invalidation scalar sums flow
  versions so a low-version connection still notifies observers when another connection has a larger version.
- These are observed encrypted transport bytes, including TLS overhead; they are not decrypted payload sizes or
  evidence that a purchase succeeded. Final duration describes transport lifetime.

## Regression cases

| Case | Automated evidence |
|---|---|
| Real TLS 1.2 and 1.3 requests finish on an open reusable socket | OpaqueKeepAliveCaptureTest: receive two HTTP 200 bodies through origin TLS, retain the same ACTIVE flow, observe increasing Room counters without calling capture flush, then close the socket and observe COMPLETED |
| Live and final counters | StreamingProxyCaptureSessionTest: pre-terminal flush, idle coalescing, concurrent observations in both directions, final counters, ignored duplicate termination/post-terminal bytes |
| Shutdown | StreamingProxyCaptureSessionTest: stop active HTTP/opaque capture, persist cancellation reason and final counters, ignore later bytes and flush |
| Stale progress and changing another flow | CanonicalSessionWriterTest: version-10 neighbor cannot mask version-1-to-2 changes, stale/newer lower counters cannot regress data, progress cannot reopen a terminal row |
| Invalid metadata | CaptureEventsTest: positive progress version, nonnegative coordinates/counters |
| UI semantics | OpaqueFlowStatusPresentationTest: Active/Closed with HTTP status zero, preserved rejection/cancellation and ordinary HTTP behavior |
| Actual inspector rendering | OpaqueFlowInspectorTest: table label equals badge, counters visible, no Pending/In Progress language for an active/closed tunnel |

The real-socket and status suites are mandatory in the aggregate network-protection report gate. Physical-device
billing/application success remains outside the inference available from a TLS tunnel.

## Executed verification

```sh
./gradlew --init-script /tmp/knet-traffic-inspector-20261110.init.gradle.kts \
  protectedTrafficQualification networkConditionsQualification :products:desktop:compileKotlin \
  --max-workers=3 --no-daemon --no-configuration-cache --console=plain
git diff --check
```

Final result: BUILD SUCCESSFUL in 37 seconds, 289 actionable tasks (67 executed, 222 up to date), following the broader
rebuild. The 25 distinct feature-gate XML outputs contain **978 tests, zero failures/errors/skips**; separate load
qualification is excluded. Relevant outputs contain 20 portable traffic-model tests, 84 data tests and 75 Traffic
UI tests. All six architecture checks and requirements-ledger validation passed. The final table/inspector render
assertion reads individual accessibility text entries because table rows merge their cell semantics.

Fresh `tunnel-active.png` and `tunnel-closed.png` renders under the Traffic module's
`build/reports/traffic-protection-ui/` show both the actual table and inspector together; both were inspected.
Build outputs were isolated under a temporary root, without launching the desktop app or replacing running JARs.
A rebuild/relaunch is required to load the change. Existing broader physical/platform qualification work remains open.

## Quiet tunnel follow-up (2026-11-11)

Status: completed; targeted tests, both feature gates and desktop compilation passed.

The reported Play tunnel persisted 11,115 uploaded bytes and 8,966 downloaded bytes over 62.526 seconds, ending
with `duplex_io_failed`. Its previous Active state correctly described an open transport, but the raw relay inherited
the 60-second HTTP/setup inactivity deadline. Every relay exception, including read/write timeout exceptions,
was reduced to the same generic failure. The timing is consistent with the inactivity cutoff, but the old record
cannot prove which exception occurred. That historical record is not rewritten.

Established raw CONNECT relays now replace the HTTP/setup timer with their own optional inactivity limit
(`rawTunnelIdleTimeoutMillis`, zero by default). Quiet reusable tunnels remain open while real pending-write,
connect and setup deadlines and admission bounds remain enforced. Explicit raw inactivity and write timeout
exceptions retain typed reasons in capture/Room; the inspector explains the deadline and the table shows Timed Out.
Genuine I/O errors still show Failed. Neither a byte count nor an orderly close claims an encrypted app operation
succeeded.

The earlier keep-alive tests reused a connection immediately and did not cross its inactivity deadline. The new
regression cases cover that missed interval:

| Case | Automated evidence |
|---|---|
| Timeout classification reproduced before the fix | RawTunnelFailureReasonTest: read/write timeout assertions failed as `duplex_io_failed`; normal IOException passed |
| Quiet established tunnel | RawTunnelInactivityTest: remove HTTP timer, remain active beyond it, retain stalled-write handler |
| Optional raw limit | RawTunnelInactivityTest: explicit inactivity limit closes with READ_TIMED_OUT; zero accepted, negative rejected |
| Real TLS 1.2 direct tunnel | OpaqueKeepAliveCaptureTest: finish HTTP response, idle for over twice the shortened HTTP deadline, receive another response on the same TLS socket and canonical flow |
| Real TLS 1.3 SNI-scoped tunnel | Same socket reuse after inactivity, with an IP CONNECT route and localhost manual SNI rule; origin certificate preserved |
| Timeout after a completed operation | Real TLS response received first, then explicit raw inactivity limit expires; Room retains typed timeout and nonzero final counters |
| Honest presentation | OpaqueFlowStatusPresentationTest: specific read/write timeout labels/explanations, no invented HTTP status, generic historic I/O failure remains generic |

An actual stalled outbound write also exercises WriteTimeoutHandler, peer closure and failed promise callbacks,
ensuring they cannot overwrite WRITE_TIMED_OUT with a generic write failure. A headless inspector render verifies
the timeout explanation, byte counters and future-connection actions together; `tunnel-inactivity-timeout.png`
was inspected. Both new engine regression suites are mandatory in the aggregate report gate.

The targeted build passed in 48 seconds. Re-running the feature-gate/desktop-compile command above passed in
7 minutes 4 seconds (289 actionable tasks: 86 executed, 203 up to date). The 25 distinct feature-gate XML outputs
contain **989 tests, zero failures/errors/skips**, with no missing mandatory suites other than the deliberately
separate storage-scale qualification. Relevant outputs contain 147 proxy tests, 85 data tests and 78 Traffic UI
tests. All six architecture checks, requirements-ledger validation and `git diff --check` passed. Logs and the
independently counted XML summary are `/tmp/knet-tunnel-idle-qualification.log` and
`/tmp/knet-tunnel-idle-qualification-summary.json`.

Verification uses isolated build outputs; it does not launch or overwrite the running desktop app. Device Billing
acceptance and the exact cause of the already captured generic failure require separate physical evidence.
