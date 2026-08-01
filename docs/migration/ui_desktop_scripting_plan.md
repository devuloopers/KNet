# UI Desktop Scripting Module Plan — `:ui:desktop:scripting` (Phase 6)

**Target Module:** `ui/desktop/scripting/`  
**Gradle Module:** `:ui:desktop:scripting`  
**Package Namespace:** `com.devuloopers.knet.ui.desktop.scripting`  
**Platform:** Compose Multiplatform (Desktop JVM)  
**Status:** Approved for Migration

---

# 📌 Vision

`:ui:desktop:scripting` is KNet's Automation Development Environment.

It provides a complete developer experience for creating, editing, organizing, executing, and analyzing automation scripts used throughout KNet.

The module is responsible only for presentation and user interaction.

All compilation, execution, sandboxing, security, and runtime behavior are delegated to `:engine:script`.

This module does **not** implement any scripting engine logic.

---

# 🎯 Responsibilities

The module owns:
- Script editor (`ScriptEditor`, powered by `:codeEditorUI`)
- Script tabs (`ScriptTabs`)
- Script explorer (`ScriptExplorer`)
- Variables & context explorers (`VariablesExplorer`, `ContextExplorer`)
- Execution console view (`ConsoleView`, `ConsoleToolbar`, `ConsoleFilter`, `ConsoleActions`)
- Diagnostics & Problems panel (`DiagnosticsView`, `ProblemsView`, `SuggestionsView`)
- Snippet & template libraries (`SnippetLibrary`, `TemplateLibrary`, `Favorites`)
- Main screen container (`ScriptingScreen`)
- UDF ViewModel (`ScriptingViewModel`) & Koin DI (`ScriptingModule`)

---

# 🚫 Explicitly Out of Scope

This module MUST NOT contain:
- GraalJS runtime
- Kotlin Script runtime
- Script compilation
- Script execution
- Script security
- HTTP execution
- Proxy logic
- Netty
- Database implementation
- Environment persistence

All runtime responsibilities belong to `:engine:script`.

---

# 📂 Directory Structure

```text
ui/
└── desktop/
    └── scripting/
        ├── build.gradle.kts
        │
        └── src/
            ├── jvmMain/
            │   └── kotlin/
            │       └── com/devuloopers/knet/ui/desktop/scripting/
            │
            │           ├── model/
            │           │     ├── ScriptingState.kt
            │           │     ├── ScriptingIntent.kt
            │           │     ├── ScriptPhase.kt
            │           │     ├── ScriptSnippet.kt
            │           │     ├── ScriptTemplate.kt
            │           │     ├── ScriptDiagnostic.kt
            │           │     ├── ConsoleLogEntry.kt
            │           │     └── ExecutionContext.kt
            │           │     └── ScriptExecutionState.kt
            │           │
            │           ├── workspace/
            │           │     ├── ScriptEditor.kt
            │           │     ├── ScriptTabs.kt
            │           │     ├── ScriptExplorer.kt
            │           │     ├── VariablesExplorer.kt
            │           │     └── ContextExplorer.kt
            │           │
            │           ├── console/
            │           │     ├── ConsoleView.kt
            │           │     ├── ConsoleToolbar.kt
            │           │     ├── ConsoleFilter.kt
            │           │     └── ConsoleActions.kt
            │           │
            │           ├── diagnostics/
            │           │     ├── DiagnosticsView.kt
            │           │     ├── ProblemsView.kt
            │           │     └── SuggestionsView.kt
            │           │
            │           ├── snippets/
            │           │     ├── SnippetLibrary.kt
            │           │     ├── TemplateLibrary.kt
            │           │     └── Favorites.kt
            │           │
            │           ├── view/
            │           │     └── ScriptingScreen.kt
            │           │
            │           ├── viewmodel/
            │           │     └── ScriptingViewModel.kt
            │           │
            │           └── di/
            │                 └── ScriptingModule.kt
            │
            └── jvmTest/
                └── kotlin/
                    └── com/devuloopers/knet/ui/desktop/scripting/
                        ├── ScriptingViewModelTest.kt
                        ├── ScriptEditorTest.kt
                        ├── ConsoleTest.kt
                        ├── ConsoleLogTest.kt
                        ├── DiagnosticsTest.kt
                        ├── VariablesExplorerTest.kt
                        ├── ExecutionContextTest.kt
                        ├── SnippetLibraryTest.kt
                        ├── TemplateLibraryTest.kt
                        ├── ScriptExecutionStateTest.kt
                        └── MigrationRegressionTest.kt
```

---

# 🏗 Component Specifications

### 1. State & Lifecycle Models (`model/`)
- `ScriptingState`: Active script, loading/saving state, diagnostic errors, console logs, auto-scroll, and execution state.
- `ScriptingIntent`: User actions (`LoadScript`, `SaveScript`, `ExecuteScript`, `AddSnippet`, `ClearConsole`).
- `ScriptPhase`: Represents script placement in pipeline (`PRE_REQUEST`, `TEST_ASSERTION`, `GLOBAL_RULE`). Reuses existing `ScriptLanguage` and `ScriptSnippet` from `:core:domain`.
- `ScriptExecutionState`: Lifecycle enum (`Idle`, `Running`, `Success`, `Failed`, `Cancelled`).
- `ExecutionContext`: UI projection of request, response, environment, and variables.

### 2. Workspace & Console (`workspace/`, `console/`)
- `ScriptEditor`: Code editor interface wrapper leveraging `:codeEditorUI`.
- `ScriptExplorer`, `VariablesExplorer`, `ContextExplorer`.
- `ConsoleView`: Displays console output stream with filter, search, copy, and auto-scroll capabilities.

### 3. Diagnostics & Snippets (`diagnostics/`, `snippets/`)
- `DiagnosticsView`: Real-time compilation and script diagnostics (errors, warnings, suggestions).
- `SnippetLibrary`: Searchable categories and favorites library panel.

---

# 📦 Dependencies

Depends on:
- `:ui:core`
- `:ui:desktop:shell`
- `:codeEditorUI`
- `:engine:script`
- `:core:domain`
- `:core:logger`

Must NOT depend on:
- `:engine:proxy`
- `:core:http`
- SQL
- Netty

---

# 🧪 Test Architecture (`jvmTest/`)

- `ScriptingViewModelTest`: UDF state updates and load/save operations.
- `ScriptEditorTest`: Integration wrapper validation with `:codeEditorUI`.
- `ConsoleTest` & `ConsoleLogTest`: Logs ordering, filtering, and console clean.
- `DiagnosticsTest`: Diagnostics rendering.
- `VariablesExplorerTest` & `ExecutionContextTest`: Context explorer models.
- `SnippetLibraryTest` & `TemplateLibraryTest`: Snippets insertion logic.
- `ScriptExecutionStateTest`: Transitions verification.
- `MigrationRegressionTest`: Model and public API stability.
