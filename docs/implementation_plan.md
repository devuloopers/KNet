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

---

## Phase 35: SSOT-Driven Stable Code Editor & Viewer Architecture `[COMPLETED]`

- [x] **Phase 1: Model SSOT Enums & Prettifiers (`:ui:desktop:httpPanel:model`)** `[COMPLETED]`
  - [x] Added `isPrettifiable: Boolean` and `fun prettify(payload: String): String` to `ResponseBodyMode`
  - [x] Added `isPrettifiable: Boolean` and `fun prettify(payload: String): String` to `RawSubFormat`
  - [x] Enforced complete SSOT for language tokens, placeholders, and formatting logic
- [x] **Phase 2: Zero-Lag Synchronous Initial Lines (`:ui:desktop:codeEditor`)** `[COMPLETED]`
  - [x] Initialized `rawLinesState` synchronously during composition in `ReadOnlyCodeViewer.kt`
  - [x] Kept fold regions calculation off-thread in `LaunchedEffect` without resetting `rawLinesState = null`
  - [x] Eliminated `KNetBodyLoadingPlaceholder` flash during document/format updates
- [x] **Phase 3: Stable Call Sites & DRY Composables (`:ui:desktop:httpPanel`)** `[COMPLETED]`
  - [x] Refactored `ResponseBodyEditor.kt` to use a single continuous `KNetCodeEditor` call site for all text modes (`JSON`, `XML`, `HTML`, `TEXT`, `RAW`)
  - [x] Refactored `RequestBodyEditor.kt` to share a single stable `KNetCodeEditor` call site across `JSON` and `RAW` modes
  - [x] Refactored `SmartBodyViewer.kt` to collapse split text format branches into a unified `KNetCodeEditor` call site
- [x] **Phase 4: Unit Testing & Multi-Module Verification** `[COMPLETED]`
  - [x] Added unit tests for `ResponseBodyMode.prettify` and `RawSubFormat.prettify` in `ResponseEditorPanelTest.kt`
  - [x] Verified 100% passing test suites across all affected modules (`BUILD SUCCESSFUL`)

---

## Phase 36: GraphQL Editor & Viewer SSOT Harmonization `[COMPLETED]`

- [x] **Phase 1: GraphQL Editor SSOT Enums & Prettifiers (`:ui:desktop:httpPanel:model`)** `[COMPLETED]`
  - [x] Enhanced `GraphQlSubTab` with `codeLanguage`, `placeholder`, `getPayload()`, `updatePayload()`, and `prettify()`
- [x] **Phase 2: Stable Single Call Site for GraphQL Editor (`:ui:desktop:httpPanel:editor`)** `[COMPLETED]`
  - [x] Refactored `GraphQlEditor.kt` to use a single continuous `KNetCodeEditor` call site across `Query`, `Variables`, and `Extensions`
- [x] **Phase 3: Stable Single Call Site for GraphQL Viewer (`:ui:desktop:httpPanel:components`)** `[COMPLETED]`
  - [x] Refactored `GraphQLBodyViewer.kt` to use `GraphQLBodySubTab` SSOT and a single stable `KNetCodeEditor` call site
- [x] **Phase 4: Unit Testing & Verification** `[COMPLETED]`
  - [x] Added unit tests for `GraphQlSubTab` operations in `httpPanel:jvmTest`
  - [x] Ran full workspace test suites to verify 100% pass rate (`BUILD SUCCESSFUL`)

---

## Phase 37: Atomic FIFO Interception Session Management `[COMPLETED]`

- [x] **Phase 1: Pure Multiplatform SSOT State Model (`:engine:interceptor`)** `[COMPLETED]`
  - [x] Replaced `ConcurrentHashMap` and dual-state architecture in `InterceptSessionManager.kt` with `MutableStateFlow<List<InterceptedEvent>>`
  - [x] Removed `java.util.concurrent.*` imports
  - [x] Implemented atomic lock-free list mutations using `update { ... }` for `suspendRequest`, `suspendResponse`, `resume`, and `clearSuspensions`
- [x] **Phase 2: Strict FIFO & Concurrent Unit Testing (`:engine:interceptor`)** `[COMPLETED]`
  - [x] Added `testFifoPreservation()` and `testResumeIdempotency()` to `InterceptSessionManagerTest.kt`
  - [x] Verified `InterceptorConcurrencyTest.kt` for race-condition-free atomic resolution
- [x] **Phase 3: Multi-Module Verification** `[COMPLETED]`
  - [x] Ran 100% passing test suite across all 20 active modules (`BUILD SUCCESSFUL in 1m 12s`)

---

## Phase 38: Live Intercept Queue Sidebar & Fluid Animations `[COMPLETED]`

- [x] **Phase 1: ViewModel Multi-Item Selection & Bulk Operations (`:ui:desktop:breakpointManager`)** `[COMPLETED]`
  - [x] Injected `ClearInterceptionSessionsUseCase` into `BreakpointManagerViewModel`
  - [x] Added `selectActiveEvent(eventId: String)` and `dropAllEvents()` to `BreakpointManagerViewModel`
  - [x] Maintained active event selection across dynamic queue updates in `observeActiveInterceptionsUseCase()`
- [x] **Phase 2: Master-Detail Layout & `InterceptQueueSidebar` Component with Animations (`:ui:desktop:breakpointManager`)** `[COMPLETED]`
  - [x] Created `InterceptQueueSidebar.kt` displaying vertically scrollable queue cards with Phase badge, Method, Path, timestamp, and single-item drop action
  - [x] Added `Modifier.animateItem()` to queue cards for fluid entry, reordering, and removal transitions
  - [x] Added `animateDpAsState` for smooth drawer width resizing between `620.dp` and `880.dp`
  - [x] Wrapped queue sidebar in `AnimatedVisibility(visible = events.size > 1, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut())`
  - [x] Added queue position/count indicators to the drawer header bar
  - [x] Wired multi-transaction callbacks in `WorkspaceHost.kt`
- [x] **Phase 3: Unit Testing & Verification** `[COMPLETED]`
  - [x] Added unit tests for selection retention and bulk actions in `BreakpointManagerViewModelTest.kt`
  - [x] Ran changed module test suites (`BUILD SUCCESSFUL in 9s`)

---

## Phase 39: Consistent Queue Sidebar Master-Detail Layout `[COMPLETED]`

- [x] **Phase 1: Stable Master-Detail Layout in LiveInterceptDrawer (`:ui:desktop:breakpointManager`)** `[COMPLETED]`
  - [x] Set fixed width `880.dp` for consistent desktop UI layout
  - [x] Render `InterceptQueueSidebar` whenever `activeEvents.isNotEmpty()` (including single-item queues)
  - [x] Streamline drawer header and remove layout morphing
- [x] **Phase 2: Changed Module Verification (`:ui:desktop:breakpointManager`, `:ui:desktop:app`)** `[COMPLETED]`
  - [x] Ran test suites on changed modules (`BUILD SUCCESSFUL in 3s`)

---

## Phase 40: Specialized Protocol Badges & Clean URI Path `[COMPLETED]`

- [x] **Phase 1: Domain & Data Protocol Inspection (`:core:domain`, `:data:desktop`)** `[COMPLETED]`
  - [x] Added `metadata: InterceptionMetadata` property to `InterceptedTransaction`
  - [x] Evaluated `ProtocolInspectorRegistry` (GraphQL inspector) in `InterceptionSessionRepositoryImpl`
- [x] **Phase 2: UI Specialized Protocol Pills & Clean Path Extraction (`:ui:desktop:breakpointManager`)** `[COMPLETED]`
  - [x] Stripped query parameters from standard HTTP requests in `InterceptQueueSidebar.kt`
  - [x] Formatted GraphQL operation name & type in `InterceptQueueSidebar.kt` (`/graphql • GetUserProfile (Query)`)
  - [x] Rendered `[GQL]`, `[gRPC]`, `[JSON]`, `[FORM]`, `[XML]` pill badges in `InterceptQueueSidebar.kt` and `LiveInterceptDrawer.kt`
- [x] **Phase 3: Testing & Verification** `[COMPLETED]`
  - [x] Added unit tests for GraphQL and HTTP path extraction in `BreakpointManagerViewModelTest.kt` and `InterceptionSessionRepositoryImplTest.kt`
  - [x] Ran changed module test suites (`BUILD SUCCESSFUL in 17s`)

---

## Phase 41: 2-Tier Drawer Header & Reusable EndpointCard with Copy `[COMPLETED]`

- [x] **Phase 1: 2-Tier Header in LiveInterceptDrawer (`:ui:desktop:breakpointManager`)** `[COMPLETED]`
  - [x] Structured header into Tier 1 (Status & Protocol Badges + Close Button) and Tier 2 (`EndpointCard` with full URL and Copy Button)
  - [x] Reused `EndpointCard` from `:ui:desktop:httpPanel` for standardized presentation
  - [x] Updated `InterceptQueueSidebar.kt` to show full URL for standard HTTP
- [x] **Phase 2: Changed Module Verification (`:ui:desktop:breakpointManager`, `:ui:desktop:app`)** `[COMPLETED]`
  - [x] Ran test suites on changed modules (`BUILD SUCCESSFUL in 4s`)

---

## Phase 42: Core Engine Open-Source & Industry-Standard Modernization `[COMPLETED]`

- [x] **Phase 1: Sockets, Lifecycle & Networking Core (`:engine:proxy`, `:engine:portal`)** `[COMPLETED]`
  - [x] Replace per-request unmanaged `CoroutineScope` in `KNetProxyHandler.kt` with a shared lifecycle-managed scope
  - [x] Add streaming response body inspection buffer safeguard (`10MB` cap) in `KNetOutboundHandler`
  - [x] Add graceful Netty `EventLoopGroup` shutdown hooks with quiet periods and timeouts in `KNetProxyServer.kt`
  - [x] Replace `java.util.Base64` with `kotlin.io.encoding.Base64.Mime` in `AppleProfileGenerator.kt`
  - [x] Refactor Java enumeration loops to idiomatic Kotlin collection functions in `MobilePortalHandler.kt`
- [x] **Phase 2: Reactive State & Interception Engine (`:engine:interceptor`, `:engine:traffic`)** `[COMPLETED]`
  - [x] Replace `ConcurrentHashMap` and dual-state in `BreakpointRuleRegistry.kt` with a single atomic `MutableStateFlow`
  - [x] Replace `CopyOnWriteArrayList` in `TrafficModifierManager.kt` with reactive `StateFlow` storing pre-sorted immutable lists
  - [x] Add capacity bounding (`1000` entries) with LRU/thread-safe eviction to `RegexCache.kt`
- [x] **Phase 3: Simulation & Payload Processing (`:engine:simulator`, `:engine:formatter`)** `[COMPLETED]`
  - [x] Modernize `KNetNetworkSimulatorHandler.kt` with `kotlin.time.Duration`
  - [x] Replace `java.util.Base64` in `GrpcWebBodyFormatter.kt` with `kotlin.io.encoding.Base64` and use `buildString`
- [x] **Phase 4: Certificates, Session, Protocol & Scripting (`:engine:certificate`, `:engine:session`, `:engine:protocol`, `:engine:script`)** `[COMPLETED]`
  - [x] Purge wildcard `import java.util.*` and `import java.io.*` across `CertificateAuthority.kt`, `CertificateManagerImpl.kt`, and `TrustStoreInstaller.kt`
  - [x] Modernize `AndroidAdbInstaller.kt` and `IosSimctlInstaller.kt` with `kotlin.time.Duration` and `kotlin.io.encoding.Base64`
  - [x] Replace `SimpleDateFormat`/`Date`/`TimeZone` in `HTTPArchiveExporter.kt` with ISO-8601 UTC Instant formatting and explicit UTF-8 charsets
  - [x] Complete KDoc documentation across protocol decoders and script executors
- [x] **Phase 5: Multi-Module Verification & Zero Regression** `[COMPLETED]`
  - [x] Execute full unit and concurrency stress test suites across all 10 engine modules

---

## Phase 43: Platform-Adaptive System Trust Architecture `[COMPLETED]`

- [x] **Phase 1: Strongly-Typed Host Platform Model (`:core:domain`)** `[COMPLETED]`
  - [x] Add `HostPlatform` enum with detection for macOS, Windows, Linux, Unknown
- [x] **Phase 2: Multiplatform Trust Store Installer & Verifier (`:engine:certificate`)** `[COMPLETED]`
  - [x] Fix `isTrustedMac` and `installMac` in `TrustStoreInstaller.kt` using standard `security find-certificate -a -Z`
  - [x] Fix `isTrustedWindows` and `installWindows` in `TrustStoreInstaller.kt` with proper `certutil -user -store Root` syntax
  - [x] Add `isTrustedLinux` bundle scanner and distribution instruction generator
- [x] **Phase 3: Platform-Adaptive Desktop Certificate UI (`:ui:desktop:certificate`)** `[COMPLETED]`
  - [x] Create `SystemTrustStatusRow.kt` replacing `WindowsTrustStatusRow.kt`
  - [x] Update `CertificateSidebar.kt` to bind `SystemTrustStatusRow` with current `HostPlatform`
  - [x] Refactor `ActiveRootCaCard.kt` to prevent false positive trust badge
- [x] **Phase 4: Full Multi-Module Verification & Regression Testing** `[COMPLETED]`
  - [x] Execute full unit and regression test suites across all modules

---

## Phase 44: Unified SSL & Trust Management Architecture for Netty & Ktor `[COMPLETED]`

- [x] **Phase 1: Unified SSL Trust Provider (`:engine:certificate`)** `[COMPLETED]`
  - [x] Implement `KNetTrustManagerProvider.kt` in `:engine:certificate` providing `X509TrustManager` and `TrustManagerFactory` backed by composite KeyStore
- [x] **Phase 2: Ktor Platform Engine HTTPS Configuration (`:core:http`)** `[COMPLETED]`
  - [x] Add `:engine:certificate` dependency in `core/http/build.gradle.kts` for `jvmMain`
  - [x] Update `HttpClientEngineProvider.kt` and `HttpClientEngineProvider.jvm.kt` with `createPlatformHttpClient`
  - [x] Refactor `KNetApiClient.kt` to use `createPlatformHttpClient`
- [x] **Phase 3: Netty Proxy Engine Unification (`:engine:proxy`)** `[COMPLETED]`
  - [x] Refactor `ProxyTrustManager.kt` to delegate to `KNetTrustManagerProvider`
  - [x] Remove unused `api(project(":core:http"))` from `engine/proxy/build.gradle.kts`
- [x] **Phase 4: Full Verification & Regression Testing (`:core:http`, `:engine:proxy`, `:ui:desktop:apistudio`)** `[COMPLETED]`
  - [x] Add `KNetTrustManagerProviderTest.kt` verifying public CAs, KNet CA, and self-signed bypass
  - [x] Execute `./gradlew test` across all modules

---

## Phase 45: Core Engine Production Readiness & Resilience Hardening `[COMPLETED]`

- [x] **Phase 1: Networking & Timing Accuracy (`:engine:proxy`)** `[COMPLETED]`
  - [x] Fix double `markRequestSent()` invocation in `KNetOutboundHandler.channelActive` to guarantee accurate TTFB calculations
- [x] **Phase 2: Platform Trust Installer Resilience & Leak Prevention (`:engine:certificate`)** `[COMPLETED]`
  - [x] Prevent process pipe deadlock in `TrustStoreInstaller.executeCommand` using merged stream reader
  - [x] Eliminate temp certificate disk leaks in `TrustStoreInstaller.install` via structured try-finally cleanup
  - [x] Use `settings delete global http_proxy` in `AndroidAdbInstaller.clearProxy`
- [x] **Phase 3: Interceptor State Safety & Lifecycle Cleanup (`:engine:interceptor`)** `[COMPLETED]`
  - [x] Eliminate redundant `resume(Drop)` calls in `InterceptCoordinator` request/response finally blocks
  - [x] Replace side-effecting `StateFlow` updates in `BreakpointRuleRegistry` with `updateAndGet` + post-update assignment
- [x] **Phase 4: High-Throughput Regex LRU Caching (`:engine:traffic`)** `[COMPLETED]`
  - [x] Replace naive `cache.clear()` in `RegexCache` with access-order LRU `LinkedHashMap` and thread-safe locking
- [x] **Phase 5: Targeted Database Updates & Payload Persistence (`:storage`, `:engine:session`)** `[COMPLETED]`
  - [x] Add `@Query` `updateResponse` in `HttpTransactionDao` to avoid full row replacement in `TransactionRecorder.recordResponse`
- [x] **Phase 6: Protocol & Simulation Cleanups (`:engine:portal`, `:engine:simulator`)** `[COMPLETED]`
  - [x] Streamline mobile portal fallback routing in `MobilePortalHandler`
  - [x] Remove `addBytesThrottled` rate-unit metric mismatch in `KNetNetworkSimulatorHandler`
- [x] **Phase 7: Full Multi-Module Verification & Zero Regression** `[COMPLETED]`
  - [x] Execute automated unit and regression test suites across all engine and storage modules (132 tasks, 0 failures)

---

## Phase 46: Centralized Pipeline Constants & SSL Exception Safety `[COMPLETED]`

- [x] **Phase 1: Pipeline Handler Names & Constants Architecture (`:engine:proxy`)** `[COMPLETED]`
  - [x] Create `PipelineHandlerNames.kt` in `:engine:proxy:pipeline` with strongly-typed constants and KDoc
- [x] **Phase 2: SSL Handshake Exception Safety & Pipeline Reconfiguration (`:engine:proxy`)** `[COMPLETED]`
  - [x] Wrap `handleConnect` dynamic cert extraction and pipeline mutation in `try/catch` with logging and clean socket closure
  - [x] Replace raw magic strings in `KNetProxyHandler.kt` with `PipelineHandlerNames` constants
  - [x] Replace raw magic strings and buffer sizes in `KNetProxyServer.kt` with `PipelineHandlerNames` constants
- [x] **Phase 3: Interceptor Pipeline Unification (`:engine:interceptor`)** `[COMPLETED]`
  - [x] Replace raw `"ssl"` check in `KNetInterceptorHandler.kt` with `PipelineHandlerNames.SSL`
- [x] **Phase 4: Full Multi-Module Verification & Zero Regression** `[COMPLETED]`
  - [x] Run test suites across `:engine:proxy`, `:engine:interceptor`, and workspace integration tests (118 tasks, 0 failures)

---

## Phase 47: Live Intercept Drawer Smooth Slide-Out Exit Animation `[COMPLETED]`

- [x] **Phase 1: Cached Transition State in LiveInterceptDrawer (`:ui:desktop:breakpointManager`)** `[COMPLETED]`
  - [x] Retain last active event and last events queue to prevent 0x0 empty composition during AnimatedVisibility exit
  - [x] Configure 250ms `FastOutSlowInEasing` animation specs on `slideInHorizontally` and `slideOutHorizontally`
- [x] **Phase 2: Full UI Test Verification & Regression Testing** `[COMPLETED]`
  - [x] Execute tests in `:ui:desktop:breakpointManager`, `:ui:desktop:app`, and `:apps:desktop` (117 tasks, 0 failures)

---

## Phase 48: API Studio Cancellation & Interceptor Auto-Closure Synchronization `[COMPLETED]`

- [x] **Phase 1: Engine & Domain Interception Session Lookup & Drop (`:engine:interceptor`, `:core:domain`, `:data:desktop`)** `[COMPLETED]`
  - [x] Enhance `InterceptSessionManager.resume` to match by both `id` and `request.id`
  - [x] Add `InterceptSessionManager.dropMatching(url, method)`
  - [x] Add `dropMatching(url, method)` to `InterceptionSessionRepository`, `InterceptionSessionRepositoryImpl`, and `DropInterceptedTransactionUseCase`
- [x] **Phase 2: API Studio ViewModel Cancellation & Timeout Coordination (`:ui:desktop:apistudio`)** `[COMPLETED]`
  - [x] Inject `DropInterceptedTransactionUseCase` into `ApiStudioViewModel` and register in `ApiStudioModule`
  - [x] Track `executionJob` and add `cancelExecution()` in `ApiStudioViewModel`
  - [x] Trigger `dropInterceptedTransactionUseCase` on timeout, cancellation, or error in `executeRequest()`
  - [x] Add `onCancelClicked` handling in `RequestUrlBar` and `ApiStudioScreen`
- [x] **Phase 3: Automated Multi-Module Test Suite Verification** `[COMPLETED]`
  - [x] Update `ApiStudioViewModelTest` and `ApiStudioExecutionPipelineE2ETest`
  - [x] Run full test suites across `:ui:desktop:apistudio`, `:data:desktop`, `:engine:interceptor`, and `:apps:desktop` (132 tasks, 0 failures)

---

## Phase 49: Configurable Timeouts with Sec/Min Segmented Toggle `[COMPLETED]`

- [x] **Phase 1: Domain Models & DataStore Mapping (`:core:domain`, `:data:desktop`)** `[COMPLETED]`
  - [x] Add `apiStudioTimeoutSeconds` and `liveInterceptionTimeoutSeconds` to `WorkspaceLayoutSettings`
  - [x] Create `TimeoutUnit` strongly-typed enum (`SECONDS`, `MINUTES`)
  - [x] Add DataStore preferences keys and persistence in `WidgetPreferencesRepositoryImpl`
- [x] **Phase 2: Engine & Client Timeout Synchronization (`:engine:interceptor`, `:data:desktop`)** `[COMPLETED]`
  - [x] Expose `setTimeoutSeconds` on `InterceptCoordinator`
  - [x] Synchronize `InterceptCoordinator.timeoutMs` on settings observation
- [x] **Phase 3: Settings Presentation & UI Implementation (`:ui:desktop:settings`)** `[COMPLETED]`
  - [x] Add timeout values, units, and intents to `SettingsState` and `SettingsIntent`
  - [x] Update `SettingsViewModel` to handle unit conversion and persistence
  - [x] Add Live Interception and API Studio Timeout cards with numeric textfield and `KNetSegmentedButton` in `NetworkProxyTab`
- [x] **Phase 4: Automated Verification & Multi-Module Testing** `[COMPLETED]`
  - [x] Write unit tests in `SettingsViewModelTest`
  - [x] Run full project test suite (132 tasks, 0 failures)

---

## Phase 50: Dynamic Runtime Timeout Synchronization for Netty & Ktor `[COMPLETED]`

- [x] **Phase 1: Ktor HTTP Client Dynamic Timeout Support (`:core:http`)** `[COMPLETED]`
  - [x] Add `updateTimeoutSeconds` and per-request `timeout { ... }` block in `KNetApiClient.kt`
  - [x] Invalidate/refresh cached client instances on configuration updates
- [x] **Phase 2: Data Layer Orchestration Bridge (`:data:desktop`)** `[COMPLETED]`
  - [x] Inject `KNetApiClient` into `WidgetPreferencesRepositoryImpl`
  - [x] Wire `KNetApiClient` injection in `DesktopDataModule.kt`
  - [x] Synchronize `apiClient?.updateTimeoutSeconds(...)` alongside `InterceptCoordinator.setTimeoutSeconds(...)`
- [x] **Phase 3: Automated Verification & Multi-Module Testing** `[COMPLETED]`
  - [x] Add unit test in `KNetApiClientTest`
  - [x] Run full project multi-module test suite (132 tasks, 0 failures)

---

## Phase 51: Data Module Dead Code Cleanup `[COMPLETED]`

- [x] **Phase 1: Remove Obsolete Classes (`:data:desktop`)** `[COMPLETED]`
  - [x] Remove `KNetCoreRepository.kt`
  - [x] Remove `SessionRuntimeRepository.kt`
  - [x] Remove `ProxyHistoryHeaderLookup.kt`
  - [x] Remove `MigrationRegressionTest.kt`
- [x] **Phase 2: DI Configuration & Import Cleanup (`:data:desktop`)** `[COMPLETED]`
  - [x] Remove dead bindings and imports in `DesktopDataModule.kt`
- [x] **Phase 3: Automated Verification** `[COMPLETED]`
  - [x] Run `./gradlew :data:desktop:jvmTest` (all passed)
  - [x] Run full project multi-module test suite (132 tasks, 0 failures)















