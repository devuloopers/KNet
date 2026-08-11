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
