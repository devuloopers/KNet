# 🌐 KNet (Kotlin Network Inspector)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.11.1-purple.svg?logo=jetpackcompose)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Netty](https://img.shields.io/badge/Netty-4.1.136.Final-blue.svg?logo=apache)](https://netty.io)
[![Room](https://img.shields.io/badge/Room-2.8.4-green.svg?logo=android)](https://developer.android.com/training/data-storage/room)
[![Koin](https://img.shields.io/badge/Koin-4.0.2-blue.svg)](https://insert-koin.io)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)]()

**KNet** is a high-performance **Kotlin Multiplatform (KMP)** desktop & mobile HTTP/HTTPS network traffic proxy inspector designed for developers, QA engineers, and security researchers. Comparable to industry tools like Charles Proxy, Proxyman, or Fiddler, KNet enables intercepting, decrypting, inspecting, rewriting, and analyzing live network payloads in real time.

Engineered on **Netty 4.1.136**, **Compose Multiplatform 1.11.1**, **Kotlin 2.4.0**, and **Clean Architecture**, KNet provides non-blocking packet routing, SSL/TLS Man-In-The-Middle (MITM) decryption, precise 5-phase socket timing diagnostics, and multi-format payload format inspectors.

---

## 📋 Table of Contents
1. [Key Features & Capabilities](#-key-features--capabilities)
2. [End-to-End System Architecture](#-end-to-end-system-architecture)
3. [Netty Pipeline & MITM Decryption Sequence](#-netty-pipeline--mitm-decryption-sequence)
4. [5-Phase Socket Diagnostics](#-5-phase-socket-diagnostics)
5. [Complete Module Architecture](#-complete-module-architecture)
6. [Network Content-Type Support Matrix](#-network-content-type-support-matrix)
7. [UI Widget Design System](#-ui-widget-design-system)
8. [Database Persistence & Migration](#-database-persistence--migration)
9. [Build & Verification Guidelines](#-build--verification-guidelines)
10. [Directory Tree Structure](#-directory-tree-structure)

---

## 🌟 Key Features & Capabilities

### 🔍 Real-Time Live Traffic Interception
- **Non-Blocking Socket Routing**: Intercepts HTTP/1.x, HTTP/2, and WebSocket traffic asynchronously using Netty NIO event loops.
- **Descending Sequential Live Feed**: Transactions render instantly at the top of the feed with sequential tracking IDs (`#N` down to `#1`).
- **Cursor-Stable Reusable Search**: `WidgetSearchBar` powered by Compose `decorationBox` layout prevents cursor jumping and placeholder overlapping.

### 🔐 SSL/TLS MITM Decryption & Certificate Engine
- **On-the-Fly Leaf Certificate Signing**: Generates X.509 v3 leaf certificates signed by KNet's Root CA matching client SNI (Server Name Indication) on demand.
- **BouncyCastle Crypto Core**: Uses BouncyCastle 1.85 for PKCS12 (.p12) keystore generation and RSA keypair creation.
- **OS System Truststore Installer**: Automated installation scripts for Windows, macOS, and Linux system truststores.

### ⏱️ 5-Phase Socket Diagnostic Timings
KNet captures **100% real, non-calculated socket timing metrics** directly from Netty pipeline channel futures without guessed multipliers:

$$\text{Total Duration} = \text{DNS} + \text{TCP Handshake} + \text{TLS Handshake} + \text{TTFB} + \text{Download}$$

### 🎨 Modular Skia Desktop UI
- **Hardware Acceleration**: Skia-backed rendering using Compose Multiplatform Desktop and Skiko.
- **`CodeViewerWidget`**: Integrated multi-line code viewer with line numbers, text search filtering, and strategy-based code folding (JSON, HTML, XML).
- **DRY Code Folding Engine**: Shared `CollapsedBadge` and `TagMarkupHighlighter` components powering syntax highlighters without code duplication.

### 💾 Offline Session Persistence
- **Room Database 2.8.4**: Persistent SQLite storage with compile-time query verification (KSP & SQLite).
- **Automated Schema Migrations**: Versioned database migrations (`MIGRATION_1_2`) ensuring disk-saved sessions upgrade seamlessly without data loss.

---

## 🔄 End-to-End System Architecture

The following diagram illustrates how raw network packets pass through the Netty Proxy engine, transition into the domain layer, and reactively update the Compose UI:

```
+-----------------------------------------------------------------------------------+
|                                  CLIENT DEVICE                                    |
|   (Mobile App / Browser / HTTP Client configured to route through localhost:8080)   |
+--------------------------------─────────┬-----------------------------------------+
                                          |
                                 [ HTTP / HTTPS CONNECT ]
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                             KNET PROXY ENGINE (Netty)                             |
|  - KNetProxyInitializer                                                           |
|  - SslHandler (MITM TLS Decryption via BouncyCastle CertificateManager)           |
|  - KNetProxyHandler (Collects real DNS, TCP, TLS, TTFB, & Download timing metrics)|
+-----------------------------------------┬-----------------------------------------+
                                          |
                               [ Intercepted HttpTransaction ]
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                                 SESSION MANAGER                                   |
|  - Buffers live traffic in atomic coroutine Flow                                  |
|  - Passes payload to BodyFormatterRegistry (2-stage priority dispatcher)          |
+-----------------------------------------┬-----------------------------------------+
                                          |
                     +--------------------+--------------------+
                     |                                         |
                     v                                         v
+------------------------------------------+ +--------------------------------------+
|            STORAGE MODULE                | |            DOMAIN MODULE             |
| - Room Database (version 2)              | | - GetLiveTrafficUseCase              |
| - SqliteBundled / HttpTransactionEntity  | | - GetTransactionDetailUseCase        |
+------------------------------------------+ +----------------──┬-------------------+
                                                                |
                                                                v
                                             +--------------------------------------+
                                             |            SHARED UI MODULE          |
                                             | - LiveTrafficViewModel (StateFlow)   |
                                             | - TrafficFeedWidget                  |
                                             | - CodeViewerWidget / TimingsWidget   |
                                             +--------------------------------------+
```

---

## 🔒 Netty Pipeline & MITM Decryption Sequence

1. **Client Handshake**: Client initiates an HTTP `CONNECT target.com:443` request to KNet's proxy port (default: `8080`).
2. **SNI Extraction**: Netty extracts the target domain name from the Client Hello TLS extension.
3. **Dynamic Certificate Signing**: `CertificateManager` uses BouncyCastle 1.85 to issue a temporary X.509 v3 leaf certificate signed by KNet's Root CA.
4. **MITM SSL Handshake**: Netty completes a TLS handshake with the client using the leaf certificate, and opens a separate secure TLS channel to the destination server.
5. **Decryption & Forwarding**: Plaintext HTTP/1.x or HTTP/2 payloads are logged into `HttpTransaction` models and forwarded to the destination.

---

## ⏱️ 5-Phase Socket Diagnostics

| Phase | Variable Name | Measurement Strategy |
| :--- | :--- | :--- |
| **DNS Lookup** | `timingDnsMs` | Real pre-connection IP resolution timing measured via `InetAddress.getByName(targetHost)`. |
| **TCP Connect** | `timingTcpMs` | Socket channel connection establishment duration to target IP. |
| **TLS Handshake** | `timingTlsMs` | Real SSL/TLS negotiation duration captured via `sslHandler.handshakeFuture()`. |
| **TTFB** | `timingTtfbMs` | Time To First Byte measured from request flush to initial response header byte. |
| **Download** | `timingDownloadMs` | Payload transfer duration from first response byte to last channel read completion. |

---

## 🏗 Complete Module Architecture

KNet is strictly architected into 11 decoupled Gradle modules following **Clean Architecture** principles (`UI -> ViewModel -> UseCases -> Repository -> Storage/Network`):

| Module | Scope / Description | Key Technologies |
| :--- | :--- | :--- |
| **`:desktopApp`** | Application launcher entry point, JVM window lifecycle, system tray integration, and native desktop window configuration. | JVM, Compose Desktop |
| **`:sharedUI`** | Declarative Compose Multiplatform UI components, theme design tokens (`KNetColors`), reusable widgets (`WidgetSearchBar`, `CodeViewerWidget`, `TimingsWidget`), and syntax highlighters. | Compose Multiplatform 1.11.1, Material 3, Koin 4.0.2 |
| **`:domain`** | Pure Kotlin enterprise business logic, domain models (`HttpTransaction`, `HttpTimings`), repository interfaces, and isolated UseCases (`GetLiveTrafficUseCase`, `GetTransactionDetailUseCase`). | Pure Kotlin 2.4.0, Coroutines, Flow |
| **`:data`** | Data layer orchestrator implementing domain repositories (`KNetCoreRepositoryImpl`), mediating between local Room database persistence and live proxy engine streams. | Kotlin Coroutines, Flow |
| **`:storage`** | Offline persistent storage powered by Room Database (version 2). Manages schema migrations (`MIGRATION_1_2`) and SQL entity mapping. | AndroidX Room 2.8.4, KSP 2.3.10, SQLite |
| **`:proxyEngine`** | Asynchronous Netty HTTP/HTTPS proxy engine handling socket connections, SSL/TLS MITM decryption, and 100% accurate 5-phase socket timing measurements. | Netty 4.1.136.Final, OpenSSL |
| **`:certificateManager`** | Dynamic Root CA certificate generation, RSA keypair creation, PKCS12 truststore management, and OS system truststore installer scripts (Windows, macOS, Linux). | BouncyCastle 1.85, Java KeyStore |
| **`:sessionManager`** | In-memory session tracking, live flow buffering, traffic filtering, and transaction state management. | Kotlin Flow, Atomic State |
| **`:bodyFormatter`** | 2-stage priority dispatcher resolving and pretty-printing raw HTTP request/response payload strings into structured, readable formats. | Pure Kotlin |
| **`:interceptor`** | Rule-based traffic modification engine for breakpoint inspection, request/response header rewrite, and mock response injections. | Kotlin Coroutines |
| **`:logger`** | Centralized structured logging pipeline supporting console output and persistent file logging via Kermit. | Touchlab Kermit 2.1.0 |

---

## 📊 Network Content-Type Support Matrix

| Content-Type | MIME / Format Header | Pretty-Print Formatter | Syntax Highlighter | Status |
| :--- | :--- | :--- | :--- | :---: |
| **JSON** | `application/json`, `application/json+*` | `JsonBodyFormatter` | `JsonLanguageHighlighter` | `[DONE]` |
| **HTML** | `text/html` | `HtmlBodyFormatter` | `HtmlLanguageHighlighter` | `[DONE]` |
| **XML** | `application/xml`, `text/xml`, `application/soap+xml`, `image/svg+xml` | `XmlBodyFormatter` | `XmlLanguageHighlighter` | `[DONE]` |
| **Form Data** | `application/x-www-form-urlencoded`, `multipart/form-data` | `FormDataBodyFormatter` | Form Key-Value Grid | `[DONE]` |
| **Server-Sent Events** | `text/event-stream` | `SseStreamFormatter` | SSE Frame Streaming | `[DONE]` |
| **WebChannel** | `application/json+webchannel` | `WebChannelStreamFormatter` | WebChannel Frame Streaming | `[DONE]` |
| **Images** | `image/png`, `image/jpeg`, `image/webp`, `image/gif` | `ImageBodyFormatter` | Image Preview Canvas | `[DONE]` |
| **Protobuf** | `application/x-protobuf`, `application/grpc` | `ProtobufBinaryFormatter` | Proto Frame Viewer | `[DONE]` |
| **Plain Text** | `text/plain` | `PlainTextBodyFormatter` | `PlainTextLanguageHighlighter` | `[DONE]` |
| **JavaScript** | `application/javascript`, `text/javascript` | Uses `PlainTextBodyFormatter` | `PlainTextLanguageHighlighter` | `[PENDING]` |
| **CSS** | `text/css` | Uses `PlainTextBodyFormatter` | `PlainTextLanguageHighlighter` | `[PENDING]` |
| **CBOR** | `application/cbor` | Pending `CborBodyFormatter` | Pending `CborViewer` | `[PENDING]` |
| **MessagePack** | `application/x-msgpack` | Pending `MessagePackBodyFormatter` | Pending `MessagePackViewer` | `[PENDING]` |
| **gRPC-Web** | `application/grpc-web`, `application/grpc-web+proto` | Pending `GrpcWebBodyFormatter` | Pending `GrpcWebViewer` | `[PENDING]` |

---

## 🎨 UI Widget Design System

KNet provides pixel-perfect reusable desktop UI widgets:

- **`WidgetSearchBar`**: A custom search bar built with Compose `decorationBox` layout that ensures text alignment, placeholder visibility, and cursor stability during fast typing.
- **`CodeViewerWidget`**: Strategy-driven multi-line text view featuring line numbering, search highlight overlays, and JetBrains-style code folding (`JsonLanguageHighlighter`, `HtmlLanguageHighlighter`, `XmlLanguageHighlighter`).
- **`TimingsWidget`**: Color-coded waterfall diagnostic bar illustrating connection setup phases (`DNS`, `TCP`, `TLS`, `TTFB`, `Download`).
- **`TransactionOverviewWidget`**: Concise inspector panel displaying HTTP headers, query parameters, connection metadata, and response status badges.

---

## 💾 Database Persistence & Migration

KNet uses **AndroidX Room Database 2.8.4** for offline storage of network capture sessions:

- **Entity Model**: `HttpTransactionEntity` stores transaction metadata, headers, request/response body references, and socket timings.
- **Schema Migration (`MIGRATION_1_2`)**: Added timing columns (`timingDnsMs`, `timingTcpMs`, `timingTlsMs`, `timingTtfbMs`, `timingDownloadMs`) to existing databases via SQL `ALTER TABLE` scripts without data loss:
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

## 🛠 Build & Verification Guidelines

> [!IMPORTANT]
> **Developer Directive**: Do **NOT** launch the desktop application directly (`:desktopApp:run`) during automated agent or CI sessions. Always compile and execute unit test suites.

### Recommended Verification Command
```bash
./gradlew :domain:compileKotlin :sharedUI:compileKotlinJvm :bodyFormatter:test :sharedUI:jvmTest
```

### Full Workspace Build Command
```bash
./gradlew build -x :desktopApp:run
```

---

## 📁 Directory Tree Structure

```
KNet/
├── .agents/
│   ├── AGENTS.md                   # Custom project rules & constraints
│   └── AGENTS_ARCHITECTURE_GUIDE.md # Developer architecture blueprint
├── bodyFormatter/                 # 2-stage payload formatters (JSON, HTML, XML, etc.)
├── certificateManager/            # BouncyCastle MITM Root CA & Truststore installers
├── data/                          # Data repository implementations
├── desktopApp/                    # Desktop launcher & main window entry point
├── docs/                          # Architecture specs & feature strategy docs
├── domain/                        # Pure Kotlin business models & UseCases
├── gradle/
│   └── libs.versions.toml         # Centralized dependency catalog
├── interceptor/                   # Traffic modification & header rewrite engine
├── logger/                        # Kermit structured logging pipeline
├── proxyEngine/                   # Netty NIO proxy engine & timing collectors
├── sessionManager/                # Live flow buffering & transaction mapping
├── sharedUI/                      # Compose Multiplatform UI, widgets, & highlighters
├── storage/                       # Room Database v2 persistent storage
├── README.md                      # Project documentation
└── settings.gradle.kts            # Gradle multi-module settings
```