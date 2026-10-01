# `:ui:desktop:networkConditions`

## Responsibility

Owns desktop presentation for configuring and observing Network Conditions.

## Owns

- The Network Conditions screen, immutable presentation state, and ViewModel actions.
- Master enable/reset controls, global-profile selection, exact/wildcard domain-rule presentation, and custom
  profile editing for asymmetric rates, utilization, latency, jitter, virtual MTU, and explicit failure effects.
- The prefilled, duplicate-aware Traffic quick-add confirmation flow.
- Presentation of payload-free runtime counters and platform packet-condition availability.

## Does not own

- Host normalization and precedence policy, persistence, proxy or VPN/TUN shaping, canonical Traffic records,
  navigation shell, or product dependency injection.
- Protocol inspection, packet payload decoding, operating-system permissions, or capability promotion evidence.

## Dependency rule

May depend on `:application:desktop`, `:core:domain`, and `:ui:core`. It must not depend on `:engine:*`,
`:connectivity:*`, `:data:*`, `:storage`, or `:products:*`.

## Runtime rule

All mutations cross `NetworkConditionsRepository`; Traffic preparation crosses
`PrepareNetworkConditionRuleUseCase`; telemetry is read through `NetworkConditionRuntimeTelemetry`. The UI never
parses display URLs or handles Netty/native packet types.
