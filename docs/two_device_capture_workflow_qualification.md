# Two-device capture workflows and resource qualification

Status: implemented and automated/targeted physical qualification passed (2026-11-15). **1,903 tests
across 36 complete outputs**, zero failures/errors/skips; **14 Apple native cases** also passed. Android
APK, macOS distributable, shared iOS UI compilation and architecture checks passed. Fresh long-duration
VPN soak and native UI acceptance remain explicit follow-up items below.

## Delivered workflows

- Exact typed Traffic selection and selected export: Cmd/Ctrl, inclusive Shift ranges, additive ranges,
  loaded-row select-all, clear, source/filter/page/arrival handling and omitted-item disclosure. Existing
  native/HAR export, privacy controls and original execution ownership are reused.
- Companion recording readiness: authenticated per-device HTTPS preflight plus negotiated WSS updates.
  Stopped/starting capture gets a Start capture in KNet dialog; paused recording explains forwarding.
  Late/foreign desktop observations, stale revisions, reconnect, cancellation and legacy endpoints are
  handled. Apple foreground observation uses pinned HTTPS while its extension owns WSS separately.
- Historical file-picker import: HAR 1.2 from HTTP Toolkit/Charles/other HAR exporters, published Charles
  XML interchange and KNet Capture schema 1. Bounded streaming parsers, private spools, indexed paging,
  byte/hash validation and explicit payload availability. No live device, policy or draft mutation.
  Native undocumented tool binaries need HAR/XML conversion. Drag-and-drop actions remain a TODO.

See [export](traffic_export.md), [import](capture_import.md) and
[readiness](companion_capture_readiness.md) for architecture, interaction and format details.

## Live run and defect found

Two unique Samsung phones (Galaxy A03 and Galaxy A50) retained their existing Companion installation,
pairing, VPN and control sessions. A disposable instrumentation UID generated controlled traffic through
that real VPN to local origins. Keep-awake and memory/presence checks ran approximately every two minutes;
desktop CPU/RSS sampling ran every fifteen seconds. The observation period was about ninety minutes.

Initial and fixture-ready retests passed on both phones: HTTP/HTTPS, one-MiB upload/download byte checks,
GraphQL GET/POST, SSE, WebSocket text/UTF-8/binary/ping/pong/close, H2C, gRPC unary/server-stream/client-stream/
bidirectional/error and GraphQL WebSocket. Early advanced-protocol attempts preceded startup of the
separate protocol lab; those fixture-unavailable failures were retained and rerun after startup.
Read-only canonical queries confirmed two distinct source identities and retained message directions.

The periodic simultaneous 256-KiB upload/download soak passed **80 payload cases**, then failed **20**
with connection resets, starting around minute forty. It is **not** a passing long-run stability result.
At failure, desktop retained 256 CLOSE_WAIT plus 256 FIN_WAIT_2 sockets; 128 authenticated gateway copy
workers were waiting in the reverse direction. Two control WSS sessions stayed connected, credentials
were valid, conditions were disabled and the origin remained healthy. Repeated one-sided raw tunnel
EOFs had retained reverse sockets and exhausted the 128-carrier admission limit.

## Cleanup repair and focused verification

Raw tunnel lifecycle now owns an idle half-close drain, independently from the optional general raw
inactivity limit:

- Fully open quiet tunnels still have no default inactivity cutoff.
- After exactly one input EOF, a 60-second reverse-direction **idle** grace begins once accepted writes
  have drained. Successful reverse writes restart it; queued/shaped writes suspend it. Existing write
  deadlines retain responsibility for a genuinely stalled socket write.
- Both EOFs complete after accepted writes drain. Duplicate EOFs cannot replace opposite-direction EOF.
- Expiry cancels and closes both sockets with `tunnel_half_close_idle_timeout`; it does not invent HTTP
  success or classify this as an HTTP request timeout. First peer EOF evidence remains the close initiator.
- Terminal paths cancel the scheduled task; late data is rejected and failed final writes retain failure.

This is a deliberate finite idle grace for an already half-closed opaque stream. A peer that half-closes
its request then produces no reverse progress for over that configured grace is retired. Ordinary fully
open long polling/push and streaming progress are unaffected. The policy value is internal runtime
configuration, not a new UI setting.

Focused tests passed: **25 raw-tunnel cases**, **8 authenticated EOF integration cases**, and **2 physical
qualification cases**. Those include virtual-time progress/queued-write/cancellation edges, delayed
HTTP/HTTPS responses, secure/plain WebSocket close, 48 keepalive lifetimes and 32 abandoned raw tunnel
lifetimes under a four-connection budget. The physical raw test additionally retired 24 abandoned tunnels
across both phones and admitted every subsequent connection, using a short fixture-only grace.

The second physical test measured these disposable production policy listeners:

| Scenario | Phone A | Phone B |
|---|---:|---:|
| Only A has an active 100 kbps rule, 12,500-byte download | 1,141 ms | 122 ms |
| Both have independently active 100 kbps download | 1,147 ms | 1,103 ms |
| Both have independently active 100 kbps upload | 1,281 ms | 1,210 ms |
| Breakpoint owned by A, B forwards independently | 363 ms after release | 84 ms |

These clients trust only their disposable fixture CA/credentials, connect through ADB reverse to real
production gateway/proxy/policy components, and never read real Companion secrets. This proves physical
carrier/engine/policy behavior; it is separate from a fresh long soak of the repaired installed desktop
through Companion TUN. The original desktop was left running, in accordance with the no-automatic-launch
qualification boundary. Its exhausted old runtime needs a restart into the rebuilt app.

## Short screen-off control check

Both phones remained Dozing for approximately 100 seconds with unchanged Companion PIDs and two
established desktop WSS control sockets at all seven observations. This is a short control-presence
check, not deep-Doze or repaired data-plane soak certification. One first attempt lost its mDNS-managed
wireless ADB transport; direct TLS ADB reconnection recovered access with the same app PID. The second
run and cleanup used that direct connection. Both original power settings and screen timeouts were
verified restored, both ADB devices are available, qualifier packages/reverse mappings were removed,
and only task-owned origins/monitor processes were stopped. No Companion reinstall or data reset occurred.

## Measured presentation optimisation

A 120-second JFR sample of the installed desktop identified the root workspace observer as a candidate
allocation site. Sample weights are estimates, not a measured RAM reduction. The old shell observed
per-second throughput, full row windows and body preparation state. New body-free projections keep
navigation, pending interception routing, clear state and quick Network Conditions controls separate
from high-frequency telemetry. The throughput indicator collects its live values locally.

Regression tests verify that sixty throughput updates produce **zero unrelated navigation emissions**,
while actual presence/selection changes still emit; Network Conditions shortcut observations likewise
exclude throughput ticks. Existing live graphs retain their history owner. No before/after whole-app
CPU/RAM percentage is claimed: compilation and the exhausted old runtime would invalidate that comparison.
The idle baseline median CPU was 1.5% across eleven unprofiled samples; RSS ranged 128–286 MiB. These
short machine-specific samples are context, not product sizing guarantees.

Phone Companion PIDs remained unchanged throughout sampling. A03 PSS was around 155 MiB; A50 grew from
roughly 170 MiB before protocol warmup to about 200 MiB. That is not proof of leak-free long-run memory,
especially with the transport retention found above and host compilation memory pressure.

## Automated gate and remaining acceptance

The initial combined gate passed 1,896 tests across 36 complete task outputs, plus 14 Apple native cases,
Android APK assembly, macOS distributable packaging and shared iOS UI compilation. The gate was rerun
following the half-close repair and range-anchor edge correction: **1,903 cases across 36 outputs**,
plus **14 Apple native cases**, with zero failures/errors/skips. All 3,540 production classes checked
across 22 desktop modules match the packaged classes, with zero missing/mismatched/duplicate classes.
The Android APK contains the new notice, observer and readiness codec. The gate rejects missing, empty, failed, errored or skipped outputs and requires the new workflow suites.

Automated coverage includes real Room/source isolation, selected archive round trips, arbitrary HAR field
order, duplicate headers, missing/empty/binary/partial bodies, Unicode/escapes, invalid UTF-8/base64/JSON,
XML external-entity rejection, ZIP path/schema/hash validation, cancellation/rollback, paging, stale
selection and original execution ownership. A 16-MiB streamed HAR fixture parsed in 515 ms in the focused
run. This verifies that fixture and bounded retained state, not constant peak RAM for arbitrary files.
Rendered tests cover import at 900/1440 widths, stable status/table positions, toolbar overflow, real
modifier clicks, response-gated export bounds and Companion notice behavior.

Reproduce automated checks:

```sh
./gradlew captureWorkflowQualification multiDeviceAppleQualification \
  :products:desktop:createDistributable :products:companion:androidApp:assembleDebug \
  :ui:companion:sharedUi:compileKotlinIosSimulatorArm64
```

For explicit two-phone listener checks, see
[the physical qualifier instructions](../tools/qualification/device_policy/README.md).

Remaining acceptance boundaries:

- Restart the installed desktop into the repaired build and repeat the full-duration real Companion VPN
  soak. The short repeated-slot recovery tests passed; they do not replace that longer workload.
- Native file-picker interaction and visual/keyboard acceptance of the new desktop controls, plus the
  capture notice on an upgraded physical Companion installation, remain manual UI acceptance.
- Installed Charles/HTTP Toolkit GUI round trips were unavailable; fixtures follow published formats.
  Physical Apple packet-tunnel acceptance remains outside the attached Android hardware.

Evidence is retained under `/Users/devuloopers/.codex/verification/knet-overnight-2026-11-15/`: health
samples, JFR, failed original soak, protocol fixture-ready retests, captured-source aggregates, thread
snapshots, focused JUnit outputs and physical timing reports. Test origins/APKs/reverse mappings and
power-setting changes are task-owned and cleaned/restored; real history, pairing and CA remain intact.

## Built artifacts

- Desktop: [KNet.app](/Users/devuloopers/.codex/verification/knet-smart-scroll-2026-11-14/build/products/desktop/compose/binaries/main/app/KNet.app)
- Android: [debug APK](/Users/devuloopers/.codex/verification/knet-smart-scroll-2026-11-14/build/products/companion/androidApp/outputs/apk/debug/androidApp-debug.apk)

Artifacts were built and inspected; the existing desktop and Companion installations were not replaced.
