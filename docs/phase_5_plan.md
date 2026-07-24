# KNet Phase 5 Plan [IN PROGRESS]: Traffic Rules (trafficModifier)

This document specifies the exact design, files, dependencies, and implementation details for Phase 5 of KNet development, which introduces automated rules to alter traffic on the fly: Map Local, Map Remote, and header/body/query rewrites.

---

## 1. Module Registration and Build Infrastructure

We will create a new subproject: `:trafficModifier`.

### 1.1 settings.gradle.kts
Register the new subproject in settings:
```kotlin
include(":trafficModifier")
```

### 1.2 trafficModifier/build.gradle.kts
Configure dependencies:
```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":logger"))
    implementation(libs.netty.all)

    testImplementation(kotlin("test"))
}
```

---

## 2. Rule Architecture & Models

We will define structured rules to support Map Local, Map Remote, and Rewrite transformations.
Package: `com.devuloopers.knet.modifier`

### 2.1 [NEW] [ModifierRule.kt](file:///c:/Users/Anant.gupta/IdeaProjects/KNet/trafficModifier/src/main/kotlin/com/devuloopers/knet/modifier/ModifierRule.kt)
Models that define regex criteria and replacement targets:
```kotlin
package com.devuloopers.knet.modifier

enum class RuleTarget {
    REQUEST_HEADER,
    RESPONSE_HEADER,
    REQUEST_QUERY,
    REQUEST_BODY,
    RESPONSE_BODY,
    RESPONSE_STATUS
}

enum class RuleAction { ADD, MODIFY, REMOVE }

data class ModifierRule(
    val id: String,
    val name: String,
    val urlPattern: String,
    val target: RuleTarget,
    val action: RuleAction,
    val matchValue: String?,
    val newValue: String?,
    val enabled: Boolean = true
)

data class MapLocalRule(
    val id: String,
    val name: String,
    val urlPattern: String,
    val localFilePath: String,
    val mimeType: String? = null,
    val enabled: Boolean = true
)

data class MapRemoteRule(
    val id: String,
    val name: String,
    val urlPattern: String,
    val targetHost: String,
    val targetPort: Int,
    val targetProtocol: String = "https",
    val enabled: Boolean = true
)
```

### 2.2 [NEW] [TrafficModifierManager.kt](file:///c:/Users/Anant.gupta/IdeaProjects/KNet/trafficModifier/src/main/kotlin/com/devuloopers/knet/modifier/TrafficModifierManager.kt)
Thread-safe rule registry:
```kotlin
package com.devuloopers.knet.modifier

import java.util.concurrent.CopyOnWriteArrayList

class TrafficModifierManager {
    val modifierRules = CopyOnWriteArrayList<ModifierRule>()
    val mapLocalRules = CopyOnWriteArrayList<MapLocalRule>()
    val mapRemoteRules = CopyOnWriteArrayList<MapRemoteRule>()

    fun addModifierRule(rule: ModifierRule) { modifierRules.add(rule) }
    fun addMapLocalRule(rule: MapLocalRule) { mapLocalRules.add(rule) }
    fun addMapRemoteRule(rule: MapRemoteRule) { mapRemoteRules.add(rule) }
    fun clearAllRules() {
        modifierRules.clear()
        mapLocalRules.clear()
        mapRemoteRules.clear()
    }
}
```

---

## 3. Netty Pipeline Integration

We will implement a Netty `ChannelDuplexHandler` called `KNetTrafficModifierHandler` that runs *before* `proxyHandler`.

### 3.1 [NEW] [KNetTrafficModifierHandler.kt](file:///c:/Users/Anant.gupta/IdeaProjects/KNet/trafficModifier/src/main/kotlin/com/devuloopers/knet/modifier/KNetTrafficModifierHandler.kt)
* **Inbound (channelRead)**:
  * If **Map Local** matches: Read local file bytes, write `FullHttpResponse` directly, skip forwarding.
  * If **Map Remote** matches: Override `knet.host` / `knet.port` channel attributes and rewrite `Host` header.
  * If **ModifierRules** (Request Targets) match: Apply header/query/body modifications inline.
* **Outbound (write)**:
  * If **ModifierRules** (Response Targets) match: Apply header/status/body modifications before response is written back to the client.

### 3.2 Pipeline Hooking
Modify `KNetProxyServer.pipelineInitializers` to register `KNetTrafficModifierHandler` dynamically when rules are active.

---

## 4. Verification Plan

### 4.1 Automated Tests
* Create `com.devuloopers.knet.modifier.KNetTrafficModifierIntegrationTest` to verify:
  * **Map Local Short-circuit**: Assert matching a regex returns local file immediately without remote network requests.
  * **Map Remote Re-routing**: Assert requests for domain A are transparently forwarded to domain B.
  * **Header, Query & Body Modifier Rules**: Assert additions/removals/replacements of headers, query params, status codes, and body text.
