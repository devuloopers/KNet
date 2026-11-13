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

## Multi-device workspace and control sessions (2026-11-13)

The device workspace ViewModel consumes application use cases only. It combines durable names with current presence, persists selection and owns one selected-source speed subscriber while Traffic is visible.

Offline tabs with retained traffic expose Close and share the toolbar Clear confirmation. The target identity is captured independently of selection; cancellation is non-destructive, and reconnecting during confirmation keeps the tab. Connected Close is disabled with hover help. Hidden selected tabs fall back to Other traffic without stealing focus on later reconnect. Pairing remains saved.

Device tab presentation separates the existing display name and unique identity suffix for compact-tab styling. Source keys, roster visibility, status, selection and Close/Clear callbacks retain their existing behavior.

Tab label cleanup removes a Companion-generated trailing four-character suffix only when it matches that exact identity. The same normalized label feeds the tab and confirmation; original names, pairing records and case-sensitive identity keys remain unchanged. Collision-safe desktop suffixes are retained.
