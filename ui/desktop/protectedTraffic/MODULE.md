# `:ui:desktop:protectedTraffic`

## Responsibility

Owns desktop presentation for protected-traffic compatibility policy and service groups.

## Owns

- The Protected Traffic screen, immutable presentation state, and ViewModel actions.
- Default inspect/tunnel behavior, ordered rule presentation, and explicit inspect, tunnel, or block editing.
- Exact/wildcard host, application identity, transport, security, protocol, and port selector fields.
- Built-in Google Play and Apple service-group visibility and user override controls.
- Explanations that distinguish decrypted HTTP exchanges from payload-opaque tunneled flows.

## Does not own

- Policy matching, built-in service definitions, persistence, source-application verification, TLS interception,
  raw tunneling, canonical Traffic records, navigation shell, or product dependency injection.
- Platform VPN/TUN permissions, certificate trust, application attribution, or packet payload inspection.

## Dependency rule

May depend on `:application:desktop`, `:core:domain`, and `:ui:core`. It must not depend on `:engine:*`,
`:connectivity:*`, `:data:*`, `:storage`, or `:products:*`.

## Runtime rule

All reads and mutations cross `ProtectedTrafficRepository`. The UI edits typed policy values and never parses
TLS ClientHello bytes, opens sockets, trusts companion-provided identity without application validation, or
reconstructs opaque payloads.
