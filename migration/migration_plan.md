# KNet Architecture Migration Plan

This plan tracks the migration of KNet into the 6-layer architecture specified in [docs/architecture_guide.md](file:///c:/Users/Anant.gupta/IdeaProjects/KNet/docs/architecture_guide.md).

---

## 📌 Master Module Migration Matrix

| Layer | Subproject Path | Target Gradle Module | Source / Responsibility | Status |
| :--- | :--- | :--- | :--- | :---: |
| **Engine** | `engine/certificate/` | `:engine:certificate` | Root CA & Dynamic Leaf Certificate Generation | `[COMPLETED]` |
| **Engine** | `engine/traffic/` | `:engine:traffic` | Map Local, Map Remote, Rewrite Rules | `[COMPLETED]` |
| **Engine** | `engine/interceptor/` | `:engine:interceptor` | Breakpoints & Pipeline Interception | `[COMPLETED]` |
| **Engine** | `engine/simulator/` | `:engine:simulator` | Latency, Throttling & Network Simulation | `[COMPLETED]` |
| **Engine** | `engine/session/` | `:engine:session` | In-memory Session Buffer & History | `[COMPLETED]` |
| **Engine** | `engine/protocol/` | `:engine:protocol` | HTTP/1.x, HTTP/2, WebSocket, gRPC Inspector | `[COMPLETED]` |
| **Engine** | `engine/formatter/` | `:engine:formatter` | JSON, XML, Protobuf, CBOR, Hex Body Formatter | `[COMPLETED]` |
| **Engine** | `engine/script/` | `:engine:script` | GraalJS & Kotlin Script Execution Runtime | `[COMPLETED]` |
| **Engine** | `engine/proxy/` | `:engine:proxy` | Netty Proxy Server & Client Pipeline (From: `networkEngine/`) | `[PENDING]` |
| **Core** | `core/http/` | `:core:http` | KMP HTTP Models, Headers, Status Codes & Client Abstractions | `[PENDING]` |
| **Core** | `core/logger/` | `:core:logger` | Kermit KMP Logger Wrapper | `[PENDING]` |
| **Core** | `core/domain/` | `:core:domain` | Shared KMP Domain Models & DTOs | `[PENDING]` |
| **Core** | `core/pairing/` | `:core:pairing` | KMP Desktop/Mobile Pairing Models & Handshake | `[PENDING]` |
| **Core** | `core/protocol/` | `:core:protocol` | KMP Shared Wire Frame Protocols | `[PENDING]` |
| **Core** | `core/crypto/` | `:core:crypto` | KMP Pure Crypto Utilities & Hashing | `[PENDING]` |
| **Core** | `core/common/` | `:core:common` | KMP Core Common Utilities | `[PENDING]` |
| **Desktop** | `desktop/data/` | `:desktop:data` | Desktop Room SQLite DB, HAR, Payload Cache | `[PENDING]` |
| **Desktop** | `desktop/pairing/` | `:desktop:pairing` | JVM mDNS Pairing Server | `[PENDING]` |
| **Desktop** | `desktop/updater/` | `:desktop:updater` | Desktop Auto-Updater | `[PENDING]` |
| **Mobile** | `mobile/data/` | `:mobile:data` | Mobile Local Preferences & Storage | `[PENDING]` |
| **Mobile** | `mobile/vpn/` | `:mobile:vpn` | Android VpnService / iOS NetworkExtension | `[PENDING]` |
| **Mobile** | `mobile/tunnel/` | `:mobile:tunnel` | Mobile Traffic Tunnel Client | `[PENDING]` |
| **Mobile** | `mobile/pairing/` | `:mobile:pairing` | Mobile QR Code & mDNS Discovery Client | `[PENDING]` |
| **UI** | `ui/core/` | `:ui:core` | Shared Compose Design Tokens & Components | `[PENDING]` |
| **UI** | `ui/apistudio/` | `:ui:apistudio` | API Studio Request Builder & Collection Views | `[PENDING]` |
| **UI** | `ui/inspector/` | `:ui:inspector` | Transaction List & Structure Inspector Views | `[PENDING]` |
| **UI** | `ui/codeeditor/` | `:ui:codeeditor` | Code Editor Subsystem & Syntax Highlighting | `[PENDING]` |
| **UI** | `ui/terminal/` | `:ui:terminal` | Terminal & Log Console Panel | `[PENDING]` |
| **UI** | `ui/settings/` | `:ui:settings` | Settings & Preferences Panel | `[PENDING]` |
| **Apps** | `apps/desktop/` | `:apps:desktop` | JVM Desktop Executable App | `[PENDING]` |
| **Apps** | `apps/mobile/` | `:apps:mobile` | Mobile Companion Application | `[PENDING]` |
| **Testing** | `testing/server/` | `:testing:server` | Mock HTTP/WebSocket Test Server | `[PENDING]` |

---

## 🌳 6-Layer Architecture Tree

```text
knet/
├── apps/                                   ← Executable Applications
│   ├── desktop/                            📦 :apps:desktop
│   └── mobile/                             📦 :apps:mobile
│
├── ui/                                     ← Presentation Layer
│   ├── core/                               📦 :ui:core
│   ├── apistudio/                          📦 :ui:apistudio
│   ├── inspector/                          📦 :ui:inspector
│   ├── codeeditor/                         📦 :ui:codeeditor
│   ├── terminal/                           📦 :ui:terminal
│   └── settings/                           📦 :ui:settings
│
├── engine/                                 ← Traffic Processing Engines
│   ├── proxy/                              📦 :engine:proxy [PENDING]
│   ├── certificate/                        📦 :engine:certificate [COMPLETED]
│   ├── traffic/                            📦 :engine:traffic [COMPLETED]
│   ├── interceptor/                        📦 :engine:interceptor [COMPLETED]
│   ├── simulator/                          📦 :engine:simulator [COMPLETED]
│   ├── session/                            📦 :engine:session [COMPLETED]
│   ├── protocol/                           📦 :engine:protocol [COMPLETED]
│   ├── formatter/                          📦 :engine:formatter [COMPLETED]
│   └── script/                             📦 :engine:script [COMPLETED]
│
├── core/                                   ← Pure KMP Shared Logic
│   ├── http/                               📦 :core:http (KMP)
│   ├── domain/                             📦 :core:domain (KMP)
│   ├── logger/                             📦 :core:logger (KMP)
│   ├── pairing/                            📦 :core:pairing (KMP)
│   ├── protocol/                           📦 :core:protocol (KMP)
│   ├── crypto/                             📦 :core:crypto (KMP)
│   └── common/                             📦 :core:common (KMP)
│
├── desktop/                                ← Desktop Infrastructure (JVM Only)
│   ├── data/                               📦 :desktop:data
│   ├── pairing/                            📦 :desktop:pairing
│   └── updater/                            📦 :desktop:updater
│
├── mobile/                                 ← Mobile Infrastructure (Platform Specific)
│   ├── data/                               📦 :mobile:data
│   ├── vpn/                                📦 :mobile:vpn
│   ├── tunnel/                             📦 :mobile:tunnel
│   └── pairing/                            📦 :mobile:pairing
│
└── testing/                                ← Test Infrastructure
    └── server/                             📦 :testing:server
```
