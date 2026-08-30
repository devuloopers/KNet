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
- `iosMain` owns the Darwin Ktor engine and Security-framework trust evaluation, Network.framework reachability,
  Bonjour rediscovery, authenticated Apple profile retrieval, certificate readiness and foreground rechecks,
  pinned proxy transport, and Network Extension lifecycle controller.
- `androidHostTest` preserves the Android inspection and real certificate-chain tests after migration from the
  former Android-only child module.
- `iosTest` verifies Darwin trust/pinning behavior and portable failure handling without treating simulator tests as
  packet-tunnel device evidence.

## Feature packages

The package hierarchy describes capability ownership rather than repeating the source-set platform:

- `connectivity.platform`: common adapter/factory contracts and the matching Android/iOS actual factories.
- `connectivity.http`: one Ktor request/response policy plus Android and Darwin request-scoped engine providers.
- `connectivity.inspection`: common lifecycle reduction plus Android VPN and iOS Network Extension adaptation.
- `connectivity.fallback`: common fail-closed capabilities retained for genuinely unavailable integrations.
- `connectivity.network`: Android and iOS network observation.
- `connectivity.discovery`: Android DNS-SD/NSD and iOS Bonjour discovery.
- `connectivity.bootstrap`: portable retrieval of the QR-pinned public root and one-time TLS invitation redemption.
- `connectivity.control`: portable secret-bearing pairing and credential refresh over the paired-root TLS channel.
- `connectivity.certificate`: Android/iOS certificate and installation-artifact retrieval, platform trust proof,
  store/lifecycle observation, X.509 helpers, paired trust construction, and bounded TLS clients.
- `connectivity.transport`: Android/iOS pinned proxy carrier boundaries, Android bounded SOCKS ingress, protected
  DNS handling, and the replaceable Android TUN-to-SOCKS engine boundary.

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

Android application composition remains in `:products:companion:androidApp`. The iOS application owns its SwiftUI
and Xcode lifecycle in `:products:companion:iosApp`; the lean Kotlin/Native packet runtime lives in
`:products:companion:iosPacketTunnel`, with Swift limited to the Apple-required extension entry shim. The signed
app and packet-tunnel targets own their entitlements and native lifecycle.

The module does not own pairing persistence, protected credentials, shared UI, desktop proxy behavior, the Apple
packet-engine implementation, or product service lifecycle. Those concerns remain behind their existing
application, data, presentation, desktop, and product boundaries. Android and iOS bootstrap/control, certificate,
discovery, and inspection-controller adapters are implemented. Android Companion and Android VPN inspection have
passed a manual physical-device end-to-end smoke test and are experimental pending a broader device and lifecycle
matrix. The iOS/iPadOS app is experimental; physical packet-tunnel inspection remains unavailable pending
entitlement-signed device qualification.

## Qualification

The companion foundation gate runs portable/JVM tests, Android host tests, iOS Simulator tests, and Android/iOS
production-target compilation. The Android product gate adds unit tests, lint, and debug APK assembly. The iOS
product gate links the iOS Simulator framework only: it does not build/sign the Xcode application, launch it, or
exercise `NEPacketTunnelProvider`. Physical Android behavior and a paid-team, entitlement-signed iOS device build
remain release gates.
