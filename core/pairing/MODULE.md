# `:core:pairing`

## Responsibility

Defines platform-neutral device identity, pairing, authorization, credential, scope, and revocation contracts for authenticated ingress and future companions/relay carriers.

## Owns

- Pairing invitations, device identities, public-key proof requests/results, issued credentials, scopes, principals, expiry, and revocation state.

## Does not own

- Traffic models, remote transport, connectivity setup, proxy runtime, or UI.

## Dependency rule

Remain independent of desktop, engine, UI, and persistence implementations.

## Current state

The desktop pairing coordinator and encrypted store implement these contracts. Direct mobile tunnels and relay carriers are intentionally absent until real product targets exist; they can be added without changing this module's credential and principal types.
