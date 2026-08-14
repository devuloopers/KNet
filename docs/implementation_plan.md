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

## Phase 9: Breakpoint Interception & In-Flight Traffic Editing Suite `[COMPLETED]`

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
- [x] **Phase 4: 1-Click Right-Click Shortcut in `TrafficTable`** `[COMPLETED]`
  - [x] Added "Add Breakpoint Rule" context menu option pre-populating URL regex and method in rule dialog
- [x] **Phase 5: Global Right Slide-Over Drawer (`LiveInterceptDrawer`)** `[COMPLETED]`
  - [x] Built smooth slide-in drawer displaying active in-flight request/response metadata, headers editor, and payload editor with forward and drop controls

---

## Phase 10: Breakpoint Clean Architecture Refactoring & In-Flight Interception Fixes `[COMPLETED]`

- [x] **Phase 1: Domain & Data Layer Clean Architecture (`:core:domain` & `:data:desktop`)** `[COMPLETED]`
  - [x] Create `InterceptedTransaction` pure domain model in `:core:domain`
  - [x] Define `InterceptionSessionRepository` interface in `:core:domain`
  - [x] Create domain UseCases: `ObserveActiveInterceptionsUseCase`, `ForwardInterceptedRequestUseCase`, `ForwardInterceptedResponseUseCase`, `DropInterceptedTransactionUseCase`, `ClearInterceptionSessionsUseCase`
  - [x] Implement `InterceptionSessionRepositoryImpl` in `:data:desktop` bridging domain UseCases to `InterceptSessionManager`
  - [x] Register repository and use cases in `DesktopDataModule.kt` Koin DI
- [x] **Phase 2: Engine & Proxy Pipeline Fixes (`:engine:proxy` & `:engine:interceptor`)** `[COMPLETED]`
  - [x] Fix Netty pipeline handler ordering in `KNetProxyHandler.handleConnect` so `knetInterceptorHandler` is placed after `httpAggregator` on HTTPS
  - [x] Preserve `isIntercepted = true`, `matchedRuleId`, and `id` from `ChannelAttributes.REQUEST_ATTR` in `KNetProxyHandler.handleRequest`
- [x] **Phase 3: Presentation Layer Decoupling & UI Highlighting (`:ui:desktop:breakpointManager`, `:ui:desktop:traffic`, `:ui:desktop:app`)** `[COMPLETED]`
  - [x] Remove `:engine:interceptor` dependency from `ui/desktop/breakpointManager/build.gradle.kts`
  - [x] Refactor `BreakpointManagerViewModel` to inject domain UseCases exclusively
  - [x] Refactor `LiveInterceptDrawer` and `BreakpointManagerState` to use domain `InterceptedTransaction`
  - [x] Inject `GetRulesUseCase` in `TrafficViewModel` to stream active rules
  - [x] Update `TrafficTable` row highlighting for intercepted transactions and active rule presence
- [x] **Phase 4: Verification & Automated Tests** `[COMPLETED]`
  - [x] Update and execute test suites across `:core:domain`, `:data:desktop`, `:engine:proxy`, `:engine:interceptor`, `:ui:desktop:breakpointManager`, `:ui:desktop:traffic`, and `:ui:desktop:app` (`BUILD SUCCESSFUL in 32s`)

---

## Phase 11: HTTPS Interception Fix, Netty-Driven Highlighting & Diagnostic Logging `[COMPLETED]`

- [x] **Phase 1: HTTPS Request Mapping & Interceptor Bypass** `[COMPLETED]`
  - [x] Bypass `CONNECT` handshakes in `KNetInterceptorHandler` without setting stale channel attributes
  - [x] Map every inbound HTTP/HTTPS request freshly from `FullHttpRequest` with body decoding
  - [x] Re-evaluate `BreakpointMatcher` against decoded body (JSON/GraphQL) and URL criteria
- [x] **Phase 2: Strict Netty-Driven Highlighting in `TrafficTable`** `[COMPLETED]`
  - [x] Remove client-side `activeBreakpointRules.any { ... }` fallback from `TrafficTable.kt`
  - [x] Enforce `isMatchedByBreakpoint = item.isIntercepted` directly from Room DB / Netty SSOT
- [x] **Phase 3: Diagnostic Logging Instrumentation** `[COMPLETED]`
  - [x] Add structured `KNetLogger` logging across `KNetInterceptorHandler`, `BreakpointMatcher`, and `InterceptCoordinator`
- [x] **Phase 4: Multi-Module Verification** `[COMPLETED]`
  - [x] All unit and integration test suites pass across all layers (`BUILD SUCCESSFUL in 17s`)

---

## Phase 12: In-Progress Interception Capture & Action-Driven Lifecycle `[COMPLETED]`

- [x] **Phase 1: Domain & Listener Contracts (`:core:domain`)** `[COMPLETED]`
  - [x] Added `onTransactionDropped(transactionId: String, reason: String)` to `ProxyTrafficListener`
- [x] **Phase 2: Data Layer Persistence (`:data:desktop`)** `[COMPLETED]`
  - [x] Implemented `onTransactionDropped` in `ProxyEngineRepositoryImpl` to update Room DB record with `responseStatusText` and `responseStatusCode = 0`
  - [x] Wired `ProxyRuntimeRepository` to pass `trafficListener` into `KNetInterceptorHandler`
- [x] **Phase 3: Engine Immediate Capture & Drop Dispatch (`:engine:interceptor`)** `[COMPLETED]`
  - [x] Instantly notify `listener?.onRequestCaptured(taggedRequest)` on breakpoint match to display row in Traffic Table while paused
  - [x] Dispatch `listener?.onTransactionDropped` on drop and timeout in `InterceptCoordinator`
- [x] **Phase 4: UI Status Rendering (`:ui:desktop:traffic`)** `[COMPLETED]`
  - [x] Render `In Progress` in orange, `Dropped` in red, `Timed Out` in muted text, and status code (e.g. `200`) in green
- [x] **Phase 5: Multi-Module Verification** `[COMPLETED]`
  - [x] Automated unit and integration tests passing across all layers (`BUILD SUCCESSFUL`)

---

## Phase 13: Dedicated GraphQL Request Formatter & Single-Level Sub-Tabs `[COMPLETED]`

- [x] **Phase 1: Dedicated GraphQL Request Viewer (`:ui:desktop:httpPanel`)** `[COMPLETED]`
  - [x] Created `GraphQLRequestBodyViewer.kt` featuring:
    - Flat single-level sub-tabs (`Query`, `Variables`, `Raw JSON`, `Raw`) using `KNetTab`
    - Operation details badge (`[GQL: QUERY/MUTATION] <operationName>`)
    - AST-formatted GraphQL query display with `KNetCodeEditor(languageHint = "graphql")`
    - JSON-formatted variables display with `KNetCodeEditor(languageHint = "json")`
- [x] **Phase 2: Request Inspector Integration & Loading State (`:ui:desktop:httpPanel`)** `[COMPLETED]`
  - [x] Updated `KNetRequestInspector.kt` to auto-detect GraphQL payloads and render `GraphQLRequestBodyViewer` in `InspectorSubTab.BODY`
  - [x] Added `isPreparing` loading placeholder support while reading payloads from disk
- [x] **Phase 3: Traffic Inspector Panel Payload Alignment (`:ui:desktop:traffic`)** `[COMPLETED]`
  - [x] Updated `TrafficInspectorPanel.kt` to pass pristine raw wire payload and `isPreparing` state to `KNetRequestInspector`
  - [x] Updated `TrafficViewModel.kt` to use `BodyFormatterRegistry` for syntax detection and off-thread document preparation
- [x] **Phase 4: Multi-Module Verification & Automated Tests** `[COMPLETED]`
  - [x] Added `GraphQLRequestBodyViewerTest.kt` in `:ui:desktop:httpPanel`
  - [x] Verified 100% test pass rate across `:core:domain`, `:engine:formatter`, `:engine:protocol`, `:ui:desktop:codeEditor`, `:ui:desktop:httpPanel`, `:ui:desktop:traffic`, and `:ui:desktop:app`

---

## Phase 14: Strongly-Typed Multi-Format Body Inspection Architecture `[COMPLETED]`

- [x] **Phase 1: Strongly-Typed Language Contracts (`:ui:desktop:codeEditor`)** `[COMPLETED]`
  - [x] Created `CodeLanguage` enum (`JSON`, `GRAPHQL`, `XML`, `HTML`, `JAVASCRIPT`, `CSS`, `PLAIN`)
  - [x] Updated `CodeHighlighterRegistry` and `KNetCodeEditor` to accept strongly-typed `CodeLanguage`
- [x] **Phase 2: Strongly-Typed Body Inspection & Polymorphic Viewers (`:ui:desktop:httpPanel`)** `[COMPLETED]`
  - [x] Created `BodyInspectionSpec` DTO (`headers`, `rawBody`, `resolvedFormat`, `isPreparing`)
  - [x] Created `FormDataViewer` for structured URL-encoded and multipart parameter display
  - [x] Created `SmartBodyViewer` polymorphic dispatcher covering all 15 formats in `BodyFormat`
- [x] **Phase 3: Standardizing Request & Response Panels (`:ui:desktop:httpPanel`)** `[COMPLETED]`
  - [x] Updated `KNetRequestInspector` and `KNetResponseInspector` to delegate directly to `SmartBodyViewer`
  - [x] Standardized `RequestViewPanel` and `ResponseViewPanel` composables
- [x] **Phase 4: Multi-Module Verification & Automated Tests** `[COMPLETED]`
  - [x] Added `SmartBodyViewerTest.kt` in `:ui:desktop:httpPanel`
  - [x] 100% passing tests across `:core:domain`, `:engine:formatter`, `:engine:protocol`, `:ui:desktop:codeEditor`, `:ui:desktop:httpPanel`, `:ui:desktop:traffic`, and `:ui:desktop:app`

---

## Phase 15: Canonical Restructuring of `:ui:desktop:httpPanel` (`viewpanels`, `editor`, `components`) `[COMPLETED]`

- [x] **Phase 1: Standardized `viewpanels/` Read-Only Facades** `[COMPLETED]`
  - [x] Created `RequestViewPanel.kt`, `ResponseViewPanel.kt`, `OverviewViewPanel.kt`, `TimelineViewPanel.kt`
- [x] **Phase 2: Standardized `editor/` Interactive Editors** `[COMPLETED]`
  - [x] Created `RequestEditorPanel.kt`, `BodyEditor.kt`, `AuthEditor.kt`, `ScriptEditor.kt`, `GraphQlEditor.kt`
- [x] **Phase 3: Consolidated `components/` Format Viewers & Micro-Widgets** `[COMPLETED]`
  - [x] Placed `SmartBodyViewer.kt`, `GraphQLBodyViewer.kt`, and `FormDataViewer.kt` directly in `components/`
  - [x] Standardized `NetworkErrorCard.kt`, `RequestSummaryHeader.kt`, `ResponseSummaryHeader.kt`, `InspectorSubTabRow.kt`, `EndpointCard.kt`
- [x] **Phase 4: Eliminated Legacy `view/` and `viewers/` Redundant Packages** `[COMPLETED]`
  - [x] Migrated `ApiStudio` and `BreakpointManager` to import directly from `viewpanels/` and `editor/`
  - [x] Completely removed `view/` and `viewers/` directories
- [x] **Phase 5: Multi-Module Verification & Zero Regression** `[COMPLETED]`
  - [x] 100% passing test suite across `:core:domain`, `:engine:formatter`, `:engine:protocol`, `:ui:desktop:codeEditor`, `:ui:desktop:httpPanel`, `:ui:desktop:traffic`, `:ui:desktop:breakpointManager`, `:ui:desktop:apistudio`, and `:ui:desktop:app`

---

## Phase 16: Dead Code & Unused Function Elimination `[COMPLETED]`

- [x] **Phase 1: Direct Consumer Migration** `[COMPLETED]`
  - [x] Migrated `ApiStudioScreen.kt` to use `RequestEditorPanel` and `RequestEditorPanelActions` directly
- [x] **Phase 2: Purged 11 Unused Backward-Compatibility Functions & Overloads** `[COMPLETED]`
  - [x] Removed `KNetRequestInspector` from `RequestViewPanel.kt`
  - [x] Removed `KNetResponseInspector` from `ResponseViewPanel.kt`
  - [x] Removed `KNetOverviewInspector` from `OverviewViewPanel.kt`
  - [x] Removed `KNetTimelineInspector` from `TimelineViewPanel.kt`
  - [x] Removed `KNetRequestEditor` and `KNetRequestEditorActions` from `RequestEditorPanel.kt`
  - [x] Removed `BodyEditorView` and loose-lambda `BodyEditor(...)` overload from `BodyEditor.kt`
  - [x] Removed `AuthEditorView` from `AuthEditor.kt`
  - [x] Removed `ScriptEditorView` from `ScriptEditor.kt`
  - [x] Removed `GraphQlBodyEditor` from `GraphQlEditor.kt`
  - [x] Removed `GraphQLRequestBodyViewer` from `GraphQLBodyViewer.kt`
  - [x] Removed `NetworkExecutionErrorCard` from `NetworkErrorCard.kt`
- [x] **Phase 3: Deleted Orphaned / Dead Files** `[COMPLETED]`
  - [x] Deleted empty `model/ErrorDetails.kt` file
- [x] **Phase 4: Multi-Module Test Suite & Warning Free Verification** `[COMPLETED]`
  - [x] Removed unused `bodyPayload` parameter from `RequestEditorPanel` and updated consumers (`ApiStudioScreen.kt`, `LiveInterceptDrawer.kt`)
  - [x] Made `when (localActiveTab)` exhaustive in `RequestEditorPanel.kt` without redundant `else` branch
  - [x] 100% passing test suite across all 8 modules (115 actionable tasks verified with 0 warnings)

---

## Phase 17: Restored Original Timeline & Overview UI Styling `[COMPLETED]`

- [x] **Phase 1: Horizontal Waterfall Timeline Restoration** `[COMPLETED]`
  - [x] Restored horizontal waterfall layout (`[Label 130dp] [18dp Bar] [Duration 64dp]`) in `TimelineViewPanel.kt`
  - [x] Restored `"Reused Connection"` pill badge in top-right header
  - [x] Restored `"Total Latency"` summary footer with accent styling
  - [x] Restored Catppuccin color palette (`Color(0xFF89B4FA)`, `Color(0xFF89DCEB)`, `Color(0xFFA6E3A1)`, `Color(0xFFF9E2AF)`, `Color(0xFF74C7EC)`)
- [x] **Phase 2: Semantic Theme Overview Restoration** `[COMPLETED]`
  - [x] Restored Title-Case clean labels (`"Status"`, `"Protocol"`, `"Remote IP"`, `"Time"`, `"Duration"`, `"Size"`, `"Type"`, `"Error Detail"`) in `OverviewViewPanel.kt`
  - [x] Bound status colors directly to semantic theme tokens (`themeColors.semantic.success`, `warning`, `error`) without hardcoded hexes
- [x] **Phase 3: Multi-Module Verification** `[COMPLETED]`
  - [x] 100% passing test suite across all modules (115 actionable tasks verified)

---

## Phase 18: Design System Tab Row Harmonization `[COMPLETED]`

- [x] **Phase 1: TrafficInspectorPanel Navigation Harmonization** `[COMPLETED]`
  - [x] Replaced custom local `InspectorTabButton` in `TrafficInspectorPanel.kt` with Design System `ScrollableTabRow` and `KNetTab` (`Overview`, `Request`, `Response`, `Timeline`)
  - [x] Cleaned up dead local `InspectorTabButton` function
- [x] **Phase 2: Multi-Module Verification** `[COMPLETED]`
  - [x] 100% passing test suite across all 8 modules (115 actionable tasks verified)

---

## Phase 19: Automatic BodyState Detection & Breakpoint Hydration `[COMPLETED]`

- [x] **Phase 1: Canonical BodyState.fromPayload in `:ui:desktop:httpPanel`** `[COMPLETED]`
  - [x] Added `BodyState.Companion.fromPayload(headers, rawBody)` leveraging `BodyFormatterRegistry` and `GraphQlPayloadMapper`
  - [x] Auto-detects and hydrates `BodyMode.GRAPHQL` with structured `GraphQlState` (`Query`, `Variables`, `Extensions`)
  - [x] Auto-detects and populates `BodyMode.FORM_DATA`, `BodyMode.JSON`, and `BodyMode.RAW` (`XML`, `HTML`, `JS`, `TEXT`)
  - [x] Added unit test suite `BodyStateFromPayloadTest.kt` with 100% test coverage
- [x] **Phase 2: LiveInterceptDrawer Dynamic Hydration** `[COMPLETED]`
  - [x] Migrated `LiveInterceptDrawer.kt` from hardcoded `BodyMode.JSON` to `BodyState.fromPayload`
  - [x] Bound reactive `bodyState` mutations (`onBodyStateChanged`) to `LiveInterceptDrawer` and forwarded encoded payload
- [x] **Phase 3: Multi-Module Verification** `[COMPLETED]`
  - [x] 100% passing test suite across all 8 modules (115 actionable tasks verified)

---

## Phase 20: Unified Format Detection via BodyModels SSOT `[COMPLETED]`

- [x] **Phase 1: BodyInspectionSpec SSOT in `:ui:desktop:httpPanel:model`** `[COMPLETED]`
  - [x] Added `codeLanguage: CodeLanguage` and `formattedText: String` computed properties to `BodyInspectionSpec`
  - [x] Added `BodyInspectionSpec.Companion.fromPayload(headers, rawBody, isPreparing)` factory
  - [x] Added unit test suite `BodyInspectionSpecTest.kt` with 100% test coverage
- [x] **Phase 2: TrafficViewModel SSOT Migration** `[COMPLETED]`
  - [x] Removed direct calls to `BodyFormatterRegistry` and eliminated dead private `detectLanguage` and `formatPayload` string methods
  - [x] Migrated off-thread document preparation to `BodyInspectionSpec.fromPayload()`
- [x] **Phase 3: ApiStudioViewModel SSOT Migration** `[COMPLETED]`
  - [x] Migrated `importRequestSpec()` to `BodyState.fromPayload()`
  - [x] Deleted dead private `toBodyMode()` converter method
- [x] **Phase 4: Multi-Module Verification** `[COMPLETED]`
  - [x] 100% passing test suite across all 8 modules (115 actionable tasks verified)

---

## Phase 21: Complete API Studio BodyModels SSOT Alignment `[COMPLETED]`

- [x] **Phase 1: Sidebar Session Loading via BodyState.fromPayload** `[COMPLETED]`
  - [x] Migrated `ApiStudioScreen.kt` from loose string updates (`updateBodyPayload`, `updateBodyType`) to `viewModel.updateBodyState(BodyState.fromPayload(headers, bodyPayload))`
  - [x] Automatically hydrates GraphQL, JSON, Form-Data, and Raw states upon selecting any saved collection request or unsaved draft
- [x] **Phase 2: Strongly-Typed Enum APIs in ApiStudioViewModel & ApiStudioScreen** `[COMPLETED]`
  - [x] Added `fun updateBodyMode(mode: BodyMode)` directly on `ApiStudioViewModel`
  - [x] Eliminated private `String.toBodyMode()` extension mapper and deleted primitive string `updateBodyType`
  - [x] Strongly typed `scriptLanguage: ScriptLanguage` in `RequestEditorState`, `ApiStudioViewModel`, and `ApiStudioScreen`
  - [x] Eliminated primitive string overloads for `updateActiveSubTab`, `updateActiveScriptPhase`, and `updateActiveResponseSubTab`
- [x] **Phase 3: Multi-Module Verification** `[COMPLETED]`
  - [x] Added unit tests in `ApiStudioViewModelTest.kt` verifying `updateBodyMode`, `updateScriptLanguage`, and GraphQL payload hydration
  - [x] 100% passing test suite across all 8 modules (115 actionable tasks verified)

---

## Phase 22: Complete Encapsulation of `:ui:desktop:codeEditor` Inside `:ui:desktop:httpPanel` `[COMPLETED]`

- [x] **Phase 1: `:ui:desktop:httpPanel` Facade & SSOT Bridge** `[COMPLETED]`
  - [x] Updated `httpPanel/build.gradle.kts` to expose `api(project(":ui:desktop:codeEditor"))`
  - [x] Added `prepareDocument()` member on `BodyInspectionSpec` and exported `typealias PreparedDocument` in `:ui:desktop:httpPanel:model`
- [x] **Phase 2: Eliminate `:ui:desktop:codeEditor` Coupling in `:ui:desktop:traffic`** `[COMPLETED]`
  - [x] Removed `project(":ui:desktop:codeEditor")` from `traffic/build.gradle.kts`
  - [x] Updated `InspectorPreparedState.kt` and `TrafficViewModel.kt` to use `httpPanel` models and `prepareDocument()`
- [x] **Phase 3: Eliminate `:ui:desktop:codeEditor` Coupling in `:ui:desktop:apiStudio`** `[COMPLETED]`
  - [x] Removed `project(":ui:desktop:codeEditor")` from `apistudio/build.gradle.kts`
  - [x] Removed unused `EditorMode` import in `ResponseInspectorView.kt`
- [x] **Phase 4: Migrate `:ui:desktop:scripting` to `:ui:desktop:httpPanel`** `[COMPLETED]`
  - [x] Replaced `project(":ui:desktop:codeEditor")` with `project(":ui:desktop:httpPanel")` in `scripting/build.gradle.kts`
  - [x] Updated `ScriptEditor.kt` to use `httpPanel` ScriptEditor facade
- [x] **Phase 5: Multi-Module Verification & Zero Leak Audit** `[COMPLETED]`
  - [x] Verified 100% pass across all modules via `./gradlew jvmTest`
  - [x] Grep verified zero external imports of `com.devuloopers.knet.ui.desktop.codeeditor` outside `:ui:desktop:httpPanel`

---

## Phase 23: Complete Internalization of Document Preparation Inside `:ui:desktop:codeEditor` `[COMPLETED]`

- [x] **Phase 1: `:ui:desktop:httpPanel` Cleanups** `[COMPLETED]`
  - [x] Removed `prepareDocument()` and `typealias PreparedDocument` from `BodyInspectionSpec.kt`
  - [x] Removed `testPrepareDocumentCreatesValidPreparedDocument` from `BodyInspectionSpecTest.kt`
  - [x] Removed `preparedBody: PreparedDocument?` parameter from `ResponseViewPanel.kt` and used `SmartBodyViewer` uniformly
- [x] **Phase 2: `:ui:desktop:traffic` Cleanups** `[COMPLETED]`
  - [x] Simplified `InspectorPreparedState.kt` to store raw strings (`requestBodyText`, `responseBodyText`, `isPreparing`)
  - [x] Updated `TrafficViewModel.kt` to remove document preparation calls
  - [x] Updated `TrafficInspectorPanel.kt` to omit `preparedBody` parameter
- [x] **Phase 3: `:ui:desktop:codeEditor` Internalization** `[COMPLETED]`
  - [x] Marked `DocumentPreparationService` as `internal`
- [x] **Phase 4: Multi-Module Verification** `[COMPLETED]`
  - [x] Verified 100% pass across all modules via `./gradlew jvmTest`

---

## Phase 24: GraphQL Real-Time Operation Name & Query Synchronization `[COMPLETED]`

- [x] **Phase 1: Engine Synchronizer Component (`:engine:formatter`)** `[COMPLETED]`
  - [x] Implemented `GraphQLQuerySynchronizer.kt` with AST and regex fallback handling
  - [x] Added comprehensive unit tests in `GraphQLQuerySynchronizerTest.kt`
- [x] **Phase 2: UI Real-Time Two-Way Sync (`:ui:desktop:httpPanel`)** `[COMPLETED]`
  - [x] Connected `Op Name` field to `GraphQLQuerySynchronizer.updateOperationName` in `GraphQlEditor.kt`
  - [x] Connected Query Code Editor to `GraphQLQuerySynchronizer.extractOperationName` in `GraphQlEditor.kt`
- [x] **Phase 3: Multi-Module Verification** `[COMPLETED]`
  - [x] Verified 100% pass across all modules via `./gradlew jvmTest`

---

## Phase 25: Eliminate LazyColumn Line Flash via Synchronous State Alignment `[COMPLETED]`

- [x] **Phase 1: Synchronous State Alignment in `EditableLineContent`** `[COMPLETED]`
  - [x] Replaced `LaunchedEffect(lineText)` with immediate synchronous state alignment during composition
- [x] **Phase 2: Surgical Single-Line Buffer Mutations in `EditableCodeEditor`** `[COMPLETED]`
  - [x] Added single-line diff detection and `documentBuffer.setLine` in `LaunchedEffect(code)`
- [x] **Phase 3: Multi-Module Verification** `[COMPLETED]`
  - [x] Verified 100% pass across all modules via `./gradlew jvmTest`

---

## Phase 26: `ResponseEditorPanel` Implementation & Live Intercept Integration `[COMPLETED]`

- [x] **Phase 1: `ResponseEditorPanel` Component (`:ui:desktop:httpPanel`)** `[COMPLETED]`
  - [x] Implemented `ResponseEditorPanel.kt` and `ResponseEditorPanelActions` in `:ui:desktop:httpPanel:editor`
  - [x] Added unit tests in `ResponseEditorPanelTest.kt`
- [x] **Phase 2: Live Intercept Drawer Integration (`:ui:desktop:breakpointManager`)** `[COMPLETED]`
  - [x] Wired `ResponseEditorPanel` into `LiveInterceptDrawer.kt` for `BreakpointPhase.RESPONSE`
  - [x] Updated `onForwardResponse` to forward modified status code, headers, and body payload
- [x] **Phase 3: Multi-Module Verification** `[COMPLETED]`
  - [x] Verified 100% pass across all modules via `./gradlew jvmTest`

---

## Phase 27: Request & Response Phase Indicators in Live Intercept Drawer `[COMPLETED]`

- [x] **Phase 1: Header Phase Badges & Dynamic Action Buttons (`:ui:desktop:breakpointManager`)** `[COMPLETED]`
  - [x] Added explicit `[REQUEST INTERCEPT]` / `[RESPONSE INTERCEPT]` badges to `LiveInterceptDrawer.kt`
  - [x] Added dynamic `FORWARD REQUEST` / `FORWARD RESPONSE` action button labels
- [x] **Phase 2: Multi-Module Verification** `[COMPLETED]`
  - [x] Verified 100% pass across all modules via `./gradlew jvmTest`

---

## Phase 28: Enable In-Flight HTTP Response Interception in Netty Pipeline `[COMPLETED]`

- [x] **Phase 1: Outbound Pipeline Aggregation (`:engine:proxy`)** `[COMPLETED]`
  - [x] Added `HttpObjectAggregator` to outbound `ChannelInitializer` in `KNetProxyHandler.kt`
  - [x] Supported `FullHttpResponse` in `KNetOutboundHandler`
- [x] **Phase 2: Multi-Module Verification** `[COMPLETED]`
  - [x] Verified 100% pass across all modules via `./gradlew jvmTest`

---

## Phase 29: Automatic Body Decompression in Live Intercept Drawer `[COMPLETED]`

- [x] **Phase 1: Body Decompression & Forwarding Header Sanitization (`:ui:desktop:breakpointManager`)** `[COMPLETED]`
  - [x] Used `decodeBodyToText` for request and response body loading in `LiveInterceptDrawer.kt`
  - [x] Stripped `Content-Encoding` when forwarding modified uncompressed payloads
- [x] **Phase 2: Multi-Module Verification** `[COMPLETED]`
  - [x] Verified 100% pass across all modules via `./gradlew jvmTest`

---

## Phase 30: Synchronize Traffic Table In-Progress State with Live Interception `[COMPLETED]`

- [x] **Phase 1: InterceptCoordinator Post-Resume Response Persistence (`:engine:interceptor`)** `[COMPLETED]`
  - [x] Fired `listener?.onResponseCaptured` upon `InterceptResult.Resume` in `coordinateResponse()`
- [x] **Phase 2: KNetOutboundHandler Interception Awareness (`:engine:proxy`)** `[COMPLETED]`
  - [x] Deferred `listener?.onResponseCaptured` when response matches an active breakpoint rule
- [x] **Phase 3: Multi-Module Verification** `[COMPLETED]`
  - [x] Verified 100% pass across all modules via `./gradlew jvmTest`

---

## Phase 31: Auto Pretty-Printing in Response/Request Editors `[COMPLETED]`

- [x] **Phase 1: BodyState Auto-Formatted Hydration (`:ui:desktop:httpPanel`)** `[COMPLETED]`
  - [x] Set `payloadText = resolvedFormat.formattedText` for JSON, XML, HTML, and JS in `BodyState.fromPayload()`
- [x] **Phase 2: Unit Testing & Multi-Module Verification** `[COMPLETED]`
  - [x] Updated `BodyStateFromPayloadTest` and `ApiStudioViewModelTest` assertions
  - [x] Verified 100% pass across all modules via `./gradlew jvmTest`

---

## Phase 32: Dedicated Response Body Editor Tabs `[COMPLETED]`

- [x] **Phase 1: ResponseBodyMode Model & Hydration (`:ui:desktop:httpPanel`)** `[COMPLETED]`
  - [x] Added `ResponseBodyMode` enum and attached to `BodyState`
- [x] **Phase 2: ResponseBodyEditor Composable Implementation (`:ui:desktop:httpPanel`)** `[COMPLETED]`
  - [x] Implemented `ResponseBodyEditor` with 6-tab pill row and auto syntax highlighting
  - [x] Integrated into `ResponseEditorPanel.kt`
- [x] **Phase 3: Unit Testing & Multi-Module Verification** `[COMPLETED]`
  - [x] Added `ResponseBodyMode` tests in `ResponseEditorPanelTest.kt`
  - [x] Verified 100% pass across all modules via `./gradlew jvmTest`

---

## Phase 33: Direct Refactoring to Symmetrical Request & Response Body Models `[COMPLETED]`

- [x] **Phase 1: RequestBodyMode & BodyState Factory Methods (`:ui:desktop:httpPanel`)** `[COMPLETED]`
  - [x] Replaced `BodyMode` with `RequestBodyMode` in `BodyModels.kt`
  - [x] Added `fromRequestPayload` and `fromResponsePayload` factory methods
- [x] **Phase 2: RequestBodyEditor & Panel Integration (`:ui:desktop:httpPanel`)** `[COMPLETED]`
  - [x] Created `RequestBodyEditor.kt` and deleted `BodyEditor.kt`
  - [x] Updated `RequestEditorPanel.kt` and `SyncBodyStateUseCase.kt`
- [x] **Phase 3: Consumer Integration (`:ui:desktop:apistudio`, `:ui:desktop:breakpointManager`)** `[COMPLETED]`
  - [x] Updated `ApiStudioViewModel.kt` and `RequestEditorState.kt`
  - [x] Updated `LiveInterceptDrawer.kt`
- [x] **Phase 4: Unit Testing & Multi-Module Verification** `[COMPLETED]`
  - [x] Updated tests in `httpPanel` and `apistudio`
  - [x] Verified 100% pass across all modules via `./gradlew jvmTest`

---

## Phase 34: Distinct RequestBodyState & ResponseBodyState Models `[COMPLETED]`

- [x] **Phase 1: RequestBodyState & ResponseBodyState Models (`:ui:desktop:httpPanel`)** `[COMPLETED]`
  - [x] Colocate `RequestBodyState` and `ResponseBodyState` in `BodyModels.kt`
- [x] **Phase 2: UI Editors & Panel Alignment (`:ui:desktop:httpPanel`)** `[COMPLETED]`
  - [x] Update `RequestBodyEditor`, `ResponseBodyEditor`, `RequestEditorPanel`, `ResponseEditorPanel`, and `SyncBodyStateUseCase`
- [x] **Phase 3: Consumer Integration (`:ui:desktop:apistudio`, `:ui:desktop:breakpointManager`)** `[COMPLETED]`
  - [x] Update `ApiStudioViewModel`, `RequestEditorState`, `ApiStudioScreen`, and `LiveInterceptDrawer`
- [x] **Phase 4: Unit Testing & Multi-Module Verification** `[COMPLETED]`
  - [x] Update tests in `httpPanel` and `apistudio`
  - [x] Verify 100% pass across all modules via `./gradlew jvmTest`













