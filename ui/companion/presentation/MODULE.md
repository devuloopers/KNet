# `:ui:companion:presentation`

## Responsibility

Provides shared companion UI state, user actions, one-shot platform effects, and a framework-neutral ViewModel.
The shared Compose Multiplatform UI renders this state while each product owns its lifecycle and native effects.

## Owns

- Immutable `CompanionUiState`, typed actions/effects, and orchestration-facing ViewModel behavior.
- Stable effect descriptions for VPN consent and certificate installation/trust guidance.
- A supervised child lifecycle, cancellation on close, and concurrency-safe foreground-operation state.

## Does not own

- Compose widgets, Android `Intent`, Apple framework values, repositories, sockets, VPN/TUN
  implementations, or dependency-injection bindings.

## Dependency rule

Depends only on `:application:companion`, `:core:companion`, and coroutines. Product modules bind platform
lifecycle/effect handlers without adding platform types to common state.
