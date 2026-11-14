# Companion cleartext HTTP/2 and gRPC forwarding

Status: **COMPLETED — automated and connected Samsung end-to-end checks passed**.

## Problem and ownership

The Android VPN gateway sends ordinary HTTP/1 requests as authenticated forward-proxy requests. Other TCP
streams use authenticated CONNECT, preserving the original destination and application bytes. Cleartext
HTTP/2 begins with `PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n`, which takes this CONNECT route. The desktop
previously assumed TLS after an inspected CONNECT and its upstream HTTP/2 pool always used TLS. Consequently,
H2C and plaintext native gRPC failed although the same phone/origin worked directly.

The repair belongs to `:engine:proxy`. The existing Companion and authenticated carrier remain compatible;
no pairing, certificate, control-WebSocket or database migration is required.

## Transport behavior

- After an inspected CONNECT succeeds, a bounded decoder selects the complete 24-byte HTTP/2 preface or the
  existing TLS ClientHello route. It preserves every byte, including coalesced SETTINGS/DATA, exactly once.
- Matching partial prefaces have an absolute setup deadline. Empty/truncated input shutdown closes the
  connection instead of retaining an admission. TLS classification is created only after TLS selection, so an
  unused SNI timer cannot later close a plaintext HTTP/2 parent.
- The H2C parent keeps the original CONNECT destination, admitted capture connection and verified device
  attribution. Child streams inherit the cleartext H2 origin requirement and use the existing frame codec,
  multiplexer, capture, inspection and flow-control boundaries.
- The upstream pool supports prior-knowledge cleartext H2 in addition to TLS/ALPN. Pool identity includes
  transport security as well as authority, resolved destination set and port. A TLS request cannot reuse a
  plaintext connection. Cleartext requests retain `http` scheme and zero TLS timing.
- A cleartext H2 origin failure does not trigger HTTP/1 replay. Existing ordinary forward-proxy HTTP/1 behavior,
  TLS ALPN fallback, and explicit CONNECT Tunnel/Block decisions retain their current contracts.
- Downstream cancellation is published before closing the corresponding origin stream, preventing peer
  teardown from winning canonical capture as an unrelated upstream failure. Sibling streams retain their parent.

The proxy contains no gRPC service schema or application-specific routing. Its protocol module continues to
observe native framing, message directions, status and trailers through the normal streaming extension.

## Committed regression cases

`ConnectHttpTwoPrefaceHandlerTest` covers every split of the preface, one-byte delivery, following/coalesced
bytes, TLS and mismatched-prefix replay, absolute trickle deadlines and EOF at every incomplete prefix.

`CompanionH2cEndToEndTest` runs real sockets through the production authenticated TLS gateway and proxy into
an independent HTTP/2 native-gRPC peer. It verifies unary/server/client/bidirectional calls, live responses
before client half-close, request/response trailers, trailers-only errors, canonical message counts, HTTP/2
protocols on both legs, zero TLS timing, retained destination/device identity, credential stripping and rejection,
concurrent streams, cancellation/sibling recovery, setup-timer retirement and plaintext/TLS pool separation.
Its capture recorder follows the production adapter's first-terminal-wins contract.

The existing `http2Qualification` and `grpcQualification` gates include both test classes through the proxy and
product suites. Full protocol regression also includes WebSocket, GraphQL WS, SSE, GraphQL/APQ and formatting.

The full run executed **1,054 tests across 21 tasks, zero failures/errors/skips**. The proxy suite contains
169 cases and desktop composition 55, including nine new regression methods across these two classes.
All six architecture checks and `:products:desktop:createDistributable` passed. XML and counts are archived
under `gate-results/` and `gate-summary.json` in the evidence directory.

## Verification evidence

Artifacts and generated-device client:
`/Users/devuloopers/.codex/verification/knet-h2c-fix-2026-11-14/`.

The user authorized opening the repaired distributable. Its 164 engine class files matched the tested build;
the Companion reconnected to the new desktop process without reinstalling or re-pairing. The user started capture.

| Physical case | Result |
|---|---|
| Previously failing H2C/native gRPC cohort | 6/6 passed: H2C, unary, server stream, client stream, bidirectional stream and typed error metadata |
| Multiplexing | All 24 concurrent requests returned their exact request-specific values |
| Live cancellation | Duplex gRPC replied before client half-close; client cancelled after the first message and follow-up traffic passed |
| Protocol compatibility | All 25 transport assertions passed, including HTTP/1.0/1.1, GraphQL GET/POST, SSE variants, WebSocket message delivery and HTTPS with h2 |
| Room capture | 39 cleartext H2 rows retained HTTP/2 on both legs, `http` scheme and 0 ms TLS; all carried one verified paired-device identity |
| gRPC capture | 36 message records; status 3/custom metadata retained for typed errors; the cancelled RPC is CANCELLED / `downstream_cancelled` |

Evidence: `phone-h2c-grpc-after.json`, `phone-h2c-stress.json`, `phone-protocol-matrix-after.json`,
`physical-capture-metadata.json`, `physical-grpc-message-metadata.json` and `desktop-engine-build.json`.
The broader cohort has 25 passing **transport assertions**; its two WebSocket parent captures still become
FAILED under the existing separate terminal-reporting defect. Successful delivery does not hide that issue.

The original failing physical cohort is retained in
[protocol qualification](protocol_qualification_2026-11-14.md). Retained TCP connections and WebSocket terminal
reporting remain separate open findings. Physical iOS, cleartext under every OEM, and release-duration soak
are not established by this Samsung run.

The disposable test APK was uninstalled and every fixture listener closed. The repaired desktop remains open,
Companion retains its original process with one authenticated WSS control connection and no fatal exception,
and Network Conditions remains off with zero condition rules. Existing pairing, certificates and captures are
retained. Cleanup evidence is in `fixture-cleanup.json` and `final-state.json`.
