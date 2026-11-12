# Protected Traffic rule manager

Date: 2026-11-11. Status: implemented and software-qualified; physical acceptance remains separate.

## Behavior

Protected Traffic now uses KNet's manager layout: compact default TLS handling in the header, a search field and
Add rule action, and a virtualized table. Columns are Enabled, Destination, Application, Port, Transport, Action and
Actions. Transport also shows a specified IP family. Edit opens the shared animated right-hand drawer; delete and
enable/disable act on the stable saved rule identity. No connection observations, compatibility presets or automatic
learning controls are added. Runtime diagnostics and destination handling remain in Traffic.

Search is literal and case-insensitive. Every space-separated term must match the displayed selector/action metadata;
search never reorders or changes policy. Rules saved from Traffic appear through the same authoritative configuration
flow, including while a query or editor is open. Retired built-ins are excluded; historical imported exceptions have
an Imported badge. No service catalogue is recreated.

The drawer retains application identity, host/wildcard/IP/CIDR, port, transport, IP family and action. Empty port means
all ports; all-transports and any-IP choices are explicit selected values. Invalid pasted ports are rejected instead
of silently rewritten. Unsupported direct bypass cannot be newly selected on the explicit proxy; historical Bypass
values remain visible as encrypted tunnelling. Default handling offers Inspect, Tunnel and Block. It governs the
existing proxy TLS connection routes, not arbitrary plain HTTP or UDP traffic.

Editing preserves identity and the saved enabled state. Toggling or saving an unchanged imported exception preserves
its origin. Editing its match or action deliberately converts it to a user rule, as stated inside the drawer and
required by Room's existing retirement contract. The repository still rejects fabricated imported exceptions.
Duplicate scopes and other repository errors stay inline without losing the draft. A deleted editor target cannot be
resurrected by Save. Stale table callbacks resolve the latest saved rule before editing or toggling.

Pending saves disable duplicate submission, closing and replacing the editor. Per-rule changes and default handling
have independent pending guards. Cancellation releases pending state without presenting an error. Changes affect new
connections; historical capture evidence and established sockets are not rewritten.

## Shared layout and architecture

`:ui:core` owns manager minimum width and density, one horizontal viewport for table headers/rows, pinned horizontal
and vertical scrollbars, and viewport-local empty states. Rows use stable IDs and LazyColumn virtualization. Compact
layout selection comes from the shared responsive foundation. The common drawer shell owns size and reduced-motion
animation; its form scrolls independently from the fixed header/actions. Footer action alignment is a shared inspector
option. The feature adds no resize thresholds or platform dependencies.

The ViewModel still depends on application use cases only. Matching, precedence, persistence, Traffic integration and
raw TLS transport remain in their existing layers. No database schema, migration or policy precedence changes are
introduced by this manager.

## Verification

Headless Compose scenes exercise the actual manager, ViewModel and application use cases. No desktop process is
launched. Gradle outputs are isolated from the running application's JARs using the local verification init script.

| Area | Automated evidence |
| --- | --- |
| Default and rule controls | Default selection, Imported badge, enable, edit, delete, Add, Cancel and close |
| Form validation | Invalid wildcard correction, malformed/out-of-range/overflow ports, blank all-port selection, backend failure recovery |
| Scope round trip | Wildcard normalization, all transports, IPv6, disabled state and stable identity |
| Import retirement | Unchanged saves/toggles preserve origin; scope/action edits become USER; real migrated Room data rejects forged or unpromoted edits |
| Live configuration | Traffic-style external rule writes appear under active search without replacing the draft; stale/removed-row handling |
| Async operations | Delayed/duplicate save, per-rule serialization, owner cancellation, unready configuration and unsupported default bypass |
| Search | Literal wildcard/bracket/IP matching, case-insensitive all-term matching, preserved order, clear/empty state and focus after typing |
| Layout | Wide and 680-by-560 larger-text scenes, finite table scrolling, virtualized rows, visible empty state and accessible fixed editor actions |
| Existing systems | Shared components, breakpoint manager, Traffic controls, policy/storage/proxy suites and desktop composition |

The final verification command passed in 2 minutes 55 seconds:

```sh
./gradlew --init-script /tmp/knet-traffic-inspector-20261110.init.gradle.kts \
  protectedTrafficQualification networkConditionsQualification \
  :ui:desktop:breakpointManager:jvmTest \
  :ui:core:compileAndroidMain :ui:core:compileKotlinIosSimulatorArm64 \
  :products:desktop:compileKotlin \
  --max-workers=3 --no-daemon --no-configuration-cache --console=plain
```

XML evidence contains **1,029 tests across 26 distinct test-task outputs**, with zero failures, errors or skips,
including **22 Protected Traffic tests**. The table below identifies the focused suites:

| Suite | Cases |
| --- | ---: |
| ProtectedRuleSearchTest | 2 |
| ProtectedTrafficSettingsTest | 4 |
| ProtectedTrafficWorkspaceTest | 1 |
| ProtectedTrafficManagerViewModelTest | 11 |
| ProtectedTrafficViewModelTest | 4 |

The Room imported-exception contract was extended in LegacyPresetRetirementTest and passed both the targeted run
and full data suite. All six architecture checks and ledger validation passed. Desktop compilation and UI-core
Android/iOS simulator compilation passed. Fresh wide, compact, editor and empty-state headless renders were inspected;
`git diff --check` passed. The new search, manager mutation and screen suites are mandatory in the aggregate report
gate. The independent storage-scale task was not rerun for this UI change and is excluded from the counts above.

Local evidence: `/tmp/knet-protected-manager-qualification.log` and
`/tmp/knet-protected-manager-qualification-summary.json`. Headless previews are written to
`ui/desktop/protectedTraffic/build/reports/protected-traffic-ui/manager-{rules,empty,compact,editor,editor-compact}.png`.
The init script redirects every Gradle module's build output to `/tmp/knet-traffic-inspector-20261110`; it does not
replace the running application's JARs. No app was launched and no user database was changed. Rebuild/relaunch loads
the manager. Physical-device acceptance remains separate; these UI changes do not qualify unavailable packet
providers or remote service behavior.
