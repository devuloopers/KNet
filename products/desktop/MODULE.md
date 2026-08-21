# `:products:desktop`

## Responsibility

Composes and launches the desktop product. This is the desktop composition root and lifecycle owner.

## Owns

- Desktop `main`, configuration loading, Koin composition, process-owned startup policies, and reverse-order
  shutdown of connectivity, gateway, setup portal, inspector, proxy/capture, and Room resources.
- All Koin binding declarations, organized under `di/<feature>/` so each feature's adapters, use cases, and ViewModels have one visible assembly location.
- Wiring concrete adapters to application ports.
- Routing typed connectivity diagnostics into the product logging backend without making reusable connectivity
  adapters depend on that backend.
- The product-owned dedicated setup-portal index as packaged HTML, injected into the connectivity adapter through
  its renderer contract rather than embedded in DI code.
- Desktop shell implementations, including settings data-directory actions, behind feature-owned contracts.
- Process-owned application-settings synchronization that applies persisted API Studio and live-interception
  timeouts to runtime collaborators without coupling persistence or Settings UI to those implementations.

## Does not own

- Business rules, canonical traffic models, engine behavior, storage logic, connectivity mechanisms, reusable UI, or reusable DI modules.

## Dependency rule

May depend on all desktop implementations needed for composition. No reusable module may depend on `:products:desktop`.

## Current state

The composition root owns normal-window and JVM-hook shutdown paths and is the only module that declares Koin bindings or assembles UI with concrete desktop runtime adapters. Bindings are grouped into `platform`, `apistudio`, `breakpoint`, `certificate`, `connectivity`, `httppanel`, `inspection`, `proxy`, `request`, `settings`, `traffic`, and `workspace` packages. Protocol breakpoint extensions are contributed as multi-bindings and collected by the application registry; GraphQL parsing is shared by its live breakpoint, captured-rule suggestion, and asynchronous inspection bindings. Proxy composition binds runtime, capture control, and breakpoint capture availability to process-scoped owners; Traffic composition injects only application use cases for capture state/control, pending-breakpoint projection, and smart breakpoint-draft preparation.
Breakpoint composition owns one coordinator shared by gate/control/capture-availability bindings, one
process-scoped Room synchronization repository with explicit shutdown, and explicit ViewModel dispatchers.
The auto-clear-on-startup preference is executed once from a process-owned coroutine after DI is ready; it is
not tied to Traffic ViewModel creation. That coroutine is cancelled before proxy/storage shutdown.
The same product lifecycle starts one application-settings runtime synchronizer. DataStore repositories remain
pure, while timeout changes reach the shared API client and breakpoint coordinator once per distinct value.
API Studio assembly is isolated in `di/apistudio`: it binds the canonical authored-request execution workflow,
Room collection adapter, script port, explicit dispatchers, and the two narrowly scoped feature ViewModels.
That assembly does not bind a traffic recorder. `di/proxy` owns the only production canonical capture source and
the shared capture-state observer used by Traffic and API Studio. API Studio receives the local proxy route only
while canonical capture is active, so a direct or capture-paused call cannot create a Room session or Traffic row.
The same assembly injects a defensive DER copy of the process-owned KNet Root CA into the API client as
local-proxy-only trust material. It does not weaken direct or proxy-to-origin TLS verification.
Cross-feature request presentation lives in `di/request`, where descriptor strategies are Koin multi-bindings
and deterministic strategy priority is enforced by the domain resolver. API Studio, Traffic, and Breakpoint
ViewModels consume only that composed use case.
No ViewModel constructor supplies a production fallback implementation.
