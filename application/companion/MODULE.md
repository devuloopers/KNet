# `:application:companion`

## Responsibility

Owns portable companion workflows and application contracts. It coordinates pairing, durable registration, credential
storage, transport connection, certificate trust, VPN consent/start/stop, recovery, and forgetting a desktop.

## Owns

- Use cases and deterministic lifecycle policies.
- Repository, secure-credential, pairing-client, transport, certificate, VPN, and network-state contracts.
- Rollback rules that prevent half-persisted pairing and stale credentials.

## Source layout

- `contract/PairingContracts.kt` — invitation decoding, device proof, and pairing-client contracts.
- `contract/RegistrationContracts.kt` — durable trusted-desktop registration contracts.
- `contract/CredentialContracts.kt` — protected credential storage and refresh outcomes.
- `contract/ConnectionContracts.kt` — authenticated transport and network-observation contracts.
- `contract/InspectionContracts.kt` — platform inspection preparation and lifecycle contracts.
- `contract/CertificateContracts.kt` — certificate download and trust-verification contracts.
- `usecase/PairingUseCases.kt` — invitation validation, device proof, pairing commit, and rollback.
- `usecase/RegistrationUseCases.kt` — registration observation, selection, and trusted-desktop removal.
- `usecase/ConnectionUseCases.kt` — authenticated connect/disconnect, network observation, and recovery.
- `usecase/InspectionUseCases.kt` — VPN preparation, inspection start/stop, and capture state.
- `usecase/CertificateUseCases.kt` — root-certificate download and trust verification.
- `usecase/CredentialUseCases.kt` — credential rotation and rollback.
- `usecase/CompanionUseCaseFailures.kt` — package-internal shared workflow failures.

## Does not own

- Android `Context`/`Intent`/`VpnService`, Apple frameworks, storage implementations, HTTP/TLS socket
  implementations, UI state/widgets, desktop proxy internals, or traffic persistence.

## Dependency rule

Depends inward only on companion/identity/pairing core models. Platform adapters and products depend on this
module, never the reverse.
