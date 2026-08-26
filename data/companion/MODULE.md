# `:data:companion`

## Responsibility

Implements versioned companion invitation and registration persistence behavior while keeping storage and
secret protection replaceable per platform.

## Owns

- Versioned JSON DTOs and strict domain mapping.
- Registration repository implementation, active-desktop selection, migration boundary, and credential-store
  adapter over an encrypted secret store.
- A bounded, defensive, versioned pairing/credential-refresh control client over a pinned-transport port.
- Android record, AES-GCM credential vault, non-exportable P-256 device identity, and proof signer adapters in
  `androidMain`; future iOS adapters can implement the same small ports.

## Does not own

- Pairing policy, transport/VPN lifecycle, UI, desktop databases, canonical traffic storage, or proxy state.

## Dependency rule

Depends on `:application:companion` ports and portable core values. Common code cannot import platform storage
APIs; credentials and private key material must never enter registration JSON or observable state. Android
credential ciphertext is bound to its credential reference with AES-GCM additional authenticated data.
