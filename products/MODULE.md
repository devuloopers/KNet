# `:products`

## Responsibility

Groups executable product composition roots. It owns no runtime implementation itself.

## Dependency rule

Concrete products live in child modules such as [`:products:desktop`](desktop/MODULE.md); reusable modules must not depend on this group.
