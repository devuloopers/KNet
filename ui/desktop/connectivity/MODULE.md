# `:ui:desktop:connectivity`

## Responsibility

Owns the desktop **Connect Device** presentation for browser-oriented Wi-Fi setup and authenticated companion
onboarding.

## Owns

- Immutable presentation state and focused intents for selecting one connectivity drawer, starting the proxy,
  and creating or refreshing an ephemeral companion invitation.
- A top-left wrapping connectivity-method card grid whose entries currently cover Wi-Fi Proxy Setup and
  Connect Companion App without hard-coding a single-card layout.
- Feature-owned Wi-Fi drawer content using the shared `:ui:core` shell, stable QR rendering, manual endpoint,
  prerequisite guidance, Android/iPhone instructions, connection verification, certificate compatibility notes,
  hover-aware close interaction, and lifecycle feedback. Setup-specific guidance stays private to this feature.
- Inline card/drawer failure feedback derived from typed operation state and typed Wi-Fi runtime failures,
  including a non-terminal listener recovery presentation. The feature does not render a separate screen-level
  notification banner for successful proxy startup.
- Feature-owned companion onboarding presentation using the same shared drawer shell: canonical version-2 QR,
  reachable desktop details, expiry countdown, refresh/recovery actions, and ordered setup guidance. Narrative
  guidance wraps within the drawer while compact identifiers and endpoints remain single-line. The secret-bearing
  QR exists only in ViewModel memory and is removed when the drawer closes or the invitation expires.
- Mapping application/core connectivity state into user-facing labels and recoverable actions.

## Does not own

- LAN listeners, setup-page delivery, proxy transport, certificates, PAC generation,
  traffic capture/storage, persistence, navigation shell, or product dependency injection.
- Android/iOS companion application behavior, the authenticated control/data transports, or OS VPN integrations.

## Dependency rule

May depend on `:application:desktop`, `:core:connectivity`, `:core:domain`, and `:ui:core`. It must not depend on
`:connectivity:desktop`, `:engine:*`, `:data:*`, `:storage`, or `:products:*`.

## Lifecycle rule

Presentation invokes application use cases only. Leaving the screen does not stop sharing. Proxy stop,
network loss, or product shutdown owns resource closure, and none of those actions clear captured traffic.
