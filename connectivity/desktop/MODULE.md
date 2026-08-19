# `:connectivity:desktop`

## Responsibility

Implements desktop connectivity mechanisms against `:core:connectivity` and `:application` ports.

## Owns

- Desktop manual-proxy, deterministic PAC, resource-backed typed Apple profile, and ADB reverse providers.
- Versioned desktop network snapshots across IPv4/IPv6/interface/default-route/VPN transitions.
- Bounded setup artifacts and a strict-authority dedicated loopback setup portal.
- Ed25519 pairing crypto, QR/deep-link onboarding support, and the bounded authenticated standard-proxy gateway.
- Automatically managed exact-interface Wi-Fi sharing, open local-client admission with bounded per-source
  quotas, stable setup-page delivery, Android/Apple certificate downloads, and LAN-to-loopback bridging.
- One-shot ingress attribution so paired identity reaches canonical traffic without changing proxy or storage contracts.

## Does not own

- Proxy transport internals, traffic storage, UI, or shared connectivity contracts.
- Android/iOS application code, VPN packet adapters, direct mobile tunnels, or relay carriers.

## Dependency rule

May depend on `:application`, `:core:connectivity`, `:core:traffic`, and `:core:pairing`. It must not depend on UI modules or proxy implementation internals.

## Current state

Manual, PAC, Apple profile, ADB reverse, the setup listeners, desktop network monitoring, pairing security,
authenticated companion gateway, and open Wi-Fi gateway are isolated here. Wi-Fi sharing follows proxy
lifecycle automatically, binds one preferred exact IPv4 interface, and retries recoverable listener failures.
The responsive setup page and Apple root-certificate profile are packaged resources with strict placeholder
validation. Network changes republish descriptors without restarting the proxy or rotating traffic sessions.
The authenticated gateway uses one owned coroutine scope, suspend authentication, bounded admission, and socket closure for deterministic cancellation; it contains no blocking coroutine bridge or private executor lifecycle.
