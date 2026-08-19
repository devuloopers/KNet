# `:ui:desktop:traffic`

## Responsibility

Owns the live traffic table, filtering, selection, inspector coordination, and traffic-specific desktop presentation state.

## Owns

- Traffic ViewModel, table/toolbar UI, selection, filters, bounded keyset-page window, generic semantic annotation presentation, typed live-interception projection, and `TrafficRowUiState`.

## Does not own

- Captured bytes, canonical exchanges, persistence, proxy lifecycle, protocol parsers, or product DI bindings.

## Dependency rule

Query traffic through application use cases and `TrafficQueryPort`; control capture attachment, required
proxy configuration rebinding, and clear history through application use cases. Never retain unbounded body
bytes in UI state. Traffic detail already follows this rule with a one-mebibyte preview bound per message.

## Current state

Traffic list/detail, capture control, proxy lifecycle observation, semantic annotations, pending breakpoints,
and clear orchestration use application use cases. Start/Stop changes capture attachment while the running
listener continues forwarding; only a proxy-port change performs a full configuration rebind. Active breakpoint
candidates are joined to durable rows only by canonical `ExchangeId`; a body-free provisional row is used until
capture metadata arrives, remains visible despite ordinary filters, and is replaced without duplication. Paused
rows display `In Progress`, a warning highlight, and a typed phase/rule marker. Starting a new capture session
merges its rows into the bounded visible history; only the explicit clear workflow removes stored traffic. The
list holds at most 1,000 metadata rows across keyset pages, detail bodies are range-loaded with preview bounds,
and no production UI code imports proxy, storage, certificate, or script runtimes. Assembly for this feature
lives in `:products:desktop` under `di/traffic`.
