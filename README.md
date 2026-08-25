# KNet

KNet is a Kotlin desktop network-inspection tool under active development. Its current product captures and inspects HTTP/1 traffic and experimental HTTP/2 traffic through a Netty proxy, persists bounded canonical sessions, supports TLS interception, breakpoints, API Studio, and independently registered desktop connectivity mechanisms.

The architecture is intentionally split into stable contracts, application orchestration, replaceable desktop adapters, engines, feature UI, and one product composition root.

## Core architecture

```text
products:desktop
  -> ui:desktop:*                  presentation
  -> data:desktop                 desktop adapters
  -> connectivity:desktop         PAC/manual/profile/ADB mechanisms
  -> engine:* / storage           concrete infrastructure

ui:desktop:* -> application -> core:traffic / core:connectivity / core:pairing
data:desktop -> application + engines + storage
engine:proxy -> core:traffic
```

`HttpRequestSnapshot`, `HttpResponseSnapshot`, and `HttpExchangeSnapshot` in `:core:traffic` are the shared immutable HTTP models used by Traffic, API Studio recording/replay preparation, breakpoints, persistence, export, and inspectors. Large body bytes are never embedded in those snapshots; `BodyRef` metadata points to the bounded body-store boundary.

The proxy forwarding path is independent from Compose, Room, connectivity mechanisms, and protocol-specific inspection. Capture is a bounded side output. Semantic inspection runs asynchronously after capture.

## Module groups

| Group | Responsibility |
| --- | --- |
| `:core:traffic`, `:core:connectivity`, `:core:pairing` | Portable stable contracts and values |
| `:application` | JVM application ports, use cases, lifecycle and coordination |
| `:engine:proxy`, `:engine:certificate`, `:engine:interceptor`, `:engine:session` | Proxy transport, TLS, Netty breakpoint adaptation, and body files |
| `:storage`, `:data:desktop` | Room schema and desktop persistence/runtime adapters |
| `:connectivity:desktop` | Independent manual, PAC, Apple profile, ADB, network-state, and setup mechanisms |
| `:ui:core`, `:ui:desktop:*` | Reusable presentation and desktop feature UI |
| `:products:desktop` | Desktop launcher, all Koin bindings, startup, and shutdown |

Every Gradle module has a `MODULE.md` at its root. The complete index is [docs/module_responsibility_index.md](docs/module_responsibility_index.md).

## Persistence policy during development

The current canonical Room schema is version 22. KNet intentionally provides no upgrade path for earlier development schemas or old certificate formats. Room uses destructive migration, so an existing pre-v22 local database is reset when opened. New traffic is stored only in canonical session, connection, exchange, message, body-object, gap, annotation, and deletion-outbox records.

Starting or stopping the proxy does not clear traffic. Traffic is removed only through the explicit Clear action, which rotates an active capture session before deleting terminal sessions and their bodies.

## Supported versus planned

The implemented foundation includes HTTP/1 streaming, experimental HTTP/2 transport, bounded capture/storage, TLS interception, canonical traffic queries, breakpoints, GraphQL/SSE semantic inspection, PAC/manual/Apple/ADB desktop connectivity, pairing, and authenticated ingress foundations.

HTTP/2, native gRPC, HTTP/1.1 WebSocket, and modern `graphql-transport-ws` inspection/breakpoints/API Studio have
completed their local JVM increments but remain `EXPERIMENTAL` until their external platform/device and
release-soak gates pass. Mobile companion apps,
VPN capture, relay, HTTP/3, and WebSocket over HTTP/2 remain future additive capabilities. They are not presented
as supported until their implementation and conformance gates pass.

## Build and verification

Do not launch the desktop application during automated verification.

```bash
./gradlew phase18ReleaseGate
```

For faster focused work, run the affected module tests and desktop compilation first. The release gate enforces architecture checks, module tests, and desktop packaging.

## Architecture documents

- [Target architecture and implementation plan](docs/target_architecture_and_implementation_plan.md)
- [Implementation history and delivery status](docs/implementation_plan.md)
- [Architecture decisions](docs/adr/)
- [Proxy test strategy and baselines](docs/proxy_test_strategy_and_baselines.md)
