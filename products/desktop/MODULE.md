# `:products:desktop`

## Responsibility

Composes and launches the desktop product. This is the desktop composition root and lifecycle owner.

## Owns

- Desktop `main`, configuration loading, Koin composition, startup, and reverse-order shutdown of connectivity, gateway, setup portal, inspector, proxy/capture, and Room resources.
- All Koin binding declarations, organized under `di/<feature>/` so each feature's adapters, use cases, and ViewModels have one visible assembly location.
- Wiring concrete adapters to application ports.
- Desktop shell implementations, including settings data-directory actions, behind feature-owned contracts.

## Does not own

- Business rules, canonical traffic models, engine behavior, storage logic, connectivity mechanisms, reusable UI, or reusable DI modules.

## Dependency rule

May depend on all desktop implementations needed for composition. No reusable module may depend on `:products:desktop`.

## Current state

The composition root owns normal-window and JVM-hook shutdown paths and is the only module that declares Koin bindings or assembles UI with concrete desktop runtime adapters. Bindings are grouped into `platform`, `apistudio`, `breakpoint`, `certificate`, `connectivity`, `httppanel`, `inspection`, `proxy`, `settings`, `traffic`, and `workspace` packages.
