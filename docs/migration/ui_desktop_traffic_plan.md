# UI Desktop Traffic Module Plan — `:ui:desktop:traffic` (Phase 5)

**Target Module:** `ui/desktop/traffic/`  
**Gradle Module:** `:ui:desktop:traffic`  
**Package Namespace:** `com.devuloopers.knet.ui.desktop.traffic`  
**Platform:** Compose Multiplatform (Desktop JVM)  
**Status:** Approved for Creation

---

# 📌 Vision

`:ui:desktop:traffic` is KNet's real-time network traffic explorer.

It provides a high-performance live transaction feed, advanced filtering, searching, sorting, grouping, session monitoring, and selection of captured network traffic.

It is the primary entry point into captured proxy traffic.

It is **not responsible** for proxy lifecycle management or application-wide status.

---

# 🎯 Responsibilities

This module owns:
- Live traffic feed & virtualized transaction table (`TrafficTable`, `TrafficColumns`, `TrafficRow`, `TrafficCell`, `EmptyTrafficView`)
- Advanced live filtering (`FilterToolbar`, `MethodFilter`, `StatusFilter`, `ProtocolFilter`, `DomainFilter`, `SearchBar`)
- Sorting & grouping (`TrafficSort`, `TrafficSession`)
- Selection & multi-selection (`TrafficSelection`)
- Feed control toolbar (`FeedToolbar`, `ExportButton`, `PauseFeedButton`, `ClearFeedButton`, `AutoScrollButton`)
- Feed metrics & summary cards (`TrafficSummaryCard`, `SessionBadge`, `ConnectionIndicator`, `FeedStatistics`)
- Main screen container (`TrafficScreen`)
- UDF ViewModel (`TrafficViewModel`) & Koin DI (`TrafficModule`)
- Desktop Shell Contribution (`TrafficContribution`)

---

# 🚫 Explicitly Out of Scope

This module MUST NOT contain:
- Proxy engine or Netty handlers (belongs to `:engine:proxy`)
- Proxy lifecycle management (belongs to `:ui:desktop:shell`)
- HTTP request execution & editing (belongs to `:ui:desktop:apistudio`)
- Deep multi-tab payload analysis (belongs to `:ui:desktop:inspector`)
- SQL database implementation (belongs to `:data`)

---

# 📂 Directory Structure

```text
ui/
└── desktop/
    └── traffic/
        ├── build.gradle.kts
        │
        └── src/
            ├── jvmMain/
            │   └── kotlin/
            │       └── com/devuloopers/knet/ui/desktop/traffic/
            │
            │           ├── model/
            │           │     ├── TrafficState.kt
            │           │     ├── TrafficIntent.kt
            │           │     ├── TrafficFilter.kt
            │           │     ├── TrafficSort.kt
            │           │     ├── TrafficSelection.kt
            │           │     ├── TrafficSession.kt
            │           │     └── TrafficMetrics.kt
            │           │
            │           ├── table/
            │           │     ├── TrafficTable.kt
            │           │     ├── TrafficColumns.kt
            │           │     ├── TrafficRow.kt
            │           │     ├── TrafficCell.kt
            │           │     └── EmptyTrafficView.kt
            │           │
            │           ├── filter/
            │           │     ├── FilterToolbar.kt
            │           │     ├── MethodFilter.kt
            │           │     ├── StatusFilter.kt
            │           │     ├── ProtocolFilter.kt
            │           │     ├── DomainFilter.kt
            │           │     └── SearchBar.kt
            │           │
            │           ├── toolbar/
            │           │     ├── FeedToolbar.kt
            │           │     ├── ExportButton.kt
            │           │     ├── PauseFeedButton.kt
            │           │     ├── ClearFeedButton.kt
            │           │     └── AutoScrollButton.kt
            │           │
            │           ├── component/
            │           │     ├── TrafficSummaryCard.kt
            │           │     ├── SessionBadge.kt
            │           │     ├── ConnectionIndicator.kt
            │           │     └── FeedStatistics.kt
            │           │
            │           ├── view/
            │           │     └── TrafficScreen.kt
            │           │
            │           ├── viewmodel/
            │           │     └── TrafficViewModel.kt
            │           │
            │           ├── contribution/
            │           │     └── TrafficContribution.kt
            │           │
            │           └── di/
            │                 └── TrafficModule.kt
            │
            └── jvmTest/
                └── kotlin/
                    └── com/devuloopers/knet/ui/desktop/traffic/
                        ├── TrafficViewModelTest.kt
                        ├── TrafficFilteringTest.kt
                        ├── TrafficSortingTest.kt
                        ├── TrafficSelectionTest.kt
                        ├── TrafficTableTest.kt
                        ├── FeedToolbarTest.kt
                        ├── ExportTest.kt
                        ├── SearchTest.kt
                        ├── TrafficContributionTest.kt
                        └── MigrationRegressionTest.kt
```

---

# 🏗 Component Specifications

### 1. State & Data Models (`model/`)
- `TrafficState`: Current transaction feed, active filters, search query, selected transactions, feed statistics, auto-scroll, and pause states.
- `TrafficIntent`: User actions (`SelectTransaction`, `MultiSelect`, `FilterByMethod`, `FilterByStatus`, `FilterByProtocol`, `Search`, `PauseFeed`, `ResumeFeed`, `ClearFeed`, `ExportSelection`).
- `TrafficFilter`, `TrafficSort`, `TrafficSelection`, `TrafficSession`, `TrafficMetrics`.

### 2. Table & Virtualization (`table/`)
- `TrafficTable`: Virtualised transaction table supporting 100k+ rows, lazy loading, keyboard navigation, sticky headers, and context menus.
- `TrafficColumns`, `TrafficRow`, `TrafficCell`, `EmptyTrafficView`.

### 3. Filters & Toolbars (`filter/`, `toolbar/`)
- `FilterToolbar`: Method, status, protocol, domain filters, and search bar.
- `FeedToolbar`: Pause/resume, clear, export, auto-scroll, and follow latest buttons.

### 4. Components & View (`component/`, `view/`)
- `TrafficSummaryCard`: Total requests, active sessions, requests/sec, errors, average latency.
- `SessionBadge`, `ConnectionIndicator`, `FeedStatistics`.
- `TrafficScreen`: Top-level traffic explorer view.

### 5. Contribution API (`contribution/`)
- `TrafficContribution`: Registers Live Traffic navigation item, toolbar actions, and context menus into `:ui:desktop:shell`.

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
- `:engine:proxy`
- `:core:http`
- `:ui:desktop:inspector`
- `:ui:desktop:apistudio`
- SQL / Netty

---

# 🧪 Test Architecture (`jvmTest/`)

- `TrafficViewModelTest`: Live updates, filtering, sorting, selection, search, pause/resume.
- `TrafficFilteringTest`: Method, status, protocol, domain, MIME type filters.
- `TrafficSortingTest`: Time, status, duration, URL, host, size sorting.
- `TrafficSelectionTest`: Single, multi, range, and keyboard selection.
- `TrafficTableTest`: Virtualized scrolling, row/column rendering, empty view.
- `FeedToolbarTest`: Pause, resume, clear, auto-scroll, follow latest.
- `ExportTest`: Export selected, filtered, or all captured transactions.
- `SearchTest`: Search by URL, headers, host, and body.
- `TrafficContributionTest`: Navigation, toolbar, and context menu registrations.
- `MigrationRegressionTest`: Model and public API stability.
