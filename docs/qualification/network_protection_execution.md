# Network protection completion execution record

Date: 2026-11-10

Overall status: **IN PROGRESS**. The complete feature is not yet software-qualified.

Latest verification: **954 module tests, zero failures/errors/skips across 26 distinct task outputs**, plus **three
passing measured-load cases**. Desktop packaging, protocol gates and Android/iOS build checks passed within their
documented scopes. **107 software requirements remain unclosed**; this is not “only physical testing remains.”
Some entries retain baseline/partial evidence, so this count is not a count of 107 wholly missing features.

Retained evidence: [machine result](network_protection_latest_result.json),
[measured workloads](network_protection_measurements_2026-11-10.tsv),
[requirement ledger](network_protection_cases.tsv) and [remaining completion work](../network_conditions_protected_traffic_completion_plan.md).

| Measured workload | Payload | Elapsed | Aggregate payload rate | Latest first byte | Sampled queue peak |
| --- | ---: | ---: | ---: | ---: | ---: |
| Four concurrent downloads | 800,000 B | 64.146 s | 99,772.8 bit/s | 0.629 s | 262,144 B |
| Four concurrent uploads with EOF/acknowledgement | 800,000 B | 64.008 s | 99,987.2 bit/s | 0.499 s | 262,144 B |
| Four incremental large transfers | 524,288,000 B | 3.059 s | 1.371 Gbit/s | 0.012 s | 0 B in the shaper |

The first two enforce ±5% around 100,000 bit/s, over a minute after all flows begin and a five-second first-byte
bound. The third uses unthrottled proxy paths and paced readers for byte/hash/memory integrity; it is not a 500 MiB
transfer at 100 kbps or an offline-UDP qualification. Measured heap increases were 37 MiB/12 MiB/17 MiB respectively;
additional direct-memory increases were 16 MiB/0/0, with warmed pooling noted. Each test uses a 512 MiB heap and
enforces separate 128 MiB growth budgets for its large-stream heap/direct fixture. RSS/plateau/soak requirements stay open.

The broad protocol/product command passed in **10 minutes 13 seconds** (708 tasks: 139 executed, 569 up to date).
The final code/bundle recheck after shutdown ownership fixes passed in **1 minute 37 seconds** (297 tasks: 72
executed, 225 up to date). Its command was:

```sh
./gradlew networkProtectionQualification :engine:simulator:networkProtectionLoadQualification \
  :products:desktop:createDistributable --max-workers=3 --no-daemon --console=plain
```

Logs: `/tmp/knet-completion-final-validation.log` and `/tmp/knet-completion-final-verification.log`. The broad
command reported the existing protobuf task configuration-cache serialization warnings; all requested gates
passed and that invocation discarded its configuration cache. The final feature/bundle invocation stored its cache.
No desktop application was launched, no physical test was run, and no signed native adapter archive is implied.
The strict completion invocation (`verifyNetworkProtectionLedger -PrequireNetworkProtectionComplete=true`)
correctly rejected the 107 unclosed software requirements. Its expected negative result prevents a passing module
count from being mistaken for complete software acceptance. Normal ledger tracking and architecture checks remain
separate successful development gates.

The machine-readable [requirements ledger](network_protection_cases.tsv) retains all 114 NC requirements,
22 Protected Traffic acceptance assertions, 14 Smart categories and six audit findings. The
[completion plan](../network_conditions_protected_traffic_completion_plan.md) defines dependencies and final closure.
`verifyNetworkProtectionLedger` checks identity, ownership, source/evidence references and status rules; strict
closure uses `-PrequireNetworkProtectionComplete=true` and rejects outstanding software or mixed requirements.
Normal validation intentionally permits explicitly tracked pending work.

The ledger now carries verified evidence for the pre-existing covered checkpoint rather than relabelling all
baseline cases missing. Partial cases remain partial. The [native delivery inventory](native_delivery_prerequisites.md)
records backend boundaries, package/signing prerequisites and the still-missing cross-provider budget coordinator.

## C01: Android UDP association teardown

Implemented association-owned jobs/queues, terminal registry admission, serialized send/close, cancellation-safe
queue completion, protected-socket failure cleanup, startup rollback and rejection of restarting a closed gateway.
Shutdown cannot recreate sockets through an old association. Unshaped/DNS queues retain the same admission bounds.

Permanent `UdpAssociationLifecycleTest` cases cover delayed-upload close without late creation/delivery, gateway
shutdown and repeated close, delayed-download close, queue saturation, protection failure, terminal registry
admission and 100 close/create races. The existing stable-flow/DNS and packet-policy tests remain in the suite.

Executed:

```sh
./gradlew :connectivity:companion:testAndroidHostTest --console=plain
```

Result: **BUILD SUCCESSFUL in 51 seconds**, 39 actionable tasks (12 executed, 27 up to date), including the seven
new lifecycle regressions. Log: `/tmp/knet-completion-udp-tests.log`. A1 is closed for this implementation/test scope;
Android physical VPN tests and the remaining packet-policy work are still open.

## C02: Smart certificate transmission evidence

The observer now distinguishes NOT_PRESENTED/PRESENTED/UNKNOWN. Supported OpenJDK JSSE contexts must produce a
certificate-authenticated flight, finish the outbound engine flight in NEED_UNWRAP and complete that transport
write successfully. SNI context selection, partial/failed writes and unsupported providers cannot establish it.
The observer keeps no certificate/key/payload material. Transmission does not prove peer trust or business success.

OpenJDK JSSE provider behavior was checked against its
[certificate-message producer](https://github.com/openjdk/jdk21u/blob/master/src/java.base/share/classes/sun/security/ssl/CertificateMessage.java),
the public [SSLEngine contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLEngine.html),
and Netty's [SNI handler API](https://netty.io/4.1/api/io/netty/handler/ssl/SniHandler.html). The mechanism does not use
reflection into JDK internals or weaken upstream certificate verification.

The focused classifier and real-socket Smart suites passed:

```sh
./gradlew :engine:proxy:test --tests '*TlsInspectionFailureClassifierTest' \
  :data:desktop:jvmTest --tests '*SmartCompatibilitySocketIntegrationTest' --console=plain
```

Result: **BUILD SUCCESSFUL in 30 seconds**, 63 actionable tasks (24 executed, 39 up to date). TLS 1.2, TLS 1.3 and
mTLS independently reconnect with origin certificates and 100 kbps behavior retained. The synthetic SNI-only test
now correctly rejects presentation evidence. Log: `/tmp/knet-completion-tls-evidence.log`.
Six controlled real-JSSE engine cases subsequently passed for TLS 1.2/1.3 successful and failed transport flights,
incomplete ClientHello and potential session resumption. Resumption offers stay conservative because retained
session certificate metadata cannot prove a newly transmitted Certificate message. Pairing security-epoch changes
and Forget also fence late handshake callbacks. Full C02 lifecycle/security closure remains tracked separately.

## C03: Transactional policy publication and mutation

Settings, profiles and rules are now read through one Room read transaction per table invalidation. Independently
combined flows could otherwise publish a committed settings row with stale profile/rule rows and briefly disable
a valid policy. Immediate SQLite write transactions now own read/validate/modify/write across repository instances;
profile deletion and quick-add cleanup commit together. No schema change or destructive reset is needed.
Product composition supplies the application-owned coroutine scope explicitly, so policy observers have the
same shutdown owner as their engine/database rather than creating a detached lifetime.
The final restart recheck caught cancellation followed by premature SQLite close. Fixtures now join their scope
before closing storage. Production bootstrap likewise drains shared and startup policy scopes on its existing
shutdown worker before database close, with a bounded deadline and container-close cancellation. Cleanup/timeout
and Koin ownership tests protect this order; an exception during rollback is not suppressed as a successful close.

The new real-SQLite suite covers 50 atomic replacements with deliberately invalid intermediate rows, 30 pairs of
concurrent independent-repository settings edits, and rollback after cancellation followed by successful reuse.
It exposed a caller-ownership issue at Room's database-scope connection boundary. The transaction owner preserves
Room's confined connection/dispatcher and restores the operation's job inside it, so suspended transaction work
rolls back on cancellation. All three new cases and six existing repository cases passed, alongside the two real
SSE cases and desktop compilation, in the 23-second focused run (`/tmp/knet-completion-atomic-sse.log`).

Version-two selectors/revisions/acknowledgements, cross-provider quota coordination and the complete corrupt-row
and migration matrix remain separate C03 requirements.

## C04: Live global Android policy

An authenticated session owns one sequential policy refresher, bounded read deadlines and a five-second monotonic
lease. Identity/endpoint changes fence in-flight responses; expiry releases stale shaping and successful reads
recover it. The Android gateway replans queued datagrams on live Off/Offline/rate changes without replacing the
stable UDP socket. Cancellation returns reservations; a separate bounded DNS admission pool preserves control
traffic under data-queue pressure. Android service teardown joins this owner before stopping the forwarder.

The application maintainer suite has six passing cases; the Android live-runtime suite has five. Real UDP tests
cover live disable, offline and recovery, and two SOCKS TCP regressions preserve the opposite direction after EOF.
This is still the version-one global wire contract: selector rules, revision acknowledgements and shared
proxy/packet budget coordination are not implemented by this slice.

## C08/C11: Stream lifetime and measured load

The first four-tunnel 100 kbps run exposed truncation just before the final bytes: read-only inactivity expired
during an active download. Transport idle detection now observes progress in either direction, defers owned
bounded shaping/transform work, and starts a fresh origin-response interval when that local work ends. Actual
stalled writes retain their separate transport deadline. EOF follows queued upload bytes, and opaque completion
waits for outstanding write promises; a failing last write cannot become a successful terminal row.

Permanent short regressions cover silent timeout, one-way progress, local/peer holds and resume, delayed duplex
transforms and close, upload FIN ordering under timer drain/live disable, real shaped upload/reverse response and
concurrent exactly-once terminal delivery. The focused combined run passed in 26 seconds.

Subsequent cases passed real SSE incremental comments/events, reconnect cursors, 100 kbps delivery and queued live
bypass. A real TLS/ALPN HTTP/2 origin passed warm plus three concurrent streams through one protected 100 kbps
tunnel, retaining the origin certificate and exactly four requests. This proves negotiated h2 delivery, not merely
the presence of an ALPN offer. IPv6 and absent-SNI fixtures extend the raw/TLS matrix without fabricating names.

An extended rate run found a further fairness defect despite correct aggregate speed: read-complete and writable
notifications could request another raw read while peer writes were still shaped. The relay now requests reads
only after its admitted write batch drains, including cross-peer writability resumes. A permanent backpressure
regression and the measured load gate qualify this change; the five-second first-byte bound is retained.

The dedicated `:engine:simulator:networkProtectionLoadQualification` uses a 512 MiB heap and incremental buffers.
An initial passing run measured 750,000 aggregate payload bytes over 60.177 seconds at **99,705.6 bit/s** across
four concurrent tunnels (±5% acceptance). Latest first byte was 0.656 seconds, sampled queue peak 750,000 bytes,
heap delta 38,797,312 bytes and direct-memory delta 16,777,216 bytes. A separate **524,288,000-byte (500 MiB)**
incremental four-tunnel transfer passed hash/byte checks in 3.240 seconds, with a 14,680,064-byte heap delta and
zero additional warmed direct-memory allocation. Final-tree reruns are required after transport changes.

These measurements do not close UDP/multiplexed upload/downlink rate windows, offline offered-load, RSS/allocation
plateau, disabled baseline, graph-visible/hidden cost or the required 30-minute protocol soak. Metrics are written
to `engine/simulator/build/reports/network-protection-load/`; the machine ledger retains these remaining assertions.

## C12: Executable gates

`networkProtectionQualification` composes both feature gates (including SSE) and checks 26 distinct task outputs
for nonempty JUnit evidence, no failures/errors/skips and required new regression suites. Its JSON summary reports
requirement closure independently of passing module counts. CI and desktop-release workflows invoke the gate and
retain evidence. The iOS product gate additionally compiles the packet provider for device, runs its Simulator
tests and links the Simulator framework. Hosted workflow execution and complete native packet-provider behavior
qualification are still pending; successful compilation does not supply either.

The real 100,000-row storage baseline has a separate mandatory `canonicalTrafficScaleQualification` stage after
both feature suites. A forced run measured 5,596 ms under simultaneous compilation/crypto; its unchanged 5,000 ms
query threshold then passed at 663 ms in the ordered stage (48,123,128 database/sidecar bytes, 100-row page).
The module `check` and aggregate gate require this stage and its nonempty report. It was moved, not skipped or
removed, and the distinct output inventory prevents double-counting it with ordinary tests. Required suites use
JUnit testcase class identity, since Kotlin target display names append labels such as `[jvm]`.

CI now triggers on pull requests and main pushes as well as manual dispatch. A separate sustained-I/O job and the
desktop release gate run the measured upload/download/500 MiB task and retain its metrics. YAML parsing passed;
this is checked-in workflow configuration, not evidence of a hosted successful run or a 30-minute protocol soak.

## Comparative inspector test review

See [reference cases](network_protection_reference_cases.md). Source review adds behavioral acceptance cases;
it does not claim that HTTP Toolkit/mitmproxy suites were executed against KNet, or that all competitors have the
same product scope. KNet retains independent fixtures and explicit differences for multiplexing, privacy and UDP.
