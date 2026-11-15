# Continuous capture-file import overlay

Import and native file dragging use one KNet-themed in-window surface across the entire desktop shell.
The surface remains mounted from hover through accepted native handoff, validation, record selection,
errors and action preparation. There is no second native dialog or imported-Traffic viewer.
Traffic continues to show records captured by KNet.

## Interaction

- Enter KNet with a local file: show a blue outline and the import surface before release. No file content
  is read during hover. Leave without dropping or press Escape to dismiss the preview.
- Release: synchronously reserve the existing surface before AWT sends its drag-end callback. Decode
  native paths while the transferable is valid, then queue parsing after native drop completion. A
  single-use reservation rejects stale or duplicate delivery after dismissal or another import.
- Validate: show progress in the same surface, with fixed theme-owned position and bounds. File details,
  record selection and four actions update in place; drop highlighting changes color smoothly.
- Choose file: Import opens this exact surface over the current workspace. Its Choose file button opens
  the OS picker. Cancelling that picker keeps the surface and any earlier valid candidate.
- Replace: retain the validated candidate until a replacement has fully parsed. Invalid transfers,
  unsupported files and multiple-file drops show an explanation without discarding that candidate.
- Dismiss: cancel pending work and retire only the overlay's temporary spool. Captured traffic, active
  devices, policies and unsaved destination editors are independent.

The surface blocks background pointer/keyboard input and keeps focus inside while open. Outside clicks
and Escape dismiss it. The top-right Close icon is the only visible dismissal control; there is no
duplicate bottom Esc Dismiss/Cancel button. Child controls remain interactive, and clicks on unused space inside the surface
cannot activate the scrim. Shared scroll clearance keeps content out of the scrollbar lane.

One local capture is accepted per drop. HAR, KNet Capture and Charles XML are content-detected rather
than selected by extension. Native file lists are bounded; remote URLs, network URI authorities and
unsupported transfer objects are rejected. Original files are never moved, modified or deleted; native
transfers negotiate Copy. Ordinary text dragging remains available to editors.

## Four explicit actions

| Choice | Result |
|---|---|
| Send to API Studio | Create a new unsent HTTP request draft from the selected complete text request. |
| Create Intercept rule | Open the existing unsaved rule editor with canonical URL/port/method and registered protocol suggestions. |
| Create Network Conditions rule | Open an unsaved destination/protocol draft with No throttling selected. |
| Create Protected Traffic rule | Open the destination editor for review, without restoring recorded policy. |

There is no Open in Traffic action or default badge. At most 128 body-free summaries are retained;
paging and the selector identify one file-local record for an editor action. Empty captures and records
that cannot provide the selected action explain why it is unavailable.

API Studio requires a complete UTF-8 text request within the 1 MiB draft budget. Decoded bodies do not
retain an obsolete Content-Encoding header. Imported responses do not become a fabricated live Studio
response. Rule suggestions use registered protocol extensions, including GraphQL URL parameters.
Destination-only Protected Traffic preparation never reads request body bytes.

The policy workspace is captured from the app when Import opens or a drop is reserved. File-supplied
device IDs never authenticate or retarget a draft. Existing rule editors retain their All devices/Only
this device creation choice and explicit Save. An existing unsaved rule is preserved with a retryable
message. Successful editor routing retires the import spool; importing never sends or saves anything.

## Ownership and cleanup

`:ui:core` owns native transfer adaptation, bounded path decoding, the continuous surface, focus/input
containment, dimensions and scrolling. The app shell mounts one receiver across navigation and title/device
areas; only the overlay observes parser progress. The Traffic toolbar exposes an Import callback but has
no imported viewer state, history rendering branch or alternate source. App composition routes only the
four typed editor drafts. Use cases own parsing, eligibility and protocol-aware draft preparation; Koin
bindings remain in the product composition root.

The obsolete history panel, its ViewModel/state/detail tabs, history-specific tests, DI registrations,
viewer routing, body-preview use case and native-dialog size extension have been removed. Bounded parsers,
format codecs and payload reading remain because supported draft actions use them.

## Verification

Automated checks cover native transfer decoding/deferred delivery; stable surface and heading identities
and bounds on every rendered transition; native drag-end before file delivery; stale/duplicate callbacks;
leave/Escape/error/cancellation; background pointer blocking and keyboard containment; narrow/Retina
layouts; bounded record selection; draft eligibility and ownership; and real HAR/Koin/GraphQL/editor
integration. See the implementation board for the latest completed qualification result.

Final qualification on 2026-11-15 passed **1,957 automated cases across 36 complete outputs**, with zero
failures/errors/skips. Six focused suites contain 29 cases: application actions (5), native path delivery
(5), theme surface geometry/identity (1), overlay state and reservations (12), actual rendered shell/input
transitions (5), and product HAR/GraphQL/editor integration (1). These replace obsolete viewer-specific tests.

Desktop and Android packaging and shared iOS simulator compilation passed. All 3,569 checked classes in
22 desktop modules match the package. The package contains none of the removed viewer, detail-state,
Traffic handoff, native size, compact-table or old drag-preview classes. Evidence and rendered preview:
`/Users/devuloopers/.codex/verification/knet-continuous-import-2026-11-15/`.

Native OS acceptance remains interactive: drag from Finder, leave/cancel, release a valid capture, cancel
the OS picker, reject a replacement/multiple-file drop, and confirm that no second window or surface opens.
Automated scenes do not claim to exercise Finder or the actual OS picker.
