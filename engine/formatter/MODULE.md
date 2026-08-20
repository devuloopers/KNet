# `:engine:formatter`

## Responsibility

Formats and presents body content into bounded, human-readable or structured representations.

## Owns

- Content-type-aware body formatters and formatter selection.
- JSON, HTML, text, image, GraphQL, CBOR, MessagePack, and stream presentation helpers.
- GraphQL request-descriptor contribution backed by the same envelope and AST parser used for body formatting;
  it accepts authored, pending, or captured descriptor input and supplies both the operation title and `GQL`
  semantic badge without replacing the HTTP transport method.
- JSON transport-shape detection: a valid complete JSON document is resolved first; explicit
  NDJSON/JSONL media types and otherwise independently valid newline records resolve to
  `BodyFormat.JsonStream`.

## JSON rule

JSON, NDJSON, and JSONL do not have separate parsing or editor-language implementations. NDJSON and
JSONL are newline-framed sequences of ordinary JSON values. This module formats every record with the
same JSON formatter and preserves record boundaries in `BodyFormat.JsonStream`; presentation modules
continue to use the single JSON language contribution.

## Does not own

- Body-byte lifetime, storage, capture, protocol classification, or UI layout.

## Dependency rule

Operate on supplied bounded content and metadata; remain independent of proxy lifecycle and desktop UI. May
implement narrow `:core:domain` extension contracts without owning API Studio state or persistence.

## Migration direction

Consume `BodyRef` through explicit body readers and return derived views without copying entire captured bodies by default.
