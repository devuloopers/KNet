# UI Desktop Architecture & Migration Roadmap — KNet Architecture v2.0

**Parent Module:** `ui/desktop/`  
**Platform:** Compose Multiplatform (Desktop)  
**Status:** Approved Master Architecture

---

# 📌 Vision

With `:ui:core` complete, the next phase is to decompose the existing `sharedUI` module into independent feature-oriented presentation modules.

Each module represents a single business capability and depends only on:
- `:ui:core`
- `:core:*`
- Required `:engine:*` modules
- Desktop platform infrastructure

The long-term goal is to completely eliminate `sharedUI`.

---

# 🎯 Architecture Goals

- Feature-based UI modules
- Independent Gradle projects
- Clear ownership boundaries
- Minimal coupling
- High reusability
- Mobile-ready architecture
- Future plugin compatibility

---

# 📂 Target UI Architecture

```text
ui/
│
├── core/                                       📦 :ui:core [COMPLETED]
│
└── desktop/
    │
    ├── app/                                   📦 :ui:desktop:app (Phase 1) [COMPLETED]
    │
    ├── workspace/                             📦 :ui:desktop:workspace (Phase 2)
    │
    ├── apistudio/                             📦 :ui:desktop:apistudio (Phase 3)
    │
    ├── inspector/                             📦 :ui:desktop:inspector (Phase 4)
    │
    ├── traffic/                               📦 :ui:desktop:traffic (Phase 5)
    │
    ├── scripting/                             📦 :ui:desktop:scripting (Phase 6)
    │
    ├── certificate/                           📦 :ui:desktop:certificate (Phase 7) [COMPLETED]
    │
    ├── settings/                              📦 :ui:desktop:settings (Phase 8)
    │
    └── codeeditor/                            📦 :ui:desktop:codeeditor [COMPLETED]
```

---

# 🏗 Module Responsibilities

---

# 1. `:ui:desktop:app` (Phase 1) [COMPLETED]

## Purpose
Owns the Desktop application's frame and navigation layout.

Responsible for:
- Main window frame
- Application navigation rail / tab bar
- Toolbar & window title bar (`TopHeader`)
- Dock manager / split panes layout
- Global status bar (`SystemStatusBar`)
- Window state & desktop application menus

### Owns
- MainWindow frame
- `TopHeader`
- Navigation Rail
- Dock Manager
- `SystemStatusBar`
- Window Layout

### Must NOT Contain
- Traffic list
- API Studio
- Inspector
- Request editor
- Business logic

---

# 2. `:ui:desktop:workspace` (Phase 2)

## Purpose
Owns all workspace navigation and environment trees.

Responsible for:
- Collections tree
- History
- Environment Explorer & variables
- Folder tree
- Workspace search & navigation

---

# 3. `:ui:desktop:apistudio` (Phase 3)

## Purpose
Owns API request authoring & execution.

Responsible for:
- Request Builder
- Headers & Query Parameters editors
- Authentication settings
- Body Editor
- Quick Replay (`QuickReplayWidget`)
- Request Tree (`RequestTreeWidget`)

---

# 4. `:ui:desktop:inspector` (Phase 4)

## Purpose
Owns HTTP transaction inspection.

Responsible for:
- Headers, Cookies, Query parameters
- Request Body (`RequestBodyWidget`)
- Response Body (`ResponseBodyWidget`)
- Body Preview & Formatter integration
- Network Timings (`TimingsWidget`, `TimingItem`)
- Notes & Tags (`NotesTagsWidget`)
- Inspector Detail Row (`DetailItem`)
- Parameter Tree (`ParameterNode`)

---

# 5. `:ui:desktop:traffic` (Phase 5)

## Purpose
Owns live traffic visualization.

Responsible for:
- Traffic table / list view
- Search & sorting
- Method, status, protocol filters
- Transaction selection & grouping

### Uses
Inspector module (`:ui:desktop:inspector`) for detailed transaction inspection.
Must NOT implement inspector views itself.

---

# 6. `:ui:desktop:scripting` (Phase 6)

## Purpose
Owns script authoring and execution console UI.

---

# 7. `:ui:desktop:certificate` (Phase 7)

## Purpose
Owns certificate installation & CA trust status UI.

---

# 8. `:ui:desktop:settings` (Phase 8)

## Purpose
Owns application settings & proxy defaults configuration UI.

---

# 9. `:ui:desktop:codeeditor` (`[COMPLETED]`)

## Purpose
Shared code editor component (`KNetCodeEditor`).

---

# 📋 Migration Roadmap & Execution Phases

| Phase | Target Module | Rationale |
|:---:|---|---|
| **Phase 1** | **`:ui:desktop:app` [COMPLETED]** | Establishes the desktop application frame, navigation rail, `TopHeader`, dock manager, and `SystemStatusBar`. |
| **Phase 2** | **`:ui:desktop:workspace`** | Establishes workspace collections, history, and environment navigation. |
| **Phase 3** | **`:ui:desktop:apistudio`** | Builds request authoring, `QuickReplayWidget`, and request builder UI. |
| **Phase 4** | **`:ui:desktop:inspector`** | Builds reusable HTTP transaction inspector (`RequestBodyWidget`, `ResponseBodyWidget`, `TimingsWidget`). |
| **Phase 5** | **`:ui:desktop:traffic`** | Builds live traffic list/table view consuming `:ui:desktop:inspector`. |
| **Phase 6** | **`:ui:desktop:scripting`** | Builds script execution & console UI. |
| **Phase 7** | **`:ui:desktop:certificate` [COMPLETED]** | Builds CA certificate installation UI. |
| **Phase 8** | **`:ui:desktop:settings`** | Builds application settings UI. |
| **Phase 9** | **`sharedUI` Deprecation** | Deletes `sharedUI` after all feature modules are migrated. |
