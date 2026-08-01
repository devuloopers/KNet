# Implementation Plan & Audit — `:core:http`

**Module:** `:core:http` (`core/http/`)  
**Package Namespace:** `com.devuloopers.knet.core.http`  
**Platform:** Kotlin Multiplatform (Desktop, Android, iOS, CLI)  
**Status:** Approved for Enhancement

---

# 📌 Vision

`:core:http` is KNet's shared HTTP execution library.

It provides a platform-independent API for executing HTTP requests while hiding the underlying HTTP engine (currently Ktor).

The module is responsible for:

- HTTP request execution
- Authentication
- Cookie management
- Retry policy
- Timeouts
- Proxy routing strategy
- Request/response metrics
- Cancellation
- Future interceptor pipeline

This module is **NOT** responsible for:

- Netty proxy server
- MITM
- TLS interception
- API Studio UI
- Desktop-only networking

---

# 🏗 Module Responsibilities

The module provides:

1. Shared HTTP execution
2. Authentication handling
3. Request body encoding
4. Cookie persistence abstraction
5. Retry & timeout configuration
6. Proxy routing decisions
7. Execution metrics
8. Future request interceptor pipeline

---

# 📂 Recommended Package Structure

```text
core/
└── http/
    ├── build.gradle.kts
    │
    └── src/
        ├── commonMain/
        │   └── kotlin/
        │       └── com/devuloopers/knet/core/http/
        │
        │           ├── client/
        │           │     KNetApiClient.kt
        │           │
        │           ├── config/
        │           │     HttpClientConfiguration.kt
        │           │
        │           ├── execution/
        │           │     HttpExecutor.kt
        │           │
        │           ├── interceptor/
        │           │     HttpInterceptor.kt
        │           │
        │           ├── cookie/
        │           │     CookieStore.kt
        │           │     MemoryCookieStore.kt
        │           │
        │           ├── routing/
        │           │     ProxyRoutingStrategy.kt
        │           │     DefaultProxyRoutingStrategy.kt
        │           │
        │           ├── model/
        │           │     ApiExecutionResult.kt
        │           │     HttpMetrics.kt
        │           │     RequestBodyType.kt
        │           │
        │           └── util/
        │
        └── commonTest/
```

---

# 🔍 Current Audit

## Existing Components

### `client/KNetApiClient.kt`
- Ktor Client (CIO)
- Verbs: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`
- Body modes: `JSON`, `XML`, `Multipart`, `GraphQL`, `Raw Text`, `Form URL Encoded`
- Auth: `None`, `Basic`, `Bearer`, `API Key`
- Proxy routing: Dynamic proxy with automatic Direct fallback

### `ApiExecutionResult`
- status code, status text, response headers, response body, latency, response size, success, error, metrics

### `RequestBodyType`
- `NONE`, `JSON`, `XML`, `RAW`, `GRAPHQL`, `FORM_URL_ENCODED`, `MULTIPART`

### `ProxyRoutingStrategy`
- Proxy selection, Direct fallback

---

# 🚀 Proposed Enhancements

## 1. Full KMP Compliance
Replace `java.io.Closeable` with `AutoCloseable`. No JVM-only APIs should remain.

## 2. Introduce HttpExecutor
```kotlin
interface HttpExecutor {
    suspend fun execute(request: SavedApiRequest): ApiExecutionResult
}
```
`KNetApiClient` becomes the default implementation.

## 3. Http Client Configuration
```kotlin
data class HttpClientConfiguration(
    val timeoutMillis: Long = 30_000L,
    val retryCount: Int = 3,
    val followRedirects: Boolean = true,
    val verifySsl: Boolean = true,
    val useCookies: Boolean = true
)
```

## 4. Retry Support
Install `HttpRequestRetry` configured via `HttpClientConfiguration`.

## 5. Cookie Support
Install `HttpCookies` with `CookieStore` interface and `MemoryCookieStore` default implementation.

## 6. Execution Metrics
Introduce `HttpMetrics` (`totalTimeMs`, `dnsTimeMs`, `tcpTimeMs`, `tlsTimeMs`, `ttfbTimeMs`, `downloadTimeMs`) embedded inside `ApiExecutionResult`.

## 7. Interceptor Pipeline
Introduce `interface HttpInterceptor` for request/response interception.

## 8. Coroutine Cancellation
Ensure execution fully respects `Job.cancel()`.

---

# 🚫 Explicit Module Boundaries

Depends on:
- `:core:domain`
- `:core:logger`

Must NOT depend on:
- Compose, Netty, Engine, Desktop APIs, Android APIs, SQL, Room, File APIs

---

# 🧪 Test Architecture (`commonTest/`)

- `KNetApiClientTest`: All HTTP verbs
- `AuthenticationTest`: Auth strategies
- `RequestBodyEncodingTest`: Payload encoding
- `RetryTest`: Retry count & exhaustion
- `CookieStoreTest`: Cookie storage
- `ProxyRoutingTest`: Routing & fallback
- `MetricsTest`: Execution metrics
- `CancellationTest`: Coroutine cancellation
- `ConfigurationTest`: Timeout & configuration
- `MigrationRegressionTest`: API stability
