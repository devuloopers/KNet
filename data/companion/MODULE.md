# `:data:companion`

## Responsibility

Implements versioned companion invitation and registration persistence behavior while keeping storage and
secret protection replaceable per platform.

## Owns

- Version 3 lightweight bootstrap decoding and version 2 durable registration DTOs with strict, bounded Base64URL
  public-root mapping.
- Registration repository implementation, active-desktop selection, migration boundary, and credential-store
  adapter over an encrypted secret store.
- A bounded, defensive, versioned pairing/credential-refresh control client that supplies paired-root trust
  material to native transports without implementing TLS policy in common code.
- Android record, AES-GCM credential vault, non-exportable P-256 device identity, and proof signer adapters in
  `androidMain`; their suspending factories and operations isolate synchronous Android storage and cryptography on
  an injected worker dispatcher. Future iOS adapters can implement the same small ports.

## Does not own

- Pairing policy, transport/VPN lifecycle, UI, desktop databases, canonical traffic storage, or proxy state.

## Dependency rule

Depends on `:application:companion` ports and portable core values. Common code cannot import platform storage
APIs; credentials and private key material must never enter registration JSON or observable state. Android
credential ciphertext is bound to its credential reference with AES-GCM additional authenticated data.
