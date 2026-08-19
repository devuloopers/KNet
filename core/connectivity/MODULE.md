# `:core:connectivity`

## Responsibility

Defines platform-neutral connectivity contracts for making a running proxy reachable and guiding a client through setup.

## Owns

- Proxy endpoint, network snapshot, setup artifact, capability, mechanism, and automatically managed open
  Wi-Fi sharing descriptors.
- Independent availability, lifecycle, and health state models.
- Contracts for managed and instruction-only connectivity mechanisms.

## Does not own

- Proxy startup, PAC/profile bytes, ADB execution, VPN implementation, UI, pairing, or relay transport.

## Dependency rule

Remain independent of identity, pairing, engine, UI, data, and platform implementations.

## Migration direction

Desktop Wi-Fi sharing and future mobile mechanisms implement these contracts additively; adding a mechanism must not require modifying proxy or traffic core.
