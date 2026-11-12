# GraphQL HTTP GET and POST implementation plan

Status: IMPLEMENTED; automated qualification complete, physical verification pending (2026-11-12).

## Objective and scope

Provide consistent GraphQL inspection for these two HTTP request forms:

| Form | GraphQL source | Intended operations | Query shortcut |
| --- | --- | --- | --- |
| POST with application/json | JSON request body | Queries and mutations | Request > Body > Query |
| GET with URL parameters | query, variables, operationName and extensions in the URL | Queries | Request > Params > Query |

The same formatted Query, Variables and Extensions viewer will serve both sources. A source label distinguishes
JSON body from URL parameters. Original method, URL, ordered/repeated parameters, headers and body remain canonical.
GET inspection does not create a synthetic HTTP body or convert the captured request into POST.

The baseline follows the current GraphQL-over-HTTP working draft, not a finalized transport standard:

- [GraphQL HTTP guidance](https://graphql.org/learn/serving-over-http/)
- [GraphQL-over-HTTP request and method definitions](https://http-spec.graphql.org/draft/)

Existing raw-body, batched, persisted-query, WebSocket and SSE behaviors retain their current compatibility/fallback
paths. Expanding their semantic support is separate from this GET/POST increment. A missing document remains
unavailable; a hash or operation name alone cannot supply its source text.

## Audited starting point

- `GraphQLDocumentParser` reads JSON bodies and derives operation facts using a declaration regex. It does not
  decode GraphQL GET parameters or reliably resolve every multi-operation document.
- `GraphQLSemanticInspector` returns a title-only hint when no body is present. Detailed GET operation metadata
  is therefore absent even when the URL contains a complete query document.
- `GraphQLBreakpointExtension` and `GraphQLNetworkConditionExtension` have separate URL operationName fallbacks;
  they can assume Query from a name without resolving the supplied document.
- `GraphQlRequestDescriptorStrategy` formats request bodies; GET operation names are not extracted from query text.
- `UrlQueryStringParser` preserves encoded key/value strings. It is a general transport helper, not a GraphQL
  form-decoding implementation; globally changing it would affect other request types and replay semantics.
- Prepared request payloads and GraphQLQueryAvailability currently describe the body. View query is wired to
  Request > Body > Query. GET needs a separate derived parameter presentation and source-aware navigation.
- API Studio imports preserve a method/URL/body through NetworkRequestSpec, but structured GraphQL editing and
  send/save paths need an explicit GET round-trip audit.

## Architecture and data ownership

1. Define typed source, extraction outcome and selected-operation facts in existing core/domain/application
   contracts. Keep transport-preservation data distinct from derived GraphQL presentation.
2. Implement one bounded GraphQL HTTP source decoder in the existing GraphQL protocol engine. It selects its
   authoritative source from method and request encoding, decodes an envelope, then uses shared GraphQL AST
   helpers to resolve operations. Reuse the already bundled graphql-java dependency rather than adding a second
   GraphQL library.
3. Adapt semantic inspection, request descriptors, breakpoint matching, Network Conditions suggestions/matching,
   Traffic preparation and API Studio import/send to those same results. Remove duplicated GET-name guessing.
4. Formatter code becomes an adapter to the shared parsing/formatting result. Validate the dependency graph and
   update architecture allowlists deliberately if a formatter-to-protocol dependency is needed. No inverse
   dependency or parser implementation in UI, Room or generic proxy handlers is allowed.
5. Preserve existing GraphQL WebSocket envelope parsing and its operation-ID correlation through regression tests
   when shared document parsing is refactored.
6. Derived decoded documents obey the existing preview/cache budgets and capture identity fences. Persisted
   annotations retain compatibility; version any changed annotation meaning and invalidate/recompute only affected
   derived caches. Original captured requests are not rewritten.

## Phase 1: Decoder and contract baseline [COMPLETED]

- [x] Introduce typed JSON_BODY and URL_PARAMETERS sources plus explicit available, unavailable, malformed,
      truncated/limited and ambiguous-operation outcomes.
- [x] Record both supplied operationName and resolved selected-operation facts so unknown or invalid selection
      cannot be presented as a successfully resolved operation.
- [x] POST: decode the application/json object with query, operationName, variables and extensions. Handle
      optional values according to the HTTP draft, including absent/null values and unknown extension metadata.
- [x] GET: split raw query pairs before decoding; apply UTF-8 form decoding exactly once to names and values.
      Decode plus as space while preserving encoded literal plus, ampersand, equals and percent characters.
- [x] Parse variables/extensions JSON as objects with explicit handling of absent/empty/null optional values;
      malformed JSON or incorrect types remain visible diagnostics.
- [x] Treat duplicate recognized GraphQL parameters as ambiguous for semantic extraction, including duplicates
      created by encoded parameter names. Preserve every original pair instead of silently selecting one.
- [x] Use the POST body as its authoritative GraphQL source and GET parameters as theirs. Preserve conflicting
      non-authoritative data for inspection without silently merging it into a different operation.
- [x] Recognize complete GraphQL documents at arbitrary endpoint paths; /graphql is a hint, not a requirement.
- [x] Use bounded AST parsing for named, anonymous, shorthand, commented and fragment-containing documents.
- [x] Honor operationName for multiple operations. Missing/unknown/duplicate selection is explicit; never choose
      the first operation silently. Query source remains viewable even when operation selection is invalid.
- [x] Identify a mutation supplied over GET as an incompatible method/operation combination. Capture and display
      it accurately without converting it, pretending it is Query or adding a new proxy rejection policy.
- [x] Define/test raw input, decoded field, AST token/depth, operation-count and retained-cache limits before
      expensive parsing. Respect existing proxy URI/header limits rather than increasing them automatically.

## Phase 2: Detection, annotation and rule consumers [COMPLETED]

- [x] Reuse the decoder in semantic annotations and request descriptors for GET and POST.
- [x] Resolve BootStrapQuery from its document even without a separate operationName parameter.
- [x] Retain operation type/name, source and honest diagnostic state as compact metadata; keep Overview compact.
- [x] Derive breakpoint and Network Conditions operation criteria from the resolved operation, independent of source.
- [x] Remove assumptions that a URL operationName implies a Query or that a GraphQL HTTP operation requires a body.
- [x] Permit GET operation matching before waiting for body capture; retain genuine POST body-completeness checks.
- [x] Feed the same facts into Traffic-to-rule suggestions and live request/response matching. Match the same valid
      operation equivalently over either source and retain its request observation for response-phase breakpoints.
- [x] Use an explicit conservative result for malformed, truncated or ambiguous operation-specific matching.
      Generic HTTP/domain handling continues to apply; unavailable parsing does not become an inspection failure.
- [x] Keep variables and full query text out of persisted operation selector rules and bandwidth scheduling state.

## Phase 3: Source-aware Traffic and shared viewer [COMPLETED]

- [x] Prepare bounded decoded GET query/variables/extensions off-thread with the selected canonical request.
- [x] Generalize the existing GraphQL viewer to accept source-aware prepared data, preserving its stable editor
      call site, theme, copy behavior and controlled Query/Variables/Extensions selection.
- [x] POST opens Request > Body > Query. GET opens Request > Params > Query through the same View query shortcut.
- [x] Keep original GET parameter pairs accessible in a raw-parameters view. POST keeps Raw JSON. Do not label
      normalized GET parameters as a captured JSON body.
- [x] Show a compact JSON body / URL parameters source label where it explains the displayed document.
- [x] Availability and tooltip copy follow the actual source, including loading, malformed input, missing query,
      unsupported extensions, retention limits and unavailable data. Do not direct GET users to an empty body.
- [x] Preserve operation metadata without repeating the full query or target in Overview. Header copy retains
      the original URL, including its encoded parameters.
- [x] Keep selection/cache identity fences, missing/truncated indicators, narrow-pane overflow and the confirmed
      scrollbar measurement fix. Source changes must not remount or animate the whole inspector.

## Phase 4: API Studio, editing and replay preservation [COMPLETED]

- [x] Import GET and POST with their actual method and source, and populate Query/Variables/Extensions consistently.
- [x] Preserve GET and its empty body on send, save/reopen, collection import and replay. Preserve POST JSON body
      placement. Viewing or importing a capture alone must never execute it.
- [x] Audit structured editor synchronization so editing a GET query writes the GraphQL URL parameters rather
      than creating a request body or silently changing the method.
- [x] Re-encode edited GET parameters exactly once while preserving untouched parameters and unrelated request
      metadata. Preserve the original encoded target for unedited replay.
- [x] Keep breakpoint editing source-aware: a GET query replacement updates its URL parameter; a POST replacement
      updates its JSON body. Preserve unrelated fields and existing validation/continuation behavior.
- [x] Keep method changes deliberate and make GET mutation incompatibility visible in authoring validation.
      Do not silently convert methods to make a request appear valid.
- [x] Preserve request/draft/collection compatibility; version stored authoring state only if a new source field
      must be persisted, with regression fixtures for existing saved requests.

## Phase 5: Qualification matrix [COMPLETED]

All rows require unit coverage, with consumer/UI/integration coverage where indicated. Fixtures use synthetic
hosts and values; the user's staging URL supplies a regression shape but is never called automatically.

| Area | Required cases |
| --- | --- |
| POST envelope | Named/anonymous query; mutation; operationName; variables/extensions; empty optional values; UTF-8 and content-type parameters |
| GET envelope | User's BootStrapQuery shape; named query without operationName; variables/extensions JSON; arbitrary endpoint; empty body |
| URL decoding | Plus/space, encoded literal plus, UTF-8, encoded delimiters, percent literals, encoded names, no double decoding |
| Invalid encoding | Incomplete/invalid escapes, invalid UTF-8, duplicate recognized keys, conflicting sources, malformed JSON, wrong JSON types |
| Document semantics | Comments/BOM, shorthand selection, fragments, multiple operations, explicit selection, missing/unknown name, malformed syntax, GET mutation |
| Missing document | Empty/missing query, hash-only metadata, incomplete/unavailable bodies, unsupported encodings; raw data remains accessible |
| Bounds | Large/deep documents and variables, oversized URLs within/above configured capture limits, operation/token limits, rapid cancellation |
| Matching | Equivalent GET/POST breakpoint and condition selection; operation-specific negatives; source-aware suggestions; response-phase observations |
| Viewer/navigation | Correct source label; Query/Variables/Extensions and raw view; View query routing; unavailable/truncated states; selection isolation |
| Editing/replay | GET remains GET with no synthetic body; POST remains JSON body; escaped edits; untouched pairs; save/reopen/import and collections |
| Layout | Wide/narrow panes, larger fonts, repeated selection, late preparation, source switching, tooltip dismissal, frame-by-frame scrollbar geometry |
| Compatibility | Existing raw/batch/persisted fallbacks, GraphQL WebSocket parser/correlation, non-GraphQL HTTP bodies, existing stored annotations/drafts |

- [x] Add decoder contract/AST unit suites and shared encoding fixtures.
- [x] Add semantic/descriptive/rule-consumer regressions against the same decoded facts.
- [x] Add headless UI action and per-frame geometry tests without launching the desktop application.
- [x] Add local real-socket capture/import/replay tests for both methods; assert the server-observed method,
      request target, headers and body, including edited GET values and cancellation.
- [x] Run relevant module suites, desktop compilation, shared Android/iOS UI compilation and architecture checks
      with isolated build outputs. Run qualification-ledger validation where affected.
- [x] Record actual case counts/results and residual platform checks in the implementation report. Physical-device
      verification remains a separately tracked check; software tests are not described as physical qualification.

## Acceptance criteria

1. The supplied GET shape is identified as GraphQL Query / BootStrapQuery and shows decoded Query and Variables,
   including device/language/siteId, with Source: URL parameters.
2. Equivalent POST JSON requests show the same semantic facts with Source: JSON body.
3. View query navigates to the right source and Query tab regardless of previously selected sub-tabs.
4. Named breakpoint/Network Conditions rules and Traffic-created suggestions behave consistently for both forms.
5. Import, structured editing, persistence and replay preserve method and source; unedited GET parameters are not
   rewritten or duplicated, and no body is invented.
6. Malformed, missing, ambiguous and limited inputs remain diagnosable without false operation matching, lost raw
   data or changes to forwarding policy.
7. Existing inspector geometry and selection isolation regressions pass. The resulting report separates completed
   automated checks from any remaining physical-device verification.

## Completion evidence and physical follow-up

All five software phases are complete. The [implementation report](graphql_http_get_post_implementation_report.md)
records 435 passing tests, 43 added test methods, loopback capture/replay/cancellation, Room restart/promotion,
headless layout evidence, desktop/shared-platform compilation and architecture verification. Original captures
remain unchanged. Physical checks are intentionally tracked separately:

- [ ] Rebuild/relaunch KNet and capture GET/POST from physical clients.
- [ ] Verify source navigation, named breakpoints/conditions, editing/save/replay and real pointer/keyboard behavior.
