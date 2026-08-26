# `:ui:companion:sharedUi`

## Responsibility

Owns the Compose Multiplatform companion interface shared by Android and future iOS products.

## Owns

- Common composable screens, responsive layouts, and multiplatform UI resources.
- Companion use of the shared `:ui:core` theme, semantic colors, spacing, shapes, and Material bridge without a
  feature-owned palette.
- Rendering of framework-neutral `CompanionUiState` without platform lifecycle or Android framework types.
- An `iosMain` UIKit controller factory that reactively hosts the same shared UI for a future iOS product.
- Small pure presentation mappings that can be verified without a device.

## Does not own

- Android activities, manifests, permissions, intents, VPN consent launchers, or process composition.
- An iOS application target, Swift entry point, lifecycle composition, signing, or Xcode project registration.
- Companion workflows, repositories, credentials, transports, certificates, or packet capture.
- Desktop proxy, Traffic persistence, or protocol inspection.

## Dependency rule

Depends only on `:ui:core`, companion presentation/core contracts, and portable Compose Multiplatform libraries.
Android and future iOS application targets host this module; this module never depends on a product target.
Platform entry points supply observable presentation state and retain ownership of its lifecycle.
