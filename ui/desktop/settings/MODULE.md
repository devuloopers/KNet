# `:ui:desktop:settings`

## Responsibility

Owns desktop settings presentation for appearance, proxy/network, storage, and related user preferences.

## Owns

- Settings screen, tabs, ViewModel, validation, and presentation models.
- The desktop-settings platform-action contract consumed by the ViewModel.
- Local numeric drafts with explicit commit actions, field-level validation/progress, typed operation notices,
  reset confirmation, and cancellation-safe certificate trust actions.
- Responsive category navigation and setting rows built from shared `:ui:core` responsive, surface, tab,
  scrollbar, switch, badge, text-field, and button behavior.

## Does not own

- Preference persistence, proxy lifecycle, connectivity mechanism implementation, certificate operations, or product DI bindings.

## Dependency rule

Read and change settings through use cases/repository contracts; composables must not coordinate engines.

## Current state

Certificate operations cross the application port, and connectivity choices are represented by typed descriptors/mechanisms rather than concrete engine calls. Assembly for this feature lives in `:products:desktop` under `di/settings`.
Opening the data directory is an injected platform action; the ViewModel does not import AWT or filesystem implementations.
The ViewModel observes and atomically updates process-level application settings through domain use cases. It does
not read or mutate workspace layout, and typing an incomplete port or timeout never changes persistence or runtime.
The payload-retention limit and theme selector remain visible as explicitly disabled future capabilities instead of
pretending to save values that have no runtime consumer. Compact windows use a top category row; wider windows use
the Settings sidebar, and both render the same tab content and intent boundary.
