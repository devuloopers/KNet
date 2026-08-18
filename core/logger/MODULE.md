# `:core:logger`

## Responsibility

Provides the multiplatform logging facade, tags, configuration, and logger factory shared by KNet modules.

## Owns

- Logging contracts and configuration.
- Safe throwable and structured-message logging behavior.

## Does not own

- Log persistence, telemetry export, UI consoles, or feature lifecycle policy.

## Dependency rule

Remain a leaf core utility with no project-module dependency.

## Migration direction

Keep as the common logging abstraction; add sinks through composition rather than feature dependencies.
