# `:ui:desktop:workspace`

## Responsibility

Owns desktop workspace presentation: explorer trees, search, layouts, splitters, tabs, and workspace interaction state.

## Owns

- Workspace screen, ViewModel, UI models, and reusable workspace components.

## Does not own

- Persistence implementations, captured traffic, request execution, engine lifecycle, or product DI bindings.

## Dependency rule

ViewModels call use cases or repository contracts; composables render state and emit intents.

## Migration direction

The standalone workspace screen currently exposes presentation types only. Product workspace persistence assembly lives in `:products:desktop` under `di/workspace`; a future activation of `WorkspaceViewModel` belongs there too.
