# Architecture Specification & Refactoring Plan — `:testingServer` (Frozen 10/10 Architecture)

**Module:** `testingServer/` (`:testingServer`)  
**Package Namespace:** `com.devuloopers.knet.testingserver`  
**Platform:** Spring Boot 3 + WebFlux + Kotlin Coroutines  
**Status:** Frozen Final Stable Architecture Specification

---

# 📌 Vision

`:testingServer` is KNet's **Standalone Testing Backend Application**.

It exists exclusively for local testing, integration validation, and debugging of KNet features (`:engine:proxy`, `:ui:desktop:apistudio`, `:ui:desktop:inspector`, `:engine:script`, `:engine:interceptor`).

It provides deterministic HTTP, WebSocket, SSE, and TLS endpoints covering every networking scenario required for local development.

This module is **never shipped with production application builds**.

---

# 🏗 Target Package Structure

```text
testingServer/
├── build.gradle.kts
│
└── src/
    └── main/
        └── kotlin/
            └── com/devuloopers/knet/testingserver/
                │
                ├── TestingServerApplication.kt
                │
                ├── configuration/
                │   ├── RouterConfiguration.kt
                │   ├── JsonConfiguration.kt
                │   └── CorsConfiguration.kt
                │
                ├── model/
                │   ├── TestResponse.kt
                │   └── ErrorResponse.kt
                │
                ├── common/
                │   ├── ResponseFactory.kt
                │   └── RequestUtils.kt
                │
                ├── basic/
                │   ├── GetHandler.kt
                │   ├── PostHandler.kt
                │   ├── PutHandler.kt
                │   ├── PatchHandler.kt
                │   ├── DeleteHandler.kt
                │   └── BasicRouter.kt
                │
                ├── authentication/
                │   ├── AuthenticationHandler.kt
                │   └── AuthenticationRouter.kt
                │
                ├── headers/
                │   ├── HeaderHandler.kt
                │   └── HeaderRouter.kt
                │
                ├── cookies/
                │   ├── CookieHandler.kt
                │   └── CookieRouter.kt
                │
                ├── redirect/
                │   ├── RedirectHandler.kt
                │   └── RedirectRouter.kt
                │
                ├── delay/
                │   ├── DelayHandler.kt
                │   └── DelayRouter.kt
                │
                ├── status/
                │   ├── StatusHandler.kt
                │   └── StatusRouter.kt
                │
                ├── upload/
                │   ├── MultipartUploadHandler.kt
                │   └── UploadRouter.kt
                │
                ├── download/
                │   ├── DownloadHandler.kt
                │   └── DownloadRouter.kt
                │
                ├── streaming/
                │   ├── StreamingHandler.kt
                │   └── StreamingRouter.kt
                │
                ├── websocket/
                │   ├── WebSocketHandler.kt
                │   └── WebSocketRouter.kt
                │
                ├── sse/
                │   ├── SseHandler.kt
                │   └── SseRouter.kt
                │
                ├── compression/
                │   ├── CompressionHandler.kt
                │   └── CompressionRouter.kt
                │
                ├── payload/
                │   ├── LargePayloadHandler.kt
                │   └── PayloadRouter.kt
                │
                ├── tls/
                │   ├── TlsHandler.kt
                │   └── TlsRouter.kt
                │
                └── error/
                    ├── ErrorSimulationHandler.kt
                    └── ErrorRouter.kt
```

---

# 🎯 Component Specifications

## 1. Response DTO (`model/TestResponse.kt`)
```kotlin
data class TestResponse(
    val success: Boolean,
    val status: Int,
    val method: String,
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val query: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap(),
    val body: Any? = null,
    val timestamp: String = java.time.Instant.now().toString()
)
```

## 2. Endpoints Matrix

### Basic HTTP Verbs (`/api/*`)
- `GET /api/get`
- `POST /api/post`
- `PUT /api/put`
- `PATCH /api/patch`
- `DELETE /api/delete`

### Headers & Cookies
- `GET /api/headers` (Echoes all request headers)
- `GET /api/cookies` (Echoes cookies, supports Set-Cookie, HttpOnly, Secure)

### Authentication
- `GET /api/auth/basic`
- `GET /api/auth/bearer`
- `GET /api/auth/apikey/header`
- `GET /api/auth/apikey/query`

### Redirects & Latency & Status
- `GET /api/redirect/{301|302|307|308}`
- `GET /api/delay/{seconds}` (Non-blocking `delay(...)`)
- `GET /api/status/{code}`

### Advanced Networking
- **Upload**: `POST /api/upload/multipart`, `POST /api/upload/binary`
- **Download**: `GET /api/download/file`, `GET /api/download/pdf`, `GET /api/download/image`
- **Streaming**: `GET /api/stream/chunked` (`application/octet-stream`)
- **WebSocket**: `ws://localhost:8080/ws/echo`, `/ws/chat`, `/ws/broadcast`
- **SSE**: `GET /api/events` (`text/event-stream`)
- **Compression**: `/api/compress/gzip`, `/api/compress/brotli`, `/api/compress/deflate`
- **Large Payloads**: `/api/payload/{1kb|10kb|100kb|1mb|10mb}`
- **Error Simulation**: `/api/error/timeout`, `/api/error/malformed-json`, `/api/error/truncated`

---

# 🚫 Dependency Rules

Must depend ONLY on:
- Spring Boot Starter WebFlux (`libs.spring.boot.starter.webflux`)
- Kotlin Coroutines Reactor (`libs.kotlinx.coroutines.reactor`)
- Kotlin Serialization / Jackson

Must NOT depend on:
- `:core`, `:engine`, `:data`, `:storage`, `:ui`, `:apps`

`:testingServer` is 100% independent.
