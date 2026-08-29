# `:products:companion`

## Responsibility

Groups executable mobile companion product roots and their shared product-level dependency-injection composition.

## Dependency rule

Platform products live in child modules such as
[`:products:companion:androidApp`](androidApp/MODULE.md) and
[`:products:companion:iosApp`](iosApp/MODULE.md). The iOS Network Extension embeds the lean
Kotlin/Native [`:products:companion:iosPacketTunnel`](iosPacketTunnel/MODULE.md) runtime. Their common Koin graph lives in
[`:products:companion:di`](di/MODULE.md). Reusable business, data, connectivity, presentation, and UI modules must
not depend on this product namespace.
