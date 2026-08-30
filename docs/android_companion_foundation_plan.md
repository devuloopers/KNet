# Android Companion Foundation

> [!NOTE]
> This is a dated 2026-08-26 foundation snapshot. The Android and iOS products, certificate adapters, and tunnel
> implementations were added later. Use the [documentation map](README.md), current module contracts, and runtime
> capability catalog for present status; companion/VPN remains pre-release until promoted by release evidence.

- **Status:** Shared foundation, Compose Multiplatform Android shell, Android connectivity/certificate boundaries,
  and fail-closed iOS connectivity placeholders implemented; transport, complete workflow UI, and packet/tunnel
  backends not started
- **Updated:** 2026-08-26
- **Scope:** Android-first companion application logic that also compiles for iOS, without changing desktop proxy, traffic, PAC, manual Wi-Fi, or protocol-inspector architecture

## Outcome

This increment establishes the portable companion model, workflows, persistence, presentation state, Android
security/connectivity boundaries, shared Compose Multiplatform UI, and a real installable Android product shell.
The shell composes only production implementations that exist today and reports their status; it does not simulate
a working transport, certificate flow, or VPN/tunnel. Those runtime capabilities will be added only with real implementations.

The companion remains a connectivity client. It does not parse proxy traffic, own traffic persistence, write Room
traffic rows, or duplicate HTTP/gRPC/WebSocket/SSE models. A future Android data plane forwards authenticated proxy
streams to the existing desktop companion gateway, which then enters the same proxy and canonical traffic pipeline.

## Implemented modules

| Module | Responsibility | Platforms |
|---|---|---|
| `:core:companion` | Validated invitation, desktop registration, endpoint, connection, certificate, inspection, network, and failure models | JVM, Android, iOS |
| `:application:companion` | Pair/select/connect/recover/disconnect, certificate, credential rotation, VPN preparation/start/stop, and forget workflows plus ports | JVM, Android, iOS |
| `:data:companion` | Versioned non-secret registration persistence, protected-secret adapter, strict invitation codec, and shared pinned-control protocol client | JVM, Android, iOS; Android adapters in `androidMain` |
| `:ui:companion:presentation` | Framework-neutral state, actions, effects, and lifecycle-owned shared ViewModel | JVM, Android, iOS |
| `:ui:core` | Shared Compose theme, semantic tokens, resources, and adaptive visual components | JVM, Android, iOS |
| `:ui:companion:sharedUi` | Shared Compose Multiplatform screens and localized UI resources | JVM, Android, iOS |
| `:connectivity:companion` | KMP connectivity boundary with qualified Android network/certificate/VPN lifecycle adapters and fail-closed iOS placeholders | Android, iOS |
| `:products:companion:androidApp` | APK, manifest, thin Compose host lifecycle, and product composition for currently implemented Android adapters | Android |

`core:identity`, `core:pairing`, and `core:connectivity` now compile for Android and iOS as portable prerequisites.
The pairing transcript explicitly binds its proof algorithm. Desktop verification supports Ed25519 and Android-safe
P-256 ECDSA without weakening existing Ed25519 clients.

## Dependency direction

```text
Android product shell
    -> :ui:companion:sharedUi -> :ui:core + :ui:companion:presentation
    -> Android composition adapters

:ui:companion:sharedUi       -> :ui:core + :ui:companion:presentation + :core:companion
:ui:companion:presentation -> :application:companion -> :core:companion
:data:companion             -> :application:companion + portable core
:connectivity:companion         -> :application:companion + :core:companion

platform adapters -> application contracts
application/core -X-> Android Context, Intent, VpnService, KeyStore, sockets, Compose, Room
companion modules -X-> desktop proxy internals, traffic persistence, protocol inspectors
```

The intermediate `ui/companion` directory remains a Gradle namespace. `connectivity/companion` is now a KMP module:
Android APIs are isolated in `androidMain`, while `iosMain` contains explicit fail-closed placeholders pending
native Network, Security, and Network Extension implementations.

## State and runtime flow

### Pairing

```text
QR/deep link/paste
  -> strict versioned invitation decoder
  -> expiry and model validation
  -> Android Keystore P-256 device identity
  -> algorithm-bound proof transcript
  -> pinned control transport port
  -> credential written to protected secret storage
  -> non-secret registration committed and selected
```

The one-time invitation and issued credential are never part of observable UI state or the serialized registration.
Credential storage and registration commit use rollback paths so partial pairing does not leave an orphaned secret.

### Inspection start

```text
shared action
  -> active registration + network + protected credential checks
  -> authenticated companion transport port
  -> real certificate trust challenge
  -> Android VPN preparation boundary
  -> typed RequestVpnConsent effect when required
  -> Android packet backend port
  -> Running(fullHttpsInspection = true/false)
```

Missing CA trust does not pretend HTTPS bodies are inspectable. The state explicitly permits limited metadata/plain
HTTP inspection while reporting `fullHttpsInspection = false`. Unsupported traffic behavior is explicit (`REJECT`
or `BYPASS`) rather than silently dropping or leaking traffic.

### Lifecycle

- The shared ViewModel creates a supervised child job under the product lifecycle scope and cancels its collectors on
  `close()`.
- Concurrent foreground operations use an atomic counter; completion of one cannot clear progress for another.
- Automatic certificate verification does not trigger a foreground loading flash.
- Android network callbacks have explicit ownership and idempotent close.
- Pairing/refresh preserve coroutine cancellation and execute rollback in `NonCancellable` cleanup.

## Android storage and key ownership

- Registration metadata uses a versioned shared JSON envelope in private Android preferences.
- Credential plaintext is encrypted with an Android Keystore non-exportable AES-GCM key before preferences storage.
- The device proof private key is a non-exportable P-256 Android Keystore key.
- Durable registration stores only a credential reference, public device identity, endpoints, certificate pins,
  granted scopes, and timestamps.
- Control request/response bodies are defensively copied and bounded. Concrete networking must enforce pinned TLS and
  the response byte limit before constructing the shared response.

## Test gates

Automated tests cover:

- value validation and secret-free durable models;
- invitation versioning, duplicate/malformed fields, Unicode round trips, and persistence restoration;
- pairing secret separation, rollback, expiry, VPN consent, and limited-inspection behavior;
- defensive control-body ownership, proof-transcript construction, sanitized rejection, and scope decoding;
- real desktop verification of an Android-compatible P-256 ECDSA proof including tamper and algorithm mismatch;
- shared ViewModel secret isolation, typed VPN-consent effects, progress completion, and collector shutdown;
- shared Compose resource/state mapping plus JVM, Android, and iOS-simulator compilation;
- Android inspection consent/start/stop lifecycle;
- Android and iOS compilation of all portable modules.

Run `./gradlew companionFoundationQualification` for shared/platform foundations, or
`./gradlew companionAndroidProductQualification` to add Android product tests, lint, and APK assembly. Neither gate
installs or launches an application.

## Deliberately remaining work

The following are real next increments, not hidden stubs:

1. Complete pairing, desktop, certificate, connection, and inspection workflow screens in the existing
   `:ui:companion:sharedUi` module.
2. A pinned Android control transport and authenticated direct-LAN carrier integrated with the matching desktop
   companion control/tunnel server.
3. A real `VpnService` plus bounded TUN-to-proxy packet backend, foreground-service lifecycle, DNS policy, MTU,
   IPv4/IPv6, per-app policy, and leak-safe stop/recovery behavior.
4. Android certificate download/install handoff and end-to-end trust challenge adapters.
5. Physical Android qualification across Wi-Fi/cellular transitions, process death, device reboot, CA trust,
   certificate pin mismatch, revocation, backpressure, and multi-hour soak.
6. Future iOS Keychain/Secure Enclave, Network Extension, and thin Compose Multiplatform product host consuming the
   unchanged shared UI and presentation modules.

Until items 1–5 exist and pass real-device evidence, Mobile Companion remains **shared Compose shell available,
capture runtime unavailable** and VPN remains **unavailable**. This is intentional capability truth, not a
limitation in the shared architecture.
