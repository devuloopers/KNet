# `:ui:desktop:codeEditor`

## Responsibility

This module is KNet's reusable, HTTP-independent source and payload editor. It provides a versioned
document, an editor session, delta-based history, search, an additive language SPI, and a virtualized
Compose desktop surface. Traffic, API Studio, GraphQL, and scripting may consume it, but their models
and behavior must never be moved into this module.

The detailed design and extension guide is the repository-level
[`docs/code_editor_architecture.md`](../../../docs/code_editor_architecture.md).

## Owns

- Immutable, versioned document snapshots and position/range/selection coordinates.
- A chunked document implementation that shares unchanged line chunks between versions.
- Directional selection, caret, selection replacement/deletion, editing commands, and one session mutation owner.
- Delta-based bounded undo and redo history.
- Literal and regular-expression find/replace in document coordinates.
- Language registration and optional syntax, folding, indentation, bracket, and comment capabilities.
- Stateful cross-line tokenization and incremental token projection.
- Bounded immediate syntax presentation that keeps unchanged semantic colors stable between a document edit
  and authoritative background-token convergence.
- Compose state, semantic-token rendering, virtualized lines, keyboard/pointer and IME interaction, folding,
  bracket auto-closing, language-aware comment toggling, built-in find/replace, clipboard actions,
  theming, and the controlled-string compatibility facade.
- A structurally stable editor toolbar whose fold-action slot is owned by configuration plus language capability;
  asynchronous fold results enable or disable its actions without adding, removing, or shifting toolbar controls.
- An ordered consumer-action rendering boundary. Features declare callback-free `CodeEditorHeaderAction` values
  with existing `EditorCommandId.Custom` identities and handle them through the generic `CodeEditorActions.onCommand`
  dispatcher. Stable command identities prevent asynchronous enabled-state changes from recreating toolbar nodes.
- Viewport-contained word wrapping by default, variable-height logical rows, and wrapped-row-aware caret
  navigation. Horizontal scrolling remains an explicit standalone-consumer opt-out.
- Cooperative cancellation checkpoints for syntax, folding, and search work.

## Does not own

- HTTP request/response models, traffic capture, API Studio state, GraphQL transport semantics, or
  script execution.
- Persistence, files, networking, dependency injection, or feature navigation.
- Payload formatting or other format-specific command behavior. Consumers may expose these operations through
  declarative header actions and the generic custom-command dispatcher.
- Autocomplete, completion providers, or language servers. These are intentionally outside the
  current scope.

## Public entry points

- `KNetCodeEditor(state, configuration, actions, registry, style)` is the scalable primary API.
- `KNetCodeEditor(code, configuration, actions, registry, style)` is the controlled-string adapter.
  It serializes full text only when `CodeEditorActions.onTextChange` is supplied.
- `CodeEditorState` observes one `EditorSession` without copying all lines into Compose state.
- `EditorSession`, `EditorDocument`, and `EditorDocumentSnapshot` are UI-neutral editing boundaries.
- `EditorLanguageRegistry` and `EditorLanguageSupport` are the additive language extension boundary.
- `EditorSearchEngine` and `EditorSearchSession` provide UI-neutral find/replace behavior.
- `EditorCommandDispatcher` maps platform-neutral commands to a session and accepts custom handlers.
- `CodeEditorHeaderAction` projects a consumer-owned `EditorCommandId.Custom` into the toolbar without teaching
  editor core the command's format semantics.

Everything under `component`, `gesture`, `modifier`, `render`, `shortcut`, and `viewport` is an
implementation detail. Feature modules must not depend on those packages.

## Dependency direction

```text
HTTP panel / Traffic / API Studio / Script UI
                    |
                    v
         codeEditor public API + language SPI
                    |
          +---------+----------+
          |                    |
          v                    v
 document/session/search   Compose viewport/rendering
```

The module depends only on Compose, coroutines, and reusable `:ui:core` desktop facilities. It has
no dependency on KNet domain, traffic, application, engine, storage, or product modules.

## Runtime flow

1. A consumer creates or remembers `CodeEditorState`.
2. UI intent is translated into an `EditorTextEdit` or session command. Consumer header actions emit their
   existing custom command identity back through `CodeEditorActions.onCommand`.
3. `EditorSession` is the only document/history/caret/selection mutation owner.
4. `ChunkedEditorDocument` publishes a new immutable snapshot and exact change delta.
5. Compose observes the snapshot and immediately projects small syntax changes onto the new version without
   discarding unchanged token chunks.
6. Authoritative syntax, folding, and search work runs on a worker dispatcher using the immutable snapshot and
   cooperative cancellation.
7. Only visible logical lines are composed. Syntax and document projections reuse unchanged chunks.
8. Consumers receive exact session events; full-string serialization is explicit and opt-in.

A workspace with multiple logical documents retains one `CodeEditorState` per document. Switching the presented
state must not replace one session's complete text with another document, because that would discard document-local
history and leak caret or selection ownership between tabs.

Pointer ownership is fixed at the initial primary-button press. A gesture that starts over text keeps
selection and auto-scroll ownership even after crossing a scrollbar hit zone; a gesture that starts on a
scrollbar remains scrollbar-owned until release.
Wrapped viewports do not reserve a bottom scrollbar hit zone because no horizontal scrollbar is rendered.

Viewport selection painting is deterministic before and after a row receives native text-layout data.
Selected line breaks occupy one character cell, zero-width range endpoints do not paint, and active-line
text input does not publish focus-driven caret changes while a pointer drag selection is in progress.
The keyed logical row retains compatible measured geometry while its read-only and editable renderers swap,
while pointer-down gesture ownership protects selection from native caret and focus side effects before the
selection has non-zero length. Active-line paint depends only on the caret line and remains stable while a
selection is created or cleared on that line. Real whitespace remains selectable; unused viewport width never
becomes a selection rectangle.
The active line reads pointer ownership directly during native callbacks, so a double-click word range cannot be
cleared by a line-field caret event later in the same pointer dispatch. After a document range is established, an
invisible Compose text-input bridge owns keyboard, dead-key, and IME composition until the active line safely
retakes focus. Committed text dispatches the existing typed insertion command and therefore replaces single-line,
reverse, multiline, and whole-document ranges through the same session transaction.
Both renderer content surfaces retain the gutter's minimum logical-line height even when wrapping is enabled,
so selection paint is continuous between logical rows while wrapped content can still grow naturally.
Selection paint owns each complete visual-line slot, including the leading around centred text, so adjacent
selected lines have no seams while typography, baselines, and vertical line spacing remain unchanged.

The default keyboard adapter provides Ctrl/Cmd+F for find/replace and Ctrl/Cmd+/ for the active
language's line or block comment capability. Ctrl/Cmd+A retains the current viewport while selecting the complete
document; the session still records the correct directional selection and document-end active caret. Partial
selection, search navigation, ordinary caret movement, editing, and drag-selection auto-scroll continue to reveal
their active position. Backspace and Delete first dispatch the typed `DeleteSelection` command against the live
session. The viewport consumes the key only when that command deletes a non-empty selection; otherwise the focused
line editor retains ordinary character and line-boundary deletion behavior. Select All transfers focus to the
editor input boundary without moving the viewport, so deletion or replacement remains immediately available even
when the document-end caret is not composed.

## Memory and concurrency rules

- Session mutation and listener delivery stay on the owning UI/controller thread.
- Immutable snapshots may be read by background workers.
- A one-line edit copies at most one bounded document chunk plus the outer chunk index.
- History retains changed fragments, not complete document snapshots.
- Incremental syntax retains unchanged token chunks and cross-line lexical state.
- Immediate presentation retokenizes at most 32 changed lines and 32 KiB of changed text on the UI thread;
  larger edited lines are temporarily unstyled until background convergence.
- The ordinary unfolded viewport uses an identity map without per-line mapping arrays.
- Collapsed-fold mappings allocate only while folds are actually collapsed.
- Word wrapping changes only visual layout. It never inserts document newlines, changes logical line numbers,
  or alters serialized request, response, GraphQL, or script text.
- Long-running syntax, folding, and search loops invoke cancellation checkpoints every bounded block.

## Language extension rule

Adding a language is additive:

1. Define a `CodeLanguage.Custom` identifier.
2. Provide only the `EditorLanguageSupport` capabilities the language actually needs.
3. Add the support to a registry using `BuiltInEditorLanguages.registry.with(...)`.
4. Pass that registry and language through `CodeEditorConfiguration`.

Do not add a central `when` branch, modify the editor session, or introduce Compose types into a
tokenizer/folding provider.

## Extraction boundary

For a future standalone Kotlin code-editor repository, move the packages as two source sets:

- UI-neutral foundation: `document`, `session`, `command`, `search`, `language`, `concurrency`, and
  `model.CodeLanguage`.
- Compose adapter: `api`, `component`, `gesture`, `modifier`, `render`, `theme`, and `viewport`.

No KNet HTTP, proxy, persistence, or product model migration is required. The only KNet-specific
integration to replace is the small `:ui:core` clipboard/context-menu/design-token adapter.
