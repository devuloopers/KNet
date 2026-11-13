# Multi-device workspace qualification

Status: **AUTOMATED QUALIFICATION COMPLETE — PHYSICAL CHECKS PENDING** (2026-11-13).

**875 tests passed across 16 task outputs: zero failures, errors or skipped cases.** Desktop distribution creation, Android APK assembly, six architecture checks, iOS Simulator tests/framework linking and iOS device-target compilation passed. Eight existing real-LAN Wi-Fi fixtures and physical-device/native-window gesture checks remain with the user.

## Delivered behavior

Device tabs use the registered cryptographic identity, independently from the VPN-run UUID, current socket generation and individual capture records. Reconnection reuses the same tab; a reinstall with a new key creates a distinct identity. Names are presentation only and receive a distinct ID suffix. Offline tabs retain history. Unverified local/manual/legacy traffic has its own Other traffic workspace.

The selected source controls Traffic history, search, facets, counts, paging, inspection, viewport, Clear and live byte rates. Capture admission and authored interception, protection and network-condition policies remain global. Canonical SQL applies source predicates before paging, including encrypted opaque records. Protocol messages retain their parent exchange/connection ownership.

One authenticated WSS connection is owned by the running Android VPN service or iOS packet-tunnel extension. It carries start/stop, heartbeat, packet-policy snapshots and lease renewals, actual native acknowledgements and revocation. Pairing, certificates, credential refresh and endpoint recovery retain their pinned HTTPS paths. Older peers retain HTTPS policy maintenance. Control traffic bypasses capture and shaping.

iOS packet shaping remains unavailable. Its extension acknowledges enabled packet policies as unsupported while maintaining device presence. This feature does not add desktop capture of otherwise unobserved UDP or QUIC traffic.

## Architecture and bounds

| Responsibility | Owner / bound |
|---|---|
| Identity, source and workflow | Application contracts/use cases; verified connection ingress determines source |
| Wire envelope | Core Companion; strict version 1, closed vocabulary, duplicate/unknown field rejection |
| Desktop WSS/HTTPS | Ktor 3.5.2 / Netty 4.2.16.Final, one existing TLS control listener |
| Native clients | Ktor with Android OkHttp and iOS Darwin; paired root and transport identity checks |
| Presence | Desktop coordinator; one current generation per identity; monotonic 60-second lease |
| Heartbeat | 15 seconds; transport ping/pong never replaces VPN heartbeat |
| Policy safety | 2-second renewal, 5-second safety lease; expiry disables shaping and requires a fresh snapshot |
| Control queues | 16 incoming / 16 outgoing; incoming overflow closes the channel; blocked outgoing enqueue fails the session within two seconds |
| Protocol size | 8 KiB encoded application envelope; native engine buffering is platform-specific |
| Admission | 128 WSS sessions; independent 32-operation short HTTPS budget; 160 physical control connections including pending TLS/idle keep-alive |
| UI row retention | At most four body-free source windows, each bounded to 1,000 rows |
| Prepared inspection | Existing shared cache: eight entries and 16 MiB retained payload budget |
| Byte metering | Primitive cumulative counters, at most 256 sources; no payload retention or per-chunk Flow emissions |
| Speed clock | One selected-source subscriber at one-second cadence while Traffic is visible |
| Scoped deletion | 128-record batches, transactional outbox, retained sibling/body references preserved |
| Storage migration | 36 to 37 adds ingress/client/connection index without deleting history |
| macOS title bar | Native JBR controls and hit testing; other/unsupported runtimes preserve system decoration |

Traffic footer rates measure actual proxy socket bytes, including HTTP/TLS framing. They continue independently of capture pause and row filters. The Network Conditions graph retains its existing payload-meter basis. Neither is a whole-device operating-system speed measurement.

Android OkHttp owns its native WebSocket frame decoder and does not permit Ktor's max-frame setter. The adapter therefore enforces the application envelope bound when receiving; it does not claim an 8 KiB bound on all native engine allocations from a hostile paired desktop.

## Regression matrix

| Area | Automated evidence |
|---|---|
| Identity and source classification | `TrafficSourceTest`; `DeviceWorkspacePresentationTest`; retained roster use case and Room adapters |
| Presence fencing | `DevicePresenceCoordinatorTest`: stale run, late disconnect, duplicate sequence, exact expiry, capacity and ACK lifetime |
| Protocol validation | `CompanionSessionProtocolTest`: all message kinds, invalid payloads/version/size/direction and duplicate fields |
| Connection budget | `ControlConnectionAdmissionTest`: overflow rejection, reuse, double-close and admission retained through protocol pipeline replacement |
| Backpressure | `ControlSessionOutboundTest`: write deadline, fatal owner failure and no late frame after timeout |
| Real desktop TLS/WSS | `CompanionControlGatewayTest`: HTTPS parity, unauthorized/origin rejection, two clients, live policy/ACK, replacement fencing, malformed/binary/oversized frames and future ACK |
| Native Android WSS | `AndroidCompanionSessionTransportTest`: actual pinned OkHttp handshake, logical TLS authority, message exchange, 401/404 classification, invalid root pin and cleanup |
| Android workflow | `MaintainCompanionSessionUseCaseTest`: applied/failed/inactive/unsupported acknowledgements, lease expiry/reconnect, renewal, idle identity switch, revocation, bounded retries, HTTPS recovery and cancellation cleanup |
| iOS extension owner | `NativeCompanionControlSessionTest`: capability ACK, heartbeat, revocation, no reconnect after revocation and bounded STOP/close cleanup |
| iOS option validation | `TunnelStartOptionsParserTest`: old provider compatibility, complete/missing/wrong-typed control endpoint, fractional/out-of-range port and invalid addresses |
| Scoped persistence | `CanonicalTrafficHistoryQueryTest`: two verified identities sharing IP/host/time, opaque records, both page directions, source/query cursor rejection, facets and shared-session Clear |
| Scoped body ownership | `CanonicalMaintenanceTest`: hundreds of selected records, bounded deletion and a body retained until its last source is cleared |
| TLS fallback preservation | `AutomaticTlsFallbackTest`: source Clear rebinds unexpired decisions, preserves original deadlines and fences late old-generation evidence |
| Large history | `CanonicalTrafficScaleBaselineTest`: 100,000-row source-filtered query and bounded page size |
| Storage migration | `KNetDatabaseMigrationTest`: retained schema generations including 36 migrate to 37 without losing history |
| Real transfer counting | `SourceTrafficMeterHandlerTest`: inbound bytes, successful/failed writes, buffer references and one close |
| Counter isolation/bounds | `SourceTrafficMetersTest`: simultaneous sources, Other, aggregates, close idempotence and inactive-slot reuse |
| Workspace state/races | `TrafficPagingViewModelTest`: source query propagation, retained filter/selection/viewport and switching during Clear |
| Sampler ownership | `DeviceWorkspaceViewModelTest`: restored selection, stable roster, one sampler, independent zero baseline, no focus stealing and hidden-workspace cancellation |
| Rendered tabs | `DeviceWorkspacePresentationTest`: compact/wide layouts, long/duplicate names, horizontal overflow, selected/offline semantics |
| Native window policy | `NativeTitleBarPolicyTest`: runtime fallback, controls/drag hit regions, fullscreen insets and removed regions |
| Existing protocol/capture behavior | Full affected proxy, protocol, connectivity, capture, protection, Traffic and product suites |

This matrix records requirement-focused regression coverage. It is not a measured assertion of 100% line/branch coverage, nor a claim that every possible operating-system/network failure has been enumerated.

## Measured resource and scale observations

The real control-channel fixture measured the following JVM heap occupancy and thread counts. These include the test client, TLS and class initialization; they are observations without GC normalization, not whole-app RSS measurements or a promise about hardware RAM usage.

| Active devices | JVM heap used (MiB) | JVM threads |
|---:|---:|---:|
| 0 | 54.84 | 18 |
| 1 | 61.84 | 23 |
| 2 | 63.84 | 24 |
| 10 | 68.84 | 24 |

The 100,000-row fixture completed its selected-device plus Other query measurement in **2,090 ms**, within the existing 5,000 ms budget. The existing 500 MiB streaming regression and all 164 proxy tests passed. The real WSS suite also exercises 25 reconnects with a two-session admission limit.

The bundled Java 21 JetBrains runtime was probed headlessly with the packaged module set: native decorations are supported and a native custom title-bar object accepts the 40-pixel height. No KNet desktop application was launched. Actual OS gestures remain manual checks.

## Reproduction and artifacts

The final run uses an isolated build directory so the user's IDE outputs remain untouched:

- Runner: `/Users/devuloopers/.codex/verification/knet-multi-device/qualify.sh`
- Gradle init script: `/Users/devuloopers/.codex/verification/knet-multi-device/isolated.init.gradle.kts`
- Log: `/Users/devuloopers/.codex/verification/knet-multi-device/tab-close-qualification.log`
- Module XML/HTML reports: `/Users/devuloopers/.codex/verification/knet-multi-device/build/`

Repository gates: `./gradlew multiDeviceQualification multiDeviceAppleQualification --max-workers=1`. The desktop/Android gate also runs the dedicated 100,000-row storage qualification task. The gates include affected JVM and Android host tests, architecture checks, desktop distribution creation, Android APK assembly/unit tests and iOS Simulator tests/framework linking plus device-target compilation. No KNet desktop application is launched by this qualification.

Machine-readable counts: [`multi_device_execution_summary.json`](multi_device_execution_summary.json).

Built artifacts:

- Desktop: `/Users/devuloopers/.codex/verification/knet-multi-device/build/products/desktop/compose/binaries/main/app/KNet.app`
- Android: `/Users/devuloopers/.codex/verification/knet-multi-device/build/products/companion/androidApp/outputs/apk/debug/androidApp-debug.apk`
- Apple framework: `/Users/devuloopers/.codex/verification/knet-multi-device/build/products/companion/iosPacketTunnel/bin/iosSimulatorArm64/debugFramework/KNetPacketTunnelRuntime.framework`

The desktop control gate excludes eight existing Wi-Fi socket fixtures which explicitly require a real non-loopback IPv4 interface. This execution host has none after restart. The production LAN safety checks remain unchanged; these fixtures remain available through `:connectivity:desktop:jvmTest --tests '*WifiSharingBackendTest'` on a connected host. The resource-backed Wi-Fi page unit test remains automated.

## Physical checks retained for the user

1. Pair two physical companions, connect both VPNs, and confirm distinct tabs/status and independent HTTP, GraphQL, gRPC, streaming and protected TLS traffic.
2. Reconnect the same identity, switch desktops, stop/restart the VPN, kill/restart the Companion, and test Wi-Fi loss, roaming and desktop restart. Confirm eventual offline status and no duplicate identity for ordinary reconnects.
3. Pause capture, resume, switch tabs during active streams/interceptions, and Clear one device while the other continues. Confirm both applications continue forwarding and history/payloads remain isolated.
4. Verify independent upload/download readings and actual shaping with Off, 4G, 100 kbps and Offline. Confirm idle zero and no speed spike merely from selecting another device.
5. Verify Android background/notification behavior and signed iOS NetworkExtension entitlement/routing on hardware. Unsupported iOS packet shaping must remain clearly unavailable.
6. Run the retained real-LAN Wi-Fi fixture suite on a connected host and verify companion forwarding on that interface.
7. On the packaged macOS JBR runtime verify real close/minimize/zoom buttons, native drag, double-click preference, edge resize, fullscreen entry/exit, monitor changes, scaling, keyboard navigation and shutdown. Verify clicking/scrolling tabs does not drag the window.



## Tab visibility and confirmed close refinement (2026-11-13)

Eligibility is independent of current search/type/status filters and payload byte counts. Connected devices
are visible even when empty. Reconnecting devices remain during the existing presence lease grace period.
Offline devices are visible only while at least one local HTTP exchange or opaque flow remains. Other traffic
is always available. Revoked identities cannot appear online, but their retained history remains selectable.

Offline tab Close and toolbar Clear use one confirmation, showing the device name and unique identity suffix.
Confirmation targets that identity's complete local traffic, without deleting pairing or sibling history.
Connected Close is disabled with hover help; connected Clear deletes local history and keeps the tab. If the
device reconnects while confirmation is open, the dialog explains that its tab will remain. New traffic arriving
after the capture-clear boundary remains visible. Closing a background tab never selects or reloads it, and
removing the selected offline tab falls back to Other without later focus stealing.

| Edge case | Evidence |
|---|---|
| Online empty, reconnect grace, offline empty/history, revocation, stable order, heartbeat suppression | `ObserveDeviceWorkspacesUseCaseTest` |
| HTTP and zero-byte TLS membership, multiple sessions/connections, spoofed/blank identities, reactive deletion, pairing/sibling retention | `CanonicalTrafficHistoryQueryTest` |
| Shared Clear/Close confirmation, cancellation, exact background target, repeat actions, reconnect during confirmation, selection fallback | `DeviceWorkspaceViewModelTest` |
| Independent accessible Close action, disabled online help, background selection unchanged, native interactive bounds, compact overflow | `DeviceWorkspacePresentationTest` |
| Explicit-source deletion, filtered-out history, duplicate-click guard, sibling table not reloaded, stale cache discarded, failure/retry | `TrafficPagingViewModelTest` |

Physical follow-up: with two devices, Clear an online tab and verify new traffic resumes; disconnect one device,
clear/close it after confirming, cancel once to verify no deletion, then reconnect while a confirmation is open.
Verify the connected tab remains and pairing does not require a new QR scan. Check macOS blank-space dragging
and keyboard/pointer activation of the new Close button in the packaged desktop app.

Refinement verification passed both gates: **865 desktop/Android tests plus 10 Apple Simulator tests**, including
10 added regression cases, with zero failures/errors/skips. The affected application/data/app-shell/Traffic suites
contain 75/122/35/130 cases respectively. Desktop distribution, Android APK, six architecture checks and Apple
framework/device-target checks passed. The desktop app was not launched.

## Compact tab appearance (reference image, 2026-11-13)

The design update is confined to UI-core workspace-tab painting/geometry and the app-shell tab label presentation.
Each tab is a separate rounded 32 dp control centered in the existing 40 dp strip, with a 6 dp gap. Selected tabs
have a complete blue outline and tinted surface; inactive tabs use quiet tonal surfaces. Identity suffixes use
secondary typography. The strip preserves width bounds, horizontal overflow, status indicators and native
interactive rectangles. Tab visibility and Close/Clear workflows are unchanged. Existing headless presentation
checks exercise compact/wide overflow and semantic/pointer Close activation without selecting the background
tab. The rendered preview is written to `ui/desktop/app/build/reports/device-workspace-tabs/compact-tabs.png`.

Verification passed: **67 UI cases** (32 UI-core plus 35 app-shell), zero failures/errors/skips, all six
architecture checks and desktop distribution creation. The actual compact-tab rendering was inspected at
2x display density. The desktop app was not launched.
Log: `/Users/devuloopers/.codex/verification/knet-compact-tabs/verification.log`.


## Device tab label cleanup and width (2026-11-13)

Companion registrations can already contain a generated four-character suffix. Desktop display strips only
that exact identity-derived ending and appends one collision-safe desktop suffix, without changing the saved
name or registered identity. Custom endings remain intact. Tabs size to their contents up to 360 dp, preserve
one-line ellipsis beyond the cap and continue to scroll horizontally. The full label appears on name hover;
leaving the name dismisses it, and hovering Close uses its own description without simultaneous popups.

Added regressions for matching/custom endings and suffix collisions, the real Samsung label, natural widths
past the old 280 dp limit, bounded long-name layout, and hover entry/exit/Close-help separation.
Verification passed: **70 UI cases** (32 UI-core plus 38 app-shell), zero failures/errors/skips, all six
architecture checks and desktop distribution creation. The rendered Samsung name, natural widths and
long-name ellipsis were visually inspected in `ui/desktop/app/build/reports/device-workspace-tabs/label-widths.png`.
The desktop app was not launched. Log: `/Users/devuloopers/.codex/verification/knet-tab-labels/verification-final.log`.
