# `:ui:core`

## Responsibility

Provides the shared Compose design system, foundations, themes, layouts, and reusable visual components.

## Owns

- Design tokens, palettes, typography, responsive layout primitives, and generic UI components.
- The non-modal, right-edge `KNetSideDrawer` shell, including responsive size classes, animation,
  surface, and border. Feature modules own drawer state and content.
- Portable clipboard and pointer APIs whose desktop-only AWT adaptation is confined to `jvmMain`.

## Does not own

- Feature state, repositories, application use cases, engines, or desktop bootstrap.

## Dependency rule

Stay independent of product features and infrastructure. Feature UI modules may depend on it, never the reverse.

## Migration direction

Keep as the reusable visual foundation; move feature-specific components back to their feature modules when discovered.
Feature code must use the asynchronous foundation clipboard API and must never import AWT clipboard types directly.
