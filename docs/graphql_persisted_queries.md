# GraphQL HTTP persisted queries

KNet recognizes Apollo version-1 persisted-query metadata in both GET URL parameters and POST JSON envelopes.
A SHA-256 hash identifies a document; it cannot be reversed into query text. KNet shows a verified document only
when it has captured the complete matching source or the user explicitly imports a matching local manifest.

## Behavior

- A valid hash-only request is GraphQL even without `operationName`. Its Query source is initially unavailable;
  variables, extensions, original URL parameters and raw body remain inspectable.
- When a complete captured request contains both source and a matching hash, KNet learns that mapping. The
  original UTF-8 source is hashed before formatting: comments, whitespace, Unicode and line endings matter.
- Subsequent hash-only requests may show Query source from **Captured traffic** or **Imported manifest**. The
  source is a read-only projection, separate from the captured query field and outgoing request.
- The shared Traffic viewer shows the hash and provenance. Its Overview and query shortcut refresh when a
  mapping arrives. Interception also refreshes its read-only payload; editable fields retain the actual request.
- Named breakpoints and operation-specific network conditions use only verified parsed documents. A supplied
  `operationName` alone never proves a query's name or type. Unknown, invalid, mismatched or incomplete source
  cannot match a named operation. Existing broad destination/transport rules still apply.
- GET retains query-only method constraints after resolution. A resolved mutation/subscription is viewable with
  a diagnostic and does not become a valid named GET match.
- Batch entries are resolved independently. If any persisted entry is unresolved, the batch cannot guess named
  operation matches. Batches retain the existing raw JSON inspection surface.
- Complete compressed HTTP capture bodies use the existing bounded Content-Encoding decoder before learning.
  Truncated, corrupted, unsupported or over-budget encoded input never teaches mappings.

KNet does not send additional lookup requests to the server, retry APQ requests itself, or insert a recovered
query into an outgoing hash-only request. The application's normal APQ miss/full-document fallback remains
unchanged. Response data and errors continue through the ordinary response viewer.

## API Studio

Open a captured persisted request in API Studio. The persisted-query toolbar shows its SHA-256 and source.
**View query** opens a read-only viewer when source is known. **Import manifest** selects a local JSON file for
the current absolute HTTP(S) endpoint. No query text is inserted into the editable Query field for hash-only data.

Replaying, importing a manifest, changing inspection tabs or resolving a hash preserves the request. Variables-only
edits preserve the original query/hash and unrelated fields. Explicitly editing Query updates existing valid APQ
hash metadata from the exact new source, keeping the Extensions editor synchronized. Explicit Extensions edits
remain user-controlled. Invalid hash metadata remains diagnosable and replayable for server troubleshooting.

### Supported manifests

An Apollo persisted-query manifest has `format: "apollo-persisted-query-manifest"`, numeric `version: 1`, and an
`operations` array. Every operation supplies `id` (SHA-256) and `body` (exact query); optional `name` and `type`
metadata must agree with the parsed document. A simple `{ "<sha256>": "<query source>" }` mapping is also accepted.

Imports validate every document before changing the catalog. A hash mismatch, invalid syntax, ambiguous operation,
unsupported format or allocation-limit failure rejects the whole import. Arbitrary server-specific persisted IDs,
Relay identifiers and remote registry downloads are outside this HTTP APQ feature.

## Scope, retention and memory

The catalog key is **HTTP(S) scheme + lowercase hostname + effective port + exact encoded path + SHA-256**.
Query parameters, fragments, userinfo, headers, variables and response bodies are not catalog keys or retained data.
Learning/lookup requires an absolute endpoint; relative targets cannot leak mappings across unknown hosts. Different
schemes, ports, hosts and paths stay isolated. Clients using the same endpoint and exact verified document hash
share its source mapping.

The process-local catalog retains source documents only. It has a 256-entry LRU limit, a conservative 4 MiB retained
weight limit and a 30-minute lifetime from learning/import. Reading does not extend lifetime. Every individual
HTTP decode is bounded to 1 MiB, parser depth/tokens are bounded, and native manifest reads retain at most 2 MiB
plus one overflow marker byte. Imports must fit both catalog count and weight limits. These are catalog/inspection
limits, not a claim that the entire desktop process uses 4 MiB RAM.

Mappings are not written to Room. Closing the desktop DI container clears them; after restart they must be learned
or imported again. Captured request bytes and generic annotations keep their existing persistence semantics. A
capture-time unavailable annotation cannot suppress a later verified quick-add operation suggestion. Overview uses
current prepared facts without rewriting historical annotations.

## Ownership

- `:core:domain`: typed HTTP document, hash metadata, provenance and diagnostic values.
- `:application:desktop`: catalog and bounded file-reader contracts, import/observation use cases.
- `:engine:protocol`: decoder, exact hashing, synchronized bounded LRU/TTL catalog, verification, imports and rule facts.
- `:engine:formatter`: presentation adapter and source-preserving edits, using application contracts without UI ownership.
- `:ui:desktop:httpPanel`: shared read-only viewer and editable source buffers.
- Traffic and interception view models: revision-aware preparation without blanking the current inspector.
- API Studio: ephemeral preview/import feedback and native file selection; documents remain outside saved edit buffers.
- `:products:desktop`: bounded native reader, shared catalog/use-case DI and shutdown cleanup.

No new library, database table or migration is introduced. Existing graphql-java, serialization and transport-decoding
implementations are reused.

## Automated qualification

The focused suites cover exact UTF-8 hashing, APQ miss/fallback/hash-only sequences, GET/POST/batches, selector/method
constraints, endpoint isolation, malformed/mismatched metadata, truncated/encoded bodies, cache count/weight/expiry,
concurrent access, atomic Apollo/simple imports, file-size/read/cancellation failures, source-preserving edits, shared
rendered unknown/resolved states, API Studio replay/import, Traffic/interception refresh and current Overview/quick-add
facts. Capture-to-Room integration verifies original hash-only bytes are retained while later inspection resolves the
source. Product DI tests verify the same catalog and cleanup. Existing GET/POST capture/replay and persistence suites
remain part of qualification.

Final qualification passed **449 tests with zero failures, errors or skips**: protocol 60, formatter 63, application 66,
shared HTTP panel 54, Traffic 119, interception 8, API Studio 62, selected product/DI suites 8, and selected data
capture/persistence/semantic suites 9. Desktop compilation, all six architecture checks and `git diff --check` passed.
Actual unknown/resolved viewer and compact import toolbar renders were reviewed. Final log: `/tmp/knet-persisted-final.log`.

Automated runs used isolated build outputs and temporary test databases, without installing/launching KNet or
accessing its user database. See `docs/implementation_plan.md` for the project board.

## Manual verification

1. Capture a real client's hash-only GraphQL request. Confirm Query source unavailable, retained variables/extensions
   and the original raw request. Forward/replay it unchanged.
2. Allow that client's normal APQ miss fallback with the full document and hash. Revisit the first row; verify the
   resolved source/provenance and query shortcut, then confirm the next hash-only request still has no query field.
3. Open the hash-only request in API Studio, import its exact manifest and view its read-only document. Replay and
   confirm on the server that method, URL/body and hash-only semantics remain unchanged.
4. Edit Variables, then Query. Check Variables preserves the original hash, while Query intentionally refreshes it.
5. Add an operation-specific breakpoint/network condition and verify it matches only after source is known. Verify
   another host/path cannot inherit the mapping. Restart KNet and confirm lookup source must be learned/imported again.

Physical-device/client interoperability remains a manual check; automated results do not claim it was performed.

## Protocol references

- [Apollo automatic persisted queries](https://www.apollographql.com/docs/apollo-server/performance/apq)
- [Apollo persisted-query manifests](https://www.apollographql.com/docs/react/data/persisted-queries)
- [GraphQL-over-HTTP working draft](https://http-spec.graphql.org/draft/)
