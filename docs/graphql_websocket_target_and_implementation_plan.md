# GraphQL over WebSocket Target Architecture and Implementation Plan

Status: **COMPLETE — LOCALLY QUALIFIED AS EXPERIMENTAL**  
Target protocol: `graphql-transport-ws` over the existing HTTP/1.1 WebSocket transport  
Capability target: Traffic inspection, message breakpoints, and API Studio subscription execution

## 1. Objective

Add modern GraphQL subscription semantics above KNet's existing WebSocket transport without changing the proxy's
HTTP forwarding contract, creating a second Traffic model, or coupling generic UI to GraphQL wire messages.

This increment is intentionally a semantic layer, not another socket implementation:

- `:engine:proxy` continues to own HTTP Upgrade and the protocol-neutral duplex relay.
- `:engine:websocket` continues to own RFC 6455 framing, masking, fragmentation, compression metadata, raw message
  capture, and wire reconstruction.
- GraphQL-over-WebSocket code owns only the `graphql-transport-ws` JSON envelope, operation correlation, semantic
  presentation, semantic breakpoint matching, and subscription authoring/execution.
- Traffic, breakpoints, API Studio collections, body storage, and persistence continue to consume their existing
  canonical/application contracts.

The first increment supports only the actively maintained `graphql-transport-ws` protocol. The legacy
`graphql-ws` subprotocol used by `subscriptions-transport-ws` is a separate compatibility feature and remains
unavailable unless a real product requirement justifies it.

## 2. Architectural decisions

### 2.1 One connection, one canonical parent

A GraphQL WebSocket connection remains one canonical `HttpExchangeSnapshot` representing its WebSocket handshake.
Every RFC 6455 logical message remains one `ProtocolMessageSnapshot` with
`MessageProtocolId.WEBSOCKET` and a body owned by the shared body store.

KNet will not add any of the following:

- a `GraphQLWebSocketRequest` or `GraphQLWebSocketResponse` traffic model;
- a second GraphQL subscription traffic repository;
- duplicate child-message rows containing the same payload;
- GraphQL fields in `HttpExchangeSnapshot` or `ProtocolMessageSnapshot`;
- GraphQL parsing inside Netty proxy handlers or Room adapters.

GraphQL semantics are a derived view of the canonical WebSocket message. The raw message remains available when
semantic parsing is unavailable, invalid, truncated, or unsupported.

### 2.2 Operation identity belongs to child messages

One `graphql-transport-ws` connection can carry several concurrent operations. Therefore:

- the parent Traffic row uses badge `GQL WS` and the WebSocket endpoint path;
- `subscribe`, `next`, `error`, and `complete` child messages show their operation ID;
- `subscribe` messages additionally show the parsed GraphQL operation name and type;
- later server messages resolve their operation name through bounded connection-scoped correlation state;
- API Studio collection/session names use the authored operation name because it is known before execution.

The parent row must never be renamed to one subscription operation because that would misrepresent multiplexed
connections.

### 2.3 Semantic fallback is mandatory

GraphQL decoding is selected only when the server negotiated `Sec-WebSocket-Protocol: graphql-transport-ws` and
the message is a valid, bounded UTF-8 JSON envelope. Otherwise the existing raw WebSocket decoder renders the
message.

This requires the common protocol-message presentation registry to support ordered decoders for the same transport
protocol. A GraphQL WebSocket decoder runs before the raw WebSocket fallback; it returns `null` when it does not
confidently own a message. Decoder identity and priority are explicit and validated at product composition.

### 2.4 Layered breakpoints produce one pause

A GraphQL subscription message is both a GraphQL WebSocket message and a WebSocket text message. Both semantic and
raw WebSocket breakpoint rules must remain useful, but one wire message must never open two interception drawers.

The message breakpoint candidate will expose an ordered protocol route, most specific first:

```text
graphql-websocket -> websocket
```

The application coordinator evaluates enabled rules in persisted priority order across that route, chooses one
matching rule, and publishes one pending decision. If no GraphQL WebSocket rule matches, a raw WebSocket rule may
still match. The proxy and generic drawer remain unaware of GraphQL message types.

### 2.5 API Studio gets a contributed editor, not another application shell

GraphQL subscriptions receive a dedicated API Studio contribution because query, variables, connection parameters,
operation lifecycle, and streamed results are materially different from the raw WebSocket composer.

The contribution reuses:

- `ApiStudioWorkspaceDocument` and the existing saved/unsaved collection tree;
- `ApiStudioProtocolAuthoringPort` and `ApiStudioProtocolSessionExecutor`;
- the existing WebSocket client/session transport;
- the shared code editor, key/value controls, dropdowns, drawer, and response/event timeline primitives.

It does not create GraphQL-only collections or a second workspace store.

## 3. Target module and package structure

```text
engine/
├── proxy/                         # KEEP: HTTP Upgrade and raw duplex relay only
├── websocket/                     # MODIFY: negotiated-subprotocol and semantic layering seams
├── protocol/                      # KEEP: reusable GraphQL document parser and HTTP GraphQL inspector
└── graphqlWebSocket/              # ADD: graphql-transport-ws semantics and execution
    ├── MODULE.md
    └── src/main/kotlin/com/devuloopers/knet/engine/graphqlwebsocket/
        ├── protocol/              # Envelope models, parser, validator, message types
        ├── session/               # Connection/operation correlation state machine
        ├── inspection/            # Traffic presentation decoder
        ├── breakpoint/            # Criteria, observation, replacement validation
        ├── descriptor/            # GQL WS request descriptor contribution
        └── apistudio/             # Draft codec, authoring adapter, interactive executor

ui/desktop/apiStudio/
└── graphqlWebSocket/              # ADD: contributed subscription editor
    ├── MODULE.md
    └── src/jvmMain/kotlin/com/devuloopers/knet/ui/desktop/apistudio/graphqlwebsocket/
        ├── model/
        ├── persistence/
        ├── view/
        └── viewmodel/

testingServer/
└── testingserver/graphql/         # MODIFY: deterministic subscription/error/lifecycle fixtures

products/desktop/.../di/
├── apistudio/                     # MODIFY: authoring, executor, workspace, ViewModel bindings
├── inspection/                    # MODIFY: decoder, breakpoint, capabilities
├── proxy/                         # MODIFY: semantic message contribution registration
└── request/                       # MODIFY: GQL WS descriptor strategy
```

The new engine module is justified because it depends on both `:engine:websocket` transport APIs and the GraphQL
document parser in `:engine:protocol`. Moving this state machine into either existing module would make raw
WebSocket depend on GraphQL or make the general semantic-inspector module own an interactive socket lifecycle.

## 4. KEEP / MODIFY / MOVE / REMOVE / ADD

### KEEP

- `HttpExchangeSnapshot` as the canonical parent request/response model.
- `ProtocolMessageSnapshot` and `MessageProtocolId.WEBSOCKET` as canonical child-message records.
- shared body ownership, limits, retention, Room tables, and Traffic queries.
- `:engine:proxy` duplex relay and its inspector/transformer contracts.
- RFC 6455 parsing, masking, fragmentation, and reconstruction in `:engine:websocket`.
- `GraphQLDocumentParser` as the GraphQL operation parser used by HTTP and WebSocket semantics.
- the generic breakpoint rule editor and live protocol-message interception drawer.
- the shared API Studio workspace document/collection architecture.
- the existing raw WebSocket API Studio editor as an independent tool.

### MODIFY

- `ProtocolMessagePayloadDecoder` and `ProtocolMessagePresentationRegistry`: add stable decoder identity and
  deterministic priority/fallback for multiple decoders of one transport protocol.
- protocol-message breakpoint candidate/gate: accept ordered semantic protocol layers and publish only the single
  winning rule.
- protocol-message suggestion input: allow a selected bounded child message to generate semantic breakpoint fields.
- WebSocket message pipeline: expose the response-selected subprotocol and complete uncompressed text-message facts
  to registered semantic contributors after RFC 6455 framing.
- `RequestKindId` and `ApiStudioEditorId`: add normalized `graphql-websocket` identities.
- Traffic descriptor registration: prefer `GQL WS` when the negotiated/requested protocol identifies the modern
  GraphQL transport, while retaining WebSocket as fallback.
- protocol lab: make subscription IDs, event counts, delays, typed failures, completion, and cancellation
  deterministic.
- product DI and runtime capability evidence.

### MOVE

No existing production class must move for this increment. The existing GraphQL HTTP implementation and raw
WebSocket implementation keep their current ownership.

### REMOVE

No current feature is removed. Any temporary implementation that branches on GraphQL inside a proxy handler,
duplicates WebSocket payloads, or stores GraphQL operation fields in canonical transport rows must be removed before
the qualification gate can pass.

### ADD

- `:engine:graphqlWebSocket` and its `MODULE.md`.
- `:ui:desktop:apiStudio:graphqlWebSocket` and its `MODULE.md`.
- strict `graphql-transport-ws` envelope parser and typed state machine.
- semantic Traffic decoder and `GQL WS` descriptor.
- message-aware breakpoint extension and replacement validator.
- GraphQL subscription API Studio contribution.
- local integration and qualification tasks.

## 5. Wire model and state machine

The semantic module models the modern protocol message types as a closed enum or sealed hierarchy:

- `connection_init`
- `connection_ack`
- `subscribe`
- `next`
- `error`
- `complete`
- `ping`
- `pong`

Unknown `type` values remain visible as raw WebSocket JSON but are not classified as valid GraphQL WebSocket
messages. The parser must not silently coerce malformed envelopes.

Connection state is confined to one WebSocket connection and includes:

- negotiated subprotocol;
- whether `connection_init` and `connection_ack` completed;
- bounded active operation map keyed by operation ID;
- operation name/type derived from each `subscribe.payload`;
- per-operation terminal state;
- connection and acknowledgement deadlines.

Required state rules include:

- at most one accepted `connection_init` per connection;
- `subscribe` is valid only after acknowledgement;
- operation IDs are unique while active and reusable only after terminal completion;
- `next`, `error`, and `complete` correlate only to a known active operation;
- client `complete` stops one operation without closing unrelated operations;
- socket close/cancel terminalizes every remaining operation and releases all state;
- protocol `ping`/`pong` payloads are kept separate from RFC 6455 ping/pong control frames.

## 6. Traffic architecture

### Capture and storage

The existing WebSocket inspector continues to persist exactly one canonical message and body. Semantic parsing does
not write another body or another message row.

Traffic presentation resolves the selected message as follows:

1. Load at most the existing one-MiB protocol-message preview.
2. Ask ordered WebSocket decoders for a presentation.
3. The GraphQL WebSocket decoder validates negotiated subprotocol and envelope.
4. A confident semantic result provides message type, operation ID/name/type, JSON formatting, and safe summary.
5. A `null` semantic result falls through to the raw WebSocket decoder.

The initial increment does not add N+1 body reads to the Traffic message list. Compact rows continue to use stored
canonical metadata; semantic details are loaded for the selected message. If a later measured UX requirement needs
semantic list summaries, add a generic protocol-message annotation store rather than GraphQL columns.

### Memory and lifecycle

- Payload bytes remain owned by the body store and breakpoint candidate wrappers.
- Semantic parsers receive defensive bounded copies and retain only small operation facts.
- Operation maps have configurable entry and character limits.
- Connection state is removed on normal close, protocol failure, cancellation, parent termination, and capture
  detachment.
- Invalid/truncated semantic payloads never prevent transparent raw forwarding.

## 7. Breakpoint architecture

Add `GraphQLWebSocketBreakpointExtension` with `BreakpointInterceptionUnit.PROTOCOL_MESSAGE` and these editor fields:

| Field | Type | Meaning |
|---|---|---|
| Direction | Choice | Any, client messages, or server messages |
| Message type | Choice | Any modern protocol envelope type |
| Operation name | Text, optional | Exact parsed GraphQL operation name |
| Operation ID | Text, optional | Exact multiplexed subscription ID |

Smart rule creation from a selected semantic child message pre-populates direction, message type, operation name,
and operation ID. Creating a rule from only the parent handshake selects the GraphQL WebSocket protocol but leaves
operation fields empty because no operation exists at handshake time.

Replacement behavior:

- pause/continue and connection drop use the existing generic decisions;
- replacement is allowed only for complete, uncompressed text messages inside the existing editable-body limit;
- replacement must remain valid UTF-8 JSON and a valid `graphql-transport-ws` envelope;
- `subscribe` replacements must contain a valid GraphQL operation document;
- operation ID and lifecycle-changing message type cannot be changed in the first increment;
- invalid edits fail closed and never emit malformed wire bytes;
- compressed WebSocket messages remain non-editable until `permessage-deflate` mutation is implemented separately.

The existing breakpoint manager UI renders all fields through its generic `Text` and wrapping `Choice` schema. It
must not gain a GraphQL-specific branch.

## 8. API Studio architecture

Add a `GraphQL WS` workspace contribution using the shared collection sidebar. Its initial blank state does not
materialize an unsaved document until the first meaningful edit, matching the gRPC and WebSocket editors.

### Authoring state

- `ws://` or `wss://` endpoint;
- handshake headers/authentication;
- connection timeout and acknowledgement timeout;
- optional `connection_init.payload` JSON;
- GraphQL query/subscription document;
- operation name;
- variables JSON;
- extensions JSON;
- an optional user-controlled operation ID, generated only when execution starts if left blank;
- active session and operation states;
- bounded event timeline.

The executor always requests `graphql-transport-ws`; users do not manually type the subprotocol in this editor.
The raw WebSocket editor remains available when arbitrary subprotocol control is required.

New editors contain no sample endpoint, GraphQL document, operation ID, or JSON payload. Connection and
acknowledgement deadlines are also visually blank; their placeholders describe the operational defaults applied at
execution time. Workspace persistence preserves blank authored values exactly.

### Execution flow

```text
GraphQL WS editor
    -> common API Studio workspace document
    -> GraphQL WebSocket authoring adapter validates the draft
    -> existing WebSocket session transport opens ws/wss connection
    -> require negotiated graphql-transport-ws
    -> send connection_init
    -> await connection_ack under deadline
    -> send subscribe with stable operation ID
    -> publish next/error/complete events to the bounded timeline
    -> client complete, cancel, or socket close terminalizes the operation
```

The engine state machine supports multiple operation IDs from the start even if the first UI presents one active
operation at a time. This avoids replacing the executor when multi-subscription tabs are later added.

When Traffic capture is enabled, API Studio uses the same capture-aware proxy routing policy as HTTP, gRPC, and raw
WebSocket. When capture is disabled, execution is direct and must not appear in Traffic.

## 9. Concurrency, backpressure, and security

- Raw forwarding remains governed by the existing duplex relay backpressure. Semantic observation never grants
  transport credit and never blocks forwarding.
- Only an enabled matching breakpoint may intentionally suspend one complete WebSocket message.
- API Studio uses bounded inbound event buffering and explicit overflow behavior; it never retains an unlimited
  subscription stream.
- Connection, acknowledgement, idle, and operation deadlines are distinct and cancellation-safe.
- Parsing is bounded by bytes, JSON depth/structure, field lengths, active operation count, and operation count per
  connection.
- `connection_init.payload`, headers, variables, and event payloads may contain credentials or personal data. They
  must not be written to logs or exception messages, and presentation must use the existing redaction policy where
  configured.
- The engine accepts semantics only after server subprotocol negotiation; a URL containing `graphql` is not proof.
- Invalid semantic input falls back to raw inspection for passive Traffic display, but API Studio authoring and
  breakpoint replacement reject invalid input before transmission.

## 10. Incremental implementation phases

### GW0 — Freeze baseline and protocol-lab contract [COMPLETED]

- Run `webSocketQualification`, GraphQL inspector tests, product composition tests, and protocol-lab tests.
- Extend the existing `/lab/v1/graphql/ws` fixture with deterministic finite subscription, typed error, cancellation,
  ping/pong, and concurrent operation scenarios.
- Assert actual `graphql-transport-ws` negotiation rather than accepting ordinary raw WebSocket echo behavior.

Exit gate: the current raw WebSocket and GraphQL HTTP behavior remains unchanged and the real fixture exposes every
state transition required by later phases.

### GW1 — Semantic model and layered extension seams [COMPLETED]

- Add `:engine:graphqlWebSocket` with strict envelope parsing and connection/operation correlation tests.
- Add selected-subprotocol facts to the WebSocket established-session path.
- Make protocol-message presentation decoders ordered and fallback-capable.
- Add ordered semantic protocol routes to message breakpoint candidates and deterministic one-rule selection.
- Add focused regression tests proving raw WebSocket behavior remains the fallback.

Exit gate: two semantic layers can share one canonical WebSocket message without duplicate capture or duplicate
breakpoint pauses; `:engine:proxy` has no GraphQL imports.

### GW2 — Traffic inspection and request identity [COMPLETED]

- Add the GraphQL WebSocket presentation decoder.
- Add `RequestKindId.GRAPHQL_WEBSOCKET` and the `GQL WS` request descriptor strategy.
- Render operation ID/name/type and formatted JSON for selected child messages.
- Cover malformed, unknown, truncated, binary, and non-negotiated fallback behavior.

Exit gate: one parent exchange and its existing child messages provide both raw and semantic Traffic inspection with
no schema-specific persistence changes.

### GW3 — Message-aware breakpoints [COMPLETED]

- Add the semantic breakpoint definition, criteria codec, observation, matcher, and smart suggestion.
- Add replacement validation and preserve operation identity/lifecycle invariants.
- Test two subscriptions using the same endpoint with different operation names and IDs.
- Test semantic-rule precedence, raw WebSocket fallback, timeout, drop, cancellation, and connection cleanup.

Exit gate: only the intended operation pauses, one wire message creates at most one pending drawer, and unrelated
operations continue unless the connection itself is explicitly dropped.

### GW4 — API Studio GraphQL subscription contribution [COMPLETED]

- Add the UI module, module documentation, workspace codec, ViewModel, and contribution.
- Add engine authoring codec and interactive execution state machine.
- Support connect/init/ack, subscribe, streamed next/error/complete, client completion, cancel, and close.
- Reuse shared collections and capture-aware routing.
- Add UI-state and engine tests without launching the desktop application.

Exit gate: a new transient or saved GraphQL subscription executes against the local protocol lab, survives workspace
restore, cancels deterministically, and appears in Traffic only while capture is enabled.

### GW5 — Product composition and qualification [COMPLETED]

- Register engine, proxy semantic contribution, decoder, breakpoint, descriptor, authoring, workspace, and ViewModel
  bindings only in `:products:desktop`.
- Add independent runtime capabilities for inspect, breakpoint, and API Studio execution.
- Add a root `graphQLWebSocketQualification` task.
- Run architecture checks, affected module tests, protocol-lab real-socket tests, and desktop composition tests.
- Record external `wss`, browser/mobile client, network-transition, and soak results before promotion from
  `EXPERIMENTAL` to `SUPPORTED`.

Exit gate: the complete local gate passes with no desktop app launch and capability claims cite exact evidence.

## 11. Required test matrix

| Area | Required evidence |
|---|---|
| Envelope parsing | Every modern message type, unknown type, malformed JSON, missing/invalid ID and payload |
| Negotiation | Requested but not selected, correctly selected, wrong selected protocol, missing protocol |
| State machine | init/ack ordering, duplicate init, operation ID reuse, concurrent operations, terminal cleanup |
| Traffic | Semantic presentation, raw fallback, no duplicate rows/bodies, bounded selected-body read |
| Breakpoints | Endpoint shared by different operation names, direction/type/ID matching, one pending decision |
| Mutation | Valid replacement, invalid JSON, changed ID/type, compressed message, oversized message |
| API Studio | blank draft, persistence, init/ack deadline, events, complete, cancel, close, capture-aware routing |
| Backpressure | slow consumer, bounded timeline, sibling operations, breakpoint suspension isolation |
| Failure | server error, socket close, proxy stop, capture detach, network loss, malformed server message |
| Composition | unique contribution identities, capability evidence, no protocol dependency in proxy/UI shell |

## 12. Explicit non-goals

- legacy `graphql-ws` / `subscriptions-transport-ws` compatibility;
- GraphQL over SSE or multipart incremental delivery in this increment;
- WebSocket over HTTP/2 extended CONNECT or HTTP/3;
- `permessage-deflate` semantic decoding or mutation;
- GraphQL schema introspection, autocomplete, validation, or completion UI;
- persisted GraphQL-specific traffic tables or canonical model fields;
- unlimited API Studio event history or unlimited concurrent subscription operations.

## 13. Completion verdict

This increment was delivered additively without changing the proxy forwarding contract or canonical traffic
schema. Ordered protocol-message presentation and layered breakpoint routing now provide the reusable extension
point for future semantic WebSocket subprotocols without coupling them to RFC 6455, persistence, or Traffic UI.

The local `graphQLWebSocketQualification` and base `webSocketQualification` gates pass without launching the
desktop application. The capability remains `EXPERIMENTAL`; external `wss`, browser/mobile, network-transition,
and release-soak evidence is intentionally tracked as qualification work rather than hidden behind a supported
claim. Exact evidence and exclusions are recorded in
[`graphql_websocket_qualification.md`](graphql_websocket_qualification.md).
