# `:products:companion:androidApp`

## Responsibility

Owns the installable Android companion APK, Android manifest, launcher lifecycle, and product composition root.

## Owns

- Android application identity, permissions, packaging resources, Compose host activity, and process lifecycle.
- Android Koin prerequisites for restored stores, platform adapters, device identity, proof signing, transport, TUN
  forwarding, and VPN runtime coordination. Portable repository, client, use-case, and ViewModel definitions come
  from `:products:companion:di`.
- A domain-scoped Android network-security policy that permits user-installed anchors only for
  `companion.knet.local`; unrelated application traffic retains platform defaults.
- Activity `ViewModelStore` ownership, lifecycle-aware state binding, and started-state effect collection for the
  shared Compose Multiplatform companion UI.
- Activity-owned native effect handling for bounded QR-image invitation decoding, public-certificate export,
  certificate settings guidance, and Android VPN consent; no Android handle crosses into common code.
- Public CA export to `Downloads/KNet` through MediaStore on Android 10+, with a Storage Access Framework fallback
  on Android 8–9 and export/Settings-return diagnostics under the shared certificate log tag.
- An Activity-scoped CameraX preview with bundled ML Kit QR-only analysis, typed permission mapping, one in-flight
  frame, one delivered payload per composition, lifecycle binding, and deterministic analyzer cleanup. The scanner
  is supplied through the shared UI capability contract rather than Koin because Activity Result registration and
  camera preview ownership are Activity-scoped.
- Asynchronous dependency restoration before Koin startup and lifecycle-scoped QR decoding so preference
  initialization, image I/O, and QR processing do not block the Activity main thread.
- A public, non-exported Android `VpnService` foreground component that owns the TUN descriptor, delegates packet
  translation to the reusable Android connectivity adapter, and reports bounded start/stop completion back to the
  shared inspection lifecycle.
- A production pairing client and inspection carrier backed by separate pinned-TLS control and proxy gateways.

## Does not own

- Shared companion models, workflows, persistence schemas, presentation state, UI screens, or UI resources.
- Reusable packet translation, proxy authentication, certificate verification, or shared inspection policy. The
  product owns only Android composition, foreground-service lifetime, TUN creation, and native system handoffs.
- Desktop proxy, canonical Traffic persistence, protocol inspection, or reusable Android adapters.

## Dependency rule

May depend on companion application, data, presentation, shared UI, Android connectivity, core modules,
`:products:companion:di`, and the repository-standard Koin runtime solely to compose the executable product. Koin
definitions remain product-owned; no reusable companion module depends on Koin or on this product.
