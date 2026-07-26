# KNet Phase 9 Plan: Replay, Stress Testing & Scripting (replayEngine & pluginApi)

## Background

Phase 9 implements the client-side retransmission engine and runtime scripting capabilities for KNet:
1. **Replay Engine**: Re-sends captured HTTP requests exactly as they were recorded, logging performance benchmarks.
2. **Stress Testing Runner**: Launches concurrent batch requests to simulate load testing scenarios, reporting aggregated latency metrics.
3. **Kotlin Scripting**: Compiles and executes user-defined `.kts` files at runtime to dynamically inspect or mutate request/response fields.
4. **Plugin API**: Defines plug-in registration interfaces to extend KNet's features via JAR libraries.

---

## 1. Module Registration and Build Infrastructure

### 1.1 settings.gradle.kts
Register the new subprojects:
```kotlin
include(":replayEngine")
include(":pluginApi")
```

### 1.2 replayEngine/build.gradle.kts
```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":logger"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
}
```

### 1.3 pluginApi/build.gradle.kts
```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":logger"))
    
    // Kotlin JVM scripting dependencies
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:2.4.0")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:2.4.0")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.0")

    testImplementation(kotlin("test"))
}
```

---

## 2. Architecture & Design

### 2.1 Replay Transmission
* Re-sends any HTTP transaction logged in KNet.
* Automatically reconstructs standard payloads, target paths, request headers, cookies, and HTTP methods.
* Runs asynchronously using Ktor's `CIO` coroutines-based engine.

### 2.2 Stress Testing Loop
* Fires concurrent requests using a semaphore-controlled dispatch loop.
* Gathers response statuses (success/error ratios), total test time, min/max delay, and average response latencies.

### 2.3 Kotlin Scripting Host
* Integrates `BasicJvmScriptEvaluator` and `ScriptCompilationConfiguration` to evaluate dynamic files at runtime.
* Exposes hook scopes so script files can read and modify requests/responses flowing through KNet's interception handlers.

---

## 3. Proposed Files

### 3.1 [NEW] [KNetReplayEngine.kt](file:///c:/Users/Anant.gupta/IdeaProjects/KNet/replayEngine/src/main/kotlin/com/devuloopers/knet/replay/KNetReplayEngine.kt)
Fires single retransmissions and updates connection status.

### 3.2 [NEW] [StressTestRunner.kt](file:///c:/Users/Anant.gupta/IdeaProjects/KNet/replayEngine/src/main/kotlin/com/devuloopers/knet/replay/StressTestRunner.kt)
Runs concurrent request batch loops and formats test result statistics.

### 3.3 [NEW] [KNetPlugin.kt](file:///c:/Users/Anant.gupta/IdeaProjects/KNet/pluginApi/src/main/kotlin/com/devuloopers/knet/plugin/KNetPlugin.kt)
Common plugin interface definition.

### 3.4 [NEW] [ScriptingHost.kt](file:///c:/Users/Anant.gupta/IdeaProjects/KNet/pluginApi/src/main/kotlin/com/devuloopers/knet/plugin/script/ScriptingHost.kt)
Compiles and evaluates `.kts` scripting files.

---

## 4. Verification Plan

### 4.1 Automated Tests
* **Single Replay Test**: Verify that replayed requests successfully ping a mock target server.
* **Stress Test Concurrency**: Assert that the concurrent loop runner throttles connection throughput according to user constraints.
* **Script Evaluator**: Load a script that adds a custom header to a mock request and verify it is mutated correctly.
