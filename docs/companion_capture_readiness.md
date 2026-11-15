# Companion recording readiness

VPN connectivity and recording admission are independent. A working Companion tunnel does not imply that
KNet desktop is saving traffic. Desktop capture still starts/stops per authenticated device.

Before inspection starts, Companion requests the new authenticated, pinned HTTPS readiness route for its own
registered identity. If the shared runtime is stopped/failed or still starting, Companion shows Start capture
in KNet with Check again and Cancel. It does not silently start desktop capture. If forwarding is available
but this device's recording is paused, connection remains usable and a dialog explains that it is forwarding
without recording. Starting capture on desktop dismisses the dialog from fresh control evidence.

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
