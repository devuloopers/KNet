# `:engine:graphqlWebSocket`

## Responsibility

Owns modern `graphql-transport-ws` semantics above KNet's existing RFC 6455 WebSocket transport: bounded envelope
parsing, operation lifecycle correlation, Traffic presentation, semantic message breakpoints, request identity, and
API Studio execution documents.

## Boundaries

- Depends on `:engine:websocket` for connection and framed-message transport APIs.
- Reuses the GraphQL document parser from `:engine:protocol`.
- Does not parse frames, forward sockets, persist canonical traffic, access Room, or render Compose UI.
- Returns `null` from passive semantic decoders when negotiation or payload validation is inconclusive so raw
  WebSocket inspection remains available.

## Extension rule

Future WebSocket subprotocols must add sibling semantic modules and register through the same ordered presentation
and breakpoint-layer contracts; they must not add branches to `:engine:proxy` or canonical traffic models.
