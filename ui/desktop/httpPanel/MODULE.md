# `:ui:desktop:httpPanel`

## Responsibility

Provides reusable HTTP request/response inspection and editing panels for Traffic, API Studio, and breakpoints.

## Owns

- Header, body, overview, timeline, GraphQL, and smart body presentation components.
- HTTP-specific method colors and response-status badges shared by desktop HTTP consumers.
- UI-level mapping from shared traffic data to bounded viewer state.
- A single inspection-text projection that joins formatted `BodyFormat.JsonStream` records with visible
  boundaries before passing them to the existing read-only JSON editor.
- The HTTP-panel Prettify header contribution and its custom-command routing. Request, response, and GraphQL
  formats own the formatting implementation; the generic editor only renders the declaration.
- Shared inspector sub-tabs and a small GraphQL editor wrapper that composes the canonical
  `StructuredPayloadState.GraphQL` payload with only the active UI tab. Query, Variables, and Extensions retain
  independent editor sessions so their undo, caret, and selection state cannot leak across tab switches.
- Request-authoring primary tabs retain content-responsive label widths and horizontal overflow while using the
  same design-system horizontal inset as the surrounding URL, body-mode, and editor controls. Their inset surface
  provides grouping without an additional full-width divider. Read-only Traffic inspector tab placement and
  separators remain caller-owned and edge-to-edge where intended.
- Params, Headers, and Cookies reuse one request-panel placement policy that insets and clips their editable
  key-value table surfaces to the same medium spacing and corner tokens as the primary tabs.
- Reusable typed request-editor rows; query, header, cookie, and structured-body entries retain identity and
  enabled state instead of being projected through lossy `Pair` lists.
- Packaged default response-editor documents, including the HTML template loaded from module resources rather
  than embedded in Kotlin presentation models.

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
