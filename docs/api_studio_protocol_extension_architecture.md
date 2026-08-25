# API Studio Protocol Extension Architecture

Status: **implemented for HTTP/GraphQL and native gRPC**

## Stable design

API Studio has one shell and one Collections sidebar. HTTP/GraphQL continue to use the canonical typed
`SavedApiRequest`; contributed editors such as gRPC use `ApiStudioWorkspaceDocument`, an opaque and versioned
document that may represent an incomplete draft. Both appear as `SidebarRequestItem` and therefore share search,
selection, rename, delete, unsaved-session creation, saved collections, and draft promotion.

```text
CollectionsSidebar
        |
        v
protocol-neutral SidebarRequestItem
        |
        +--> HTTP editor --------> SavedApiRequest / CollectionsRepository
        |
        +--> contributed editor -> ApiStudioWorkspaceDocumentStore -> Room
                                      ^
                                      |
                              editor-owned payload codec
```

The shell knows only:

- `ApiStudioEditorId` for routing to an editor contribution;
- `RequestKindId` and `badgeLabel` for semantic presentation;
- document identity, location, name, and `RequestNameOrigin`;
- opaque payload version and defensively copied bytes.

It never decodes protobuf, WebSocket frames, GraphQL syntax, or future protocol payloads. Room stores the same
neutral envelope and also does not decode it.

Selecting an editor tab is navigation only. The shell remembers one selected document per contributed editor and
shows a neutral empty state when none exists. A Room draft is created only by the sidebar New action or the empty
state's explicit New button; merely visiting gRPC or another future editor never creates an unsaved session.

## Contribution contract

An independently implementable editor contributes `ApiStudioWorkspaceContribution`:

1. a stable, normalized `editorId`;
2. its initial semantic `RequestKindId` and tab label;
3. `createInitialDocument(id)`, producing an incomplete but persistable draft;
4. `Content(documentId, modifier)`, which restores and renders the exact selected document.

The editor owns a versioned codec between its UI state and opaque payload. Every authoring change is auto-saved
through `UpdateApiStudioWorkspaceContentUseCase`. Content updates may change the generated name, badge, and semantic
kind, but cannot silently change collection placement. User-defined names are preserved by the storage adapter.

At execution time only, the editor validates the draft and creates a strict protocol execution document through
the application authoring port. Consequently, incomplete targets, missing schemas, and unfinished metadata may be
saved without weakening runtime validation.

## Adding another protocol editor

Add, without modifying the common sidebar or Room schema:

1. A focused UI module with `MODULE.md`.
2. An editor state and versioned opaque codec with round-trip and unsupported-version tests.
3. An `ApiStudioWorkspaceContribution` that creates an initial draft and renders a selected document ID.
4. Application authoring/execution adapters for its `RequestKindId` when the protocol is executable.
5. Engine/runtime implementation isolated from Compose, Room, and the common shell.
6. Product DI bindings that register the contribution, codec, authoring adapter, and executor.
7. Qualification tests for incomplete-draft restart, autosave ordering, collection promotion, execution,
   cancellation, and actual negotiated protocol reporting.

Examples:

- WebSocket adds a WebSocket editor/codec and session executor; saved collections remain unchanged.
- SSE can continue using the HTTP editor when it only authors an HTTP request, while its response inspector is a
  separate additive renderer. It needs a new editor only if KNet later adds SSE-specific authoring state.
- GraphQL remains an HTTP-editor format because it shares HTTP transport state and only changes semantic naming,
  body editing, badges, and inspection.
- gRPC uses the contributed-editor path because descriptors, RPC cardinality, metadata, and duplex messages do not
  fit the HTTP request editor without coupling it to protobuf.

Do not add a protocol enum switch to `ApiStudioScreen`, a protocol-specific collection table, a second sidebar, or
engine objects in persisted/UI contracts. A new editor should be additive at composition time.
