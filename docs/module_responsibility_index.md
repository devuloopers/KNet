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
- [`:ui`](../ui/MODULE.md) — presentation modules.
- [`:ui:desktop`](../ui/desktop/MODULE.md) — desktop presentation features.

## Stable contracts and application boundary

- [`:application`](../application/MODULE.md) — JVM desktop use cases and UI-neutral runtime ports.
- [`:core:traffic`](../core/traffic/MODULE.md) — canonical shared HTTP request, response, exchange, body-reference, and ingress models.
- [`:core:connectivity`](../core/connectivity/MODULE.md) — connectivity mechanism and setup contracts.
- [`:core:domain`](../core/domain/MODULE.md) — non-traffic feature domain contracts and use cases.
- [`:core:http`](../core/http/MODULE.md) — outbound HTTP-client capability.
- [`:core:logger`](../core/logger/MODULE.md) — shared logging facade.
- [`:core:pairing`](../core/pairing/MODULE.md) — pairing, device identity, credential, scope, and revocation contracts.
- [`:core:serialization`](../core/serialization/MODULE.md) — shared serialization configuration.

## Runtime implementations and adapters

- [`:connectivity:desktop`](../connectivity/desktop/MODULE.md) — desktop connectivity mechanism implementations.
- [`:data:desktop`](../data/desktop/MODULE.md) — desktop repository and mapping adapters.
- [`:storage`](../storage/MODULE.md) — durable desktop persistence and schema.
- [`:engine:certificate`](../engine/certificate/MODULE.md) — CA, certificate, key, and trust implementation.
- [`:engine:formatter`](../engine/formatter/MODULE.md) — bounded body formatting and derived views.
- [`:engine:interceptor`](../engine/interceptor/MODULE.md) — breakpoint interception and typed mutation behavior.
- [`:engine:protocol`](../engine/protocol/MODULE.md) — protocol-specific parsers and inspectors.
- [`:engine:proxy`](../engine/proxy/MODULE.md) — proxy transport and channel lifecycle.
- [`:engine:script`](../engine/script/MODULE.md) — sandboxed script runtimes.
- [`:engine:session`](../engine/session/MODULE.md) — canonical body-object file storage.
- [`:engine:simulator`](../engine/simulator/MODULE.md) — network condition simulation.

## Desktop presentation

- [`:ui:core`](../ui/core/MODULE.md) — reusable Compose design system.
- [`:ui:desktop:app`](../ui/desktop/app/MODULE.md) — desktop shell and navigation.
- [`:ui:desktop:workspace`](../ui/desktop/workspace/MODULE.md) — workspace presentation.
- [`:ui:desktop:apistudio`](../ui/desktop/apistudio/MODULE.md) — API Studio editing and execution presentation.
- [`:ui:desktop:traffic`](../ui/desktop/traffic/MODULE.md) — traffic list and inspector coordination.
- [`:ui:desktop:httpPanel`](../ui/desktop/httpPanel/MODULE.md) — reusable HTTP inspection/editing panels.
- [`:ui:desktop:scripting`](../ui/desktop/scripting/MODULE.md) — scripting UI.
- [`:ui:desktop:certificate`](../ui/desktop/certificate/MODULE.md) — certificate-management UI.
- [`:ui:desktop:settings`](../ui/desktop/settings/MODULE.md) — settings UI.
- [`:ui:desktop:breakpointManager`](../ui/desktop/breakpointManager/MODULE.md) — breakpoint-management UI.
- [`:ui:desktop:codeEditor`](../ui/desktop/codeEditor/MODULE.md) — reusable code editor.

## Composition and test support

- [`:products:desktop`](../products/desktop/MODULE.md) — sole desktop Koin binding/composition root and process lifecycle owner; bindings are grouped by feature under `di/`.
- [`:testingServer`](../testingServer/MODULE.md) — deterministic integration-test server.

## Shared HTTP model rule

`HttpRequestSnapshot`, `HttpResponseSnapshot`, and `HttpExchangeSnapshot` in `:core:traffic` are the immutable feature-facing records. Traffic UI, API Studio execution results, breakpoints, storage adapters, exports, scripts, and remote APIs share them. Feature-specific mutable concerns remain separate:

- API Studio uses a request draft and converts it to a snapshot for execution.
- Breakpoints express edits as typed patches over a snapshot.
- Traffic UI stores selection/filter/presentation state, not a duplicate exchange.
- Body bytes are obtained through a body store using `BodyRef`, not embedded in every model copy.

Production consumers include paged Traffic list/detail, API Studio direct recording and replay preparation, breakpoints, semantic inspectors, storage, and paired ingress. `:data:desktop` maps schema-v13 Room records to canonical exchanges, returns bounded previews through `:application`, and adapts concrete runtimes to application ports. The canonical writer/body/query stack is the sole traffic authority. Ordinary proxy responses stream; only an enabled response breakpoint requests bounded full-response aggregation.

## Current target ownership

The proxy defaults to loopback and strict upstream TLS, enforces timeout/connection policy, orders HTTP/1 exchanges, streams both directions, and owns deterministic startup/shutdown. Application services own breakpoints, connectivity, pairing, certificates, inspection, script execution, traffic paging, clear, and recording. The dedicated setup listeners, authenticated companion-ready gateway, and explicitly activated approved-client Wi-Fi gateway live in `:connectivity:desktop`; none is inserted into the proxy pipeline. HTTP/2, HTTP/3, WebSocket transport, gRPC, VPN, relay, and mobile applications remain explicitly unavailable rather than represented by dormant stubs.
