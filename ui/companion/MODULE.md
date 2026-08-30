# `:ui:companion`

## Responsibility

Groups companion presentation and shared Compose UI modules. It is a Gradle namespace only and contains no source code or runtime
behavior.

## Dependency rule

Leaf presentation modules define their own dependencies; this grouping project must remain empty.

## Children

- [`:ui:companion:presentation`](presentation/MODULE.md) owns framework-neutral state and behavior.
- [`:ui:companion:sharedUi`](sharedUi/MODULE.md) owns Compose Multiplatform screens and UI resources shared by
  Android and iOS products.
