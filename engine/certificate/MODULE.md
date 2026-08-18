# `:engine:certificate`

## Responsibility

Implements certificate-authority, leaf-certificate, TLS key-manager, cache, and desktop trust-installation behavior required for HTTPS inspection.

## Owns

- CA and leaf certificate generation, owner-only files, opaque internal identity names, contained key lookup, and secure key material handling.
- TLS certificate selection, bounded single-flight asynchronous generation, weighted LRU/idle caching, installation adapters, and related policies.
- Atomic in-memory ownership and persistence of client-certificate and mTLS-rule configuration.

## Does not own

- Proxy connection flow, connectivity instructions, UI, or captured traffic.

## Dependency rule

Depends only on the minimal domain/logging contracts it needs. Consumers access it through certificate-facing interfaces.

## Migration direction

Keep the cryptographic engine intact, its bounded single-flight cache, and strict upstream trust default. Move Apple profile and connectivity setup orchestration out to `:connectivity:desktop`.
Compound certificate/rule mutations and snapshots stay protected by the manager's explicit state lock rather than synchronized collection wrappers.
