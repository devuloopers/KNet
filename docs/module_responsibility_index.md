# KNet Module Responsibility Index

This index links the ownership contract stored at every Gradle module root. A module's `MODULE.md` is the local source of truth for what that module owns, what it must not own, its dependency direction, and its current evolution direction. The broader target and sequencing remain defined in [target_architecture_and_implementation_plan.md](target_architecture_and_implementation_plan.md) and [implementation_plan.md](implementation_plan.md).

Gradle enforces four rules through `verifyArchitectureFoundation`:

1. Every included module must have a root `MODULE.md`.
2. Stable architecture modules may only use their approved direct project dependencies.
3. UI production code cannot import concrete KNet runtimes/storage; the pure formatter library is the only explicit engine-package exception.
4. Koin binding declarations may exist only in executable product composition roots; reusable application, core, connectivity, data, engine, storage, and UI modules do not assemble the product.

## Grouping projects

- [`:products`](../products/MODULE.md) — executable product composition roots.
- [`:connectivity`](../connectivity/MODULE.md) — platform connectivity implementations.
- [`:core`](../core/MODULE.md) — stable contracts and low-level utilities.
- [`:data`](../data/MODULE.md) — platform data adapters.
- [`:engine`](../engine/MODULE.md) — independently testable runtime capabilities.
- [`application`](../application/MODULE.md) — application-layer namespace for desktop and companion workflows.
- [`:ui`](../ui/MODULE.md) — presentation modules.
- [`:ui:desktop`](../ui/desktop/MODULE.md) — desktop presentation features.

## Stable contracts and application boundary

- [`:application:desktop`](../application/desktop/MODULE.md) — JVM desktop use cases and UI-neutral proxy/capture runtime contracts.
- [`:application:companion`](../application/companion/MODULE.md) — portable companion workflows and platform adapter contracts.
- [`:core:companion`](../core/companion/MODULE.md) — portable companion registration, connection, certificate,
  inspection, network, and failure models.
- [`:core:traffic`](../core/traffic/MODULE.md) — canonical shared HTTP request, response, exchange, body-reference, and ingress models.
- [`:core:connectivity`](../core/connectivity/MODULE.md) — connectivity mechanism and setup contracts.
- [`:core:domain`](../core/domain/MODULE.md) — non-traffic feature domain contracts and use cases.
- [`:core:http`](../core/http/MODULE.md) — outbound HTTP-client capability.
- [`:core:identity`](../core/identity/MODULE.md) — stable registered-device identity shared across transports.
- [`:core:logger`](../core/logger/MODULE.md) — shared logging facade.
- [`:core:pairing`](../core/pairing/MODULE.md) — pairing, credential, scope, and trusted-device contracts.
- [`:core:serialization`](../core/serialization/MODULE.md) — shared serialization configuration.

## Runtime implementations and adapters

- [`:connectivity:desktop`](../connectivity/desktop/MODULE.md) — desktop connectivity mechanism implementations.
- [`:connectivity:companion`](../connectivity/companion/MODULE.md) — shared bounded Ktor bootstrap/control
  transports, native Android/Darwin TLS enforcement, qualified Android network/certificate/VPN lifecycle
  boundaries, and explicit fail-closed iOS device-capability placeholders.
- [`:data:companion`](../data/companion/MODULE.md) — versioned companion registration/credential adapters,
  invitation codec, shared control client, and Android Keystore implementations.
- [`:data:desktop`](../data/desktop/MODULE.md) — desktop repository, capture-generation, registered-device, and mapping adapters.
- [`:storage`](../storage/MODULE.md) — durable desktop persistence, schema, and registered/trusted-device rows.
- [`:engine:certificate`](../engine/certificate/MODULE.md) — CA, certificate, key, and trust implementation.
- [`:engine:formatter`](../engine/formatter/MODULE.md) — bounded body formatting and derived views.
- [`:engine:grpc`](../engine/grpc/MODULE.md) — native gRPC framing, descriptors, reflection, inspection,
  breakpoints, and API Studio execution.
- [`:engine:graphqlWebSocket`](../engine/graphqlWebSocket/MODULE.md) — modern `graphql-transport-ws` envelope
  semantics, bounded operation correlation, presentation, breakpoints, identity, and API Studio execution.
- [`:engine:sse`](../engine/sse/MODULE.md) — incremental Server-Sent Events parsing, live capture, Traffic
  decoding, HTTP response interpretation, and response-record breakpoints.
- [`:engine:websocket`](../engine/websocket/MODULE.md) — RFC 6455 handshake recognition, bounded frame/message
  inspection, message breakpoints, payload presentation, and API Studio execution.
- [`:engine:interceptor`](../engine/interceptor/MODULE.md) — breakpoint interception, pre-pause capture admission, and typed mutation behavior.
- [`:engine:protocol`](../engine/protocol/MODULE.md) — protocol-specific parsers, asynchronous inspectors,
  and additive live-breakpoint extensions.
- [`:engine:proxy`](../engine/proxy/MODULE.md) — proxy transport, channel lifecycle, and one-shot exchange capture handoff.
- [`:engine:script`](../engine/script/MODULE.md) — sandboxed script runtimes.
- [`:engine:session`](../engine/session/MODULE.md) — canonical body-object file storage.
- [`:engine:simulator`](../engine/simulator/MODULE.md) — network condition simulation.

## Shared presentation foundation

- [`:ui:core`](../ui/core/MODULE.md) — Compose Multiplatform design system, resources, and adaptive components for
  JVM desktop, Android, and iOS.

## Desktop presentation
- [`:ui:desktop:app`](../ui/desktop/app/MODULE.md) — desktop shell, navigation, and row-gated global overlay placement.
- [`:ui:desktop:workspace`](../ui/desktop/workspace/MODULE.md) — workspace presentation.
- [`:ui:desktop:apiStudio`](../ui/desktop/apiStudio/MODULE.md) — API Studio editing and execution presentation.
- [`:ui:desktop:apiStudio:grpc`](../ui/desktop/apiStudio/grpc/MODULE.md) — contributed native gRPC authoring,
  streaming execution, and versioned draft codec.
- [`:ui:desktop:apiStudio:graphqlWebSocket`](../ui/desktop/apiStudio/graphqlWebSocket/MODULE.md) — contributed
  GraphQL subscription authoring, execution presentation, and versioned workspace draft codec.
- [`:ui:desktop:apiStudio:websocket`](../ui/desktop/apiStudio/websocket/MODULE.md) — contributed WebSocket
  authoring, interactive duplex execution, and versioned workspace draft codec.
- [`:ui:desktop:traffic`](../ui/desktop/traffic/MODULE.md) — traffic list, capture controls, live breakpoint projection, and inspector coordination.
- [`:ui:desktop:connectivity`](../ui/desktop/connectivity/MODULE.md) — connectivity card grid plus Wi-Fi and
  ephemeral companion-onboarding drawer presentation.
- [`:ui:desktop:httpPanel`](../ui/desktop/httpPanel/MODULE.md) — reusable HTTP inspection/editing panels.
- [`:ui:desktop:scripting`](../ui/desktop/scripting/MODULE.md) — scripting UI.
- [`:ui:desktop:certificate`](../ui/desktop/certificate/MODULE.md) — certificate-management UI.
- [`:ui:desktop:settings`](../ui/desktop/settings/MODULE.md) — settings UI.
- [`:ui:desktop:breakpointManager`](../ui/desktop/breakpointManager/MODULE.md) — breakpoint management and Live Intercept drawer content.
- [`:ui:desktop:codeEditor`](../ui/desktop/codeEditor/MODULE.md) — reusable versioned document,
  extensible language services, search/history, and virtualized Compose code editor; independent
  from HTTP and feature models.

## Companion presentation

- [`:ui:companion:presentation`](../ui/companion/presentation/MODULE.md) — framework-neutral companion state,
  actions, native effects, and lifecycle-owned shared ViewModel for Android and iOS.
- [`:ui:companion:sharedUi`](../ui/companion/sharedUi/MODULE.md) — Compose Multiplatform companion screens,
  responsive layout, and shared UI resources hosted by platform products using the `:ui:core` theme.

## Composition and test support

- [`:products:desktop`](../products/desktop/MODULE.md) — sole desktop Koin binding/composition root and process lifecycle owner; bindings are grouped by feature under `di/`.
- [`:products:companion:androidApp`](../products/companion/androidApp/MODULE.md) — installable Android companion APK,
  manifest, thin Compose host lifecycle, and Android companion composition root; unavailable transport/VPN
  capabilities are not simulated.
- [`:products:companion:iosApp`](../products/companion/iosApp/MODULE.md) — installable SwiftUI iOS shell,
  Kotlin/Native composition framework, AVFoundation scanner, and Apple Network Extension entry point.
- [`:products:companion:iosPacketTunnel`](../products/companion/iosPacketTunnel/MODULE.md) — lean Kotlin/Native
  packet-tunnel runtime owning validated start options, pinned desktop TLS, local SOCKS5 forwarding,
  NetworkExtension settings, and the hev engine lifecycle.
- [`:testingServer`](../testingServer/MODULE.md) — deterministic integration-test server.

## Shared HTTP model rule

`HttpRequestSnapshot`, `HttpResponseSnapshot`, and `HttpExchangeSnapshot` in `:core:traffic` are the immutable feature-facing records. Traffic UI, API Studio execution results, breakpoints, storage adapters, exports, scripts, and remote APIs share them. Feature-specific mutable concerns remain separate:

- API Studio uses a request draft and converts it to a snapshot for execution.
- Breakpoints express edits as typed patches over a snapshot.
- Traffic UI stores selection/filter/presentation state, not a duplicate exchange.
- Body bytes are obtained through a body store using `BodyRef`, not embedded in every model copy.

Production consumers include paged Traffic list/detail, API Studio direct recording and replay preparation, breakpoints, semantic inspectors, storage, and paired ingress. `:data:desktop` maps current-schema Room records to canonical exchanges and registered identities, returns bounded previews through `:application:desktop`, and adapts concrete runtimes to application contracts. Breakpoint persistence stores a generic protocol ID/payload envelope and never branches on GraphQL or future formats. The canonical writer/body/query stack is the sole traffic authority. Ordinary proxy traffic streams; Traffic Start/Stop attaches or detaches a versioned capture target while the listener and Wi-Fi forwarding remain stable. Detached writers drain through one bounded owner. Enabled request or response breakpoints request bounded aggregation, but capture pause bypasses and releases breakpoint decisions without changing that pipeline shape.

## Current target ownership

The proxy defaults to loopback and strict upstream TLS, enforces timeout/connection policy, orders HTTP/1
exchanges, multiplexes isolated experimental HTTP/2 streams, streams both directions, and owns deterministic
startup/shutdown. Application services independently own proxy lifecycle, capture attachment, breakpoints,
connectivity, pairing, certificates, inspection, script execution, traffic paging, clear, and recording. The
dedicated setup listeners, authenticated companion-ready gateway, and automatically managed open-client Wi-Fi
gateway live in `:connectivity:desktop`; none is inserted into the proxy pipeline. HTTP/2, native gRPC, and
HTTP/1.1 WebSocket capture, modern `graphql-transport-ws` semantic inspection/breakpoints/API Studio, and native
gRPC remain `EXPERIMENTAL` pending external platform/device and release-soak qualification. Legacy `graphql-ws`,
HTTP/3, WebSocket over HTTP/2, VPN, relay, and mobile applications remain explicitly unavailable rather than
represented by dormant stubs.
