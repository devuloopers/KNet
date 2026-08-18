# `:ui:desktop:app`

## Responsibility

Provides the desktop application shell, navigation host, window scaffold, notifications, and global status presentation.

## Owns

- Top-level routes, navigation state, shell layout, and global UI surfaces.

## Does not own

- Process startup, engine lifecycle, repositories, feature business rules, canonical traffic data, or Koin binding declarations.

## Dependency rule

Compose feature screens and application-facing state; do not depend directly on concrete data or engine implementations.

## Migration direction

Navigation may resolve product-provided ViewModels through Koin Compose, but every binding declaration lives in `:products:desktop`.
