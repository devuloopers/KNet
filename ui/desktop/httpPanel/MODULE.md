# `:ui:desktop:httpPanel`

## Responsibility

Provides reusable HTTP request/response inspection and editing panels for Traffic, API Studio, and breakpoints.

## Owns

- Header, body, overview, timeline, GraphQL, and smart body presentation components.
- HTTP-specific method colors and response-status badges shared by desktop HTTP consumers.
- UI-level mapping from shared traffic data to bounded viewer state.
- A single inspection-text projection that joins formatted `BodyFormat.JsonStream` records with visible
  boundaries before passing them to the existing read-only JSON editor.
- Shared inspector sub-tabs and a small GraphQL editor wrapper that composes the canonical
  `StructuredPayloadState.GraphQL` payload with only the active UI tab.

## Does not own

- Canonical request/response types, body storage, formatting engines, network execution, or product DI bindings.
- Generic themes, dropdowns, inputs, tables, or other design-system primitives owned by `:ui:core`.

## Dependency rule

Consume shared snapshots plus explicit body/formatter services; remain reusable across feature UIs.

## Current state

Shared `RequestHead`, `ResponseHead`, `ExchangeTimings`, body specs, and inspector tabs are the common
inputs while edit state remains explicit feature patches/drafts. The module has no parallel response
or timing specification. Timeline rows use one responsive label-column measurement so every waterfall
track remains aligned while preserving the original desktop width. Assembly for payload strategies lives
in `:products:desktop` under `di/httppanel`. JSON streams reuse the same `CodeLanguage.JSON` tokenizer,
folding, search, and editor surface as single JSON documents; this module does not define an NDJSON language.
`PayloadInspectionSpec` exposes a conservative retained-byte estimate so feature-owned caches can be bounded
by payload weight instead of entry count alone. Request, response, body-mode, and GraphQL navigation reuse
the constraint-stable tab primitive from `:ui:core`; GraphQL authoring allocates the remaining toolbar width
to its scrollable tabs while preserving the operation-name field.
