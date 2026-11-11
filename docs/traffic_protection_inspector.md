# Protected connection controls in Traffic

Status: implemented and automated verification passed (2026-11-10).

Protected Traffic now contains only default TLS handling and saved rules. Its duplicate
observed-connections list, decision drawer and Overview/Advanced tabs have been removed. Traffic remains the place
to inspect captured connection evidence and change destination handling. Smart Compatibility is retired; see
[the current retirement record](smart_compatibility_retirement.md) for verification and compatibility details.

The approved inspector starts with the destination, captured status and a diagnosis from typed evidence. A
certificate rejection is explicit; app trust settings and pinning remain possible causes. Generic handshake
failures, blocked flows and encrypted tunnels have separate descriptions. No exact TLS alert token is invented.

Future connections shows the current saved/default rule. No learner snapshot, trial or automatic-decision action is
present. Typed historical automatic evidence remains readable without becoming active policy.

Always tunnel saves an exact destination/port/transport rule. Inspect next time saves or replaces the equivalent
inspection rule; it does not replay a request or repair the selected connection. A matching saved tunnel becomes an
inspection rule with the same ID, instead of leaving the override active behind a forgotten runtime entry.
Mutations stay in Traffic with inline success/error feedback and disabled controls while saving. Row context menus
use the same application workflow. Saved rules remain authoritative across restarts.

Duration and transfer counters remain visible. Technical details starts collapsed and retains captured policy,
source, security, offered protocols, timestamps, termination and condition evidence. Live policy changes never
rewrite these historical fields. Shared UI-core primitives own wrapping, scrollbars and intrinsic action reflow.

Tests cover live policy, exact-device isolation and legacy/missing contexts; rule replacement, errors, duplicate
clicks and selection changes; captured diagnosis versus current policy, overflow Forget, disclosure toggling,
compact text/overflow, disabled controls and the desktop reader's retained-flow/ingress lookup. See the main
implementation board for current execution evidence. The desktop application is not launched by verification.

Historical verification before automatic-learning retirement on 2026-11-10 passed 143 tests: application 53, Traffic 53, workspace 12, UI core 21 and focused
data-query 4, with zero failures/errors/skips. Desktop compilation and six architecture checks passed. Normal
and compact 125% text renders were inspected. All Gradle outputs were isolated under a task-specific temporary
build root so running development JARs were not replaced. This does not constitute physical-device acceptance
or a change to the separate automatic-fallback burst guard.

Saved-action selection follow-up (2026-11-10) [COMPLETED]: the winning exact saved choice now shows a checked
Tunnelling enabled or Inspection enabled control, with redundant submission disabled. Selection comes from
current policy on revisits/restarts. Inherited/global/Smart sources remain distinct, overridden choices identify
the winning policy, and transient success messages do not determine active state. Actions wait for authoritative
committed-rule publication; missing-port destination actions are rejected rather than broadened. Historical
captured outcomes remain unchanged. The [completed selection plan](traffic_protection_selection_plan.md) maps all
17 scenarios to executed assertions: 182 tests, desktop compilation and six architecture checks passed.
