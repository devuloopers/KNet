# `:application`

## Responsibility

Owns JVM desktop application use cases and UI-neutral ports that coordinate KNet capabilities. It is the stable boundary consumed by the desktop product, desktop UI, and desktop-facing automation or remote-control surfaces.

## Owns

- Proxy runtime and traffic query ports.
- Safe loopback proxy start/stop/state use cases and technology-neutral runtime policy values.
- Bounded body-access/write, finalized-object maintenance, pre-allocation capture ingress, explicit streaming-body completion, canonical traffic query, direct HTTP recording, and traffic-detail ports/use cases.
- Traffic-clear orchestration that rotates capture ownership before terminal metadata/body deletion.
- Application-owned breakpoint coordination with bounded rules, pauses, bytes, decisions, and deadlines.
- Asynchronous semantic-inspection scheduling, generic annotation persistence/query, and capability truth.
- Connectivity provider/mechanism coordination, canonical certificate-management summaries/rules,
  pairing, stock-phone Wi-Fi sharing control, and sandboxed script-execution ports.
- Cross-capability orchestration contracts.
- Application-level commands, results, and lifecycle policies.

## Does not own

- Netty, Compose, database, filesystem, device, or protocol implementation details.
- Canonical traffic data; that belongs to `:core:traffic`.
- Authored breakpoint rules and outbound API request/result values; those belong to `:core:domain`.
- Script language, phase, snippet, and assertion values; those belong to `:core:scripting`.
- Android/iOS companion workflows; those get their own application layer and share only the required `:core:*` contracts.

## Dependency rule

May depend on stable `:core:*` contracts. Implementations depend inward on this module; this module must not depend on `:engine:*`, `:data:*`, `:connectivity:*`, `:ui:*`, or `:products:*`.

## Current state

This is intentionally a Kotlin/JVM module, not a KMP sharing boundary. Traffic paging/detail, desktop proxy control, direct API Studio recording, clear/session rotation, breakpoints, certificates, connectivity, pairing, semantic inspection, and script execution all cross it. Its ports reuse canonical core/domain values instead of declaring application-local copies. Every new traffic record uses the canonical writer and the UI does not call concrete runtimes or storage.
