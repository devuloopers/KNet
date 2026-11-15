# Traffic row context menu

Traffic uses the shared KNet context-menu component with typed actions, separators and child menus.
The Protected Traffic child opens by hover or click and offers exactly three destination actions:
Always tunnel this destination, Attempt inspection next time, and Block this destination.
The same child is available on ordinary captured request rows and opaque transport rows.

The root menu starts with Export and the grouped Copy submenu, followed by a divider. Copy shows the
selection count on its parent for groups and contains full/summary, address, request, response, combined,
contextual protocol, connection and developer-format choices. See [Copy Traffic details](traffic_copy_details.md).
Icon-labelled feature actions follow the sidebar order: API Studio, Intercepts (Add Breakpoint Rule),
Network Conditions and Protected Traffic. Protected Traffic uses the shared shield icon.

Request rows keep Send to API Studio and Add Breakpoint Rule. Encrypted opaque rows have no captured
HTTP request to replay, so those entries remain absent. Add to Network Conditions remains available.
Host and port now lives under Copy > Address and copies every selected destination, one per line.
Copy presets carry exact typed single/group IDs and source; a non-applicable record stays explicit in
mixed copies. Export uses the group only when the clicked row belongs to it. Selection helpers remain
absent from the menu; Cmd/Ctrl-click, Shift-click, Cmd/Ctrl+A and Escape continue to control selection.

Opening a row menu selects and focuses that row immediately. If it already belongs to a multi-selection,
the group and range anchor remain intact while inspector focus moves to that member. Opening a menu on
an unselected row replaces the previous selection, so the toolbar and menu export target agree.

Each submenu command carries a typed canonical record ID. Ordinary requests resolve their recorded
absolute/authority target, effective port and authenticated connection ingress in the application layer.
The original device owns a private, activated destination rule; a matching rule is replaced through the
same publication-fenced save path as opaque-row actions. An exchange ID never falls back to an opaque
record with the same text. Missing history/invalid targets do not create guessed rules. Request action
failures are shown only while the originating Traffic source is still displayed.

These actions configure future protected connections through existing TLS/CONNECT policy support.
Showing the submenu on an HTTP/GraphQL/gRPC/WebSocket request does not replay that request, repair its
historical response, decrypt plaintext HTTP, or add new raw HTTP/UDP transport capabilities. For a plain
HTTP record, saving a rule records the exact host/port for future protected connections; it does not
convert that request into TLS or add plaintext-proxy tunneling behavior.

The shared menu keeps a frozen action snapshot during one opening and releases it on dismissal.
Incoming Traffic updates cannot replace the open menu's leaf callbacks. Child popups keep one keyboard
owner: arrows navigate/open/close, Enter/Space activate, and Escape closes a child before the root.
Disabled entries and separators are skipped. Outside click closes the menu; choosing a leaf dismisses
all levels before invoking its action. Child panels flip sides near window edges. Sizing and single-line
label behavior are owned by `:ui:core`, using the existing KNet theme.

Verification covers rendered pointer/keyboard/dismissal behavior, popup edge calculations, exact action
lists and replay eligibility, selected-export routing, canonical destination parsing, device isolation,
ID-kind collisions, duplicate submission and product DI/Room/runtime policy integration.

The complete workflow gate passed **1,924 automated cases across 36 outputs**, with zero failures,
errors or skips. Seventeen menu/routing/persistence/product cases passed, including actual request and
opaque popup interactions at standard and Retina density. Existing source-switch error and responsive
toolbar cases also passed. Desktop packaging, the Android APK and shared iOS simulator compilation passed.
An independent class comparison found all 3,543 checked classes from 22 modules in the rebuilt desktop
package, without mismatches or duplicates. The product integration uses canonical fixture metadata with
real policy storage and runtime adapters; it does not replace physical-device or native interactive tests.

Logs, focused XML results, rendered menu images and package checks are saved in
`/Users/devuloopers/.codex/verification/knet-traffic-menu-2026-11-15/`.
