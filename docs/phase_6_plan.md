# KNet Phase 6 Plan [COMPLETED]: Network Simulation & Throttling (networkSimulator)

## Background

Phase 6 introduces a standalone `:networkSimulator` module that simulates degraded network conditions transparently inside the Netty pipeline. It follows the same `pipelineInitializers` integration pattern used by `:trafficModifier`.

Three simulation profiles can be active simultaneously:
1. **Bandwidth Throttling** — limits the byte transfer rate per second.
2. **Latency Injection** — artificially delays request forwarding and response delivery.
3. **Packet Loss** — randomly drops a configurable percentage of requests entirely.

---

## 1. Module Registration

### 1.1 settings.gradle.kts
```kotlin
include(":networkSimulator")
```

### 1.2 networkSimulator/build.gradle.kts
```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":logger"))
    implementation(libs.netty.all)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.core)
}
```

---

## 2. Simulation Configuration Models

### 2.1 [NEW] [NetworkProfile.kt](file:///c:/Users/Anant.gupta/IdeaProjects/KNet/networkSimulator/src/main/kotlin/com/devuloopers/knet/simulator/NetworkProfile.kt)

```kotlin
package com.devuloopers.knet.simulator

/**
 * Immutable configuration of simulated network conditions.
 *
 * @property bandwidthBytesPerSecond Max outbound bytes per second. null = no limit.
 * @property latencyMs Fixed artificial delay added to every request/response, in milliseconds.
 * @property packetLossPercent 0–100 integer. The percentage of requests randomly dropped.
 */
data class NetworkProfile(
    val bandwidthBytesPerSecond: Long? = null,
    val latencyMs: Long = 0L,
    val packetLossPercent: Int = 0
) {
    companion object {
        /** No simulation applied — passthrough mode. */
        val NONE = NetworkProfile()

        /** Simulates a slow 3G mobile connection. */
        val MOBILE_3G = NetworkProfile(bandwidthBytesPerSecond = 50_000, latencyMs = 300)

        /** Simulates a typical 4G LTE mobile connection. */
        val MOBILE_4G = NetworkProfile(bandwidthBytesPerSecond = 5_000_000, latencyMs = 50)

        /** Simulates a high-packet-loss unstable network. */
        val LOSSY = NetworkProfile(latencyMs = 200, packetLossPercent = 20)
    }
}
```

### 2.2 [NEW] [NetworkSimulatorManager.kt](file:///c:/Users/Anant.gupta/IdeaProjects/KNet/networkSimulator/src/main/kotlin/com/devuloopers/knet/simulator/NetworkSimulatorManager.kt)

Thread-safe, hot-swappable profile holder using `@Volatile`:
```kotlin
class NetworkSimulatorManager {
    @Volatile var activeProfile: NetworkProfile = NetworkProfile.NONE

    fun applyProfile(profile: NetworkProfile) { activeProfile = profile }
    fun reset() { activeProfile = NetworkProfile.NONE }
}
```

---

## 3. Netty Pipeline Integration

### 3.1 [NEW] [KNetNetworkSimulatorHandler.kt](file:///c:/Users/Anant.gupta/IdeaProjects/KNet/networkSimulator/src/main/kotlin/com/devuloopers/knet/simulator/KNetNetworkSimulatorHandler.kt)

A `ChannelDuplexHandler` registered before `proxyHandler`:

**Inbound (channelRead — request path):**
- **Packet loss**: Roll a random integer; if within the loss percentage threshold, release the message and return without forwarding it.
- **Latency**: Schedule `context.fireChannelRead(msg)` through `context.executor().schedule(delay, MILLISECONDS)` so the Netty event loop is not blocked.

**Outbound (write — response path):**
- **Latency**: Schedule `context.write(msg, promise)` with the configured delay.
- **Bandwidth**: Use Netty's built-in `GlobalTrafficShapingHandler` — which we add to the pipeline dynamically when a profile with a bandwidth limit is active, and remove when reset.

### 3.2 Bandwidth via GlobalTrafficShapingHandler
Netty's `io.netty.handler.traffic.GlobalTrafficShapingHandler` is the idiomatic approach — it works at the byte level, not the request level, so it correctly shapes binary streams without needing a custom byte counter. It is added to the head of the pipeline when bandwidth limiting is configured.

---

## 4. Preset Profiles
We expose well-known named presets (3G, 4G, LOSSY, etc.) via the `NetworkProfile.Companion` so users can configure profiles in one line rather than specifying raw numbers.

---

## 5. Verification Plan

### 5.1 Automated Tests
* **Packet loss**: Assert that with 100% loss, all requests are silently dropped.
* **Latency**: Assert that a coroutine-suspended delay fires after at least `latencyMs`.
* **Profile presets**: Assert that known presets have the expected field values.
* **Manager hot-swap**: Assert that changing the active profile mid-test is immediately reflected in subsequent reads.

---

## Open Questions for User

> [!IMPORTANT]
> **Bandwidth Shaping granularity**: `GlobalTrafficShapingHandler` works globally across all channels. Should we use per-channel shaping (`ChannelTrafficShapingHandler`) to throttle each connection independently? This matters if multiple clients connect to KNet simultaneously. For a desktop proxy tool with one operator, global shaping is usually sufficient — but per-channel gives finer control.
