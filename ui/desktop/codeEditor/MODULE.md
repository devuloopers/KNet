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
- Directional selection, caret, editing commands, and one session mutation owner.
- Delta-based bounded undo and redo history.
- Literal and regular-expression find/replace in document coordinates.
- Language registration and optional syntax, folding, indentation, bracket, and comment capabilities.
- Stateful cross-line tokenization and incremental token projection.
- Compose state, semantic-token rendering, virtualized lines, keyboard/pointer interaction, folding,
  bracket auto-closing, language-aware comment toggling, built-in find/replace, clipboard actions,
  theming, and the controlled-string compatibility facade.
- Cooperative cancellation checkpoints for syntax, folding, and search work.

## Does not own

- HTTP request/response models, traffic capture, API Studio state, GraphQL transport semantics, or
  script execution.
- Persistence, files, networking, dependency injection, or feature navigation.
- Payload formatting. Consumers may expose a formatter through `CodeEditorActions.onPrettify`.
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
2. UI intent is translated into an `EditorTextEdit` or session command.
3. `EditorSession` is the only document/history/caret/selection mutation owner.
4. `ChunkedEditorDocument` publishes a new immutable snapshot and exact change delta.
5. Compose observes the snapshot; syntax, folding, and search run on a worker dispatcher using the
   immutable snapshot and cooperative cancellation.
6. Only visible logical lines are composed. Syntax and document projections reuse unchanged chunks.
7. Consumers receive exact session events; full-string serialization is explicit and opt-in.

Pointer ownership is fixed at the initial primary-button press. A gesture that starts over text keeps
selection and auto-scroll ownership even after crossing a scrollbar hit zone; a gesture that starts on a
scrollbar remains scrollbar-owned until release.

Viewport selection painting is deterministic before and after a row receives native text-layout data.
Selected line breaks occupy one character cell, zero-width range endpoints do not paint, and active-line
text input does not publish focus-driven caret changes while a pointer drag selection is in progress.
Selection paint owns each complete visual-line slot, including the leading around centred text, so adjacent
selected lines have no seams while typography, baselines, and vertical line spacing remain unchanged.

The default keyboard adapter provides Ctrl/Cmd+F for find/replace and Ctrl/Cmd+/ for the active
language's line or block comment capability.

## Memory and concurrency rules

- Session mutation and listener delivery stay on the owning UI/controller thread.
- Immutable snapshots may be read by background workers.
- A one-line edit copies at most one bounded document chunk plus the outer chunk index.
- History retains changed fragments, not complete document snapshots.
- Incremental syntax retains unchanged token chunks and cross-line lexical state.
- The ordinary unfolded viewport uses an identity map without per-line mapping arrays.
- Collapsed-fold mappings allocate only while folds are actually collapsed.
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
