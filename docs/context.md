# KNet Project Context

## 🌐 What is KNet?

**KNet (Kotlin Network Inspector)** is a high-performance Kotlin Multiplatform (KMP) desktop & mobile HTTP/HTTPS network traffic proxy inspector — comparable to Charles Proxy, Fiddler, or Proxyman.

---

## 🔑 Key Technologies Stack

| Category | Technology |
|----------|------------|
| **Language** | Kotlin 2.4.0 |
| **Multiplatform** | Compose Multiplatform 1.11.1 |
| **Network Core** | Netty 4.1.136.Final (NIO event loops) |
| **Crypto/SSL** | BouncyCastle 1.85, OpenSSL |
| **Database** | Room Database 2.8.4 (SQLite), KSP 2.3.10 |
| **DI Container** | Koin 4.0.2 |
| **Logging** | Kermit 2.1.0 |

---

## 🏗️ Module Architecture (Clean Architecture)

KNet is architected into strictly decoupled Gradle modules following Clean Architecture principles:

```
UI → ViewModel → UseCase → Repository → Storage / Network
```

| Module | Description |
|--------|-------------|
| `:apps:desktop` | Desktop launcher entry point, JVM window lifecycle, Compose Multiplatform Desktop |
| `:domain` | Pure Kotlin business logic — models (`HttpTransaction`, `HttpTimings`), repository interfaces, isolated UseCases |
| `:data` | Data layer implementing domain repositories with Room DB + live proxy engine streams |
| `:storage` | Offline persistent storage via Room Database v2 (schema migrations included) |
| `:proxyEngine` | Asynchronous Netty HTTP/HTTPS proxy engine, MITM TLS decryption, 5-phase socket timing |
| `:certificateManager` | Dynamic Root CA generation, RSA keypair creation, PKCS12 truststore management |
| `:sessionManager` | In-memory session tracking, live flow buffering, traffic filtering |
| `:bodyFormatter` | 2-stage priority dispatcher for raw HTTP payload pretty-printing (JSON, HTML, XML, etc.) |
| `:interceptor` | Rule-based traffic modification — breakpoints, header rewrite, mock responses |

---

## 🔍 Core Capabilities

### 🔐 SSL/TLS MITM Decryption
- On-the-fly leaf certificate signing via BouncyCastle matching client SNI
- OS system truststore installation scripts (Windows, macOS, Linux)

### ⏱️ 5-Phase Socket Diagnostics
Real, non-calculated timing from Netty channel futures:
```
Total = DNS + TCP Handshake + TLS Handshake + TTFB + Download
```

### 📦 Network Content-Type Support Matrix
13+ content types with dedicated formatters & syntax highlighters:
- **JSON/HTML/XML** — strategy-driven pretty-printing with code folding
- **Form Data, SSE, WebChannel** — structured stream formatting
- **Images** — Canvas preview rendering
- **Protobuf / CBOR / MessagePack / gRPC-Web** — binary decoding & tree visualization

### 💾 Offline Persistence
- Room Database v2 with automated schema migrations (`MIGRATION_1_2`)
- SQL migration scripts preserving existing data while adding timing columns

---

## 📁 Directory Structure Overview

```
KNet/
├── apps/desktop/              # Desktop launcher & composition root
├── core/domain/               # Pure Kotlin business logic (models, usecases)
├── core/http/                 # HTTP protocol domain layer
├── data/                      # Repository implementations
├── engine/                    # Runtime engines (certificate, interceptor, traffic, simulator, session, proxy)
├── storage/                   # Room Database v2 + migration schemas
├── ui/core/                   # Shared Compose UI widgets & highlighters
├── ui/desktop/                # Desktop-specific features (apistudio, certificate, codeEditor, inspector, scripting, traffic, workspace)
├── interceptor/               # Traffic modification engine
├── logger/                    # Kermit structured logging pipeline
├── proxyEngine/               # Netty NIO proxy & timing collectors
├── sessionManager/            # Live flow buffering & transaction mapping
├── testingServer/             # Test server suite
└── docs/                      # implementation_plan.md (Phase 1: IN PROGRESS)
```

---

## 📋 Development Status

| Phase | Status | Notes |
|-------|--------|-------|
| **Phase 0** — `:ui:core` & `:ui:desktop:app` Greenfield Rewrite | ✅ COMPLETED | Vision, design language, primitive/composite components, desktop framework |
| **Phase 1** — Feature Modules Rewrite | 🔄 IN PROGRESS | Inspector, apistudio, workspace, scripting, certificate, codeEditor modules pending |

---

## 📝 Build Commands

```bash
# Recommended verification (compile + test only)
./gradlew compileKotlinJvm jvmTest

# Full workspace build (no UI launch)
./gradlew build -x :apps:desktop:run
```

---

## 🔧 Technical Notes

- **Gradle Multi-Module**: Strict boundaries with no cross-module dependencies between pure logic layers
- **Clean Architecture**: `UI → ViewModel → UseCase → Repository → Storage / Network` flow enforced at compile time
- **Netty NIO**: Non-blocking socket routing for HTTP/1.x, HTTP/2, and WebSocket traffic
- **BouncyCastle 1.85**: Crypto core for PKCS12 (.p12) keystore generation and RSA keypair creation
- **Compose Multiplatform Desktop**: Hardware-accelerated Skia-based rendering with Compose UI framework
- **KSP (Kotlin Scripting Plugin)**: Compile-time SQL query verification for Room database entities

---

## 📊 Network Content-Type Support Matrix Summary

| Content-Type | MIME Header | Formatter | Highlighter |
|-------------|-------------|-----------|-------------|
| JSON | `application/json` | JsonBodyFormatter | JsonLanguageHighlighter |
| HTML | `text/html` | HtmlBodyFormatter | HtmlLanguageHighlighter |
| XML | `application/xml`, `text/xml` | XmlBodyFormatter | XmlLanguageHighlighter |
| Form Data | `application/x-www-form-urlencoded` | FormDataBodyFormatter | Form Key-Value Grid |
| SSE | `text/event-stream` | SseStreamFormatter | SSE Frame Streaming |
| WebChannel | `application/json+webchannel` | WebChannelStreamFormatter | WebChannel Frame Streaming |
| Images | `image/png`, `image/jpeg`, etc. | ImageBodyFormatter | Image Preview Canvas |
| Protobuf | `application/x-protobuf` | ProtobufBinaryFormatter | Decoded JSON Tree |
| CBOR | `application/cbor` | CborBodyFormatter | Json/Code Viewer |
| MessagePack | `application/x-msgpack` | MessagePackBodyFormatter | Json/Code Viewer |
| gRPC-Web | `application/grpc-web*` | GrpcWebBodyFormatter | Decoded JSON Tree |
| Plain Text | `text/plain` |PlainTextBodyFormatter |PlainTextLanguageHighlighter |
| JavaScript | `application/javascript`, `text/javascript` | JsBodyFormatter | JsLanguageHighlighter |
| CSS | `text/css` | CssBodyFormatter | CssLanguageHighlighter |

---

## 🎨 UI Widget Design System

- **WidgetSearchBar**: Custom search bar with Compose `decorationBox` layout for cursor stability and placeholder visibility
- **CodeViewerWidget**: Strategy-driven multi-line text view with line numbering, search highlighting, and JetBrains-style code folding
- **TimingsWidget**: Color-coded waterfall diagnostic bar (DNS, TCP, TLS, TTFB, Download)
- **TransactionOverviewWidget**: Inspector panel showing HTTP headers, query parameters, connection metadata, status badges

---

## 💾 Database Persistence & Migration

**Entity Model**: `HttpTransactionEntity` storing transaction metadata, headers, request/response body references, and socket timings.

**Schema Migration (MIGRATION_1_2)**:
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE HttpTransactionEntity ADD COLUMN timingDnsMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE HttpTransactionEntity ADD COLUMN timingTcpMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE HttpTransactionEntity ADD COLUMN timingTlsMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE HttpTransactionEntity ADD COLUMN timingTtfbMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE HttpTransactionEntity ADD COLUMN timingDownloadMs INTEGER NOT NULL DEFAULT 0")
    }
}
```

---

## 🛠 Developer Guidelines

> **[!IMPORTANT]** Do **NOT** launch the desktop application directly (`:apps:desktop:run`) during automated agent or CI sessions. Always compile and execute unit test suites.

**Recommended Verification Command**:
```bash
./gradlew compileKotlinJvm jvmTest
```

**Full Workspace Build Command**:
```bash
./gradlew build -x :apps:desktop:run
```

---

## 📁 Directory Tree Structure (Detailed)

```text
KNet/
├── .agents/                    # Custom project rules & constraints
├── apps/
│   └── desktop/                # Desktop launcher & composition root
├── data/                       # Data repository implementations
├── docs/
│   ├── implementation_plan.md  # Architecture specs & feature strategy docs
│   └── context.md              # Project context (this file)
├── domain/                     # Pure Kotlin business models & usecases
├── gradle/
│   └── libs.versions.toml      # Centralized dependency catalog
├── interceptor/                # Traffic modification & header rewrite engine
├── logger/                     # Kermit structured logging pipeline
├── proxyEngine/                # Netty NIO proxy engine & timing collectors
├── settings.gradle.kts         # Gradle multi-module settings
├── sessionManager/             # Live flow buffering & transaction management
├── storage/                    # Room Database v2 persistent storage
│   ├── schemas/
│   │   ├── com.devuloopers.knet.storage.database.KNetDatabase/
│   │   └── com.devuloopers.knet.storage.KNetDatabase/
│   ├── build/
│   ├── src/
│   └── build.gradle.kts
├── ui/
│   ├── core/                   # Shared Compose UI, widgets, & highlighters
│   └── desktop/                # Desktop-specific features
│       ├── apistudio/          # API Studio module
│       ├── app/                # Desktop application framework
│       ├── certificate/        # Certificate management UI
│       ├── codeEditor/         # Code editor widget
│       ├── http/               # HTTP inspection UI
│       ├── inspector/          # Traffic inspector UI
│       ├── scripting/          # Scripting module
│       ├── traffic/            # Traffic display & management UI
│       └── workspace/          # Developer workspace UI
├── README.md                   # Project documentation
└── build.gradle.kts            # Root Gradle configuration
```

---

## 📋 Audit Targets (Engine Modules)

### `:engine:certificate`
- [ ] Verify zero UI dependencies
- [ ] Decouple `RootCertGenerator` into interface contract in `:core:domain`
- [ ] Ensure thread-safety of SNI certificate cache

### `:engine:interceptor`
- [ ] Verify non-blocking coroutine execution
- [ ] Audit memory leak potential on paused requests that timeout

### `:engine:traffic`
- [ ] Pure logic engine — ensure 0 network socket or DB dependencies
- [ ] Validate regex compilation performance

### `:engine:simulator`
- [ ] Audit Netty `ChannelHandler` thread pool safety
- [ ] Ensure no thread blocking on main UI loop

### `:engine:session`
- [ ] Enforce memory limit caps on body buffers to prevent OOM errors

### `:engine:proxy`
- [ ] Verify channel pipeline handler order
- [ ] Ensure graceful Netty `EventLoopGroup` shutdown
