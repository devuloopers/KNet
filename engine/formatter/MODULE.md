# `:engine:formatter`

## Responsibility

Formats and presents body content into bounded, human-readable or structured representations.

## Owns

- Content-type-aware body formatters and formatter selection.
- JSON, HTML, text, image, GraphQL, CBOR, MessagePack, and stream presentation helpers.

## Does not own

- Body-byte lifetime, storage, capture, protocol classification, or UI layout.

## Dependency rule

Operate on supplied bounded content and metadata; remain independent of proxy lifecycle and desktop UI.

## Migration direction

Consume `BodyRef` through explicit body readers and return derived views without copying entire captured bodies by default.
