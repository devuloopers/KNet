# `:ui:desktop:networkConditions`

## Responsibility

Owns desktop presentation for configuring and observing Network Conditions.

## Owns

- The Network Conditions screen, immutable presentation state, and ViewModel actions.
- Master enable/reset controls, global-profile selection, exact/wildcard domain-rule presentation, and custom
  profile editing for asymmetric rates, utilization, latency, jitter, virtual MTU, and explicit failure effects.
- The prefilled, duplicate-aware Traffic quick-add confirmation flow.
- Presentation of payload-free runtime counters and platform packet-condition availability.
- The compact global toolbar, bounded Canvas monitor, Rules/Profiles tab state, virtualized tables, and readable
  rule summaries obtained from extension-owned editor values at the application boundary.
- A reusable global-control popover for the Traffic toolbar, using the same configuration/ViewModel, atomic
  profile activation, saved-profile selection, master bypass, error presentation, and full-screen navigation action.
  Its anchor measures the displayed label and animates width with KNet motion within the host's bounds; unselected
  labels do not reserve width, and reduced-motion settings apply immediate size changes.

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

The global-profile selector inherits the shared dropdown width and motion; full-width rule/profile form controls retain their parent layout constraints.
