# `:products:companion`

## Responsibility

Groups executable mobile companion product roots. It is a Gradle namespace and owns no runtime source.

## Dependency rule

Platform products live in child modules such as
[`:products:companion:androidApp`](androidApp/MODULE.md). Shared companion modules must not depend on this namespace.
