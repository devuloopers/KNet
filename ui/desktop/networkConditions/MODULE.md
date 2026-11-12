# `:ui:desktop:networkConditions`

## Responsibility

Owns desktop presentation for configuring and observing Network Conditions.

## Owns

- Network Conditions header content and actions within UI core's shared screen layout; page insets, title/subtitle
  styling, header geometry and action overflow are inherited from the same contract as the other traffic controls.

- The Network Conditions screen, immutable presentation state, and ViewModel actions.
- Master enable/reset controls, global-profile selection, exact/wildcard domain-rule presentation, and custom
  profile editing for asymmetric rates, utilization, latency, jitter, virtual MTU, and explicit failure effects.
- The prefilled, duplicate-aware Traffic quick-add confirmation flow.
- Presentation of payload-free runtime counters and configured profile parameters.
- The grouped global-profile panel, Overview Canvas/counter panel, three-tab workspace state, virtualized tables, and readable
  rule summaries obtained from extension-owned editor values at the application boundary.
- A reusable global-control popover for the Traffic toolbar, using the same configuration/ViewModel, atomic
  profile activation, saved-profile selection, master bypass, error presentation, and full-screen navigation action.
  Its anchor measures the displayed label and animates width with KNet motion within the host's bounds; unselected
  labels do not reserve width, and reduced-motion settings apply immediate size changes.
- Shared global-profile presentation offers one No throttling choice for both saved null and built-in
  no-throttling defaults, without rewriting stored IDs during rendering. The workspace and Traffic popover use
  the same options/selection mapping and read the display name from the profile catalog. Rule/profile lists
  retain No throttling and custom unlimited profiles keep their identities. The renamed Unstable connection
  preset retains the stable lossy-unstable ID and parameters.

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

The full-screen global-profile field fills its assigned settings column. The separate Traffic shortcut retains
its existing shared label-driven width and motion; rule/profile form controls retain their parent constraints.

The approved 2026-11-12 refresh is confined to this destination: KNet surfaces, subtle metric separators, measured
line/area paths, an underlined selected tab and a bounded empty-rule panel. Size classification and table overflow
come from UI core. Compact heights condense descriptive copy and counters to preserve usable table rows. The
packet-capability footer is removed from this screen; capability declarations and implementations are unchanged.
See `docs/network_conditions_implementation_plan.md` for scope, rendering and regression evidence.

Overview is the initial tab and uses the remaining workspace for its graph. Rules and Profiles each receive the
full remaining table viewport with their own contextual add action; global controls remain above all three tabs.
Both tables use the shared viewport's separate measured-header slot, so their header surface reaches the right
edge while vertical scrollbars begin in the rows below it. Headers and rows keep identical horizontal alignment.
Tab-local saveable list state restores scroll positions. Monitoring belongs to the shared shell/ViewModel lifecycle
across Traffic and this destination, so switching screens/tabs neither resets history nor starts additional samplers.
Traffic quick-add and rule editing select Rules explicitly; the live footer link selects Overview while preserving
an unfinished rule draft for its own Rules tab.

NetworkThroughputIndicator is a shell-composed, accessible click target using the same rates and decimal formatter
as Overview. It starts at 0 kbps before the first sample and on monitoring reset; later invalid intervals retain
the unavailable dash, and valid idle intervals show zero, regardless of shaping enablement or capture filters.
Stable number slots keep spare space after their start-aligned value so labels
retain the shared small gap; footer overflow belongs to UI core.
NetworkConditionsRuntimeMonitoring is the sole composition-level owner of the shared sampling lifecycle.

The throughput sampler advances from monotonic elapsed time, coalescing subsecond observations without losing
cumulative byte totals. Duplicate/stale timestamps do not consume chart slots; counter resets invalidate the
measurement interval. Delayed ticks leave bounded invalid slots. Profile changes preserve the current monitoring
window across Traffic and Overview; leaving both and returning starts a fresh baseline. See the 2026-11-12 graph-clock repair in
`docs/network_conditions_implementation_plan.md` for regression evidence.
