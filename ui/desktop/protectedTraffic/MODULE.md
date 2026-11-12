# `:ui:desktop:protectedTraffic`

## Responsibility

Owns Protected Traffic settings for explicit TLS policy management.

## Owns

- Protected Traffic title, description and default-policy controls composed in UI core's shared screen layout;
  page insets, typography, header alignment and narrow-width action placement remain design-system owned.

- The Protected Traffic settings screen, immutable state, and use-case-driven ViewModel actions.
- Default TLS inspect/tunnel behavior, scope/action explanations, ordered rule presentation, and explicit inspect,
  tunnel, or block editing. This control applies to proxy CONNECT routes, not plain HTTP or UDP/QUIC traffic.
- Exact/wildcard host, IP/CIDR, application identity, transport, address-family and port selector fields.
- Searchable, virtualized manager table with per-rule enable, edit and delete actions, plus a shared animated drawer.
- Saved-rule editing and imported legacy exception visibility; no service catalogue or preset toggle controls.
  Unchanged imported saves/toggles retain provenance; match/action edits become user rules under the same identity.

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

## Rule manager interaction

Literal, case-insensitive multi-term search filters presentation only; it preserves policy order and includes rules
saved from Traffic through the observed repository configuration. Fields retain wildcard/all-port/all-transport and
address-family scope when editing. The editor uses the latest saved identity, keeps validation and persistence
failures inline, and prevents duplicate saves or replacing/dismissing an in-flight save. Per-rule mutations and
default handling retain their own pending state. A deleted draft cannot silently recreate a removed rule.

`:ui:core` owns manager density/minimum width, pinned table scrollbars, compact layout classification and the
animated drawer shell with independently scrolling content and fixed actions. Connection diagnostics remain in
Traffic, and saved changes apply to new connections. See `docs/protected_traffic_rule_manager.md` for qualification.
The table supplies a separate header slot to the shared viewport; the full-width header surface remains above
the row-only scrollbar track, with a single horizontal scroll owner preserving column alignment.

The default dropdown offers Inspect with automatic fallback, Inspect only, Tunnel and Block. The preference is
persisted atomically through the application use case; explicit rule actions retain their existing values. Temporary
learning has no preset catalogue, observed-connection screen or persistent rule row. See `docs/automatic_tls_fallback.md`.

The shared dropdown exposes KNet-themed hover descriptions for its selected mode and all four menu
choices. Copy distinguishes client reconnect from replay, describes encrypted metadata, and explains saved-rule
precedence and new-connection scope. These descriptions do not mutate policy or change field/menu dimensions.
Hover help dismisses immediately when the pointer leaves the control/row, even if it retains focus; keyboard
navigation retains accessible descriptions without opening a hover popup.
