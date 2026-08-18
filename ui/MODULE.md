# `:ui`

## Responsibility

Groups reusable and platform-specific presentation modules. It owns no presentation implementation directly.

## Dependency rule

UI depends inward on use cases and stable models; core, application, engines, and data adapters must not depend on UI.
