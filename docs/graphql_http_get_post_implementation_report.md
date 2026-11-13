# GraphQL HTTP GET and POST implementation report

Date: 2026-11-12. Status: IMPLEMENTED AND AUTOMATED VERIFIED; physical verification remains pending.

## Delivered behavior

| Request form | Canonical source | Inspector and authoring location |
| --- | --- | --- |
| POST application/json | Original JSON body | Request > Body > Query / Variables / Extensions / Raw JSON |
| GET form-encoded parameters | Original URL; no synthetic body | Request > Params > Query / Variables / Extensions / Raw parameters |

Both forms share source extraction, bounded AST parsing, operation selection, descriptors, semantic annotations,
breakpoint observations/suggestions and Network Conditions matching. The synthetic BootStrapQuery fixture resolves
its name from the document without a separate operationName parameter. Its variables include device, language and
siteId. The user's staging endpoint was never called.

View query opens the correct source and resets the Query sub-tab. Overview remains compact. Prepared GET fields
are loaded off-thread, counted in the existing cache budget and fenced by exchange identity. The shared viewer uses
KNet theme/layout primitives; wide/narrow renders and per-frame navigation geometry are covered by headless tests.

GET editing changes only the selected URL fields. Original encoded pairs, unrelated repeated keys and ordering remain
intact. POST edits preserve unknown JSON envelope fields. Exact JSON body whitespace survives unedited import/replay.
Query/Variables buffers remain intact during incomplete keystrokes; asynchronous validation updates diagnostics without
reformatting or erasing them. Anonymous operations clear stale selectors; multiple operations retain explicit selection.

Drafts and collections continue to store method, URL, query rows and actual body through the existing schema.
No new database schema or synthetic GET body was introduced. A real Room restart/promotion fixture covers both forms.
Canonical ordered headers remain stored; transport-managed header handling continues to follow the existing client policy.

## Shared implementation and limits

- Platform-neutral source, operation, diagnostic and editable-field contracts live in core/domain.
- GraphQLHttpDecoder in engine/protocol owns HTTP extraction and the shared graphql-java AST parser. The existing
  graphql-java artifact is reused; no second parser library, global parser setting or retained AST cache was added.
- GraphQLHttpRequestFormatter in engine/formatter is the presentation/authoring adapter. The dependency direction is
  formatter -> protocol, with no inverse edge or parser added to Room, UI composables or proxy forwarding handlers.
- Raw input is bounded to 1 MiB of body bytes or URL characters. AST parsing is bounded to 32,768 tokens, 32,768
  whitespace tokens and rule depth 128. JSON nesting is checked before recursive parsing. At most 256 operations,
  4,096 URL pairs and 256 operation-name characters are accepted. Derived formatting/cache weight is bounded too.
- GET names and values use strict UTF-8 form decoding once. Invalid escapes, ambiguous recognized parameters,
  invalid JSON/types, unknown operation selectors, truncated bodies and limits have explicit diagnostics.
- Semantic annotations use schema version 2. Old records remain stored. Overview chooses the highest completed
  schema per inspector; precise suggestions ignore old-schema facts and re-inspect the available canonical request.
- Invalid/incompatible captures retain their original data and forwarding behavior. Structured authoring reports
  invalid input before execution. A missing/persisted query cannot be reconstructed from its hash or name alone.

The transport baseline follows the current [GraphQL-over-HTTP working draft](https://http-spec.graphql.org/draft/)
and [GraphQL HTTP guidance](https://graphql.org/learn/serving-over-http/). It is not a claim of complete GraphQL
server/schema conformance. Official graphql-response+json responses remain JSON in the response inspector.

## Bugs caught by qualification

1. HTTP/1.1 URL reconstruction reordered interleaved repeated parameters. Exact HTTP/1.1 now uses the existing
   JVM raw-URI adapter with HTTP/1.1 explicitly selected; its bounded client-pool key includes the selected protocol.
2. GraphQL transport encoders trimmed JSON bodies. Existing JSON envelope bytes now survive unchanged in both paths.
3. An idle JVM response read could block the collector dispatcher. Blocking reads now run interruptibly on
   Dispatchers.IO; the real SSE cancellation regression verifies that the call closes without waiting for a new frame.
4. Asynchronous GET editor hydration could replace unfinished input. Structured edits retain their buffers and
   publish validation independently. URL/import changes still hydrate and clear stale projections under identity fences.
5. Legacy name guessing accepted a selector absent from the document. Shared AST selection now fails explicitly.
6. GraphQL response JSON media types could be treated as request envelopes. The registry routes them to JSON.

## Automated qualification

The GET/POST baseline gate passed: **435 tests, 0 failures, 0 errors, 0 skipped**. This increment adds 43 test methods,
including loop-based encoding/selection/invalid-input matrices. Counts below include relevant existing regressions.

| Module / selected suite | Tests |
| --- | ---: |
| engine/protocol | 36 |
| engine/formatter | 58 |
| engine/graphqlWebSocket | 21 |
| engine/simulator | 49 |
| ui/desktop/httpPanel | 45 |
| ui/desktop/traffic | 118 |
| ui/desktop/apiStudio | 56 |
| ui/desktop/breakpointManager | 7 |
| application/desktop (selected) | 18 |
| data/desktop (selected) | 8 |
| core/http (selected) | 19 |
| Total | 435 |

Desktop product compilation, shared Android and iOS simulator UI compilation, all six architecture checks,
Network Protection ledger validation and git diff whitespace checks passed. The core HTTP suites include exact
HTTP/1.0, HTTP/2, real SSE cancellation and mocked client compatibility. The data selection includes five loopback
capture/replay/cancellation tests, the new Room source-persistence test and two existing workspace persistence tests.

Tests use isolated temporary Room databases,
loopback servers and headless Compose scenes. The desktop application was not launched or its user database opened.
Build outputs are isolated under /tmp/knet-traffic-inspector-20261110 so the running application's artifacts are untouched.

Verification log: /tmp/knet-graphql-http-final-validation.log.
Additional transport regression log: /tmp/knet-graphql-http-transport-fixed.log.
Headless renders: /tmp/knet-graphql-get-viewer-680.png and /tmp/knet-graphql-get-viewer-300.png.

### Coverage evidence

- GraphQLHttpDecoderTest: POST/GET equivalence, BootStrapQuery, UTF-8/plus/delimiters/encoded names, no double decode,
  duplicate keys, conflicting sources, optional values, unknown/multiple/anonymous operations, comments/BOM/fragments,
  invalid syntax, GET mutation/subscription, hash-only data, truncation and character/token/depth/operation/pair limits.
- GraphQLHttpConsumerTest and existing extension suites: source-independent breakpoint selection and retained response
  observations, Network Conditions matching, source-aware suggestions, invalid-operation negatives and schema-version behavior.
- GraphQLNetworkConditionChannelHandlerTest: a real Netty GET head matches immediately without body aggregation.
- GraphQLHttpEditingTest / GraphQLSourceEditingTest: typed target edits, unrelated fields/pairs, exact body retention,
  malformed authoring JSON, anonymous operation selection and explicit multi-operation selection.
- GraphQLQueryShortcutTest and inspector stability suites: source-specific navigation, raw parameters, selection identity,
  schema deduplication, stable node/viewport geometry, narrow panes and larger fonts.
- ApiStudioViewModelTest: GET/POST import, no automatic execution, editing, draft save/restore, stale preparation,
  partial keystrokes, deliberate method handling and visible GET mutation validation.
- GraphQLHttpCaptureReplayTest: real production client -> proxy -> Room capture -> import -> replay. Both AUTO and exact
  HTTP/1.1 assert the origin-observed method, raw target, marker header and body. Edited escaped variables and cancellation
  before response headers are covered; cancellation must not replay the application request.
- GraphQLHttpPersistenceTest: GET/POST drafts promote to collections and survive a real Room database restart.
- Existing GraphQL WebSocket, raw/batch, non-GraphQL body, streaming, exact HTTP/1.0 and HTTP/2 suites protect compatibility.

## Remaining manual verification

- Rebuild/relaunch KNet and capture the same GraphQL query through GET and POST from a physical Android/iOS client.
- Use View query, Query/Variables/Extensions/raw views and one named breakpoint/condition for each source.
- Import, edit a variable, save/reopen and send each form; confirm GET remains bodyless and POST remains JSON.
- Confirm selection/typing feels stable with actual pointer/keyboard input and the system font/display scale.

These are physical/product checks; software implementation and automated qualification for this increment are complete. The broader Network Protection ledger is a
separate program: its validator still reports 173 requirements with 93 broader software requirements open. This increment
does not mark that program or all of KNet complete.

## Read-only JSON formatting follow-up (2026-11-12)

The source-preserving GET/POST decoder correctly retained compact JSON, but the viewer adapter did not create
an indented Variables/Extensions projection. The adapter now uses the shared JSON printer for both sources.
Strict whole-document parsing leaves malformed values unchanged; it does not salvage substrings or alter drafts.
Parsing is limited to 1 MiB and nesting depth 128, and output is stopped at 2 MiB before indentation can expand
unboundedly. Original URL/body values and incomplete authoring text remain unchanged.

Four added tests cover GET/POST indentation and nested arrays, semantic/source preservation, malformed input,
input/depth/output limits and actual shared-editor line counts. Headless renders were inspected at
/tmp/knet-graphql-json-formatting-variables.png and /tmp/knet-graphql-json-formatting-extensions.png.

The follow-up gate passed **287 tests, 0 failures/errors/skips**: formatter 61, HTTP panel 46, Traffic 118,
API Studio 56 and selected source-preserving data tests 6. Desktop product compilation, architecture checks
and git diff whitespace checks passed. Log: /tmp/knet-graphql-json-inspection-formatting.log.
The desktop app was not relaunched; rebuild/relaunch it for manual confirmation.

## API Studio initial editor formatting follow-up (2026-11-12)

The Traffic read-only fix did not change editable GET state. Initial authoring hydration now reuses the same
bounded query/JSON formatters for Query, Variables and Extensions. This covers Traffic sharing, URL loading, saved
request restoration and the shared interception editor. The encoded URL, absent GET body and supplied operationName
remain canonical; merely opening a field or changing its tab does not write formatted content back into the request.

Validation-only preparation skips JSON display formatting. Existing buffers survive partial input and unrelated
header/auth edits. Real URL/parameter changes clear stale projections and hydrate the new source under the existing
identity/cancellation fences. POST's existing structured-body hydration stays covered by the regression suite.

Four added tests cover initial import, reopen, shared hydration and raw validation; the existing partial-keystroke
test now also covers unrelated edits. Three new formatting regressions failed before the fix. The final gate passed
**298 tests, 0 failures/errors/skips**: formatter 61, HTTP panel 48, API Studio 58, interception 7, Traffic 118 and
selected data tests 6. Desktop compilation, architecture gates and whitespace checks passed.

Logs: /tmp/knet-graphql-studio-prettify-baseline.log and /tmp/knet-graphql-studio-prettify-verification.log.
The desktop application was not relaunched; rebuild/relaunch for manual confirmation.

## Persisted-query follow-up

The HTTP APQ source catalog, manifest import, source-preserving hash edits and current inspector/rule resolution
are documented in [GraphQL persisted queries](graphql_persisted_queries.md). Hashes remain non-reversible; verified
source can now be learned from complete capture or imported explicitly without rewriting the wire request.
