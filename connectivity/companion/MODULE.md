# `:connectivity:companion`

## Live packet policy and owner cleanup

Android UDP associations own child jobs, bounded delayed work and terminal destination registries. Control close
and VPN teardown cancel both directions, release admission slots, and prohibit late upstream recreation. DNS has
separate bounded admission. Once condition-enabled UDP routing is established, disabling shaping preserves that
route instead of dropping later datagrams. TCP relays retain both halves until their structured copies finish.

The Android forwarder applies authenticated global policy in place, re-planning pending deadlines and fencing
superseded sends. Its policy-refresh coroutine belongs to the foreground service, not a Traffic screen or flow.
Native original-tuple callbacks, packet destination rules and UDP desktop reporting remain incomplete; these changes
do not promote app attribution, QUIC inspection or physical WebRTC support.

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
- iOS Network.framework reachability, Bonjour rediscovery, authenticated `.mobileconfig` retrieval,
  Security-framework certificate readiness, app-foreground trust rechecks, and Network Extension lifecycle.
- iOS pinned companion proxy transport and the start-option boundary into the separately packaged
  `:products:companion:iosPacketTunnel` runtime.
- Platform adapter tests and target compilation for Android, iOS device, and iOS Simulator.

## Package structure

- `connectivity.platform` owns the portable adapter bundle/factory and matching Android/iOS actual factories.
- `connectivity.http` owns the shared Ktor exchange and Android/Darwin engine security providers.
- `connectivity.inspection` owns shared lifecycle reduction and Android/iOS inspection adaptation.
- `connectivity.fallback` owns deterministic fail-closed capabilities for genuinely unavailable integrations.
- `connectivity.network` owns Android and iOS network observation.
- `connectivity.discovery` owns Android DNS-SD/NSD and iOS Bonjour discovery adapters.
- `connectivity.bootstrap` owns portable public-root pinning and complete-invitation redemption.
- `connectivity.control` owns portable pinned-TLS pairing and credential-refresh transport.
- `connectivity.certificate` owns Android/iOS certificate retrieval, installation artifacts, trust verification,
  store/lifecycle observation, X.509 helpers, and bounded TLS adapters.
- `connectivity.transport` owns authenticated Android/iOS carrier boundaries, Android SOCKS ingress, and the
  replaceable Android TUN forwarder.

Source sets identify the native platform; platform-named packages such as `connectivity.android` or
`connectivity.ios` are intentionally avoided. Platform suffixes remain on actual filenames for IDE clarity.

## Does not own

- Shared companion policies, use cases, persistence, presentation state, or Compose UI.
- Android or iOS product composition roots.
- Desktop proxy/capture behavior.
- The Apple packet-tunnel implementation or app-extension entry point; those remain product-owned.

## Dependency rule

Depends only on shared companion application/core contracts, `:core:logger`, Ktor client core, platform Ktor
engines, and native platform SDK APIs. Android and iOS source sets never depend on one another or on the desktop
proxy engine. Custom `X509TrustManager` implementations remain prohibited; Android dynamic paired trust uses
platform trust factories. `commonMain` must not import native APIs, declare a native-context abstraction, or pass a
platform dependency through `Any`. Implementation presence does not by itself promote the product-level Mobile
Companion or VPN capabilities; release maturity remains governed by the runtime catalog and physical-device gates.

## Multi-device workspace and control sessions (2026-11-13)

Native Ktor OkHttp/Darwin clients reuse paired TLS/root/pin validation for persistent VPN-owned control sessions. Session transport exposes bounded typed messages and sanitized failure categories. Android host tests use default Android stub values so actual OkHttp sockets can be exercised without a device.


## Bodyless certificate challenge responses

The shared HTTP client accepts a successful 204 certificate challenge without Content-Length and tolerates
legacy explicit zero lengths. It rejects body bytes, nonzero/malformed lengths and transfer coding on a 204.
Ordinary responses still require a bounded declared length. Root/platform TLS validation, transport pinning,
authentication and exact nonce comparison remain owned by their existing adapters. Common and real native
OkHttp TLS regression fixtures cover the response-framing boundary for both Companion platforms.

## Android VPN socket teardown

The local SOCKS gateway contains expected I/O failures at each accepted connection's coroutine boundary.
VPN stop closes tracked sockets before cancelling workers, so blocked greeting, command, payload and UDP
control reads terminate without reaching Android's uncaught-exception handler. Completion always closes
the client and releases its connection permit. Cancellation and non-I/O programming failures still propagate.
An injectable execution context retains a gateway-owned child job and makes late worker failures observable
in real-socket host tests; it does not change the production dispatcher or connection limits.
