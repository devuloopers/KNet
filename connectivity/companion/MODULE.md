# `:connectivity:companion`

## Responsibility

Owns multiplatform companion connectivity adapters for Android and iOS. Shared source sets contain the bounded
Ktor HTTP exchange and platform-neutral adapter orchestration; native certificate trust, VPN, network, and
lifecycle APIs remain in their platform source sets.

## Owns

- A platform-neutral, serialized inspection lifecycle reducer in `commonMain`.
- Platform-neutral adapter-bundle and factory contracts plus a constructor-free `expect` factory boundary.
- Android network observation, VPN consent/lifecycle adaptation, and certificate readiness proof.
- Android pinned-TLS companion proxy transport, loopback SOCKS ingress, and TUN-to-SOCKS forwarding boundary.
- A fail-closed Android UDP policy: protected direct DNS is supported, while unsupported UDP is rejected by
  default so QUIC can fall back to inspectable TCP/TLS.
- Shared Ktor bootstrap and control transports with one bounded request/response policy, typed operations,
  redirect rejection, defensive body handling, and request-scoped clients.
- A root-bootstrap-only cleartext capability for the QR-discovered LAN endpoint: exact credential-free root GET,
  certificate media type and response bound, followed by common QR fingerprint verification before pinned TLS.
- Android Ktor OkHttp-engine TLS configuration that resolves the fixed `companion.knet.local` authority to the
  paired LAN endpoint, then applies platform PKIX plus QR-root, root-chain, and exact transport-identity checks
  before a secret-bearing request is transmitted.
- Fresh exact DER SHA-256 lookups in Android's CA store before the TLS readiness challenge.
- Secret-free transport, root-validation, and trust-challenge diagnostics under the shared certificate log tag.
- iOS Darwin-engine TLS configuration using Security-framework policies, request-scoped custom anchors for
  pairing, native system trust for platform-trusted requests, and the same root/identity checks.
- Fail-closed iOS network, certificate-installation/readiness, and Network Extension capabilities until those
  native adapters and an iOS product are implemented and qualified.
- Platform adapter tests and target compilation for Android, iOS device, and iOS Simulator.

## Package structure

- `connectivity.platform` owns the portable adapter bundle/factory and matching Android/iOS actual factories.
- `connectivity.http` owns the shared Ktor exchange and Android/Darwin engine security providers.
- `connectivity.inspection` owns shared lifecycle reduction and Android inspection adaptation.
- `connectivity.fallback` owns shared deterministic unavailable capabilities.
- `connectivity.network` owns Android network observation.
- `connectivity.bootstrap` owns portable public-root pinning and complete-invitation redemption.
- `connectivity.control` owns portable pinned-TLS pairing and credential-refresh transport.
- `connectivity.certificate` owns Android certificate retrieval, trust, store observation, X.509, and TLS adapters.
- `connectivity.transport` owns the Android authenticated carrier, SOCKS ingress, and replaceable TUN forwarder.

Source sets identify the native platform; platform-named packages such as `connectivity.android` or
`connectivity.ios` are intentionally avoided. Platform suffixes remain on actual filenames for IDE clarity.

## Does not own

- Shared companion policies, use cases, persistence, presentation state, or Compose UI.
- Android or iOS product composition roots.
- Desktop proxy/capture behavior.
- A production iOS connectivity claim while the shared unavailable capabilities remain active.

## Dependency rule

Depends only on shared companion application/core contracts, `:core:logger`, Ktor client core, platform Ktor
engines, and native platform SDK APIs. Android and iOS source sets never depend on one another or on the desktop
proxy engine. Custom `X509TrustManager` implementations remain prohibited; Android dynamic paired trust uses
platform trust factories. `commonMain` must not import native APIs, declare a native-context abstraction, or pass a
platform dependency through `Any`.
