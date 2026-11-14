# `:ui:desktop:traffic`

## Responsibility

Owns the live traffic table, filtering, selection, inspector coordination, and traffic-specific desktop presentation state.

## Owns

- Traffic ViewModel, table/toolbar UI, selection, presentation-owned filters, bounded rolling keyset-page
  window, generic semantic annotation presentation, typed live-interception projection, and lean
  body-free `TrafficRowUiState` values.
- A constrained toolbar slot immediately before Auto Scroll for the shell-composed global Network Conditions
  shortcut. Traffic owns placement, not policy state; capture actions remain visible while long runtime status
  labels yield width to the shared controls.
- A shell-composed actual-throughput slot in the existing statistics footer, next to the visible-row byte total.
  UI core owns fixed-height overflow. Traffic filters and capture attachment never manufacture or reset rate values.
- Stable storage-owned serial numbers, separate loaded-versus-available counts, and Auto scroll Smart/Manual
  modes. Smart follows only at the exact top; Manual exposes a persisted switch. Per-source reading checkpoints
  and a compact new-arrival footer action preserve history and selection while new traffic arrives. Fully visible
  rows advance a per-source last-seen checkpoint; explicit Jump to latest acknowledges the remaining batch.
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

Query retained traffic through application use cases and `TrafficQuery`; control capture attachment, required
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
Creating a breakpoint from a Traffic row delegates semantic detection to the application use case. The shared side drawer
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
exact matching total. Paused windows freeze admission of new rows; indexed insertion checkpoints count matching
HTTP and opaque arrivals beyond the loaded window without counting lifecycle updates. Fully visible rows advance
one stable last-seen position; near the old window head, adjacent newer paging preserves the visible anchor and
loads every intermediate block. Smart resumes only at the actual newest matching record, and typed older anchors
preserve paging after row-window trimming. Live invalidations are conflated with a bounded refresh interval instead of debounced indefinitely.
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

The opaque/TLS inspector separates immutable captured diagnostics from a live future-connection policy read.
A dedicated TrafficProtectionViewModel observes one selected flow, dispatches exact-destination Tunnel/Inspect/Block
rules or Use default through application use cases, serializes mutations and reports feedback in place. Right-click
actions use the same owner without navigating away. Live temporary fallback is shown separately from saved rules
and captured history; automatic tunnels explain the earlier rejection. Manual choices remain authoritative and reset
removes only the exact destination override. See `docs/automatic_tls_fallback.md`.
Diagnostic copy derives from typed captured evidence and never claims pinning is proven. Technical metadata is
collapsed initially; legacy automatic provenance is labelled historical. Shared inspector components own paragraph
wrapping, overflow, scrollbars and action reflow.

Saved action selection is reconstructed from the enabled equivalent destination rule and the current winning
rule. Active actions retain a selected visual and accessible state while redundant submission is disabled.
Inherited/global handling can still be promoted to an exact saved rule. Overridden choices
are labelled explicitly. Selection never depends on the transient success message or a per-row click cache.

All single-select Traffic filters inherit UI core's selected-label dropdown width: they fit the current label/placeholder
and animate both expansion and contraction without feature-specific sizing options. Width measurement, limits, chevron spacing, easing and reduced-motion handling stay
in UI core. Popup width remains independent so every option is readable even when the anchor displays only Type.
The existing shared filter-group horizontal scrolling handles constrained windows.

Opaque connections use one shared Active/Closed status contract in the table and inspector; other typed terminal outcomes remain distinct. These labels describe transport lifetime, not encrypted HTTP/business success. Live counters refresh through canonical feed generations.

Typed raw inactivity/write timeouts display Timed Out and identify the applicable deadline in the inspector.
Generic I/O failures remain Failed; historical records are never reinterpreted as timeouts from duration alone.

Terminal opaque Technical details presents recorded Closed by, Close reason and Close observed at. Device/Server
mean the first observed peer side; KNet requires explicit local intent. Legacy/unproven evidence remains Unknown
with no invented timestamp. The existing shared inspector disclosure/scrolling owns layout and overflow.

The protocol-aware inspector uses one persistent endpoint/metric header and one scrolling grouped overview.
Only completed meaningful annotation documents appear inline; skipped checks are invisible and failed analysis
is secondary technical information. IDs remain collapsed. HTTP, GraphQL, gRPC, WebSocket, GraphQL WebSocket
and SSE retain distinct transport/RPC/stream outcomes. Message navigation is available before the first frame;
SSE uses Events with record counts. Protocol decoders own selected-message fields and optional bounded operation
correlation. Opaque TLS retains future-connection actions separately from immutable capture evidence; UDP/QUIC
remains metadata-only. All responsive policy stays in UI core. See `docs/traffic_inspector_redesign.md`.

Selection applies cached terminal detail and annotations atomically. Same-item refreshes preserve prepared content;
active exchanges are refreshed rather than cached as complete. Entire inspector subtrees remain mounted while
UI core resets only identity-owned scroll state. Live message-page refreshes retain unchanged selected previews,
and async message payloads commit only to their exact current exchange/message/parent selection. See
`docs/traffic_inspector_redesign.md` for selection stability qualification.

GraphQL Overview projects compact operation metadata with a guarded View query action. It navigates explicitly to
Request > Body > Query using the matching prepared request, and omits duplicate query/target text only from the display.
Other annotations retain their summaries; source annotations and header URLs remain intact. See
`docs/traffic_inspector_redesign.md`.

## GraphQL GET parameter preparation

Traffic prepares GET query data off-thread into `InspectorPreparedState.requestParameterSpec`,
includes its size in cache accounting and uses the existing exchange identity/cancellation fences.
View query targets Params for GET and Body for POST, resetting the shared Query sub-tab. The source
URL is unchanged for header copy and cross-feature transfer; missing/ambiguous data remains honest.

## Persisted-query updates

Selected request preparation includes the absolute endpoint and observes verified catalog revisions. Derived cache
entries are invalidated without clearing the selected inspector. Current read-only hash/source/operation facts and
query availability can refresh Overview without rewriting captured annotations or request bytes.
See `docs/graphql_persisted_queries.md`.

## Single table/inspector divider

Traffic opts into the UI-core single-line split boundary. Inspector chrome, table header and statistics footer
omit adjoining side frames through shared primitives; the splitter alone paints the vertical seam. The larger
invisible resize target overlays the pane edges, with no visible gutter or hover-induced width change.
Internal scrollbars and selected-row outlines remain owned by the table. Rendered/pointer/keyboard verification
is recorded in `docs/traffic_inspector_redesign.md`.

Traffic measures and horizontally scrolls headers/rows within the same shared scrollbar gutter. Text and
selection paint are clipped before the lane, header chrome extends across it, and the horizontal track excludes
it. The vertical thumb is inset clear of the neighboring splitter target; empty lists do not retain stale chrome.
The selected-row frame is painted at the visible content boundary, so horizontal scrolling cannot hide its
left/right edges. Lazy row positions keep it aligned during vertical scrolling and selection changes.
See the scrollbar follow-up in `docs/traffic_inspector_redesign.md`.

## Multi-device workspace and control sessions (2026-11-13)

Traffic ViewModel scopes every query/provisional row and fences asynchronous page/export work with a source generation. At most four body-free workspace windows retain filters, selection and scroll; prepared payloads remain in the existing bounded cache.

The shell routes offline Clear through the same confirmation as tab Close. `clearSource` accepts an explicit target so a background tab can be cleared without selecting it, changing current filters or reloading a sibling workspace. Repeated clicks while clearing are ignored; failures surface through the existing error banner and retained history remains available for retry.

Auto scroll behavior, ownership and automated qualification are documented in
[`docs/traffic_auto_scroll.md`](../../../docs/traffic_auto_scroll.md).
