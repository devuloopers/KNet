# `:ui:desktop:apiStudio:graphqlWebSocket`

## Responsibility

Provides the contributed GraphQL subscription editor for API Studio. It owns incomplete workspace draft state,
Compose presentation, a lifecycle-aware ViewModel, and the versioned workspace payload codec.
It uses the common resizable authoring/result geometry: endpoint controls remain with query authoring, while the
subscription timeline occupies the complete result-pane height.

## Boundaries

- Uses only application/use-case contracts and shared `:ui:core` components.
- Reuses the common API Studio workspace and collection sidebar; it has no GraphQL-specific repository.
- Does not import proxy, persistence, Netty, Java WebSocket, or GraphQL engine implementations.
- A blank editor stays transient until the first meaningful edit.
