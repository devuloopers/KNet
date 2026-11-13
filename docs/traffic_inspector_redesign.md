# Protocol-aware Traffic inspector

The Traffic inspector uses KNet's existing dark theme and shared inspector components. A persistent endpoint
header owns protocol/method identity, the complete copyable URL or destination, and three captured summary
metrics. Request, Response, Timeline and API Studio export remain available for HTTP exchanges. Technical
identifiers are collapsed by default. All overview sections scroll together; there is no fixed semantic footer.

## Captured facts and supported workspaces

| Traffic | Header metrics | Protocol details and workspace |
| --- | --- | --- |
| HTTP / HTTPS / HTTP/2 | HTTP result, duration, size | Captured connection protocols and source; normal request/response/timeline |
| GraphQL over HTTP | HTTP result, duration, size | Completed parser document, operation/type fields when captured |
| WebSocket | Connection state, handshake status, message count | Negotiated subprotocol; Messages available before the first frame |
| GraphQL WebSocket | Connection state, handshake status, message count | Selected envelope type and operation ID; optional correlated operation name/type |
| gRPC | RPC status, HTTP status, duration | Service/method, captured trailers; Messages with descriptor-backed JSON or hexadecimal fallback |
| Server-Sent Events | Stream state, HTTP status, record count | Events workspace; record/event type, explicit record ID and retry interval when present |
| Opaque TLS | Connection state, uploaded bytes, downloaded bytes | Captured rejection/transport evidence; separate future-connection controls |
| Opaque TCP / UDP / QUIC | Connection state, uploaded bytes, downloaded bytes | Captured transport metadata; no decrypted HTTP or HTTP/3 claims |

These are presentation modes for existing capabilities, not new capture transports. HTTP/2, gRPC, WebSocket,
GraphQL WebSocket and live SSE retain their existing qualification status. Decrypted HTTP/3 remains unavailable.

A successful HTTP upgrade does not close a WebSocket. An HTTP 200 does not establish successful RPC completion.
Missing, malformed, duplicate or out-of-range grpc-status remains Pending or Unavailable. Initial headers support
trailers-only gRPC responses. Captured server 502/503/504 responses retain inspectable headers and bodies;
explicit transport failures have separate diagnostics. Opaque connection states describe transport lifetime,
not encrypted HTTP or billing outcomes. Certificate rejection does not prove pinning.

## Annotation and selection boundaries

Only COMPLETED inspection documents with meaningful fields or summary appear under Protocol details.
SKIPPED candidates are invisible. FAILED analysis is disclosed as unavailable additional details under Technical
details and never changes the captured HTTP result. GraphQL's broad POST candidate filter remains intact.

Canonical exchanges, annotation ownership, message pages and selected message payloads are fenced by exchange
identity. Changing the selected item clears disclosure state and cannot display another exchange's cached payload.
Protocol engines provide display fields through the existing decoder SPI; composables do not parse protocol bodies.

## Message previews and bounded operation correlation

Messages use a shared list/detail viewport. Metadata has its own bounded scroll area so the payload remains usable.
Directions are Sent and Received. Missing and truncated payloads remain explicit, and binary payloads keep a
hexadecimal fallback. SSE counts records because comments and state updates are not dispatched data events.
SSE displays the exact record, including event/data/control lines; an ID inherited from missing history is not invented.

Only selected GraphQL WebSocket operation envelopes request history. The application queries at most 128 earlier
text records, newest first in durable captureSequence order, on the same exchange. A Room read query uses existing
capture metadata; no schema migration or saved policy changes are needed. Message direction sequences and wall
clock timestamps are not used as cross-direction order.

Each context body is capped at 64 KiB, with a total context budget of 1 MiB; the selected preview keeps its existing
1 MiB limit. Missing, truncated, invalid, foreign or ended-operation evidence stops correlation. The nearest preceding
client subscribe for the exact operation ID can supply its name/type. ID reuse selects the newer subscription;
interleaved operations never share one connection-wide operation label. Unavailable context leaves the name absent
while preserving the selected raw payload. Coroutine cancellation is propagated.

## Layout ownership and verification

`:ui:core` owns summary headers, aligned metadata, overflow previews, disclosure controls, list/detail minimum
widths and metadata height limits. Narrow panes use shared horizontal scrolling; metadata labels and values stay
single-line. Explanatory paragraphs use the shared wrapping policy. No new UI dependency is introduced.

Automated qualification covers protocol/state presentation, annotation filtering, missing RPC status, multiplexed
operation history and gaps, retained body truncation, context limits and cancellation, Room ordering/scoping,
HTTP error responses, protected actions, narrow/larger-font layouts and headless renders. Verification does not
launch the desktop application or open the user's capture database. Physical-device qualification remains separate.

## Automated results (2026-11-12)

Thirty regression cases were added. The completed validation ran 454 tests with no failures, errors or skips:

| Suite | Tests |
| --- | ---: |
| Shared UI core | 24 |
| Traffic | 98 |
| HTTP panel | 37 |
| Desktop app shell | 26 |
| Desktop application | 62 |
| gRPC | 22 |
| GraphQL WebSocket | 21 |
| WebSocket | 27 |
| SSE | 28 |
| Desktop data / Room | 109 |

Desktop compilation, UI-core Android and iOS simulator compilation, all six architecture gates, qualification
ledger validation and whitespace checks passed. Final logs are `/tmp/knet-inspector-verification.log` and
`/tmp/knet-inspector-final-gates.log`; the final Traffic render-fixture recheck is
`/tmp/knet-inspector-render-recheck.log`. Application-protocol headless renders are in
`/tmp/knet-inspector-redesign-renders`; protected/opaque renders use the existing Traffic UI test report directory.
The current ledger records 173 requirements and 93 open software requirements across broader network/protection
work. These passing suites qualify this change, not every product capability or a physical platform.

## Selection stability follow-up (2026-11-12)

The first redesign remounted entire inspector subtrees when capture/message identity changed. Cached selections
also published an empty loading state before asynchronous cache restoration, and their annotations were briefly
cleared when the observer restarted. Response loading could switch through the No Response view. These produced
visible flashes despite the selected data already being available.

The pane now stays mounted. Shared viewport components accept a content identity to reset only their scroll state;
existing callers without an identity retain their prior saveable scroll behavior. Disclosure remains capture-local.
Cached terminal details, including annotations, are selected in one state publication under the existing LRU byte
budget. Active exchanges refresh on revisit. Same-item refreshes retain prepared data, and real annotation deletion
still removes obsolete fields.

Response tabs remain visible while a new payload is prepared. Message-page invalidation preserves an unchanged
selected preview, clicking its selected row does not clear it, and late async results can only commit to the same
exchange/message/parent selection. Missing new details never borrow another capture's body.

Nine regression cases were added and the existing cache-reuse case now checks the immediate selection state.
All 194 relevant UI/viewmodel tests passed (Traffic 106, HTTP panel 38, UI core 24, app shell 26), plus desktop,
shared Android/iOS UI compilation and the six architecture checks. The final log is
`/tmp/knet-inspector-stability-final.log`. The earlier protocol/storage qualification remains as recorded above.

## Scrollbar measurement follow-up (2026-11-12)

The remaining selection jump came from geometry rather than body loading. An unmeasured ScrollState starts with
an unconstrained maximum offset; that value is not proof of overflow. Recreating scroll states for a new identity
briefly displayed scrollbar spacing. In the wide test fixture the header grew from 136px to 152px, moving navigation
from y=138 to y=154, then returned after measurement.

Inspector scroll states now keep their viewport measurements and reset only offsets without animation. Horizontal
scrollbar chrome requires a measured viewport and real overflow. This preserves real narrow-pane scrolling and
avoids the temporary header-height pulse. Three regression cases reproduce the original failure and check every
selection frame, wide/narrow header geometry and absent unmeasured chrome. The previous cache, selection identity,
scroll reset and payload-isolation regressions remain green.

All 197 relevant tests passed (Traffic 109, HTTP panel 38, UI core 24, app shell 26), along with desktop/shared
Android/iOS UI compilation and architecture checks. Evidence is in `/tmp/knet-inspector-geometry-baseline.log`
and `/tmp/knet-inspector-geometry-verification.log`. The desktop application was not launched during verification.

## GraphQL query shortcut (2026-11-12)

GraphQL Overview now displays operation metadata and a View query action. It omits the duplicate raw query summary
and request target, which is already present in the endpoint header. This is a display projection: original annotations
remain available to matching and other consumers. Shared application constants identify the GraphQL document kind
and legacy target field without binding Traffic UI to a parser implementation or changing the stored schema.

The action selects Request > Body > Query explicitly. GraphQLBodyViewer, SmartBodyViewer and RequestViewPanel
accept optional controlled GraphQL tab selection, while existing uncontrolled consumers keep local state. The
shortcut resets Query even after the caller previously viewed Variables, Extensions or Raw JSON, and switches the
outer request sub-tab from Headers, Cookies or Params to Body.

Availability uses only the selected exchange's prepared request format. A loading, missing, foreign or unsupported
query view disables navigation and exposes an accessible hover description. Truncated retained queries can be opened
with the existing body-preview warning. A raw JSON batch does not pretend to have the existing single-query viewer;
its retained payload remains available through the normal Request body view. No query is reconstructed from metadata.

Nine regression cases cover compact projection, intact original annotations, unrelated summaries, availability,
foreign selection, raw/truncated/batched inputs, navigation from other sub-tabs, controlled/local viewer behavior
and disabled accessibility actions. All 225 relevant tests passed: Traffic 115, HTTP panel 41, UI core 24, app shell 26
and protocol engine 19. Desktop/shared Android/iOS UI compilation, architecture and existing frame-geometry tests
also passed. The compact render is `/tmp/knet-graphql-query-shortcut-overview.png`; verification evidence is
`/tmp/knet-graphql-query-shortcut-verification.log`.

## Single divider follow-up (2026-11-13)

Traffic's table and inspector share one continuous theme-owned 1 dp vertical divider. The shared horizontal split
has an opt-in `SINGLE_LINE` style with an overlaid 8 dp resize target; hovering, focusing or dragging colors the
same line blue without widening it or changing pane geometry. Existing split callers retain their gutter style.
The inspector root, tabs, table header and statistics footer no longer contribute adjoining vertical borders.
The table keeps its internal scrollbars and row-selection outline.

Verified 152 tests (32 shared UI/core, 120 Traffic), desktop compilation, all six architecture checks and shared
UI Android/iOS simulator compilation. Pixel-level render checks cover one logical pixel at densities 1 and 2,
wide/compact actual Traffic composition, hover exit and stable sizing. Pointer tests begin within the invisible
hit area, continue outside the moving target and clamp at the configured limits; keyboard focus/arrows retain
accessible range semantics. Reviewed `/tmp/knet-traffic-single-divider-1440.png` and
`/tmp/knet-traffic-single-divider-1024.png`, plus shared hover/density renders.
Final log: `/tmp/knet-traffic-divider-check.log`.

## Scrollbar clearance follow-up (2026-11-13)

The Traffic list now reserves the UI-core measured-overflow scrollbar gutter before resolving column widths.
Both header and rows use that content width and horizontal offset, with clipping before the scrollbar lane.
Header background/separators remain full-width and the horizontal scrollbar track stops before the lane.
Short/empty lists reclaim the gutter; empty lists also suppress stale scrollbar state from the previous page.
The vertical thumb is inset inside the gutter so its edge does not compete with the split-pane resize target.

Verified **155 tests (UI core 32, Traffic 123), zero failures/errors/skips**, desktop compilation, all six
architecture checks, shared UI Android compilation and iOS simulator compilation. Rendered pixel checks cover
text and selected-row clearance at wide/narrow larger-text sizes, horizontal/vertical scrolling, and list-size
transitions. Pointer interaction verifies the thumb scrolls beside the divider without resizing the panes.
Reviewed `/tmp/knet-traffic-scrollbar-wide.png` and
`/tmp/knet-traffic-scrollbar-narrow-scrolled-larger-text.png`. Log: `/tmp/knet-traffic-scrollbar-check.log`.

## Complete selected-row frame (2026-11-13)

Traffic paints its selected-row frame at the visible row viewport boundary, after scrollbar clearance and before
horizontal scrolling. The source cells and selected fill retain their shared column offset, while all four frame
edges remain visible even when the table is wider than the pane. Lazy row offsets move the frame with vertical
scrolling; partially visible rows are clipped within the list. The existing scrollbar lane remains clear.
The frame adds no hit target and does not change row selection, context menus, or column sizing.

Validation: two pixel-based regressions failed on the missing right edge before the fix and passed afterward.
All 125 Traffic tests, desktop compilation, and six architecture checks passed. Wide/narrow frame renders
confirm four visible edges and a clear scrollbar lane; horizontal offsets and vertical/selection changes are
covered. Log: `/tmp/knet-traffic-selection-check.log`.
