# `engine/` Feature Module Group

This directory is a **Feature Module Group** containing KNet's runtime execution engines and network proxy services.

> **Note**: This folder is an organizational directory only and does **not** contain a `build.gradle.kts` file. Each child sub-module owns its own `build.gradle.kts`.

---

## 📦 Engine Sub-Modules Directory

```text
  engine/
  ├── certificate/                📦 :engine:certificate
  │   └── Root CA Generation & Dynamic SNI Leaf Certificate Signing
  │
  ├── interceptor/                📦 :engine:interceptor
  │   └── Breakpoint Engine & Coroutine Pause Pipeline
  │
  ├── traffic/                    📦 :engine:traffic
  │   └── Map Local, Map Remote, & Regex Rewrite Rules
  │
  ├── simulator/                  📦 :engine:simulator
  │   └── Bandwidth Throttling, Latency Injection, & Packet Loss
  │
  ├── session/                    📦 :engine:session
  │   └── HAR 1.2 Import/Export & In-Memory Session Buffers
  │
  └── proxy/                      📦 :engine:proxy
      └── Netty MITM Proxy Pipeline, WebSockets, & Protobuf Decoders
```

---

## 📋 Sub-Module Specification & Audit Target Checklist

### 1. `:engine:certificate` (was `:certificateManager`)
- **Gradle Path**: `:engine:certificate`
- **Target Folder**: `engine/certificate`
- **Responsibilities**:
  - BouncyCastle Root CA generation (X.509 v3 certificates).
  - Dynamic SNI Leaf Certificate signing per domain.
  - PKCS12 / JKS KeyStore export & OS Trust Store installation helper.
- **Audit Checklist**:
  - [ ] Verify zero UI dependencies.
  - [ ] Decouple `RootCertGenerator` into interface contract in `:core:domain`.
  - [ ] Ensure thread-safety of SNI certificate cache.

---

### 2. `:engine:interceptor` (was `:interceptor`)
- **Gradle Path**: `:engine:interceptor`
- **Target Folder**: `engine/interceptor`
- **Responsibilities**:
  - Breakpoint rule matching (URL, HTTP Method, Status Code).
  - Coroutine `CompletableDeferred` pause & resume mechanism for live HTTP transactions.
- **Audit Checklist**:
  - [ ] Verify non-blocking coroutine execution.
  - [ ] Audit memory leak potential on paused requests that time out.

---

### 3. `:engine:traffic` (was `:trafficModifier`)
- **Gradle Path**: `:engine:traffic`
- **Target Folder**: `engine/traffic`
- **Responsibilities**:
  - **Map Local**: Intercept matching request and respond with local file system mock.
  - **Map Remote**: Redirect request to alternative host/port destination.
  - **Rewrite Rules**: Regex header, body, or status code replacements.
- **Audit Checklist**:
  - [ ] Pure logic engine — ensure 0 network socket or DB dependencies.
  - [ ] Validate regex compilation performance.

---

### 4. `:engine:simulator` (was `:networkSimulator`)
- **Gradle Path**: `:engine:simulator`
- **Target Folder**: `engine/simulator`
- **Responsibilities**:
  - Artificial network latency injection (e.g. 500ms delay).
  - Per-channel bandwidth throttling (e.g. 3G 400 kbps throttling).
  - Packet loss simulation.
- **Audit Checklist**:
  - [ ] Audit Netty `ChannelHandler` thread pool safety.
  - [ ] Ensure no thread blocking on main UI loop.

---

### 5. `:engine:session` (was `:sessionManager`)
- **Gradle Path**: `:engine:session`
- **Target Folder**: `engine/session`
- **Responsibilities**:
  - In-memory ring buffer for active proxy session transactions.
  - HAR 1.2 specification exporter & importer parser.
- **Audit Checklist**:
  - [ ] Enforce memory limit caps on body buffers to prevent OutOfMemory errors.

---

### 6. `:engine:proxy` (was `:protocolInspector` / Netty proxy core)
- **Gradle Path**: `:engine:proxy`
- **Target Folder**: `engine/proxy`
- **Responsibilities**:
  - Netty MITM SSL Termination Proxy Pipeline.
  - WebSockets frame decoder & inspector.
  - Protobuf binary payload decoder.
- **Audit Checklist**:
  - [ ] Verify channel pipeline handler order.
  - [ ] Ensure graceful Netty `EventLoopGroup` shutdown.
