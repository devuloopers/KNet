# ADR 0005: Protocol Transport and Inspector Capability Truth

- Status: Accepted
- Date: 2026-08-18

## Context

GraphQL, gRPC, WebSocket, SSE, HTTP/2, and HTTP/3 require different combinations of transport framing, capture, semantic parsing, mutation, and UI. A parser class alone does not make a protocol supported.

## Decision

Transport adapters emit canonical exchanges, stream events, or protocol frames without depending on semantic inspectors. Asynchronous inspectors consume bounded snapshots/body access and publish versioned annotations. Capability status is reported per layer: detect, forward, capture, inspect, mutate, replay, and export. A layer is marked Supported only with a production-wiring end-to-end test.

HTTP/2 and HTTP/3 are transport increments. WebSocket and SSE add stream/frame capture after their transport prerequisites. GraphQL is an HTTP semantic inspector. gRPC combines HTTP/2 transport, message framing, optional descriptors, and its own inspector.

## Consequences

New inspectors do not change forwarding core. New transports do not force GraphQL or other semantic logic into Netty handlers. The product does not overstate dormant or partially wired code.
