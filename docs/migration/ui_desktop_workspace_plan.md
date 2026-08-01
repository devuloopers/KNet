# UI Desktop Workspace Module Plan — `:ui:desktop:workspace` (Phase 2)

**Target Module:** `ui/desktop/workspace/`  
**Gradle Module:** `:ui:desktop:workspace`  
**Package Namespace:** `com.devuloopers.knet.ui.desktop.workspace`  
**Platform:** Compose Multiplatform (Desktop JVM)  
**Status:** Approved for Creation

---

# 📌 Vision

`:ui:desktop:workspace` is responsible for organizing the user's working environment inside KNet.

It owns workspace navigation, collection exploration, history browsing, environment management, layout persistence, and workspace preferences.

It **does not own feature UIs** such as Traffic, Inspector, API Studio, Certificate Manager, or Script Console. Instead, those features are hosted by `:ui:desktop:shell` and participate in the workspace through the `WorkspacePanelContribution` extension point.

---

# 🎯 Responsibilities

The workspace module owns:
- Collections Explorer (`CollectionsExplorer`)
- Environment Explorer (`EnvironmentExplorer`)
- History Explorer (`HistoryExplorer`)
- Reusable Tree View (`ExplorerTree`)
- Workspace Search (`WorkspaceSearch`)
- Workspace navigation & selection handling (`WorkspaceSelection`, `ExplorerType`)
- Generic Panel model & host (`WorkspacePanel`, `WorkspacePanelHost`, `WorkspaceSplitter`)
- Layout configuration persistence (`WorkspaceLayoutData`)
- Panel Contribution Registry (`WorkspacePanelContribution`, `WorkspacePanelRegistry`)
- UDF ViewModel (`WorkspaceViewModel`) & Koin DI (`WorkspaceModule`)

---

# 🚫 Explicitly Out of Scope

This module MUST NOT contain:
- Traffic Feed or live transaction tables (belongs to `:ui:desktop:traffic`)
- Request/Response payload Inspector panels (belongs to `:ui:desktop:inspector`)
- Request Builder & Quick Replay (belongs to `:ui:desktop:apistudio`)
- Certificate Manager (belongs to `:ui:desktop:certificate`)
- Database, Netty, Ktor, or Engine code

---

# 📂 Directory Structure

```text
ui/
└── desktop/
    └── workspace/
        ├── build.gradle.kts
        │
        └── src/
            ├── jvmMain/
            │   └── kotlin/
            │       └── com/devuloopers/knet/ui/desktop/workspace/
            │
            │           ├── model/
            │           │     ├── WorkspaceLayoutData.kt
            │           │     ├── WorkspaceSelection.kt
            │           │     ├── WorkspaceIntent.kt
            │           │     ├── WorkspaceState.kt
            │           │     ├── WorkspacePanel.kt
            │           │     └── ExplorerType.kt
            │           │
            │           ├── explorer/
            │           │     ├── CollectionsExplorer.kt
            │           │     ├── EnvironmentExplorer.kt
            │           │     ├── HistoryExplorer.kt
            │           │     ├── WorkspaceSearch.kt
            │           │     └── ExplorerTree.kt
            │           │
            │           ├── layout/
            │           │     ├── WorkspaceLayout.kt
            │           │     ├── WorkspaceSplitter.kt
            │           │     └── WorkspacePanelHost.kt
            │           │
            │           ├── viewmodel/
            │           │     └── WorkspaceViewModel.kt
            │           │
            │           ├── contribution/
            │           │     ├── WorkspacePanelContribution.kt
            │           │     └── WorkspacePanelRegistry.kt
            │           │
            │           └── di/
            │                 └── WorkspaceModule.kt
            │
            └── jvmTest/
                └── kotlin/
                    └── com/devuloopers/knet/ui/desktop/workspace/
                        ├── WorkspaceViewModelTest.kt
                        ├── WorkspaceLayoutTest.kt
                        ├── ExplorerTreeTest.kt
                        ├── WorkspaceSearchTest.kt
                        ├── WorkspaceContributionTest.kt
                        └── MigrationRegressionTest.kt
```

---

# 🏗 Component Specifications

### 1. Contribution API (`contribution/`)
```kotlin
interface WorkspacePanelContribution {
    val panelId: String
    val panelTitle: String
    val content: @Composable () -> Unit
}

class WorkspacePanelRegistry {
    fun register(contribution: WorkspacePanelContribution)
    fun getPanels(): List<WorkspacePanelContribution>
}
```

### 2. Workspace Models (`model/`)
- `WorkspacePanel`: Generic panel representation DTO (`id`, `title`).
- `ExplorerType`: Enum (`COLLECTIONS`, `ENVIRONMENTS`, `HISTORY`).
- `WorkspaceSelection`: Selected item DTO (`id`, `type`, `name`).
- `WorkspaceState`: Current UI state DTO holding active selection, expanded nodes, search query, and layout configuration.
- `WorkspaceIntent`: User actions (`SelectCollection`, `SelectFolder`, `SelectRequest`, `SelectEnvironment`, `Search`, `SaveLayout`, `RestoreLayout`).
- `WorkspaceLayoutData`: Persisted panel widths and splitter positions.

### 3. Explorer System (`explorer/`)
- `ExplorerTree`: Reusable tree component supporting expand/collapse, selection, and search highlighting.
- `CollectionsExplorer`: Collections, folders, and request tree navigator.
- `EnvironmentExplorer`: Environments, active variables, and key-value manager.
- `HistoryExplorer`: Session history and past request logs.
- `WorkspaceSearch`: Search bar and filtering primitive.

### 4. Layout System (`layout/`)
- `WorkspaceLayout`: Primary workspace layout composable.
- `WorkspaceSplitter`: Resizable divider handle.
- `WorkspacePanelHost`: Slot container hosting contributed panels.

### 5. ViewModel & DI (`viewmodel/` & `di/`)
- `WorkspaceViewModel`: Connects to `:core:domain` UseCases (`GetWorkspaceLayoutUseCase`, `SaveWorkspaceLayoutUseCase`).
- `WorkspaceModule`: Koin DI module.

---

# 📦 Dependencies

Depends on:
- `:ui:core`
- `:ui:desktop:shell`
- `:core:domain`
- `:core:logger`
- Compose Multiplatform (Desktop)
- Koin

Must NOT depend on:
- `:engine:*`
- `:core:http`
- SQL / Netty / Ktor

---

# 🧪 Test Architecture (`jvmTest/`)

- `WorkspaceViewModelTest`: State updates, selection changes, search, layout persistence.
- `WorkspaceLayoutTest`: Splitter sizes, layout restoration, panel placement.
- `ExplorerTreeTest`: Expand/collapse, tree node selection.
- `WorkspaceSearchTest`: Filtering and search query state.
- `WorkspaceContributionTest`: Dynamic panel registration and lookup.
- `MigrationRegressionTest`: Public API and model stability.
