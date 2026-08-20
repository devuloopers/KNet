# `:products:desktop`

## Responsibility

Composes and launches the desktop product. This is the desktop composition root and lifecycle owner.

## Owns

- Desktop `main`, configuration loading, Koin composition, process-owned startup policies, and reverse-order
  shutdown of connectivity, gateway, setup portal, inspector, proxy/capture, and Room resources.
- All Koin binding declarations, organized under `di/<feature>/` so each feature's adapters, use cases, and ViewModels have one visible assembly location.
- Wiring concrete adapters to application ports.
- Desktop shell implementations, including settings data-directory actions, behind feature-owned contracts.

## Does not own

- Business rules, canonical traffic models, engine behavior, storage logic, connectivity mechanisms, reusable UI, or reusable DI modules.

## Dependency rule

May depend on all desktop implementations needed for composition. No reusable module may depend on `:products:desktop`.

## Current state

The composition root owns normal-window and JVM-hook shutdown paths and is the only module that declares Koin bindings or assembles UI with concrete desktop runtime adapters. Bindings are grouped into `platform`, `apistudio`, `breakpoint`, `certificate`, `connectivity`, `httppanel`, `inspection`, `proxy`, `settings`, `traffic`, and `workspace` packages. Protocol breakpoint extensions are contributed as multi-bindings and collected by the application registry; GraphQL parsing is shared by its live breakpoint and asynchronous inspection bindings. Proxy composition binds runtime, capture control, and breakpoint capture availability to process-scoped owners; Traffic composition injects only application use cases for capture state/control and pending-breakpoint projection.
The auto-clear-on-startup preference is executed once from a process-owned coroutine after DI is ready; it is
not tied to Traffic ViewModel creation. That coroutine is cancelled before proxy/storage shutdown.
API Studio assembly is isolated in `di/apistudio`: it binds the canonical authored-request execution workflow,
Room collection adapter, script port, ordered GraphQL/HTTP request-descriptor strategies, explicit dispatchers,
and the two narrowly scoped feature ViewModels. Protocol descriptor precedence is therefore a product concern;
feature ViewModels consume only the composed use case.
No ViewModel constructor supplies a production fallback implementation.
