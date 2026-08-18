# `:ui:desktop`

## Responsibility

Groups desktop Compose feature and shell modules. It owns no feature implementation directly.

## Dependency rule

Feature UIs live in child modules, share `:ui:core`, and invoke runtime behavior through application-facing contracts.
