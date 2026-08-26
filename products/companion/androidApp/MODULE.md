# `:products:companion:androidApp`

## Responsibility

Owns the installable Android companion APK, Android manifest, launcher lifecycle, and product composition root.

## Owns

- Android application identity, permissions, packaging resources, Compose host activity, and process lifecycle.
- Composition of the implemented registration, protected credential, invitation, device-identity, proof-signing,
  and network-observation adapters.
- Lifecycle-aware binding of product state into the shared Compose Multiplatform companion UI.

## Does not own

- Shared companion models, workflows, persistence schemas, presentation state, UI screens, or UI resources.
- Pinned control/data transport, certificate installation, or VPN/TUN behavior that has not yet been implemented.
- Desktop proxy, canonical Traffic persistence, protocol inspection, or reusable Android adapters.

## Dependency rule

May depend on companion application, data, presentation, shared UI, Android connectivity, and core modules solely
to compose the executable product. No reusable module may depend on this product.
