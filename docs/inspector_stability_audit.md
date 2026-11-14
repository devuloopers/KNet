# Inspector selection stability audit

Status: SOFTWARE QUALIFICATION PASSED (2026-11-14). This audit uses source inspection, ViewModel/application tests and
headless Compose rendering; it does not launch the native desktop application or modify user traffic.

## Confirmed defect and fix

Selecting an opaque row replaced `TrafficProtectionState` with an empty loading state before reading
canonical metadata and observing policy. The inspector also rendered the new row before its selection
effect ran. Together these transitions temporarily removed the selected check mark, changed button
labels/colors and replaced Current handling with Loading, even when both rows used the same saved rule.

Opaque rows now retain a bounded canonical metadata reference. The application use case can immediately
project the current manual/default policy for that row's explicit source. Traffic uses this read-only
projection on the first frame and keeps it live while the canonical flow/ingress lookup completes.
Canonical lookup still determines connection/session-specific automatic fallback, missing history and
authoritative errors. Mutations continue to resolve canonical metadata independently, and callbacks are
fenced by the selected flow ID. A selection revision also rejects obsolete observation results.

This is a live projection, not a cache of the previous row's selected action. Device identity, SNI/IP,
port, application and transport matching retain existing policy precedence. Mixed-source views and
ambiguous automatic fallback wait for the connection lookup rather than borrowing another connection's
decision. Malformed retained targets cannot throw from the synchronous Compose presentation path.

## Component audit

| Inspector area | Finding and verification |
|---|---|
| TLS Future connections | Fixed the empty-state reset and the first-frame selection-effect gap. Pixel comparisons check identical saved-policy controls on each tested frame at 580/900-pixel widths, with node identities and bounds retained. |
| TLS/opaque summary and diagnostics | Remain mounted for same-kind row changes. Captured reasons, counters and outcomes remain row-specific; current future policy never rewrites historical evidence. |
| Overview and protocol details | Shared viewport stays mounted; existing tests cover GraphQL details, gRPC status/trailers, WebSocket lifetimes, GraphQL WS and SSE. Technical expansion and scroll offsets deliberately reset for another capture. |
| Request, Response and Timeline | The protocol matrix checks navigation/header identity and geometry on the first rendered frames of row changes at wide/narrow widths. Existing preview-cache, stale-ID, unavailable-body and export tests remain required. |
| Messages and Events | The matrix covers gRPC, WebSocket, negotiated GraphQL WS and SSE message workspaces. Existing tests cover selected-message metadata mounting, binary/unavailable previews and correct exchange identity. |
| Shared inspector components | Summary, fields, disclosure, action wrapping, overflow/scrollbars and content containers reuse the KNet design system. No whole-inspector crossfade, remount key or selection animation was added. |

Genuine loading of a previously uncached body or connection-specific fallback is still permitted. A new
capture must not display another capture's body or learned decision merely to suppress a loading state.

## Test evidence

- `ObserveProtectedTrafficFlowHandlingUseCaseTest`: immediate read-only manual projection, live policy
  changes during delayed reads, missing-history precedence, full device identity, canonical ingress
  resolution, invalid target safety and existing selector/fallback checks.
- `TrafficProtectionViewModelTest`: equivalent-row first frames, delayed live policy, selection changes,
  stale callback fencing, metadata changes for the same flow ID, missing records and mutation regressions.
- `OpaqueFlowInspectorTest`: exact action-region pixel equality, node identity, bounds and labels across
  repeated cancelled TLS row switches; existing mouse, keyboard, overflow and saved-action cases.
- `TrafficInspectorRedesignTest`: HTTP, GraphQL, gRPC, WebSocket, GraphQL WS and SSE across Overview,
  Request, Response, Timeline and applicable Messages/Events views at narrow/wide sizes.

The wider UI run exposed a timing-sensitive table test using an animated pixel scroll to reach the top.
Its fixture now uses the lazy list's explicit semantic Home (`ScrollToIndex(0)`) action and a bounded
settling check, retaining the original wheel-scroll, insertion-anchor and top-follow assertions.
Production table/auto-scroll behavior is unchanged by this inspector fix.

Focused log: `/tmp/knet-inspector-stability-focused.log`. Final complete gate and desktop packaging:
`/tmp/knet-inspector-stability-final.log`. Reproduce with:

```sh
./gradlew devicePolicyLibrariesQualification :products:desktop:createDistributable \
  --max-workers=1 --no-daemon
```

This session redirects build output to the existing isolated verification directory. Visible native
desktop interaction and physical-device acceptance remain separate from these finite automated checks.

## Final results

| Check | Result |
|---|---|
| Complete policy/protocol gate | **1,708 tests across 33 outputs; zero failures/errors/skips.** |
| Affected application/UI outputs | **385 passed**: application 94, Traffic 167, HTTP inspector 54, design system 32, app host 38. |
| Identical-policy cancelled TLS switches | **24 transient frames** across 580/900-pixel widths: identical action-region pixels, control bounds, semantics IDs and selected labels. |
| Protocol/navigation matrix | **168 row-change frames** across HTTP, GraphQL, gRPC, WebSocket, GraphQL WS and SSE; wide/narrow sizes and applicable inspector tabs. |
| Architecture and protection-ledger checks | PASS. |
| Desktop distributable | PASS. |
| Diff whitespace check | PASS. |

The ten additional cases over the preceding 1,698-case baseline cover five application scenarios,
three ViewModel scenarios, the TLS pixel comparison and the all-protocol frame matrix. The existing
accessibility-scroll assertion also passed using its explicit semantic Home navigation fixture.

Clean evidence and render images are retained in
`/Users/devuloopers/.codex/verification/knet-inspector-stability-2026-11-14/`.
The rebuilt app is
`/Users/devuloopers/.codex/verification/knet-smart-scroll-2026-11-14/build/products/desktop/compose/binaries/main/app/KNet.app`.
These checks establish finite automated scenarios; visible native desktop testing was not performed.
