# Traffic and API Studio export

Export and exact multi-selection are implemented. The original export-only evidence is preserved
below; the combined workflow increment is qualified in
[two_device_capture_workflow_qualification.md](two_device_capture_workflow_qualification.md).

## Entry points

The first automatically focused Traffic row is also selected for export, so startup shows **Export (1)**
and exports that exact typed record without another click. This happens once per source/filter view;
new arrivals and page eviction do not replace it. Explicit deselection remains empty until another
selection gesture or a new source/filter view.

Traffic's existing right-click row menu contains **Export this item**. The toolbar's **Export**
action opens a frozen query for the selected source. The dialog can explicitly select filtered results,
all traffic for that original source, or all sources. Bulk export traverses stored pages, including
records outside the visible/retained 1,000-row UI window.

Traffic also supports exact multi-selection: normal click replaces selection, Cmd/Ctrl-click toggles,
Shift-click selects a loaded inclusive range, and Cmd/Ctrl+Shift-click extends it. Cmd/Ctrl+A explicitly
selects loaded rows; Escape clears selection. The toolbar shows Export (N), and selected-row menus
also offer Export N selected items. An explicit selection is capped at 10,000 canonical typed IDs.
Arrivals never expand it; source changes retain their own selection, while filter changes and Clear
reset it. If a range anchor was paged out, the clicked row becomes the new anchor. Selected export
freezes the exact IDs, ordering and original source, discloses items cleared before preparation and
never broadens to an entire query. Larger result sets use filtered/source export.

API Studio has a fixed **Export** action beside the response Copy/Clear actions. Native protocol
editors expose it in their response/session timeline header. It exports the execution that produced the
displayed result; editing its request does not change that execution. New Send/Connect/Invoke, opening
another document, clearing the response and disposal retire its ownership. A saved draft alone cannot
be exported through this action. Request-only collection export is outside this feature.

Actual HTTP error statuses (including 502/503/504), empty responses and server rejections remain
exportable. Pre-response validation, DNS, connection and TLS failures do not enable API Studio export.
Finite HTTP enables on terminal response evidence; SSE can export after its received response head.
A cancelled/failed transfer after headers retains partial evidence. Native sessions require an actual
server handshake/headers/message/terminal response, not a local Started event or outbound message.
Traffic itself can export failed and opaque connections without an HTTP response.

Capture need not be enabled for an API Studio export. Direct and proxy-routed API Studio executions
retain their own evidence. Formatted previews and rolling visible-message windows are not archival data.

## Sharing dialog

KNet Capture is the default. HAR is offered only when at least one HTTP exchange is present. Its
preflight reports HTTP exchange count, omitted opaque/native records and omitted framed message count.
Choose captured payloads or metadata only. Authorization, Proxy-Authorization, Cookie, Set-Cookie and
X-API-Key header values are replaced with `[REDACTED]` by default; explicitly enable credentials to retain
those values. HAR cookie projections are empty under this policy and populated only with that opt-in.
This operation never edits the recorded capture or authored request.

This is a precisely scoped header transform, not a guarantee of anonymization. URL user information,
query values, arbitrary payload fields and protocol initialization messages can contain secrets. Omit
payloads and review URLs/content when a capture needs broader sanitization. No script source, pairing
credential store, CA private key, registered-device trust record or active rule configuration is exported.
Recorded policy/network-condition explanations are historical metadata only.

Preparation copies an isolated private spool before selecting a destination. Switching source/document
or clearing live traffic afterward cannot redirect the export. Saving uses a native file dialog with the
chosen extension, background chunked I/O, progress, cancellation, fsync and atomic destination replacement.
An unavailable atomic move fails instead of exposing a partially copied final file. Temporary destination
files are removed on failure/cancellation; an existing destination remains intact until publication.

## KNet Capture version 1

A `.knetcapture` is a ZIP archive, independent of Room schema versions:

```text
manifest.json
records.jsonl
payloads/1.bin
payloads/2.bin
...
```

The manifest declares `format: "KNet Capture"`, `schemaVersion: 1`, producer, snapshot time, parent-record
and framed-message counts, records entry name, payload inclusion and header-credential policy.
`records.jsonl` is UTF-8 JSON Lines, one bounded metadata object per line. This permits streaming a large
session without loading its full manifest/record list in memory. Parent records retain their stable
capture IDs as archive-local provenance; messages retain parent IDs, sequence, direction and timestamps.
An imported ID must never become authority to mutate live devices or policies.

Record kinds:

| Kind | Content |
|---|---|
| `http` | Original request/response heads, ordered headers/trailers, bodies, measured timings, source and recorded lifecycle/evidence. |
| `opaque` | TLS/TCP/UDP/QUIC metadata actually observed, source, policy explanation, byte counts and closure evidence; no invented decrypted payload. |
| `protocol` | API Studio's typed protocol target/operation and authored metadata, without executable scripts. |
| `message` | Original retained binary bytes with parent identity, protocol/direction/sequence, capture state and compression evidence where available. |
| `event` | API Studio native handshake/header/terminal evidence and trailers. |

Traffic freezes stored semantic annotations and available registered-device display labels in the same
read snapshot. Available GraphQL GET/POST documents, variables/extensions and persisted-query hashes
remain in the original HTTP representation. Recorded inspector resolution retains its annotation version
and provenance. No source document is fabricated from a hash.

A body reference contains an explicit state, available observed/stored counts and, when included, an
archive entry plus SHA-256 of its retained bytes. States distinguish empty, complete, truncated,
unavailable/missing and omitted-by-user data. Parent states identify active-at-snapshot and partial
executions. Available body objects are immutable; references to active/nonfinalized bodies may be
unavailable at that snapshot. The exporter does not wait for a connection to finish or synthesize its
missing prefix from a formatted preview.

JVM HTTP transports retain the request URI/method/headers and encoded entity paired with the response,
including generated multipart boundaries and redirected final requests. Transport-generated connection
headers not exposed by the HTTP library cannot be reconstructed. When a specialized executor does not
expose its original sent representation, the archive labels the post-script semantic request explicitly;
generated/unavailable bodies stay unavailable. A terminal-only executor must provide original bytes for
body export; decoded response text is not substituted.

## HAR 1.2

HAR represents compatible HTTP exchanges (including GraphQL over HTTP); it does not carry native
WebSocket/gRPC/SSE child messages or standalone opaque connections portably. The export dialog discloses
these omissions. KNet Capture retains them instead.

Requests retain their URL, method, ordered headers/query pairs and available entity. Valid UTF-8
request bytes are streamed as normal escaped `postData.text`. Non-UTF-8 request bytes use the explicit
KNet extension `postData._encoding: "base64"`, which foreign tools may not understand. Response content
uses standard HAR base64 encoding. Captured content encodings remain explicit; KNet does not silently
replace compressed bytes with a decoded preview. `_capture` describes payload availability and `_knet`
retains available source/lifecycle/timing metadata. Missing response status is represented using HAR's
conventional zero sentinel with explicit KNet evidence, never as a fabricated successful HTTP status.
Unknown timing phases use `-1`; actual available total time is preserved rather than split into invented
DNS/connect/send/wait/receive values. HTTP archive structure is checked by automated test readers.
Opening the exported file in a specific third-party desktop product remains a separate interoperability
acceptance step; native Charles/mitmproxy-format compatibility is not claimed by this increment.

With credentials included, request cookie name/value pairs and response Set-Cookie name/value,
path/domain, secure and HttpOnly attributes have structured HAR projections. Original ordered headers
retain the full cookie syntax, including attributes not projected separately. The implementation follows
the [HAR format documentation](https://www.softwareishard.com/blog/har-12-spec/) and uses explicit KNet
extensions for evidence the portable format cannot represent faithfully.

## Architecture and resource limits

Presentation delegates to `PrepareCaptureExportUseCase`, `SaveCaptureExportUseCase` and
`ReleaseCaptureExportUseCase`. A shared dialog/ViewModel owns immutable source selection, progress,
late-result fencing and cancellation. Platform file selection and snapshot/serialization are data adapters
provided through Koin; feature UI does not query Room or read files.

Traffic's bounded metadata pages run under one SQLite read snapshot. A scoped body-store read lease
fences both deletion entry points until available bodies are copied to private storage. Clear can commit
its metadata immediately; its existing deferred cleanup worker retries pinned bodies. User destination
I/O occurs after the transaction and read lease have ended. Payloads are copied/hashed in 64 KiB chunks,
and archive metadata is processed one line at a time. At most two preparations/writes execute concurrently.

API Studio retains up to 64 MiB of payload bytes and 100,000 native events per execution, independent
of visible-window limits. Limits are explicit through truncated bodies and dropped-event counts. At most
64 execution owners can be retained. UI owners release results on replacement/clear/disposal; startup
removes only task-owned stale snapshot/session directories. Ancillary export I/O failures retire export
evidence without cancelling the API request. Storage exhaustion during a user export is reported and
its task-owned artifacts are cleaned. These are implementation bounds; measured qualification results
are recorded separately and are not a claim about total application RAM.

## Verification evidence

The final `captureExportQualification` gate passed **1,747 tests across 34 complete outputs**, with zero
failures, errors or skips. There are **35 new export-specific cases**, including real product DI
composition, Room/device isolation, serializers, original Direct HTTP transport evidence, cancellation,
file integrity, resource bounds, ViewModel lifecycle and a rendered control stability matrix.
The final 32 MiB archive fixture completed in **950 ms** and showed no positive retained heap delta
after cleanup; this is not a whole-app or peak-RAM measurement. Desktop packaging, architecture and
ledger validation passed. The desktop application was not launched.

See [the qualification report](traffic_export_qualification.md) for reproducible commands, the executed
matrix and remaining native/physical/foreign-tool acceptance. Historical HAR, Charles XML and KNet import is documented in [capture_import.md](capture_import.md). CSV, Postman, direct foreign-native files, drag-and-drop actions and configuration/rule transfer remain separate work.
