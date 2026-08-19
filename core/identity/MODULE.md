# `:core:identity`

## Responsibility

Defines platform-neutral registered-device identity for pairing and future authenticated companion or relay
transports.

## Owns

- Stable registered-device identifiers.
- Durable user-visible device identity, registration kind, last-seen time, and revocation state.

## Does not own

- Pairing invitations, credentials, network addresses, session authorization, persistence, transport, or UI.

## Dependency rule

Remain dependency-free and independent of pairing, connectivity, traffic, engines, persistence, and products.

## Current state

Cryptographically paired companions use this durable identity model. Open manual Wi-Fi clients are attributed
by their observed source address and do not create, select, or persist a registered identity.
