# GraphQL over WebSocket Qualification

## Status

KNet's modern `graphql-transport-ws` semantic increment is locally qualified as `EXPERIMENTAL`. It is not promoted
to `SUPPORTED` until the external `wss`, browser/mobile device, network-transition, and release-soak matrices pass.

## Delivered boundaries

- `:engine:proxy` still owns only the protocol-neutral HTTP/1.1 Upgrade and duplex relay and has no GraphQL import.
- `:engine:websocket` still owns RFC 6455 framing, masking, message reconstruction, canonical child-message capture,
  and raw fallback presentation.
- `:engine:graphqlWebSocket` owns strict modern envelope parsing, bounded per-connection operation correlation,
  semantic presentation, layered message breakpoints, request identity, and API Studio execution.
- `:ui:desktop:apiStudio:graphqlWebSocket` contributes transient/saved GraphQL subscription authoring to the one
  shared API Studio workspace and collection sidebar.
- Canonical persistence remains one `HttpExchangeSnapshot` parent and its existing `ProtocolMessageSnapshot`
  children. No GraphQL-specific traffic row, body copy, or Room table was introduced.

## Local evidence

| Area | Evidence |
|---|---|
| Modern envelope parsing and legacy/malformed rejection | `GraphQLWebSocketProtocolTest` |
| Init/ack ordering, concurrent operations, ID reuse, and cleanup | `GraphQLWebSocketProtocolTest` |
| Semantic presentation priority and raw WebSocket fallback | `GraphQLWebSocketLayeringTest` |
| Negotiated-subprotocol requirement and message-kind filtering | `GraphQLWebSocketLayeringTest` |
| Operation-aware breakpoints and safe replacement identity | `GraphQLWebSocketLayeringTest` |
| One semantic/raw route producing one pause | `ProtocolMessageBreakpointRoutingTest` |
| Real local socket negotiation and streamed API Studio execution | `GraphQLWebSocketApiStudioExecutorTest` |
| Incomplete authoring round trip and generated naming | `GraphQLWebSocketWorkspaceDraftCodecTest` |
| Finite stream, typed error, cancellation isolation, concurrency, and protocol ping/pong | `ProtocolLabIntegrationTest` |
| Product contribution/registry uniqueness | `DesktopModulesTest` |

Run the complete non-UI-launching local gate with:

```bash
./gradlew graphQLWebSocketQualification
```

## Supported experimental envelope

- Modern `graphql-transport-ws` only, over KNet's HTTP/1.1 WebSocket transport.
- Strict `connection_init`, `connection_ack`, `subscribe`, `next`, `error`, `complete`, `ping`, and `pong`
  envelopes with bounded parsing and operation state.
- Concurrent operation correlation by connection and operation ID, including terminal cleanup and safe ID reuse.
- Semantic Traffic presentation with deterministic raw WebSocket fallback for unrecognized or invalid messages.
- Message breakpoints by direction, envelope type, operation name, and operation ID, with one winning rule per wire
  message and identity-preserving replacement validation.
- API Studio connection initialization, acknowledgement deadline, subscription execution, streamed events,
  completion, cancellation, workspace persistence, and capture-aware routing.

## Explicitly not claimed

- Legacy `graphql-ws` / `subscriptions-transport-ws` compatibility.
- WebSocket over HTTP/2 extended CONNECT, HTTP/3, or QUIC.
- Semantic mutation of compressed `permessage-deflate` messages.
- GraphQL schema introspection, autocomplete, validation, or editor completion.
- External `wss` server/device qualification, browser/mobile matrices, network-transition recovery, large concurrency
  soak, or production maturity.

These exclusions require additive compatibility, transport, or qualification work. They do not require changes to
the proxy relay, canonical traffic model, shared workspace store, or generic breakpoint coordinator.
