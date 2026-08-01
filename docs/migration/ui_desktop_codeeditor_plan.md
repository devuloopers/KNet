# UI Desktop Code Editor Migration Plan — `:ui:desktop:codeEditor`

**Source Module:** `codeEditorUI/`  
**Target Module:** `ui/desktop/codeEditor/`  
**Source Gradle:** `:codeEditorUI`  
**Target Gradle:** `:ui:desktop:codeEditor`  
**Source Package:** `com.devuloopers.knet.editor`  
**Target Package:** `com.devuloopers.knet.ui.desktop.codeeditor`  
**Platform:** Compose Multiplatform (Desktop JVM + commonMain)  
**Status:** Approved for Migration

> **Naming note:** Multi-word desktop modules use camelCase (`codeEditor`, `apiStudio`). Single word modules (`workspace`, `traffic`, `inspector`, `scripting`, `certificate`) are lowercase.

---

# 📌 Vision

`:ui:desktop:codeeditor` is KNet's reusable desktop code editing framework.

It provides a high-performance Compose Multiplatform editor capable of editing and viewing source code for multiple languages while remaining completely independent from API Studio, Inspector, Scripting, or any other feature module.

This is a **framework module**, not a feature module.

It owns all editor rendering, editing algorithms, syntax infrastructure, and reusable UI components.

---

# 🎯 Responsibilities

Owns:

- Code editing
- Read-only viewers
- Syntax highlighting
- Language abstraction
- Code folding
- Undo / Redo
- Bracket matching
- Auto indentation
- Line numbering
- Caret & selection rendering
- Context menus
- Editor toolbars
- Editor themes
- Editor state models
- Rendering algorithms

Must NOT own:

- API Studio logic
- Inspector logic
- HTTP execution
- Proxy logic
- Netty
- Formatter orchestration
- ViewModels
- Dependency Injection
- Business logic

---

# 📂 Target Directory Structure

```text
ui/
└── desktop/
    └── codeeditor/
        ├── build.gradle.kts
        │
        └── src/
            ├── commonMain/
            │   └── kotlin/
            │       └── com/devuloopers/knet/ui/desktop/codeeditor/
            │           │
            │           ├── api/
            │           │     KNetCodeEditor.kt         ← only public composable entry point
            │           │     EditorMode.kt             ← only public model
            │           │
            │           ├── algorithm/
            │           │     AutoIndentEngine.kt
            │           │     BracketMatcher.kt
            │           │     FoldManager.kt
            │           │     FoldToggleEngine.kt
            │           │     LineLayoutEngine.kt       (renamed from LineLayoutMeasurer)
            │           │     UndoRedoManager.kt        (renamed from UndoRedoStack)
            │           │
            │           ├── syntax/
            │           │     ├── language/
            │           │     │     JsonLanguageHighlighter.kt
            │           │     │     HtmlLanguageHighlighter.kt
            │           │     │     XmlLanguageHighlighter.kt
            │           │     │     JsLanguageHighlighter.kt
            │           │     │     CssLanguageHighlighter.kt
            │           │     │     PlainTextLanguageHighlighter.kt
            │           │     │     TagMarkupHighlighter.kt
            │           │     │
            │           │     ├── tokenizer/
            │           │     │     TokenMaker.kt
            │           │     │     FsmTokenMakerVisualTransformation.kt
            │           │     │
            │           │     ├── highlighter/
            │           │     │     CodeLanguageHighlighter.kt
            │           │     │     CodeSyntaxHighlighter.kt
            │           │     │
            │           │     └── registry/
            │           │           CodeHighlighterRegistry.kt
            │           │
            │           ├── component/
            │           │     EditorSurface.kt          (renamed from EditableCodeEditor internal)
            │           │     EditorToolbar.kt          (renamed from EditorHeaderToolbar)
            │           │     EditorGutter.kt
            │           │     ContextMenu.kt            (renamed from KNetContextMenu)
            │           │
            │           ├── model/
            │           │     EditorState.kt            (new — encapsulates collapsed folds, text field value)
            │           │     CursorPosition.kt         (new)
            │           │     SelectionRange.kt         (new)
            │           │     FoldRegion.kt             (moved from algorithm/)
            │           │
            │           └── theme/
            │                 EditorColors.kt
            │                 EditorTypography.kt       (renamed from CodeEditorTokens)
            │                 EditorTokens.kt           (layout constants split from tokens)
            │
            └── commonTest/
                └── kotlin/
                    └── com/devuloopers/knet/ui/desktop/codeeditor/
                        ├── api/
                        │     PublicApiTest.kt
                        ├── algorithm/
                        │     UndoRedoManagerTest.kt
                        │     FoldManagerTest.kt
                        │     BracketMatcherTest.kt
                        │     AutoIndentEngineTest.kt
                        │     LineLayoutEngineTest.kt
                        ├── syntax/
                        │     TokenizerTest.kt
                        │     HighlighterTest.kt
                        │     LanguageRegistryTest.kt
                        ├── component/
                        │     EditorToolbarTest.kt
                        │     EditorGutterTest.kt
                        │     ContextMenuTest.kt
                        ├── model/
                        │     EditorStateTest.kt
                        │     SelectionRangeTest.kt
                        │     CursorPositionTest.kt
                        ├── theme/
                        │     EditorThemeTest.kt
                        └── MigrationRegressionTest.kt
```

---

# 📦 Public API

The editor exposes a **deliberately minimal public API**.

Public:
```kotlin
// api/KNetCodeEditor.kt
@Composable
fun KNetCodeEditor(
    code: String,
    mode: EditorMode,
    modifier: Modifier = Modifier,
    languageHint: String? = null,         // ← plain String, NOT BodyFormat
    searchQuery: String = "",
    isFoldingEnabled: Boolean = true,
    showLineCountHeader: Boolean = true,
    showFoldActionsHeader: Boolean = true,
    isWordWrapEnabled: Boolean = true
)

// api/EditorMode.kt
sealed interface EditorMode {
    data class Editable(val onCodeChange: (String) -> Unit, ...) : EditorMode
    data object ReadOnly : EditorMode
}
```

Everything else must be `internal`:
- `FoldManager`, `UndoRedoManager`, `BracketMatcher`
- All tokenizers, registries, renderers
- All internal Compose widgets

Consumers must never directly depend on internal classes. This allows internal refactoring without breaking downstream modules.

---

# 🔑 Key Architectural Decision — Remove `:engine:formatter` Dependency

The current `codeEditorUI` depends on `:engine:formatter` only to accept `BodyFormat?` in `KNetCodeEditor` and `CodeHighlighterRegistry.resolve()`.

**This couples a pure UI framework to an engine module — that is wrong.**

### Fix

Replace `BodyFormat?` in the public API with `languageHint: String?`:

```kotlin
// BEFORE (wrong — engine dependency in UI framework)
KNetCodeEditor(code, mode, bodyFormat = BodyFormat.Json)

// AFTER (correct — plain String hint)
KNetCodeEditor(code, mode, languageHint = "json")
```

The `BodyFormat → languageHint` mapping moves to the caller side:

```kotlin
// In :ui:desktop:inspector or :ui:desktop:apistudio:
val langHint = when (transaction.bodyFormat) {
    is BodyFormat.Json -> "json"
    is BodyFormat.Html -> "html"
    is BodyFormat.Xml  -> "xml"
    else               -> null
}
KNetCodeEditor(code = body, mode = EditorMode.ReadOnly, languageHint = langHint)
```

This makes `CodeHighlighterRegistry` resolve purely by `String` language ID, and the editor has **zero dependency on `:engine:formatter`**.

---

# 🧩 Supported Languages

Initial languages (migrated from existing):
- JSON
- HTML
- XML
- JavaScript
- CSS
- Plain Text

Future additions require only a new `CodeLanguageHighlighter` implementation and a registry entry:
- Kotlin
- SQL, YAML, TOML, GraphQL, Markdown, Protobuf, Java, Rust, C/C++

---

# 📦 Dependencies

### Required
```kotlin
implementation(libs.compose.runtime)
implementation(libs.compose.foundation)
implementation(libs.compose.material3)
implementation(libs.compose.ui)
implementation(libs.compose.components.resources)
implementation(compose.materialIconsExtended)
```

### Optional (evaluate necessity)
```kotlin
api(project(":core:domain"))          // Only if domain models are genuinely needed
```

### Removed
```kotlin
api(project(":engine:formatter"))     // ← REMOVED. BodyFormat → languageHint String in callers.
```

### Must NOT Depend On
- `:engine:proxy`
- `:engine:formatter`
- `:engine:script`
- `:core:http`
- Any database or business logic module

---

# 📋 Current Content Inventory

## Source Files (25 total)

### Root public entry
| File | Current Package | Description |
|:---|:---|:---|
| `KNetCodeEditor.kt` | `editor` | Public composable. Dispatches to `EditableCodeEditor` or `ReadOnlyCodeViewer`. Handles 100k+ line payload truncation via coroutine background offload. |

### model/
| File | Description |
|:---|:---|
| `EditorMode.kt` | Sealed interface: `Editable(onCodeChange, placeholder, textColor)` and `ReadOnly`. |

### engine/ → algorithm/
| File | Description |
|:---|:---|
| `AutoIndentEngine.kt` | Enter-key auto-indentation from prior line indent level. |
| `BracketMatcher.kt` | Open/close bracket pair matcher `{}`, `[]`, `()`. |
| `FoldManager.kt` | Fold region calculation engine. LRU cache (32 entries), 5,000-line safety cap. Includes `buildVisualLineMap()`. |
| `FoldToggleEngine.kt` | `performFoldToggle()` and `collapseAllFolds()` — text-based fold: physically inserts/removes lines from displayed string. |
| `LineLayoutMeasurer.kt` | Reads `TextLayoutResult.onTextLayout` to extract per-line Y-offset Dp values for gutter alignment. |
| `UndoRedoStack.kt` | `ArrayDeque`-based undo/redo history of text snapshots. |

### highlighter/ → syntax/
| File | Description |
|:---|:---|
| `CodeLanguageHighlighter.kt` | Interface: `languageId`, `calculateFoldRanges()`, `resolveClosingSymbol()`, `highlightLine()`. |
| `CodeSyntaxHighlighter.kt` | Full `AnnotatedString` pipeline — calls registry and applies `SpanStyle` colors. |
| `CodeHighlighterRegistry.kt` | Strategy resolver by `BodyFormat` (to be replaced with `String` languageId). |
| `TokenMaker.kt` | Zero-allocation FSM tokenizer utilities. `isOnlyWhitespaceBetween()` and JSON token detection. |
| `FsmTokenMakerVisualTransformation.kt` | Compose `VisualTransformation` wrapping FSM tokenizer. 16-entry LRU cache. |
| `JsonLanguageHighlighter.kt` | JSON token syntax: keys, values, numbers, booleans, null, structure. |
| `HtmlLanguageHighlighter.kt` | HTML tag names, attributes, attribute values, text content. |
| `XmlLanguageHighlighter.kt` | XML tag names, attributes, CDATA, comments. |
| `JsLanguageHighlighter.kt` | JavaScript: keywords, strings, numbers, comments. |
| `CssLanguageHighlighter.kt` | CSS: selectors, properties, values, units. |
| `PlainTextLanguageHighlighter.kt` | No-op pass-through. |
| `TagMarkupHighlighter.kt` | Shared HTML/XML tag-attribute logic base. |

### tokens/ → theme/
| File | Description |
|:---|:---|
| `CodeEditorTokens.kt` | Layout constants: `FontSize (11.sp)`, `LineHeight (18.sp)`, gutter sizes, `editorTextStyle()` factory. |
| `EditorColors.kt` | Color palette: `BackgroundDark`, `ActiveBlue`, `TextSecondary`, `BorderDark`, token highlight colors. |

### widget/ → component/
| File | Description |
|:---|:---|
| `EditorGutter.kt` | 2-column sidebar: line numbers aligned to Y-offset measurements, fold toggle icons. |
| `EditorHeaderToolbar.kt` | Top bar: line count, truncation warning, Copy All, Expand/Collapse All. |
| `KNetContextMenu.kt` | Custom `TextContextMenu`: Copy Selected, Copy Formatted Body, Expand All Blocks. |

### Tests
| File | Description |
|:---|:---|
| `EditorPerformanceBenchmarkTest.kt` | LRU cache cold/warm latency, zero-allocation whitespace benchmark, fold memoization, 100k-line coroutine processing. |

---

# 🔄 Migration Phases

## Phase 1 — Create Module
Create `ui/desktop/codeeditor/build.gradle.kts` with updated dependencies (removing `:engine:formatter`).  
Register `:ui:desktop:codeeditor` in `settings.gradle.kts`.  
Remove `:codeEditorUI` from `settings.gradle.kts`.

## Phase 2 — Copy & Reorganize Sources
Move all source files into the new internal package layout:

| Old location | New location |
|:---|:---|
| `editor/` root | `api/` |
| `editor/model/` | `api/` (merged with root) |
| `editor/engine/` | `algorithm/` |
| `editor/highlighter/` | `syntax/language/`, `syntax/tokenizer/`, `syntax/highlighter/`, `syntax/registry/` |
| `editor/tokens/` | `theme/` |
| `editor/widget/` | `component/` |

## Phase 3 — Package Rename

```text
com.devuloopers.knet.editor.*
        ↓
com.devuloopers.knet.ui.desktop.codeeditor.*
```

## Phase 4 — Internal Visibility
Mark all non-public classes `internal`. Only `KNetCodeEditor` and `EditorMode` remain public.

## Phase 5 — Remove `BodyFormat` Dependency
- Remove `bodyFormat: BodyFormat?` parameter from `KNetCodeEditor`
- Replace `CodeHighlighterRegistry.resolve(BodyFormat?)` with `resolve(languageHint: String?)`
- Update `:ui:desktop:inspector` and `:ui:desktop:apistudio` callers to pass `languageHint` string instead

## Phase 6 — Update Callers

| Module | Change |
|:---|:---|
| `:ui:desktop:apistudio` | `project(":codeEditorUI")` → `project(":ui:desktop:codeeditor")` + import update |
| `:ui:desktop:inspector` | Same |
| `:ui:desktop:scripting` | Same |
| `sharedUI` (legacy) | Same, during sharedUI deprecation |

## Phase 7 — Write Tests
Create full test suite as defined in the directory structure above.

## Phase 8 — Delete Old Module
Delete `codeEditorUI/` entirely after all consumers compile successfully.

---

# 🧪 Verification Commands

```powershell
.\gradlew.bat :ui:desktop:codeeditor:compileKotlinJvm
.\gradlew.bat :ui:desktop:codeeditor:jvmTest
.\gradlew.bat :ui:desktop:apistudio:jvmTest
.\gradlew.bat :ui:desktop:inspector:jvmTest
.\gradlew.bat :ui:desktop:scripting:jvmTest
.\gradlew.bat compileKotlinJvm
```

---

# ✅ Verification Criteria

Migration is complete when:

- `:ui:desktop:codeeditor` compiles successfully
- All existing editor functionality behaves identically
- Read-only mode works correctly
- Editable mode works correctly
- Undo / Redo behaves correctly
- Code folding behaves correctly
- Syntax highlighting applies correctly for all languages
- Large files (100k+ lines) render efficiently with truncation
- No feature-specific logic (Inspector, API Studio, Scripting) exists in the editor module
- Only `KNetCodeEditor` and `EditorMode` are public — all other classes are `internal`
- `BodyFormat` is not imported anywhere in the editor module
- Public API remains stable across all callers
