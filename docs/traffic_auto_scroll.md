# Traffic Auto scroll

The Traffic toolbar labels the control **Auto scroll** and offers two modes:

- **Smart** (default): follow while the actual newest matching request is fully visible at the top.
  Wheel, touchpad, keyboard, accessibility and scrollbar navigation into history pause following,
  including a partially clipped first row. The top of an older loaded window loads adjacent newer
  records without jumping; reaching the actual newest request resumes following.
- **Manual**: an explicit switch controls following. On follows newly arriving records;
  Off preserves the current window. Switching modes retains the last Manual switch value.

A compact **N new requests** action appears in the existing statistics footer while new matching
traffic remains above the reader's last-seen checkpoint. It adds no footer row and does not
cover table content. Viewing newer fully visible rows advances the checkpoint progressively, for
example 19 to 18 to 17. Selecting a row alone does not acknowledge it. Jump to latest intentionally
reveals the newest page and acknowledges the entire known batch; reaching the actual newest row
also clears the remainder. In Manual mode, this one-time action leaves the switch unchanged.
The pill stays hidden during active following while the newest row is being positioned.

The Smart/Manual label crossfades inside the same dropdown anchor. The Manual switch and its spacing
expand/fade into place and shrink/fade out using shared KNet motion tokens. The toolbar height stays
fixed, the switch's On/Off width changes are smoothed, and reduced motion applies changes immediately.
The outgoing switch is disabled while disappearing so it cannot change saved Manual state after Smart
has been selected. These animations change presentation only; following and arrival-count behavior are
unchanged.

## Ownership and storage

`TrafficAutoScrollSettings` belongs to the domain workspace settings and is saved through
`UpdateWorkspaceLayoutUseCase` into DataStore. Absent or unknown mode keys resolve to Smart.
Transient reading positions and counts stay in memory, independently for each Traffic source.
Row windows remain bounded to four workspaces (one active and three dormant); lightweight reading
checkpoints survive eviction without retaining bodies. Inspector selection is unaffected by arrival
refreshes while history is paused.

The application query contract exposes `TrafficArrivalCheckpoint` and `TrafficArrivals` alongside
an ordinary bounded page. HTTP exchanges and opaque transports have independent insertion sequences.
Storage counts matching retained rows after both acknowledged boundaries, using the same source,
search, scheme, method, status, protocol and semantic-record filters as the page. This includes
HTTP, GraphQL, SSE, WebSocket and gRPC exchange records and payload-opaque TLS/TCP/UDP flows.
A message, response, body, byte counter or lifecycle update of an existing record is not an insertion.

Arrival detection is independent of timestamps, changing display ordinals, loaded page size and Room
invalidation generations. Progressive read positions use the same timestamp plus namespaced identity
order as canonical keyset paging, together with the insertion snapshot known when viewed. Later
backdated admissions therefore remain new until viewed or acknowledged; display renumbering and
retention do not move the checkpoint. The count covers matching retained records beyond the
1,000-row UI bound. Updates to existing records do not manufacture arrivals.

`TrafficReadPoint` carries the stable exchange/opaque identity, chronological position and table
insertion sequence. `TrafficSeenCheckpoint` retains one read point and its observed insertion snapshot,
without an unbounded set of seen request IDs. The reader returns the actual newest matching read point
independently of the page direction, so an old loaded-window boundary cannot be mistaken for the live
edge. These queries use metadata rather than payload reads or additional traffic collectors.

## Interaction and reset rules

Paused refreshes update overlapping lifecycle records without replacing the reader's row window.
Near its newer edge, ascending keyset queries load the next adjacent block of up to 200 records and
prepend it while Compose retains the visible stable-key anchor. Even a 1,500-record burst is traversed
without skipping intermediate requests. When the bounded window drops its older tail, a typed older
anchor reconnects paging to that retained tail. Cached source windows keep their continuation when
switching tabs. A failed newer-page query suppresses automatic retries until the user scrolls again.

Fully visible rows are reported from measured viewport bounds; partially clipped rows are excluded.
Source/filter/clear epochs reject stale viewport observations. A separate read revision prevents a
count query started for an earlier seen position from overwriting newer progress. Progress refreshes
are conflated at the existing 75 ms interval instead of querying for every scroll pixel. Explicit jumps
use immediate scrolling rather than per-arrival scroll animations.

Changing filters starts a new reading baseline and returns to the first matching page. Successful
Clear resets reading state for its target source; clearing all resets all workspaces. A device tab
has its own reading state, so another device cannot acknowledge its pending arrivals. Unopened
sources and application restarts establish a fresh baseline rather than flagging all saved traffic
as new.

## Automated verification

Tests cover exact-top policy, partial first rows, Smart pause/resume, actual wheel and thumb dragging,
Manual selection and switch interaction, one-time jumps while Manual is off, stable anchor/selection
and footer geometry, saved preferences and unknown-value fallback, rapid mode changes, a pause during
an in-flight refresh, filter/clear rebasing, source/cache isolation, independent HTTP/opaque insertion
identities, backdated/equal timestamps, status-only updates, deletions, and a 1,500-arrival burst beyond
the row bound. Real Room fixtures and isolated rendered Compose scenes are used without opening or
modifying the running desktop application or the user's database.

Qualification on 2026-11-14: 144 Traffic, 38 app-shell, 76 application, 95 domain, 7 storage,
138 data and 32 UI-core tests passed (530 distinct cases, with focused history/settings reruns).
The desktop compilation, all six architecture checks, and shared UI-core Android/iOS Simulator compilation passed. Logs are
`/tmp/knet-smart-scroll-qualification.log` (broad module run) and
`/tmp/knet-smart-scroll-final.log` (final successful build, regression tests and gates), and
`/tmp/knet-smart-scroll-platforms.log` (Android/iOS compilation).
Rendered examples are `/tmp/knet-smart-scroll-manual-history.png` and
`/tmp/knet-smart-scroll-manual-control.png`.

Motion follow-up on 2026-11-14: all 217 UI regressions passed (147 Traffic, 38 shell and 32 UI-core).
Three new rendered cases sample intermediate expansion/contraction frames, check a fixed control height
and right edge, retain the dropdown anchor identity, exercise rapid mode reversal and preserve Manual's
switch value, and verify immediate reduced-motion changes. Desktop/shared Android/iOS Simulator compilation
and all six architecture checks passed. Log: `/tmp/knet-auto-scroll-motion-final.log`.

Progressive last-seen follow-up on 2026-11-14: all 421 automated cases passed (158 Traffic, 38 shell,
77 application, 141 data and 7 storage), with zero failures/errors/skips. Tests include 19 to 18 to 17,
fully visible versus clipped rows, latest-jump acknowledgement in Manual off, 1,500 arrivals traversed
without paging gaps, older-tail eviction/recovery, mixed HTTP/opaque equal-time keys, late/backdated
admissions, source/filter/clear epochs, background-device clearing, tab cursor restoration and stale
in-flight count results. Desktop compilation and all six architecture checks passed. Logs:
`/tmp/knet-progressive-read-qualification.log` and `/tmp/knet-progressive-read-final.log`.
The running desktop app and user database were not accessed or modified.
