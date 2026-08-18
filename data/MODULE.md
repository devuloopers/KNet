# `:data`

## Responsibility

Groups platform data adapters that implement inward-facing repository and application ports. It owns no implementation directly.

## Dependency rule

Platform adapters live in child modules such as [`:data:desktop`](desktop/MODULE.md); stable contracts must not depend on this group.
