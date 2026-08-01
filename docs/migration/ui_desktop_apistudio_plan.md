# UI Desktop API Studio Module Plan — `:ui:desktop:apistudio` (Phase 3)

**Target Module:** `ui/desktop/apistudio/`  
**Gradle Module:** `:ui:desktop:apistudio`  
**Package Namespace:** `com.devuloopers.knet.ui.desktop.apistudio`  
**Platform:** Compose Multiplatform (Desktop JVM)  
**Status:** Approved for Creation

---

# 📌 Vision

`:ui:desktop:apistudio` is KNet's dedicated HTTP API development environment.

It provides everything required to author, execute, inspect, and organize HTTP requests, similar to Postman, Insomnia, or Bruno.

The module owns the entire request editing experience while remaining completely independent from Live Traffic, Inspector, Certificate Management, and Workspace navigation.

---

# 🎯 Responsibilities

This module owns:
- HTTP request editor (`RequestEditor`, `UrlBar`, `MethodSelector`)
- Request toolbar (`RequestToolbar`) and Tab bar (`RequestTabBar`)
- Editor tabs: Query parameters (`QueryTab`), Headers (`HeadersTab`), Cookies (`CookiesTab`), Auth (`AuthTab`), Body (`BodyTab`), Scripts (`ScriptTab`), Tests (`TestsTab`)
- HTTP request execution via `:core:http`
- Response viewer (`ResponseViewer`, `ResponseBodyView`, `ResponseHeadersView`, `ResponseCookiesView`, `ResponseStatusBar`, `ResponseMetadataView`)
- Environment selector (`EnvironmentSelector`)
- Quick replay card (`QuickReplayCard`)
- Execution toolbar (`ExecutionToolbar`) and indicators (`RequestExecutionIndicator`)
- Desktop Shell Contribution (`ApiStudioContribution`)

---

# 🚫 Explicitly Out of Scope

This module MUST NOT contain:
- Collections explorer (belongs to `:ui:desktop:workspace`)
- Workspace navigation & History explorer (belongs to `:ui:desktop:workspace`)
- Live traffic table (belongs to `:ui:desktop:traffic`)
- Transaction inspector (belongs to `:ui:desktop:inspector`)
- Certificate manager (belongs to `:ui:desktop:certificate`)
- Database implementation, Netty, or Proxy engine

---

# 📂 Directory Structure

```text
ui/
└── desktop/
    └── apistudio/
        ├── build.gradle.kts
        │
        └── src/
            ├── jvmMain/
            │   └── kotlin/
            │       └── com/devuloopers/knet/ui/desktop/apistudio/
            │
            │           ├── model/
            │           │     ├── ApiStudioState.kt
            │           │     ├── RequestEditorState.kt
            │           │     ├── ResponsePresentation.kt
            │           │     ├── ExecutionState.kt
            │           │     └── RequestTab.kt
            │           │
            │           ├── viewmodel/
            │           │     └── ApiStudioViewModel.kt
            │           │
            │           ├── editor/
            │           │     ├── RequestEditor.kt
            │           │     ├── UrlBar.kt
            │           │     ├── MethodSelector.kt
            │           │     ├── RequestToolbar.kt
            │           │     ├── RequestTabBar.kt
            │           │     │
            │           │     └── tabs/
            │           │           ├── QueryTab.kt
            │           │           ├── HeadersTab.kt
            │           │           ├── CookiesTab.kt
            │           │           ├── AuthTab.kt
            │           │           ├── BodyTab.kt
            │           │           ├── ScriptTab.kt
            │           │           └── TestsTab.kt
            │           │
            │           ├── response/
            │           │     ├── ResponseViewer.kt
            │           │     ├── ResponseBodyView.kt
            │           │     ├── ResponseHeadersView.kt
            │           │     ├── ResponseCookiesView.kt
            │           │     ├── ResponseStatusBar.kt
            │           │     └── ResponseMetadataView.kt
            │           │
            │           ├── component/
            │           │     ├── QuickReplayCard.kt
            │           │     ├── EnvironmentSelector.kt
            │           │     ├── ExecutionToolbar.kt
            │           │     ├── ResponseSummary.kt
            │           │     └── RequestExecutionIndicator.kt
            │           │
            │           ├── contribution/
            │           │     └── ApiStudioContribution.kt
            │           │
            │           └── di/
            │                 └── ApiStudioModule.kt
            │
            └── jvmTest/
                └── kotlin/
                    └── com/devuloopers/knet/ui/desktop/apistudio/
                        ├── ApiStudioViewModelTest.kt
                        ├── RequestEditorTest.kt
                        ├── RequestExecutionTest.kt
                        ├── ResponsePresentationTest.kt
                        ├── RequestTabsTest.kt
                        ├── EnvironmentSelectorTest.kt
                        ├── ApiStudioContributionTest.kt
                        └── MigrationRegressionTest.kt
```

---

# 🏗 Component Specifications

### 1. State & Presentation Models (`model/`)
- `ApiStudioState`: Top-level UI state holding open tabs, active tab, active request/response, environment, execution state.
- `RequestEditorState`: Authoring state holding URL, method, headers, query params, auth, body payload, pre-request script.
- `ResponsePresentation`: Formatted HTTP response model for UI preview (status, headers, body, time, size).
- `ExecutionState`: Enum (`IDLE`, `EXECUTING`, `SUCCESS`, `ERROR`).
- `RequestTab`: Active request tab item model.

### 2. Request Editor (`editor/`)
- `RequestEditor`: Primary request authoring view container.
- `UrlBar` & `MethodSelector`: URL input field and GET/POST/PUT/DELETE/PATCH method picker.
- `RequestToolbar` & `RequestTabBar`: Send, Save, Duplicate actions and multi-tab switcher bar.
- Editor tabs: `QueryTab`, `HeadersTab`, `CookiesTab`, `AuthTab`, `BodyTab`, `ScriptTab`, `TestsTab`.

### 3. Response Viewer (`response/`)
- `ResponseViewer`: Primary response container view.
- `ResponseBodyView`: Pretty formatted body viewer (JSON/Text/Raw).
- `ResponseHeadersView` & `ResponseCookiesView`: Response headers and cookies key-value tables.
- `ResponseStatusBar` & `ResponseMetadataView`: Status code badge, duration in ms, MIME type, payload size in KB.

### 4. Components (`component/`)
- `QuickReplayCard`: One-click request replay card widget.
- `EnvironmentSelector`: Environment picker dropdown (Dev, QA, Staging, Prod).
- `ExecutionToolbar`: Execute/Cancel controls.

### 5. Contribution API (`contribution/`)
- `ApiStudioContribution`: Implements `:ui:desktop:shell` contribution interfaces to register navigation items, menu entries, and toolbar actions into the shell.

---

# 📦 Dependencies

Depends on:
- `:ui:core`
- `:ui:desktop:shell`
- `:core:domain`
- `:core:http`
- `:core:logger`
- Compose Multiplatform (Desktop)
- Koin

Must NOT depend on:
- `:ui:desktop:workspace`
- `:ui:desktop:traffic`
- `:ui:desktop:inspector`
- `:engine:*`
- SQL / Netty

---

# 🧪 Test Architecture (`jvmTest/`)

- `ApiStudioViewModelTest`: Request editing, UDF state updates, environment switching.
- `RequestEditorTest`: URL, method, header, query, body, and auth editing.
- `RequestExecutionTest`: HTTP execution via `:core:http`, loading indicator, error handling.
- `ResponsePresentationTest`: Response formatting, header rendering, MIME detection, size formatting.
- `RequestTabsTest`: Open, close, switch, dirty state tabs.
- `EnvironmentSelectorTest`: Active environment updates and variable substitution.
- `ApiStudioContributionTest`: Navigation, toolbar, menu contributions.
- `MigrationRegressionTest`: Model and public API stability.
