# `:ui:core`

## Responsibility

Provides the KMP-safe Compose design system, foundations, themes, layouts, and reusable visual components.

## Owns

- Design tokens, palettes, typography, responsive layout primitives, and generic UI components.
- System-aware light/dark theme resolution, Material theme bridging, and reduced-motion behavior.
- One cohesive dropdown contract for compact, searchable, disabled, selected, hover, focus, and keyboard states,
  with compact/standard/large height presets, a bounded content-responsive anchor width calculated from the
  widest option, plus an independently content-responsive popup width that is clamped by window constraints.
  Anchor and popup width targets are stable across selection changes, animate only when their option-set-derived
  target changes, and remain overridable by feature layout. Genuinely clipped standard labels reuse the shared
  stationary-hover overflow preview. A composition-scoped expansion coordinator gives single-select,
  multi-select, and searchable dropdowns one active owner: clicking another anchor closes the previous popup and
  opens the new one in the same pointer event, while clicking the active anchor closes it once. Popups do not steal
  focus or consume the next anchor's click. A short-lived, monotonic owner marker bridges desktop popup dismissal on
  pointer press to anchor activation on release, preventing the just-closed header from reopening. Searchable controls
  preserve text-field focus and text-cursor ownership.
  Popup entry and exit use the shared reduced-motion-aware motion tokens. Single-select callers may opt into a
  centered label-and-chevron group
  without changing width calculation or the default edge-separated arrangement; both arrangements retain the
  shared label-to-chevron spacing token.
- A generic multi-select dropdown that reuses the same anchor and popup contract, keeps the menu open across
  toggles, and gives each accessible checkbox row one interaction owner. Feature-specific option types remain
  outside `:ui:core`.
- One theme-aware vertical scrollbar primitive for finite and virtualized scroll states. It renders only after
  measured content overflows its viewport; single-select, multi-select, and searchable dropdowns reuse it instead
  of owning separate scrollbar styling or visibility rules.
- Constraint-stable, horizontally scrollable tab rows whose shared surface uses the design-system medium corner
  shape and whose single-line labels retain their measured width under unbounded scroll constraints, truncating
  only at the design-system maximum.
- Controlled split-pane primitives whose ratios remain owned by their feature screens.
- Button defaults that preserve caller-owned outer spacing and sizing modifiers; explicit caller heights may
  override the density default without forcing padding into the content measurement. Loading buttons disable
  interaction by default, with an explicit opt-in for cancellable operations that must retain click and hand-
  cursor behavior while showing progress.
- Cohesive text-input APIs that preserve selection, validation/supporting text, password visibility, and focus state.
- One measured text-overflow preview host shared by standard text fields and compact editable key/value cells. It
  owns stationary-hover timing, bounded above/below placement, and display-only popup styling; consumers retain
  their existing single-line editing and data semantics.
- Read-only key/value inspection whose values wrap and grow rows by default so headers, cookies, parameters, and
  form content remain visible. Keys, column headers, and copy actions retain stable top-aligned columns; compact
  consumers may explicitly opt into single-line value truncation. Editable key/value rows remain single-line.
- The non-modal, right-edge `KNetSideDrawer` shell, including responsive size classes, animation,
  surface, and border. Feature modules own drawer state and content.
- Portable clipboard and pointer APIs whose desktop-only AWT adaptation is confined to `jvmMain`.

## Does not own

- Feature state, repositories, application use cases, engines, or desktop bootstrap.
- HTTP methods/status semantics, Traffic table columns, payload inspection, or other protocol-specific presentation.

## Dependency rule

Stay independent of product features and infrastructure. Feature UI modules may depend on it, never the reverse.

## Migration direction

Keep as the reusable visual foundation; move feature-specific components back to their feature modules when discovered.
Do not add compatibility aliases, no-op helpers, production catalogs/samples, or speculative component-framework types.
Prefer one cohesive component API over parallel overload families when state such as text selection must be preserved.
Feature code must use the asynchronous foundation clipboard API and must never import AWT clipboard types directly.
