# `:application:desktop`

## Responsibility

Owns JVM desktop application use cases, coordinators, and UI-neutral contracts that coordinate KNet capabilities. It is the stable boundary consumed by the desktop product, desktop UI, and desktop-facing automation or remote-control surfaces.

## Owns

- Proxy runtime and traffic query contracts.
- Safe loopback proxy start/stop/state use cases, independent capture pause/resume/state use cases,
  and technology-neutral runtime policy values.
- Bounded body-access/write, finalized-object maintenance, pre-allocation capture ingress, explicit
  streaming-body completion, cross-session canonical traffic query, and traffic-detail contracts/use cases.
- Paged traffic results pair the shared canonical exchange snapshot with a durable capture sequence
  and an exact filtered total; presentation modules never derive row identity from a loaded window.
- Traffic-clear orchestration that rotates capture ownership before terminal metadata/body deletion without disconnecting proxy clients.
- A capture-availability boundary that bypasses and releases breakpoints while no Traffic row can
  be created, without changing engine aggregation requirements or closing connections.
- Application-owned breakpoint coordination with deterministic rule priority, bounded rules, pauses,
  retained transport/edit bytes, headers, phase-specific decisions, deadlines, and an additive
  protocol-extension registry.
- Explicit breakpoint body ownership: `Unchanged` forwards retained wire bytes while `Replace`
  intentionally substitutes even an empty body. Nullable body edits are not used as intent.
- A current immutable transport prefilter used by optional engine adapters without reconnecting clients
  or exposing protocol-specific matching to the proxy.
- Global interception disablement immediately continues existing pauses unchanged and prevents candidates
  that raced with the toggle from entering the pending queue.
- Protocol-neutral framed-message breakpoint candidates, decisions, and gates. A protocol engine may pause,
  replace, or drop one message stream without exposing its wire types to the coordinator or drawer shell.
- UI-neutral protocol criteria field schemas, extension-owned criteria compilation, fail-closed
  matching, and bounded compact request observations correlated to response phases by `ExchangeId`.
- Protocol-aware breakpoint draft preparation from one canonical exchange: semantic extensions receive
  a bounded request/body input, suggestions are validated in deterministic priority order, volatile query
  parameters are excluded from the endpoint pattern, and unrecognized traffic falls back to HTTP criteria.
- Asynchronous semantic-inspection scheduling, generic annotation persistence/query, bounded multi-exchange
  annotation observation for list presentation, and capability truth.
- Protocol-neutral API Studio authoring, explicit reflection, batch execution, and interactive streaming-session
  contracts. It also owns the opaque, versioned workspace-document boundary used to persist incomplete drafts
  without teaching the application layer a protocol's editor fields. Concrete codecs and clients remain outer-owned.
- Connectivity provider/mechanism coordination, canonical certificate-management summaries/rules,
  pairing, durable companion-device coordination, read-only stock-phone Wi-Fi lifecycle observation, and
  sandboxed script-execution contracts.
- Cross-capability orchestration contracts.
- Application-level commands, results, and lifecycle policies.
- Typed certificate-authority lifecycle and Root trust-installation results, including explicit manual-action instructions; UI never parses engine strings or JCA types.
- One captured-request conversion use case that produces the shared API Studio/replay
  `NetworkRequestSpec` without duplicating URL, ordered-header, repeated-query, or body-decoding behavior in UI.
- API Studio request execution orchestration from the canonical `SavedApiRequest`, including scripts,
  outbound execution, and response formatting. It deliberately cannot manufacture captured Traffic.
- Generic live-HTTP response interpreter/session contracts and a deterministic registry. Protocol engines can
  recognize a response head and emit bounded semantic records without application or UI branches for SSE.

## Does not own

- Netty, Compose, database, filesystem, device, or protocol implementation details.
- Canonical traffic data; that belongs to `:core:traffic`.
- Authored breakpoint rules and outbound API request/result values; those belong to `:core:domain`.
- Script language, phase, snippet, and assertion values; those belong to `:core:scripting`.
- Android/iOS companion workflows; those belong to sibling `:application:companion` and share only the required
  `:core:*` contracts.

## Dependency rule

May depend on stable `:core:*` contracts. Implementations depend inward on this module; this module must not depend on `:engine:*`, `:data:*`, `:connectivity:*`, `:ui:*`, or `:products:*`.

## Current state

This is intentionally a Kotlin/JVM module, not a KMP sharing boundary. Traffic paging/detail, desktop proxy
control, clear/session rotation, breakpoints, certificates, connectivity,
pairing, companion-device registration, semantic inspection, and script execution all cross it. Wi-Fi
sharing exposes a read-only application state because the desktop adapter follows proxy lifecycle directly.
Its contracts reuse canonical core/domain values instead of declaring application-local copies. Every new traffic
record uses the canonical writer and the UI does not call concrete runtimes or storage.
Protocol extensions implement the application breakpoint SPI from outer engine modules. The coordinator
never imports GraphQL, gRPC, WebSocket, SSE, or custom-format implementations and never retains their raw
request bodies between phases. Compact request observations are released by terminal response inspection,
streaming response completion, explicit drop, disconnect, or capture detachment.
The same registry may ask those extensions for a rule-editor suggestion when a user creates a breakpoint
from captured traffic. The application loads at most one bounded preview and returns only a canonical rule
plus generic field values, so neither Traffic nor the breakpoint editor imports protocol implementations.
Proxy runtime and capture state are intentionally separate: a running listener can be `Capturing` or
`Paused`, and only full product/configuration lifecycle commands stop the listener.
Traffic page queries use optional session scope, typed method/status/scheme/application-protocol criteria,
an opaque keyset cursor, storage-owned capture ordering, and an exact first-page match count carried through
the cursor snapshot. A null session deliberately means all retained history rather than "latest."
API Studio execution accepts one complete domain document; it has no dependency on Compose editor state and
can therefore be reused by another desktop surface, automation entry point, or remote-control adapter.
The streaming execution path preserves the same pre-request script, route, request body, terminal formatting,
and response-test pipeline. It forwards owned head/chunk events before completion; protocol interpreters remain
outer contributions and the terminal-only execution contract stays available.
It also has no traffic-recording contract: direct execution returns its response only to the caller, while a
proxy-routed execution can appear in Traffic exclusively through the active proxy capture ingress.
Strict authoring/execution adapters are selected by `RequestKindId`. Persisted workspace documents separately carry
an open `ApiStudioEditorId`, semantic request kind, placement, generated/user-defined name ownership, badge, payload
version, and defensively copied opaque bytes. Adding gRPC, WebSocket, or another future editor adds its codec,
contribution, runtime adapter, and product binding without adding a protocol branch to these use cases.

## Source layout

- `contract/` contains application-owned interfaces, immutable boundary values, and extension schemas.
- `coordinator/` contains stateful application orchestration such as breakpoint, connectivity, inspection,
  and pairing coordination.
- `usecase/` contains focused commands and queries invoked by presentation or other authorized callers.

The `contract` name is intentional: it describes what outer adapters must implement without using the
hexagonal-architecture term `port` or implying a network socket port.
