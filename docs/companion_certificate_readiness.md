# Companion Certificate Readiness

## Purpose

The companion must know whether the exact KNet Root CA paired with the active desktop is installed and usable by
the active platform TLS policy. KNet does not infer readiness from a downloaded file or a platform lifecycle event.
Both products require a real platform-trusted TLS handshake with a fresh authenticated challenge. Android also
performs an exact DER SHA-256 lookup in the current CA store before that handshake; iOS relies on
Security-framework system trust and rechecks when the app returns to the foreground.

Shared state, contracts, use cases, presentation behavior, and bounded Ktor HTTP execution live in Kotlin
Multiplatform source sets. Android framework calls remain in `androidMain`; Darwin, Security, UIKit lifecycle, and
Network Extension calls remain in `iosMain` or the iOS product targets.

## Authoritative flow

1. The version 3 QR carries a bounded one-time bootstrap, open public-root endpoint, pinned TLS redemption
   endpoint, expiry, and independent root/transport SHA-256 pins.
2. Shared Ktor code downloads only public root material over the credential-free bootstrap request and verifies its
   exact fingerprint before any secret-bearing request is sent.
3. The native transport validates trust. Android creates an in-memory `KeyStore` and asks
   `TrustManagerFactory` for PKIX trust managers; iOS configures a request-scoped Security-framework custom anchor.
   Both enforce the fixed `companion.knet.local` hostname, expected root chain, and transport identity.
4. Pairing completion proves the device identity over pinned TLS. The desktop consumes the invitation once and
   returns a bounded credential grant. Public registration values are persisted separately from the
   platform-protected credential.
5. `DownloadCompanionRootCertificateUseCase` reads the active registration and credential ephemerally. The
   authenticated endpoint returns the exact DER root; mismatched bytes are rejected.
6. The installation artifact is platform-specific. Android exports the DER `.crt`; iOS downloads and validates a
   `.mobileconfig` containing the expected `com.apple.security.root` payload.
7. The user completes the system-owned installation/trust flow. Download and installation are separate states.
8. `VerifyCompanionCertificateTrustUseCase` confirms the expected root again. Android first requires an exact
   `AndroidCAStore` fingerprint match. iOS evaluates the platform-trusted chain through the Security framework.
9. The verifier creates a cryptographically random single-use nonce and connects using the platform's normal trust
   policy and HTTPS hostname verification.
10. The desktop authenticates the paired credential and `SETUP_ARTIFACT_READ` scope, consumes the nonce once during
    the replay window, and echoes it in the response header.
11. The companion reports `Trusted` only when platform TLS trust, hostname verification, paired root/transport
    identity, desktop authentication, and exact nonce echo all succeed.

`CompanionCertificateState` distinguishes `Unknown`, `InstallationRequired`, `Verifying`, `Trusted`,
`VerificationDeferred`, and typed rejection. Desktop connection and inspection readiness remain separate state
machines.

## Invitation acquisition

The shared companion UI owns a Navigation 3 scanner stage and consumes only portable scanner states and QR text.
Android owns camera permission, CameraX preview lifecycle, bundled ML Kit QR-only analysis, and its bounded image
decoder. iOS owns AVFoundation capture plus the PHPicker/Core Image image-decoding effect. No Android or Apple
camera, image, lifecycle, or context type crosses the shared API.

The ViewModel accepts camera results only while the scanner route is active and no earlier result is resolving,
then reuses the canonical invitation use case for the same version, expiry, endpoint, and fingerprint checks used
by imported images. The scanner never opens URLs or interprets arbitrary barcode data. Camera frames are neither
stored nor transmitted.

## Android behavior

The Android product applies a network security configuration only to `companion.knet.local`. That domain accepts
system and user-installed anchors, while unrelated application traffic keeps the platform default policy. The
platform adapter listens for `KeyChain.ACTION_TRUST_STORE_CHANGED`; the event triggers verification but never marks
the certificate trusted by itself.

The readiness gate enumerates `AndroidCAStore` and compares the full certificate fingerprint on every verification,
so a previous TLS session cannot hide a removed certificate. Unsupported provider behavior fails closed.

## iOS behavior

The iOS product downloads the authenticated `.mobileconfig` and hands it to the platform document/profile flow.
`IosCertificateStoreChangeObserver` triggers a recheck when the app becomes active; that event never marks trust by
itself. `IosCompanionCertificateTrustVerifier` reports `Trusted` only after Security-framework system trust,
hostname validation, paired identity checks, desktop authentication, and exact nonce echo succeed.

## Desktop endpoint security

The companion control gateway is isolated from the proxy data plane and general setup portal. It uses a KNet-CA
signed leaf for the fixed server name, TLS 1.2 or newer, bounded admission, strict bounded HTTP/1.1 parsing,
scope authentication, no-store responses, bounded nonce retention, replay rejection, and deterministic closure.
The gateway is currently composed on port `8183`; the active registration advertises that secure control endpoint.

Certificate readiness intentionally remains separate from packet-transport readiness. Android `VpnService` and
iOS Network Extension lifecycles have their own preparation, consent, start, failure, and stop states.

## Qualification boundary

Unit and host integration tests cover invitation/credential replay, proof and scope validation, atomic refresh,
state mapping, stale-result rejection, PKIX/root-pin validation, authenticated root delivery, trust-store rechecks,
nonce echo/replay bounds, and unauthenticated denial. The companion gates also compile Android and iOS production
targets and link the iOS Simulator framework.

Those automated checks do not install or launch a mobile app. A manual physical Android end-to-end smoke test has
verified the companion VPN inspection path, so Android Companion and Android VPN inspection are cataloged as
experimental. Android OEM/version coverage, iOS profile trust, and the entitlement-signed iOS packet tunnel remain
release qualification steps; physical iOS packet-tunnel inspection stays unavailable in the runtime catalog.
