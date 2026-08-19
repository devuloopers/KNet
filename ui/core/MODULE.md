# `:ui:core`

## Responsibility

Provides the KMP-safe Compose design system, foundations, themes, layouts, and reusable visual components.

## Owns

- Design tokens, palettes, typography, responsive layout primitives, and generic UI components.
- System-aware light/dark theme resolution, Material theme bridging, and reduced-motion behavior.
- One cohesive dropdown contract for compact, searchable, disabled, selected, hover, focus, and keyboard states,
  with compact/standard height presets, a finite overridable anchor width that is independent of the selected
  label, and an explicitly constrained popup that safely supports lazy results. Ordinary dropdown popups consume
  outside clicks so one anchor click closes them once; searchable popups preserve text-field focus. Popup entry
  and exit use the shared reduced-motion-aware motion tokens.
- A generic multi-select dropdown that reuses the same anchor and popup contract, keeps the menu open across
  toggles, and gives each accessible checkbox row one interaction owner. Feature-specific option types remain
  outside `:ui:core`.
- Constraint-stable, horizontally scrollable tab rows whose single-line labels retain their measured width under
  unbounded scroll constraints and truncate only at the design-system maximum.
- Controlled split-pane primitives whose ratios remain owned by their feature screens.
- Button defaults that preserve caller-owned outer spacing and sizing modifiers; explicit caller heights may
  override the density default without forcing padding into the content measurement.
- Cohesive text-input APIs that preserve selection, validation/supporting text, password visibility, and focus state.
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
