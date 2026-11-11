# `:ui:desktop:protectedTraffic`

## Responsibility

Owns Protected Traffic settings for explicit TLS policy management.

## Owns

- The Protected Traffic settings screen, immutable state, and use-case-driven ViewModel actions.
- Default TLS inspect/tunnel behavior, scope/action explanations, ordered rule presentation, and explicit inspect,
  tunnel, or block editing. This control applies to proxy CONNECT routes, not plain HTTP or UDP/QUIC traffic.
- Exact/wildcard host, application identity, transport, security, protocol, and port selector fields.
- Saved-rule editing and imported legacy exception visibility; no service catalogue or preset toggle controls.

## Does not own

- Policy matching, legacy-data migrations, persistence, source-application verification, TLS interception,
  raw tunneling, canonical Traffic records, navigation shell, or product dependency injection.
- Platform VPN/TUN permissions, certificate trust, application attribution, or packet payload inspection.
- Observed connection lists or decision inspectors. Traffic owns connection evidence and destination actions.

## Dependency rule

May depend on `:application:desktop`, `:core:domain`, and `:ui:core`. It must not depend on `:engine:*`,
`:connectivity:*`, `:data:*`, `:storage`, or `:products:*`.

## Runtime rule

All reads and mutations cross application use cases. `ProtectedTrafficRepository` owns durable configuration;
this settings ViewModel observes configuration without subscribing to runtime connection snapshots. The Traffic
inspector remains independent of whether this screen is open. The UI edits typed policy values and never parses
TLS ClientHello bytes, opens sockets, trusts companion-provided identity without application validation, or
reconstructs opaque payloads.
