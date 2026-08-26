# `:application:companion`

## Responsibility

Owns portable companion workflows and application contracts. It coordinates pairing, durable registration, credential
storage, transport connection, certificate trust, VPN consent/start/stop, recovery, and forgetting a desktop.

## Owns

- Use cases and deterministic lifecycle policies.
- Repository, secure-credential, lightweight-invitation resolver, pairing-client, transport, certificate-source,
  platform trust-verifier, trust-store change-notification, VPN, and network-state contracts.
- Expiry-first asynchronous bootstrap redemption that verifies returned endpoint and certificate identities before
  exposing the complete invitation to pairing.
- Fail-closed commit rules that prevent half-persisted pairing and never restore a credential after the desktop has
  replaced it.

## Source layout

- `contract/PairingContracts.kt` — bootstrap decoding/redemption, device proof, and pairing-client contracts.
- `contract/RegistrationContracts.kt` — durable trusted-desktop registration contracts.
- `contract/CredentialContracts.kt` — protected credential storage and refresh outcomes.
- `contract/ConnectionContracts.kt` — authenticated transport and network-observation contracts.
- `contract/InspectionContracts.kt` — platform inspection preparation and lifecycle contracts.
- `contract/CertificateContracts.kt` — separate authenticated root source, platform trust verifier, and trust-store
  recheck-trigger contracts.
- `usecase/PairingUseCases.kt` — bootstrap resolution, invitation validation, device proof, and fail-closed pairing commit.
- `usecase/RegistrationUseCases.kt` — registration observation, selection, and trusted-desktop removal.
- `usecase/ConnectionUseCases.kt` — authenticated connect/disconnect, network observation, and recovery.
- `usecase/InspectionUseCases.kt` — VPN preparation, inspection start/stop, and capture state.
- `usecase/CertificateUseCases.kt` — paired-root confirmation, authoritative trust verification, and
  trust-store recheck observation.
- `usecase/CredentialUseCases.kt` — credential rotation and fail-closed local persistence.
- `usecase/CompanionUseCaseFailures.kt` — package-internal shared workflow failures.

## Does not own

- Android `Context`/`Intent`/`VpnService`, Apple frameworks, storage implementations, HTTP/TLS socket
  implementations, UI state/widgets, desktop proxy internals, or traffic persistence.

## Dependency rule

Depends inward only on companion/identity/pairing core models. Platform adapters and products depend on this
module, never the reverse.
