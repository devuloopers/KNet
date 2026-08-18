# `:engine`

## Responsibility

Groups independently testable runtime capability implementations such as proxying, certificates, interception, scripting, sessions, protocol inspection, and formatting.

## Dependency rule

Each child engine has a narrow responsibility and integrates through contracts; engines do not own UI or application composition.
