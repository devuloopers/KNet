# `:core:companion`

## Responsibility

Defines the portable companion vocabulary shared by Android now and iOS later: desktop registrations,
pairing invitations, pinned endpoints, certificate trust, connection state, inspection state, and policy.

## Owns

- Validated companion identifiers and immutable state models.
- Direct-versus-relay transport selection and unsupported-traffic policy values.
- Secret-free durable registration metadata.
- Platform-neutral failure codes suitable for application and presentation layers.

## Does not own

- Credentials, private keys, persistence, sockets, VPN/TUN handles, Android/iOS APIs, proxy parsing,
  canonical traffic records, or UI widgets.

## Dependency rule

May depend only on `:core:identity`, `:core:pairing`, and `:core:connectivity`. It must compile for Android,
JVM, and iOS without platform APIs.
