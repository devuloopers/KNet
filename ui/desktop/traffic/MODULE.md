# `:ui:desktop:traffic`

## Responsibility

Owns the live traffic table, filtering, selection, inspector coordination, and traffic-specific desktop presentation state.

## Owns

- Traffic ViewModel, table/toolbar UI, selection, presentation-owned filters, bounded rolling keyset-page
  window, generic semantic annotation presentation, typed live-interception projection, and lean
  body-free `TrafficRowUiState` values.

## Does not own

- Captured bytes, canonical exchanges, persistence, proxy lifecycle, protocol parsers, or product DI bindings.

## Dependency rule

Query retained traffic through application use cases and `TrafficQueryPort`; control capture attachment, required
proxy configuration rebinding, and clear history through application use cases. Never retain unbounded body
bytes in UI state. Detail uses bounded body previews, bounded decompression, identity-keyed preparation, and
an eight-entry/16 MiB byte-weighted presentation cache.

## Current state

Traffic list/detail, capture control, proxy lifecycle observation, semantic annotations, pending breakpoints,
and clear orchestration use application use cases. Start/Stop changes capture attachment while the running
listener continues forwarding; only a proxy-port change performs a full configuration rebind. Active breakpoint
candidates are joined to durable rows only by canonical `ExchangeId`; a body-free provisional row is used until
capture metadata arrives, remains visible despite ordinary filters, and is replaced without duplication. Paused
rows display `In Progress`, a warning highlight, and a typed phase/rule marker. Room queries all retained
sessions by default, so restart and capture rotation do not hide older stored traffic. Search, method, status,
scheme, and application-protocol filters execute in storage; HTTP/HTTPS are typed schemes while HTTP/2 and
HTTP/3 are typed application protocols. Semantic formats such as GraphQL, SSE, gRPC, and future WebSocket
streams are not guessed from URLs.

The filter toolbar passes typed filter values directly to the shared dropdown component. Count chips and filter
anchors use the same compact height, while each dropdown keeps a stable finite width across placeholder and
selected labels. Column visibility uses the generic KNet multi-select dropdown; Traffic supplies typed
`TrafficColumn` values and visibility callbacks while UI core owns its checkbox presentation, popup, motion,
and dismissal behavior.

The rolling list holds at most 1,000 metadata-only rows while keyset cursors continue through larger stored
history. Live invalidations are conflated with a bounded refresh interval instead of debounced indefinitely.
Rows keep only table metadata and one content-type value; ordered headers, repeated query pairs, response
heads, annotations, and bodies come from the selected canonical exchange. Clear is serialized, affects only
stored traffic, and leaves the forwarding listener available. Assembly lives in `:products:desktop` under
`di/traffic`.
