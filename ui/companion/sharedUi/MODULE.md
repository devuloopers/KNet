# `:ui:companion:sharedUi`

## Responsibility

Owns the Compose Multiplatform companion interface shared by Android and iOS products.

## Owns

- Common composable screens, responsive layouts, and multiplatform UI resources.
- Serializable Navigation 3 keys, explicit multiplatform saved-state configuration, and state-gated stack
  reconciliation across the inline QR connection flow, certificate setup, and inspection home.
- A portable scanner capability contract whose state, permission actions, and composable preview slot contain no
  Android or Apple framework type; products retain native camera ownership.
- Companion use of the shared `:ui:core` theme, semantic colors, spacing, shapes, and Material bridge without a
  feature-owned palette.
- Rendering of framework-neutral `CompanionUiState` without platform lifecycle or Android framework types.
- A typed certificate-installation guidance model supplied by each product, so the common download/install layout
  is reused without hardcoding Android Settings copy into the shared screen.
- An `iosMain` UIKit controller factory that reactively hosts the same shared UI in the iOS product.
- Pure route and restored-stack mappings that can be verified without a device.
- Certificate setup starts with a checking phase. Exact trusted roots show Verified/Continue with optional file
  details; only missing trust offers download/install guidance. Unavailable/rejected checks expose the reason and
  Retry, without inferring installation state. The shared progress indicator follows verified trust independently
  of whether a file was exported during this session.

## Does not own

- Android activities, manifests, permissions, CameraX/ML Kit analyzers, intents, VPN consent launchers, or process
  composition.
- The iOS application target, Swift entry point, lifecycle composition, signing, or Xcode project registration.
- Companion workflows, repositories, credentials, transports, certificates, or packet capture.
- Desktop proxy, Traffic persistence, or protocol inspection.

## Dependency rule

Depends only on `:ui:core`, companion presentation/core contracts, and portable Compose Multiplatform libraries.
Android and iOS application targets host this module; this module never depends on a product target.
Platform entry points supply observable presentation state, certificate guidance, and native scanner capability
while retaining ownership of their lifecycles.

The production Companion root uses UI core's fixed-dark KNetApplicationTheme. Explicit light previews remain
component qualification surfaces; host appearance does not select the shipped Companion palette.

## Mobile redesign and saved desktops

Uses the approved circular controls, vector brand header, inline desktop selector, compact setup milestones and
reusable status panels. Hero pages scroll on compact/larger-text layouts; instruction pages keep bottom actions
reachable. Busy circles are non-interactive, expose progress semantics and obey the KNet reduced-motion policy. Desktop management, QR add flow, ordered handoff/recovery, forget confirmation and the
app-owned VPN explanation render typed presentation state. Platform-specific certificate instructions remain typed
as Android/iOS guidance. Large bitmap heroes and their unused packaged resources have been removed.
See `docs/companion_ui.md` for screen coverage, workflow behavior and qualification boundaries.

Scan enters native permission directly. Denials keep Connect visible, and an explicit later tap opens the app-owned
Settings sheet when prompting is blocked. Pending intent is saveable; native permission state remains product-owned.
The shared QR-image secondary button stays available without camera access. See `docs/companion_ui.md` for the
permission state boundary and remaining physical checks.
