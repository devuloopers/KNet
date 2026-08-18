# `:ui:desktop:codeEditor`

## Responsibility

Provides a reusable Compose code editor and read-only code viewer for desktop features.

## Owns

- Editor rendering, viewport, selection, navigation, pointer behavior, shortcuts, and editor-local state.
- Monotonic gesture/undo timing and the narrow JVM cursor adapter used by desktop pointer input.

## Does not own

- Script execution, HTTP bodies, feature business rules, persistence, or network behavior.

## Dependency rule

Remain a generic UI component consumed by feature modules; do not depend on infrastructure or feature implementations.
Clipboard access goes through `:ui:core`; AWT cursor access stays in `jvmMain`.

## Migration direction

Keep the editor reusable and move any discovered feature-specific semantics to the owning UI module.
