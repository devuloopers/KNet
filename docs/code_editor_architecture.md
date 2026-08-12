# KNet Code Editor Module Architecture (`:ui:desktop:codeEditor`)

This document describes the complete directory layout, package responsibilities, and single-responsibility algorithm engines governing the KNet Code Editor.

---

## 📁 Directory Structure Overview

```
com.devuloopers.knet.ui.desktop.codeeditor/
├── 📁 api/                      # Top-level public entry-point APIs & modes
│   ├── EditableCodeEditor.kt
│   ├── EditorMode.kt
│   ├── KNetCodeEditor.kt
│   └── ReadOnlyCodeViewer.kt
├── 📁 algorithm/                # Core editing algorithms & single-responsibility engines
│   ├── AutoIndentEngine.kt
│   ├── AutoScrollController.kt
│   ├── BracketMatcher.kt
│   ├── DocumentBuffer.kt
│   ├── DocumentLayoutMap.kt
│   ├── DocumentPreviewGenerator.kt
│   ├── FoldManager.kt
│   ├── FoldToggleEngine.kt
│   ├── LazyLine.kt
│   ├── LazyLineVisibilityEngine.kt
│   ├── LineLayoutMeasurer.kt
│   ├── PasteEngine.kt
│   ├── PointerHitTestEngine.kt
│   ├── SelectionEngine.kt
│   ├── UndoRedoStack.kt
│   └── WordBoundaryEngine.kt
├── 📁 component/                # Core UI composables & layout viewport
│   ├── EditorCaretState.kt
│   ├── EditorGutter.kt
│   ├── EditorHeaderToolbar.kt
│   ├── EditorTextContextMenu.kt
│   ├── KNetContextMenu.kt
│   ├── LazyCodeBody.kt
│   ├── LazyCodeBodyMode.kt
│   └── 📁 viewport/              # Row-level virtualized viewport rendering
│       ├── EditableLineContent.kt
│       ├── LazyCodeBodyContent.kt
│       ├── LazyCodeGutterSlot.kt
│       ├── LazyCodeLineRow.kt
│       └── ReadOnlyLineContent.kt
├── 📁 gesture/                  # Pointer gesture processing & selection handlers
│   ├── MultiClickGestureHandler.kt
│   └── SelectionGestureHandler.kt
├── 📁 inspector/                # Higher-level domain inspector components
│   ├── KNetRequestEditorView.kt
│   └── KNetResponseInspector.kt
├── 📁 model/                    # Immutable state models & data structures
│   ├── EditorSelection.kt
│   ├── LineSelectionBounds.kt
│   └── PreparedDocument.kt
├── 📁 modifier/                 # Custom Compose layout & pointer input modifiers
│   ├── EditorPointerInputModifier.kt
│   └── SelectionHighlightModifier.kt
├── 📁 service/                  # Background worker services
│   └── DocumentPreparationService.kt
├── 📁 shortcut/                 # Key event parsing & editor shortcut execution
│   ├── EditorShortcutHandler.kt
│   └── LineKeyNavigationHandler.kt
├── 📁 syntax/                   # Highlighting algorithms, tokenizers & language rules
│   ├── CodeHighlighterRegistry.kt
│   ├── CodeLanguageHighlighter.kt
│   ├── CodeSyntaxHighlighter.kt
│   ├── CssLanguageHighlighter.kt
│   ├── HtmlLanguageHighlighter.kt
│   ├── JsLanguageHighlighter.kt
│   ├── JsonLanguageHighlighter.kt
│   ├── PlainTextLanguageHighlighter.kt
│   ├── TagMarkupHighlighter.kt
│   ├── TokenMaker.kt
│   └── XmlLanguageHighlighter.kt
└── 📁 theme/                    # Token constants, color palettes, and typography
    ├── CodeEditorStyle.kt
    ├── CodeEditorTokens.kt
    └── EditorColors.kt
```

---

## 🗂️ Package Breakdown & Component Responsibilities

### 🔌 1. API Package (`com.devuloopers.knet.ui.desktop.codeeditor.api`)
- [KNetCodeEditor.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/api/KNetCodeEditor.kt): Primary facade entry point choosing between ReadOnly and Editable modes.
- [EditableCodeEditor.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/api/EditableCodeEditor.kt): Full interactive editor container managing undo/redo stack, document buffer, and header toolbar.
- [ReadOnlyCodeViewer.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/api/ReadOnlyCodeViewer.kt): High-performance read-only viewer for HTTP response bodies.
- [EditorMode.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/api/EditorMode.kt): Sealed interface defining `Editable` vs `ReadOnly` operational contracts.

### ⚙️ 2. Algorithm Package (`com.devuloopers.knet.ui.desktop.codeeditor.algorithm`)
- [UndoRedoStack.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/algorithm/UndoRedoStack.kt): 5-rule boundary coalescing undo/redo history engine with selection restoration.
- [SelectionEngine.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/algorithm/SelectionEngine.kt): Single-responsibility engine for fold-aware multi-line text extraction, deletion, and line selection bounds.
- [PointerHitTestEngine.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/algorithm/PointerHitTestEngine.kt): Viewport (X,Y) pixel hit-tester mapping mouse coordinates to raw document lines & columns.
- [DocumentBuffer.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/algorithm/DocumentBuffer.kt): Thread-safe line buffer storing raw document text lines.
- [FoldManager.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/algorithm/FoldManager.kt): AST scanning engine calculating block fold regions (`{...}`, `[...]`).
- [FoldToggleEngine.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/algorithm/FoldToggleEngine.kt): Manages collapsed fold state sets and toggle operations.
- [LazyLineVisibilityEngine.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/algorithm/LazyLineVisibilityEngine.kt): Filters uncollapsed lines and generates display text stubs for collapsed fold rows.
- [AutoIndentEngine.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/algorithm/AutoIndentEngine.kt): Computes auto-indentation spaces on Enter key presses.
- [AutoScrollController.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/algorithm/AutoScrollController.kt): Smooth edge-scrolling controller during drag selection.
- [PasteEngine.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/algorithm/PasteEngine.kt): Multi-line paste insertion engine.
- [BracketMatcher.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/algorithm/BracketMatcher.kt): Pair bracket matching for `()`, `{}`, `[]`.
- [WordBoundaryEngine.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/algorithm/WordBoundaryEngine.kt): Word-level boundary detection for double-click and word navigation.
- [DocumentLayoutMap.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/algorithm/DocumentLayoutMap.kt): TextLayoutResult caching engine.
- [LineLayoutMeasurer.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/algorithm/LineLayoutMeasurer.kt): Line dimension measurement utilities.
- [DocumentPreviewGenerator.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/algorithm/DocumentPreviewGenerator.kt): Truncated preview text stub generator.
- [LazyLine.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/algorithm/LazyLine.kt): Model mapping original line index to visible display text.

### 🎨 3. Component & Viewport Package (`com.devuloopers.knet.ui.desktop.codeeditor.component`)
- [LazyCodeBody.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/component/LazyCodeBody.kt): Virtualized `LazyColumn` body container (188 lines).
- [EditorTextContextMenu.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/component/EditorTextContextMenu.kt): Fold-aware text context menu provider (Copy, Cut, Paste, Select All).
- [KNetContextMenu.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/component/KNetContextMenu.kt): Native right-click context menu composable.
- [EditorHeaderToolbar.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/component/EditorHeaderToolbar.kt): Header toolbar showing line count, language badge, copy all, and fold controls.
- [EditorGutter.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/component/EditorGutter.kt): Standalone gutter column composable.
- [EditorCaretState.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/component/EditorCaretState.kt): 2D caret state (`lineIndex`, `colIndex`).
- [LazyCodeBodyMode.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/component/LazyCodeBodyMode.kt): Enum distinguishing `Editable` vs `ReadOnly` body modes.

#### 🪟 Viewport Subpackage (`...component.viewport`)
- [LazyCodeLineRow.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/component/viewport/LazyCodeLineRow.kt): Top-level row composable for a single line item in `LazyColumn`.
- [EditableLineContent.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/component/viewport/EditableLineContent.kt): Scoped single-line `BasicTextField` container (208 lines).
- [ReadOnlyLineContent.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/component/viewport/ReadOnlyLineContent.kt): High-speed non-editable text line renderer.
- [LazyCodeGutterSlot.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/component/viewport/LazyCodeGutterSlot.kt): Gutter slot with line numbers and 120ms non-flashy animated fold chevrons.
- [LazyCodeBodyContent.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/component/viewport/LazyCodeBodyContent.kt): Inner `LazyColumn` row renderer.

### 👆 4. Gesture Package (`com.devuloopers.knet.ui.desktop.codeeditor.gesture`)
- [SelectionGestureHandler.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/gesture/SelectionGestureHandler.kt): Single-click, drag-selection, and shift-click selection engine.
- [MultiClickGestureHandler.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/gesture/MultiClickGestureHandler.kt): Double-click (select word) and triple-click (select entire line) gesture handler.

### ⌨️ 5. Shortcut Package (`com.devuloopers.knet.ui.desktop.codeeditor.shortcut`)
- [EditorShortcutHandler.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/shortcut/EditorShortcutHandler.kt): Key event parser executing `Ctrl+C`, `Ctrl+X`, `Ctrl+V`, `Ctrl+A`, `Ctrl+Z`, `Ctrl+Y`, and `Backspace`/`Delete`.
- [LineKeyNavigationHandler.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/shortcut/LineKeyNavigationHandler.kt): Single-line arrow key navigation, line splitting, line merging, and Undo/Redo key handler.

### 🔤 6. Syntax Package (`com.devuloopers.knet.ui.desktop.codeeditor.syntax`)
- [TokenMaker.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/syntax/TokenMaker.kt): Tokenizer converting raw strings into syntax-highlighted `AnnotatedString`.
- [CodeSyntaxHighlighter.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/syntax/CodeSyntaxHighlighter.kt): Main syntax highlighting engine.
- [CodeHighlighterRegistry.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/syntax/CodeHighlighterRegistry.kt): Registry mapping language hints (JSON, XML, HTML, JS, CSS) to highlighters.
- [JsonLanguageHighlighter.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/syntax/JsonLanguageHighlighter.kt): Fast JSON tokenizer.
- [XmlLanguageHighlighter.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/syntax/XmlLanguageHighlighter.kt): XML syntax tokenizer.
- [HtmlLanguageHighlighter.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/syntax/HtmlLanguageHighlighter.kt): HTML syntax tokenizer.
- [JsLanguageHighlighter.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/syntax/JsLanguageHighlighter.kt): JavaScript syntax tokenizer.
- [CssLanguageHighlighter.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/syntax/CssLanguageHighlighter.kt): CSS syntax tokenizer.
- [TagMarkupHighlighter.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/syntax/TagMarkupHighlighter.kt): Generic tag markup highlighter.
- [CodeLanguageHighlighter.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/syntax/CodeLanguageHighlighter.kt): Abstract base interface for language highlighters.
- [PlainTextLanguageHighlighter.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/syntax/PlainTextLanguageHighlighter.kt): Fallback plain-text highlighter.

### 📐 7. Model & Modifier Packages
- [EditorSelection.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/model/EditorSelection.kt): 2D selection range (`startLine`, `startCol`, `endLine`, `endCol`).
- [LineSelectionBounds.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/model/LineSelectionBounds.kt): Calculated line selection bounds.
- [PreparedDocument.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/model/PreparedDocument.kt): Async pre-processed document state.
- [EditorPointerInputModifier.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/modifier/EditorPointerInputModifier.kt): Custom Modifier extension for pointer input, drag selection, scrollbar drag locking, and cursor switching.
- [SelectionHighlightModifier.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/modifier/SelectionHighlightModifier.kt): Custom Compose `drawBehind` modifier drawing pixel-perfect blue selection backgrounds.

### 🔍 8. Inspector & Service Packages
- [KNetResponseInspector.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/inspector/KNetResponseInspector.kt): High-level HTTP response inspector with view switching (JSON, Raw, Headers).
- [KNetRequestEditorView.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/inspector/KNetRequestEditorView.kt): High-level HTTP request payload editor view.
- [DocumentPreparationService.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/service/DocumentPreparationService.kt): Coroutine background service for splitting large documents off the main UI thread.

### 🎨 9. Theme Package (`...theme`)
- [CodeEditorTokens.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/theme/CodeEditorTokens.kt): Layout dimensions and font styling tokens.
- [EditorColors.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/theme/EditorColors.kt): Color palette definitions (backgrounds, text, highlights).
- [CodeEditorStyle.kt](file:///Users/devuloopers/Development/KNet/ui/desktop/codeEditor/src/commonMain/kotlin/com/devuloopers/knet/ui/desktop/codeeditor/theme/CodeEditorStyle.kt): Configuration dataclass for editor styling.
