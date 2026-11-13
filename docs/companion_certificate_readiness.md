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
5. After pairing, `VerifyCompanionCertificateTrustUseCase` retrieves authenticated root material in memory and
   checks the exact paired root against platform trust and the challenge below. Already-trusted users can continue
   without another exported file; unavailable checks offer Retry instead of assuming installation is missing.
6. Only when setup is required, `DownloadCompanionRootCertificateUseCase` reads the active registration and
   credential ephemerally. Android exports the exact DER `.crt`; iOS downloads and validates a `.mobileconfig`
   containing the expected `com.apple.security.root` payload. Mismatched bytes are rejected.
7. The user completes the system-owned installation/trust flow. Download and installation are separate states.
8. `VerifyCompanionCertificateTrustUseCase` confirms the expected root again. Android first requires an exact
   `AndroidCAStore` fingerprint match. iOS evaluates the platform-trusted chain through the Security framework.
9. The verifier creates a cryptographically random single-use nonce and connects using the platform's normal trust
   policy and HTTPS hostname verification.
10. The desktop authenticates the paired credential and `SETUP_ARTIFACT_READ` scope, consumes the nonce once during
    the replay window, and echoes it in the response header.
11. The companion reports `Trusted` only when platform TLS trust, hostname verification, paired root/transport
    identity, desktop authentication, and exact nonce echo all succeed.

## Verification-first onboarding

After pairing, certificate setup renders Checking while the existing verifier checks the active desktop's root.
An already-trusted exact root opens the Verified/Continue phase without downloading or exporting another file.
Missing trust offers download and the platform-owned installation steps. Deferred or rejected checks show their
reason and Retry; a desktop/network failure is never treated as proof that the certificate is missing.

Download/export remains separate from installation and trust. A downloaded file cannot enable Continue, and its
details are optional on the Verified screen. Explicit Continue re-verifies the root and records durable enrollment;
it does not start inspection or grant VPN consent. Pending verification is fenced by attempt, desktop identity and
root fingerprint, so cancelled or late results cannot overwrite a newer check or a replacement certificate.
An authoritative removal/rejection reopens setup even after enrollment. Temporary unavailability preserves an
already enrolled Home, with inspection readiness still checked independently.

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

The verification-first onboarding update on 2026-11-12 added 21 cases covering existing trust without export,
first installation, retry, repeated QR pairing, certificate removal/replacement, stale results, duplicate Continue,
incomplete verifier results, enrollment persistence failure and common rendered screens. All 440 tests across
shared workflows/persistence/UI, Android host/product and iOS Simulator gates passed; the Android debug APK
assembled and the iOS Simulator product framework linked. Product apps were not installed or launched.
See /tmp/knet-companion-certificate-check-qualification.log for this local qualification run.

Unit and host integration tests cover invitation/credential replay, proof and scope validation, atomic refresh,
state mapping, stale-result rejection, PKIX/root-pin validation, authenticated root delivery, trust-store rechecks,
nonce echo/replay bounds, and unauthenticated denial. The companion gates also compile Android and iOS production
targets and link the iOS Simulator framework.

Those automated checks do not install or launch a mobile app. A manual physical Android end-to-end smoke test has
verified the companion VPN inspection path, so Android Companion and Android VPN inspection are cataloged as
experimental. Android OEM/version coverage, iOS profile trust, and the entitlement-signed iOS packet tunnel remain
release qualification steps; physical iOS packet-tunnel inspection stays unavailable in the runtime catalog.


## Bodyless challenge response interoperability (2026-11-13)

A connected Android device reported `transport_unavailable`, `trust_mode=platform_trusted`, and
`reason=IllegalArgumentException` even though its exact installed-root lookup succeeded. The shared client
required Content-Length on every response. After the control gateway moved to Ktor/Netty, successful certificate
challenges returned HTTP 204 with no Content-Length. The old socket gateway included an explicit zero length,
which hid this incompatibility in earlier fixtures.

[HTTP/1.1 framing, RFC 9112 section 6.3](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3) defines 204 responses
as bodyless, and [RFC 9110 section 8.6](https://www.rfc-editor.org/rfc/rfc9110.html#section-8.6) prohibits a server
from sending Content-Length on a 204. The shared client now accepts the absent header for 204, tolerates legacy
explicit zero, and rejects nonzero/malformed lengths, transfer coding and observable body bytes. Other responses
retain required declared-length and size checks. TLS trust, certificate pins, authentication and nonce echo
validation are unchanged.

Added common response-framing cases, real OkHttp TLS interoperability tests, and a Ktor desktop challenge test
covering the absent header, nonce echo and replay rejection. Verification passed 173 automated executions:
93 Android transport cases, 51 desktop cases and 29 iOS Simulator cases, with zero failures/errors/skips.
All six architecture checks, Android APK assembly and iOS device/Simulator compilation passed.

The updated APK's signing identity was checked against the installed application before an in-place update
on the connected Samsung SM-A505F (Android API 31). Pairing was retained. The real app changed from
Couldn’t verify certificate to Certificate verified / Installed and trusted, and logged
`verification_completed result=trusted`. It was left at Continue; VPN activation was not started.
Logs: `/Users/devuloopers/.codex/verification/knet-certificate-204/qualification-final.log` and
`/Users/devuloopers/.codex/verification/knet-certificate-204/device-verification.log`.
