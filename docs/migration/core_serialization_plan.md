# Core Serialization Module Plan — `:core:serialization`

**Target Module:** `core/serialization/`  
**Gradle Module:** `:core:serialization`  
**Package Namespace:** `com.devuloopers.knet.core.serialization`  
**Platform:** Kotlin Multiplatform (`commonMain`, `commonTest`)  
**Status:** Approved for Creation

---

# 📌 Vision

`:core:serialization` is KNet's shared serialization infrastructure.

It provides a single, reusable serialization configuration for every KNet module and application.

Its responsibilities are intentionally limited to:
- Shared `Json` configuration (`KNetJson.default` and `KNetJson.pretty`)
- Common custom serializers (`UuidSerializer`)
- Serialization utility helpers (`SerializationHelper`)

This module contains **no business logic**, **no domain models**, and **no protocol-specific serializers**.

---

# 🎯 Responsibilities

The module is responsible for:
- Providing shared `Json` instances
- Providing reusable custom serializers
- Providing safe JSON helper utilities
- Maintaining consistent serialization behavior across Desktop, Mobile, CLI, and Tests

---

# 🚫 Explicitly Out of Scope

This module MUST NOT contain:
- Domain models
- API models
- HAR models
- Network DTOs
- Database entities
- Ktor configuration
- Compose
- Netty
- SQL
- Business logic

---

# 📂 Recommended Directory Structure

```text
core/
└── serialization/
    ├── build.gradle.kts
    │
    └── src/
        ├── commonMain/
        │   └── kotlin/
        │       └── com/devuloopers/knet/core/serialization/
        │
        │           ├── KNetJson.kt
        │           ├── SerializationHelper.kt
        │           │
        │           └── serializer/
        │                 └── UuidSerializer.kt
        │
        └── commonTest/
            └── kotlin/
                └── com/devuloopers/knet/core/serialization/
                      ├── KNetJsonTest.kt
                      ├── UuidSerializerTest.kt
                      └── MigrationRegressionTest.kt
```

---

# 🏗 Components

## KNetJson

Provides centralized Json configuration.

```kotlin
object KNetJson {
    val default = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    val pretty = Json(default) {
        prettyPrint = true
        prettyPrintIndent = "    "
    }
}
```

## SerializationHelper

Provides lightweight helper functions around `KNetJson`:
- `decode<T>()`
- `decodeOrNull<T>()`
- `encode()`
- `encodePretty()`

## UuidSerializer

Provides multiplatform serialization support for Kotlin UUID values (`kotlin.uuid.Uuid`).

---

# 📦 Dependencies

Depends on:
- Kotlin Stdlib
- `kotlinx.serialization`

Must NOT depend on:
- `:core:domain`
- `:core:http`
- `:engine:*`
- UI modules / Compose
- Netty / SQL / Ktor

---

# 🧪 Test Architecture (`commonTest/`)

- `KNetJsonTest.kt`: Verify default and pretty Json configuration (`ignoreUnknownKeys`, `encodeDefaults`, `coerceInputValues`, `explicitNulls`, pretty printing).
- `UuidSerializerTest.kt`: Verify UUID serialization and deserialization round-trip.
- `MigrationRegressionTest.kt`: Verify public API stability after migration and ensure `KNetJson` remains the single shared entry point.
