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
