# KNet Local Protocol Testing Lab

## Purpose

The `:testingServer` application is a deterministic local target for KNet. It lets API Studio, the proxy,
Traffic, body formatters, breakpoints, and future protocol inspectors exercise real wire behavior without
depending on an external public service.

The server is intentionally independent of KNet production code. KNet reaches it over the network, so a test
cannot accidentally pass by sharing internal models or storage.

## Start and discover

Start only the testing server:

```shell
./gradlew :testingServer:bootRun
```

Default addresses:

- Browser dashboard: `http://127.0.0.1:9090/`
- Machine-readable manifest: `http://127.0.0.1:9090/lab/v1`
- GraphiQL: `http://127.0.0.1:9090/lab/graphiql`
- Native gRPC: `127.0.0.1:9091`
- TLS/ALPN HTTP/2: `https://localhost:9443/lab/v1/http2/echo`

Set `KNET_TEST_GRPC_PORT` or `KNET_TEST_HTTP2_TLS_PORT` to override the independent listener ports. The HTTP
listeners bind to all local interfaces so a phone on the same trusted network can use the corresponding desktop
LAN address.

To capture dashboard traffic, enable the KNet proxy and configure the browser or phone to use KNet before
opening the dashboard. API Studio can call the same HTTP and GraphQL addresses directly.

## HTTP behavior API

All routes below are under `http://127.0.0.1:9090`.

| Method | Route | Test behavior |
|---|---|---|
| Any standard method | `/lab/v1/http/echo` | Echo method, path, repeated headers, query values, cookies, text body, and receive time |
| GET | `/lab/v1/http/status?status=418` | Return a selected final status from 200 through 599 |
| GET | `/lab/v1/http/delay?millis=1000` | Non-blocking bounded latency |
| GET | `/lab/v1/http/redirect?status=302` | Local 301, 302, 307, or 308 redirect |
| GET | `/lab/v1/http/cookies` | Echo request cookies |
| GET | `/lab/v1/http/cookies/set` | Emit repeated `Set-Cookie` response fields |
| GET | `/lab/v1/http/auth/bearer` | Require a Bearer-shaped `Authorization` header |
| GET | `/lab/v1/http/auth/basic` | Require a Basic-shaped `Authorization` header |
| GET | `/lab/v1/http/auth/api-key` | Require an `X-API-Key` header |

The auth routes validate transport and UI behavior only. They do not contain real users, passwords, or secrets.

## Payload API

| Method | Route | Media type or purpose |
|---|---|---|
| GET | `/lab/v1/payload/json` | Nested `application/json` |
| GET | `/lab/v1/payload/ndjson?count=3&delayMillis=100` | Incremental `application/x-ndjson` records |
| GET | `/lab/v1/payload/xml` | Resource-backed XML |
| GET | `/lab/v1/payload/soap` | Resource-backed SOAP 1.2 envelope |
| GET | `/lab/v1/payload/text` | UTF-8 plain text |
| GET | `/lab/v1/payload/large-text?bytes=1048576` | Bounded large response for capture limits and compression |
| GET | `/lab/v1/payload/binary?bytes=256` | Deterministic arbitrary octets |
| GET | `/lab/v1/payload/cbor` | CBOR structured payload |
| GET | `/lab/v1/payload/messagepack` | MessagePack structured payload |
| GET | `/lab/v1/payload/protobuf` | Protobuf-encoded `EchoRequest` payload |
| POST | `/lab/v1/payload/form` | Consume and echo URL-encoded repeated fields |
| POST | `/lab/v1/payload/multipart` | Consume all parts and return safe part metadata |

Large and streaming inputs are clamped to safety limits so an accidental request cannot exhaust local memory.

## Streaming and WebSocket API

| Transport | Address | Behavior |
|---|---|---|
| SSE | `/lab/v1/streams/sse?count=5&delayMillis=250` | Finite named events with IDs and JSON bodies |
| Chunked HTTP | `/lab/v1/streams/chunks?count=5&delayMillis=250` | Finite newline-delimited text chunks |
| Raw WebSocket | `ws://127.0.0.1:9090/lab/v1/websocket/echo` | Echo text and binary messages; answer ping with pong |

Streams are deliberately finite by default, which makes manual tests repeatable and prevents test processes from
remaining open indefinitely.

## GraphQL API

HTTP operations use `POST /lab/v1/graphql`. Subscriptions use
`ws://127.0.0.1:9090/lab/v1/graphql/ws` with the GraphQL transport protocol.

API Studio named-query example:

```graphql
query NamedEcho($message: String!) {
  echo(message: $message) {
    message
    operation
  }
}
```

Variables:

```json
{
  "message": "graphql-through-knet"
}
```

The schema also provides `reverse` as a mutation and `ticker` as a finite subscription. Named operations are
intentional so KNet's semantic request naming and GraphQL breakpoint matching can be validated when multiple
operations use the same `/graphql` endpoint.

## Native gRPC API

The generated service is `knet.testing.v1.ProtocolLab` on `127.0.0.1:9091`:

| RPC | Cardinality | Purpose |
|---|---|---|
| `UnaryEcho` | Unary | One request and one response |
| `ServerStream` | Server streaming | One request and bounded ordered responses |
| `ClientStream` | Client streaming | Multiple requests and one summary |
| `BidirectionalEcho` | Bidirectional streaming | Immediate ordered response for each request |
| `Fail` | Unary failure | `INVALID_ARGUMENT` status with `knet-test-trailer` metadata |

Server reflection and the standard gRPC health service are enabled. The canonical contract is
`testingServer/src/main/proto/protocol_lab.proto`.

This is native gRPC over HTTP/2, not JSON pretending to be gRPC. KNet will only display its semantic messages
after native gRPC capture and decoding are implemented; until then, the endpoint remains a real target for that
work. gRPC-Web is separately marked `PLANNED` because it requires a standards-compatible adapter.

## HTTP/2, HTTP/3, and WebTransport

The HTTP listener accepts clear-text HTTP/2 negotiation (H2C) as well as HTTP/1.1. The integration suite uses an
H2C-only client and asserts the negotiated protocol is `HTTP/2.0`; an HTTP/1.1 response cannot satisfy that test.

The independent listener on port `9443` speaks HTTP/2 over TLS only and requires ALPN `h2`. It generates a local
self-signed certificate for each server process. Automated tests trust that certificate only inside their test
client. For a deliberate manual strict-TLS experiment, the public certificate is available from the ordinary
HTTP listener at `http://127.0.0.1:9090/lab/v1/http2/certificate.pem`; the private key is never exposed.

| Method | TLS HTTP/2 route | Wire behavior |
|---|---|---|
| Any | `/lab/v1/http2/echo` | Echo the negotiated protocol, method, path, and bounded body |
| GET | `/lab/v1/http2/trailers` | Body followed by a trailing `x-knet-trailer` HEADERS frame |
| GET | `/lab/v1/http2/slow-stream?label=a&chunks=3&delayMillis=25` | Bounded delayed DATA frames for multiplexing and backpressure tests |
| GET | `/lab/v1/http2/reset-stream` | Reset only the current stream with `CANCEL` |
| GET | `/lab/v1/http2/goaway` | Complete the active response, emit GOAWAY, and close the drained connection |
| GET | `/lab/v1/http2/large-headers?bytes=4096` | Bounded large response header block for HPACK and limit tests |

Every ordinary response also includes `x-knet-connection-id`, allowing tests to prove that concurrent streams
shared one parent connection rather than merely completing in parallel on separate sockets.

HTTP/3 and WebTransport require a QUIC/UDP listener and are not emulated by ordinary WebFlux routes. They appear
as `PLANNED` in `/lab/v1` until a real listener, certificate profile, and wire-level tests are added.

## Automated contract

Run:

```shell
./gradlew :testingServer:test
```

The integration suite starts real ephemeral HTTP, TLS/ALPN HTTP/2, and gRPC listeners. It verifies discovery,
HTTP metadata, H2C and TLS `h2` negotiation, multiplexed streams, trailing headers, large header blocks, reset and
GOAWAY handling, NDJSON, SSE, named GraphQL HTTP operations, GraphQL WebSocket subscriptions, raw WebSocket, all
native gRPC cardinalities, typed gRPC failures, and trailers. It does not launch the KNet desktop app.

## Adding another protocol

For an additive extension:

1. Add one cohesive package under `testingserver/<protocol>` and a schema/resource directory if required.
2. Use the protocol's real server implementation rather than returning look-alike data over HTTP.
3. Add an `AVAILABLE` catalog entry only after its listener binds and a wire-level integration test passes.
4. Add the endpoint and KNet test expectation to this document.

This keeps the lab modular while preventing its discovery document from drifting away from executable behavior.
