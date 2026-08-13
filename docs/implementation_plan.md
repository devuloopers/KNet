# Implementation Plan: Cross-Platform (Android & iOS) Mobile Interception Suite

This document serves as the live project tracking board for implementing seamless HTTPS interception for both **Android** and **iOS** devices, emulators, and simulators in KNet.

---

## Phase Status Tracking

- **Phase 1: Decoupled Portal Engine & Resource Templates (`:engine:portal`)** `[COMPLETED]`
  - [x] Add `:engine:portal` module in `settings.gradle.kts`
  - [x] Create `TemplateLoader.kt` for classpath resource loading with in-memory caching
  - [x] Create `setup_portal.html.template` and `knet-ca.mobileconfig.template` under `src/main/resources/templates/`
  - [x] Refactor `AppleProfileGenerator.kt` in `:engine:portal` using resource template substitution
  - [x] Refactor `PortalHtmlRenderer.kt` in `:engine:portal` using resource template substitution
  - [x] Implement `MobilePortalHandler.kt` in `:engine:portal` intercepting `http://knet.local` and `/setup`
  - [x] Wire `MobilePortalHandler` into `ProxyRuntimeRepository.kt` (`:data:desktop`)
  - [x] Implement Self-Proxy Loop Guard & `/favicon.ico` local handler in `MobilePortalHandler.kt` and `KNetProxyHandler.kt`

- **Phase 2: SSL Passthrough / Bypass Rules & Settings Persistence** `[PENDING]`
  - [ ] Create `SslBypassRule.kt` model with `ANDROID_SYSTEM` and `IOS_SYSTEM` presets in `:core:domain`
  - [ ] Add `sslBypassDomains` to `WorkspaceLayoutSettings.kt` and `WidgetPreferencesRepositoryImpl.kt`
  - [ ] Implement direct TCP passthrough in `KNetProxyHandler.kt` for bypassed domains

- **Phase 3: Automated CLI Integrations for Android (ADB) & iOS (simctl)** `[COMPLETED]`
  - [x] Implement `AndroidAdbInstaller.kt` in `:engine:certificate` for 1-click ADB System CA injection
  - [x] Implement `IosSimctlInstaller.kt` in `:engine:certificate` for 1-click `simctl` keychain injection

- **Phase 4: UI Integration in Certificate Studio & Settings** `[COMPLETED]`
  - [x] Create `MobileSetupWidget.kt` in `:ui:desktop:certificate` with QR code portal & 1-click installer buttons
  - [ ] Add SSL Passthrough Domain Manager UI in `:ui:desktop:settings`

- **Phase 5: Documentation & Verification** `[IN PROGRESS]`
  - [x] Create comprehensive architecture guide in `docs/mobile_interception_guide.md`
  - [x] Run full automated test suite across `:engine:portal`, `:engine:certificate`, `:engine:proxy`, `:ui:desktop:certificate`

---

## Phase 6: Traffic Inspector — Non-Blocking Response Body Rendering `[COMPLETED]`

- [x] Add `KNetBodyLoadingPlaceholder` shimmer skeleton to `:ui:core` for off-thread loading feedback
- [x] Add `preparedBody: PreparedDocument?` and `isPreparing: Boolean` params to `KNetResponseInspector`
- [x] Forward `preparedState.responseBody` and `preparedState.isPreparing` from `TrafficInspectorPanel` into `KNetResponseInspector`
- [x] Decouple `FsmTokenMakerVisualTransformation` from Compose measure thread: pre-tokenize `AnnotatedString` on `Dispatchers.Default` in `LaunchedEffect` inside `ReadOnlyCodeViewer`
- [x] Fix `FoldManager.calculateFolds()` threshold guard: add `respectLineThreshold` param and pass `false` from `DocumentPreparationService` for off-thread callers (fixes missing folds for 5000–10000 line responses)
- [x] Create `LazyReadOnlyBody` virtualized `LazyColumn` renderer for zero UI-thread blocking at any line count (200 to 100,000+ lines)
- [x] Set `LAZY_VIEWER_LINE_THRESHOLD = 200` in `ReadOnlyCodeViewer` — routes all documents above 200 lines to `LazyReadOnlyBody`, keeping `BasicTextField` only for tiny docs with fold support
- [x] Wrap `LazyReadOnlyBody` in top-level `SelectionContainer` for multi-line mouse drag selection and Cmd+C / Ctrl+C support
- [x] All modules compile and pass JVM tests: `BUILD SUCCESSFUL`

---

## Phase 7: Traffic to API Studio Data Transfer — Zero-Data-Loss In-Memory Transfer `[COMPLETED]`

- [x] Implement `getTransactionById(transactionId: String)` in `GetLiveTrafficUseCase`
- [x] Implement `exportToStudioSpec(transactionId: String, onSpecReady: (NetworkRequestSpec) -> Unit)` in `TrafficViewModel` with background body loading and domain mapper delegation
- [x] Refactor `TrafficTable` and `TrafficInspectorPanel` `onSendToApiStudio` callbacks to pass `transactionId` string
- [x] Update `TrafficScreen` to delegate `transactionId` to `viewModel.exportToStudioSpec`
- [x] Update `WorkspaceHost` to forward `NetworkRequestSpec` directly to `apiStudioViewModel.importRequestSpec(spec)`
- [x] Verify Gradle compilation and unit tests across `:ui:desktop:traffic`, `:ui:desktop:apistudio`, and `:ui:desktop:app`: `BUILD SUCCESSFUL`

---

## Phase 8: API Studio — Decoupled Reactive Auto-Save Pipeline `[COMPLETED]`

- [x] Implement `AutoSaveApiSessionUseCase.kt` in `:ui:desktop:apistudio` encapsulating auto-save routing logic for unsaved drafts and saved collection requests
- [x] Register `AutoSaveApiSessionUseCase` in Koin DI (`ApiStudioModule.kt`) and inject into `ApiStudioViewModel`
- [x] Add reactive `triggerAutoSave()` calls across all editor mutation methods in `ApiStudioViewModel` (`updateUrl`, `updateMethod`, `updateHeaders`, `updateBodyState`, `updateBodyPayload`, `updateGraphQlState`, `updateScripts`)
- [x] Clean up `ApiStudioScreen.kt` by delegating action callbacks directly to `viewModel` methods
- [x] Add `AutoSaveApiSessionUseCaseTest.kt` unit test suite verifying persistent storage of GraphQL body modes, queries, operation names, and variables
- [x] All modules compile and pass JVM tests: `BUILD SUCCESSFUL`

---

## Phase 9: Breakpoint Interception & In-Flight Traffic Editing Suite `[IN PROGRESS]`

- [x] **Phase 1: `:ui:desktop:breakpointManager` UI & State** `[COMPLETED]`
  - [x] Created `:ui:desktop:breakpointManager` module registered in `settings.gradle.kts`
  - [x] Implemented `BreakpointRuleUiModel` and `BreakpointManagerState` with strongly-typed `HttpMethod?` and `BreakpointPhase`
  - [x] Built `BreakpointRulesTable` with status switches, monospace URL regex formatting, and method/phase badges
  - [x] Built `AddEditBreakpointRuleDialog` reusing `KNetDialog`, `KNetTextField`, `KNetButton`, and `KNetSwitch`
  - [x] Built `BreakpointManagerScreen` and integrated `Intercepts` into left navigation rail (`Ctrl+3`)
- [x] **Phase 2: Reactive Interceptor Engine & Domain Synchronization** `[COMPLETED]`
  - [x] Upgraded `BreakpointRuleRegistry` in `:engine:interceptor` with `rulesStream` and `isGlobalInterceptionEnabled` `StateFlow` streams
  - [x] Upgraded `InterceptSessionManager` with `activeEventsStream: StateFlow<List<InterceptedEvent>>`
  - [x] Added `if (!isGlobalInterceptionEnabled.value) return null` global interception guard to `BreakpointMatcher`
  - [x] Registered `KNetInterceptorHandler()` into Netty `pipelineInitializers` in `ProxyRuntimeRepository.kt` (`:data:desktop`)
  - [x] Placed all domain rules UseCases (`GetRulesUseCase`, `SaveRuleUseCase`, `DeleteRuleUseCase`, `ToggleRuleUseCase`, `ObserveGlobalInterceptionUseCase`, `ToggleGlobalInterceptionUseCase`) in `:core:domain`
  - [x] Wired `BreakpointManagerViewModel` with domain UseCases and registered in `BreakpointManagerModule.kt` Koin DI
- [x] **Phase 3: `:storage` Room Database Persistence** `[COMPLETED]`
  - [x] Created `BreakpointRuleEntity` and `BreakpointRuleDao` in `:storage` module
  - [x] Updated `KNetDatabase` schema to version 7
  - [x] Updated `RulesRepositoryImpl` in `:data:desktop` to stream Room DB records to `BreakpointRuleRegistry` engine
  - [x] Verified full multi-module JVM test suite across `:storage`, `:data:desktop`, `:engine:interceptor`, `:ui:desktop:breakpointManager`, and `:ui:desktop:app` (`BUILD SUCCESSFUL in 13s`)
- [ ] **Phase 4: 1-Click Right-Click Shortcut in `TrafficTable`** `[PENDING]`
- [ ] **Phase 5: Global 75% Width Right Slide-Over Panel (`InFlightInterceptionSlideOver`)** `[PENDING]`

