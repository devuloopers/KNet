# `:ui:desktop:traffic`

## Responsibility

Owns the live traffic table, filtering, selection, inspector coordination, and traffic-specific desktop presentation state.

## Owns

- Traffic ViewModel, table/toolbar UI, selection, presentation-owned filters, bounded rolling keyset-page
  window, generic semantic annotation presentation, typed live-interception projection, and lean
  body-free `TrafficRowUiState` values.
- Stable storage-owned serial numbers, separate loaded-versus-available counts, and auto-scroll policy that
  reacts to a genuinely newer sequence rather than a page-size change.
- Separate transport and semantic method presentation: filters retain the canonical HTTP method while the
  table renders the shared request descriptor label such as `POST` or `GQL`.
- Optional Protocol, Stream, and Source columns backed by canonical exchange data; Source distinguishes API Studio from
  ordinary proxy clients, while Protocol prefers the observed upstream response and falls back to the client request.
- Protocol-aware Host-column presentation omits redundant HTTP `:80` and HTTPS `:443` ports while retaining
  non-default ports and the complete canonical authority outside the compact table label.
- One typed column-width layout shared by headers and rows. Path fills remaining viewport space until explicitly
  resized; every column has practical bounds, overflow uses synchronized horizontal scrolling, drag completion
  persists through workspace use cases, and individual or complete resets restore defaults. Header labels and row
  values share design-token-derived content insets, preserving alignment while clearing resize affordances. The
  final visible column keeps an invisible idle resize target that reveals its accent affordance on hover or drag,
  avoiding a redundant terminal divider without removing last-column sizing.
- Asynchronous breakpoint-draft requests by `ExchangeId`, retaining only the application-prepared canonical
  rule and generic protocol field values while the existing editor is visible.

## Does not own

- Captured bytes, canonical exchanges, persistence, proxy lifecycle, protocol parsers, or product DI bindings.

## Dependency rule

Query retained traffic through application use cases and `TrafficQueryPort`; control capture attachment, required
proxy configuration rebinding, and clear history through application use cases. Never retain unbounded body
bytes in UI state. Detail uses bounded body previews, bounded decompression, identity-keyed preparation, and
an eight-entry/16 MiB byte-weighted presentation cache.

## Current state

Traffic list/detail, capture control, proxy lifecycle observation, semantic annotations, pending breakpoints,
and clear orchestration use application use cases. Start Capture/Pause Capture changes capture attachment while the
running listener continues forwarding external clients; API Studio treats the paused state as direct execution.
Only a proxy-port change performs a full configuration rebind. Active breakpoint
candidates are joined to durable rows only by canonical `ExchangeId`; a body-free provisional row is used until
capture metadata arrives, remains visible despite ordinary filters, and is replaced without duplication. Paused
rows display `In Progress`, a warning highlight, and a typed phase/rule marker. Room queries all retained
sessions by default, so restart and capture rotation do not hide older stored traffic. Search, method, status,
scheme, and application-protocol filters execute in storage; HTTP/HTTPS are typed schemes while HTTP/2 and
HTTP/3 are typed application protocols. Traffic contains no GraphQL, SSE, gRPC, or WebSocket branch: the shared
descriptor strategy pipeline can use canonical metadata immediately, then persisted semantic annotation kinds
refine retained rows through one bounded batch observation without rereading bodies.
Creating a breakpoint from a Traffic row delegates semantic detection to the application use case. The dialog
opens only after one immutable draft is ready and receives protocol-neutral definitions/values, so GraphQL and
future format-specific defaults do not add branches or parser dependencies to this module.

The filter toolbar passes typed filter values directly to the shared dropdown component. Count chips and filter
anchors use the same compact height, while each dropdown keeps a stable finite width across placeholder and
selected labels. The existing search state is rendered first in this filter hierarchy, immediately before the
protocol count pills, and shares the horizontally scrollable constrained-width group without duplicating filter
logic. Column visibility uses the generic KNet multi-select dropdown; Traffic supplies typed
`TrafficColumn` values and visibility callbacks while UI core owns its checkbox presentation, popup, motion,
and dismissal behavior.

The rolling list holds at most 1,000 metadata-only rows while keyset cursors continue through larger stored
history. Loading a page never renumbers retained rows; the footer distinguishes the loaded window from the
exact matching total. Live invalidations are conflated with a bounded refresh interval instead of debounced indefinitely.
The virtualized table overlays the shared theme-aware vertical scrollbar only while its measured rows overflow the
viewport. Header separators use the UI-core resize handle while this feature owns typed widths and constraints.
Header and rows resolve one layout and share a finite horizontal scroll state, whose scrollbar appears only when
the constrained columns exceed the viewport. Live drag state remains presentation-only; DataStore is updated once
when the gesture completes. The Columns menu and separator double-click provide complete and individual resets.
Rows keep only table metadata and one content-type value; ordered headers, repeated query pairs, response
heads, annotations, and bodies come from the selected canonical exchange. Clear is serialized, affects only
stored traffic, and leaves the forwarding listener available. Assembly lives in `:products:desktop` under
`di/traffic`.
The Overview panel always exposes client protocol, upstream protocol, and source independently, so HTTP/1
translation is not misreported as one ambiguous value. Protocol and Source remain hidden table columns by
default and can be enabled through the existing generic column menu.
HTTP/2 filtering matches either observed protocol leg, and the optional Stream column plus Overview stream field
read only the stored canonical `StreamId`; presentation never infers a stream from row order or connection state.
