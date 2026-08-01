# Architecture Specification & Migration Plan — `:data:desktop` (Frozen 10/10 Architecture)

**Target Module:** `data/desktop/`  
**Gradle Module:** `:data:desktop`  
**Package Namespace:** `com.devuloopers.knet.data.desktop`  
**Platform:** JVM (Desktop)  
**Status:** Frozen Final Stable Architecture Specification

---

# 📌 Vision

`:data:desktop` is KNet's **Desktop Data Layer**.

It is the implementation layer that bridges:
- Repository interfaces from `:core:domain`
- Storage implementations from `:storage`
- Desktop runtime engines (`:engine:*`)
- Local persistence
- Session coordination

It contains **no UI**, **no ViewModels**, **no Compose**, and **no application bootstrap logic**.

Its only responsibility is implementing domain contracts and coordinating data access.

---

# 🎯 Responsibilities & Boundaries

### Owns
- Repository implementations
- Repository coordination
- Storage adapters
- Runtime coordinators (`ProxyRuntimeRepository`, `SessionRuntimeRepository`, `CertificateRuntimeRepository`)
- Engine integration
- Local caching & datasources
- Mapping between Domain ↔ Storage (`mapper/`)
- Repository DI registration (`DesktopDataModule`)

### Explicitly Out of Scope
`:data:desktop` MUST NOT contain:
- Compose UI
- ViewModels
- Navigation & Screens
- Koin application startup
- Application lifecycle
- Business UseCases
- Domain models
- Storage implementation details
- Netty implementation details
- Certificate generation logic
- Script execution logic

---

# 🏗 Module & Package Structure

```text
data/
└── desktop/
    ├── build.gradle.kts
    │
    └── src/
        ├── main/
        │   └── kotlin/
        │       └── com/devuloopers/knet/data/desktop/
        │
        │           ├── runtime/
        │           │     ├── ProxyRuntimeRepository.kt
        │           │     ├── SessionRuntimeRepository.kt
        │           │     └── CertificateRuntimeRepository.kt
        │           │
        │           ├── apistudio/
        │           │     ├── repository/
        │           │     │    └── CollectionsRepositoryImpl.kt
        │           │     └── autocomplete/
        │           │          └── ProxyHistoryHeaderLookup.kt
        │           │
        │           ├── traffic/
        │           │     └── repository/
        │           │          └── LiveTrafficRepositoryImpl.kt
        │           │
        │           ├── inspector/
        │           │     └── repository/
        │           │          └── InspectorRepositoryImpl.kt
        │           │
        │           ├── rules/
        │           │     └── repository/
        │           │          └── RulesRepositoryImpl.kt
        │           │
        │           ├── workspace/
        │           │     └── repository/
        │           │          └── WidgetPreferencesRepositoryImpl.kt
        │           │
        │           ├── mapper/
        │           │     ├── CollectionMapper.kt
        │           │     ├── RequestMapper.kt
        │           │     └── TransactionMapper.kt
        │           │
        │           ├── datasource/
        │           │     ├── local/
        │           │     └── cache/
        │           │
        │           └── di/
        │                 └── DesktopDataModule.kt
        │
        └── test/
            └── kotlin/
                └── com/devuloopers/knet/data/desktop/
                    ├── runtime/
                    │     ├── ProxyRuntimeRepositoryTest.kt
                    │     └── SessionRuntimeRepositoryTest.kt
                    ├── repository/
                    │     ├── CollectionsRepositoryTest.kt
                    │     ├── LiveTrafficRepositoryTest.kt
                    │     ├── InspectorRepositoryTest.kt
                    │     ├── RulesRepositoryTest.kt
                    │     └── WorkspaceRepositoryTest.kt
                    ├── mapper/
                    │     └── MapperTest.kt
                    ├── di/
                    │     └── DesktopDataModuleTest.kt
                    └── MigrationRegressionTest.kt
```

---

# 🎯 Component Specifications

## Runtime Coordinators (`runtime/`)
The `runtime/` package coordinates desktop runtime components without implementing engines directly.
- **`ProxyRuntimeRepository`**: Manages proxy engine (`:engine:proxy`) startup & shutdown.
- **`SessionRuntimeRepository`**: Coordinates live proxy transaction streams and session buffering (`:engine:session`).
- **`CertificateRuntimeRepository`**: Coordinates Root CA certificate loading and caching (`:engine:certificate`).

## Repositories
Each repository implements exactly one Domain interface from `:core:domain`:
- `CollectionsRepositoryImpl` -> `CollectionsRepository`
- `LiveTrafficRepositoryImpl` -> `LiveTrafficRepository`
- `InspectorRepositoryImpl` -> `InspectorRepository`
- `RulesRepositoryImpl` -> `RulesRepository`
- `WidgetPreferencesRepositoryImpl` -> `WidgetPreferencesRepository`

No repository uses `getInstance()` or static singleton state. All dependencies are injected via constructor.

## Mapper Layer (`mapper/`)
Isolated conversion between Storage entities and Domain models:
- `CollectionMapper`
- `RequestMapper`
- `TransactionMapper`

## Dependency Injection (`di/`)
`DesktopDataModule` exports sub-modules:
```kotlin
public object DesktopDataModule {
    public val runtime: Module = module { ... }
    public val repositories: Module = module { ... }
    public val datasource: Module = module { ... }
    public val mapper: Module = module { ... }

    public val all: List<Module> = listOf(
        runtime,
        datasource,
        mapper,
        repositories
    )
}
```

---

# 🔄 Dependency Flow

```text
apps:desktop
   │
   ▼
:data:desktop
   │
   ▼
:core:domain ──► :storage ──► :engine
```

---

# 📋 Migration Phases

1. **Phase 1**: Create `data/desktop/` and register `:data:desktop` in `settings.gradle.kts`.
2. **Phase 2**: Move `CollectionsRepositoryImpl`, `LiveTrafficRepositoryImpl`, `InspectorRepositoryImpl`, `RulesRepositoryImpl`, `WidgetPreferencesRepositoryImpl`, and `ProxyHistoryHeaderLookup` into feature sub-packages.
3. **Phase 3**: Split monolithic `KNetCoreRepository` into `ProxyRuntimeRepository`, `SessionRuntimeRepository`, and `CertificateRuntimeRepository`. Remove `getInstance()`.
4. **Phase 4**: Extract mappers into `mapper/` and datasources into `datasource/`.
5. **Phase 5**: Implement `DesktopDataModule.all`.
6. **Phase 6**: Update `:apps:desktop` and consumers to resolve repositories via Koin.
7. **Phase 7**: Delete legacy root `:data` module.
8. **Phase 8**: Execute comprehensive test suite in `data/desktop/src/test/`.
