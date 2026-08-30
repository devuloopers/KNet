# KNet documentation map

KNet documentation separates current capability truth from dated design and delivery records. This distinction is
intentional: implementation presence, automated evidence, and release support are different states.

## Current sources of truth

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
   [`sse_qualification.md`](sse_qualification.md).
5. The executable `RuntimeCapabilityCatalog` in
   `products/desktop/src/jvmMain/kotlin/com/devuloopers/knet/products/desktop/di/inspection/InspectionBindings.kt`
   — final product maturity (`SUPPORTED`, `EXPERIMENTAL`, or `UNAVAILABLE`).

When prose and the runtime catalog disagree, use the more conservative status and correct the prose. A capability
is not release-supported merely because source code or a simulator build exists.

## Companion status

Android and iOS implementations now exist for pairing, protected state, discovery, certificate setup/readiness,
and local VPN/packet-tunnel integration. Android Companion and Android VPN inspection have passed a manual
physical-device end-to-end smoke test and are therefore `EXPERIMENTAL`, not unavailable. Automated gates also
compile/test shared and platform boundaries, assemble the Android debug app, and link the iOS Simulator framework.
The iOS/iPadOS app is `EXPERIMENTAL`; physical iOS packet-tunnel inspection remains `UNAVAILABLE` until a properly
entitled build is qualified on devices.

See [`companion_connectivity_kmp.md`](companion_connectivity_kmp.md) and
[`companion_certificate_readiness.md`](companion_certificate_readiness.md) for current implementation boundaries.

## Historical and planning records

The following files preserve useful reasoning and chronological delivery evidence, but dated status statements
inside them are not current capability claims:

- [`deep_architecture_scalability_engineering_audit.md`](deep_architecture_scalability_engineering_audit.md)
- [`target_architecture_and_implementation_plan.md`](target_architecture_and_implementation_plan.md)
- [`implementation_plan.md`](implementation_plan.md)
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
