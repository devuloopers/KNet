# `:ui:desktop:apiStudio`

## Responsibility

Owns API Studio's desktop shell, shared collection sidebar, HTTP editor, active-document presentation state,
dialogs, contribution SPI, and HTTP response inspection projection.

## Owns

- One active `RequestEditorState` projection and editor-only view selections.
- Lossless conversion between that projection and the canonical `SavedApiRequest` document.
- Typed `HttpMethod` plus ordered `KeyValueEntry` query/header/cookie rows that retain enabled state.
- API Studio ViewModels, components, and the `ResponseInspectorState` UI projection for loading,
  failures, console logs, and assertions.
- Ordered auto-save coordination and explicit restoration/persistence-failure presentation.
- Debounced latest-wins publication of generated session/request titles while preserving user-defined names.
- Sidebar projection of semantic request badges from the shared descriptor use case; the editor method selector
  continues to edit the real HTTP transport method.
- A standalone, selection-stable method selector whose label and chevron form a centered compact group, plus a
  flexible URL authoring field in the request bar.
- A per-document HTTP-version selector bound directly to the canonical authored request. It does not infer the
  response version; response inspection displays the transport's observed `ApplicationProtocol`.
- An execution action that changes from Send to an interactive loading Cancel control without weakening the
  shared button's default duplicate-submit protection.
- Product-configured API Studio capture attribution emitted only while the KNet runtime is running and canonical
  capture is active. Paused capture executes directly even though the persistent listener may keep forwarding
  external-device traffic; direct execution remains private to API Studio and carries no attribution metadata.
- One explicit Collections-header Save action for promoting the active draft, plus an independent Saved Collections
  add action for creating an empty collection. Saved request edits continue to auto-save and do not expose a
  redundant request-bar Save action.
- One protocol-neutral sidebar projection that merges canonical HTTP documents with opaque contributed-editor
  documents. Selection, search, rename, delete, draft creation, and collection promotion use the same UI actions.
- A generic workspace-contribution host keyed by open `ApiStudioEditorId`. Native gRPC and future non-HTTP editors
  contribute initial-draft creation and rendering without importing their engines into this module.
- Side-effect-free editor navigation: switching a protocol tab restores that editor's selected document or renders
  the contribution's transient blank editor. Persistence begins only from an explicit New action or the first
  meaningful authoring mutation; focus and presentation-only selection never create a sidebar row.

## Does not own

- A second canonical HTTP request/response model, contributed-editor payload decoding, protocol execution engines,
  traffic recording, persistence implementations, or product DI bindings.

## Dependency rule

Presentation state maps to one complete `SavedApiRequest`. Runtime work crosses application/domain use cases;
the screen never coordinates hydration or persistence.

## Current state

`ApiStudioViewModel` is the sole active-document owner. `CollectionsViewModel` observes sidebar data and performs
saved collection creation/rename/delete operations. Draft promotion may also create a destination collection
transactionally. Startup restoration loads one request directly; editor changes enter one
serialized,
latest-state auto-save actor; draft promotion is transactional and changes UI identity only after success.
Generated titles are recomputed from immutable canonical request snapshots off the UI dispatcher and persisted
with their ownership. Manual save-dialog or sidebar renames disable future automatic replacement.
Saved and unsaved sidebar rows derive their badge from the same canonical descriptor pipeline: ordinary HTTP
uses its actual method, GraphQL uses `GQL`, and unknown future kinds use the neutral feature accent without a
sidebar code change.
Execution is delegated to `:application` with cancellation revision checks so superseded results cannot publish.
The response summary reports the protocol actually observed by the API Studio transport. API Studio routes through
KNet only while capture is `Capturing`; a running listener with capture `Paused` is deliberately ignored. When proxy
routing is active, the captured Traffic exchange is additionally marked `API Studio`; ordinary phone/browser traffic
remains `Proxy client`, and both use the same canonical capture path.
The authored HTTP-version preference follows the same editor-to-domain, auto-save, restore, and execution path as
method and URL; Traffic handoff preserves exact HTTP/1.0, HTTP/1.1, or HTTP/2 captures and falls back to `AUTO`
for protocols the current outbound client cannot force.
On JVM, `AUTO` prefers HTTP/2 through ALPN and accepts an observed HTTP/1.1 fallback. Exact HTTP/2 is a fail-closed
choice. Direct and proxy-routed execution share this policy; when routed through KNet, scoped Root CA trust and
capture attribution are applied only to the local proxy hop.
The old phantom tab/environment models and feature-local execution/recording workflows have been removed.
Assembly lives in `:products:desktop` under `di/apistudio`.
Contributed documents use the application-owned opaque workspace contract. The common shell never interprets their
payloads: the owning editor restores and auto-saves its versioned draft, then creates a strict executable document
only when the user invokes it. Future editors therefore add a module, codec, contribution, runtime adapter, and DI
binding; the sidebar, Room schema, collection dialogs, and shell routing stay unchanged.
The contribution reports a lazily materialized document ID back to the shell after persistence succeeds, allowing
the common sidebar to select it without owning protocol fields or risking loss of the user's first edit.
