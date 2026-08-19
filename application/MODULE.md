# `:application`

## Responsibility

Owns JVM desktop application use cases and UI-neutral ports that coordinate KNet capabilities. It is the stable boundary consumed by the desktop product, desktop UI, and desktop-facing automation or remote-control surfaces.

## Owns

- Proxy runtime and traffic query ports.
- Safe loopback proxy start/stop/state use cases, independent capture pause/resume/state use cases,
  and technology-neutral runtime policy values.
- Bounded body-access/write, finalized-object maintenance, pre-allocation capture ingress, explicit
  streaming-body completion, cross-session canonical traffic query, direct HTTP recording, and traffic-detail
  ports/use cases.
- Traffic-clear orchestration that rotates capture ownership before terminal metadata/body deletion without disconnecting proxy clients.
- A capture-availability boundary that bypasses and releases breakpoints while no Traffic row can
  be created, without changing engine aggregation requirements or closing connections.
- Application-owned breakpoint coordination with bounded rules, pauses, bytes, decisions, deadlines,
  and an additive protocol-extension registry.
- UI-neutral protocol criteria field schemas, extension-owned criteria compilation, fail-closed
  matching, and bounded compact request observations correlated to response phases by `ExchangeId`.
- Asynchronous semantic-inspection scheduling, generic annotation persistence/query, and capability truth.
- Connectivity provider/mechanism coordination, canonical certificate-management summaries/rules,
  pairing, durable companion-device coordination, read-only stock-phone Wi-Fi lifecycle observation, and
  sandboxed script-execution ports.
- Cross-capability orchestration contracts.
- Application-level commands, results, and lifecycle policies.
- One captured-request conversion use case that produces the shared API Studio/replay
  `NetworkRequestSpec` without duplicating URL, ordered-header, repeated-query, or body-decoding behavior in UI.

## Does not own

- Netty, Compose, database, filesystem, device, or protocol implementation details.
- Canonical traffic data; that belongs to `:core:traffic`.
- Authored breakpoint rules and outbound API request/result values; those belong to `:core:domain`.
- Script language, phase, snippet, and assertion values; those belong to `:core:scripting`.
- Android/iOS companion workflows; those get their own application layer and share only the required `:core:*` contracts.

## Dependency rule

May depend on stable `:core:*` contracts. Implementations depend inward on this module; this module must not depend on `:engine:*`, `:data:*`, `:connectivity:*`, `:ui:*`, or `:products:*`.

## Current state

This is intentionally a Kotlin/JVM module, not a KMP sharing boundary. Traffic paging/detail, desktop proxy
control, direct API Studio recording, clear/session rotation, breakpoints, certificates, connectivity,
pairing, companion-device registration, semantic inspection, and script execution all cross it. Wi-Fi
sharing exposes a read-only application state because the desktop adapter follows proxy lifecycle directly.
Its ports reuse canonical core/domain values instead of declaring application-local copies. Every new traffic
record uses the canonical writer and the UI does not call concrete runtimes or storage.
Protocol extensions implement the application breakpoint SPI from outer engine modules. The coordinator
never imports GraphQL, gRPC, WebSocket, SSE, or custom-format implementations and never retains their raw
request bodies between phases.
Proxy runtime and capture state are intentionally separate: a running listener can be `Capturing` or
`Paused`, and only full product/configuration lifecycle commands stop the listener.
Traffic page queries use optional session scope, typed method/status/scheme/application-protocol criteria,
and an opaque keyset cursor. A null session deliberately means all retained history rather than "latest."
