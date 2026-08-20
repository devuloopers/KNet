# `:engine:certificate`

## Responsibility

Implements certificate-authority, leaf-certificate, client-identity, TLS key-manager, and certificate-cache behavior required for HTTPS inspection.

## Owns

- CA and leaf certificate generation, CA validity/key-pair validation, owner-only key material, opaque internal identity names, and contained key lookup.
- PKCS#12, JKS, and combined PEM parsing. Accepted identities are normalized to an owner-only internal PKCS#12 without persisting the source passphrase.
- TLS certificate selection, bounded single-flight asynchronous generation, weighted LRU/idle caching, and exact/wildcard mTLS host routing.
- Atomic in-memory ownership of client-certificate and mTLS-rule state, with persistence delegated through `CertificateConfigurationStore` as one complete snapshot.

## Does not own

- Proxy connection flow, connectivity instructions, UI, captured traffic, JSON/filesystem metadata persistence, or operating-system trust-store commands.

## Dependency rule

Depends only on logging, Kotlin date/time, and JCA/Bouncy Castle. Consumers access it through certificate-facing interfaces; desktop data supplies persistence and TLS-context adapters.

## Migration direction

The cryptographic engine is independent of the proxy, application UI, serialization format, and OS integration. Apple profile/connectivity setup stays in `:connectivity:desktop`; desktop trust installation stays in `:data:desktop`.
Compound certificate/rule mutations and snapshots are protected by one explicit reentrant state lock. Atomic values are not used because a mutation spans lists, key-manager cache, persistent snapshot, and key-file lifecycle as one invariant.
