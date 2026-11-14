# Supported-protocol qualification — 2026-11-14

Baseline status: **COMPLETED WITH FINDINGS — RELEASE QUALIFICATION FAILED**.

Follow-up: **all three reported blockers were subsequently repaired and rechecked on the connected Samsung**.
See [H2C/gRPC repair](companion_h2c_forwarding.md) and
[connection cleanup and WebSocket close repair](connection_lifecycle_qualification.md). This document preserves
the baseline qualification results. It distinguishes automated product suites, physical Companion traffic and
isolated headless proxy fixtures; the excluded release/device/platform matrix still needs separate qualification.

## Scope and environment

- Current runtime/README protocol matrix: HTTP/1.0, HTTP/1.1, HTTPS/CONNECT, HTTP/2, GraphQL over HTTP,
  SSE, HTTP/1.1 WebSocket, modern GraphQL WebSocket and native gRPC.
- Existing macOS desktop, connected Samsung SM-A505F / Android 12, unchanged production sources.
- Disposable independent-UID Kotlin Android client; generated requests and temporary LAN origins.
- Desktop was not launched by the agent. Desktop UI interaction remains excluded at the user's request.
- SQLite inspection is read-only. Pairing, user certificates, security settings and retained captures are preserved.
- Evidence: `/Users/devuloopers/.codex/verification/knet-protocols-2026-11-14/`.

## Automated qualification

All five gates passed: `http2Qualification`, `grpcQualification`, `webSocketQualification`,
`graphQLWebSocketQualification` and `sseQualification`. Explicit `:engine:protocol:test` and
`:engine:formatter:test` runs also passed. Actual test execution was forced without forcing all production
compilation. The 21 executed test tasks produced **1,045 cases; zero failures, errors or skips**.

| Area | Executed cases | Representative coverage |
|---|---:|---|
| Proxy engine | 164 | HTTP/1.0/1.1 framing/trailers, CONNECT/SNI/ALPN, H2 multiplexing, reset/GOAWAY, flow control, runtime bounds, TLS evidence, protected tunnelling |
| GraphQL HTTP and persisted queries | 60 | GET/POST source isolation, encoded/duplicate parameters, operation safety, batches, exact UTF-8 hashes, APQ miss/fallback/lookup, bounded catalog and imports |
| gRPC engine | 22 | Envelope framing, compression flags, metadata/trailers/status, interactive incremental sends, half-close, deadlines and cancellation |
| WebSocket engine | 27 | UTF-8/binary, fragmentation/control interleaving, masks, permessage-deflate capture, limits, message interception and shaping |
| GraphQL WebSocket engine | 21 | Modern envelopes, concurrent operation correlation, ID reuse, completion/cancellation, invalid or foreign history |
| SSE engine | 28 | Incremental events, malformed UTF-8 recovery, gzip/deflate/raw-deflate byte boundaries, unsupported/corrupt encoding, limits and transform/re-encode |
| Supporting product/application/data/UI/HTTP/formatter tasks | 723 | Canonical capture/storage, protocol authoring/import, inspection navigation, rules, presentation, formatting and composition |

Counts are disjoint by task; shared dependencies were executed once. Architecture verification also passed.
Passing coverage does not include the missing physical lifecycle regression below. Archived XML and per-task
counts are in `gate-results/` and `gate-summary.json`.

The separate 120-second SSE codec churn check also passed: **2,949,837 byte-exact streams** across gzip,
deflate and stacked gzip/deflate, with zero test failures. This is a bounded diagnostic run; the default
three-hour release soak remains outstanding. Its XML is archived under `additional-results/boundedSse/`.
The headless proxy fixture lifecycle test passed separately and is not counted as 1,045 functional cases.

## Running desktop and Companion

The 25-case physical cohort passed 19 cases and failed six. HTTP controls passed before/after the failed
cleartext HTTP/2 cases. The same six cases passed directly from the phone with VPN stopped. An attempt to
bind test sockets to the underlying Wi-Fi network while VPN was active was rejected by Android with EPERM;
that harness restriction is excluded from protocol pass/fail counts.

| Protocol / case | Physical result |
|---|---|
| HTTP/1.0 | Passed; 200 response with HTTP/1.0 status line |
| HTTP/1.1 | Passed; 30,000-byte chunked upload and following health request |
| GraphQL HTTP | Passed GET URL query/variables and POST JSON forwarding |
| HTTPS / TLS HTTP/2 | Passed; independent OkHttp client negotiated h2 and canonical capture recorded HTTP/2 on both legs |
| SSE | Passed finite stream, multiline data, comments, fragmented chunks, malformed UTF-8 recovery bytes, mid-record EOF, Last-Event-ID resume, gzip, deflate and 250-event burst |
| Live SSE cancellation | Passed; three events received then client cancellation, canonical exchange CANCELLED |
| Raw WebSocket | UTF-8, binary, ping/pong and close round trips passed; terminal capture FAILED separately |
| GraphQL WebSocket | Subscription delivered 1/2/3, complete and close 1000; terminal capture FAILED separately |
| H2C | Failed through Companion: `Required SETTINGS preface not received`; direct phone control passed |
| Native cleartext gRPC | All four cardinalities and typed failure/metadata failed through Companion with the same preface error; all five direct phone controls passed |

SSE assertions distinguish wire delivery from semantic event validity: forwarding malformed bytes or an incomplete
record is expected. Capture retained one `sse_record_ended_without_blank_line` failed record for the disconnect
fixture; successful HTTP completion does not assert that every contained SSE record is valid.

Evidence: `phone-vpn-matrix.json`, `phone-h2c-direct-vpn-off.json`, `live-capture-metadata.json` and
`live-message-metadata.json`. The canonical burst contains all 250 SSE records; the compressed fixtures each
produced one complete semantic record. The malformed fixture's final semantic annotation reports one valid
event after the gap.

## Isolated TLS protocol controls

All **16 final cases passed through the isolated production proxy**, and all 16 direct origin controls
passed from the same phone.

| TLS cohort | Result |
|---|---|
| HTTP/2 ALPN echo and trailers | Passed; negotiated h2 and retained trailing header |
| Multiplexing | Four concurrent streams passed with one origin connection ID |
| Reset isolation | Proxy returned 502 for the reset stream; a following stream succeeded |
| GOAWAY | Active response completed and the following request recovered |
| SSE over H2 | Identity, gzip and deflate event delivery passed |
| GraphQL over WSS | Subscription 1/2/3, complete and normal close 1000 passed |
| Raw WSS | UTF-8 and 32 KiB binary echo with normal close 1000 passed |
| Origin WebSocket frame bound | Oversized 128 KiB frame produced expected server close 1009 through proxy and directly |
| Native gRPC over TLS/H2 | Unary, server stream, client stream and bidirectional payloads/counts passed; status 0 retained |
| gRPC typed failure | INVALID_ARGUMENT status 3 and custom failure metadata retained, including trailers-only handling |

Two initial harness expectations were corrected before final counting. The reset check originally required a
client exception, while the existing proxy contract also allows 502. The initial WSS listener did not acknowledge
server-initiated closure after the oversized echo. The independent direct control timed out identically. The final
listener handles `onClosing`; normal-size echo and explicit oversize/1009 checks pass on both paths. Initial logs
are retained as diagnostics rather than counted as product failures or hidden successes.

This fixture uses a disposable CA, generated origin certificates and the production KNetProxyServer. Its explicit-proxy socket bypasses the Companion data path
only while VPN is stopped. The fixture's upstream verification is disabled solely for the self-signed LAN
origins; the running desktop's TLS verification and installed CA are unchanged. These checks qualify engine
transport behavior, not the running desktop's DI, capture persistence or Companion TLS gRPC path.
The direct control trusts only the generated fixture certificate bundle and checks its fixed LAN host; a
fixture-only hostname override accommodates the protocol lab's CN-only localhost certificate. The proxied
client retains normal hostname verification against its disposable KNet CA. No OS certificate was installed.

Evidence: `phone-tls-proxy-final.json`, `phone-tls-direct-final.json`, `fixture/PhysicalTlsProtocolFixtureTest.kt`,
`fixture.init.gradle`, `tls_bridge.py` and `Probe.kt` in the evidence directory.

## Recovery and cleanup

Companion VPN was restored and a fresh HTTP health request passed through the running desktop. The same
Companion process remained alive with one authenticated control WSS connection and no fatal exception in
its crash buffer. Network Conditions remains off with zero condition rules. The disposable client was
uninstalled, generated origin private key removed and all seven fixture listener ports closed. Existing
pairing, certificates and captured data remain intact. Evidence: `phone-final-recovery.json` and `final-state.json`.

## Baseline blockers and remaining scope

All three baseline blockers below are resolved by the linked follow-ups; their original evidence is preserved.

1. [RESOLVED] Retained closed-peer gateway/proxy sockets and shared loopback admission pressure.
2. [RESOLVED] Successful WebSocket and GraphQL WebSocket close exchanges persisted as FAILED / `duplex_io_failed`.
3. [RESOLVED] Companion H2C and cleartext native gRPC routing failed although independent origin controls passed.

See [connected-device findings](physical_android_qualification_2026-11-14.md) for the original detailed diagnosis
and the failing isolated both-input-EOF regression. The initial testing-only run included no production repair; the linked follow-ups include repairs and repeat qualification.

HTTP/3, QUIC/WebTransport inspection, WebSocket over HTTP/2 and legacy GraphQL WS are outside the implemented
protocol matrix. Android UDP packet handling/opaque metadata does not imply UDP application-protocol decoding.
Physical iOS, Windows/Linux desktop, a second concurrent device, manual UI interaction, and full release-duration
soak remain unverified. This is scenario qualification, not a claim of 100% code or possible-input coverage.
