# Per-device capture qualification

Status: SOFTWARE QUALIFICATION PASSED (2026-11-14). Physical desktop UI and Companion VPN acceptance remain separate
from the automated scenarios below. All fixtures use disposable databases, certificates, origins and
authenticated carriers; they do not change the user's capture history, pairing or installed CA.

## Behavior and architecture

Start/Pause Capture controls the selected authenticated device or Other traffic. A toolbar command
captures its source immediately; tab switches change the displayed status without changing recording.
Each process remembers independent choices through reconnects, Clear/writer rotation and proxy port
changes. A new device starts paused. A fresh desktop process begins with capture stopped.

The implementation keeps one proxy listener, Companion control service and canonical writer. Application
use cases expose a typed admission snapshot; the runtime serializes source choices with writer/listener
lifecycle. Capture uses the complete ingress identity, never a display name, host or selected tab. The
legacy low-level global pause/resume interfaces remain available for administrative callers.

Pausing a source ends only its current recording bindings with `capture_paused`. It does not close its
transport sockets, detach the shared writer, reset sibling TLS learning or change Network Conditions /
Protected Traffic rules. Existing pending interceptions remain actionable; the source cannot enter a
new HTTP/message breakpoint pause until recording resumes.

Each binding owns a short-lived recording lease. Pause fences late HTTP/frame callbacks, cancels
outstanding byte reservations, finalizes retained partial bodies and cancels unfinished framed child
records before their parent. This prevents stale In Progress records, post-pause body appends and
temporary body writers surviving until an unrelated device stops.

The Traffic table identifies the partial recording with a pause indicator and keeps any received HTTP
status visible. The inspector reports capture state separately from a received HTTP status. If the pause
preceded response headers, the status is shown as **Not captured**.

Resume records subsequent complete requests or streams. Existing HTTP keep-alive/H2 connections can
record their next exchange/stream. An already cut body, SSE response or WebSocket session stays cut;
create a new request, subscription/stream or WebSocket connection to capture it again. KNet continues
forwarding the old transport and does not invent missing bytes. API Studio capture routing observes
Other traffic's recording state independently of the selected phone.

## Automated scenario map

| Area | Executed test suite / scenario |
|---|---|
| Typed admission | `WorkspaceCaptureTest`: explicit source choices, Other and future devices, shared writer lifecycle, administrative defaults. |
| Toolbar state and commands | `TrafficDeviceCaptureTest`: A/B/Other status, immediate tab projection, rapid conflation retaining each device, delayed acknowledgement, failed command retry. |
| HTTP interception | `BreakpointCoordinatorTest`: stop new source pauses while retaining existing owners, sibling admission and stable pipeline requirements. |
| Message interception | `ProtocolMessageBreakpointRoutingTest`: paused source bypasses new framed pauses; previously pending frames remain resolvable. |
| Capture binding | `SwitchableProxyCaptureSinkTest`: exact authenticated identity, copied local identity cannot impersonate a device, source cut, connection reuse, writer rotation and newcomer admission. |
| Partial HTTP/frame storage | `ScopedStreamingCaptureTest`: WebSocket, GraphQL WS, gRPC and SSE callback families; reserved payload cancellation, late completion fencing, terminal child records, partial HTTP body finalization, source-specific opaque TLS counters. |
| Runtime lifecycle | `CanonicalCaptureCutoverTest`: one writer/listener, idempotent source pause, retained sibling TLS learning, independent Other traffic, rotation and listener restart retaining source choices. |
| Real authenticated sockets | `DeviceCaptureSocketEndToEndTest`: production runtime and Room, same-name identities, HTTP/HTTPS, GraphQL GET/POST, new-device admission, reconnect/rotation, in-flight bodies and valid SSE records, WebSocket / negotiated GraphQL WS control frames, native gRPC/H2C connection reuse, pending request resolution, and live shaping/protected-policy enforcement while recording is paused. |
| Protocol and resource regressions | The full `devicePolicyLibrariesQualification` gate requires complete nonempty, unfiltered outputs and all new capture suites. It rejects failures, errors and skipped test cases. |

## Reproduction

```sh
./gradlew devicePolicyLibrariesQualification \
  :products:desktop:createDistributable :products:companion:androidApp:assembleDebug \
  :ui:core:compileKotlinIosSimulatorArm64 --max-workers=1 --no-daemon
```

This session isolates build output with
`/Users/devuloopers/.codex/verification/knet-smart-scroll-2026-11-14/isolated.init.gradle.kts`, configuration
cache disabled, one worker and in-process Kotlin compilation (1,536 MiB heap, 768 MiB metaspace).
Focused logs: `/tmp/knet-device-capture-focused.log`, `/tmp/knet-device-capture-streaming.log` and
`/tmp/knet-device-capture-e2e.log`. Final gate: `/tmp/knet-device-capture-qualification.log`.

## Final results

| Gate | Result |
|---|---|
| Complete policy/protocol gate | **1,698 tests across 33 outputs; zero failures, errors or skips.** |
| New authenticated capture socket suite | **6 cases passed**: HTTP/HTTPS/GraphQL + rotation/new identity, in-flight HTTP/SSE, WebSocket/GraphQL WS, native gRPC/H2C, pending HTTP actions, and shaping/TLS policy while paused. |
| Architecture and protection-ledger validation | PASS. |
| Desktop distributable and Android debug APK | PASS. |
| Shared UI Apple simulator compilation | PASS. |
| Diff whitespace check | PASS. |

The full gate includes **24 additional cases** over the preceding 1,674-case baseline. Counts by changed
complete task output are application 89, data 178, Traffic UI 162 and desktop product 83. Other protocol,
control, storage and UI tasks are included in the 33-output total. These are executed scenario counts,
not a measured line/branch coverage percentage.

Final evidence is retained at
`/Users/devuloopers/.codex/verification/knet-device-capture-2026-11-14/`, including the clean gate log,
summary, per-task counts and capture-specific XML reports. The rebuilt desktop package is
`/Users/devuloopers/.codex/verification/knet-smart-scroll-2026-11-14/build/products/desktop/compose/binaries/main/app/KNet.app`.
No desktop application was launched during qualification. Physical device/visible UI acceptance was not
rerun for this increment.

## Physical acceptance boundary

Automated socket tests establish authenticated ingress, delivery, recording and durable source isolation.
They do not establish the visible native desktop control interaction or real Android/iOS VPN packet
behavior. The source admission change does not introduce UDP/QUIC decoding, a packet provider, a new
Companion wire protocol or a hardware identity scheme. These are finite scenario checks, not a claim
of exhaustive line/branch coverage or every possible device/timing combination.
