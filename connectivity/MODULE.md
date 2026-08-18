# `:connectivity`

## Responsibility

Groups platform-specific connectivity implementations. It owns no shared contract or mechanism implementation itself.

## Dependency rule

Shared contracts belong to `:core:connectivity`; implementations live in child modules such as [`:connectivity:desktop`](desktop/MODULE.md).
