# KNet Technology Stack Specification

This document details the selected technology stack for KNet, specifying library coordinates and versions updated to their latest stable releases, with Kotlin pinned to version 2.4.0.

---

## 1. Runtime and Language

* **Language**: Kotlin 2.4.0
  * **Rationale**: Offers modern language features such as Coroutines, extension functions, pattern matching, and type safety, facilitating clean code patterns and high developer velocity.
* **Runtime**: Java Virtual Machine (JVM) (JDK 17 or higher)
  * **Rationale**: Required for high-performance enterprise networking (Netty) and dynamic cryptography libraries. Ensures cross-platform compatibility across Windows, macOS, and Linux.

---

## 2. Core Network Proxy Engine

* **Library**: Netty (io.netty:netty-all:4.1.136.Final)
  * **Rationale**: Netty is the industry-standard asynchronous, event-driven network application framework. 
  * **Capabilities Covered**:
    * Non-blocking socket I/O (NIO/Epoll/Kqueue).
    * Built-in codecs for HTTP/1.x, HTTP/2 (netty-codec-http, netty-codec-http2).
    * WebSocket frame handling (netty-codec-http WebSocket extensions).
    * SSL/TLS tunneling (CONNECT request handler) and pipeline dynamic modification.

---

## 3. Cryptography and MITM Certificate Authority

* **Library**: BouncyCastle (org.bouncycastle:bcprov-jdk18on:1.85 & org.bouncycastle:bcpkix-jdk18on:1.85)
  * **Rationale**: The most comprehensive and widely trusted cryptographic API provider on the JVM.
  * **Capabilities Covered**:
    * Generation of Root Certificate Authority (CA) certificates and RSA/ECDSA key pairs.
    * On-the-fly generation of X.509 v3 leaf certificates signed by the Root CA matching client SNI (Server Name Indication).
    * Exporting CA keys and certs in PKCS12 (.p12) or PEM (.pem) formats.

---

## 4. User Interface Layer

* **Framework**: Compose Multiplatform Desktop (org.jetbrains.compose:1.11.1)
  * **Rationale**: Jetpack Compose ported to JVM Desktop. Powered by Skia (Skiko) for hardware-accelerated rendering.
  * **Capabilities Covered**:
    * Dynamic, reactive UI state mapping.
    * Deep integration with Coroutines for background tasks.
    * Modern UI widgets (split-panes, scrolling lists, custom layout grids) for visualizing request lists and raw byte viewers.

---

## 5. Persistence and Workspace Storage

* **Library**: Android Room (androidx.room:room-runtime:2.8.4 or androidx.room3:room3-runtime:3.0.0 & compiler)
  * **Rationale**: Room supports Kotlin Multiplatform (including JVM target) natively. It abstracts SQLite, providing type-safe compile-time query verification.
  * **Capabilities Covered**:
    * Saving capture sessions to disk.
    * Storing workspace preferences, rewrite rules, map local files, and application states.
    * Flow support for reactive database queries (UI updates automatically when database entries change).

---

## 6. Dependency Injection

* **Framework**: Koin (io.insert-koin:koin-core:4.2.2 & koin-compose:1.1.2)
  * **Rationale**: Lightweight, pure-Kotlin dependency injection library. Avoids heavy annotation processing times and reflection during application startup.
  * **Capabilities Covered**:
    * Injecting singletons like the ProxyEngine, SessionManager, and Storage instances across modules.
    * Managing ViewModel lifecycles in the Compose UI layer.

---

## 7. Serialization and Formats

* **Library**: kotlinx.serialization (org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0)
  * **Rationale**: Official Kotlin compiler-plugin serialization engine. Extremely fast, lightweight, and type-safe.
  * **Capabilities Covered**:
    * Storing configuration files (JSON).
    * Exporting and importing sessions, rules, and mock responses.

---

## 8. Network Clients (Replay Engine)

* **Library**: Ktor Client (io.ktor:ktor-client-core:3.5.1 & CIO engine)
  * **Rationale**: Native asynchronous Kotlin HTTP client. 
  * **Capabilities Covered**:
    * Re-sending/Replaying requests.
    * Multi-threaded performance tests and concurrent connection stress testing.

---

## 9. Dynamic Scripting and Rules Engine

* **Library**: Kotlin Scripting JVM Host (org.jetbrains.kotlin:kotlin-scripting-jvm-host:2.4.0)
  * **Rationale**: Allows executing compiled Kotlin Script (.kts) files at runtime.
  * **Capabilities Covered**:
    * Programmatic rule execution for request interception, validation, or rewriting (analogous to Python scripting in mitmproxy).

---

## 10. Logging Framework

* **Libraries**: Kotlin Logging (io.github.oshai:kotlin-logging-jvm:8.0.4) & Logback Classic (ch.qos.logback:logback-classic:1.5.38)
  * **Rationale**: Kotlin Logging provides an idiomatic lazy-evaluated logger facade, and Logback Classic serves as a highly performant SLF4J implementation.
  * **Capabilities Covered**:
    * Console and file logger configurations.
    * Customizable rolling log outputs for request histories and server logs.

---

## 11. Mock Test Server

* **Framework**: Spring Boot WebFlux (org.springframework.boot:spring-boot-starter-webflux:4.1.0 or 3.4.0)
  * **Rationale**: Built on Netty, WebFlux provides a highly performant non-blocking reactive server environment ideal for simulating arbitrary network responses under test conditions.
  * **Capabilities Covered**:
    * Stub endpoints for Server-Sent Events (SSE), WebSockets, file transfers, slow responses, redirect loops, and compression algorithms.

---

## 12. Testing Infrastructure

* **JUnit 5/6**: JUnit 6.1.2 (jupiter-engine)
* **MockK**: io.mockk:mockk:1.14.11
* **Ktor Client (configured via proxy)**: Used inside integration tests to route traffic through the Netty Proxy engine to the WebFlux mock server to verify rules and decrypt configurations.
