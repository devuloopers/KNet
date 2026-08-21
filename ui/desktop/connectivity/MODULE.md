# `:ui:desktop:connectivity`

## Responsibility

Owns the desktop **Connect Device** presentation for the automatically managed Wi-Fi proxy setup path.

## Owns

- Immutable presentation state and focused intents for opening the setup drawer and starting the proxy.
- A top-left wrapping connectivity-method card grid whose first entry is Wi-Fi Proxy Setup.
- Feature-owned Wi-Fi drawer content using the shared `:ui:core` shell, stable QR rendering, manual endpoint,
  prerequisite guidance, Android/iPhone instructions, connection verification, certificate compatibility notes,
  hover-aware close interaction, and lifecycle feedback. Setup-specific guidance stays private to this feature.
- Inline card/drawer failure feedback derived from typed operation state and typed Wi-Fi runtime failures,
  including a non-terminal listener recovery presentation. The feature does not render a separate screen-level
  notification banner for successful proxy startup.
- Mapping application/core connectivity state into user-facing labels and recoverable actions.

## Does not own

- LAN listeners, setup-page delivery, proxy transport, certificates, PAC generation,
  traffic capture/storage, persistence, navigation shell, or product dependency injection.
- Android/iOS companion application behavior or OS VPN integrations.

## Dependency rule

May depend on `:application`, `:core:connectivity`, `:core:domain`, and `:ui:core`. It must not depend on
`:connectivity:desktop`, `:engine:*`, `:data:*`, `:storage`, or `:products:*`.

## Lifecycle rule

Presentation invokes application use cases only. Leaving the screen does not stop sharing. Proxy stop,
network loss, or product shutdown owns resource closure, and none of those actions clear captured traffic.
