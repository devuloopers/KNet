# `:connectivity:desktop`

## Responsibility

Implements desktop connectivity mechanisms against `:core:connectivity` and `:application:desktop` ports.

## Owns

- Desktop manual-proxy, deterministic PAC, resource-backed typed Apple profile, and ADB reverse providers.
- Versioned desktop network snapshots across IPv4/IPv6/interface/default-route/VPN transitions.
- Bounded setup artifacts and a strict-authority dedicated loopback setup portal.
- Algorithm-bound Ed25519/P-256 pairing crypto, QR/deep-link onboarding support, and the bounded authenticated
  standard-proxy gateway.
- A bounded process-local onboarding store plus a one-time, secret-authenticated redemption route on the companion
  TLS gateway; invalid, expired, and replayed references share one non-descriptive rejection.
- A bounded companion control TLS gateway that completes proof-bearing pairing, issues and atomically rotates
  credentials, serves the exact KNet root, and accepts each certificate-readiness nonce once during a bounded
replay window.
- HTTP/1.1 request framing compatible with bodyless Ktor `GET` requests: an absent content length means no body,
  while malformed, oversized, duplicate, and transfer-encoded framing remains fail-closed.
- Automatically managed exact-interface Wi-Fi sharing, open local-client admission with bounded per-source
  quotas, stable setup-page delivery, Android/Apple certificate downloads, and LAN-to-loopback bridging.
- One-shot ingress attribution so paired identity reaches canonical traffic without changing proxy or storage contracts.

## Does not own

- Proxy transport internals, traffic storage, UI, or shared connectivity contracts.
- Android/iOS application code, VPN packet adapters, direct mobile tunnels, or relay carriers.

## Dependency rule

May depend on `:application:desktop`, `:core:connectivity`, `:core:traffic`, `:core:pairing`, and the portable
`:core:companion` wire vocabulary. It must not depend on UI modules, logging implementations, or proxy
implementation internals. Typed diagnostic callbacks are supplied by the product composition root.

## Current state

Manual, PAC, Apple profile, ADB reverse, the setup listeners, desktop network monitoring, pairing security,
authenticated companion gateway, and open Wi-Fi gateway are isolated here. Wi-Fi sharing follows proxy
lifecycle automatically, binds one preferred exact IPv4 interface, and publishes listeners transactionally.
Transient process-handover conflicts use safe TCP rebinding, bounded fast retries, generation-based lifecycle
cancellation, and an ordered setup-portal port fallback before settling into slow background recovery. Actual
platform exceptions remain in logs while only typed, presentation-safe failures cross the module boundary.
The responsive setup page and Apple root-certificate profile are packaged resources with strict placeholder
validation. Network changes republish descriptors without restarting the proxy or rotating traffic sessions.
The authenticated proxy and control gateways use owned coroutine scopes, suspend authentication, bounded
admission, and socket closure for deterministic cancellation; neither contains a blocking coroutine bridge or
private executor lifecycle. The control gateway bounds requests and retained nonces, gives invalid pairing inputs
one generic rejection, consumes invitations once, rotates credentials with compare-and-set storage, and rejects
old-credential replay. It also redeems complete companion invitations only after TLS and an independently digested
one-time bootstrap secret, while the open Wi-Fi portal continues to expose only public root-certificate material.
