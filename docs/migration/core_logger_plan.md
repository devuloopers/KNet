# Implementation Plan — `:core:logger`

**Module:** `:core:logger` (`core/logger/`)  
**Package Namespace:** `com.devuloopers.knet.core.logger`  
**Platform:** Kotlin Multiplatform (Desktop, Android, iOS, CLI)  
**Logging Backend:** Kermit  
**Status:** Approved for Migration

---

# 📌 Vision

`:core:logger` is KNet's shared logging infrastructure.

It provides a consistent logging API across all KNet applications while delegating log output to **Kermit**.

Every module in KNet logs through this module.

This module is intentionally lightweight and contains no business logic.

---

# 🎯 Responsibilities

The module is responsible for:

- Logger initialization
- Shared logger instances
- Log tags
- Log level configuration
- Structured logging
- Exception logging
- Kermit configuration

---

# 🚫 Explicitly Out of Scope

This module MUST NOT contain:

- Compose
- Netty
- Ktor
- SQL
- Room
- File APIs
- Domain models
- Business logic

---

# 📂 Recommended Package Structure

```text
core/
└── logger/
    ├── build.gradle.kts
    │
    └── src/
        ├── commonMain/
        │
        │   └── kotlin/
        │       └── com/devuloopers/knet/core/logger/
        │
        │           ├── KNetLogger.kt
        │           ├── LoggerFactory.kt
        │           ├── LoggerConfiguration.kt
        │           ├── LogTags.kt
        │           └── internal/
        │
        └── commonTest/
            │
            ├── KNetLoggerTest.kt
            ├── ThrowableLoggingTest.kt
            ├── LoggerConfigurationTest.kt
            ├── LoggerFactoryTest.kt
            ├── LoggerThreadSafetyTest.kt
            └── MigrationRegressionTest.kt
```

---

# 🏗 Core Components

## KNetLogger

Shared logging API used throughout KNet.

Provides:
- verbose()
- debug()
- info()
- warn()
- error()

Supports:
- message logging
- throwable logging
- custom tags

---

## LoggerFactory

Responsible for creating configured Kermit logger instances.

All modules obtain loggers through this factory.

Example:
```kotlin
val logger = LoggerFactory.get("Proxy")
```

---

## LoggerConfiguration

Represents global logger configuration.

Example:
```kotlin
data class LoggerConfiguration(
    val minimumSeverity: Severity = Severity.Info,
    val enableThreadName: Boolean = false,
    val enableTimestamp: Boolean = true
)
```

---

## LogTags

Centralized log tag constants:
- `API_STUDIO`
- `PROXY`
- `CERTIFICATE`
- `INTERCEPTOR`
- `TRAFFIC`
- `SESSION`
- `SCRIPT`
- `HTTP`
- `DATABASE`
- `WORKSPACE`

Avoid hardcoded strings throughout the project.

---

# 🔄 Migration Tasks

## Module Migration
- Move `logger/` → `core/logger/`
- Rename Gradle module
- Update package namespace

## Gradle
Update:
```kotlin
include(":logger")
```
to
```kotlin
include(":core:logger")
project(":core:logger").projectDir = file("core/logger")
```

## Dependencies
Replace `project(":logger")` with `project(":core:logger")` across all modules.

## Source Imports
Replace `com.devuloopers.knet.logger` with `com.devuloopers.knet.core.logger`.

---

# 🧪 Test Plan (`commonTest/`)

- `KNetLoggerTest`: `verbose()`, `debug()`, `info()`, `warn()`, `error()`
- `ThrowableLoggingTest`: exception logging & stack traces
- `LoggerConfigurationTest`: minimum severity & options
- `LoggerFactoryTest`: logger creation & tag assignment
- `LoggerThreadSafetyTest`: concurrent logging safety
- `MigrationRegressionTest`: package migration stability

---

# 📦 Dependencies

Depends on:
- Kotlin Stdlib
- Kermit (`co.touchlab.kermit`)

Must NOT depend on:
- Compose, Netty, Ktor, SQL, Room, Domain, Engine, UI
