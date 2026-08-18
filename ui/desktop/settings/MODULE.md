# `:ui:desktop:settings`

## Responsibility

Owns desktop settings presentation for appearance, proxy/network, storage, and related user preferences.

## Owns

- Settings screen, tabs, ViewModel, validation, and presentation models.
- The desktop-settings platform-action contract consumed by the ViewModel.

## Does not own

- Preference persistence, proxy lifecycle, connectivity mechanism implementation, certificate operations, or product DI bindings.

## Dependency rule

Read and change settings through use cases/repository contracts; composables must not coordinate engines.

## Current state

Certificate operations cross the application port, and connectivity choices are represented by typed descriptors/mechanisms rather than concrete engine calls. Assembly for this feature lives in `:products:desktop` under `di/settings`.
Opening the data directory is an injected platform action; the ViewModel does not import AWT or filesystem implementations.
