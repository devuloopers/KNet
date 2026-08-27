# Companion Connectivity KMP Module

## Purpose

`:connectivity:companion` is the platform-adapter boundary between portable companion application contracts and
native mobile connectivity APIs. It compiles for Android, iOS ARM64, and iOS Simulator ARM64 without allowing one
platform source set to depend on another.

## Source-set ownership

- `commonMain` owns portable application/core contracts, the bounded Ktor HTTP exchange, bootstrap/control
  transports, the adapter-bundle abstraction, the constructor-free `expect` factory declaration, shared
  fail-closed capabilities, and the serialized inspection lifecycle reducer. Native adapters supply Ktor engine
  trust configuration, preparation, start, stop, and failure operations without duplicating request policy,
  locking, idempotency, cancellation recovery, or state transitions.
- `androidMain` owns `ConnectivityManager`, `VpnService` adaptation, `KeyChain`, Java/Android certificate APIs,
  Android TLS, trust-store callback lifecycles, the authenticated proxy carrier, loopback SOCKS ingress,
  TUN-to-SOCKS forwarding, and the Android `actual` factory. Only this source set accepts Android `Context`, and
  it immediately retains the application context.
- `iosMain` owns the Darwin Ktor engine and Security-framework trust evaluation used by invitation redemption and
  control calls. Network observation, certificate installation/readiness, and inspection still use unavailable
  adapters, publish no synthetic readiness, and must not be registered in a production iOS composition root yet.
- `androidHostTest` preserves the Android inspection and real certificate-chain tests after migration from the
  former Android-only child module.
- `iosTest` verifies that incomplete device capabilities fail closed while invalid TLS roots are rejected before
  network access.

## Feature packages

The package hierarchy describes capability ownership rather than repeating the source-set platform:

- `connectivity.platform`: common adapter/factory contracts and the matching Android/iOS actual factories.
- `connectivity.http`: one Ktor request/response policy plus Android and Darwin request-scoped engine providers.
- `connectivity.inspection`: common lifecycle reduction and Android VPN/packet-backend adaptation.
- `connectivity.fallback`: common fail-closed capabilities used by incomplete native implementations.
- `connectivity.network`: Android network observation.
- `connectivity.bootstrap`: portable retrieval of the QR-pinned public root and one-time TLS invitation redemption.
- `connectivity.control`: portable secret-bearing pairing and credential refresh over the paired-root TLS channel.
- `connectivity.certificate`: Android certificate retrieval, platform trust proof, store observation, X.509 helpers,
  paired PKIX construction, and the bounded TLS client.
- `connectivity.transport`: Android pinned-TLS proxy carrier, bounded SOCKS ingress, protected DNS handling, and
  the replaceable TUN-to-SOCKS engine boundary.

The Android TUN path can preserve a destination IP after device DNS resolution even though the originating TLS
ClientHello still carries the application hostname in SNI. The companion forwards that standard CONNECT tunnel
without interpreting TLS. Desktop proxy transport owns the corresponding SNI-aware certificate selection and
keeps the destination IP separate from TLS/HTTP authority metadata.

Bootstrap and control calls use Ktor in `commonMain`; the request policy rejects redirects, requires explicit
bounded response lengths, defensively copies bodies, and creates a fresh native client per request so trust anchors
cannot leak between desktops. The first bootstrap request retrieves only public root material over open HTTP and
checks its SHA-256 fingerprint in common code. Because its LAN IP is discovered from the QR at runtime, the Android
product permits cleartext at the platform boundary; Android network-security XML cannot constrain that dynamic IP
to one path. The shared request model supplies the narrower enforceable boundary: only credential-free `GET` of
the exact `/knet-ca.crt` path and certificate media type, with no custom headers or body and a certificate-sized
response limit. The one-time secret is placed only in the second, pinned-TLS request.

Android trust does not use a hand-written `X509TrustManager`. Its Ktor OkHttp engine maps the fixed
`companion.knet.local` TLS authority to the paired LAN endpoint, rejects the root unless the DER fingerprint and CA
constraints match the QR, places it in an in-memory `KeyStore`, and obtains trust managers from the platform
`TrustManagerFactory`. Certificate readiness additionally performs a fresh exact DER SHA-256 lookup in Android's CA
store before TLS, preventing a resumed session from hiding certificate removal. Darwin creates a Security-framework
certificate and request-scoped custom
anchor for pinned pairing; platform-trusted readiness keeps the system trust policy. Both platforms enforce the
fixed TLS hostname, exact transport identity, and expected root chain before Ktor transmits a secret-bearing body.

The control transport repeats those checks for every pairing or refresh request. Portable code owns bounded typed
request/response mapping; Ktor owns HTTP execution; each native engine owns trust, hostname, and exact
certificate-chain identity enforcement. Native certificate handles are released when the request-scoped client
closes.

Tests mirror these packages, with shared builders isolated under `connectivity.testing`. The `.android.kt` and
`.ios.kt` suffixes remain on actual factory filenames because they improve source-set and IDE navigation without
creating redundant platform packages.

## Platform construction contract

Portable consumers depend on `CompanionPlatformAdapterFactory` and `CompanionPlatformAdapters`. The common
`PlatformCompanionAdapterFactory` declaration intentionally has no constructor. Each `actual` class can therefore
accept only the native dependencies available in its own source set without inventing a common context wrapper,
an `Any` parameter, or a service locator. The product composition root constructs the actual factory and owns the
returned adapter bundle until `close()`.

The root architecture verification rejects Android/iOS imports in `commonMain`, opaque context/handle bridge
parameters in this module, and any future common constructor added to the expected platform factory.

## Product boundary

Android application composition remains in `:products:companion:androidApp`. A future iOS application and Network
Extension product will own their native lifecycles, entitlements, and composition. The future iOS implementation
will replace the unavailable capabilities with `NWPathMonitor`, certificate installation/readiness, lifecycle
recheck triggers, and a separately qualified Network Extension packet backend.

The module does not own pairing persistence, protected credentials, shared UI, desktop proxy behavior, or product
service lifecycle. Those concerns remain behind their existing application, data, presentation, desktop, and
product boundaries. Android and iOS bootstrap/control HTTP transports are implemented. Android has a qualified
packet adapter wired by the Android product; iOS certificate readiness and its Network Extension packet data plane
remain explicit future adapters.

## Qualification

The companion foundation gate runs the Android host tests and compiles the iOS Simulator production target.
Focused Ktor qualification also runs common MockEngine transport tests, Android engine tests, iOS Simulator tests,
and Android/iOS target compilation. No APK or iOS application is assembled or launched by these checks.
