# Server-Sent Events Target Architecture and Implementation Plan

- Status: **IMPLEMENTED — EXPERIMENTAL QUALIFICATION**
- Target transport: SSE (`text/event-stream`) over the existing HTTP/1.1 and HTTP/2 transports
- Capability target: live Traffic capture, API Studio execution, event inspection, persistence, and response-event
  breakpoints

## 1. Objective

Evolve KNet's current bounded, post-capture SSE summary into real live SSE support without creating a second HTTP
model, buffering an unbounded response, or putting SSE knowledge into the proxy, persistence, or generic UI.

The completed increment must support a response that remains open for hours while:

- forwarding bytes to the client without waiting for inspection or persistence;
- keeping the parent HTTP exchange in progress;
- parsing and displaying complete events as they arrive;
- persisting bounded event records linked to the parent exchange;
- allowing API Studio to cancel a live stream;
- optionally pausing only a matching event when an SSE breakpoint rule is enabled;
- preserving bounded memory, body ownership, and backpressure under slow UI, disk, or inspector consumers.

This plan does not replace KNet's HTTP architecture. SSE is HTTP response semantics, not a new transport shell.

## 2. Baseline before this implementation

### What KNet already has

- `HttpRequestSnapshot`, `HttpResponseSnapshot`, and `HttpExchangeSnapshot` are the canonical HTTP models used by
  Traffic, API Studio recording, breakpoints, storage, replay, and inspectors.
- `ProxyStreamInspectorFactory` and `ProxyStreamInspector` observe ordered response heads, borrowed payload slices,
  trailers, direction completion, and exchange termination without owning forwarding.
- `ProxyStreamTransformerFactory` and `ProxyStreamTransformer` provide the bounded, opt-in transformation seam
  already used by gRPC message breakpoints.
- `ProtocolMessageSnapshot` stores bounded child messages linked by connection, exchange, and optional HTTP/2
  stream IDs; its payload uses the common body store.
- `ProxyMessageCapture` reserves body capacity before copying and persists message lifecycle state through the
  canonical capture ingress.
- Traffic already renders protocol messages through `ProtocolMessagePayloadDecoder`, with no gRPC or WebSocket
  parsing in the UI.
- API Studio already has common saved/unsaved workspace documents and generic streaming protocol execution events.
- The testing server exposes a finite deterministic `/lab/v1/streams/sse` endpoint.

### What is not live SSE support today

- `SseSemanticInspector` runs only after capture and concatenates its bounded response preview before parsing.
- It parses only `event` and `data`; it does not implement the full incremental record rules for `id`, `retry`,
  comments, split UTF-8 input, or arbitrary network chunk boundaries.
- `KNetApiClient` calls `readRawBytes()`, so API Studio receives only a terminal buffered `ExecutionResult`.
- Traffic does not receive a child message when an SSE event is dispatched.
- SSE breakpoints cannot match, edit, or terminate one live response event.
- Existing SSE tests validate a finite string/response, not live transport, lifecycle, backpressure, or persistence.

Consequently, the truthful baseline before this increment was **bounded post-capture SSE semantic preview**, not
complete SSE.

### Implementation checkpoint — 2026-08-25

SSE-0 through SSE-4 are implemented additively:

- `:engine:sse` owns one bounded incremental parser used by live capture, historical inspection, Traffic
  decoding, API Studio interpretation, formatting, and breakpoint validation.
- HTTP/1.1 and exact/AUTO HTTP/2 API Studio execution publish owned response-head and body-chunk events before
  completion; collection cancellation closes the active response. HTTP/1.0 remains terminal-only.
- The proxy passively captures identity-encoded records as generic child protocol messages without delaying
  forwarding. Room and the body store persist them without SSE-specific schema fields.
- Traffic renders live and historical records through its existing payload-decoder registry.
- API Studio remains the HTTP workspace and switches its response pane to a bounded live record timeline only
  after an interpreter recognizes `text/event-stream`.
- Response-record breakpoint rules can match event type, event ID, or data, then continue, replace, or terminate
  the matching stream through the generic message-decision boundary.
- Deterministic HTTP/1.1 and HTTP/2 fixtures prove first-record delivery, fragmentation, malformed input, resume,
  gzip classification, cancellation, parser limits, capture, persistence, and breakpoint behavior.

The runtime capabilities remain `EXPERIMENTAL`, not `SUPPORTED`. Incremental gzip/deflate interpretation,
cross-platform and real-device proxy evidence, multi-hour soak, and the complete concurrent HTTP/2 isolation
matrix remain SSE-5 qualification work.

## 3. Architectural decisions

### 3.1 The common HTTP model remains the source of truth

An SSE request and response remain exactly one `HttpExchangeSnapshot`:

- request method, URL, headers, body, source, ingress identity, connection, timings, and negotiated protocol remain
  in the common request/response/exchange models;
- API Studio, Traffic, replay, export, and breakpoints continue to pass those same models;
- no `SseRequest`, `SseResponse`, SSE repository, or SSE-specific traffic table is added;
- the parent exchange remains `IN_PROGRESS` until the response completes, is cancelled, disconnects, or fails.

Each bounded SSE record selected for capture is a child `ProtocolMessageSnapshot` linked to that parent exchange;
the SSE decoder classifies it as a dispatched event, comment/keep-alive, or state-only record. This is the same
parent/child architecture already used for gRPC and WebSocket, so long-lived responses do not require a rewrite of
the HTTP model.

### 3.2 SSE is an HTTP semantic extension, not an API Studio top-level protocol

API Studio keeps its existing HTTP workspace. A user enters a normal URL, method, headers, authentication, and
request body. After the response head identifies `Content-Type: text/event-stream`, the response pane changes from
the buffered body inspector to a live event timeline.

There is no separate SSE tab, request editor, collection tree, or authored-document format. A future SSE-specific
request option may be contributed inside the HTTP editor only when it represents real HTTP state such as an
`Accept` header or `Last-Event-ID` value.

### 3.3 Parsing has one source of truth

One incremental SSE parser owns WHATWG-compatible record parsing. The proxy capture adapter, API Studio response
interpreter, breakpoint transformer, historical formatter, and tests all use that parser.

The parser accepts owned byte chunks and emits typed results. It does not depend on Netty, Ktor, Compose, Room, the
proxy runtime, or API Studio.

### 3.4 Forwarding never waits for passive SSE inspection

The proxy owns byte forwarding and downstream/upstream backpressure. The passive SSE inspector receives borrowed
payload slices, copies only bytes admitted by a canonical capture reservation, and performs only bounded parsing
work. Capture saturation, parsing failure, persistence delay, or a closed Traffic screen cannot delay forwarding.

An enabled matching breakpoint is the sole deliberate pause path. It uses the existing bounded transformer and
application breakpoint coordinator, including timeout, cancellation, byte, and concurrent-pause limits.

### 3.5 Raw evidence is retained; semantic views are derived

The parent response body continues to retain its configured bounded raw preview. Each captured SSE record gets an
independently bounded child body containing the raw record bytes. Parsed event type, ID, retry, joined data, and
whether the record dispatches an event are derived by the SSE decoder and are not added as SSE-specific columns to
core traffic or Room.

If an event is malformed, truncated, compressed by an unsupported content encoding, or beyond capture limits, raw
forwarding continues and KNet records an explicit truncation/gap/failure state rather than inventing a valid event.

## 4. Target module and package structure

```text
engine/
├── proxy/                                  # MODIFY narrowly: zero-copy borrowed-slice delimiter scanning
├── protocol/                               # MODIFY: GraphQL and other post-capture semantics only
└── sse/                                    # ADD: complete SSE semantic increment
    ├── MODULE.md
    ├── build.gradle.kts
    └── src/
        ├── main/kotlin/com/devuloopers/knet/engine/sse/
        │   ├── protocol/                   # Incremental parser, typed fields/events, limits
        │   ├── capture/                    # ProxyStreamInspectorFactory and event capture
        │   ├── breakpoint/                 # Rule extension, matcher, bounded transformer
        │   ├── inspection/                 # Traffic payload decoder and historical annotation
        │   ├── apistudio/                  # HTTP response-stream interpreter
        │   └── capability/                 # Evidence-backed capability contribution
        └── test/kotlin/...                 # Parser, capture, breakpoint, lifecycle tests

core/
├── traffic/                                # MODIFY: add extension-safe SSE IDs/kinds only
├── http/                                   # MODIFY: expose streaming HTTP execution beside terminal execution
└── domain/                                 # MODIFY: protocol-neutral streaming HTTP contracts only if ownership fits

application/
└── .../port/apistudio/                     # MODIFY: generic HTTP response-stream events/interpreter registry

ui/desktop/apiStudio/                       # MODIFY: generic HTTP live-response timeline
ui/desktop/traffic/                         # KEEP structurally; consume the registered SSE decoder

data/desktop/                               # KEEP structurally; generic protocol messages/body store persist SSE
products/desktop/.../di/                    # MODIFY: register SSE contributions additively
testingServer/                              # MODIFY: deterministic SSE qualification scenarios
docs/                                       # ADD: this plan and later qualification evidence
```

`:engine:sse` is justified because complete SSE spans incremental parsing, proxy observation, event capture,
breakpoint transformation, Traffic presentation, and API Studio stream interpretation. Keeping those together
prevents `:engine:proxy`, `:core:http`, and generic UI from acquiring protocol rules, while matching the existing
`:engine:grpc` and `:engine:websocket` extension pattern.

No `:ui:desktop:apiStudio:sse` module is planned initially. The response UI is a generic live HTTP response
timeline; a separate UI module is justified only if measured SSE-specific authoring complexity appears later.

Dependency direction:

```text
core:traffic <- application <- products:desktop
      ^              ^                |
      |              |                +-- registers engine:sse contributions
engine:proxy <--- engine:sse           +-- assembles UI and adapters
      ^              |
      +--------------+

ui:desktop:traffic ------> application/core contracts
ui:desktop:apiStudio ----> application/core contracts
data:desktop ------------> application/core contracts
```

Forbidden dependencies remain:

- proxy -> SSE, UI, Room, or product DI;
- core traffic -> SSE;
- UI -> SSE parser or Netty;
- persistence -> SSE fields;
- SSE engine -> Compose or Room.

## 5. KEEP / MODIFY / MOVE / REMOVE / ADD

### KEEP

- Common `HttpRequestSnapshot`, `HttpResponseSnapshot`, and `HttpExchangeSnapshot` contracts.
- `BodyRef`, `MessageBodyRef`, body reservations, body limits, retention, clear, and recovery ownership.
- Generic `ProtocolMessageSnapshot` persistence and query path.
- Proxy stream inspector/transformer lifecycle contracts and HTTP/2 stream identity.
- Generic breakpoint coordinator and interception drawer.
- Generic Traffic protocol-message panel and decoder registry.
- Existing HTTP API Studio request editor, collection tree, session naming, authentication, scripts, and proxy route.
- Direct-versus-local-proxy routing rule: API Studio records to Traffic only through a running capture route.

### MODIFY

- `MessageProtocolId`: add `SSE` as an extension-safe constant.
- `ProtocolMessageKind`: add one generic `RECORD` token; the SSE decoder derives event/comment/control semantics
  after immutable capture metadata has been created.
- `ProxyPayloadSlice`: add a read-only bounded delimiter-search operation so SSE can locate line/record boundaries
  without copying unreserved transport bytes. The slice remains borrowed and cannot escape the callback.
- HTTP client execution: expose response head and bounded body chunks as a flow before terminal completion.
- API Studio HTTP execution coordinator: consume live response events and retain existing terminal behavior for
  ordinary HTTP responses.
- Traffic request descriptor/capability registration: show an `SSE` badge based on canonical response metadata.
- Product DI: register SSE stream inspector, breakpoint transformer, payload decoder, response interpreter, and
  capability evidence.
- Testing server: add live, fragmented, reconnect, malformed, cancellation, compression, and overload fixtures.
- Roadmap/module docs: distinguish post-capture preview from live support.

### MOVE

- Move `SseSemanticInspector` and its parsing responsibility from `:engine:protocol` to `:engine:sse`.
- Replace its private parser with the shared incremental parser. The historical post-capture annotation may remain
  as an adapter over the new parser until live child-message capture is fully qualified.

### REMOVE

- Remove the old private `SseSemanticInspector.parse` implementation after parity tests pass.
- Remove any temporary full-response concatenation used only to recognize SSE.
- Remove duplicate SSE formatting/parsing rules from `SseStreamFormatter` once callers use the shared parser.
- Do not retain experimental code that stores SSE-specific columns or branches on SSE inside the proxy/UI.

### ADD

- `:engine:sse` plus a root `MODULE.md` defining its ownership and exclusions.
- Incremental parser and content-decoding boundary.
- Passive stream-capture adapter and `SseProtocolMessageDecoder`.
- Generic HTTP streaming execution contract and KNet client adapter.
- API Studio live HTTP response timeline mode.
- SSE breakpoint extension and bounded event transformer.
- Local qualification suite and evidence document.

## 6. SSE wire model and parser behavior

The parser is a bounded state machine, not a line-sequence utility. Its conformance source is the
[WHATWG Server-Sent Events parsing and interpretation algorithm](https://html.spec.whatwg.org/multipage/server-sent-events.html#parsing-an-event-stream).
It must support:

- UTF-8 code points split across arbitrary chunks;
- an optional UTF-8 BOM only at the beginning of a stream;
- `LF`, `CRLF`, and `CR` line endings, including split delimiters;
- blank-line event dispatch;
- `data` fields joined with a single newline and the final newline removed on dispatch;
- `event` as the event type for the next dispatch;
- `id` updates, ignoring values containing U+0000;
- `retry` values containing ASCII digits only, with overflow handled as a bounded invalid value;
- comment lines beginning with `:`;
- a field with no colon as a field with an empty value;
- exactly one optional leading space removed from field values;
- unknown fields ignored semantically but retained in the raw bounded record;
- EOF discarding a pending record that was not terminated by the required blank line;
- blank records with no `data` updating valid ID/retry state but not dispatching a data event;
- defaulting an empty event type to `message` for presentation while preserving the raw record;
- explicit overflow/malformed results instead of exceptions escaping into forwarding.

Suggested internal results:

```text
SseParseResult.Event(rawRecord, type, data, id, retryMillis)
SseParseResult.Comment(rawRecord, text)
SseParseResult.StateUpdate(lastEventId, retryMillis)
SseParseResult.Gap(reason, observedBytes)
```

The durable kind remains `RECORD`; these results are derived presentation/decision facts. Data-bearing records are
counted as dispatched events. Comment keep-alives and ID/retry-only records may remain visible as bounded control
records because they are useful network evidence, but they are not falsely counted as dispatched events.

Initial configurable limits must include:

- maximum encoded line bytes;
- maximum raw event bytes;
- maximum decoded data characters;
- maximum events and child-body bytes per exchange;
- maximum event type and ID characters;
- maximum API Studio retained live events;
- maximum concurrent active parsers and breakpoint pauses.

Values must come from one SSE limits configuration object registered by the product. They must not be duplicated
as unrelated UI, proxy, and parser constants.

## 7. Traffic and storage architecture

### Parent exchange

1. The existing proxy capture starts the common HTTP exchange.
2. The response head is stored immediately.
3. If the normalized media type is `text/event-stream`, the SSE inspector becomes active.
4. The parent row remains `IN_PROGRESS` while the response is open.
5. Normal completion, client cancellation, upstream disconnect, timeout, or failure produces the existing terminal
   exchange state and timing data.

### Child events

For each selected SSE record, the SSE capture adapter:

1. uses the borrowed slice's read-only delimiter search to find the next line segment without copying it;
2. starts a `ProtocolMessageSnapshot` with `MessageProtocolId.SSE`, generic `RECORD` kind, response direction,
   stream identity, and a monotonic sequence when the record begins;
3. reserves each exact record segment through `ProxyMessageCapture` before copying it;
4. feeds the owned reservation bytes to the parser, publishes them, and finalizes the child at the blank line;
5. completes it as `COMPLETE` or `TRUNCATED` without changing immutable capture metadata after parsing;
6. emits a gap only once when the configured per-exchange event/body budget is exhausted;
7. stops semantic capture after exhaustion while HTTP forwarding continues unchanged.

No new Room table is expected because protocol and kind IDs are open tokens and the existing message/body tables
are generic. The implementation phase must verify schema constraints and add a migration only if that audit finds
a closed enum/check constraint.

Clear Traffic continues to clear the parent exchange, child message metadata, and their body objects through the
existing canonical deletion/outbox path. It must not affect proxy state, API Studio drafts, certificates, or
connectivity.

### Traffic UI

- The table row uses `SSE` as its semantic badge while retaining actual client/upstream HTTP protocol metadata.
- The response details show the normal response head plus the generic protocol-message/event panel.
- The event list shows sequence, time, event type, ID, data preview, size, and state when available.
- Selection loads at most the existing bounded message preview and asks `SseProtocolMessageDecoder` to present
  formatted raw/data content.
- Search/filter operates on already loaded bounded summaries initially; database-side semantic search is deferred
  until measured scale requires a generic annotation index.
- UI state retains a bounded window and uses existing paged history/invalidation mechanics; it never collects the
  entire live stream into Compose state.

## 8. Proxy runtime and backpressure flow

```text
client request
  -> protocol-neutral proxy forwards request
  -> upstream response head arrives
  -> proxy forwards head immediately
  -> SSE inspector recognizes text/event-stream
  -> each borrowed HTTP payload slice
       -> forwarding path writes with existing channel backpressure
       -> passive inspector copies only bounded admitted bytes
       -> incremental parser emits zero or more complete events
       -> canonical child-message capture persists admitted events
  -> Traffic observes parent/message invalidations
  -> end/cancel/failure closes parser, child capture, and parent exchange
```

Memory ownership rules:

- Netty/Ktor owns transport buffers; SSE code never retains a borrowed payload slice.
- The parser owns only its bounded incomplete-line/current-event scratch storage.
- Canonical body capture owns copied raw record bytes after reservation.
- Traffic and API Studio own bounded presentation copies only.
- No `ByteArrayOutputStream` grows for the lifetime of the response.

Backpressure rules:

- passive inspection is synchronous and bounded; if it cannot admit more bytes, it records a gap and detaches;
- forwarding does not wait for Room, semantic formatting, or Compose;
- API Studio's network reader may suspend on its bounded event channel, but UI presentation uses a bounded rolling
  window and exposes dropped/gap counts rather than silently accumulating;
- cancellation closes the HTTP call and releases parser, channel, and body ownership deterministically.

## 9. Content encoding

HTTP framing is already normalized by the HTTP transport, but `Content-Encoding` is application payload encoding.
The SSE module therefore owns an internal incremental decoder strategy before its parser:

```text
SseContentDecoder: identity | gzip | deflate | contributed future decoder
```

The first locally qualified increment requires identity encoding. Gzip and deflate should be delivered before a
`SUPPORTED` claim. Brotli or Zstandard may be added as internal strategies without changing proxy/core/UI contracts.
Until a decoder exists, KNet forwards and may retain the bounded raw response but reports live semantic inspection
as unavailable for that encoding; it must never parse compressed bytes as text.

## 10. API Studio architecture and UX

### Execution contract

Add a protocol-neutral streaming HTTP boundary alongside `HttpExecutor`, for example:

```text
HttpExecutionEvent.RequestStarted
HttpExecutionEvent.ResponseHead
HttpExecutionEvent.BodyChunk
HttpExecutionEvent.Trailers
HttpExecutionEvent.Completed
HttpExecutionEvent.Failed
```

The event types contain common HTTP models and owned bounded chunks, not SSE values. `KNetApiClient` implements the
streaming boundary for HTTP/1.1, AUTO, and HTTP/2. The existing terminal `HttpExecutor.execute` folds the same event
stream for ordinary responses, avoiding two divergent client implementations.

An application-owned `HttpResponseStreamInterpreter` registry selects an interpreter after the response head. The
SSE engine contribution recognizes `text/event-stream`, consumes owned chunks through the shared parser, and emits
protocol-neutral live-response presentation records. Ordinary HTTP retains the existing buffered response path.

### Response pane

For SSE, the existing right-hand response pane shows:

- response status, headers, negotiated client/upstream protocol, source, start time, elapsed duration, bytes, and
  connection state;
- a live bounded event timeline with sequence and event summary;
- selected event raw/data view using the common code editor in read mode;
- copy raw record, copy data, search, clear visible events, and cancel stream actions;
- an explicit truncated/dropped/gap counter when limits are reached;
- terminal reason after completion, cancellation, disconnect, or failure.

`Clear visible events` affects only the current bounded presentation window. It does not delete Traffic history.

The initial increment supports explicit `Last-Event-ID` request input and manual reconnect. Automatic reconnect is
not silently enabled: if later added, it must be opt-in, bounded by attempts/backoff/time, cancellable, and preserve
one logical API Studio execution identity with visible reconnect boundaries.

Pre-request scripts run before the HTTP call as today. Post-response scripts/tests run only when a finite stream
completes normally and their required bounded response data is available. Endless, cancelled, truncated, or failed
streams expose a clear not-run reason instead of executing against an invented complete body.

## 11. Breakpoint architecture

Add an `SseBreakpointProtocolExtension` using `BreakpointInterceptionUnit.PROTOCOL_MESSAGE`, response direction,
and fields such as:

| Field | Type | Meaning |
|---|---|---|
| Event type | Text/choice | Exact or wildcard SSE `event` value |
| Event ID | Text, optional | Exact or wildcard valid `id` value |
| Data | Text/regex, optional | Match bounded joined event data |
| Phase | Fixed response-event | SSE events are response semantics |

The add-rule drawer can generate a smart suggestion from a selected Traffic event: endpoint pattern, method,
event type, and optional event ID/data criteria. The protocol extension owns these fields; the generic drawer does
not branch on SSE.

Runtime flow:

1. Product DI installs the SSE transformer only when an enabled rule may match the request route/method.
2. A non-SSE response head bypasses the transformer immediately.
3. The transformer buffers at most one bounded event record while forwarding previously resolved records.
4. A matching event publishes one generic protocol-message breakpoint candidate.
5. HTTP/2 pauses only that stream; HTTP/1.1 pauses only that exchange, within existing coordinator limits.
6. Continue forwards the original record. Replace validates exactly one complete SSE record, encodes it, and
   forwards it. Terminate closes/drops the stream through the existing typed decision.
7. Timeout, drawer close, proxy stop, client disconnect, or upstream failure releases the pause deterministically.

Initial scope deliberately does not promise suppression of one event while keeping the stream open because the
current common decision is `DropStream`, not `DropMessage`. Add single-record suppression later only by introducing
a generic message-decision semantic that is valid for SSE, gRPC, and WebSocket—not an SSE-only proxy special case.

## 12. Lifecycle and network-state behavior

- Starting/stopping capture does not restart or delay an already forwarded stream beyond the current proxy
  lifecycle contract.
- Stopping the proxy cancels active SSE parser/capture/transformer state and resolves pending breakpoint decisions.
- API Studio direct execution is independent of proxy state and is not recorded in Traffic.
- API Studio local-proxy execution uses the running proxy endpoint and appears as one parent exchange plus events.
- Network transitions terminate affected streams with an explicit reason; KNet does not hide reconnection as one
  continuous TCP/HTTP stream.
- App shutdown cancels API Studio calls before closing client engines and waits for proxy/capture ownership to
  converge within existing deadlines.
- Restarted KNet can query previously persisted finite or terminated SSE events; it does not attempt to resurrect
  a live stream automatically.

## 13. Security rules

- Existing listener scope, LAN exposure, certificate, strict upstream TLS, and connectivity policies remain
  authoritative.
- SSE event data is untrusted input. It is rendered as text/code, never interpreted as HTML or executable script.
- Parser sizes, characters, event counts, retry values, and breakpoint regex execution remain bounded.
- `Last-Event-ID` is treated as an ordinary sensitive request header/value in logging and export policies.
- Raw event data follows the existing body redaction/export rules; SSE does not bypass them.
- Malformed UTF-8, control characters, extremely long lines, and reconnect storms must not crash the proxy or UI.

## 14. Implementation phases and exit gates

### SSE-0 — Truthful baseline and contracts [COMPLETED]

- Correct roadmap/module claims from complete SSE to bounded post-capture preview.
- Split the runtime capability claim into `sse.preview` (`SUPPORTED` by the existing bounded end-to-end test),
  `sse.capture`, `sse.apistudio`, and `sse.breakpoints` (`UNAVAILABLE` until their own gates pass).
- Add parser, capture, API Studio, breakpoint, and capability contract tests that initially describe missing live
  behavior.
- Freeze one limits configuration and deterministic fixtures.

Exit gate: current behavior is accurately documented; new contracts do not depend on UI, proxy implementation,
or persistence.

### SSE-1 — Incremental parser and semantic core [COMPLETED]

- Add `:engine:sse` and `MODULE.md`.
- Implement incremental UTF-8/line/event parsing and identity content decoding.
- Move the existing post-capture inspector and formatter onto the shared parser.
- Add property/chunk-boundary, malformed-input, overflow, and official-behavior fixture tests.

Exit gate: every fixture yields the same result for all tested byte chunkings; memory never exceeds configured
scratch/event limits; the old private parser is gone.

### SSE-2 — Live proxy capture, persistence, and Traffic [COMPLETED — EXPERIMENTAL]

- Add the SSE protocol ID, generic record kind, borrowed-slice delimiter search, and passive stream-inspector
  contribution.
- Capture complete bounded records through `ProxyMessageCapture`.
- Register the Traffic payload decoder and semantic descriptor.
- Verify Room/body-store retention, clear, restart, gaps, pagination, and parent lifecycle.

Exit gate: an ongoing HTTP/1.1 and HTTP/2 SSE response appears immediately as one in-progress parent with ordered
child events; forwarding remains byte-correct under capture saturation and a closed Traffic UI.

### SSE-3 — API Studio live HTTP execution [COMPLETED — EXPERIMENTAL]

- Add the generic HTTP execution-event boundary and implement it in `KNetApiClient`.
- Fold ordinary responses into the existing `ExecutionResult` from the same stream.
- Add the response-interpreter registry and SSE contribution.
- Render the live response timeline, detail, cancel, search, copy, gap, and terminal states.
- Preserve focus, workspace identity, session naming, scripts, direct/proxy routing, and saved/unsaved behavior.

Exit gate: API Studio displays the first event before the server completes; cancelling releases the connection;
ordinary HTTP/1.0, HTTP/1.1, and HTTP/2 behavior remains unchanged.

### SSE-4 — Event breakpoints [COMPLETED — EXPERIMENTAL]

- Add protocol extension, smart rule suggestion, matcher, replacement validator, and transformer.
- Reuse the global side drawer and generic pending-decision model.
- Test continue, replace, terminate, timeout, concurrent HTTP/2 stream isolation, proxy stop, and disconnect.

Exit gate: only the matching event/stream pauses, nonmatching streams and passive forwarding continue, and no
pending decision or buffer survives termination.

### SSE-5 — Qualification and hardening [PARTIAL]

- Add incremental gzip and deflate observation decoding.
- Run cross-platform JVM, real listener, device proxy, slow consumer, capture saturation, reconnect, long-lived
  stream, memory, descriptor, cancellation, and shutdown tests.
- Publish `docs/sse_qualification.md` with exact evidence, limits, and exclusions.
- Promote capability from `EXPERIMENTAL` to `SUPPORTED` only when every declared platform gate passes.

Exit gate: no unbounded path, forwarding regression, silent data loss, UI accumulation, or lifecycle leak remains;
the capability catalog matches evidence.

## 15. Test and qualification matrix

| Area | Required cases |
|---|---|
| Parser | LF/CRLF/CR, BOM, split UTF-8, split delimiters, multiline data, empty values, comments, ID/NUL, retry, unknown fields, incomplete EOF |
| Limits | oversized line/event/data/ID/type, event-count limit, byte-budget exhaustion, one explicit gap, parser detachment |
| HTTP/1.1 | chunked/non-chunked finite stream, long-lived stream, trailers, close, upstream failure, client cancellation |
| HTTP/2 | DATA-frame fragmentation, concurrent streams, one cancelled/paused SSE stream while other streams progress |
| Encoding | identity required; gzip/deflate before Supported; unsupported encoding forwarded with truthful semantic state |
| Proxy capture | capture on/off, passive forwarding byte parity, storage delay, saturation, proxy stop/start, network change |
| Persistence | parent/child ordering, body ownership, restart query, retention, outbox deletion, Clear Traffic isolation |
| Traffic UI | immediate in-progress row, ordered live events, bounded window, scrollbar/paging, selection, decoder fallback, terminal states |
| API Studio | first-event latency, finite/endless/cancelled streams, direct/local-proxy route, HTTP version, scripts, session focus, saved/unsaved docs |
| Breakpoints | smart rule, type/ID/data matches, continue, valid/invalid replace, terminate, timeout, disconnect, HTTP/2 isolation |
| Security | untrusted HTML/script data, malformed UTF-8, regex limits, huge retry value, sensitive header/export behavior |
| Soak | configurable multi-hour stream, high event rate, slow UI, bounded heap/direct memory, thread/descriptor recovery |

The testing server should provide named endpoints rather than query combinations that obscure fixtures:

```text
/lab/v1/streams/sse/finite
/lab/v1/streams/sse/live
/lab/v1/streams/sse/multiline
/lab/v1/streams/sse/comments
/lab/v1/streams/sse/fragmented
/lab/v1/streams/sse/malformed
/lab/v1/streams/sse/disconnect
/lab/v1/streams/sse/resume
/lab/v1/streams/sse/gzip
/lab/v1/streams/sse/fast
```

The existing finite endpoint may remain as an alias during development, then be removed because KNet does not need
backward compatibility before release.

## 16. Capability matrix

| Capability | Implemented state | Supported gate |
|---|---|---|
| HTTP forwarding | Existing HTTP transport, unchanged by SSE | Existing HTTP qualification |
| Post-capture summary | Supported bounded preview using the shared parser | Regression tests |
| Live Traffic events | Experimental identity-encoded event history | Platform/device/soak |
| Persistence/history | Experimental generic child-message persistence | Restart/retention/clear matrix |
| API Studio live stream | Experimental for HTTP/1.1 and HTTP/2 | Direct/proxy/version/device matrix |
| Event breakpoints | Experimental match/continue/replace/terminate | Full lifecycle and HTTP/2 isolation matrix |
| Identity encoding | Experimental | SSE-5 |
| Gzip/deflate live decode | Unavailable; terminal response remains bounded | SSE-5 decoder contribution |
| Brotli/Zstd live decode | Unavailable | Future decoder contribution |
| Automatic reconnect | Unavailable | Future opt-in product scope |

## 17. Explicit exclusions

The first complete local SSE increment does not include:

- HTTP/3, which remains a transport project;
- a separate SSE API Studio workspace or traffic model;
- browser `EventSource` CORS, cookies, or browser credential-policy emulation;
- automatic endless reconnect enabled by default;
- suppression of one matched record while keeping the stream open; current Drop terminates the stream explicitly;
- server-side SSE authoring or an SSE mock server in the desktop product;
- database-side full-text indexing of all event data;
- silently treating unsupported content encoding as decoded SSE.

These exclusions require additive extensions or a separate transport increment; none requires migrating the proxy,
PAC/manual Wi-Fi connectivity, common HTTP models, canonical Traffic storage, or existing API Studio collections.

## 18. Target architecture verdict

This design can add production-grade SSE without a rewrite. KNet's existing common HTTP exchange, bounded body
store, canonical protocol-message child records, generic proxy stream hooks, breakpoint coordinator, Traffic
decoder registry, and shared API Studio workspace already provide the required stable boundaries. The work is a
new semantic engine plus one generic streaming HTTP execution seam—not a replacement of the proxy or traffic
architecture.

The scalable implementation slice is complete and does not require a future proxy, Traffic, persistence, or API
Studio rewrite. KNet must still describe live SSE as experimental until incremental content decoding,
cross-platform/device evidence, complete HTTP/2 isolation, and long-lived resource recovery pass SSE-5. The
capability catalog deliberately preserves that distinction.
