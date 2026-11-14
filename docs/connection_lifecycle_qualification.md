# Connection EOF cleanup and upgraded-flow completion

Status: **COMPLETED — both remaining blockers repaired; automated and connected-phone checks passed**.

## Defects and repair ownership

The connected-device baseline exposed two remaining blockers after the H2C/gRPC repair:

- **F1:** half-closure was enabled on sockets, but HTTP keep-alive and HTTP/2 parent handlers did not
  retire input-ended connections. The authenticated carrier correctly kept the response direction open,
  so its reverse-copy workers could wait until proxy inactivity timeout and consume shared loopback admission.
- **F2:** the upgraded-byte relay did not finish on input EOF. Successful WebSocket close handshakes
  could leave an active transport until an idle exception persisted `duplex_io_failed`.

The repair belongs to `:engine:proxy`; the authenticated carrier retains its half-close semantics. No
Companion reinstall, protocol-specific service exceptions, persisted-data edits or certificate changes are needed.

## Lifecycle contracts

HTTP/1 records input EOF and stops connection reuse. A complete decoded request retains its pending response,
including asynchronous final body interception; incomplete requests cancel. Accepted pipelined requests drain in
order. Upstream EOF without a complete response fails immediately, while a fully decoded response can finish
its pending transformation and final client write after the upstream socket closes.

HTTP/2 installs a connection EOF owner only after protocol selection (prior knowledge, Upgrade, CONNECT or
TLS ALPN). It observes request END_STREAM before multiplexing, cancels incomplete children on parent input EOF,
and retains complete requests until their children and final write promises drain. Parent close releases admission.
Sibling streams and ordinary gRPC request half-close keep their existing behavior.

Upgraded relays account for both input directions, accepted writes, asynchronous transformations and output
shutdown. Duplicate TCP/TLS EOF notifications cannot impersonate opposite EOF. FIN or TLS close_notify follows
queued payload. Terminal selection fences write/transform admission across both event loops; failed delivery
wins over graceful EOF until all admitted work settles. Canonical capture publishes once before closing both
sockets. Opaque protected tunnels also release both sockets after their existing EOF/write-drain terminal decision.

## Regression coverage

- `DuplexEofLifecycleTest`: both EOFs, duplicate SSL/TCP EOF, reverse-direction payload after half-close,
  delayed final write, failed final write, delayed final transform and transform failure at EOF.
- `DuplexTransformLifecycleTest`: paused-interception idle holds and cancellation/late-completion cleanup.
- `UpstreamEofLifecycleTest`: headerless EOF produces 502 immediately; a complete intercepted response survives
  origin close and delivers its final transformed body.
- `RawTunnelTerminalLifecycleTest`: shared write drain, concurrent completion, late write failure and socket release.
- `CompanionConnectionEofEndToEndTest`: real authenticated TLS gateway and proxy with HTTP/HTTPS keep-alive,
  delayed half-closed responses, plain/secure WebSocket close, and 48 sequential/concurrent lifetimes under a four-connection admission budget.
- `CompanionH2cEndToEndTest`: completed-parent EOF release, three delayed multiplexed responses after carrier
  half-close, and cancellation of a request without END_STREAM, alongside all-cardinality native gRPC regressions.

Before repair, the duplex both-input-EOF regression and all six initial real-carrier cleanup checks failed.
The failing XML/logs are retained with subsequent results. The delayed-response fixtures were tightened during
implementation: they do not demand an extra read while request/response backpressure deliberately pauses it;
they require full response delivery and prompt final EOF after client output shutdown instead.

## Verification evidence

Evidence directory: `/Users/devuloopers/.codex/verification/knet-lifecycle-fix-2026-11-14/`.

The user started capture in the repaired desktop. Its 167 packaged proxy engine classes matched the tested
output. The existing Companion process (PID 19130) reconnected without reinstalling, re-pairing or CA changes.
Conditions were off and the saved rule count was zero.

| Connected Samsung case | Result |
|---|---|
| Persistent HTTP churn | All 150 requests passed; all 150 exchanges completed and their connections closed; maximum recorded response-completion-to-connection-close gap 35 ms |
| Raw WebSocket close | Ten UTF-8/binary/ping-pong/close cycles passed and captured COMPLETED |
| GraphQL WebSocket close | Ten subscriptions delivered sequences 1/2/3, operation completion and close 1000; captured COMPLETED |
| Old timeout-window recheck | All 20 upgraded rows remained COMPLETED with no error at least 85,510 ms after their final completion; maximum connection duration 456 ms |
| Supported-protocol matrix | All 25 assertions passed: HTTP/1.0, chunked HTTP/1.1, GraphQL GET/POST, SSE variants/cancellation, WebSocket, HTTPS/H2 and H2C/all gRPC cardinalities/status |
| Multiplexing/cancellation | 24 concurrent H2C requests matched their exact values; live bidi gRPC cancellation and follow-up traffic passed |
| Resource/capture convergence | All 223 generated LAN exchanges had CLOSED connections: 221 COMPLETED and two deliberately CANCELLED; zero authenticated-gateway CLOSE_WAIT sockets |
| HTTPS validation | Public trusted-origin control captured HTTP/2 on both legs and COMPLETED; TLS verification remained enabled |

These are 199 client case assertions, including grouped concurrent requests, rather than 199 distinct protocol
features. Canonical metadata was inspected read-only. `phone-*.json`, `http-churn-verdict.json`,
`ws-after-timeout.json`, socket snapshots and `desktop-engine-build.json` retain the evidence. The initial
health control passed before churn and both protocol/cancellation cohorts ended with successful health controls.

The full qualification executed **1,546 tests across 30 tasks, zero failures/errors/skips**. It includes
HTTP/2, gRPC, WebSocket, GraphQL WS, SSE, Network Conditions and Protected Traffic gates, plus GraphQL protocol
and formatter tests. The proxy suite passed 178 cases; desktop composition passed 65. Nineteen regression
methods were added for this repair. All six architecture checks, the Network Protection ledger check, and
desktop distribution creation passed. `gate-summary.json`, archived XML in `gate-results/`, and
`qualification.log` record the actual executions; test cache/up-to-date reuse was disabled for the final run.

Cleanup removed the disposable Android client and temporary LAN servers. The fixed desktop remains open with
capture running, one Companion control connection, the same Companion process, no fatal crash entries, conditions
off and zero condition rules. User pairing, certificates and captured traffic were retained.

Physical UI gestures, a second
concurrent device, additional operating systems/OEMs and release-duration soak remain separate qualification.
EOF regression coverage does not establish coverage of every possible network input or peer implementation.
