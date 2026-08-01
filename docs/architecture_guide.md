# KNet Architecture (v2.0)

This document defines the long-term module architecture for KNet.

The architecture is designed around three core principles:

- Kotlin Multiplatform first
- Desktop-first proxy engine
- Clean Architecture & Domain Driven Design

---

# 🌳 Root Directory Structure

```text
knet/
│
├── settings.gradle.kts
├── build.gradle.kts
│
├── apps/
│   │
│   └── desktop/                                     📦 :apps:desktop [COMPLETED]
│       ├── build.gradle.kts
│       ├── data/                                    (Desktop Repository Implementations)
│       ├── di/
│       ├── launcher/
│       ├── updater/
│       └── resources/
│
│
├── ui/
│   │
│   ├── core/                                        📦 :ui:core [COMPLETED]
│   │   └── build.gradle.kts
│   │
│   └── desktop/
│       ├── app/                                     📦 :ui:desktop:app (Phase 1) [COMPLETED]
│       │    build.gradle.kts
│       │
│       ├── workspace/                               📦 :ui:desktop:workspace (Phase 2) [COMPLETED]
│       │    build.gradle.kts
│       │
│       ├── apistudio/                               📦 :ui:desktop:apistudio (Phase 3) [COMPLETED]
│       │    build.gradle.kts
│       │
│       ├── inspector/                               📦 :ui:desktop:inspector (Phase 4) [COMPLETED]
│       │    build.gradle.kts
│       │
│       ├── traffic/                                 📦 :ui:desktop:traffic (Phase 5) [COMPLETED]
│       │    build.gradle.kts
│       │
│       ├── scripting/                               📦 :ui:desktop:scripting (Phase 6) [COMPLETED]
│       │    build.gradle.kts
│       │
│       ├── certificate/                             📦 :ui:desktop:certificate (Phase 7) [COMPLETED]
│       │    build.gradle.kts
│       │
│       └── codeEditor/                              📦 :ui:desktop:codeEditor [COMPLETED]
│            build.gradle.kts
│
│
├── core/
│   │
│   ├── domain/                                      📦 :core:domain (KMP) [COMPLETED]
│   │    build.gradle.kts
│   │
│   ├── http/                                        📦 :core:http (KMP) [COMPLETED]
│   │    build.gradle.kts
│   │
│   ├── logger/                                      📦 :core:logger (KMP) [COMPLETED]
│   │    build.gradle.kts
│   │
│   └── serialization/                               📦 :core:serialization (KMP) [COMPLETED]
│        build.gradle.kts
│
│
├── engine/
│   │
│   ├── proxy/                                       📦 :engine:proxy [COMPLETED]
│   │    build.gradle.kts
│   │
│   ├── certificate/                                 📦 :engine:certificate [COMPLETED]
│   │    build.gradle.kts
│   │
│   ├── traffic/                                     📦 :engine:traffic [COMPLETED]
│   │    build.gradle.kts
│   │
│   ├── interceptor/                                 📦 :engine:interceptor [COMPLETED]
│   │    build.gradle.kts
│   │
│   ├── simulator/                                   📦 :engine:simulator [COMPLETED]
│   │    build.gradle.kts
│   │
│   ├── session/                                     📦 :engine:session [COMPLETED]
│   │    build.gradle.kts
│   │
│   ├── protocol/                                    📦 :engine:protocol [COMPLETED]
│   │    build.gradle.kts
│   │
│   └── script/                                      📦 :engine:script [COMPLETED]
│        build.gradle.kts
│
│
├── testing/
│   └── server/                                      📦 :testing:server [COMPLETED]
│        build.gradle.kts
│
└── docs/
```

---

# 📦 Module Responsibilities

## apps/

Application entry points.

Responsible for:

- Dependency Injection
- Repository implementations
- Window lifecycle
- Platform services
- Application startup
- Platform integrations

Each application owns its own implementations.

Examples:

Desktop
- DesktopCollectionRepository
- DesktopWorkspaceRepository
- DesktopSettingsRepository

---

## ui/

Contains presentation only.

Must never contain:
- Networking
- SQL
- Netty
- Repository implementations

Contains:
- Compose UI
- ViewModels
- UI State
- Navigation
- Dialogs
- Screens

### ui:desktop:app [COMPLETED]

Owns application-level composition:
- `MainWindow` — Top-level Compose window frame
- `NavigationRail` — Vertical destination selector
- `NavigationHost` — Explicit destination switcher rendering active feature screen
- `NavigationController` — Manages active `DesktopDestination` via StateFlow
- `DesktopDestination` — Sealed interface defining all navigation screens
- `Toolbar` — Application toolbar with breadcrumb title
- `StatusBar` — Persistent footer bar

The App module does NOT create ViewModels. Each feature screen manages its own ViewModel internally via Koin injection.

Navigation is explicit. There is no runtime plugin or Contribution registry. All screen mappings are hardcoded in `NavigationHost`.

---

## core/

Shared Kotlin Multiplatform libraries.

Everything here should compile for:
- Desktop
- Android
- iOS

### core:domain [COMPLETED]

Contains
- Domain Models
- Repository Interfaces
- UseCases
- Business Rules
- Validation

No implementations.

---

### core:http [COMPLETED]

Shared HTTP execution layer.

Contains
- Ktor Client
- Authentication
- Retry
- Cookies
- Timeout
- HTTP execution pipeline

Used by
- Desktop API Studio

Does NOT contain the proxy server.

---

### core:logger [COMPLETED]

Shared logging abstraction.

Examples
- Logger
- LogLevel
- LoggerFactory

---

### core:serialization [COMPLETED]

Shared serialization infrastructure.

Contains
- Json configuration
- CBOR configuration
- Proto serializers
- Shared serializers
- Polymorphic serializers

No business logic.

---

## engine/ [COMPLETED]

Desktop runtime engines.

These are Desktop-only.

Contains
- Netty
- TLS
- Certificate generation
- Traffic modification
- Session recording
- Protocol decoding
- Script execution

---

## testing/ [COMPLETED]

Reusable testing infrastructure.

Contains
- Embedded HTTP server
- Mock APIs
- Test utilities

---

# 🔄 Dependency Flow

```text
apps
   │
   ▼
ui
   │
   ▼
core
   │
   ▼
engine
```

Detailed dependency graph:

```text
                  +----------------------+
                  |    apps:desktop      |
                  +----------+-----------+
                             |
                             ▼
                  +----------------------+
                  |  ui:desktop:app      |
                  +----------+-----------+
                             |
                             ▼
                  +----------------------+
                  | ui:desktop:*         |  (feature modules)
                  +----------+-----------+
                             |
                             ▼
                  +----------------------+
                  |     core:http        |
                  +----------+-----------+
                             |
                             ▼
                  +----------------------+
                  |    core:domain       |
                  +----------------------+
                       ▲           ▲
                       │           │
               core:logger   core:serialization
                             │
                             ▼
                        engine:*
```

Only the App module (`ui:desktop:app`) coordinates navigation.

Feature modules (`workspace`, `traffic`, `inspector`, `apistudio`, `scripting`, `certificate`, `codeEditor`) never depend on each other.

---

# 🧭 Design Rules

## Applications own implementations

Repository interfaces belong to:
```text
:core:domain
```

Repository implementations belong to:
```text
:apps:desktop
```

---

## Engine owns runtime execution

Examples:
- Netty Proxy
- TLS
- MITM
- Session Recording
- Breakpoints
- Traffic Rules

Everything Desktop-only belongs here.

---

## Core owns shared business logic

Everything reusable across all applications belongs here.

No platform APIs.

---

## UI owns presentation

Everything Compose.

Nothing else.

---

## Navigation Rule

The App module (`ui:desktop:app`) is the single coordinator of navigation.

Feature modules expose only their root screen composable:
- `TrafficScreen()`
- `InspectorPanel()`
- `ApiStudioScreen()`
- `WorkspaceLayout()`
- `ScriptingScreen()`
- `CertificateManagerScreen()`
- `KNetCodeEditor()`

Feature modules own:
- ViewModel
- UI
- State
- DI module

Feature modules do NOT own navigation.

---

# 🚫 Modules Removed

The following modules were intentionally removed from earlier proposals:

```text
:core:data
```
Reason: Repository implementations are application-specific.

```text
:core:crypto
```
Reason: MITM certificate generation belongs to `:engine:certificate`.

```text
:core:protocol
```
Reason: Protocol decoding belongs to `:engine:protocol`.

```text
:core:common
```
Reason: Avoid generic "common" modules that become dumping grounds.

```text
:engine:network
```
Reason: Its responsibilities were split into:
- `:engine:proxy` (Desktop Netty proxy)
- `:core:http` (Shared Ktor client)

This separation better reflects their distinct purposes.

```text
:ui:desktop:shell
```
Reason: Replaced by `:ui:desktop:app`. The shell architecture relied on a runtime plugin/Contribution pattern (`ShellContribution`, `NavigationContribution`, etc.) that was removed in KNet v2.0. The App module uses explicit navigation through `DesktopDestination` and `NavigationHost`.

---

# ⏳ Migration Status

## Completed

| Module | Status |
|:---|:---|
| `:engine:*` (all 8 engine modules) | COMPLETED |
| `:core:domain` | COMPLETED |
| `:core:http` | COMPLETED |
| `:core:logger` | COMPLETED |
| `:core:serialization` | COMPLETED |
| `:ui:core` | COMPLETED |
| `:ui:desktop:app` (was `:ui:desktop:shell`) | COMPLETED |
| `:ui:desktop:workspace` | COMPLETED |
| `:ui:desktop:apistudio` | COMPLETED |
| `:ui:desktop:inspector` | COMPLETED |
| `:ui:desktop:traffic` | COMPLETED |
| `:ui:desktop:scripting` | COMPLETED |
| `:ui:desktop:certificate` | COMPLETED |
| `:ui:desktop:codeEditor` (was `:codeEditorUI`) | COMPLETED |
| `:apps:desktop` (was `:desktopApp`) | COMPLETED |
| `:testing:server` | COMPLETED |

## Pending

| Module | Status | Notes |
|:---|:---|:---|
| None | - | All desktop v2.0 modules completed |

## Key Remaining Work

1. **Mobile Companion & Cross-Platform Expansion** — Standardize `:apps:mobile` and `:ui:mobile:*` when mobile companion development begins.
