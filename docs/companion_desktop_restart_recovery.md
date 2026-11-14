# Companion recovery after desktop restart

The VPN owns its authenticated control WebSocket for the lifetime of a run. Restarting the paired
desktop does not require stopping the VPN, scanning another QR code or registering another device.
Desktop presence becomes online only after the new desktop process authenticates a fresh START.
Capture can remain paused; its control listener starts independently of traffic recording.

## Recovery and ownership

- A socket-local cancellation or EOF is a recoverable transport loss. Actual cancellation of the VPN
  owner still ends retries, closes the client and releases packet shaping.
- Each attempt uses its paired endpoint and credential, verifies the existing root and
  transport pins, sends START and waits for WELCOME. It retains the device identity and VPN run ID.
- Missing control envelopes expire after ten seconds, including when packet shaping is unsupported.
  The desktop normally sends a policy renewal every two seconds. This health deadline is separate
  from the five-second packet-policy safety lease and the fifteen-second outgoing heartbeat.
- Reconnect attempts use the existing bounded backoff and jitter. Long outages do not end the owner or
  create parallel retry workers. Explicit VPN stop, a different paired identity or revocation ends it.
- In the shared registration-backed owner, a credential rotated during a rejected handshake is
  reread on the next attempt, as is the current saved endpoint. An unchanged
  rejected credential ends recovery; pin validation is never relaxed to regain connectivity.
  The separate iOS extension retains its authenticated start options and stops on revocation.
- The new channel accepts a fresh policy revision sequence. An expired policy is released rather than
  extended by a stale renewal. Lack of native shaping support does not disable presence recovery.

Application requests that fail during an outage are not replayed by this control-channel workflow.
Applications must open new connections. Existing traffic history and saved device identities are
unchanged.

## Displayed state

Android combines the active VPN's control health with the initial proxy-readiness result. Companion
shows Reconnecting during recovery and Connected only after authenticated WELCOME. The Stop control
remains available. Observations from a replaced VPN run cannot clear the newer owner's state.

The iOS extension reports recovery through Network Extension's native `reasserting` property; the host
projects that native status without stopping the VPN. Apple documents this transition in
[NETunnelProvider.reasserting](https://developer.apple.com/documentation/networkextension/netunnelprovider/reasserting).

## Verification

Automated regression and socket qualification passed 306 tests with no failures, errors or skips.
Coverage includes real pinned TLS listener replacement, three fresh desktop presence instances,
connection-local versus owner cancellation, unsupported shaping, silent peers, repeated restarts,
a ten-minute outage, identity changes, credential rotation, revocation and native iOS status changes.
Android APK assembly, iOS device compilation and Simulator framework linking, desktop compilation and
all six architecture checks passed. Module counts and logs are recorded in `implementation_plan.md`.
All socket fixtures use temporary identities and local test listeners; they do not modify the running
desktop, installed Companion or the user's database.

The user confirmed that restart recovery is working on their connected device. The complete physical
matrix below, including abrupt shutdown, longer outages and signed iOS verification, remains separate.

Physical release check:

1. Connect Companion once and leave the VPN enabled. Confirm the device is online on desktop.
2. Quit desktop, wait for Companion to show Reconnecting, then open the same paired desktop again.
   Confirm the same device returns online without toggling VPN or starting capture.
3. Start capture and generate a new request. Confirm it belongs to the existing device tab. Repeat
   with abrupt desktop termination and an outage lasting several minutes.
4. Stop VPN while desktop is unavailable. Restart desktop and confirm Companion stays stopped and
   does not make the device online until the user connects again.

The iOS system status bridge additionally requires a signed Network Extension on a physical device;
Simulator compilation and owner tests do not exercise system entitlement or device lifecycle behavior.
