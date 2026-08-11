# KNet Request & Response Architecture Refactoring Plan

This document serves as the live architectural roadmap for unifying Request/Response domain specifications, standardizing shared UI components, bridging Traffic Feed to API Studio, and delivering live in-flight interception editing.

---

## Architectural Objectives

1. **Strongly-Typed Domain Policy**: Replace loose strings and non-standard maps with strongly-typed `NetworkRequestSpec` and `NetworkResponseSpec` domain contracts in `:core:domain`.
2. **Unified UI Primitives**: Share a single standard Request Editor (`KNetRequestEditorView`) and Response Inspector (`KNetResponseInspector`) component across Live Traffic and API Studio without adding nested ViewModels (Option B: Parent ViewModels emit `NetworkResponseSpec` / `ResponseInspectorState` directly into `KNetResponseInspector`).
3. **1-Click API Studio Bridge**: Seamlessly convert captured traffic items into active API Studio draft tabs via `ImportRequestToStudioUseCase`.
4. **Real-Time Breakpoint Interceptor**: Intercept in-flight Netty proxy requests, allow dynamic editing of headers/body, and resume transport.

---

## Phase Status Tracking

### Phase 1: Data Class Unification with Strongly-Typed Contracts `[COMPLETED]`
- [x] Create `NetworkRequestSpec` data class in `:core:domain` with strongly-typed `HttpMethod` enum, `List<Pair<String, String>>` headers, `List<Pair<String, String>>` queryParams, `bodyPayload`, `bodyType` (`RequestBodyType` enum), `cookies`, and `AuthState`.
- [x] Create `NetworkResponseSpec` data class in `:core:domain` with strongly-typed status code, status text, `List<Pair<String, String>>` headers, `bodyPayload`, duration metrics, size metrics, and `NetworkFailureReason`.
- [x] Implement `HttpTransaction.toNetworkRequestSpec()` and `HttpTransaction.toNetworkResponseSpec()` mapping extensions in `:core:domain`.
- [x] Implement `NetworkRequestSpec.toSavedApiRequest()` and `SavedApiRequest.toNetworkRequestSpec()` converters in `:core:domain`.
- [x] Write unit tests verifying zero data loss during bidirectional conversions (`NetworkSpecMappersTest`).

### Phase 2: UI Component Unification & Reusable Empty State Placeholders `[COMPLETED]`
- [x] Create `KNetResponseInspector` composable based on API Studio's rich response layout (status badges, timing/size metrics, syntax-highlighted code editor, headers/cookies key-value viewers, copy dropdowns).
- [x] Create `KNetRequestEditorView` composable based on API Studio's request editor layout supporting sub-tabs (Body, Headers, Params, Cookies).
- [x] Implement Option B architecture: `KNetResponseInspector` and `KNetRequestEditorView` are pure composables driven directly by `TrafficViewModel` and `ApiStudioViewModel` emitting `NetworkResponseSpec` / `NetworkRequestSpec`.
- [x] Refactor `ResponseInspectorView` in `:ui:desktop:apistudio` to delegate to `KNetResponseInspector`.
- [x] Refactor `TrafficInspectorPanel` in `:ui:desktop:traffic` to consume `KNetResponseInspector` and `KNetRequestEditorView`.
- [x] Create `KNetEmptyStatePlaceholder` in `:ui:core` (`ui/core/src/commonMain/kotlin/com/devuloopers/knet/ui/core/components/placeholder/KNetEmptyStatePlaceholder.kt`).
- [x] Apply `KNetEmptyStatePlaceholder` across all empty sub-tabs (`Body`, `Headers`, `Params`, `Cookies`) in `KNetResponseInspector`, `KNetRequestEditorView`, and `KNetReadOnlyKeyValueViewer`.

### Phase 3: "Send to API Studio" Feature in Traffic UI `[COMPLETED]`
- [x] Add "Send to API Studio" option in `TrafficTable` right-click context menu (`KNetContextMenuArea`).
- [x] Add "Open in API Studio" primary button callback in `TrafficInspectorPanel` header toolbar.
- [x] Implement `ImportRequestToStudioUseCase` in `:core:domain` (`core/domain/src/commonMain/kotlin/com/devuloopers/knet/domain/apistudio/usecase/ImportRequestToStudioUseCase.kt`).
- [x] Refactor `ApiStudioViewModel` to consume `GetWorkspaceLayoutUseCase`, `SaveWorkspaceLayoutUseCase`, and `ImportRequestToStudioUseCase`.
- [x] Wire workspace navigation controller to automatically switch view to `DesktopDestination.ApiStudio` upon import.
- [x] Write unit tests for `ImportRequestToStudioUseCaseTest` and `ApiStudioViewModelTest`.

### Phase 4: Pause Interception, Edit Values, and Resume `[PENDING]`
- [ ] Expose live breakpoint rule configuration UI in Traffic view toolbar.
- [ ] Connect `InterceptSessionManager.activeEvents` flow to an "Interception Active" drawer/modal overlay in `:ui:desktop:traffic`.
- [ ] Display in-flight paused request (`InterceptedEvent`) inside an editable `KNetRequestEditorView` panel.
- [ ] Wire "Forward / Resume" action button to `InterceptCoordinator` to rebuild Netty request with edited headers/body (`RequestRebuilder.rebuild`) and resume socket execution.
- [ ] Wire "Drop" action button to close Netty channel on demand.
