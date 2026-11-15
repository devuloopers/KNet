# Traffic and API Studio export qualification

Status: automated qualification completed (2026-11-15). **1,747 tests across 34 complete outputs**
passed with zero failures, errors or skips. This includes 35 new export-specific cases, one of which
checks the real product composition. Desktop packaging, architecture and ledger validation passed.
Native/physical acceptance remains separate as listed below.

## Delivered behavior

Traffic offers a row-context action and a toolbar action. The shared export dialog freezes its source
and query, supports explicit all-source selection, and exports retained rows outside the UI window.
API Studio keeps an Export action in the response/session toolbar; drafts and local failures cannot
qualify, while actual server errors, empty responses and retained partial/streaming results can.

Both paths use one versioned KNet Capture writer and a HAR projection for compatible HTTP evidence.
Native captures retain framed messages and opaque connection metadata. HAR discloses unsupported
record/message counts and binary-request extensions. Default header credential removal and optional
payload omission affect the exported copy. Import is outside this delivery.

## Reproduction and retained evidence

```sh
./gradlew captureExportQualification :products:desktop:createDistributable
```

The executed run used one worker, in-process Kotlin compilation and an isolated build directory via
`/Users/devuloopers/.codex/verification/knet-smart-scroll-2026-11-14/isolated.init.gradle.kts`.
It completed successfully in 5 minutes 13 seconds. The aggregate task rejects missing/empty reports,
failures, errors, skipped cases and missing export-specific suites; it does not infer coverage from a
successful compile. Evidence is retained in
`/Users/devuloopers/.codex/verification/knet-export-2026-11-14/`: the complete log, aggregate JSON,
focused JUnit XML, resource output and class-by-class package comparisons.
All **2,448 production classes across 15 affected modules** match the packaged class bytes, with no
stale duplicate found in those comparisons. The rebuilt app is at
`/Users/devuloopers/.codex/verification/knet-smart-scroll-2026-11-14/build/products/desktop/compose/binaries/main/app/KNet.app`.

## Focused evidence

| Layer | Executed scenarios |
|---|---|
| Application pipeline | Post-script request ownership and original response bytes; actual 503 response; storage-open and append failure isolation; cancellation before and after response evidence. |
| Snapshot/storage | Real Room with two authenticated device identities sharing display name/destination; Clear after preparation; both body-deletion entry points; 1,107-row traversal and frozen filter semantics; missing bodies and checksum-backed payloads. |
| Native archive | Binary equality/digests; duplicate headers; metadata-only and credential opt-in; paged gRPC/WebSocket/SSE messages and ordering; opaque TLS/QUIC metadata; active SSE prefix snapshot; partial response ownership after release. |
| API Studio eligibility | Actual 200/204/304/400/500/502/503/504 responses; unsent/local-failure exclusion; outbound-only exclusion; inbound binary evidence; trailers-only server rejection; actual Direct HTTP multipart request and binary error response. |
| HAR | Ordered headers and query values; binary/base64 response; escaped UTF-8 request; sentinel-like user strings; cookie projections and privacy; explicit omitted request bodies; no dangling private payload path in HAR content. |
| Lifecycle/UI | Frozen execution during request edits; picker cancellation; destination failure/retry; superseded preparation cleanup; duplicate Save; real Koin resolution and owner retirement on close. |
| Rendered UI | Export semantics identity and exact bounds before/after response changes across eight statuses at 420/980-pixel widths, including repeated intermediate frames. |
| Resource/file behavior | 32 MiB payload streamed, archived and byte-verified; retained-heap check after cleanup; cancellation preserves existing destination and removes task-owned destination temporary files. |

The complete regression gate also covers existing HTTP/HTTPS/H2/H2C, GraphQL GET/POST and persisted
query handling, WebSocket, GraphQL WebSocket, gRPC, SSE, proxy/session lifecycle, Companion control,
per-device admission/policy, Room and shared UI behavior. Existing real-socket protocol fixtures run
locally. Export tests decode archives using test readers; a production importer is not introduced.

Two existing asynchronous tests needed deterministic synchronization under the full run: Smart Scroll
now waits for the Home animation to settle before asserting its position; the policy-manager deletion
case waits for the committed revision to reach its manager before acknowledging deletion. Assertions
and production behavior are preserved.

## Resource interpretation

The final 32 MiB fixture completed export and verification in **950 ms**, with a **0-byte positive
retained heap delta** after cleanup. The large-payload fixture uses a seeded 1 MiB block repeated to 32 MiB, bounded body reads, streamed ZIP
writing and streamed byte verification. Its retained heap delta is measured after collection and cleanup.
This detects retaining a whole payload after export; it does not measure allocation rate, peak heap,
RSS or whole-application RAM. ZIP central-directory metadata grows with entry count. The measurement
must not be generalized into constant total-memory claims for arbitrary sessions.

API Studio retention is explicitly capped at 64 MiB and 100,000 native events per execution, with at
most 64 retained execution owners. Truncation, missing payloads and dropped events are disclosed.
Traffic uses 128-record/message pages and 64 KiB payload operations. Destination writing starts after
the database snapshot and canonical-body lease end.

## Remaining acceptance boundaries

- Native save-dialog interaction, operating-system overwrite confirmation, keyboard/hover interactions
  and visual acceptance in the running desktop application require a manual pass. Desktop was not launched.
- Opening HAR in a named external desktop product is a separate interoperability check. Binary requests
  use a disclosed KNet extension; unknown timing phases are marked unavailable. No native Charles,
  mitmproxy, HTTP Toolkit, Postman or CSV compatibility is claimed.
- Local tests do not replace physical Android/iOS, packet-provider, other operating-system or sustained
  production-scale acceptance. No physical device or user's live capture/pairing store was modified.
- The matrix is executed scenario coverage, not a universal 100% line/branch or all-future-edge-case claim.

See [the feature/schema reference](traffic_export.md) and [the approved export plan](traffic_api_studio_export_plan.md).
