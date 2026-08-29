# `:products:companion:di`

## Responsibility

Owns the KMP Koin definitions shared by the Android and iOS companion product composition roots.

## Owns

- Common bindings from the product-provided `CompanionPlatformAdapters` aggregate to portable application
  contracts.
- Portable companion repositories, codecs, control clients, use cases, lifecycle ViewModel dependencies, and
  ViewModel definitions.
- A deterministic module list installed after each product has registered its restored platform prerequisites.

## Does not own

- Native persistence construction, Keychain or Keystore identity, platform display names, native transports,
  VPN/TUN lifecycle, scanner ownership, or application startup.
- Business rules, persistence implementations, connectivity implementations, presentation behavior, or UI.

## Dependency rule

May depend on companion application, connectivity, data, and presentation modules plus Koin solely to assemble the
shared mobile product graph. Android and iOS product roots may depend on this module. No reusable module may depend
on it.
