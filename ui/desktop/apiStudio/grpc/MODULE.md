# `:ui:desktop:apiStudio:grpc`

## Responsibility

This module owns the desktop authoring and execution presentation for native gRPC calls in API Studio.
It renders descriptor import or explicit server reflection, target and method selection, metadata, outbound
protobuf-JSON messages, stream execution state, cancellation, trailers, and the bounded message timeline. Its
versioned codec losslessly owns incomplete gRPC authoring payloads stored through the common workspace boundary.
It uses the common resizable API Studio authoring/result geometry, keeping target actions in the authoring pane
and the stream timeline full-height in the result pane.

## Dependency boundary

- Depends on `:application:desktop` authoring/execution contracts and safe domain identifiers.
- Reuses `:ui:core`, `:ui:desktop:codeEditor`, and the API Studio workspace contribution SPI.
- Does **not** depend on `:engine:grpc`, protobuf, grpc-java, Netty, Room, proxy handlers, or certificates.
- Is composed by `:products:desktop`; it does not define product DI.

## Does not own

- gRPC framing, compression, descriptor parsing/reflection transport, channels, HTTP/2, TLS, or proxy routing.
- Canonical traffic capture or persistence.
- Shared collection/sidebar UI, document placement, or HTTP/GraphQL API Studio documents.
- Breakpoint matching or live interception.

Future protocol workspaces should contribute the same API Studio SPI without modifying this module.

gRPC draft creation, selection, generated naming, rename/delete, and collection promotion use the common API Studio
sidebar. Draft edits auto-save through `ApiStudioWorkspaceDocumentStore`; imported descriptor bytes remain in the
independent schema store. The editor validates and materializes `ApiStudioProtocolDocument` only for reflection or
execution, so a blank host, blank port, or unfinished metadata row can safely survive restart.

Opening the gRPC tab renders a complete transient blank editor without writing to persistence. Its first meaningful
authoring mutation atomically materializes one unsaved workspace document containing that edit, reports the new ID
to the common shell, and then enters the normal debounced auto-save path. Message-tab and event selection remain
presentation-only and cannot create an abandoned request.

The optional deadline remains visually blank in new and persisted incomplete drafts. Reflection and invocation
apply the 30-second operational default only when execution begins.

Unary and server-streaming calls use the bounded batch execution contract. Client-streaming and bidirectional
calls use the generic interactive-session contract, so future streaming protocols can add their own workspace
without adding protocol checks to the API Studio shell.
