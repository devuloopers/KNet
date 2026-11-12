# `:ui:core`

## Responsibility

Provides KNet's Compose Multiplatform design system, foundations, themes, layouts, resources, and reusable visual
components. Production code targets JVM desktop, Android, iOS ARM64, and iOS Simulator ARM64 from one common API.

## Owns

- Design tokens, palettes, typography, responsive layout primitives, and generic UI components.
- A shared manager-screen layout and header with matching page insets, title/subtitle typography, stable
  heading baselines and minimum height. Measured controls move below the title and flow within narrow viewports;
  feature modules supply action behavior without owning header resize rules.
- System-aware light/dark theme resolution, Material theme bridging, and reduced-motion behavior.
- One cohesive dropdown contract for compact, searchable, disabled, selected, hover, focus, and keyboard states,
  with compact/standard/large height presets and centrally bounded content-responsive widths. Every single-select
  anchor fits its displayed label or placeholder and uses themed growth/contraction by default, without feature
  opt-ins. Reduced motion applies the target immediately. The independent
  popup still fits the widest option and respects window constraints. Explicit sizing modifiers take precedence. Genuinely clipped standard labels reuse the shared
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
- Optional dropdown descriptions explain the current value and menu choices after a shared hover delay.
  The reusable tooltip host uses KNet tokens, bounds wrapped paragraphs to the window, prefers beside-menu help,
  never takes focus or reserves layout space, and cancels stale/pending help on interaction or content changes.
  Popup visibility requires the pointer to remain inside its control/row; retained keyboard focus cannot keep
  it visible or reopen it after pointer exit. Keyboard focus retains the same accessible description.
  Described controls expose the same text to accessibility services and suppress duplicate overflow previews.
  Narrow-window help avoids the complete menu bounds, while shared anchor/row keyboard routing preserves
  arrow navigation and Enter/Space selection across non-focusable popup layers.
- A manager-table viewport with one finite shared width for header and virtualized rows, theme-owned minimum
  width and row density, viewport-pinned scrollbars, and an empty state that remains visible during horizontal
  overflow. Table rows support disabled interaction and the shared manager density token.
  Its separate header slot supplies the measured header height: the header background spans the scrollbar lane,
  vertical tracks begin below it, and horizontal/vertical gutters are reserved only for actual overflow.
  Header cells and rows retain one shared horizontal scroll owner and identical finite column width.
- A generic multi-select dropdown that reuses the same anchor and popup contract, keeps the menu open across
  toggles, and gives each accessible checkbox row one interaction owner. Feature-specific option types remain
  outside `:ui:core`.
- One theme-aware vertical scrollbar primitive for finite and virtualized scroll states. It renders only after
  measured content overflows its viewport; single-select, multi-select, and searchable dropdowns reuse it instead
  of owning separate scrollbar styling or visibility rules.
- One theme-aware horizontal scrollbar primitive for finite scroll state, sharing the same metrics and hover styling as the vertical primitive and rendering only for real overflow.
- A fixed-height statistics footer with leading/trailing slots, intrinsic-width values and a pinned horizontal
  scrollbar for narrow panes or larger text. Its height and overflow policy belong to UI core rather than Traffic.
- A reusable table-header resize handle with a wide desktop hit target, horizontal-resize cursor, hover/drag
  feedback, absolute density-independent width callbacks, gesture-completion notification, and double-click reset.
  Column identity, limits, layout calculation, and persistence remain feature-owned.
- Constraint-stable, horizontally scrollable tab rows whose shared surface uses the design-system medium corner
  shape and whose single-line labels retain their measured width under unbounded scroll constraints, truncating
  only at the design-system maximum.
- Controlled split-pane primitives whose ratios remain owned by their feature screens.
- A shared inspector content viewport with minimum readable width, horizontal/vertical scrollbars, explanatory
  paragraph wrapping, and intrinsic-width actions that reflow without feature-specific resize breakpoints; action
  lines can align to either edge within the shared layout.
- A theme-owned overflow menu for secondary actions with accessible naming and disabled-item semantics.
- An opt-in `KNetAnimatedDetailsLayout` that reserves inspector width alongside a list, clips stable-width
  content during reveal/collapse, uses shared motion and responsive tokens, and snaps for reduced motion.
- Button defaults that preserve caller-owned outer spacing and sizing modifiers; explicit caller heights may
  override the density default without forcing padding into the content measurement. Loading buttons disable
  interaction by default, with an explicit opt-in for cancellable operations that must retain click and hand-
  cursor behavior while showing progress.
- One reusable accessible switch whose fixed track geometry and shared thumb inset keep the thumb centered in
  every enabled, disabled, checked, and unchecked feature usage.
- Cohesive text-input APIs that preserve selection, validation/supporting text, password visibility, and focus state.
- One measured text-overflow preview host shared by standard text fields and compact editable key/value cells. It
  owns stationary-hover timing, bounded above/below placement, and display-only popup styling; consumers retain
  their existing single-line editing and data semantics.
- Read-only key/value inspection whose values wrap and grow rows by default so headers, cookies, parameters, and
  form content remain visible. Keys, column headers, and copy actions retain stable top-aligned columns; compact
  consumers may explicitly opt into single-line value truncation. Editable key/value rows remain single-line.
- The non-modal, right-edge `KNetSideDrawer` shell, including responsive size classes, animation,
  surface, and border. Feature modules own drawer state and content.
- Portable clipboard and pointer APIs. JVM confines its AWT clipboard adaptation, hover, secondary-click, cursor,
  and interactive scrollbar implementations to `jvmMain`; Android and iOS use native Compose clipboard access,
  long-press context menus, touch-native scrolling, and no-op pointer-only affordances.

## Does not own

- Feature state, repositories, application use cases, engines, or desktop bootstrap.
- HTTP methods/status semantics, Traffic table columns, payload inspection, or other protocol-specific presentation.

## Dependency rule

Stay independent of product features and infrastructure. Desktop and companion feature UI modules may depend on
it, never the reverse. Common code must not import JVM, Android, or iOS platform APIs; unavoidable adaptation lives
behind common contracts in the matching platform source set.

## Migration direction

Keep as the reusable visual foundation; move feature-specific components back to their feature modules when discovered.
Do not add compatibility aliases, no-op helpers, production catalogs/samples, or speculative component-framework types.
Prefer one cohesive component API over parallel overload families when state such as text selection must be preserved.
Feature code must use the asynchronous foundation clipboard API and must never import AWT clipboard types directly.
Platform-specific behavior should adapt an existing common component contract rather than create a second public
component family.

## Production appearance

KNetApplicationTheme is the shared fixed-dark boundary for desktop and Companion roots. It ignores host appearance;
explicit KNetTheme light/system palettes remain available for previews and future work. See
`docs/fixed_dark_application_appearance.md`.

Inspector summaries, aligned captured fields, disclosure controls and framed-message list/detail viewports are
shared UI-core primitives. The design system owns minimum readable widths, selected-message metadata limits,
scrollbars and single-line overflow previews. Feature modules supply display facts and actions only.
See `docs/traffic_inspector_redesign.md`.

Inspector content identities reset only scroll offsets; they do not key/remount the supplied content tree.
Identity-free callers retain saveable scroll behavior. This supports stable row/message selection while keeping
per-capture view state isolated. See the selection follow-up in `docs/traffic_inspector_redesign.md`.

Inspector scroll offsets reset through retained, measured ScrollState instances. Identity changes do not replace
viewport measurements, and horizontal scrollbar chrome is hidden until a measured viewport proves overflow.
This avoids transient header-height changes during selection; wide/narrow per-frame regressions are documented
in `docs/traffic_inspector_redesign.md`.
