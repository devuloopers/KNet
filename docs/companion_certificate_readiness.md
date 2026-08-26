# Companion Certificate Readiness

## Purpose

The companion must know whether the exact KNet Root CA paired with the active desktop is usable by the platform
TLS policy. KNet does not infer readiness from a downloaded file, an Android Settings broadcast, or private
trust-store enumeration. Readiness is proven by a real TLS handshake and a fresh authenticated challenge.

This design is Android-first and iOS-ready. Shared state, contracts, use cases, presentation behavior, and bounded
Ktor HTTP execution live in Kotlin Multiplatform source sets. Android framework calls remain in
`:connectivity:companion`'s `androidMain`; Darwin/Security trust for bootstrap and control calls lives in `iosMain`.
iOS certificate installation/readiness, network observation, and inspection remain fail-closed until native
adapters and lifecycle notifications are implemented.

## Authoritative flow

1. The version 3 QR carries only a bounded one-time bootstrap, the open public-root endpoint, the pinned TLS
   redemption endpoint, expiry, and independent root/transport SHA-256 pins. Shared Ktor code downloads the public
   root and verifies its exact fingerprint; the active native engine then applies platform PKIX/Security trust.
2. Only after the pinned TLS handshake, fixed-hostname, root-chain, and transport-pin checks succeed does the
   companion send the one-time secret and receive the complete version 3 invitation. The desktop consumes the
   bootstrap atomically; replay and expiry share one rejection.
3. The companion sends proof-bearing pairing completion through the same shared Ktor control transport and native
   trust policy. The desktop consumes the invitation once and returns a bounded credential grant. Public
   registration values are persisted separately from the platform-protected credential.
4. `DownloadCompanionRootCertificateUseCase` reads the active registration and credential ephemerally.
5. Android validates the invitation root's fingerprint, validity, CA constraint, and self-signature, places it in
   an in-memory `KeyStore`, and asks `TrustManagerFactory` for the platform PKIX trust managers. It does not
   implement or suppress a custom `X509TrustManager`.
6. Android opens TLS to the paired address with SNI and HTTPS identity `companion.knet.local`. The credential-bearing
   request is not written until the platform PKIX handshake and hostname verification succeed.
7. The authenticated desktop endpoint returns the exact DER-encoded KNet root. Android validates its validity,
   self-signature, CA constraint, paired root fingerprint, and relationship to the served TLS chain.
8. Download and installation are separate. The user installs or enables the public root through platform UI.
9. `VerifyCompanionCertificateTrustUseCase` confirms the expected root again, creates a cryptographically random
   single-use nonce, and connects with Android's normal TLS trust policy and HTTPS hostname verification.
10. The desktop authenticates the paired credential and `SETUP_ARTIFACT_READ` scope, accepts the nonce once during
   the replay window, and echoes it in the response header.
11. The companion reports `Trusted` only when platform TLS trust, hostname verification, the paired transport pin,
   the exact root, desktop authentication, and nonce echo all succeed.

`CompanionCertificateState` distinguishes `Unknown`, `InstallationRequired`, `Verifying`, `Trusted`, and a typed
`Rejected` failure. Transport connection and inspection readiness remain separate state machines.

## Invitation acquisition

The shared companion UI owns a Navigation 3 camera-scanner stage and consumes only portable scanner states and QR
text. The Android product owns camera permission, CameraX preview lifecycle, and bundled ML Kit QR-only analysis;
no Android camera, image, lifecycle, or context type crosses the shared API. One analyzer frame may be in flight and
one payload may be delivered per scanner composition. The ViewModel accepts camera results only while that route is
active and no earlier result is resolving, then reuses `AcceptPairingInvitationUseCase` for the same version,
expiry, endpoint, and fingerprint validation used by pasted and imported invitations. Imported QR images remain a
separate product effect and use the existing bounded ZXing decoder.

The scanner never opens URLs or interprets arbitrary barcode data. It forwards QR text to the canonical invitation
codec, which accepts only the bounded KNet pairing protocol. Camera frames are neither stored nor transmitted.

## Android behavior

The Android product applies a network security configuration only to `companion.knet.local`. That domain accepts
system and user-installed trust anchors, while unrelated application traffic keeps the platform default policy.
The platform adapter listens for `KeyChain.ACTION_TRUST_STORE_CHANGED`; the event triggers another verification
but never changes state to trusted by itself. If the process was not alive for an event, initial ViewModel
verification on the active registration remains authoritative.

Android does not expose a supported API for reliably enumerating every user-installed CA across versions and
device policies. KNet therefore tests the capability it needs instead of guessing from store contents.

## Desktop endpoint security

The companion control gateway is isolated from the proxy data plane and general setup portal. It uses a KNet-CA-signed
leaf for the fixed server name, TLS 1.2 or newer, bounded concurrent admission, strict bounded HTTP/1.1 parsing,
zero-length requests, scope authentication, no-store responses, one request per connection, bounded nonce
retention, replay rejection, and deterministic lifecycle closure. Public root bytes are copied defensively.

The gateway is currently composed on port `8183`; the paired registration advertises that port as its secure
control endpoint. The desktop Connect Device surface emits the canonical version-3 lightweight bootstrap, serves
the complete invitation once, completes proof-bearing pairing, and rotates authenticated credentials on this same
TLS endpoint. The secured VPN/data-plane adapter remains a separate product integration phase; certificate
readiness intentionally does not treat successful pairing as packet-transport readiness.

## Extension boundary

A future iOS readiness implementation supplies three adapters without changing the already shared Ktor control
transport, use cases, or UI state:

- `CompanionRootCertificateSource` for paired-root confirmation and installation material.
- `CompanionCertificateTrustVerifier` for Apple-policy TLS evaluation and the same nonce proof.
- `CompanionCertificateStoreChangeObserver` for lifecycle or configuration notifications that trigger rechecking.

Other platforms follow the same rule: an event may request verification, but only an end-to-end proof may produce
`Trusted`.

## Qualification

Unit and JVM integration tests cover pairing proof, invitation and credential replay, atomic refresh, credential
scoping, missing/expired credentials, state mapping, trust-store
rechecks, stale ViewModel results, version 3 bootstrap/response round trips, legacy-version rejection, one-time
expiry/replay behavior, PKIX trust rooted only in the paired certificate, malformed invitation-root rejection
before network access, exact-root confirmation, Android
trust rejection, exact nonce echo, authenticated desktop root delivery, replay rejection, nonce-capacity bounds,
and unauthenticated denial. Android, JVM, and iOS Simulator compilation verifies the portable boundaries.
Physical-device evidence for OEM trust behavior remains a release qualification step and is not replaced by local
JVM tests.
