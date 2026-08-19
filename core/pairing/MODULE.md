# `:core:pairing`

## Responsibility

Defines platform-neutral pairing, authorization, credential, scope, and trusted-device contracts for authenticated ingress and future companions/relay carriers.

## Owns

- Pairing invitations, public-key proof requests/results, issued credentials, scopes, trusted-device projections, principals, expiry, and revocation behavior.

## Does not own

- Traffic models, remote transport, connectivity setup, proxy runtime, or UI.

## Dependency rule

May depend only on the neutral identity types in `:core:identity`; remain independent of desktop, connectivity, engine, UI, and persistence implementations.

## Current state

The desktop pairing coordinator and Room-backed registered-device adapter implement these contracts. A registered identity is durable while source-address authorization remains session-only. Direct mobile tunnels and relay carriers are intentionally absent until real product targets exist; they can be added without changing this module's identity, credential, and principal types.
