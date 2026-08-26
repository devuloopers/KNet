# `:ui:companion:presentation`

## Responsibility

Provides shared companion UI state, user actions, one-shot platform effects, and a multiplatform AndroidX
`ViewModel`. The shared Compose Multiplatform UI renders this state while each product supplies a
`ViewModelStoreOwner` and handles native effects.

## Owns

- Immutable `CompanionUiState`, typed actions/effects, and orchestration-facing ViewModel behavior.
- `CompanionViewModelDependencies`, a presentation-owned constructor bundle that references application-layer
  use cases without implementing or relocating business operations.
- A deterministic setup-stage reducer for invitation entry, in-app scanning, confirmation, certificate
  verification, native inspection consent, and the ready state; navigation framework types remain outside this
  module.
- Stable effect descriptions for QR image selection, VPN consent, public-certificate export, and trust guidance.
- Lifecycle-owned coroutine cancellation through `viewModelScope` and concurrency-safe foreground-operation state.
- Asynchronous bootstrap redemption with stale-job rejection, so scanning or replacing an invitation cannot apply
  an older network result to current UI state.
- Camera submission gating that ignores results outside the scanner route and ignores duplicate frames while the
  first invitation is resolving; rejected camera input returns to an explicit retry state.
- Authoritative certificate rechecks after platform trust-store notifications, with stale results rejected when
  the active desktop changes during verification.
- Secret-free certificate-export and verification lifecycle diagnostics through the shared core logger.

## Does not own

- Compose widgets, Android `Intent`, Apple framework values, repositories, sockets, VPN/TUN
  implementations, or dependency-injection bindings.

## Dependency rule

Depends only on `:application:companion`, `:core:companion`, `:core:logger`, AndroidX Lifecycle ViewModel, and
coroutines. Product modules provide lifecycle stores and effect handlers without adding platform types to common
state.
