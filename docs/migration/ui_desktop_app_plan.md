# UI Desktop Shell Module Plan — `:ui:desktop:shell`

**Target Module:** `ui/desktop/shell/`  
**Gradle Module:** `:ui:desktop:shell`  
**Package Namespace:** `com.devuloopers.knet.ui.desktop.shell`  
**Platform:** Compose Multiplatform (Desktop JVM)  
**Status:** Approved for Creation

---

# 📌 Vision

`:ui:desktop:shell` is the Desktop application's presentation framework.

It owns the application's window, navigation, layout, toolbar, menu system, docking system, and status bar.

It provides **extension points** (`ShellContribution`) that allow feature modules to contribute UI without modifying the shell itself.

The shell never contains Traffic, Inspector, API Studio, Certificate, or Settings logic.

---

# 🎯 Responsibilities

The shell owns:
- Main application window (`MainWindow`, `WindowState`)
- Window layout (`MainWindowShell`, `DockLayout`, `SplitLayout`, `PanelHost`)
- Navigation (`NavigationItem`, `NavigationRail`, `NavigationBar`, `NavigationState`)
- Toolbar (`Toolbar`, `ToolbarAction`, `ToolbarHost`)
- Menu system (`MenuBar`, `MenuItem`, `ContextMenu`)
- Status bar (`StatusBar`, `StatusItem`, `StatusBarHost`)
- Contribution API (`ShellContribution`, `ToolbarContribution`, `StatusContribution`, `NavigationContribution`)
- Configuration DTO (`ShellConfiguration`)

---

# 🚫 Explicitly Out of Scope

The shell MUST NOT contain:
- Proxy controls or traffic table logic
- Certificate actions
- API request execution
- Inspector panels
- Collections / Workspace repositories
- Database, Netty, Ktor, or Engine code

---

# 📂 Directory Structure

```text
ui/
└── desktop/
    └── shell/
        ├── build.gradle.kts
        │
        └── src/
            ├── jvmMain/
            │   └── kotlin/
            │       └── com/devuloopers/knet/ui/desktop/shell/
            │
            │           ├── window/
            │           │     ├── MainWindow.kt
            │           │     └── WindowState.kt
            │           │
            │           ├── layout/
            │           │     ├── MainWindowShell.kt
            │           │     ├── DockLayout.kt
            │           │     ├── SplitLayout.kt
            │           │     └── PanelHost.kt
            │           │
            │           ├── navigation/
            │           │     ├── NavigationItem.kt
            │           │     ├── NavigationRail.kt
            │           │     ├── NavigationBar.kt
            │           │     └── NavigationState.kt
            │           │
            │           ├── toolbar/
            │           │     ├── Toolbar.kt
            │           │     ├── ToolbarAction.kt
            │           │     └── ToolbarHost.kt
            │           │
            │           ├── menu/
            │           │     ├── MenuBar.kt
            │           │     ├── MenuItem.kt
            │           │     └── ContextMenu.kt
            │           │
            │           ├── statusbar/
            │           │     ├── StatusBar.kt
            │           │     ├── StatusItem.kt
            │           │     └── StatusBarHost.kt
            │           │
            │           ├── contribution/
            │           │     ├── ShellContribution.kt
            │           │     ├── ToolbarContribution.kt
            │           │     ├── StatusContribution.kt
            │           │     └── NavigationContribution.kt
            │           │
            │           └── model/
            │                 └── ShellConfiguration.kt
            │
            └── jvmTest/
                └── kotlin/
                    └── com/devuloopers/knet/ui/desktop/shell/
                        ├── NavigationTest.kt
                        ├── ToolbarTest.kt
                        ├── StatusBarTest.kt
                        ├── DockLayoutTest.kt
                        ├── MainWindowShellTest.kt
                        └── MigrationRegressionTest.kt
```

---

# 🏗 Component Specifications

### 1. Contribution API (`contribution/`)
Extension points for feature modules:
```kotlin
interface ShellContribution
interface NavigationContribution : ShellContribution { fun getNavigationItems(): List<NavigationItem> }
interface ToolbarContribution : ShellContribution { fun getToolbarActions(): List<ToolbarAction> }
interface StatusContribution : ShellContribution { fun getStatusItems(): List<StatusItem> }
```

### 2. Window & State (`window/`)
- `MainWindow`: Desktop application entry point composable.
- `WindowState`: Window size, position, and title state holder.

### 3. Layout System (`layout/`)
- `MainWindowShell`: Root persistent container hosting toolbar, navigation, dock layout, and status bar.
- `DockLayout`: Panel arrangement manager for split panes (left dock, right dock, bottom dock, center content).
- `SplitLayout`: Two-pane resizable split view container.
- `PanelHost`: Container slot for rendering contributed feature panels.

### 4. Navigation (`navigation/`)
- `NavigationItem`: Data model (`id`, `title`, `icon`, `badgeCount`).
- `NavigationRail`: Vertical desktop navigation bar.
- `NavigationBar`: Horizontal navigation bar.
- `NavigationState`: Remembers active selection and backstack state.

### 5. Toolbar (`toolbar/`)
- `ToolbarAction`: Action model (`id`, `label`, `icon`, `onClick`, `isEnabled`, `color`).
- `ToolbarHost`: Renders registered toolbar actions contributed by features.
- `Toolbar`: Top bar layout composable.

### 6. Menu System (`menu/`)
- `MenuBar`: Application menu bar (File, Edit, View, Tools, Help).
- `MenuItem`: Action items for menus.
- `ContextMenu`: Popup context menu primitive.

### 7. Status Bar (`statusbar/`)
- `StatusItem`: Generic status item model (`id`, `label`, `value`, `color`).
- `StatusBarHost`: Renders status items contributed by features.
- `StatusBar`: Footer bar layout composable.

---

# 📦 Dependencies

Depends on:
- `:ui:core`
- `:core:domain`
- `:core:logger`
- Compose Multiplatform (Desktop)

Must NOT depend on:
- `:engine:*`
- `:core:http`
- `:desktop:data`
- Netty / SQL / Ktor

---

# 🧪 Test Architecture (`jvmTest/`)

- `NavigationTest`: Verify data-driven navigation rendering and item selection.
- `ToolbarTest`: Verify action rendering, ordering, and callback execution.
- `StatusBarTest`: Verify status item rendering and dynamic value updates.
- `DockLayoutTest`: Verify panel layout, split resizing, and slot content hosting.
- `MainWindowShellTest`: Verify complete shell rendering (toolbar + navigation + dock layout + status bar).
- `MigrationRegressionTest`: Verify public API stability and contribution interfaces.
