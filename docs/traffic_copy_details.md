# Copy Traffic details

Right-click a Traffic row and open **Copy**. For a selected group, the parent reads **Copy (N)**.
Opening the menu on a member preserves the group; opening on another row replaces the selection.
All Copy leaves freeze the same exact canonical IDs and device/source as the menu opening.

## Presets

| Group | Choices |
| --- | --- |
| Main | Full traffic details; Summary |
| Address | Full URL; Host and port; Path and query; Query parameters |
| Request | Headers; Cookies; Body; Headers and body; Body as Base64; Body as hexadecimal |
| Response | Headers; Cookies; Body; Original text; Headers and body; Body as Base64; Body as hexadecimal |
| Combinations | URL and request body; URL and request with headers; URL and response body; URL and response with headers; Request and response bodies; Request and response with headers |
| GraphQL | Query; Variables; Extensions; Operation details; Complete GraphQL request |
| WebSocket / GraphQL WebSocket | Messages in both directions; Client messages; Server messages; Handshake details |
| gRPC | Service and method; Metadata and trailers; Messages |
| SSE | Events; Event data |
| Opaque connection | Destination; Connection metadata; Failure and closure details |
| Connection | Connection details; Timing details; Failure and closure details |
| Developer formats | cURL; Structured JSON |

Protocol details shows the protocols represented in the known selected row metadata. A single protocol
opens directly into its actions; mixed selections group actions by protocol. Ordinary HTTP has no
protocol-specific group. Request actions require an HTTP item. Response actions are disabled when no
known selected item has a response; selected exchanges outside the loaded page are resolved on demand
rather than silently excluded. Actual applicability is always rechecked against canonical data.

## Clipboard layout

Full traffic details preserves the labelled document: item count/device, numbered items, host/URL,
recorded status/timing/size, Request Params/Headers/Body and Response Headers/Body, with dividers between
items. JSON is formatted and GraphQL HTTP requests separate query, variables, extensions and operation.
Empty full-detail sections are omitted; missing/truncated/binary data remains explicit.

A single component such as Body, Headers, Variables or Query copies its value directly for quick paste,
preserving trailing whitespace and line endings without appending an extra newline.
Address-only actions use one line per selected record, preserving duplicate destinations and order.
Combined presets and multiple components use labelled blocks. Multi-selection always retains every
selected item: a non-applicable opaque connection, absent response or cleared item receives an explicit
availability marker. There is no fallback to a different selected row or the entire Traffic query.

Base64 and hexadecimal represent **stored captured bytes**, preserving compression and binary values.
Text actions use bounded decoding and existing inspection formatters. GraphQL GET reads URL parameters;
POST reads its captured body. Batch documents keep each operation's selected component separate.
Hash-only documents retain unavailable/resolved-source provenance and copying does not learn new hashes.
Cookies preserve individual Cookie/Set-Cookie values, including duplicates and Set-Cookie attributes.

Framed actions consider a bounded first page and filter protocol and direction before body reads.
The result discloses when additional history exists or no matching messages occur in that page.
The existing protocol decoder registry supplies readable gRPC messages when descriptors are available.
SSE Event data comes from the engine's shared SSE parser through its optional `dataText` presentation
field; application/UI code does not parse SSE records. Binary/unsupported payloads remain labelled.

cURL is a POSIX shell representation for compatible absolute HTTP/HTTPS requests. It is never executed
by KNet. Only complete retained request bodies are eligible. Shell quoting preserves literal quotes,
dollar signs and backticks; octal `printf` streams exact binary/compressed bytes without a temporary
file. Transport-owned headers are omitted and curl-generated headers are suppressed when absent from
the captured request. Captured trailers, unfinished/truncated requests and incompatible WebSocket/gRPC/
CONNECT requests produce explicit unavailability comments. Multi-copy separates commands with numbered
shell comments. This represents the captured request, not its previous connection or HTTP/2 wire frames.

Structured JSON is a valid versioned clipboard document, not a KNet capture archive or HAR. It contains
source, ordered typed record IDs and named text evidence fields, including unavailable selected records.
Bodies stay text fields; binary content is described rather than invented as JSON or decrypted HTTP.

## Ownership and bounds

The shared `:ui:desktop:httpPanel` catalogue serves Traffic and API Studio; `:ui:core` owns its themed popup.
`:ui:desktop:traffic` derives eligibility from lean body-free rows and builds themed nested menus.
It prepares shared group capabilities once, retaining no payloads. A lifecycle ViewModel passes the
frozen selection and typed `TrafficCopyPreset` to the application command, then publishes asynchronously
through modern Compose Clipboard. Source changes, replacement commands and owner disposal cancel stale
preparation. Preparation errors use the existing Traffic banner and preserve the previous clipboard.

The application verifies source ownership before body access, snapshots metadata, holds the existing
body-file lease against Clear, and resolves only the chosen preset on IO. Address/headers/summary/
timing actions never read HTTP payloads or framed pages. Data supplies bounded JSON/GraphQL formatting;
engine contributions supply optional semantic message presentation. No extra formatting library is added.
The existing kotlinx JSON library serializes the structured clipboard representation.

Bounds apply to all variants:

- At most 10,000 distinct selected canonical identities.
- 4,194,304 characters for metadata weighting and the complete clipboard document.
- 65,536 stored bytes per HTTP body and at most 65,536 decoded bytes per body.
- One page of at most 32 framed messages per exchange; at most 16,384 stored bytes per payload.
- 131,072 displayed characters per labelled/JSON evidence field, with a truncation marker.
- cURL commands are complete or unavailable; commands are never silently cut to the field limit.

An entirely cleared selection or oversized document fails atomically. Use Export for larger portable
captures. Clipboard copying is a snapshot of available evidence, not an atomic transaction or replay.

## Verification

Application tests cover every preset, exact/mixed selection, duplicate fields/addresses, body-free
metadata actions, binary fidelity, JSON syntax/order, source isolation before body reads, missing and
truncated payloads, protocol/direction/parent filtering, GraphQL provenance, gRPC trailers and SSE data.
A real local POSIX shell invokes a stub curl function to verify literal arguments and binary stdin
without sending a network request. ViewModel tests cover preset/source snapshots, cancellation and
clipboard errors. Rendered nested popups at standard/Retina density and actual table pointer interactions
verify grouped routing and dismissal. Product integration combines real Koin, Room device names, body
files and formatters with fixture traffic and an in-memory Compose clipboard for every preset.

The workflow gate requires the copy suites alongside existing import/export/protocol regressions.
Native system clipboard and physical device interaction remain separate manual checks.

The final workflow gate passed **1,997 automated tests across 36 outputs** with zero failures/errors/
skips. Architecture checks and desktop packaging passed. All **3,683 checked classes across 23 modules**,
including the updated SSE decoder, match the desktop package without missing, mismatched or duplicate
classes. Focused XML, build logs and package checks are saved in
`/Users/devuloopers/.codex/verification/knet-traffic-copy-presets-2026-11-15/`.

The catalogue now also includes Original text (44 typed presets). API Studio reuses this catalogue and
the HTTP rendering path with its own execution source; see [API Studio Copy](api_studio_copy.md).

The subsequent API Studio Copy qualification passed 2,017 tests across 36 outputs and verified 3,721
packaged classes across 23 modules, including the shared catalogue/renderer and all existing Traffic
copy regressions. Its current evidence is recorded in `docs/api_studio_copy.md`.
