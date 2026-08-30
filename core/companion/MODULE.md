# `:core:companion`

## Responsibility

Defines the portable companion vocabulary shared by Android and iOS: desktop registrations,
pairing invitations, pinned endpoints, certificate trust, connection state, inspection state, and policy.

## Owns

- Validated companion identifiers and immutable state models.
- The canonical, bounded `knet://pair/v3` bootstrap codec, one-time redemption body codec, and complete invitation
  response codec shared by desktop and companion products.
- Canonical bounded pairing-completion, initial credential-grant, and credential-refresh wire codecs plus their
  versioned control paths and media types.
- Strongly typed public-root and pinned-TLS bootstrap endpoints, fingerprints, bounds, and media types.
- Direct-versus-relay transport selection and unsupported-traffic policy values.
- Secret-free durable registration metadata, including bounded immutable public root trust material.
- Certificate-readiness states plus the versioned TLS server name, endpoint paths, nonce, and response bounds used
  by platform adapters.
- Platform-neutral failure codes suitable for application and presentation layers.

## Does not own

- Credentials, private keys, persistence, sockets, VPN/TUN handles, Android/iOS APIs, proxy parsing,
  canonical traffic records, or UI widgets.

## Dependency rule

May depend only on `:core:identity`, `:core:pairing`, and `:core:connectivity`. It must compile for Android,
JVM, and iOS without platform APIs.
