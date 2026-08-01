# UI Core Module Plan — `:ui:core`

**Target Module:** `ui/core/`  
**Gradle Module:** `:ui:core`  
**Package Namespace:** `com.devuloopers.knet.ui.core`  
**Platform:** Kotlin Multiplatform (`commonMain`, `commonTest`)  
**Status:** Approved for Creation

---

# 📌 Vision

`:ui:core` is KNet's shared UI foundation built on **Compose Multiplatform**.

It provides the reusable presentation building blocks used across every KNet application including:
- Desktop
- Mobile Companion
- CLI (future Compose terminal UI)

It owns the design language of KNet while remaining completely independent from application features.

---

# 🎯 Responsibilities

The module is responsible for:
- Theme (colors, typography, shapes, spacing, dimensions, elevation)
- Design tokens & icons (`KNetIcons`)
- Generic badges (`MethodBadge`, `StatusBadge`, `TagBadge`, `ProtocolBadge`)
- Generic input controls (`KNetInputField`, `KNetDropdown`, `TableCellTextField`, `CopyActionButton`)
- Generic layout primitives (`WidgetFrame`, `CollapsibleSection`, `InspectorSection`, `WidgetSearchBar`)
- Generic table components (`EditableKeyValueTable`)
- Generic feedback components (`LoadingIndicator`, `EmptyState`, `ErrorView`)
- Multiplatform clipboard utilities (`util/Clipboard.kt`)
- Theme & component previews (`preview/`)

---

# 🚫 Explicitly Out of Scope

This module MUST NOT contain:
- Screens
- ViewModels
- Navigation
- `UiState` / `UiEvent`
- Business logic
- API Studio widgets (`QuickReplayWidget`, `RequestTreeWidget`)
- Inspector widgets (`ParameterNode`, `TimingItem`, `DetailItem`, `RequestBodyWidget`, `ResponseBodyWidget`, `MiddleInspectorWidget`, `TimingsWidget`)
- Traffic widgets (`TransactionOverviewWidget`, `SubFrame`)
- Desktop Shell widgets (`TopHeader`, `SystemStatusBar`)
- Database, Netty, Ktor, or Engine code

---

# 📁 Recommended Directory Structure

```text
ui/
└── core/
    ├── build.gradle.kts
    │
    └── src/
        ├── commonMain/
        │   └── kotlin/
        │       └── com/devuloopers/knet/ui/core/
        │
        │           ├── theme/
        │           │     ├── KNetTheme.kt
        │           │     ├── KNetColors.kt
        │           │     ├── KNetTypography.kt
        │           │     ├── KNetShapes.kt
        │           │     ├── KNetSpacing.kt
        │           │     ├── KNetDimensions.kt
        │           │     └── KNetElevation.kt
        │           │
        │           ├── icon/
        │           │     └── KNetIcons.kt
        │           │
        │           ├── badge/
        │           │     ├── MethodBadge.kt
        │           │     ├── StatusBadge.kt
        │           │     ├── TagBadge.kt
        │           │     └── ProtocolBadge.kt
        │           │
        │           ├── input/
        │           │     ├── KNetInputField.kt
        │           │     ├── KNetDropdown.kt
        │           │     ├── TableCellTextField.kt
        │           │     └── CopyActionButton.kt
        │           │
        │           ├── layout/
        │           │     ├── WidgetFrame.kt
        │           │     ├── CollapsibleSection.kt
        │           │     ├── InspectorSection.kt
        │           │     └── WidgetSearchBar.kt
        │           │
        │           ├── table/
        │           │     └── EditableKeyValueTable.kt
        │           │
        │           ├── feedback/
        │           │     ├── LoadingIndicator.kt
        │           │     ├── EmptyState.kt
        │           │     └── ErrorView.kt
        │           │
        │           ├── util/
        │           │     └── Clipboard.kt
        │           │
        │           └── preview/
        │                 ├── ThemePreview.kt
        │                 ├── BadgePreview.kt
        │                 └── InputPreview.kt
        │
        └── commonTest/
            └── kotlin/
                └── com/devuloopers/knet/ui/core/
                      ├── theme/KNetThemeTest.kt
                      ├── badge/BadgeTest.kt
                      ├── input/InputControlTest.kt
                      ├── layout/LayoutTest.kt
                      └── MigrationRegressionTest.kt
```

---

# 🏗 Component Guidelines

## Theme (`theme/`)
Owns the complete design system:
- `KNetColors`: Dark surfaces, borders, status/method accents, typography colors.
- `KNetTypography`: Monospace & UI font styles, font sizes, weights.
- `KNetShapes`: Corner radius tokens (4.dp, 6.dp, 8.dp).
- `KNetSpacing`: Padding/margin tokens (2.dp, 4.dp, 8.dp, 12.dp, 16.dp, 24.dp).
- `KNetDimensions`: Standard widget heights/widths.
- `KNetElevation`: Shadow and z-index elevation tokens.
- `KNetTheme`: MaterialTheme dark color scheme wrapper.

## Icons (`icon/`)
`KNetIcons`: Single source of truth for icons used by reusable components (Copy, Check, ArrowDown, Search, Warning, Error, Info, Close, Refresh).

## Badges (`badge/`)
- `MethodBadge`: HTTP Method badge (GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD).
- `StatusBadge`: HTTP Status code badge (1xx, 2xx, 3xx, 4xx, 5xx, 101).
- `TagBadge`: Generic pill tag badge.
- `ProtocolBadge`: Network protocol badge (HTTP/1.1, HTTP/2, HTTP/3, WebSocket, gRPC).

## Inputs (`input/`)
- `KNetInputField`: Styled input field with focus borders and auto-complete popups.
- `KNetDropdown`: Single-select popup dropdown component.
- `TableCellTextField`: Inline table cell editor.
- `CopyActionButton`: Reusable copy button that triggers an `onCopy()` callback and shows visual "Copied!" feedback state.

## Layout (`layout/`)
- `WidgetFrame`: Styled panel container with border, background, and title header bar.
- `CollapsibleSection`: Expandable header & content container.
- `InspectorSection`: Section header line with title and content slot.
- `WidgetSearchBar`: Compact search input field.

## Table (`table/`)
- `EditableKeyValueTable`: Generic editable key-value table (decoupled from HTTP-specific header domain models).

## Feedback (`feedback/`)
- `LoadingIndicator`: Centered indeterminate progress spinner.
- `EmptyState`: Empty list / no content visual state placeholder.
- `ErrorView`: Visual error message banner with retry action trigger.

## Utilities (`util/`)
- `Clipboard.kt`: 100% KMP clipboard helper wrapping `LocalClipboard.current` without any AWT/desktop-only imports.

---

# 🚫 Components NOT Migrated (Remaining in `sharedUI` / Feature Modules)

### Inspector Features
- `ParameterNode`
- `TimingItem`
- `DetailItem`
- `TransactionOverviewWidget`
- `TimingsWidget`
- `RequestBodyWidget`
- `ResponseBodyWidget`
- `MiddleInspectorWidget`

### API Studio Features
- `QuickReplayWidget`
- `RequestTreeWidget`

### Desktop Shell Features
- `TopHeader`
- `SystemStatusBar`

### Formatting Features
- `FormattingResult`

---

# 📦 Dependencies

Depends on:
- `libs.compose.runtime`
- `libs.compose.foundation`
- `libs.compose.material3`
- `libs.compose.ui`
- Kotlin Stdlib

Must NOT depend on:
- `:core:domain`
- `:core:http`
- `:engine:*`
- Netty / SQL / Ktor
- ViewModels / Desktop APIs

---

# 🧪 Test Architecture (`commonTest/`)

- `KNetThemeTest`: Verify theme composition, color tokens, typography, shapes, and spacing.
- `BadgeTest`: Verify MethodBadge, StatusBadge, TagBadge, and ProtocolBadge rendering logic.
- `InputControlTest`: Verify KNetInputField, KNetDropdown, TableCellTextField, and CopyActionButton callbacks.
- `LayoutTest`: Verify WidgetFrame, CollapsibleSection, InspectorSection, and WidgetSearchBar layout slots.
- `MigrationRegressionTest`: Verify public composables remain available and imports compile cleanly.
