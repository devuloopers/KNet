# Multi-device Traffic workspace and macOS title bar

- **Created:** 2026-11-13
- **Status:** [AUTOMATED QUALIFICATION COMPLETE — PHYSICAL CHECKS PENDING]
- **Goal:** Inspect several paired Companion devices on one desktop without mixing their traffic views.
- **Delivery boundary:** macOS desktop title bar, Android/iOS Companion presence integration, device-scoped
  Traffic, automated qualification, and an explicit physical-device checklist. Platform support claims remain
  governed by the runtime capability catalog and actual device evidence.

## Agreed product decisions

1. Use the proposed Chrome-style device tabs in a KNet-themed macOS title bar. Keep real native window controls.
2. Use the existing registered-device identity and Companion display name, including its current short suffix.
   The complete `RegisteredDeviceId`, rather than the visible name or suffix, identifies a tab.
3. Keep one tab per registered identity. Reconnecting with that identity updates its existing tab.
4. Retain an offline device's tab and captured history. Offline tabs remain selectable.
5. Start one authenticated WebSocket presence channel for the active Companion VPN session. Closing the mobile
   app's UI must not stop presence while its VPN service or packet-tunnel extension remains running.
6. Device selection scopes Traffic and its live speed readings. It does not stop background capture or change
   which devices can forward traffic.
7. Keep identity recovery simple: if reinstalling or clearing data results in a new cryptographic identity,
   treat it as a new device. Do not add hardware identifiers, recognition hints, recovery accounts, or automatic
   history merging in this increment. A retained, successfully authenticated identity can reuse its existing tab.
8. Custom Windows/Linux window decorations, automatic replacement of an old device registration, per-device
   shaping profiles, and per-device capture start/pause are outside this increment.

## Existing foundations and missing work

Repository inspection confirmed these implementation boundaries on 2026-11-13. This is feasibility evidence,
not a claim that the proposed feature already exists.

| Area | Existing foundation | Required addition |
|---|---|---|
| Identity | `core/identity` owns `RegisteredDeviceId` and durable display names; pairing persists trusted devices. | Observe a device roster and project connection status independently from pairing timestamps. |
| Attribution | `AuthenticatedProxyGateway` authenticates each stream and attaches `LanPairedDevice` plus its verified device ID. | Preserve and exercise this association throughout queries, inspector actions, live messages, and telemetry. |
| Storage | `traffic_connections` persists `ingressKind` and `clientIdentity`; exchanges and opaque flows refer to connections. | Device-scoped indexed queries, facets/counts, scoped history maintenance, and migration qualification. |
| Presence | Proxy sockets are tracked privately for revocation. The control gateway currently serves bounded HTTPS requests and closes each connection. | A real WebSocket endpoint/client and lifecycle-owned presence coordinator; the current HTTPS server is not already a WebSocket server. |
| Traffic | Canonical history uses bounded keyset pages and live generations. | Typed source scope and separately restored filters, selection, inspector navigation, paging, and scroll state. |
| Rates | Network Conditions publishes aggregate counters on a monotonic one-second sampling clock. | Actual device-attributed counters and scoped observations using the same rate calculation and indicator components. |
| Window | `DesktopBootstrap` opens a standard decorated Compose window. | A native-backed macOS title-bar adapter and reusable device-tab presentation. |

Relevant entry points:

- `core/identity/src/commonMain/kotlin/com/devuloopers/knet/identity/RegisteredDeviceModels.kt`
- `connectivity/desktop/src/jvmMain/kotlin/com/devuloopers/knet/connectivity/desktop/gateway/AuthenticatedProxyGateway.kt`
- `connectivity/desktop/src/jvmMain/kotlin/com/devuloopers/knet/connectivity/desktop/gateway/CompanionControlGateway.kt`
- `application/desktop/src/main/kotlin/com/devuloopers/knet/application/contract/traffic/TrafficQuery.kt`
- `storage/src/jvmMain/kotlin/com/devuloopers/knet/storage/capture/entity/CanonicalCaptureEntities.kt`
- `ui/desktop/app/src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/app/workspace/WorkspaceHost.kt`
- `products/desktop/src/jvmMain/kotlin/com/devuloopers/knet/products/desktop/bootstrap/DesktopBootstrap.kt`

## Source classification and tab behavior

Model the selected Traffic source with a typed contract, rather than overloading nullable strings:

- **Paired device:** Match its authenticated `RegisteredDeviceId` and paired ingress category.
- **Other traffic:** Retain traffic without a verified paired-device association in one clearly named fallback
  tab. This includes local/manual proxy traffic, currently unattributed ADB traffic, and older unclassified rows.
  It is not labelled as a specific physical device. Keep this fallback available before the first device pairs.

No combined all-device tab is required for the initial design. Preserve all records in the canonical store;
tabs are indexed views over shared history, not separate capture engines or databases.

Rules:

- Do not infer a paired identity from an IP address, hostname, device name, request header, or selected tab.
  Two phones behind the same NAT or making the same request must remain distinct.
- Display existing Android manufacturer/model names and existing iOS device-family names without promising a
  personal iPhone name or exact iPhone model that Companion does not currently collect.
- Derive tab order from stable registration ordering. Connection-status updates must not reorder tabs or steal
  selection. A new device can become selected when no previous usable selection exists.
- Persist only small workspace preferences such as the selected source. On restart, restore registered tabs
  and retained history, but initialize their presence as offline until freshly verified.
- Show a restrained status dot and an explicit offline state using KNet tokens. A short reconnecting state may
  appear during recovery; do not repeatedly add/remove tabs as the network changes.
- No close buttons, duplicate pairing button, or extra dashboard is needed in the tab strip. Existing Connect
  Device workflows remain the registration entry point.
- Use shared horizontal overflow/scrollbar and focus behavior when tabs do not fit. Long names remain one line.
- Revocation prevents future authentication and closes authorized channels according to existing policy.
  Retained captures remain inspectable; their historical identity must not be reassigned to another device.

## Scope of existing controls

| Surface or action | Behavior in this increment |
|---|---|
| Traffic rows, counters, filters and facets | Only the selected source, with source filtering applied before paging/counting. |
| Overview, bodies, headers, messages and timeline | Only the selected record; reject stale results from a previous tab. |
| Search, auto-scroll, selected record and inspector tabs | Restore each source's own lightweight workspace state. |
| Download/upload footer | Actual observed rates for the selected source; initialize/reset at `0 kbps`. |
| Start/Pause Capture | Remain desktop-wide. Communicate this scope in existing help/accessibility text. Pausing capture does not disconnect devices. |
| Clear | Clear the selected source's captured history without deleting another source's records or stopping forwarding. |
| API Studio import/copy/export actions, where available | Act on selected records; imported API Studio execution remains desktop-origin traffic. |
| Network Conditions quick control and full screen | Keep current global/domain/protocol policy semantics and global monitoring. Selecting a device does not silently change a policy's scope. |
| Protected Traffic and breakpoint rules | Keep their existing matching scope. Selecting a device is not a new rule-matching criterion. |
| Live interception | Preserve pending decisions when switching devices. Another device's paused exchange must remain discoverable through existing interception management. |
| Other sidebar destinations | Remain shared screens. Remember the Traffic source when navigating away and back. |

## Authenticated presence protocol

Presence answers whether the desktop has recently verified that a Companion VPN session is running. It does
not identify the owner of an arbitrary traffic connection and does not prove that the phone is powered off
when its lease expires.

### Lifecycle

1. Pair using the existing QR, proof key, pinned desktop trust and credential flow.
2. Start the VPN/packet forwarder. Only after real startup succeeds, open one pinned `wss://` channel and send
   a typed session-start message. Pairing alone must not show an actively connected VPN.
3. Bind the connection to the authenticated principal. Validate the existing `PROXY_STREAM` authorization for
   this VPN-presence capability; never trust a client-supplied device ID or permit device-list access implicitly.
4. Use a versioned, bounded protocol containing only necessary session/state information. Distinguish WebSocket
   ping/pong health from the application message declaring that the VPN session is running.
5. Initial timing proposal: one application heartbeat every 15 seconds and a 60-second lease, centralized in a
   tested policy. Use a monotonic desktop clock for expiry, with wall-clock last-seen time for display only.
6. A normal stop sends a best-effort stop event and cancels the channel. Abrupt process loss or unreachable
   networks expire the lease. Closing a stale channel or receiving an old stop event cannot retire a newer run.
7. Recover with bounded exponential backoff and jitter. Fence each VPN run and each channel generation so a
   delayed heartbeat, duplicated frame or reconnect cannot resurrect a stopped/revoked session.
8. A presence outage must not close otherwise working proxy streams. Distinguish reconnecting control state
   from transport failure. If bytes still arrive on an attributed stream, report those real bytes rather than
   inventing a zero rate solely because the presence channel expired.

### Transport and resource requirements

- Reuse the existing desktop trust/pinning, authentication and discovery boundaries. Qualify the maintained
  WebSocket transport and runtime dependencies before choosing the endpoint implementation. Add an explicit
  WebSocket route; preserve existing bootstrap, refresh, certificate and control HTTPS behavior.
- The existing custom `SSLServerSocket` request handler cannot be treated as an automatic WebSocket upgrade.
  Keep transport changes behind connectivity adapters and avoid a handwritten WebSocket framing engine.
- Preserve native Android and Darwin trust enforcement. A normal Ktor HTTP client's pin checks do not by
  themselves qualify a new WebSocket path; verify the actual upgrade and subsequent connection handling.
- Keep credentials out of URLs, logs, captures, UI state and durable presence rows. Bound handshakes, message
  size, send queues and reconnect attempts; reject malformed versions and unauthorized messages.
- Give long-lived presence channels separate admission accounting so they cannot consume every slot reserved
  for short control requests, credential refresh or pairing. Limit to one authoritative channel per device/run.
- Run control traffic outside the captured/shaped data path. Selecting Offline or 100 kbps must not manufacture
  device disconnections or heartbeat records in Traffic.
- Expiry, rotation and revocation apply to already-open channels as well as new handshakes. Reauthenticate
  through the existing credential workflow when necessary; do not grant a perpetual lease to a stale token.
- Own Android presence in the VPN service and iOS presence in the packet-tunnel runtime, not in a foreground
  screen. Respect the lean iOS extension's dependency/memory budget and startup/teardown ordering.
- Cancel and join owned jobs on stop, desktop switch and shutdown. Do not persist a live online flag or write
  the database on every heartbeat; coalesce any durable last-seen updates.
- Missing presence support in an older Companion is a typed unknown/unavailable state, not a fabricated
  assertion that its VPN is healthy. Its authenticated proxy traffic must still be captured and attributed.

## Implementation phases

### Phase 1 — Contracts and source model [COMPLETED — AUTOMATED]

- [x] Add typed Traffic source, presence/session generation, status, and device-roster observation contracts.
- [x] Keep registered identity, current presence and captured-connection lifetime as separate concepts.
- [x] Add application use cases for observing devices, selecting/restoring source state, presence updates,
  scoped queries and scoped maintenance. UI ViewModels consume use cases, not storage or socket implementations.
- [x] Document module ownership and maintain product-only Koin bindings. Avoid a new module unless the existing
  boundaries cannot own a cohesive responsibility.

### Phase 2 — Presence transport and VPN lifecycle [COMPLETED — AUTOMATED]

- [x] Add the actual authenticated WebSocket server/client adapters and protocol negotiation.
- [x] Implement desktop lease ownership, fencing, bounded admission and credential/revocation handling.
- [x] Integrate Android service start/stop, failure cleanup, background ownership and endpoint recovery.
- [x] Integrate the same protocol in the iOS packet-tunnel extension, including pinned endpoint access,
  extension-owned cancellation and exclusion from recursive VPN routing.
- [x] Switching the Companion's selected desktop stops the old presence run before activating the new one.
  One Companion VPN remains connected to one selected desktop at a time.

### Phase 3 — Attribution and canonical history queries [COMPLETED — AUTOMATED]

- [x] Carry the verified source from admitted connection through HTTP/1, HTTP/2, GraphQL GET/POST, gRPC, SSE,
  WebSocket, GraphQL WebSocket, protected TLS tunnels and any already-recorded opaque flow types.
- [x] Scope protocol messages through their owning exchange/connection; preserve parent ownership through
  upgrades, long-lived streams, cancellation, pause/resume and capture-generation changes.
- [x] Apply source filtering in SQL before keyset pagination, including opaque rows, combined order, total
  counts, HTTP/HTTPS counts, search, facets and history ordinals. Cursors must not cross source/query scopes.
- [x] Add only the Room indexes/schema changes supported by query plans and measured representative fixtures.
  Test migration from the current schema; never guess the device for a historical row lacking attribution.
- [x] Do not load all device traffic into memory and split it in Compose. Keep canonical snapshots and body
  references shared rather than copying payloads into per-tab collections.
- [x] Preserve current packet visibility limits. Device tabs do not create a new UDP/QUIC capture path. A
  Companion-local packet record can enter a device view only through a separately authenticated attribution
  path; unsupported/unobserved packets must not be presented as captured desktop traffic.

### Phase 4 — Scoped Clear and capture safety [COMPLETED — AUTOMATED]

- [x] Extend maintenance to accept the selected typed source and define a clear operation's capture boundary.
- [x] Serialize clearing with writer transitions. Fence queued callbacks so old selected-source rows do not
  reappear after Clear; new traffic after its boundary can appear normally.
- [x] Retain another device's records even when both share a capture session or reused long-lived streams.
  Do not implement selected-device Clear by deleting the shared session.
- [x] Reclaim bodies/messages only when ownership and references permit it. Do not delete files still used
  by retained records, an active writer or an inspector read; reuse existing body-store maintenance rules.
- [x] Preserve the existing explicitly global startup auto-clear setting. Source-scoped toolbar Clear must
  not accidentally change that preference or its established global behavior.

### Phase 5 — Device telemetry [COMPLETED — AUTOMATED]

- [x] Add bounded cumulative counters keyed by the same verified source at actual transfer boundaries.
  Define and document byte semantics for decrypted payloads and opaque forwarded bytes; avoid double counting
  bridge, proxy, TLS framing and protocol inspection observations of the same transfer.
- [x] Keep one shared monotonic one-second sampling cadence. Reuse the current formatter/indicator and rate
  calculation. A configuration update, heartbeat or extra byte event must not advance graph time.
- [x] Report device-to-server as upload and server-to-device as download. The values are observed KNet traffic,
  not configured profile limits or the phone's total interface bandwidth.
- [x] Scope the Traffic footer; leave the Network Conditions graph aggregate. Monitor during shaping off/on
  and while capture is paused if forwarding remains active.
- [x] Isolate sample baselines when switching tabs or reconnecting; initialize/reset at `0 kbps`, handle idle
  intervals and counter resets, and use existing unit scaling without label/value layout shifts.
- [x] Bound inactive-source telemetry state and release it on lifecycle teardown. Do not retain per-device
  histories, payloads or coroutines just to keep an offline tab visible.

### Phase 6 — Traffic workspace state [COMPLETED — AUTOMATED]

- [x] Observe the durable roster and runtime presence in the app shell; retain one stable tab per identity.
- [x] Pass source scope through Traffic's list, counts, facets, details, protocol messages and footer use cases.
- [x] Preserve each source's search, filters, auto-scroll, selected row, inspector/subtab and scroll anchors.
  Keep inactive state lightweight and bound cached pages/editor state instead of retaining a full screen per tab.
- [x] Cancel or fence in-flight list/detail/body/message requests and actions on source changes. Rapid switching
  must never paint another device's late response, selection, protection action or speed reading.
- [x] Preserve stable inspector layout and shared scrollbar/divider behavior already qualified in KNet.
- [x] Switching a tab cannot resolve, discard or duplicate an intercepted request. Keep global interception
  management usable for background-device requests and identify their source in the existing pending context.

### Phase 7 — macOS title bar and device tabs [COMPLETED — AUTOMATED]

- [x] Qualify full-window content/native-backed title-bar support against development and packaged JVMs before
  selecting the adapter. Retain native decorations as a safe fallback if integration is unavailable.
- [x] Keep real close/minimize/zoom controls and reserve their native insets. Implement a themed tab strip
  without painting fake traffic-light controls or overlapping native input regions.
- [x] Preserve native window dragging, double-click behavior following macOS preferences, resizing, minimize,
  zoom, fullscreen, multi-monitor movement, keyboard shortcuts and existing shutdown cleanup.
- [x] Exclude interactive tabs/scroll controls from the drag region. Empty title-bar space remains draggable;
  switching a tab must not move the window or trigger native zoom.
- [x] Add reusable UI-core tab geometry/overflow/accessibility behavior and app-shell presentation with
  one-line labels, selected/status states and keyboard focus. Keep native APIs in the product/platform adapter.
- [x] Keep device context visible as proposed across sidebar navigation, but apply its data scope only to
  Traffic. Windows/Linux retain native decorations and use the same in-content device selector until their
  custom decoration work is separately planned.

### Phase 8 — Automated qualification [COMPLETED — AUTOMATED]

Use deterministic clocks, bounded fixture stores and real loopback TLS/WebSocket/proxy integration where
transport behavior matters. Unit fakes alone cannot establish working multi-device capture.

| Area | Required cases |
|---|---|
| Identity/roster | Same identity reconnects without duplication; two devices with the same name or short suffix stay separate; a new identity after reinstall stays separate; revocation and restart restoration. |
| Admission/security | Missing/invalid/expired/revoked credential; insufficient scope; claimed identity mismatch; wrong certificate/pin; malformed upgrade/frames; oversized messages; unsupported version; secrets absent from logs/captures. |
| Presence | Successful/failed VPN start; normal stop; app UI closure; process loss; exact lease boundary; delayed/duplicate/out-of-order events; stale disconnect after a new run; endpoint change; reconnect backoff; credential refresh and desktop shutdown. |
| Concurrency/resources | At least two real clients; repeated reconnection; separate presence/control admission; pending handshakes; bounded queues/state; cancellation and socket/job cleanup; no cross-device eviction. |
| Attribution | Same host/path/time and same source IP for two devices; many connections per device; one-shot bridge claim; opaque TLS and protocol upgrades; spoofed headers and local traffic cannot impersonate a paired device. |
| Queries/storage | Interleaved device rows and mixed protocol types; page boundaries, equal timestamps, stale/wrong-source cursors, facets/counts/search, migrated legacy rows, empty/offline history and live updates. |
| Protocol inspection | HTTP/1, HTTP/2 multiplexing, GraphQL GET/POST, gRPC, SSE, WebSocket, GraphQL WebSocket and protected TLS; child messages stay with their original owner after switching. |
| Capture/clear | Global pause/resume without disconnect; selected-source Clear across a shared session; in-flight callbacks and long-lived streams; body ownership; another device continues recording; global startup clear remains global. |
| Telemetry | Independent simultaneous upload/download; idle zero; correct units/direction; reconnect/reset; monotonic gaps; no negative/spiked tab-switch rates; no double counting; shaping off, 4G, 100 kbps and Offline; extra events cannot accelerate sampling. |
| Workspace races | A/B/A switching during page/detail/body/message reads; restored filters/selection/scroll; no stale paint; deleted selected record; navigation away/back; background device does not steal focus; pending intercepts remain actionable. |
| Rendered UI | Selected/online/reconnecting/offline/unknown states; duplicate and long names; many tabs; compact widths; larger text; keyboard/focus/accessibility; scrollbar clearance; no inspector jumping. |
| Window adapter | Bounds/inset and drag-hit testing; native fallback; fullscreen insets; disposal; product wiring and packaged-runtime launch smoke evidence. Native OS gestures also require manual verification. |

- [x] Extend existing pairing gateway, canonical history, capture-session, Traffic, app-shell and Companion
  suites with meaningful regressions for the matrix above. Add coverage for new contracts and migration paths.
- [x] Run affected module tests, the repository's architecture checks, desktop compilation/package verification,
  Android host tests/APK assembly, and iOS shared/extension compilation plus available simulator linking gates.
- [x] Include representative indexed-history and reconnect/streaming load checks, recording device count, rows,
  duration and memory baseline/delta. Do not claim low RAM or unlimited devices without measured evidence.
- [x] Record exact commands, test totals, failures/skips, runtime versions and unresolved limitations in a
  focused qualification report. Update the main implementation board as phases progress.

### Phase 9 — Physical and native-window qualification [PENDING]

- [ ] Two physical Android devices connect simultaneously and make distinguishable requests to the same host.
- [ ] Android plus an entitled physical iPhone/iPad run concurrently when the iOS runtime is eligible; do not
  promote existing unavailable iOS packet capture based only on shared compilation or simulator evidence.
- [ ] Verify foreground/background, screen lock, VPN stop, Wi-Fi loss/change, desktop sleep/wake, credential
  revocation, mobile process termination and switching the Companion to a second configured desktop.
- [ ] Verify offline history, reconnect identity reuse, desktop restart and retained names. A new identity after
  reinstall creates a new tab by design; historical captures remain associated with their original identity.
- [ ] Perform controlled upload/download tests with two devices under unthrottled, 4G, 100 kbps and Offline
  conditions. Compare real transfers, selected-device rates and aggregate monitoring without capturing heartbeats.
- [ ] Verify macOS native controls, blank-space drag, tab clicks, double-click preferences, edge/corner resize,
  fullscreen transitions, multiple displays, display scaling, Mission Control and normal close/shutdown.
- [ ] Repeat native-window smoke tests in the packaged app, not only an IDE/Gradle launch.

## Completion criteria

- Two authenticated devices can capture concurrently, with correctly separated rows, inspectors, counts,
  Clear behavior and actual speed readings across all currently captured protocol types.
- Reconnecting with the same identity updates one tab; offline history survives navigation and desktop restart.
- Presence is tied to the running VPN lifecycle, bounded, authenticated and independent of shaping/capture pause.
- Native macOS window behavior remains usable and qualified with real controls; tab interaction is stable.
- Automated evidence and outstanding physical checks are reported separately. Implementation completion must
  not be described as full physical qualification before Phase 9 evidence exists.
- Reinstall recognition, automatic identity/history merging, Windows/Linux custom decorations and new protocol
  support remain explicitly outside this delivery rather than hidden prerequisites.


## Execution evidence (2026-11-13)

Software phases 1–8 are implemented and passed both repository qualification gates: 865 desktop/Android cases plus 10 iOS Simulator cases, with no failures or skips. The dedicated 100,000-row storage task and existing 500 MiB proxy streaming regression passed. Desktop/Android artifacts and Apple Simulator framework/device-target compilation are verified. Native window API support is probed without launching KNet; actual OS gestures remain in Phase 9. The eight real-LAN fixtures require a connected non-loopback IPv4 interface and remain part of physical qualification, without changing production LAN safeguards.

See [the qualification report](multi_device_qualification.md) for the complete matrix, measured bounds, exact commands and artifact paths. This is requirement-focused regression evidence, not a measured 100% line/branch coverage assertion.


## Device tab lifecycle refinement (2026-11-13) [COMPLETED — PHYSICAL CHECKS PENDING]

- [x] Observe indexed canonical-history membership across sessions, including zero-byte TLS metadata.
- [x] Show connected devices even when empty, retain reconnecting devices during the lease grace period,
  and hide offline devices without local traffic. Keep Other traffic as a stable fallback.
- [x] Add a Close button to offline tabs, with the same confirmation as offline toolbar Clear. Identify the
  exact target by device name and unique suffix; clear its entire local history regardless of filters.
- [x] Keep connected Close disabled with explanatory hover help. Online Clear leaves the empty tab visible.
- [x] Preserve pairing, sibling traffic and selection. Reconnection during confirmation keeps the tab;
  removing the selected offline tab falls back once and later reconnect does not steal focus.
- [x] Complete storage, use-case, ViewModel, rendered accessibility/native-hit-bound regression checks,
  product wiring/build verification and architecture checks. Physical gestures remain user-owned.

Refinement qualification added 10 regression cases. Both gates passed with 875 total tests, zero failures/errors/skips,
all architecture checks and desktop/Android/Apple artifacts. Physical close/clear and native gestures remain pending.
