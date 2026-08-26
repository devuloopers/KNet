# `:products`

## Responsibility

Groups executable product composition roots. It owns no runtime implementation itself.

## Dependency rule

Concrete products live in child modules such as [`:products:desktop`](desktop/MODULE.md) and
[`:products:companion:androidApp`](companion/androidApp/MODULE.md); reusable modules must not depend on this group.
