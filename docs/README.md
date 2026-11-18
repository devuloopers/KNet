# KNet documentation map

KNet documentation separates current capability truth from dated design and delivery records. This distinction is
intentional: implementation presence, automated evidence, and release support are different states.

## Current sources of truth

Windows integrated window controls, runtime selection and native acceptance evidence are documented in
[`windows_title_bar.md`](windows_title_bar.md).

Use these documents when deciding what the repository currently owns or supports:

1. [`README.md`](../README.md) — public product scope, maturity summary, setup, limitations, and verification entry
   points.
2. [`module_responsibility_index.md`](module_responsibility_index.md) and each module's `MODULE.md` — current
   ownership and dependency direction.
3. [`adr/`](adr/) — accepted architectural decisions and invariants.
4. Focused qualification documents — protocol-specific implemented evidence and remaining promotion gates:
   [`http2_target_and_implementation_plan.md`](http2_target_and_implementation_plan.md),
   [`grpc_qualification.md`](grpc_qualification.md), [`websocket_qualification.md`](websocket_qualification.md),
   [`graphql_websocket_qualification.md`](graphql_websocket_qualification.md), and
   [`sse_qualification.md`](sse_qualification.md). Network Conditions test coverage, missing edge cases, and
   promotion evidence are tracked in
   [`network_conditions_test_gap_report.md`](network_conditions_test_gap_report.md). Protected Traffic manager
   behavior and UI qualification are recorded in [`protected_traffic_rule_manager.md`](protected_traffic_rule_manager.md);
   current automatic TLS fallback is documented in [`automatic_tls_fallback.md`](automatic_tls_fallback.md).
5. The executable `RuntimeCapabilityCatalog` in
   `products/desktop/src/jvmMain/kotlin/com/devuloopers/knet/products/desktop/di/inspection/InspectionBindings.kt`
   — final product maturity (`SUPPORTED`, `EXPERIMENTAL`, or `UNAVAILABLE`).

When prose and the runtime catalog disagree, use the more conservative status and correct the prose. A capability
is not release-supported merely because source code or a simulator build exists.

Traffic and API Studio export behavior, archive v1 and format limits are documented in
[`traffic_export.md`](traffic_export.md); the executed matrix and remaining native acceptance boundaries
are tracked in [`traffic_export_qualification.md`](traffic_export_qualification.md).

Local capture-file imports and their format/security bounds are documented in
[`capture_import.md`](capture_import.md). Authenticated per-device recording readiness and the Companion
start-capture dialog are documented in [`companion_capture_readiness.md`](companion_capture_readiness.md).
The two-device soak, resulting cleanup repair and combined verification are recorded in
[`two_device_capture_workflow_qualification.md`](two_device_capture_workflow_qualification.md).

The continuous Import overlay, pre-release drag feedback and four explicit editor handoffs are documented in
[`capture_drop_actions.md`](capture_drop_actions.md).

Traffic row actions, grouped connection handling and keyboard behavior are documented in
[`traffic_context_menu.md`](traffic_context_menu.md). Structured single/group clipboard copying is documented in
[`traffic_copy_details.md`](traffic_copy_details.md). API Studio’s execution-owned split copy control,
shared themed menu and native/SSE copies are documented in [`api_studio_copy.md`](api_studio_copy.md).

Traffic reading controls and arrival-count behavior are documented in
[`traffic_auto_scroll.md`](traffic_auto_scroll.md).

Product versioning, draft staging, package verification and coordinated release retries are documented
in [`releases/README.md`](releases/README.md). The 0.2.0 changes are recorded separately for
[Desktop](releases/desktop-0.2.0.md) and [Companion](releases/companion-0.2.0.md).
The [0.2.0 qualification report](releases/qualification-0.2.0.md) records successful hosted gates, verified
packages/signing/provenance and public release URLs.

## Public website

The independent React/Vite landing page, GitHub-only download links, branding/artwork provenance,
local commands and static Docker/Coolify delivery are documented in [`website.md`](website.md).
Executed browser/container evidence and delivery boundaries are tracked in
[`website_qualification.md`](website_qualification.md).
The approved full-feature redesign and automatic Compose screenshot pipeline are tracked in
[`website_feature_showcase_plan.md`](website_feature_showcase_plan.md).

## Companion status

Android and iOS implementations now exist for pairing, protected state, discovery, certificate setup/readiness,
and local VPN/packet-tunnel integration. Android Companion and Android VPN inspection have passed a manual
physical-device end-to-end smoke test and are therefore `EXPERIMENTAL`, not unavailable. Automated gates also
compile/test shared and platform boundaries, assemble the Android debug app, and link the iOS Simulator framework.
The iOS/iPadOS app is `EXPERIMENTAL`; physical iOS packet-tunnel inspection remains `UNAVAILABLE` until a properly
entitled build is qualified on devices.

Cleartext HTTP/2 and native gRPC carrier behavior and qualification are documented in
[`companion_h2c_forwarding.md`](companion_h2c_forwarding.md).

See [`companion_connectivity_kmp.md`](companion_connectivity_kmp.md) and
[`companion_certificate_readiness.md`](companion_certificate_readiness.md) for current implementation boundaries.
See [`companion_desktop_restart_recovery.md`](companion_desktop_restart_recovery.md) for VPN-owned
control recovery, presence and restart verification.

## Historical and planning records

The following files preserve useful reasoning and chronological delivery evidence, but dated status statements
inside them are not current capability claims:

- [`deep_architecture_scalability_engineering_audit.md`](deep_architecture_scalability_engineering_audit.md)
- [`target_architecture_and_implementation_plan.md`](target_architecture_and_implementation_plan.md)
- [`implementation_plan.md`](implementation_plan.md)
- [`website_implementation_plan.md`](website_implementation_plan.md) — implemented minimal React/Vite
  website and local qualification; public-domain configuration and actual deployment remain pending.
- [`traffic_api_studio_export_plan.md`](traffic_api_studio_export_plan.md) — completed export-only workflow
  for Traffic and API Studio, response gating, portable archives/HAR and qualification matrix.
- [`device_policy_libraries_implementation_plan.md`](device_policy_libraries_implementation_plan.md) — implemented
  shared/private rule definitions, independent per-device activation, migration and qualification matrix.
- [`multi_device_qualification.md`](multi_device_qualification.md) — multi-device automated evidence, resource bounds and hardware verification checklist.
- [`protocol_qualification_2026-11-14.md`](protocol_qualification_2026-11-14.md) — 1,045-case protocol gate run, connected Android matrix, isolated TLS/WSS/gRPC controls and remaining release blockers.
- [`physical_android_qualification_2026-11-14.md`](physical_android_qualification_2026-11-14.md) — connected Samsung results, measured global/per-host shaping, lifecycle recovery and open data-path defects.
- [`multi_device_implementation_plan.md`](multi_device_implementation_plan.md) — implemented authenticated device
  presence, separate Traffic views and native-backed macOS device tabs; physical qualification remains pending.
- [`network_conditions_implementation_plan.md`](network_conditions_implementation_plan.md)
- [`protected_traffic_passthrough_implementation_plan.md`](protected_traffic_passthrough_implementation_plan.md)
- [`android_companion_foundation_plan.md`](android_companion_foundation_plan.md)
- [`wifi_connectivity_implementation_plan.md`](wifi_connectivity_implementation_plan.md)
- protocol target/implementation plans where a separate qualification document now exists

Do not copy a historical `future`, `unavailable`, or `completed` statement into public release notes without
checking the current module contracts, runtime catalog, and focused gate.

## Architecture images

- [`assets/knet-architecture.svg`](assets/knet-architecture.svg) is a representative module view, not an exhaustive
  dependency graph.
- [`assets/knet-companion-setup-flow.svg`](assets/knet-companion-setup-flow.svg) describes the implemented
  pre-release setup path; it does not imply physical-device release qualification.

- [Connection EOF cleanup and upgraded-flow qualification](connection_lifecycle_qualification.md) documents
  keep-alive/admission cleanup, multiplexed response drain, and WebSocket close regression evidence.

- [Traffic Clear performance](traffic_clear_performance.md) covers v38 reference indexes, metadata completion,
  deferred file cleanup/retry, UI refresh coalescing, migration and scale-test evidence.

- [Device policy libraries qualification](device_policy_libraries_qualification.md): scoped-library scenarios, real sockets and disposable two-device Android evidence.
- [Per-device capture qualification](device_capture_qualification.md): independent Start/Pause recording, streaming cut boundaries, authenticated sockets and remaining physical acceptance.
- [Inspector stability audit](inspector_stability_audit.md): TLS action flashing, live policy projection and frame-level checks across the supported inspector views.
