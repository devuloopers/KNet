# Device policy libraries qualification

Status: SOFTWARE QUALIFICATION PASSED (2026-11-14). This report records executed scenarios, not a claim of exhaustive line/branch
coverage or every possible network/application behavior. The implementation plan defines the feature's
acceptance boundary. Physical results below use disposable fixtures; the user's running desktop,
Companion pairing, certificate installation and capture history remain untouched.

## Automatic replacement follow-up (2026-11-14)

Saving an exact matching rule now replaces its activation for the captured device automatically in
Intercepts, Network Conditions and Protected Traffic. All three editors and the Traffic shortcut omit
the replacement checkbox. Previous definitions remain available, sibling activations remain intact,
and a four-second confirmation names the original device even if the user switches tabs during Save.
Inactive edits stay inactive; self-edits and ordinary overlapping selectors do not report replacements.

Added regression scenarios:

- `RoomDevicePolicyLibraryTest`: automatic replacement for every rule family with either availability,
  retained definitions and sibling settings, inactive/self edits, distinct methods/ports/phases/operations,
  overlap precedence, rollback after disabling a conflicting binding, and concurrent independent writers.
- `DevicePolicyManagerEndToEndTest.savingEquivalentRulesAutomaticallyReplacesOnlyTheCapturedDeviceAcrossAllManagers`:
  real Room/Koin managers, switch from A to B before Save, retained A target, B's original activations,
  device-named confirmations and expiry without a replacement opt-in.
- `DevicePolicySocketEndToEndTest.automaticExactReplacementChangesOnlyItsAuthenticatedDeviceWhileSiblingStreamingContinues`:
  replace A with Offline while B has queued shaped bytes; A fails and recovers after another save,
  while B completes the intact 25,000-byte stream using its original bandwidth rule.
- `RuleLibraryEditorTest`: removed checkbox and rendered polite live confirmation with captured name.
- `PrepareNetworkConditionRuleUseCaseTest`: Traffic quick-add prefers the active equivalent over retained
  inactive alternatives in either list order, while an inactive-only saved rule remains editable and disabled.

The final gate table below includes these scenarios. Refreshed desktop/Android packages and shared UI
Apple simulator compilation use the same source. The physical Android, sustained-load and native Apple
measurements later in this report belong to the earlier qualification; they were not rerun for this follow-up.
Follow-up logs are `/tmp/knet-auto-replace-qualification.log`, `/tmp/knet-auto-replace-final.log` and
`/tmp/knet-auto-replace-acceptance.log`; evidence copies use the `automatic-replacement-` prefix in the
evidence directory recorded below.

## Availability wording follow-up (2026-11-14)

All three editors now show **Available to** with **All devices** and
**Only this device — [captured name]**. Rule lists reuse the same availability labels. Creation says
**Enabled only for [name] after saving**; All devices also explains that other devices can enable it
separately. Editing impact, deletion and availability errors use plain device/rule wording. The internal
Shared/private ownership and activation behavior is unchanged.

The affected application, design-system, breakpoint, Network Conditions, Protected Traffic and app-host
suites passed **231 cases, zero failures/errors/skips**. Apple simulator UI compilation and the refreshed
desktop distributable also passed. Evidence: `/tmp/knet-rule-availability-wording.log`.

## Implementation

- Shared/private authored definitions are separate from per-workspace activation for Intercepts,
  Network Conditions and Protected Traffic. Creation enables only its captured workspace.
- Room v39 migrates existing rules/settings to Shared with preserved activations for every existing
  registered identity and Other traffic. Future identities start inactive.
- Definitions have optimistic revisions and shared-usage checks. Duplicate/replacement/deletion/Forget
  are transactional. An open editor or confirmation retains its original identity after tab switching.
- Runtime decisions use authenticated ingress. Bandwidth budgets, queue limits, counters, TLS fallback
  epochs and pending interception ownership remain separate. The response aggregation prefilter also
  selects the authenticated workspace, including HTTP/2 child streams.
- Custom profiles remain shared assets; profile editing discloses references/usage and offers duplication.
- Per-device packet policies use the existing authenticated WSS/HTTPS interfaces. Their transport,
  acknowledgement and platform capability limits are retained.

## Executed focused coverage

| Area | Evidence |
|---|---|
| All three libraries | `RoomDevicePolicyLibraryTest`: creation, future identity, independent activation, private visibility/authorization, inactive edits, promotion, duplication/replacement, revisions, disclosed usage, rollback, concurrent writers, profile references, restart and Forget. |
| Real schema migration | `KNetDatabaseMigrationTest`: retained schema generations including v38, zero/two devices, enabled/disabled rules, settings, IDs, capture preservation and new identity inactivity. |
| Manager composition | `DevicePolicyManagerEndToEndTest`: real Koin + Room + all three ViewModels; open on A, switch to B, save to A; shared usage, private duplicate, scoped defaults and captured delete target. |
| Controls/layout | `RuleLibraryEditorTest` and existing rendered manager/editor suites: availability controls, captured target, promotion reversal, shared lock/duplicate, scroll and fixed actions. |
| Network engine | `DeviceNetworkConditionEngineTest`: independent budgets in both directions, within-device aggregation, Other/unmatched traffic, live change isolation, queue fairness, telemetry cadence and ten-workspace accounting. |
| Actual proxy sockets | `DevicePolicySocketEndToEndTest`: Room -> authenticated TLS carrier -> proxy -> origin; separate speeds, concurrent upload/download, pause ownership, live disable, Offline/recovery and same destination Tunnel/Block/Inspect with capture unavailable. |
| HTTP/2/gRPC identity | `CompanionH2cEndToEndTest`: two authenticated peers on the same endpoint, request/response pause isolation, plus existing native cardinality, trailer, streaming, EOF and credential regressions. |
| Control delivery | `CompanionControlGatewayTest`: separate real WSS configurations, no sibling revision broadcast, HTTPS resolver parity, mismatched credential rejection, heartbeat and listener restart. |
| TLS fallback | `AutomaticTlsFallbackTest`: independent authorization epochs, same destination across devices, scoped reset, stale/session/CA fencing and existing classification/socket regressions. |
| Protocol regression | Existing HTTP/HTTPS/H2/H2C, GraphQL GET/POST/persisted query, WebSocket/GraphQL WS, gRPC and SSE gates; new framed-factory tests verify owner identity and inactive-sibling bypass before buffering. Complete totals are recorded below. |
| Interception resources | HTTP and message ownership, scoped clear after disabling Shared rules, combined global cap, per-device fairness, 1,000 Shared definitions compiled once across ten workspaces, retained-byte limits and existing timeout/disconnect tests. |

## Defects found during qualification

- A policy committed between simulator initialization and subscription could be lost. The scoped
  observer now reconciles its initial collected value; a deterministic startup race regression covers it.
- An idle workspace's empty telemetry map compared equal to its previous sample, stopping clock ticks.
  Published workspace frames now include their real monotonic sample time, without per-packet publication.
- HTTP admission did not count pending protocol messages toward the same global interception cap.
  Both paths now enforce the combined count/byte budget and preserve per-device capacity.
- A newly enabled sibling could change Shared edit/delete impact without changing its authored revision.
  Captured usage is checked inside the commit transaction alongside the authored revision.
- Forgetting a workspace without forgetting its registration allowed stale actions to recreate it.
  Explicit Forget now removes registration/credentials and cascades only that identity's private policy.
- The desktop composition root compiled against an older transitive Lifecycle API than its runtime UI
  dependencies. It now explicitly uses the same catalog Lifecycle version for compilation.
- Manager headers now replace the previous device name immediately while the newly selected catalog loads.

Legacy test adapters were updated to retain their deliberate delayed-publication behavior after scoping.
The new framing fixtures use complete WebSocket Upgrade headers and explicit SSE decisions. A bounded bind retry closes failed Netty owners when the OS takes a fixture port before binding. The rendered
scroll fixture reacquires the current semantics node after list insertion, as an accessibility client does.

## Physical Android evidence

Two attached devices: Samsung SM-A505F (USB) and Samsung SM-A032F (wireless ADB). Each successful probe
checks complete payload length and byte integrity. Timings include TLS carrier setup and Android/ADB
transport overhead; they measure bounded delivered throughput, not an inferred line-rate guarantee.

| Scenario | Device A | Device B | Result |
|---|---:|---:|---|
| Shared definition enabled only on A; 12,500-byte download | 1,645 ms | 173 ms | PASS: B discovers availability without being shaped. |
| Same 100 kbps definition enabled on both; concurrent downloads | 1,069 ms | 1,148 ms | PASS: independent budgets, concurrent completion. |
| Concurrent 12,500-byte uploads at 100 kbps | 1,066 ms | 1,152 ms | PASS: independent upload limits and intact payload. |
| Breakpoint enabled only on A | A owns pending request | B forwards | PASS: explicit A resume; no sibling pause. |

Eight hardware requests passed in the final run, including the stricter 1,850 ms concurrent-transfer upper bound. Temporary package and port forwarding were removed on both devices; both removals were checked through ADB.
Reproduction is documented in `tools/qualification/device_policy/README.md`. The dedicated physical task
is separate from deterministic CI so an absent phone cannot be silently counted as a passed test.

## Sustained transfer and resource evidence

`NetworkProtectionLoadQualificationTest` passed all three real-socket cases with no failures or skips:

| Case | Measured result |
|---|---|
| Four downloads sharing one 100 kbps workspace budget | 99,760.65 bit/s across 800,000 bytes in 64.15 s. |
| Four uploads sharing one 100 kbps workspace budget | 99,962.44 bit/s across 800,000 bytes in 64.02 s; complete EOF/acknowledgement. |
| 500 MiB across four slow-consumer streams | All 524,288,000 bytes verified; 18 MiB heap increase, no measured direct-buffer increase. |

The independent two-device budgets are verified separately by deterministic scheduler tests and the
physical probes above. Resource figures are fixture deltas, not a claim about total desktop process RAM.

## Reproduction

The deterministic gate requires complete nonempty reports and rejects failed, errored or skipped cases:

```sh
./gradlew devicePolicyLibrariesQualification --max-workers=1 --no-daemon
```

The additional gates used here are:

```sh
./gradlew :engine:simulator:networkProtectionLoadQualification multiDeviceAppleQualification \
  :ui:core:compileKotlinIosArm64 \
  :products:companion:iosApp:linkDebugFrameworkIosSimulatorArm64 \
  :products:companion:iosApp:compileKotlinIosArm64 \
  :products:desktop:createDistributable :products:companion:androidApp:assembleDebug \
  --max-workers=1 --no-daemon
```

Physical reproduction and its disposable package are documented in
[`tools/qualification/device_policy/README.md`](../tools/qualification/device_policy/README.md).
This session redirected build output using
`/Users/devuloopers/.codex/verification/knet-smart-scroll-2026-11-14/isolated.init.gradle.kts`, with one worker,
configuration cache disabled, in-process Kotlin compilation, a 1,536 MiB Gradle heap and 768 MiB metaspace.
Evidence logs are `/tmp/knet-device-policy-clean-gate.log` and `/tmp/knet-device-policy-acceptance.log`;
the latter records successful hardware/load/native tasks and the subsequently corrected test-fixture failures.

## Final gate results

The current gate also includes the subsequent [per-device capture increment](device_capture_qualification.md),
and [inspector stability audit](inspector_stability_audit.md), with their toolbar, recording-boundary,
authenticated-socket and frame-comparison cases. Earlier physical and load
measurements remain the dated evidence recorded above.

| Gate | Result |
|---|---|
| `devicePolicyLibrariesQualification` | **1,708 tests across 33 complete task outputs; 0 failures, 0 errors, 0 skipped.** Includes unit, Room, rendered UI, composition and real socket integration/E2E cases. |
| Architecture and protection-ledger validation | PASS. |
| `devicePolicyPhysicalQualification` | 1 hardware case, 8 payload-verified requests on two Android phones; PASS, no skips. |
| `networkProtectionLoadQualification` | 3 real-socket sustained/resource cases; PASS, no skips. |
| `multiDeviceAppleQualification` | 13 native control-session cases; PASS, no skips. |
| Desktop distributable and Android debug APK | PASS. |
| Apple simulator framework link, device-arm64 application and shared-UI compilation | PASS. |
| `git diff --check` | PASS. |

The deterministic gate's report is
`/Users/devuloopers/.codex/verification/knet-smart-scroll-2026-11-14/build/root/reports/device-policy-libraries/summary.json`.
An evidence copy and the physical results are retained in
`/Users/devuloopers/.codex/verification/knet-device-policy-2026-11-14/`.
The packaged desktop is
`/Users/devuloopers/.codex/verification/knet-smart-scroll-2026-11-14/build/products/desktop/compose/binaries/main/app/KNet.app`.
The package has one implementation of `ViewModelStore`, at Lifecycle 2.11.0; its compatibility bridge
contains no duplicate implementation class.

## Complete deterministic task outputs

All outputs below contain zero failed, errored or skipped test cases.

| Task | Passed cases |
|---|---:|
| `:core:domain:jvmTest` | 99 |
| `:core:http:jvmTest` | 87 |
| `:core:traffic:jvmTest` | 24 |
| `:core:companion:jvmTest` | 32 |
| `:application:desktop:test` | 94 |
| `:application:companion:jvmTest` | 69 |
| `:connectivity:desktop:multiDeviceControlQualification` | 31 |
| `:connectivity:companion:testAndroidHostTest` | 99 |
| `:data:desktop:jvmTest` | 178 |
| `:storage:jvmTest` | 8 |
| `:engine:proxy:test` | 178 |
| `:engine:protocol:test` | 60 |
| `:engine:interceptor:test` | 20 |
| `:engine:simulator:test` | 56 |
| `:engine:grpc:test` | 23 |
| `:engine:sse:test` | 29 |
| `:engine:websocket:test` | 28 |
| `:engine:graphqlWebSocket:test` | 22 |
| `:testingServer:test` | 22 |
| `:ui:core:jvmTest` | 32 |
| `:ui:desktop:app:jvmTest` | 38 |
| `:ui:desktop:traffic:jvmTest` | 167 |
| `:ui:desktop:httpPanel:jvmTest` | 54 |
| `:ui:desktop:networkConditions:jvmTest` | 48 |
| `:ui:desktop:protectedTraffic:jvmTest` | 24 |
| `:ui:desktop:breakpointManager:jvmTest` | 8 |
| `:ui:desktop:apiStudio:jvmTest` | 62 |
| `:ui:desktop:apiStudio:websocket:jvmTest` | 5 |
| `:ui:desktop:apiStudio:graphqlWebSocket:jvmTest` | 5 |
| `:ui:desktop:apiStudio:grpc:jvmTest` | 9 |
| `:products:desktop:test` | 83 |
| `:products:companion:androidApp:testDebugUnitTest` | 13 |
| `:products:companion:di:testAndroidHostTest` | 1 |

## Remaining qualification / boundaries

Signed iOS packet-provider testing and desktop native gesture/UI interaction are not
established by these fixtures. Android TUN UDP/QUIC/WebRTC shaping and the real Companion reconnect UI
were not physically rerun against the changed desktop in this qualification. Their existing host/unit
checks do not establish physical packet behavior. There is no new iOS/desktop packet provider or QUIC
payload decoder in this feature.

All named automated gate scenarios passed. These are finite scenario and regression checks, not measured
100% line/branch coverage or proof of every protocol/device/timing combination. The native/hardware limits
above remain explicit physical acceptance items rather than skipped tests counted as passes.
