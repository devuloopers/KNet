# `:ui:desktop:app`

## Responsibility

Provides the desktop application shell, navigation host, window scaffold, notifications, and global status presentation.

## Owns

- Top-level routes, including Connect Device hosting, navigation state, shell layout, and global UI surfaces.
- Sidebar grouping and ordering in both expanded and collapsed modes: Traffic and API Studio; Intercepts,
  Network Conditions and Protected Traffic; Connect Device and Certificates. Inset dividers separate those
  groups; Settings remains anchored above the existing branding footer.
- Workspace-level placement of the Live Intercept drawer above destination-owned drawers, gated until its canonical Traffic row is projected.
- Composition of the Network Conditions quick control into Traffic's toolbar slot, sharing the existing feature
  ViewModel and navigation route without adding policy ownership or infrastructure dependencies to Traffic.
- Composition of the same feature's live rate indicator into Traffic's footer, with navigation explicitly selecting
  Overview. The workspace host keeps one telemetry lifecycle active across Traffic and Network Conditions and stops
  it on departure/disposal; changing between the two does not reset the sampler.

## Does not own

- Process startup, engine lifecycle, repositories, feature business rules, canonical traffic data, or Koin binding declarations.

## Dependency rule

Compose feature screens and application-facing state; do not depend directly on concrete data or engine implementations.

## Migration direction

Navigation may resolve product-provided ViewModels through Koin Compose, but every binding declaration lives in `:products:desktop`.

MainWindow uses UI core's fixed-dark KNetApplicationTheme, including its shared dialog and overlay hosts. The legacy
global toolbar has no theme selector or theme callbacks. Host appearance does not select the application palette.
