# `:core:connectivity`

## Responsibility

Defines platform-neutral connectivity contracts for making a running proxy reachable and guiding a client through setup.

## Owns

- Proxy endpoint, network snapshot, setup artifact, capability, mechanism, and Wi-Fi sharing descriptors.
- Independent availability, lifecycle, and health state models.
- Contracts for managed and instruction-only connectivity mechanisms.

## Does not own

- Proxy startup, PAC/profile bytes, ADB execution, VPN implementation, UI, pairing, or relay transport.

## Dependency rule

This is a leaf contract module and has no production project dependencies.

## Migration direction

Desktop Wi-Fi sharing and future mobile mechanisms implement these contracts additively; adding a mechanism must not require modifying proxy or traffic core.
