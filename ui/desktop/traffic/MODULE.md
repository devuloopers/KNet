# `:ui:desktop:traffic`

## Responsibility

Owns the live traffic table, filtering, selection, inspector coordination, and traffic-specific desktop presentation state.

## Owns

- Traffic ViewModel, table/toolbar UI, selection, filters, bounded keyset-page window, generic semantic annotation presentation, and `TrafficRowUiState`.

## Does not own

- Captured bytes, canonical exchanges, persistence, proxy lifecycle, protocol parsers, or product DI bindings.

## Dependency rule

Query traffic through application use cases and `TrafficQueryPort`; control proxy lifecycle and clear history through application use cases. Never retain unbounded body bytes in UI state. Traffic detail already follows this rule with a one-mebibyte preview bound per message.

## Current state

Traffic list/detail, proxy lifecycle, semantic annotations, and clear orchestration use application use cases. Starting a new capture session merges its rows into the bounded visible history; only the explicit clear workflow removes existing traffic. The list holds at most 1,000 metadata rows across keyset pages, detail bodies are range-loaded with preview bounds, and no production UI code imports proxy, storage, certificate, or script runtimes. Assembly for this feature lives in `:products:desktop` under `di/traffic`.
