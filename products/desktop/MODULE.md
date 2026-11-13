# `:products:desktop`

## Policy and transaction shutdown

The shared policy/transform scope is explicitly owned by product composition and cancelled on container close.
Desktop bootstrap registers a bounded drain resource before storage and before proxy/ingress resources, so reverse
shutdown stops transports, cancels and waits for policy/transform cleanup, then closes SQLite. Startup policy work
uses the same drain contract. It executes on the existing shutdown worker/hook rather than the Compose event
thread. A cleanup timeout is reported and cannot be mistaken for successful scope completion.

## Responsibility

Composes and launches the desktop product. This is the desktop composition root and lifecycle owner.

## Owns

- Desktop `main`, configuration loading, Koin composition, process-owned startup policies, and reverse-order
  shutdown of connectivity, gateway, setup portal, inspector, proxy/capture, and Room resources.
- Composition and lifecycle ownership for the authenticated companion control gateway and its process-stable
  KNet-CA-signed TLS identity.
- Product-owned companion onboarding environment composition, including the active setup-portal authority,
  current LAN address, public root and transport identity, service endpoints, stable desktop metadata, and shared
  version-3 bootstrap/response codecs.
- All Koin binding declarations, organized under `di/<feature>/` so each feature's adapters, use cases, and ViewModels have one visible assembly location.
- Wiring concrete adapters to application contracts.
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
Product integration tests exercise the control gateway through real TLS with generated KNet root and leaf
material; reusable connectivity modules do not gain a certificate-engine dependency for test convenience.
The Connect Device composition creates a lightweight companion bootstrap from the active LAN session and resolves
the setup portal's actual fallback port instead of assuming 8181. The complete invitation remains behind the
one-time TLS redemption route. The same process-owned control gateway completes proof-bearing pairing and atomic
credential refresh. The secured VPN/data-plane adapter remains a separate, deliberately fail-closed capability.
Cross-feature request presentation lives in `di/request`, where descriptor strategies are Koin multi-bindings
and deterministic strategy priority is enforced by the domain resolver. API Studio, Traffic, and Breakpoint
ViewModels consume only that composed use case.
No ViewModel constructor supplies a production fallback implementation.

## Protected TLS handling

The composition root binds persisted configuration, the TLS rule adapter, one bounded automatic-rejection cache and
application use cases. The cache uses the product-owned coroutine scope; authority identity/validity and canonical
session lifecycle fence temporary decisions. Both manager and Traffic actions share the same state. The existing
protectedTrafficQualification gate covers explicit policy and automatic reconnects, compatible storage, actual shaping,
Traffic/UI and composition. Physical device and entitlement gates remain separate. See `docs/automatic_tls_fallback.md`.

Desktop bootstrap uses the shared fixed-dark application theme. Startup and packaged JVM arguments also pin native
macOS appearance before the first AWT window is created. The resize-background workaround uses the same dark tokens.
See `docs/fixed_dark_application_appearance.md` for validation and native appearance scope.

## Persisted-query composition

Binds one process-local verified GraphQL catalog, bounded native manifest reader and import/observation use cases.
Container shutdown clears ephemeral source mappings. File reads retain at most the import limit plus one overflow
marker byte; the protocol extension owns validation and no database migration is needed.
See `docs/graphql_persisted_queries.md`.

## Multi-device workspace and control sessions (2026-11-13)

The composition root binds the shared Ktor endpoint, presence, telemetry and workspace use cases. The macOS adapter uses JBR native custom title-bar capability and measured hit regions; unsupported runtimes retain complete system decorations. macOS run/package tasks select a Java 21 JetBrains runtime by default through the existing toolchain resolver; knet.desktop.javaHome can override it. IDE launches on unsupported runtimes retain native decorations.
