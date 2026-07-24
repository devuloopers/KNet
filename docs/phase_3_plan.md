# KNet Phase 3 Plan [COMPLETED]: Non-Blocking Interception & Pipelines (interceptor)

This document specifies the exact design, files, dependencies, and implementation details for Phase 3 of KNet development, which introduces non-blocking HTTP request/response interception and breakpoints using Kotlin Coroutines and Netty backpressure.

---

## 1. Module Registration and Build Infrastructure

### 1.1 settings.gradle.kts
Register the `interceptor` module:
```kotlin
include(":desktopApp")
include(":shared")
include(":logger")
include(":certificateManager")
include(":proxyEngine")
include(":interceptor")
```

### 1.2 interceptor/build.gradle.kts
Configure dependencies:
```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":logger"))
    implementation(project(":proxyEngine"))
    implementation(libs.netty.all)
    implementation(libs.kotlinx.coroutines.core) // For CompletableDeferred and event loops
    
    testImplementation(kotlin("test"))
}
```

---

## 2. Dynamic Interception Mechanics & Backpressure

To allow real-time manual editing of HTTP payloads without freezing Netty's NIO event loop threads, KNet will employ an asynchronous suspension model:

1. **Auto-Read Suppression**: When an interception rule triggers, the handler executes `context.channel().config().setAutoRead(false)`. This stops Netty from reading further byte sequences from the TCP socket, halting incoming network frames.
2. **Coroutine Suspension**: The pipeline suspends execution by creating a Kotlin `CompletableDeferred<HttpRequest>` or `CompletableDeferred<HttpResponse>`.
3. **Resume and Flush**: Once the user edits the data in the presentation layer and triggers a resume event, the deferred state resolves. The handler writes the modified object into the Netty context, restores auto-read (`setAutoRead(true)`), and flushes the pipeline.

---

## 3. Class Design and Packages

All classes will be located under `com.devuloopers.knet.interceptor`.

### 3.1 BreakpointRule
A DTO representing matching criteria.
* **Fields**:
  * `val id: String`
  * `val urlRegex: String?`
  * `val method: String?` (e.g. GET, POST)
  * `val isRequestEnabled: Boolean`
  * `val isResponseEnabled: Boolean`
* **Methods**:
  * `matches(url: String, method: String): Boolean`

### 3.2 InterceptedEvent
A state container for a paused connection.
* **Fields**:
  * `val id: String`: Unique tracing identifier.
  * `val request: HttpRequest`: The captured request data.
  * `val response: HttpResponse?`: The captured response data (null if paused during request phase).
  * val deferred: CompletableDeferred<InterceptResult>
* **Data Classes**:
  * `sealed class InterceptResult`
    * `class Resume(val modifiedRequest: HttpRequest?, val modifiedResponse: HttpResponse?) : InterceptResult()`
    * `object Drop : InterceptResult()`

### 3.3 BreakpointManager
A thread-safe state container tracking active breakpoint rules and suspended events.
* **Fields**:
  * `private val rules = ConcurrentHashMap<String, BreakpointRule>()`
  * `private val activeSuspensions = ConcurrentHashMap<String, InterceptedEvent>()`
* **Methods**:
  * `addRule(rule: BreakpointRule)`
  * `removeRule(id: String)`
  * `shouldInterceptRequest(url: String, method: String): Boolean`
  * `shouldInterceptResponse(url: String, method: String): Boolean`
  * `suspendRequest(request: HttpRequest): CompletableDeferred<InterceptResult>`
  * `suspendResponse(request: HttpRequest, response: HttpResponse): CompletableDeferred<InterceptResult>`
  * `resume(eventId: String, result: InterceptResult)`

### 3.4 KNetInterceptorHandler
An inbound and outbound Netty handler placed in the pipeline (after HTTP aggregation).
* **Process**:
  * **Inbound (Request)**: If `BreakpointManager.shouldInterceptRequest` matches, suspend using `suspendRequest` on a CoroutineScope, block the handler write, set auto-read to false, wait for resolution, then proceed or drop.
  * **Outbound (Response)**: Similarly, before writing the response back, verify if response interception is enabled. If matched, suspend, await edits, and then write the modified response.

---

## 4. Verification Plan

We will write integration tests in `interceptor/src/test/kotlin/com/devuloopers/knet/interceptor/InterceptorIntegrationTest.kt`:
1. **Request Interception**: Route a request through the proxy that matches a breakpoint. Assert that the request suspends, auto-read is disabled, we edit a request header, call resume, and the mock remote server receives the modified header.
2. **Response Interception**: Route a request, trigger a response breakpoint. Assert that the response suspends, we modify the response body, resume, and the client receives the modified body.
3. **Drop Connection**: Assert that dropping an intercepted event closes the client connection immediately.
