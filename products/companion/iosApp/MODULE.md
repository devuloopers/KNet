# `:products:companion:iosApp`

## Responsibility

Owns the installable iOS companion application, SwiftUI lifecycle, Xcode packaging, and iOS product composition root.

## Layout

- `src/iosMain` owns the Kotlin/Native product composition and generated Compose resources. Its package layout
  follows the desktop product convention: root entry point, `bootstrap`, `di`, `platform`, and `runtime`.
- `app` owns the SwiftUI application host, application assets, plist, and entitlements.
- `packetTunnelExtension` owns the Apple Network Extension entry point, plist, and entitlement. The tunnel
  implementation itself lives in `:products:companion:iosPacketTunnel`.
- `KNetCompanion.xcodeproj` packages the application and packet-tunnel targets.
- `scripts` owns reproducible native dependency build integration.

## Owns

- The SwiftUI `App` entry point and UIKit bridge into the static `KNetCompanionIos` Kotlin/Native framework.
- iOS application identity, privacy descriptions, Bonjour declarations, deployment settings, and app icon assets.
- iOS Koin prerequisites for qualified persistence, Keychain identity, Ktor Darwin transport, Apple profile
  download, and Bonjour discovery adapters. Portable repository, client, use-case, and ViewModel definitions come
  from `:products:companion:di`.
- The `NEPacketTunnelProvider` extension, its app-extension entitlement, and the product boundary that passes one
  authenticated in-memory tunnel session to it.
- The pinned Hev SOCKS packet engine build integration and the native local SOCKS-to-KNet-proxy bridge used only by
  the packet-tunnel extension.
- AVFoundation QR capture and UIKit certificate-profile export effects that are invoked through shared UI contracts.
- Stable bootstrap, loading, and failure surfaces while DataStore-backed state is restored asynchronously.
- Native one-shot effect handling. Unsupported packet classes are rejected explicitly instead of bypassing the
  inspection route.

## Does not own

- Shared companion models, use cases, state, screens, illustrations, theme, or persistence formats.
- Android VPN, CameraX, Android Keystore, or Android certificate installation behavior.
- Desktop proxy behavior, pairing authority, certificate issuance, or traffic persistence.
- Persistent storage of the pairing credential in the Network Extension profile. The extension receives secrets
  only in ephemeral start options after the app has authenticated the paired desktop.

## Dependency rule

May depend on companion application, data, presentation, shared UI, iOS connectivity, core UI, core models,
`:products:companion:di`, and Koin solely to compose the executable Apple product. Reusable modules must not depend
on this product.

## Native qualification

- Simulator compilation and KMP tests validate source and contract integration.
- A signed physical-device build with the `packet-tunnel-provider` capability is required to exercise VPN consent,
  packet routing, profile installation, and background extension lifecycle before release.
- `scripts/build-hev-socks5-tunnel.sh` pins the official Hev source revision and verifies that revision before every
  uncached native build.
