# `:ui:desktop:httpPanel`

## Responsibility

Provides reusable HTTP request/response inspection and editing panels for Traffic, API Studio, and breakpoints.

## Owns

- Header, body, overview, timeline, GraphQL, and smart body presentation components.
- UI-level mapping from shared traffic data to bounded viewer state.
- Shared inspector sub-tabs and a small GraphQL editor wrapper that composes the canonical
  `StructuredPayloadState.GraphQL` payload with only the active UI tab.

## Does not own

- Canonical request/response types, body storage, formatting engines, network execution, or product DI bindings.

## Dependency rule

Consume shared snapshots plus explicit body/formatter services; remain reusable across feature UIs.

## Current state

Shared `RequestHead`, `ResponseHead`, `ExchangeTimings`, body specs, and inspector tabs are the common
inputs while edit state remains explicit feature patches/drafts. The module has no parallel response
or timing specification. Assembly for payload strategies lives in `:products:desktop` under
`di/httppanel`.
