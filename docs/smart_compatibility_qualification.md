# Smart Compatibility qualification

**Retired by user decision (2026-11-10).** Smart Compatibility has been removed. The implementation, matrix and
execution records below describe historical work, not current product behavior. See
[the retirement record](smart_compatibility_retirement.md) for the current manual-rule workflow and verification.


Last updated: 2026-11-10

Initial software paths: **implemented, readiness follow-ups open**. Current automated gate: **passed; full matrix incomplete**.
Physical Android/Darwin qualification: **not completed**.

The subsequent [readiness audit](network_conditions_protected_traffic_readiness_audit.md) found software and test
gaps beyond physical testing. Its baseline forced rerun passed in 5 minutes 1 second (286 tasks executed), with
**868 tests, zero failures/errors/skips** across 24 distinct feature-gate outputs, superseding the earlier 954 total
recorded below. Completion work now replaces the SNI-context flag with supported JSSE certificate-flight evidence
and adds pairing/Forget invalidation, transport lifetime/fairness and real h2/SSE/load regressions. Current commands,
measured results and remaining software work are maintained in the
[completion execution record](qualification/network_protection_execution.md). The full matrix remains open.
Its latest final-tree aggregate passes **954 module tests across 26 distinct outputs**, with zero failures/errors/skips,
and three separate load cases. This fresh result is independent of the earlier superseded count with the same number.

## Implemented behaviour

- Smart Compatibility defaults on for new and upgraded databases. A saved disable, fallback action, explicit rules,
  existing enabled legacy choices as saved exceptions, Network Conditions, and retained captures survive migration.
- The proxy recognizes supported JDK received-certificate-alert formats after an independently observed complete
  downstream certificate flight is written successfully. SNI context selection alone, unsupported providers,
  incomplete/failed writes and potential resumption cannot establish presentation. Unknown evidence stays a
  suggestion; transmission does not prove pinning, trust or business success.
- Exact hostname/port/provider/application scopes isolate temporary decisions. The current mobile translator still
  lacks verified original-app attribution; the UI reports destination-only scope honestly.
- One independent reconnect owns a trial. Bidirectional opaque-byte progress establishes a temporary tunnel decision,
  not application/purchase success. No failed request or TLS handshake is replayed.
- The learner uses monotonic expiry, 512-key default capacity, 30-minute absolute lifetime, a 10-minute trial window,
  and a five-destination/60-second setup burst guard. Expired KNet authority setup suppresses automatic exclusion.
- Settings, pairing authority changes, Forget and proxy stop invalidate/fence temporary state. Koin close
  cancels owned observer/expiry jobs. Raw progress reaches the learner only once per positive direction.
- Schema 33 stores default-on settings and nullable canonical origin/reason/decision evidence. Old flows retain
  unknown evidence rather than fabricated provenance. Automatic exclusions remain session-local.
- Protected Traffic contains the Smart switch/help, default handling and saved rules. Its duplicate observed list,
  decision drawer and Overview/Advanced tabs were retired on 2026-11-10. Traffic owns connection diagnosis, scoped
  current policy/Smart evidence and persistent saved destination choices. Settings observes configuration only;
  removing the duplicate UI does not stop the runtime learner.
- The preset catalogue and toggle API are removed. Schema 34 imports enabled legacy choices as visible exceptions
  with their old lower precedence, preserving explicit user overrides, including disabled ones. Disabled presets
  create no rules; fresh databases contain no built-in rules.
- Bypass is unavailable for new direct-routing choices on the JVM
  explicit proxy; old saved Bypass policies retain their action with an explanation of effective tunnel mapping.

## Protected Traffic settings cleanup (2026-11-10)

The historical Overview/Advanced and animated observed-list tests below describe the retired UI. Current
`ProtectedTrafficWorkspaceTest` has four settings-shell tests: immediate content with no duplicate observations,
compact 125% text and readiness gating, actual hover help show/hide, and keyboard Smart switching.
`ProtectedTrafficSettingsTest` exercises the composed ViewModel/use-case screen: defaults, imported rule controls,
no preset catalogue or observed tabs, unavailable Bypass and add/cancel editing. The four ViewModel tests retain
rule validation/editing and independent Smart changes without losing the current draft or saved policy.

Current renders are `settings.png`, `settings-shell.png` and `settings-header-*.png` under
`ui/desktop/protectedTraffic/build/reports/smart-compatibility-ui/`. The normal settings and compact header renders
were visually inspected. The learner and Traffic actions remain outside this configuration-only screen.

Cleanup verification passed with isolated Gradle outputs (43 seconds): **109 tests, zero failures/errors/skips**:
Protected Traffic 9, Traffic 72, workspace 12, Smart runtime/socket 16. Desktop compilation and all six architecture
checks passed. No desktop app was launched and no live runtime JAR was replaced. This focused cleanup does not
close the broader software qualification matrix or change the automatic-fallback setup-suspicion guard.

```sh
./gradlew --init-script /tmp/knet-traffic-inspector-20261110.init.gradle.kts \
  :ui:desktop:protectedTraffic:jvmTest :ui:desktop:traffic:jvmTest :ui:desktop:app:jvmTest \
  :data:desktop:jvmTest --tests '*SmartCompatibilityRuntimeTest' --tests '*SmartCompatibilitySocketIntegrationTest' \
  :products:desktop:compileKotlin verifyArchitectureFoundation \
  --max-workers=3 --no-daemon --no-configuration-cache --console=plain
```

## Historical initial implementation evidence (superseded)

The counts in this section are historical reports, not current distinct-output totals. The readiness audit and
completion execution record above supersede them. They do not close outstanding software acceptance requirements.

The dedicated `smartCompatibilityQualification` gate runs architecture verification and affected domain, traffic,
application, storage, data, proxy, simulator, shared UI, Protected Traffic UI, Traffic UI, app UI, companion trust
boundary, and product-composition suites. The Network Conditions gate is repeated for regression coverage.

Executed commands:

```sh
./gradlew smartCompatibilityQualification :products:desktop:compileKotlin --console=plain
./gradlew smartCompatibilityQualification networkConditionsQualification :products:desktop:compileKotlin --console=plain
./gradlew :products:desktop:createDistributable --console=plain
./gradlew smartCompatibilityQualification networkConditionsQualification :products:desktop:createDistributable --console=plain
git diff --check
```

The final combined command passed in **53 seconds**, with **290 actionable tasks** (90 executed, 200 up to date).
The gate module reports contain **954 tests, zero failures, and zero skipped tests**. This is a test result count,
not a statement of 100% line coverage or universal platform qualification.

Focused suites include:

| Suite | Cases | Evidence |
|---|---:|---|
| SmartCompatibilityEligibilityTest | 3 | Only supported rejection is eligible; explicit rules, off/loading states, non-Inspect defaults, invalid scope |
| TlsInspectionFailureClassifierTest | 2 | Supported old/new JDK alert formats, unknown errors, setup boundary, duplicate failure events |
| SmartCompatibilityRuntimeTest | 13 | Scope isolation, precedence, 100 concurrent reconnects, clocks/expiry, bounded keys, setup burst, cooldown, pairing/Forget invalidation, close and expired-CA guard |
| TlsCertificateFlightObserverTest | 6 | Real JSSE TLS 1.2/1.3 successful/failed complete flights, incomplete ClientHello and conservative resumption |
| SmartCompatibilitySocketIntegrationTest | 3 | Actual TLS 1.2, TLS 1.3 and mutual-TLS rejection/reconnect; origin certificate unchanged; one request only; real 100 kbps delivery |
| LegacyPresetRetirementTest | 4 | Enabled-only migration, exact scopes, priority/disabled overlap, CRUD, restart, forged import rejection and explicit edits |
| SmartCompatibilityEvidencePersistenceTest | 1 | Canonical origin/reason/temporary ID survive SQLite restart; legacy null evidence decodes |
| ProtectedTrafficWorkspaceTest | 6 | Initially absent inspector, intermediate animation bounds, row changes, tabs, reduced motion, compact larger text, virtualized 512-entry history, keyboard and focus |
| ProtectedTrafficAdvancedTest | 1 | Actual ViewModel/use-case/screen composition, fallback mutation, absent preset catalogue, imported rules, unavailable Bypass and Overview return |
| ProtectedTrafficViewModelTest | 4 | Existing policy operations plus independent Smart/tab/selection state and disabled-rule identity-preserving editing |

Additional assertions cover default-on 32-to-33 migration, settings-row/no-row upgrade through retained schemas,
saved-off restart, preservation of global actions and the effects of enabled legacy choices, and promotion of observed metadata to the
existing durable rule identity. Existing proxy/simulator suites cover raw relay, half-close, backpressure, cancellation,
aggregate budgets, fault recovery and capture lifecycle. The gate does not imply every platform path was exercised.

Headless Compose renders were inspected at normal and compact dimensions, including 125% font scaling. Generated
captures are under `ui/desktop/protectedTraffic/build/reports/smart-compatibility-ui/`: initial Overview, selected
Overview, compact Overview, and Advanced. They use test data, not captured user traffic.

## Preset retirement follow-up (2026-11-10)

The user accepts an initial inspection failure for learning. The UI and toggle API now expose no service catalogue.
The versioned 33-to-34 migration imports only enabled historical exceptions, preserving selector, ID and lower
priority. Saved rule editing, disable/delete, restart without repopulation, overlapping manual rules, forged import
rejection, and exact group scope are covered by `LegacyPresetRetirementTest` and the Advanced/domain policy tests.
Migration-only historical values and test fixtures remain for upgrades; runtime discovery contains no service list.

Follow-up full qualification and rebuilt bundle: **BUILD SUCCESSFUL**. The combined Smart Compatibility/Network
Conditions gates and distributable task passed in 53 seconds (290 actionable tasks). Current reports contain
954 tests with zero failures/skips. The 949-test result from before retirement is superseded.

## Physical evidence and blockers

### Android

`adb devices -l` returned no attached devices. A Play-enabled physical device, configured companion, test application,
and Billing license-tester workload are still needed. Destination-only Smart behaviour does not complete the
original-flow tuple adapter or per-package qualification tracked by Phase 135.4.

### iPadOS / iOS

`xcrun devicectl list devices` found a paired physical iPad. A filtered installed-app query found no KNet Companion.
The entitled device build was attempted both with existing signing state and provisioning updates enabled:

```sh
xcodebuild -project products/companion/iosApp/KNetCompanion.xcodeproj \
  -scheme 'KNet Companion' -destination 'id=<paired-device>' \
  -derivedDataPath /tmp/knet-smart-ios-device-build build
xcodebuild -project products/companion/iosApp/KNetCompanion.xcodeproj \
  -scheme 'KNet Companion' -destination 'id=<paired-device>' \
  -derivedDataPath /tmp/knet-smart-ios-device-build -allowProvisioningUpdates build
```

Both failed before installation. Xcode reported **No Accounts** and no matching development provisioning profiles
for `com.devuloopers.knet.companion` and `com.devuloopers.knet.companion.PacketTunnel`. A local development signing
identity alone does not supply those profiles. An account and entitled profiles must be configured before device
deployment, VPN routing, independent protected-client and StoreKit qualification can proceed. No device test is
marked passed by compilation or the paired-device inventory.

### macOS and other packet transports

The shared JVM TCP/TLS implementation and product wiring are tested. Packaged-app physical workloads and the signed
transparent macOS Network Extension remain independent Phase 135.6 gates. UDP/QUIC capture, HTTP/3 semantics, WebRTC,
and app attribution retain the Phase 134/135 limits; Smart learning does not add those capabilities.

## Initial implementation checkpoint (superseded by readiness audit)

Final combined automated command: **BUILD SUCCESSFUL**, including architecture checks and desktop compilation.

Desktop distributable: **BUILD SUCCESSFUL**, rebuilt as part of the final 53-second combined gate. The app bundle is generated at
`products/desktop/build/compose/binaries/main/app/KNet.app`. This verifies bundle construction; it is not a claim of
manual real-device or signed transparent-extension qualification.

Phase 136 remains in progress while software matrix closure and independent platform qualification are outstanding. Supported alert classification
is a bounded heuristic: KNet cannot inspect every application's trust store, guarantee first-attempt success, infer
business-operation success from encrypted traffic, or see connections outside its provider's routing path.

See [implementation decisions and full matrix](smart_compatibility_implementation_plan.md) and
[Protected Traffic platform gates](protected_traffic_passthrough_implementation_plan.md).
