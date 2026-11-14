# Connected Android qualification — 2026-11-14

Baseline status: **COMPLETE FOR THE AVAILABLE SETUP — FAILED RELEASE QUALIFICATION**.

Follow-up: **all three blockers recorded by this baseline were subsequently repaired and rechecked on the
connected Samsung**. See [H2C/gRPC repair](companion_h2c_forwarding.md) and
[connection cleanup and WebSocket close repair](connection_lifecycle_qualification.md). The remaining sections
preserve the original failing evidence and historical qualification limits; they do not describe the repaired
build as still carrying those defects. Longer soak and the excluded platform/device/UI matrix remain separate.

## Environment and safeguards

- Samsung SM-A505F, Android 12, using its existing pairing and installed KNet certificate.
- Existing desktop was inspected without agent launch. The user manually restarted it once during diagnosis.
- Desktop UI automation was unavailable; the user chose to continue without it and operated capture/profile/rule controls.
- A disposable independent-UID Kotlin client sent generated data to temporary LAN fixtures and one public HTTPS health target.
  It did not read Companion credentials or application data. Metadata checks used SQLite read-only connections.
- The actual project protocol lab supplied HTTP/2, native gRPC and GraphQL WebSocket controls.
- Evidence directory: `/Users/devuloopers/.codex/verification/knet-physical-2026-11-14/`.

## Automated evidence

| Gate | Result |
|---|---|
| Multi-device desktop/Android qualification | 882 cases; zero failures/errors/skips |
| Apple qualification | 10 cases; zero failures/errors/skips |
| Previously excluded real-LAN Wi-Fi fixtures | All eight `WifiSharingBackendTest` cases passed on the active interface |
| Architecture/build gates | Six architecture checks, desktop distribution, Android APK and Apple framework/device-target gates passed |
| New isolated duplex EOF reproducer | FAILED as expected: both peers finish input but the relay remains active |
| Ordinary proxy suite after the isolated run | Passed again without the external regression source; the failing reproducer and XML were archived separately |

At baseline, the reproducer lived outside production test sources and was included only with `regression.init.gradle`.
It demonstrates a missing case in the existing passing suite. No production code was changed during that initial testing-only task; later repairs are documented in the follow-ups.

## Verified physical behavior

| Case | Observed result |
|---|---|
| Core protocol cohort | All eight visible-client cases passed: HTTP, 1 MiB download/upload, GraphQL GET/POST, SSE, raw WebSocket and HTTPS |
| Transfer integrity | Upload byte count/SHA-256 matched; download lengths matched and recorded hashes were repeatable |
| HTTPS | The installed KNet root was accepted; capture recorded HTTP/1.1 downstream and HTTP/2 upstream |
| SSE | All eight generated events arrived in order |
| Raw WebSocket data | UTF-8 text, binary, ping/pong and close payload round trips passed; terminal reporting failed separately below |
| GraphQL WebSocket data | Real lab subscription delivered sequences 1/2/3, operation completion and close code 1000 |
| Source attribution | Generated captured exchanges belonged to one verified LAN_PAIRED_DEVICE identity |
| Repeated VPN lifecycle | Five additional stop/start cycles retained the process and Verified certificate; control sockets changed exactly 1 → 0 → 1 |
| Wi-Fi interruption | Ten-second outage automatically reconnected a fresh control socket without restarting Companion |
| Recovery traffic | Thirty fresh HTTP requests passed after Wi-Fi recovery |
| Desktop restart | The user restarted desktop; Companion reconnected automatically to the new process without re-pairing |
| Companion process recreation | Intentional force-stop/relaunch preserved certificate/pairing, resumed VPN and retained the captured device identity |
| Background observation | Twenty-one samples over five minutes retained one Companion PID and one control connection; RSS 159,464–173,352 KiB, threads 56–66 |
| Screen off | More than 70 seconds retained the same process and same control socket; the screen was awakened without bypassing keyguard |
| Crash check | No fatal exception in the recreated Companion process |

The five-minute observation is a bounded diagnostic run, not release soak or proof of stable memory across Android/OEM versions.

## Measured Network Conditions

The user operated the desktop controls; persisted settings and applied rule metadata were checked before judging results.
Rates below use complete client request durations and generated payload bytes, including setup/latency effects.

| Configuration | Transfer | Measured duration | Interpretation |
|---|---|---:|---|
| Conditions off | 1 MiB download/upload | 154 / 240 ms in the successful core control | Local baseline |
| Global Streaming 100 kbps | 256 KiB download | 21,293 ms | Approximately 98.5 kbps; expected cap applied |
| Global Streaming 100 kbps | 256 KiB upload | 203 ms | Upload is intentionally unlimited in this preset |
| Global Slow 3G | 256 KiB download/upload | 8,159 / 6,224 ms | Both below the configured 400 kbps ceiling; latency and concurrent global traffic also affect throughput |
| Global Offline | HTTP health | Rejected in 262 ms | Application traffic failed while control remained connected |
| Host/port rule: 192.168.0.15:19091, Streaming 100 kbps; global No throttling | 256 KiB matched download/upload | 21,150 / 213 ms | Download cap applied; upload intentionally unlimited |
| Same host, unmatched port 19090 | 256 KiB download | 1,096 ms | Unmatched port remained unthrottled |

Matched captures recorded `DOMAIN_RULE`, the temporary rule ID and `streaming-100-kbps`. The unmatched port did not
receive that rule. This physically verifies the configured exact host/port case, not every wildcard/protocol criterion.

## Baseline findings — resolved by the linked repair qualifications

### F1 — Retained closed-peer data connections and eventual request rejection [RESOLVED]

Desktop started with 80 authenticated-gateway sockets in CLOSE_WAIT. All 80 original TCP tuples remained in every
saved pre-restart sample while additional sockets accumulated. A thread dump showed 80 reverse-copy workers waiting
on the internal proxy. New application requests intermittently ended before origin/capture admission while the
separate control WebSocket continued working. Restarting desktop removed the retained sockets and restored ordinary
HTTP controls.

All authenticated bridges originate from loopback. `KNetProxyServer` currently keys downstream admission by remote
address, with a default limit of 128 per client address. Retained loopback connections can therefore consume a shared
data admission budget. The precise cleanup path and counter exhaustion require the corrective end-to-end regression;
no private runtime counter was read to claim an exact admission total.

A controlled HTTP/1.1 keep-alive response was fully received and its phone socket closed. Its exchange completed,
but the canonical connection remained OPEN and closed only approximately 60 seconds later. A Connection: close control
terminated immediately. TLS close notification, TCP input shutdown, idle persistent HTTP and gateway cleanup need to
be qualified together, preserving legitimate half-closed requests awaiting their final response.

Evidence: `desktop-sockets-before.log`, `desktop-sockets-sustained.log`, `desktop-sockets-after-core.log`,
`desktop-sockets-after-repeat.log`, `socket-retention-intersection.json`, `desktop-threads-before.log`,
`persistent-http-close-evidence.json`, `persistent-http-close-evidence-later.json`.

### F2 — Normal WebSocket closures later reported as failures [RESOLVED]

Raw WebSocket and the independent protocol lab's GraphQL subscription both completed their application-level close
exchange successfully. Their captures later became FAILED with `duplex_io_failed`. This occurred on the original
and clean desktop runs; successful message delivery alone is insufficient to qualify connection termination.

The isolated `PhysicalDuplexEofRegressionTest` reproduces the missing terminal transition when both relay inputs
finish. `KNetDuplexRelayHandler` handles channel inactivity/exceptions but lacks the corresponding input-shutdown
transition while half-closure is enabled. Fixes must drain writes, release peers/leases and publish one accurate
terminal outcome without truncating in-flight data.

Evidence: `probe-core-visible.log`, `probe-graphqlws.log`, `probe-graphqlws-clean.log`,
`clean-close-state-evidence.json`, `duplex-eof-regression.log`, `duplex-eof-regression-result.xml`,
`regressions/PhysicalDuplexEofRegressionTest.kt`.

### F3 — Clear-text HTTP/2/native gRPC does not traverse Companion correctly [RESOLVED]

A clean desktop run had successful HTTP controls immediately before and after this cohort. H2C and every native
gRPC scenario failed through VPN with `Required SETTINGS preface not received`. The same phone/client/lab with VPN
off passed all six controls: H2C, unary, server streaming, client streaming, bidirectional streaming and typed failure
with metadata. This confirms a VPN data-path defect for this clear-text transport, independently of the earlier
saturated-desktop/preflight failures.

The Android SOCKS initial-payload classification and desktop CONNECT/classification path need an end-to-end fix and
regression. This result does not establish a failure of TLS/ALPN native gRPC, which was not physically exercised.
HTTP/1.1-to-upstream-HTTP/2 forwarding passed in the HTTPS control and is a distinct path.

Evidence: `probe-advanced-clean.log`, `probe-clean-health-before.log`, `probe-clean-health-after-advanced.log`,
`probe-advanced-direct-final.log`.

## Harness controls and excluded results

- Early background/preflight launches and a request cohort overlapping the intentional Wi-Fi outage failed. Their
  raw logs remain available; they are not counted as successful qualification or automatically attributed to a codec.
- Initial protocol-specific attribution was deferred once retained data sockets were identified. The clean desktop
  cohort with adjacent HTTP controls established F3 separately.
- The first gRPC failure control assumed its custom metadata must appear in trailing headers. Valid trailers-only
  responses can place it in the initial headers. The client was corrected and the direct final cohort passed all six cases.
- An initial host-rule attempt failed before origin/capture admission on the saturated desktop. The clean repeat
  measured the actual rule and unmatched-port behavior successfully.
- Instrumentation runner exit status was not treated as case success; individual JSON results and expected Offline
  rejection were checked explicitly. `physical-summary.json` indexes the selected case evidence.

## Original follow-up list and untested boundaries

Items 1–3 below are completed in the linked repair qualifications. The broader soak and platform matrix in
items 4–5 remains outside this single-device run.

1. Repair data/relay EOF and TLS close cleanup, then add real gateway-plus-proxy persistence/admission regressions.
   Repeatedly close more connections than the configured limits and verify admission returns to baseline.
2. Repair upgraded-flow termination and assert correct captured outcomes for clean close, reset, one-sided EOF,
   opposite EOFs, delayed writes and control-session shutdown.
3. Repair the clear-text HTTP/2/gRPC Companion path. Add physical-path-equivalent HTTP/2 preface and all-cardinality
   gRPC tests; qualify TLS/ALPN gRPC separately.
4. Repeat the physical cohorts and a substantially longer soak after those repairs before production promotion.
5. Two physical devices concurrently, signed iOS packet routing, other Android/OEM versions, actual Wi-Fi roaming,
   desktop UI/tab/graph/native-window gestures, UDP/WebRTC/QUIC scenarios, destructive reinstall/revocation and every
   wildcard/protocol-specific rule remain outside this single-device/no-desktop-UI run.

## Cleanup

The user removed the temporary host rule and disabled Network Conditions; read-only checks confirmed zero rules
and the original `(disabled, no global profile)` settings. The disposable Android client was uninstalled and all
four temporary fixture listener ports (19090/19091/19093/19094) were verified closed. The temporary phone hierarchy
file was removed. Companion VPN was left running with one established control connection; pairing/certificate
data remained intact. Existing keyguard security was preserved. No control HTTP rows for ports 8182/8183 were recorded.

Generated capture rows are retained for review; the agent did not clear device history or edit the live database.
`final-state.json` records the verified cleanup and current services. No commit or push was performed for this test report.
