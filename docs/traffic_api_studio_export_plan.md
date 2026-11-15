# Traffic and API Studio export plan

This dated initial scope is followed by the 2026-11-15 workflow increment in
[implementation_plan.md](implementation_plan.md): multi-selection and file-picker import are now included.
The separate drag-and-drop action chooser remains pending.


Status: COMPLETED (2026-11-15) for implementation and automated qualification. Native/physical and
named foreign-tool acceptance remain explicitly separate in [the report](traffic_export_qualification.md).

## Scope and agreed entry points

Deliver one shared export workflow for captured Traffic and API Studio execution results. Offer a
versioned KNet Capture archive (`.knetcapture`) and interoperable HTTP Archive (`.har`). Import is a
separate later increment. CSV, Postman, settings/rule export and direct foreign-native formats are
outside this increment.

The user clarified that the requested menu is Traffic's existing row context menu, currently opened
with right-click. Ordinary left-click continues selecting the row and opening its inspector.

- Traffic row menu: add **Export this item...** for the captured HTTP exchange or opaque connection.
  Capture that exact item and its source when the action is invoked; a subsequent selection change
  cannot redirect the export.
- Traffic toolbar: provide **Export traffic...** for the selected source, with all retained records or
  current filtered results. The dialog can explicitly select all sources. Do not add multi-selection
  merely for export; selected-items bulk export is deferred until a selection contract exists.
- API Studio: place a compact secondary **Export...** button at the far right of the response summary
  toolbar, grouped with the existing Copy and Clear actions. Keep the button mounted and the action
  area stable when unavailable. Do not place it beside Send or make the request URL row wider.
- Live HTTP/SSE and native protocol result panels expose the same response/session action placement
  through shared design-system behavior. A pending/empty response view still reserves its action slot.
- Narrow layouts inherit the shared toolbar/overflow strategy. No new feature-owned minimum sizes,
  wrapping rules or whole-panel remounts.

Conceptual API Studio response toolbar:

```text
200 OK | HTTP/2 | Time: 439 ms | Size: 1.15 KB       Copy   Export...   Clear
```

## Export eligibility

Traffic exports any retained record in its frozen scope, including failures, opaque TLS/UDP metadata
and incomplete records. A row does not need an HTTP response to be useful capture evidence. A bulk
action is disabled when the authorized scope has no records.

API Studio export is tied to server-response evidence from the currently selected execution, not
merely to a non-null inspector state, a filled request body or the presence of an outbound message.

| API Studio state | Behavior |
|---|---|
| New, restored or opened draft that has not been sent | Disabled; explain that a response is required. |
| Send/Connect/Invoke started; no server response | Disabled, including while an older response remains visible. |
| HTTP response completes, including HEAD/204/304 and zero-byte bodies | Enabled. |
| Actual HTTP 4xx/5xx response, including 502/503/504 | Enabled; unsuccessful status is still a response. |
| Validation, script, DNS, connect or TLS failure before a response | Disabled; diagnostics alone do not satisfy this gate. |
| Response started but transfer fails/cancels | Enable retained response evidence as explicitly partial. |
| Current response cleared, another document selected, or a new execution begins | Disable/recompute for the new owner; never reuse an older run's eligibility. |
| Request edited after a response | Export the immutable request sent for that displayed result, never the edited draft. |

For ordinary finite HTTP, wait for completion to offer the complete body. A streaming response may be
exported while active once server-response evidence exists, labelled a snapshot:

- SSE/live HTTP: a received response head qualifies; preserve available raw bytes/events.
- WebSocket/GraphQL WebSocket: an actual server handshake response, including a rejected upgrade, or
  a received message qualifies. A client-generated Started/connected label alone is not response proof.
- gRPC: received response headers, an inbound message, or a server terminal status/trailers qualifies.
  Distinguish server rejection from local transport errors, including trailers-only responses.
- Disconnect/cancellation after qualifying evidence retains an exportable partial result until the
  response is explicitly cleared or replaced. Clearing only the visible message window must not
  silently erase the execution export; its retained data/capture limits remain explicit.

The existing HTTP `ResponseInspectorState.hasResponse` excludes gateway-status responses and the UI
stores formatted strings/maps. Neither is a sufficient archival contract. Add typed execution-owned
evidence, original request/response metadata and bounded payload ownership below presentation.

## Content and formats

### KNet Capture

Define a versioned portable manifest and stream payload entries into a compressed archive. The schema
must not be a copy of Room entities/database files, and must be documented for the later importer.
Retain original identifiers as archive-local provenance; they never authorize actions on live devices.

Include what KNet actually retained:

- Actual sent request after variable/script preparation and corresponding response, ordered duplicate
  headers, cookies, binary/text bytes, content encodings and measured timings.
- GraphQL GET/POST parameters, operation information and available persisted-query resolution with
  provenance. Do not manufacture source from a hash or overwrite the captured wire request.
- WebSocket/GraphQL WebSocket, gRPC and SSE messages/events with sequence, direction and timestamps,
  trailers/close information and available raw payload references.
- Opaque TLS/TCP/UDP connection metadata, recorded lifecycle/failure/termination evidence, recorded
  policy explanation and byte counts. A tunnel never acquires fabricated decrypted HTTP data.
- Device grouping and source/protocol information as historical display metadata, with unavailable
  identity reported honestly. No pairing credentials, CA private keys or device registration authority.
- Explicit complete/active-at-snapshot/partial/redacted/missing/truncated payload states, retained and
  observed byte counts, schema and producer versions, and per-entry integrity metadata.

Recorded rule explanations are historical facts only. Do not serialize executable scripts, active
settings, rule definitions/activation, Companion registrations or live connection credentials.

### HAR

Serialize compatible HTTP exchanges, including GraphQL over HTTP, with correct ordered headers,
query/body representation, binary encoding, cookies and truthful available timings. Preserve failed or
partial HTTP evidence using documented extensions where appropriate. Never invent status codes,
decrypted payloads or unmeasured timing phases.

HAR cannot portably preserve every streaming message or opaque record. Preflight must report the exact
compatible/omitted counts and unsupported details. Offer KNet Capture for complete retained protocol
evidence; block a HAR export with zero compatible exchanges. Do not silently discard content.

## Dialog and file workflow

Use one reusable KNet-themed export dialog with an immutable source summary, format choice, content
choice (captured payloads or metadata only), estimated/count information where available, privacy
controls and a platform save dialog. API Studio exports its current eligible execution; it does not
offer unrelated Traffic/device scopes.

Remove common authorization/cookie header credentials by default, with an explicit opt-in to retain
them. Define and document the exact fields covered. Request/response cookies and matching duplicated
representations must agree. Body/query secrets require configured field rules or payload omission;
do not label a file universally sanitized. Redaction affects an export copy, never stored traffic or
the API Studio editor. Record omissions/redaction in the archive without retaining removed values.

Freeze source identity, filter semantics, record membership/order and execution revision before the
save operation. Export every matching retained record, not only the current UI page/viewport. Source
or document changes during the dialog, serialization or save cannot redirect its captured target.

Write to a task-owned temporary file and publish atomically on success. Show bounded progress and
cancellation. A cancelled/failed export must leave no apparently complete archive, overwrite an
existing target only through the save dialog's decision, and clean its temporary work.

## Architecture and resource ownership

Follow UI -> ViewModel -> application use cases/contracts -> data/storage adapters, wired through
Koin. Share format serializers, privacy transforms, eligibility types and dialog components between
Traffic and API Studio. Protocol extensions contribute typed export data without teaching HTTP UI
about each protocol implementation or exposing engine/storage types to feature modules.

Traffic preparation uses canonical indexed queries and bounded body/message access. Add a stable,
bounded snapshot/read-lease or spool mechanism that cooperates with Clear and deferred body cleanup.
An arrival watermark alone freezes membership but does not freeze mutable active-flow metadata/bytes;
snapshot those revisions and finalized readable byte limits explicitly. Do not hold a long SQLite
transaction or proxy/event-loop lock while writing a user-selected destination.

API Studio must retain an immutable execution export record plus bounded original payload storage,
independent of its formatted/rolling presentation and Traffic capture admission. Verify Direct routing
and capture-paused operation. Scope leases to document/execution revisions, handle retries/cancellation
and release old results on clear/replacement/disposal. Keep missing/truncated evidence honest instead
of exporting a pretty preview as a full response.

Use paged metadata, chunked payload reads, bounded workers/queues and background I/O. Avoid assembling
the entire session or archive in memory. Clean leases/spool files on completion, failure, cancellation
and process restart. No changes to live capture, forwarding, pairing or device policy activation.

## Delivery phases

1. **[COMPLETED] Contracts and schema:** inventory actual exportable metadata for each protocol; document
   archive v1, HAR mappings, typed response evidence, loss/preflight results and privacy coverage.
2. **[COMPLETED] Snapshot and ownership:** stable canonical Traffic snapshots, cleanup coordination and
   bounded API Studio execution/body retention, including capture-paused and Direct executions.
3. **[COMPLETED] Shared writers:** KNet Capture/HAR serialization, credential removal, integrity checks,
   bounded file handling, atomic completion, progress/cancellation and clear error reporting.
4. **[COMPLETED] Traffic UI:** existing row context menu action and source/filter toolbar export with
   frozen target, complete stored-result traversal and format capability preflight.
5. **[COMPLETED] API Studio UI:** persistent response-toolbar action across HTTP/live/protocol views,
   execution-specific eligibility and original sent-request ownership through draft edits/retries.
6. **[COMPLETED] Qualification:** execute the matrix below, update evidence/current documentation and
   build the desktop distributable. Do not launch desktop automatically. Native UI acceptance is separate.

## Verification and acceptance matrix

- Serializer/schema: structurally decode written archives in test code, verify manifest references,
  checksums, payload byte equality, message order/direction and versions; no production importer yet.
- HAR: validate against the supported schema and representative reference fixtures; GET/POST GraphQL,
  duplicate/empty headers, Unicode, binary/base64, compression, multipart and unknown timing values;
  record compatibility limitations and test any advertised foreign-tool acceptance explicitly.
- Eligibility: each HTTP status class, actual 502/503/504 versus local errors, empty/HEAD/204/304, server
  response followed by script/transport failure, pre-response cancellation and streaming evidence gates.
- Run ownership: new Send, slow prior completion, same-document edits, collection/document switches,
  protocol changes, clear versus clear-visible-messages, stale callbacks and export during a new run.
- Sources: two authenticated devices using the same destination, Other traffic, all-source explicit
  export, renamed/disconnected devices, rapid tab switching and retained historical device grouping.
- Snapshot concurrency: ongoing arrivals/active byte counters, body finalization, capture pause/resume,
  Clear/close/retirement, concurrent exports and body cleanup; explicit partial/missing outcomes.
- Resource/file behavior: more than the 1,000-row UI window, large binary bodies/message streams,
  cancellation at each stage, slow/full/unwritable disk, overwrite rejection, writer failure and cleanup
  after restart. Measure bounded heap growth rather than claiming low RAM from implementation alone.
- Rendered UI: fixed button slot across disabled/enabled states and protocol changes, hover explanation,
  keyboard/menu activation, narrow/wide layouts, progress/cancellation and no inspector remount/flash.
- End-to-end: real HTTP/HTTPS/H2, GraphQL GET/POST/persisted-query, WebSocket/GraphQL WS, gRPC and SSE
  fixtures plus TLS rejection/tunnel metadata; API Studio Direct and capture-paused exports. Verify
  fixtures/bytes through test-only readers and independently validate HAR interoperability where feasible.
- Run affected/full regression, architecture and ledger checks, whitespace validation and desktop
  packaging. Report executed counts, measurements and any remaining physical/UI checks precisely.

Completion requires working exports from both entry points, all supported source/protocol evidence
represented or explicitly disclosed, the execution-response gate enforced, no silent preview truncation,
and completed qualification evidence. Import remains outside this delivery.


## Delivery evidence

The final gate passed **1,747 tests across 34 complete outputs**, zero failures/errors/skips, plus
architecture/ledger validation and desktop packaging. This includes 35 new export-specific cases.
The 32 MiB fixture completed in 950 ms without positive retained heap growth after cleanup; no
whole-application RAM claim is made. See [the executed matrix and limits](traffic_export_qualification.md).
