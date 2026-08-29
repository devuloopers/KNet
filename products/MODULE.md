# `:products`

## Responsibility

Groups executable product composition roots. It owns no runtime implementation itself.

## Dependency rule

Concrete products live in child modules such as [`:products:desktop`](desktop/MODULE.md) and
[`:products:companion:androidApp`](companion/androidApp/MODULE.md) and
[`:products:companion:iosApp`](companion/iosApp/MODULE.md). Product-shared composition support lives in
[`products:companion:di`](companion/di/MODULE.md); reusable modules must not depend on this group.
