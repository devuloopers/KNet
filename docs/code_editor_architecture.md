# Kotlin Code Editor Architecture

## Status and intent

This document is the architecture source of truth for `:ui:desktop:codeEditor`. The module is the
shared editing surface for KNet payloads and scripts and is deliberately independent from HTTP and
feature state. It is also the extraction-ready foundation for a future standalone Kotlin code-editor
repository.

Autocomplete and completion providers are not part of this design. The absence is intentional; no
placeholder completion abstraction is included.

## Stable boundaries

| Boundary | Responsibility | May depend on |
|---|---|---|
| `document` | Positions, ranges, directional selection, versioned snapshots, edits, chunk sharing, delta history | Kotlin standard library |
| `session` | Single mutation owner for document, caret, selection, undo/redo, and events | `document` |
| `command` | Strongly typed, platform-neutral editor intent | `session`, `document` |
| `search` | Non-destructive find/replace in document coordinates | `document`, `session`, `concurrency` |
| `language` | Extensible language registry and optional language capabilities | `document`, folding contract, `concurrency` |
| `concurrency` | Framework-neutral cooperative cancellation checkpoint | Kotlin standard library |
| `api` | Cohesive Compose State/Actions/Configuration/Style entry point | All stable editor boundaries |
| rendering packages | Virtualization, pointer/keyboard input, clipboard, semantic colors, folds | `api`, document/language projections, Compose |

Feature modules may use `api`, `document`, `session`, `command`, `search`, `language`, and the public
theme types. Rendering implementation packages are internal.

## State and ownership

`EditorSession` is the single source of truth for mutable editor behavior. It owns one
`EditorDocument`, one bounded `EditorUndoManager`, the caret, a directional selection, and session
listeners. `CodeEditorState` is only a Compose-observable adapter; it never owns a second mutable
line list.

`EditorDocumentSnapshot` is immutable. A snapshot may be retained by rendering or background
language work while the session publishes a newer version. Every accepted mutation produces an
`EditorDocumentChange` containing both coordinate ranges and only the removed/inserted fragments.
Undo, redo, syntax invalidation, and consumer callbacks use those deltas.

Directional selection stores an anchor and active endpoint. Its normalized range is derived. This
avoids the common backward-selection bug where normalization destroys the Shift/drag anchor.

## Large-document behavior

The default document stores lines in immutable bounded chunks. A normal one-line edit rebuilds one
chunk and reuses every unaffected chunk. Full text is not a reactive state value; callers must
explicitly invoke `snapshot.text()`.

The primary stateful Compose API emits exact `EditorSessionEvent` deltas. The controlled-string API
exists for ordinary feature state and performs full serialization only when its optional
`onTextChange` callback is present.

Syntax state is also chunked. Stateful tokenizers receive the preceding line's lexical state, so
multiline strings and comments remain correct. After an edit, tokenization begins at the first
affected line and stops when text and incoming lexical state converge. Unchanged token chunks are
reused instead of rebuilding a document-sized reference list on every key press.

An ordinary edit also receives a bounded immediate presentation projection before the next frame. The directly
changed lines are retokenized against the new snapshot, while the previous prefix and suffix token chunks remain
visible until authoritative background convergence completes. This prevents the entire viewport from alternating
between semantic colors and plain text on every keystroke. Immediate work is capped by changed-line and character
budgets; an oversized edited line is temporarily plain rather than blocking the UI thread, and unrelated lines
remain stable.

The `LazyColumn` composes only visible lines. Exactly one active line owns a `BasicTextField`; other
visible lines are lightweight text rows. The optional non-wrapped mode shares one horizontal scroll state.
The normal unfolded path uses an identity logical-to-visual mapping and allocates fold arrays only when folds
are collapsed.

Word wrapping is enabled by default, keeping code and payload text inside the viewport without changing the
document. One logical line may therefore occupy multiple variable-height visual rows while retaining one gutter
line number. Compose text layout owns visual wrapping and offset geometry; pointer hit testing and selection
painting consume that measured geometry. Up/Down remains inside the active text field while another wrapped
visual row exists and crosses to an adjacent logical line only at the first or final visual row. A standalone
consumer may explicitly disable wrapping to opt into the retained horizontal-scroll mode.

Pointer drag ownership is selected once at the initial press and retained until release. This prevents
downward text selection from being cancelled when the pointer crosses the horizontal scrollbar while still
allowing gestures that begin on either scrollbar to remain exclusively scrollbar-owned.
When wrapping is active, the nonexistent horizontal scrollbar has no bottom-edge hit zone, so selection and
vertical auto-scroll remain available across the complete text viewport.

Selection ranges are projected into stable per-line paint bounds. Native text paths paint selected
characters, selected logical newlines use one trailing character cell, and an exclusive end position at
column zero produces no transient row paint. The editable active-line input suspends focus/caret publication
for the duration of viewport drag selection so focus transfer cannot clear the canonical session selection.
The keyed logical row, rather than either renderer child, owns its latest compatible `TextLayoutResult`.
Switching a row between lightweight read-only text and its active `BasicTextField` therefore does not fall
back to estimated geometry. Pointer-down also publishes selection-gesture ownership immediately, suppressing
native caret and focus side effects before the range gains length. Active-line paint is an independent layer
derived only from the caret line, so selecting or clearing text on that line never toggles its background.
Selection still paints actual space characters at their document columns, but never paints unused viewport
width beyond the logical end of line.
Read-only and editable content share the gutter's minimum logical-line height. Enabling wrapping removes the
fixed row height but not this minimum, so a selection painter owns every pixel between adjacent logical rows;
multi-visual-line content remains free to expand without changing typography or document coordinates.

## Background work and stale-result prevention

Syntax, folding, and search receive immutable snapshots and run on `Dispatchers.Default` from a
snapshot-version keyed `LaunchedEffect`. Starting a newer version cancels the prior job. Core loops
call `EditorCancellationCheckpoint` every bounded block so cancellation is observed during large
documents, not only after a full scan.

Results carry or retain their source document version. Rendering accepts token/search results only
for the current snapshot, preventing a slower older computation from being applied to newer text. A synchronous,
current-version presentation model bridges normal edits until the background result is ready; it never publishes
an older version as though it were current.

## Language contribution model

`CodeLanguage` is an extensible sealed identifier: built-ins are strongly typed and unknown
languages remain `CodeLanguage.Custom` instead of being silently converted to plain text.

`EditorLanguageSupport` composes independent optional capabilities:

- stateful syntax tokenization;
- folding;
- indentation;
- bracket pairs;
- comment delimiters;
- aliases and MIME types.

A language does not implement capabilities it does not need. Registry construction validates
canonical identifiers, aliases, and MIME types once. Registry extension returns a new immutable
registry, so editor instances may safely use different language sets.

Example contribution:

```kotlin
val kotlinLanguage = CodeLanguage.Custom("kotlin", "Kotlin")
val kotlinSupport = EditorLanguageSupport(
    language = kotlinLanguage,
    aliases = setOf("kt", "kts"),
    mimeTypes = setOf("text/x-kotlin"),
    tokenizer = KotlinSyntaxTokenizer(),
    indentationProvider = KotlinIndentationProvider()
)

val registry = BuiltInEditorLanguages.registry.with(listOf(kotlinSupport))
```

No editor-core branch changes are required. Tokenizers return semantic categories, never Compose
colors, and custom semantic categories are mapped through `CodeEditorSemanticColors.custom`.

The Compose adapter consumes these capabilities directly: registered bracket pairs drive automatic
closing insertion, Ctrl/Cmd+/ uses the registered line or block comment delimiters, indentation
drives line splitting, and folding remains language-owned.

## Search and commands

Search is a projection over an immutable snapshot; it never filters the displayed lines. Literal,
case-sensitive, whole-word, and line-oriented regular-expression modes return typed results and a
typed invalid-regex failure. Replacement uses session edits, and replace-all is one atomic undo
group. The built-in Ctrl/Cmd+F panel is a Compose adapter over this state and is available in both
editable and read-only surfaces; mutation controls are hidden in read-only mode.

`EditorCommandDispatcher` converts platform-neutral commands to session operations. Custom commands
use validated namespaced identifiers and ordered external handlers. Platform key handling remains a
Compose adapter concern, keeping the command/session foundation reusable by another UI toolkit.

## Consumer guidance

Use the stateful API for large documents, long-lived editors, or consumers that can process deltas:

```kotlin
val editorState = rememberCodeEditorState(initialText)
KNetCodeEditor(
    state = editorState,
    configuration = CodeEditorConfiguration(
        mode = EditorMode.Editable,
        language = CodeLanguage.JSON
    ),
    actions = CodeEditorActions(onDocumentChange = ::handleEditorChange)
)
```

Use the controlled-string adapter when the owning feature model stores a complete string and its
documents are reasonably sized. Supplying `onTextChange` explicitly opts into serialization.

Formatting remains consumer-owned because JSON, GraphQL, scripts, and future languages may use
different engines and error policies.

## Standalone repository extraction

The present Gradle module intentionally keeps the UI-neutral and Compose layers in separate
packages rather than adding more KNet Gradle modules. When the standalone repository is created,
those packages can become `editor-core` and `editor-compose` artifacts without changing their
contracts. The Compose artifact will replace the narrow KNet `:ui:core` adapters with standalone
clipboard, context-menu, and design-token implementations.

This is a packaging operation, not an editor-engine rewrite.
