# KNet Phase 2 Plan: Core Asynchronous Proxy Engine (proxyEngine) [COMPLETED]

**Status**: Completed

This document outlines the detailed design, dependencies, package structures, and implementation steps for Phase 2, which builds the Netty-based asynchronous proxy server capable of plain HTTP proxying and dynamic HTTPS decryption.

---

## 1. Module Registration and Build Infrastructure

### 1.1 settings.gradle.kts
Register the `proxyEngine` module:
```kotlin
include(":desktopApp")
include(":shared")
include(":certificateManager")
include(":proxyEngine")
```

### 1.2 gradle/libs.versions.toml
Ensure the Netty version is configured in the catalog:
```toml
[versions]
netty = "4.1.136.Final"

[libraries]
netty-all = { module = "io.netty:netty-all", version.ref = "netty" }
```

### 1.3 proxyEngine/build.gradle.kts
Configure dependencies:
```kotlin
plugins {
    kotlin("jvm") version "2.4.0"
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":certificateManager"))
    implementation(libs.netty.all)
    
    testImplementation(kotlin("test"))
}
```

---

## 2. Core Data Models in `shared`

The models representing HTTP entities will be defined in the `shared` module under `com.devuloopers.knet.model` (located in `shared/src/commonMain/kotlin` so they are accessible to both the network engine and the UI layer).

### 2.1 HttpRequest
* **Fields**:
  * `val id: String`: Unique UUID identifier.
  * `val method: String`: HTTP method (GET, POST, etc.).
  * `val url: String`: Complete request URL.
  * `val protocol: String`: Protocol version (e.g., HTTP/1.1).
  * `val headers: List<Pair<String, String>>`: Parsed headers.
  * `val body: ByteArray?`: Request body.
  * `val timestamp: Long`: Epoch milliseconds.

### 2.2 HttpResponse
* **Fields**:
  * `val statusCode: Int`: HTTP status code (e.g., 200).
  * `val statusText: String`: HTTP status text (e.g., OK).
  * `val headers: List<Pair<String, String>>`: Parsed response headers.
  * `val body: ByteArray?`: Response body.
  * `val timestamp: Long`: Epoch milliseconds.

---

## 3. Proxy Handler and Engine Design

The proxy server will consist of two primary components inside the `com.devuloopers.knet.engine` package.

### 3.1 KNetProxyServer
An orchestrator that instantiates and runs the Netty server.
* **Fields**:
  * `private val port: Int` (default: 8080)
  * `private val ca: CertificateAuthority`
  * `private val certCache: CertificateCache`
  * `private var bossGroup: EventLoopGroup?`
  * `private var workerGroup: EventLoopGroup?`
* **Methods**:
  * `start()`: Boots the Netty `ServerBootstrap`, configures the socket channel pipeline, and binds to the target port.
  * `stop()`: Shuts down the Netty event loops gracefully.

### 3.2 KNetProxyHandler
Extends Netty's `ChannelInboundHandlerAdapter` to process client requests.
* **HTTP CONNECT Handshake (HTTPS)**:
  * When a `CONNECT` request is received (e.g., `CONNECT github.com:443 HTTP/1.1`), the handler intercepts it and responds immediately with `HTTP/1.1 200 Connection Established`.
  * The handler dynamically generates a leaf certificate matching the requested host (`github.com`) using `CertificateCache` and `CertificateAuthority`.
  * It dynamically alters the Netty pipeline, adding a client-side `SslHandler` initialized with the dynamic leaf certificate and private key.
  * The handler establishes a secure outbound connection (using a Netty Client `Bootstrap`) to the remote server on port 443.
  * Once the outbound connection is complete, a server-side `SslHandler` is added to the outbound pipeline to enable decryption.
* **HTTP Request/Response Relay (Decrypted Traffic)**:
  * Reads plain or decrypted request data, creates an corresponding outbound request, forwards it to the remote server, captures the response, and writes it back to the client.
  * Triggers event logs to record traffic metadata.

---

## 4. Verification Plan

Unit and integration tests inside `proxyEngine/src/test/kotlin` will verify the following flows:
1. **HTTP Proxying**: Run the proxy server locally, execute a plain HTTP GET request using standard Java `HttpClient` routed through KNet proxy port, and assert that the proxy forwards and returns the response correctly.
2. **HTTPS Decryption (MITM)**: Configure a test HTTPS server. Run KNet proxy server, register the test CA certificate, make an HTTPS request through KNet, and assert that the request executes, TLS handshakes succeed, and headers are visible within KNet logs.
3. **Graceful Shutdown**: Start and stop the engine repeatedly on dynamic ports, asserting socket release behavior.
