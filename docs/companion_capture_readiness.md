# Companion recording readiness

VPN connectivity and recording admission are independent. A working Companion tunnel does not imply that
KNet desktop is saving traffic. Desktop capture still starts/stops per authenticated device.

Before inspection starts, Companion requests the new authenticated, pinned HTTPS readiness route for its own
registered identity. If the shared runtime is stopped/failed or still starting, Companion shows a themed
bottom sheet: Start capture in KNet with Check again and Not now. It does not silently start desktop capture. If forwarding is available
but this device's recording is paused, connection remains usable and the same bottom-sheet style explains
that it is forwarding without recording, with Got it to dismiss. Starting capture on desktop closes
the notice from fresh control evidence.

Readiness is derived from the coherent `WorkspaceCaptureSnapshot`, including per-device admission. No route
accepts a user-selected device ID as a substitute for the authenticated principal. Another phone's capture
choice does not affect this phone's status or emit an unrelated update.

New WSS clients opt into capture-status frames with `X-KNet-Capture-Status: 1`. Existing envelope encoding omits
the new nullable field, and old clients receive no new frame kind. Reconnect sends a fresh state; clients
reset prior status to Unknown and reject stale revisions. Status frames do not renew network-policy leases.
Legacy/unsupported HTTPS routes, malformed responses or timeouts return Unknown and preserve prior compatible
connection behavior. Unknown never claims recording or fabricates an authoritative stopped state.

A dismissed paused/stopped prompt is not reopened by heartbeats. A new transition or reconnect can show a new
notice. Changing the selected desktop clears stale notices; a late observation for another desktop is ignored.

The Apple extension accepts negotiated status frames without changing packet policy or restarting its socket.
Because its control owner runs in another process, the foreground Companion status observer falls back to
pinned HTTPS only while inspection is running: after a two-second WSS grace period, every five seconds for
known states and every thirty seconds for unknown/legacy replies. An app-visible authoritative WSS status
suppresses polling. Stopping inspection, changing desktop or disposing the owner cancels reads. HTTP evidence
has its own capture-observation type and never fabricates a WSS WELCOME/connected phase.

## Notice presentation

Android and iOS share `CompanionCaptureNoticeSheet` in `:ui:companion:sharedUi`. It uses the same
KNet-themed modal treatment as Companion permission sheets: rounded corners, standard drag handle,
selected-desktop guidance, full-width touch actions, native modal insets and scrollable content on
compact/larger-text layouts. The standard modal retains its accessibility pane and drag-handle
actions. Back/scrim/swipe dismissal sends the existing notice-dismissed action. Recheck shows loading and
blocks duplicate taps while its operation is pending. No platform-specific dialog or new network
request path is introduced; the selected-desktop status reducer continues to own prompt lifetime.

## Bottom-sheet verification

The UI/presentation run passed 116 automated cases without failures/errors/skips. The actual common
root is exercised for bottom anchoring, recheck/deferral/acknowledgment, selected-desktop instructions,
standard dismiss and outside-tap behavior, loading and duplicate-input blocking, capture-start closure,
modal priority and reachable actions at 320x480 with 150% text on both platform projections. Existing
reducer tests retain heartbeat deduplication and foreign-desktop rejection coverage. Architecture
validation, Android debug assembly and shared iOS device/simulator compilation passed. APK inspection
confirmed the new sheet facade and absence of the old capture-dialog facade. Native gesture behavior
on physical devices remains interactive acceptance; automated render checks run without device UI.
Evidence: `/Users/devuloopers/.codex/verification/knet-companion-capture-sheet-2026-11-15/`.
