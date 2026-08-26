# `:connectivity:companion`

## Responsibility

Groups platform companion connectivity adapters. It is a Gradle namespace only and owns no source code.

## Dependency rule

Each platform leaf depends on shared companion application contracts; platform leaves never depend on one
another or on the desktop proxy engine.
