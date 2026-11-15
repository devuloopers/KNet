# API Studio execution Copy

The response toolbar has a labelled split control beside Export. Clicking the main button copies the
active response component: **Copy body**, **Copy headers** or **Copy cookies**. The chevron opens the
same KNet-themed nested menu used by Traffic. It supports pointer and keyboard navigation, flips at
window edges, closes on outside click/Escape and closes when its execution is replaced or cleared.
The primary segment fits its visible label and animates width changes using the shared button motion.
Copied/error feedback replaces a fixed-size icon without changing the segment width.

## Options

The shared catalogue offers full exchange details, summary, address, request, response, combinations,
contextual protocol details, connection information and developer formats. Inapplicable categories are
disabled or absent. Response Body applies the shared bounded JSON formatter where applicable; Original text decodes the
captured response without prettifying it. Base64 and hexadecimal represent stored bytes. Cookies retain
individual Set-Cookie values and attributes rather than joining them into a lossy key/value map.
GraphQL GET and POST use the shared query/variables/extensions/persisted-query inspection formatter.

WebSocket and GraphQL WebSocket expose message direction and handshake metadata. gRPC exposes the
recorded operation, metadata, trailers and messages. Its engine-decoded JSON is explicitly labelled and
kept separate from the original protobuf payload. Unsupported binary messages remain labelled, with
Export available for their original bytes. Live HTTP streams expose Copy events and SSE Event data;
only complete records in the retained prefix are copied, using the registered engine interpreter.
Stream/native toolbars inherit horizontal overflow from `:ui:core` so the controls retain their size.

## Source and lifecycle

The command freezes the last execution ID and preset before asynchronous preparation. It never reads
the current editor draft or queries Traffic, and works with capture paused or stopped. Actual response
heads enable HTTP Copy immediately, including real 4xx/5xx responses. Synthetic transport-error status
codes do not enable it. Native protocols require actual server handshake/message/response evidence.
An active or cancelled stream remains explicitly partial; a missing or cleared owner is an error rather
than a fallback to another execution. Preparation errors preserve the existing clipboard.

The existing per-execution recorder owns private files. The copy source reads bounded prefixes under
that owner's guard on IO, then returns owned bytes that survive subsequent file cleanup. HTTP paths
reuse Traffic's renderer and formatting; native paths retain native metadata without inventing an HTTP
response. When an executor exposes only the submitted post-script semantic request, full copy and cURL
include that provenance. Encoded cURL requires transport request evidence; unencoded semantic bytes are
never represented as compressed wire bytes. Copy preparation never executes a request or shell command.

The shared presentation ViewModel calls an application use case, keeps no payload in UI state and fences
late work on execution changes, replacement commands and disposal. Clipboard publication uses modern
asynchronous Compose Clipboard. Product DI binds the same recorder instance to export and copy sources.

## Bounds

- 65,536 retained bytes per HTTP body and the shared bounded decoded output.
- First 32 native messages, at most 16,384 retained bytes per message, with omitted-history disclosure.
- At most 16,384 characters of already engine-decoded gRPC JSON per retained message; larger interpreted
  views are omitted and their raw binary availability remains explicit.
- 655,360 bytes of owned raw prefixes and at most 524,288 interpreted-message characters per snapshot.
- Shared 4,194,304-character clipboard limit and 131,072-character labelled-field limit.

Metadata-only options do not read body files. Use Export for larger portable data. See
[Traffic Copy](traffic_copy_details.md) for shared formatting, cURL and multi-item rules.

## Verification

Recorder tests cover immutable execution data, duplicate headers/cookies, actual error responses,
pre-response/released-owner rejection, oversized/active prefixes, original text versus pretty JSON,
all presets, complete SSE records, native direction/page bounds, decoded gRPC/raw-protobuf separation,
failed prefixes, preserved handshake metadata, interpreter cleanup on errors and encoded semantic cURL
refusal with a transport-evidence positive control. Address-only copy does not add a trailing newline.
Structured JSON labels the execution source as API Studio rather than assigning it to Other traffic.
ViewModel tests cover stale/cleared owners,
cancellation, latest-command wins and atomic error handling. Actual Compose input tests exercise the
split control, tab callbacks, themed nested menus, owner dismissal, compact label spacing and stable feedback bounds at standard/Retina
density and narrow/wide widths. Product integration uses real DI/Room initialization/recorder/files,
formatters and the SSE interpreter with an in-memory Compose clipboard; Traffic access is a failing
trap. Native system clipboard interaction remains an interactive check.

The final workflow gate passed **2,017 tests across 36 complete outputs**, with zero failures/errors/
skips. The five new copy suites contain 20 cases. Architecture validation, desktop packaging, Android
assembly and shared iOS simulator compilation passed. All **3,721 checked classes across 23 modules**
match the desktop package without missing, mismatched or duplicate classes. XML, logs, the rendered
menu and package checks are saved in
`/Users/devuloopers/.codex/verification/knet-studio-copy-2026-11-15/`.

The follow-up content-sizing correction removes the hidden longest-label reservation. All 52 focused
UI cases passed, including exact content padding and grow/shrink checks at standard/Retina density.
Desktop/Android packaging, shared iOS compilation and architecture validation passed; the desktop
package matches all 3,721 checked classes across 23 modules. Evidence:
`/Users/devuloopers/.codex/verification/knet-copy-spacing-2026-11-15/`.
