# `:ui:desktop:scripting`

## Responsibility

Owns desktop scripting screens, editor workflows, snippets, variables, console, diagnostics, and feature state.

## Owns

- Scripting ViewModel, UI models, templates, editor, and results presentation.

## Does not own

- Script runtime implementation, proxy integration, canonical traffic data, or product DI bindings.

## Dependency rule

Invoke script use cases and render their state; runtime implementation remains in `:engine:script` behind application-facing contracts.

## Migration direction

The module currently exposes presentation types only. A future product activation must add its binding under `:products:desktop/di/scripting`.
