# KNet gRPC Target Architecture and Implementation Plan

Status: **IMPLEMENTED — LOCAL QUALIFICATION ACTIVE; REAL-DEVICE QUALIFICATION PENDING**  
Current runtime capabilities: **`grpc.capture`, `grpc.breakpoints`, and `grpc.apistudio` are EXPERIMENTAL**  
Target: native gRPC inspection, message-aware breakpoints, and API Studio execution over KNet's existing
HTTP/2 transport without replacing the proxy, canonical HTTP traffic, persistence, or connectivity architecture.

Executable evidence and the remaining promotion blockers are tracked in
[`grpc_qualification.md`](grpc_qualification.md).

## 1. Decision summary

KNet will treat gRPC as two related views of the same network operation:

1. The existing `HttpExchangeSnapshot` remains the canonical request/response record. It owns the HTTP/2
   method, authority, path, headers, trailers, connection ID, stream ID, timings, origin, and terminal state.
2. Ordered protocol messages are additive children of that exchange. They own direction, sequence, wire payload
   reference, capture state, compression facts, and optional schema-derived presentation.

There will be no `GrpcHttpRequest`, `GrpcHttpResponse`, second traffic repository, gRPC-only proxy server, or
gRPC-only Traffic screen. API Studio, Traffic, breakpoints, export, and future replay will resolve the same parent
HTTP exchange and the same stored protocol-message records.

A dedicated `:engine:grpc` JVM module is justified now. Native gRPC requires HTTP/2 message framing, protobuf
descriptors, server reflection, compression codecs, status/trailer interpretation, and outbound call execution.
Keeping those dependencies out of `:engine:proxy`, `:core:http`, `:application:desktop`, and Compose avoids coupling the
stable core to one protocol. Future protocols do not need to use this module; they implement the same
protocol-message and breakpoint extension contracts independently.

## 2. Definition of complete native gRPC support

KNet may advertise native gRPC support only when all of the following work through real KNet sockets:

- Detection of native gRPC over H2C and TLS/ALPN HTTP/2 without URL-only guesses.
- Unary, server-streaming, client-streaming, and bidirectional-streaming RPCs.
- Independent client-facing and upstream protocol reporting from the existing HTTP/2 exchange.
- Correct five-byte gRPC envelope parsing across arbitrary HTTP/2 DATA frame boundaries.
- Multiple gRPC messages inside one DATA frame and one message split across many DATA frames.
- Ordered message capture linked to the parent exchange, connection ID, and stream ID.
- Identity and gzip message compression with an additive codec registry for future encodings.
- Request/response metadata, binary `-bin` metadata, response trailers, `grpc-status`, `grpc-message`, and
  `grpc-status-details-bin` preservation.
- Descriptor-backed protobuf decoding from imported descriptor sets or explicit server reflection.
- Useful schema-free fallback showing service/method, message direction, sequence, size, compression, and raw
  bytes without inventing field names.
- Per-message breakpoints that pause only the matched HTTP/2 child stream.
- Safe message editing and reframing when a descriptor and supported compression codec are available.
- Cancellation, deadline, reset, malformed-message, unsupported-encoding, and connection-loss behavior.
- Durable restart-safe message paging, bounded body ownership, deletion, retention, and orphan recovery.
- API Studio unary and all three streaming call shapes with cancellation and actual protocol reporting.
- Cross-platform automated qualification plus real Android/iOS Wi-Fi proxy smoke evidence before promotion to
  `SUPPORTED`.

The first native implementation does not include gRPC-Web, Connect RPC, transcoded JSON/REST gateways, or gRPC
over HTTP/3. Those are different wire contracts and must receive separate capability IDs and adapters.

## 3. Existing repository foundation

### KEEP

- `:core:traffic` `HttpRequestSnapshot`, `HttpResponseSnapshot`, `HttpExchangeSnapshot`, `ConnectionId`,
  `StreamId`, body references, trailers, timings, origin, and lifecycle states.
- `:engine:proxy` as the only owner of Netty channels, HTTP/2 frames, flow-control credit, pooled upstream
  connections, and reference-counted buffers.
- The current HTTP/2 H2C/TLS negotiation, per-stream bridge, protocol-leg reporting, resets, GOAWAY behavior,
  backpressure, and qualification gate.
- `ProxyCaptureSink` as the non-blocking capture boundary.
- `:application:desktop` as owner of use cases, bounded breakpoint coordination, capability-neutral ports, and lifecycle.
- `BreakpointProtocolExtension` and its dynamic rule-field schema. The gRPC rule definition will be another
  contribution, like GraphQL, rather than a branch in the rule editor.
- `:engine:interceptor` as the HTTP request/response breakpoint adapter. Existing HTTP and GraphQL behavior will
  not be rewritten to pass through gRPC abstractions.
- Room body-object storage, deletion outbox, retention, integrity recovery, canonical exchanges, semantic
  annotations, and the existing dormant `duplex_messages` table.
- Traffic's generic descriptor strategy, inspection tabs, code editor, paging, resizable columns, and global live
  interception drawer shell.
- `:testingServer` native grpc-java `ProtocolLab` service, health service, reflection service, and the existing
  unary/server-stream/client-stream/bidirectional/failure fixtures.
- `:products:desktop` as the composition root and capability catalog owner.

### MODIFY

- `:core:traffic` to add strongly typed, protocol-neutral message snapshots/events and IDs while leaving the
  canonical HTTP request/response models unchanged.
- `ProxyCaptureSink` and desktop capture adapters to accept bounded message lifecycle events in addition to HTTP
  exchange events.
- The dormant `duplex_messages` persistence path to support inserts, terminal updates, keyset queries, body
  references, retention, deletion, and restart recovery.
- The application breakpoint candidate/control model to support a stable protocol-message interception unit in
  addition to the existing HTTP exchange unit.
- Breakpoint phase presentation so a gRPC extension can display `Client messages` and `Server messages` while
  persisted request/response phase semantics remain stable.
- The live interception drawer to choose a renderer by interception-unit kind, not by concrete protocol name.
- Traffic inspection to expose a generic `Messages` tab only when an exchange has stored messages.
- API Studio workspace/document dispatch so an extension-owned authored protocol request can coexist with the
  existing `SavedApiRequest` HTTP/GraphQL editor.
- The runtime capability catalog to track gRPC capture, breakpoints, and API Studio execution independently.

### MOVE

- No existing HTTP, GraphQL, SSE, proxy, persistence, or UI responsibility needs to move.
- If any gRPC prototype parsing appears in a proxy handler during delivery, it must move into `:engine:grpc`
  before the phase can complete.

### REMOVE

- No working behavior during the additive phases.
- Remove temporary duplicate gRPC DTOs, full-body-only parsers, URL-only detection, or UI protocol branches
  before qualification.
- Do not revive the previously removed dormant protobuf inspection stubs; the new implementation must use the
  canonical message and descriptor contracts described here.

### ADD

- `:engine:grpc` with a `MODULE.md` at its module root.
- Protocol-neutral message contracts and queries.
- A bounded proxy payload-extension SPI that lets a protocol module observe or transform one stream without
  making `:engine:proxy` depend on that protocol module.
- Native gRPC classification, framing, compression, descriptor, reflection, semantic inspection, breakpoint,
  and API Studio execution adapters.
- Generic message timeline presentation and message interception presentation.
- Through-proxy qualification fixtures and one root `grpcQualification` task.

## 4. Target dependency direction

```text
                     :core:domain        :core:traffic
                           ^                   ^
                           |                   |
                     :application:desktop -------------+
                        ^      ^
                        |      |
              :engine:grpc    :data:desktop ------> :storage
                  ^    ^             ^
                  |    |             |
       :engine:proxy    +-------------+
                  ^
                  |
          :engine:interceptor

UI modules ----------> :application:desktop / :core:domain / :core:traffic
products:desktop -----> all runtime implementations and UI composition
testingServer --------> test-only server libraries; no production module depends on it
```

Rules:

- `:engine:proxy` exposes only transport-neutral stream hooks and never imports gRPC/protobuf types.
- `:engine:grpc` may depend on the proxy hook API, application contracts, canonical models, protobuf, and grpc-java.
- `:application:desktop` never depends on Netty, Room, grpc-java, protobuf runtime objects, or Compose.
- `:storage` never decodes protobuf or decides gRPC semantics.
- UI never receives Netty frames, protobuf descriptors, Room entities, or grpc-java call objects.
- `:core:http` remains the ordinary HTTP/API Studio client and does not acquire grpc-java dependencies.
- The product composition root supplies gRPC extensions to proxy, inspection, breakpoint, descriptor, API Studio,
  and capability registries.

## 5. Proposed Gradle and package structure

```text
engine/grpc/
├── MODULE.md
├── build.gradle.kts
└── src/
    ├── main/kotlin/com/devuloopers/knet/engine/grpc/
    │   ├── detection/       # content-type/path validation and RPC identity
    │   ├── framing/         # incremental five-byte envelope deframer/framer
    │   ├── compression/     # identity/gzip registry and limits
    │   ├── descriptor/      # descriptor sets, reflection, cache, schema lookup
    │   ├── inspection/      # RPC annotation and protobuf presentation documents
    │   ├── breakpoint/      # criteria extension and message breakpoint adapter
    │   ├── proxy/           # transport-neutral stream hook implementation
    │   └── client/          # API Studio native gRPC execution
    └── test/kotlin/com/devuloopers/knet/engine/grpc/

core/traffic/src/commonMain/kotlin/com/devuloopers/knet/traffic/
├── id/                      # protocol-message identity
├── model/message/           # protocol-neutral message snapshots
└── event/                   # message capture lifecycle events

application/desktop/src/main/kotlin/com/devuloopers/knet/application/
├── port/message/            # message store/query/body access contracts
├── port/breakpoint/         # generic interception-unit additions
├── port/apistudio/          # protocol document/executor/editor-neutral contracts
└── usecase/message/         # paged timeline and detail use cases

storage/src/jvmMain/kotlin/com/devuloopers/knet/storage/capture/
├── entity/                  # activate/extend DuplexMessageEntity
└── dao/                     # insert/finalize/keyset/count/delete queries

data/desktop/src/jvmMain/kotlin/com/devuloopers/knet/data/desktop/
├── capture/                 # message event writer and body ownership
├── mapper/                  # entity/canonical message mapping
└── message/                 # desktop message query/store adapters

ui/desktop/traffic/.../inspector/messages/
ui/desktop/breakpointManager/.../intercept/message/
ui/desktop/apiStudio/.../protocol/

products/desktop/src/jvmMain/kotlin/com/devuloopers/knet/products/desktop/di/grpc/
testingServer/src/main/proto/protocol_lab.proto
testingServer/src/main/kotlin/com/devuloopers/knet/testingserver/grpc/
```

Every new Gradle module must have its own `MODULE.md`. Existing module documents are updated only where their
real responsibilities change.

## 6. Canonical traffic and message model

### Parent exchange remains the SSOT

For a native gRPC call, the parent HTTP exchange records:

- `POST /package.Service/Method` and authority;
- client-facing and upstream HTTP/2 protocol legs;
- ordered request/response metadata headers;
- response trailers, including raw gRPC status fields;
- connection/stream identity, timings, origin, state, and body-storage relationships.

No feature reconstructs those values from a gRPC-specific copy.

### Additive message records

The core message contract will be protocol-neutral and strongly typed. Its minimum information is:

- message ID, protocol ID, parent exchange ID, connection ID, and optional stream ID;
- monotonically ordered sequence and direction (`CLIENT_TO_SERVER` or `SERVER_TO_CLIENT`);
- protocol-owned message-kind ID rather than a closed gRPC enum;
- occurred timestamp and terminal/capture state;
- declared payload length, observed length, stored length, and truncation/failure state;
- compression flag and encoding token;
- immutable `MessageBodyRef` to exact wire payload bytes, not embedded `ByteArray` content.

The five-byte gRPC envelope is transport framing and is not stored as part of the protobuf payload. Enough framing
metadata is preserved to reproduce or diagnose it. Decoded protobuf JSON/text is a derived presentation document,
not the authoritative body.

### Storage activation

`DuplexMessageEntity` is **KEEP + MODIFY**, not replaced. The implementation will:

- add DAO admission/finalization and exchange-scoped keyset queries;
- add exact counts so the Messages tab does not infer totals from loaded pages;
- link message bodies through existing body-object rows;
- add only the framing/capture columns that cannot be derived safely;
- include message bodies in retention, clear-traffic deletion, outbox cleanup, and orphan recovery;
- keep certificate, collection, settings, and device data outside traffic clearing;
- preserve deterministic order after restart.

Semantic `InspectionAnnotation` remains appropriate for one RPC summary. It does not replace the ordered message
timeline and will not be overloaded with every protobuf payload.

## 7. Detection, framing, descriptors, and status

### Native classification

A stream is classified as native gRPC only when canonical metadata is consistent:

- HTTP/2 transport;
- `POST` method;
- native gRPC content type such as `application/grpc` or a valid native suffix;
- a valid `/fully.qualified.Service/Method` path.

The path alone is insufficient. `application/grpc-web*` is explicitly excluded and left for a future adapter.

### Incremental framing

The deframer consumes arbitrary bounded chunks and maintains only:

- up to five pending envelope bytes;
- declared length and delivered length for the current message;
- the current bounded capture/body writer or breakpoint reservation;
- a per-stream sequence counter.

It must accept coalesced and fragmented messages and reject invalid compression flags, length overflow, configured
oversize values, incomplete terminal messages, and bytes after a terminal state. Observation failure degrades
semantic capture but does not corrupt unrelated streams or the proxy listener.

### Descriptor resolution

Descriptor precedence is deterministic:

1. a user-imported descriptor set or `.proto` source compiled to a descriptor set;
2. an already-approved origin-scoped reflection cache;
3. schema-free raw presentation.

KNet will not silently issue reflection calls while passively capturing traffic. Reflection is an explicit user or
API Studio action because it creates network traffic and can expose service metadata. Caches are keyed by origin,
TLS policy, and descriptor fingerprint, bounded by count/bytes/time, and contain no private keys or auth secrets.

### Compression and status

- Identity and gzip are the first qualified codecs behind an encoding registry.
- Unsupported encodings remain forwardable and capturable as raw bytes but are not presented as editable decoded
  messages.
- Decompression has a strict output limit and compression-ratio guard.
- `grpc-status`, `grpc-message`, `grpc-status-details-bin`, and custom trailers remain canonical HTTP trailers.
- A gRPC inspection document derives a human-readable status and typed details without deleting or rewriting raw
  trailer fields.

## 8. Runtime event flow, memory ownership, and backpressure

```text
HTTP/2 child stream
    |
    +-- canonical HTTP exchange admission (existing)
    |
    +-- protocol stream extension registry
            |
            +-- no match -> existing zero-gRPC-overhead forwarding path
            |
            +-- gRPC match -> incremental message deframer
                                  |
                                  +-- forward original DATA under HTTP/2 credit
                                  +-- bounded capture events/body writer
                                  +-- optional matched message breakpoint
                                  +-- RPC summary/status annotation
```

Ownership rules:

- Netty retains exclusive ownership of inbound `ByteBuf` values.
- Protocol extensions receive immutable bounded data or an explicitly scoped transport slice; no reference-counted
  object crosses into application, storage, or UI.
- Ordinary capture never requires full-message heap aggregation. Payload bytes stream into the existing file-backed
  body store under a reservation.
- Only a message selected by an enabled breakpoint may be aggregated for editing, and only within an explicit
  per-message limit.
- Each child stream has a message-state budget; each parent connection and the process have stricter aggregate
  budgets.
- Capture exhaustion records a gap/truncated message and keeps forwarding. Breakpoint budget exhaustion fails
  closed to `Continue unchanged` with a safe diagnostic.
- A paused gRPC message withholds credit only for its child stream. It must not pause the HTTP/2 parent or sibling
  RPCs.
- Cancellation, RST_STREAM, GOAWAY, timeout, and disconnect each release message writers, body reservations,
  descriptor work, and breakpoint state exactly once.
- Descriptor decoding and semantic formatting run on bounded worker dispatchers, never a Netty event loop or the
  Compose UI dispatcher.

## 9. Breakpoint architecture and UI

### Rule matching

`GrpcBreakpointProtocolExtension` contributes fields through the existing dynamic rule schema:

- fully qualified service;
- method;
- direction (`Client message`, `Server message`, or both);
- optional message sequence/range;
- optional gRPC status for terminal matching;
- later, descriptor-backed field predicates as another versioned criterion, not a core rewrite.

When a rule is created from Traffic, service, method, direction, and known descriptor facts are suggested from the
selected message. Two RPC methods hosted at the same origin remain distinct because native gRPC identity includes
the exact service and method path.

The current rule `BreakpointPhase` remains the persisted transport direction: request maps to client messages and
response maps to server messages. `BreakpointProtocolDefinition` gains UI-neutral labels and supported-phase
metadata so the editor displays protocol-correct wording without checking for `grpc`.

### Runtime candidate

The application interception queue gains a stable message unit alongside the existing HTTP exchange unit. A
message candidate contains canonical parent identity, protocol ID, direction, sequence, bounded payload, framing
facts, and optional versioned presentation document. It contains no Netty or protobuf runtime object.

Supported initial decisions are:

- continue unchanged;
- replace payload and rebuild its envelope when editable;
- cancel this RPC/HTTP2 stream.

The UI labels cancellation explicitly as `Cancel RPC`; it does not misleadingly call it `Drop request`. Removing
one message from a live stream is deferred until a protocol adapter can declare that operation safe. Decision
capabilities come from the candidate, so future WebSocket/SSE adapters can expose different actions additively.

### Live drawer

The global drawer shell, queue, selection, timeout, and close behavior remain shared. It selects one of two stable
unit renderers:

- existing HTTP request/response editor;
- generic protocol-message editor.

The message renderer shows protocol, service/method summary, direction, sequence, compression, payload size, and
schema state. Descriptor-backed messages use structured protobuf fields/JSON in the common code editor. Unknown
schemas show a read-only raw/hex representation and download/copy actions. No `if (grpc)` branch belongs in the
drawer shell.

## 10. Traffic UI architecture

- The Traffic table still shows one row per canonical HTTP exchange.
- A gRPC request descriptor strategy contributes badge `gRPC` and title `Service/Method` through the existing
  descriptor registry. Table/ViewModel code does not parse gRPC paths.
- Overview retains raw client/upstream protocols, connection/stream IDs, source, timings, and HTTP status; a generic
  inspection card adds RPC service, method, call shape, schema source, and gRPC terminal status.
- Existing Request and Response tabs retain raw HTTP metadata/body/trailers.
- A generic `Messages` tab appears only when the message-count use case reports children for that exchange.
- Messages use keyset paging and a visible scrollbar. Each row shows sequence, direction, time, decoded type when
  known, compression, size, and capture state.
- Selecting a message opens a detail panel using the common formatter/code editor. UI reads bodies lazily and
  boundedly; scrolling the list never loads all payloads.
- Search/filter additions operate through typed application/storage queries. Traffic does not scan message files.

The same Messages tab can later display WebSocket frames or another duplex protocol without changing its shell.

## 11. API Studio architecture

The existing `SavedApiRequest` remains the HTTP/GraphQL authored-document model. It will not gain nullable gRPC
service, descriptor, streaming, or message fields.

API Studio adds an open, versioned protocol-document envelope with:

- stable request/document ID and user/generated name ownership;
- `RequestKindId` and extension payload schema version;
- extension-owned encoded draft payload;
- shared collection/folder relationship and timestamps.

`:engine:grpc` owns the typed `GrpcRequestDraft` codec and execution adapter. Its draft includes target, TLS/proxy
policy, descriptor source, service/method, call shape, deadline, ordered metadata, outbound messages, and expected
status. UI obtains an editor contribution from an API Studio protocol registry; adding WebSocket later adds another
contribution rather than another workspace `when` branch.

Delivery order is deliberate:

1. unary composer and execution;
2. server-streaming receive timeline;
3. client-streaming outbound message queue and half-close;
4. bidirectional live send/receive timeline;
5. saved drafts, collections, cancellation, replay, and import/export.

When capture is enabled, API Studio gRPC calls enter Traffic through the same local proxy attribution path as
ordinary API Studio HTTP calls. When capture is disabled, direct execution remains direct and must not use a stale
proxy route. Traffic stores the same canonical exchange/message events regardless of whether the source is a phone,
desktop client, or API Studio.

## 12. Lifecycle, concurrency, network state, and security

- Starting/stopping capture attaches or detaches message persistence and breakpoints without rebuilding the HTTP/2
  transport unless the proxy listener itself stops.
- A proxy restart accepts new calls immediately; body cleanup, annotation cleanup, and descriptor-cache maintenance
  remain background work.
- Existing RPC streams receive deterministic cancellation on proxy shutdown and exactly one terminal state.
- Reflection and API Studio calls honor proxy availability, selected network route, TLS policy, deadlines, and
  coroutine cancellation.
- Imported descriptors are treated as untrusted input: size/count/depth limits, safe parser failures, and no code
  generation or arbitrary compiler execution in the desktop process.
- Binary metadata is decoded only under a strict bound and never logged verbatim by default.
- Authorization metadata, cookies, message payloads, and status details do not appear in normal logs.
- Upstream TLS verification remains strict by default; KNet's root CA is used for KNet's local interception leg,
  not as a global trust-all policy.
- Per-message and aggregate limits are configurable through runtime policy, validated once, and surfaced through
  safe diagnostics.

## 13. Incremental implementation phases

All phases are additive. Existing HTTP, GraphQL, SSE, Wi-Fi, certificate, and API Studio regression gates must stay
green. No phase launches the desktop app as part of automated verification.

### G0 — Freeze baseline and extend the protocol lab

Status: **PARTIAL — all cardinalities, health, reflection, typed failure, and trailers are executable; native gRPC
TLS/ALPN and generated-client-through-proxy fixtures remain qualification work**

- Preserve the current direct grpc-java cardinality/status/trailer tests.
- Add TLS/ALPN native gRPC and deterministic compression, deadline, cancellation, malformed-envelope, large-message,
  slow-stream, and concurrency fixtures.
- Add generated-client tests that traverse a real KNet proxy, not only direct server tests.

Exit gate: the lab independently proves every intended scenario, and a minimal client can traverse KNet's current
HTTP/2 transport before semantic capture is introduced.

### G1 — Canonical message contracts and durable storage

Status: **IMPLEMENTED — schema v23, message events/query/body ownership, retention, clear, and restart recovery**

- Add protocol-neutral message IDs/models/events and capture ports.
- Activate `DuplexMessageEntity`, message body ownership, keyset paging, exact counts, retention, deletion, and
  restart recovery.
- Add synthetic event tests for interleaved streams, truncation, failure, and clear-traffic behavior.

Exit gate: concurrent message events persist and restore in exact order without Netty/protobuf types outside engine
modules.

### G2 — Native gRPC classification and observation-only framing

Status: **IMPLEMENTED — bounded incremental classification/deframing and proxy stream hooks; capability remains
EXPERIMENTAL until the real-device/TLS qualification matrix passes**

- Create `:engine:grpc` and its `MODULE.md`.
- Add the proxy stream-extension SPI and native classifier.
- Add incremental deframing and identity/gzip metadata while forwarding original bytes unchanged.
- Capture all four call cardinalities through H2C and TLS.

Exit gate: message boundaries, directions, sequences, sizes, compression facts, and terminal status are correct;
unrelated HTTP/2 streams remain unaffected. Capability remains `EXPERIMENTAL`.

### G3 — Descriptor and semantic inspection

Status: **IMPLEMENTED — bounded descriptor-set import, explicit v1 reflection, registry isolation, decode, and raw
fallback**

- Add descriptor-set import, explicit reflection, bounded cache, schema lookup, and raw fallback.
- Add protobuf-to-presentation conversion and one RPC summary annotation.
- Add the gRPC request descriptor contribution for unified badges/names.

Exit gate: known schemas decode correctly, unknown schemas remain inspectable, and malformed/untrusted descriptors
fail safely without changing forwarding.

### G4 — Traffic Messages presentation

Status: **IMPLEMENTED — generic message query/body contracts and Traffic Messages presentation**

- Add paged message use cases, counts, lazy body detail, and generic Messages tab.
- Add RPC overview/status presentation and descriptor-driven table label.
- Verify restart, pagination, selection, large streams, missing bodies, and clear traffic.

Exit gate: Traffic reads only canonical/application contracts and contains no gRPC wire parsing.

### G5 — Message breakpoint runtime

Status: **IMPLEMENTED — per-stream message gate, unchanged/replace/drop decisions, envelope rebuild, and bounded
ownership; extended 20-stream soak remains part of qualification**

- Add the generic message interception unit, limits, timeout, decisions, and queue integration.
- Add `GrpcBreakpointProtocolExtension` and per-child-stream pause/reframe/cancel adapter.
- Qualify unchanged forwarding, descriptor-backed edit, compressed edit, cancellation, timeout, disconnect, and
  concurrent sibling streams.

Exit gate: pausing one message in one of at least 20 concurrent streams does not delay the other 19, and all owned
buffers/reservations are released once.

### G6 — Breakpoint rule and live-drawer presentation

Status: **IMPLEMENTED — protocol-supplied criteria/suggestions and generic live message drawer**

- Add protocol-supplied phase labels and gRPC criteria fields to the existing rule editor.
- Add generic message renderer to the global live drawer.
- Add smart rule suggestions from a selected gRPC message.

Exit gate: service/method/direction rules distinguish RPCs on the same host, and no gRPC-specific branch exists in
the drawer shell or common rule form.

### G7 — API Studio unary gRPC

Status: **IMPLEMENTED — protocol workspace SPI, persisted opaque documents/schemas, import/reflection, strict direct
or active-proxy routing, execution, cancellation, status, trailers, and actual protocol reporting**

- Add protocol document envelope, gRPC draft codec, descriptor/service browser, metadata editor, unary message
  editor, execution, cancellation, and persistence.
- Route through KNet only while capture is active and report actual negotiated protocol/status.

Exit gate: a saved unary call survives restart, executes directly and through active capture, and produces the same
canonical Traffic exchange/message records as an external client.

### G8 — API Studio streaming gRPC

Status: **IMPLEMENTED — server-stream timeline plus bounded interactive client/bidirectional send, half-close,
cancel, inbound flow control, and event history**

- Add server-streaming timeline, client-streaming send/half-close, and bidirectional live send/receive.
- Add deadlines, cancel, retry-safe behavior, stream backpressure, and bounded UI history.

Exit gate: all cardinalities work against the local lab with deterministic cancellation and no unbounded UI or
transport queues.

### G9 — Qualification and capability promotion

Status: **PARTIAL — root qualification task and macOS/Windows/Linux CI matrix are implemented; long-running soak and
real Android/iOS Wi-Fi evidence remain before support promotion**

- Add root `grpcQualification` task spanning architecture, traffic, storage/data, proxy, gRPC engine, breakpoint,
  API Studio, Traffic, testing server, and desktop composition.
- Run macOS/Windows/Linux CI, long-stream/parallel-stream soak, leak detection, and real Android/iOS Wi-Fi tests.
- Record evidence in module and protocol docs.

Exit gate: promote `grpc.capture`, `grpc.breakpoints`, and `grpc.apistudio` independently. No capability becomes
`SUPPORTED` merely because another gRPC capability passed.

## 14. Required test matrix

| Area | Required cases |
|---|---|
| Negotiation | H2C, TLS/ALPN, downstream H2/upstream H2, explicit downgrade diagnostics |
| Cardinality | Unary, server stream, client stream, bidirectional stream, empty request/response |
| Framing | split prefix, split payload, coalesced messages, zero-length message, malformed flag/length, early EOF |
| Compression | identity, gzip, unsupported encoding passthrough, decompression limit, ratio guard |
| Metadata | duplicate ASCII metadata, bounded `-bin`, headers, trailers, percent-encoded `grpc-message` |
| Status | OK, typed error, missing status, invalid status, details-bin, HTTP failure before gRPC status |
| Descriptors | imported set, reflection, cache isolation, missing schema, malformed/untrusted input |
| Backpressure | slow client, slow server, capture full, paused breakpoint, 20+ sibling streams, cancellation |
| Persistence | restart, keyset pages, exact counts, missing/corrupt body, retention, clear traffic, orphan recovery |
| Breakpoints | service/method/direction/sequence, same endpoint different method, edit, cancel, timeout, disconnect |
| API Studio | save/restore, direct, active proxy, stopped proxy, all cardinalities, deadline, cancel, source attribution |
| Security | TLS verification, auth redaction, binary metadata bounds, descriptor limits, no raw exception leakage |

## 15. Future protocol impact matrix

| Future feature | Reuse unchanged | Add | Modify stable core? |
|---|---|---|---|
| WebSocket | canonical exchange, message records, message paging, drawer shell | WebSocket frame adapter, descriptor, editor | No protocol branch; only new extension bindings |
| SSE | canonical exchange, body storage, message timeline | optional live SSE message adapter | No, existing post-capture inspector can coexist |
| Connect RPC | HTTP/2, message/storage/UI contracts | Connect classifier/framer/status/client extension | No gRPC core modification |
| gRPC-Web | HTTP exchange and message contracts | gRPC-Web text/binary framing and trailer adapter | No native gRPC parser modification |
| HTTP/3 gRPC | canonical exchange/message contracts | QUIC/HTTP3 transport plus gRPC stream hook adapter | Transport registry addition, not gRPC UI/storage rewrite |
| Mobile Companion | remote transport and canonical events | companion pairing/relay transport | No proxy/gRPC/PAC/manual/Wi-Fi migration |
| Relay | canonical events, origin, IDs, message storage | authenticated relay ingress/egress | No gRPC semantic rewrite |

## 16. Principal risks and controls

- **Risk: full-message buffering under streaming load.** Control: streaming body writers for ordinary capture;
  bounded aggregation only for a matched editable message.
- **Risk: parsing gRPC inside proxy handlers.** Control: transport-neutral hook plus `:engine:grpc` implementation;
  architecture verification forbids gRPC/protobuf imports in `:engine:proxy`.
- **Risk: protobuf cannot decode without a schema.** Control: explicit schema state and useful raw fallback; never
  fabricate JSON.
- **Risk: reflection creates hidden network activity.** Control: explicit user action and origin-scoped cache.
- **Risk: one breakpoint stalls a multiplexed connection.** Control: child-stream credit isolation and a
  concurrency gate with at least 20 siblings.
- **Risk: API Studio becomes a large protocol `when` tree.** Control: versioned document and editor/executor
  contribution registries.
- **Risk: capability claims outrun evidence.** Control: separate capture/breakpoint/client maturity and one
  reproducible qualification task.

## Target architecture verdict

The current KNet architecture can support native gRPC without rewriting the proxy, HTTP/2 transport, canonical
request/response model, Wi-Fi connectivity, certificates, Traffic shell, or persistence foundation. The required
work is substantial because gRPC is a bidirectional framed-message protocol, not merely protobuf in an HTTP body.
Activating the existing message-storage direction, adding one isolated gRPC engine, and evolving breakpoints/API
Studio around protocol-neutral message units is the realistic scalable path. If the phases and qualification gates
above are followed, later WebSocket, gRPC-Web, Connect RPC, relay, and companion features remain additive rather
than forcing another major migration.
