# Traffic protection action selection

Current scope update (2026-11-10): Smart Compatibility was removed by user decision. Explicit destination rules,
saved selection and TLS diagnostics remain; automatic learning/trial/Forget requirements are superseded. See
[the retirement record](smart_compatibility_retirement.md). Historical execution counts below predate this removal;
the broader outstanding Network Conditions/platform work is not closed by it.


Status: [COMPLETED] — implemented and automated verification passed 2026-11-10. Physical smoke testing remains separate.

## Problem and outcome

After a user chooses Always tunnel, leaves the row and returns, the saved rule is read again, but both buttons
still look actionable. The transient success message is cleared on selection changes. This makes the persisted
choice appear forgotten even when Current handling already reports Tunnel from a saved rule.

The inspector must visibly reflect the applicable saved choice whenever a row is selected. It must derive that
state from authoritative policy, including rule precedence, instead of storing a last-clicked flag per row.

For example, returning to a cancelled connection after saving an exact destination tunnel rule should show:

- Captured outcome: Cancelled, unchanged.
- Future handling: Tunnel, from a saved rule.
- Tunnel action: selected and labelled Tunnelling enabled; redundant submission disabled.
- Inspect action: available to change future handling.

## Required behavior

| Current policy | Inspector behavior |
| --- | --- |
| Enabled exact destination Tunnel rule wins | Show the tunnel action selected, label it Tunnelling enabled, and disable duplicate submission. |
| Enabled exact destination Inspect rule wins | Show the inspection action selected, label it Inspection enabled, and disable duplicate submission. |
| Rule is still loading or configuration is not ready | Show loading; disable actions until authoritative state is available. Never flash an incorrect selected option. |
| Rule mutation is pending | Disable mutations and show saving feedback. Do not present a successful selection before persistence succeeds. |
| Mutation fails | Preserve the previous policy selection, show the error and allow another attempt. |
| Exact rule is disabled or deleted | Recompute from current effective policy and remove its selected state. |
| Global default, broader rule, or temporary Smart decision supplies the action | Identify that source accurately. Keep the option to save a meaningful exact destination override; temporary tunnelling must not appear to be a saved Always tunnel choice. |
| Saved destination choice is overridden by another winning rule | Show the effective handling and identify the saved choice as overridden. Do not present the last click as active. |
| Another row matches the same saved rule | Reflect the same applicable choice without saving another rule. |
| Another row has a different port, transport, or relevant application scope | Evaluate its own policy; do not carry selection over from the previous row. |

The rule scope remains the existing exact destination/port/transport scope used by Traffic actions. Explicit
destination rules can apply to multiple devices; temporary Smart entries remain isolated by trusted ingress and
verified application. This work must not silently change either scope.

## Implementation sequence

- [x] S1 — Extend the application read model to expose the equivalent destination rule needed for these two
  actions, plus its relationship to the winning rule. Continue to read canonical flow metadata and preserve
  conservative handling of missing/legacy context. Do not expose the entire configuration to the composable.
- [x] S2 — Derive a typed presentation state for each action: available, active, saving, unavailable, or overridden.
  Distinguish an enabled exact saved rule from an inherited/global or temporary Smart action. Match canonical
  selectors, not display text or the most recent feedback message.
- [x] S3 — Bind action styling, labels, accessibility selection and click availability to that state. Reuse KNet
  theme components and the shared inspector action layout. Retain the alternative action for switching handling.
- [x] S4 — Preserve observation on selection and policy changes. Returning to a row or creating a fresh ViewModel
  must reconstruct the choice from saved policy. Keep transient success/error feedback separate. Retain fences
  for late reads and saves belonging to another row.
- [x] S5 — Reuse the existing rule mutation workflow when switching Tunnel to Inspect or back. Preserve the
  equivalent rule ID, prevent duplicates, and retain failures/cancellation semantics. Loading or active-state
  controls must not submit a redundant action.
- [x] S6 — Add the automated matrix below, inspect normal/compact renders and verify desktop composition.
- [x] S7 — Update the implementation board and inspector documentation with executed evidence. Keep physical
  smoke testing recorded separately from automated verification.

Primary ownership:

- `:application:desktop`: authoritative destination-rule relationship and effective-policy read model.
- `:ui:desktop:traffic`: derived action state, observation, feedback and rendering.
- `:ui:core`: existing selection/accessibility components and shared responsive action layout.
- `:data:desktop`: existing Room persistence and reopen evidence; no new table or migration is expected.

## Automated test matrix

| ID | Scenario | Required assertion |
| --- | --- | --- |
| T01 | Save Tunnel on row A, select B, return to A | Tunnel remains visibly selected without another save. |
| T02 | Save Inspect on row A, select B, return to A | Inspection remains visibly selected. |
| T03 | Switch Tunnel to Inspect and back | One equivalent rule retains its ID and selected state follows committed policy. |
| T04 | Select another matching captured connection | The same applicable rule is shown, including an older cancelled/rejected row. |
| T05 | Different host, port or TCP/UDP transport | No incorrect selection leaks from another rule scope. |
| T06 | Canonically equivalent host and an IP route with visible SNI | Existing canonical matching identifies the correct destination rule. |
| T07 | Fresh ViewModel and Room database reopen | Persisted selection is reconstructed without a cached click or success message. |
| T08 | Edit, disable, delete or replace the rule from Protected Traffic | The open Traffic inspector updates to effective policy. |
| T09 | Save fails or is cancelled before commit | Previous selection remains; no false saved/active state or duplicate rule appears. |
| T10 | Rapid repeated clicks or delayed save | At most one pending mutation; active action cannot submit again. |
| T11 | Change rows during a delayed read or save, then return | Late feedback cannot affect another row; revisiting reads the committed rule. |
| T12 | Global Tunnel/Inspect, wildcard/app rule and Smart temporary tunnel | Source is correct; temporary/inherited state is not labelled as a saved exact action. |
| T13 | Competing rule overrides a saved destination choice | Effective action and override reason are accurate; last click is not presented as active. |
| T14 | Missing/cleared history, unavailable context, startup loading | Controls stay unavailable until the selected scope can be resolved safely. |
| T15 | Forget automatic decision while a saved rule exists | Saved selection remains; only the exact runtime entry is removed. |
| T16 | Change live policy while viewing a historical row | Original Cancelled/Rejected status, diagnosis and captured technical details remain unchanged. |
| T17 | Compact pane, 125% text, keyboard and reduced motion | Active labels remain readable, selected/disabled semantics are correct and alternative actions are usable. |

## Verification and acceptance

Run the affected application, Traffic and shared UI tests; verify Room reopen behavior with a temporary database;
compile desktop composition and run architecture checks. Use isolated Gradle output directories so running
development JARs are not replaced. Do not launch the desktop app during automated verification.

Acceptance requires T01–T17 to pass with no missing mandatory assertion, plus inspected normal and compact
renders. The user must not need to select Always tunnel again after navigating between rows when the saved exact
rule still applies. No success indicator may contradict the current winning policy.

## Scope boundary

This is a presentation and authoritative-state follow-up. It does not replay requests, repair old connections,
add a per-row persistence cache, or change the separate automatic-fallback burst guard. That guard refinement
remains pending on the main implementation board.

## Executed evidence — 2026-11-10

All T01–T17 scenarios have automated assertions. Verification passed 182 tests with zero failures, errors or
skips: application 56, Traffic 72, workspace 12, UI core 21, Protected Traffic UI 13, and focused data tests 8.
Desktop composition compiled and all six architecture checks passed. `git diff --check` passes. Normal,
compact 125% text, dark/light selected states and the overridden-rule state were rendered and inspected.

| Scenarios | Executed coverage |
| --- | --- |
| T01–T02 | `TrafficProtectionViewModelTest`: both saved choices survive row changes and a fresh ViewModel without another save. |
| T03 | ViewModel Tunnel/Inspect replacement keeps one rule ID; `TrafficProtectionSelectionPersistenceTest` verifies the same behavior against Room. |
| T04 | Matching-history selection in the ViewModel and a second retained flow in the Room persistence test. |
| T05–T06 | `ObserveProtectedTrafficFlowHandlingUseCaseTest`: canonical host/SNI, IPv6 route, different hosts/ports/TCP-UDP transport, and application precedence. |
| T07 | Room close/reopen plus fresh ViewModel selection reconstruction; no feedback or click cache is needed. |
| T08 | ViewModel live replacement/disable/delete plus actual Room disable/delete observation. |
| T09 | Failed alternative, cancellation before commit, and policy-publication timeout leave no false selected rule. |
| T10 | Active action cannot resubmit; delayed write/publication and duplicate clicks retain one mutation. |
| T11 | Existing late-read/save selection fences, revisiting after delayed save, and a render check for stale-row selection flashes. |
| T12 | `TrafficProtectionActionStateTest` and `OpaqueFlowInspectorTest`: global/application/wildcard/Smart sources are not saved exact selections and remain promotable. |
| T13 | Application evaluator, typed action state and rendered override explanation agree on the actual winning rule. |
| T14 | Missing history, unknown/not-ready context and startup loading keep controls unavailable; destination actions reject missing ports instead of creating an all-ports rule. |
| T15 | Forget removes only the automatic entry while the exact saved choice stays selected. |
| T16 | Room and UI assertions preserve Cancelled/Rejected outcome and captured policy fields while future handling changes. |
| T17 | Compact 125% text, dark/light theme, selected/disabled radio semantics, pointer no-op on active controls, keyboard alternatives and reduced-motion renders. |

A completed rule action now waits for the committed rule to appear in the authoritative configuration, with a
bounded five-second publication timeout. This closes the interval where the database write has finished but the
policy reader still holds an older snapshot. Failure to confirm publication produces an inline error instead of
inventing a selected state. Reading policy still never consumes an automatic tunnel trial.

Gradle outputs were isolated with a temporary init script under `/tmp/knet-traffic-inspector-20261110` to protect
running development JARs. The wider run covered desktop compilation and all six test groups above; the final UI,
desktop compilation and architecture rerun passed after the last presentation refinement. No desktop app was
launched. This evidence applies to this selection follow-up, not the separate Network Conditions/Smart completion
program or the pending automatic-fallback guard refinement.
