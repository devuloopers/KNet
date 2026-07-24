# KNet Phase 7 Plan [COMPLETED]: Advanced Protocol Support (protocolInspector)

## Background

Phase 7 expands KNet's network proxy capabilities to inspect advanced application protocols beyond standard HTTP/1.1:
1. **WebSockets**: Intercepts WebSocket handshake upgrades and decodes bidirectional frames (text, binary, close, ping, pong) for logging and inspection.
2. **HTTP/2**: Decodes multiplexed concurrent streams, enabling single-connection multi-stream visibility.
3. **gRPC & Protobuf**: Provides dynamic binary decoding of Protobuf payloads when schema descriptors are registered.

To keep the codebase modular, we will create a unified `:protocolInspector` module rather than just a WebSockets module.

---

## 1. Module Registration and Build Infrastructure

### 1.1 settings.gradle.kts
Register the new subproject:
```kotlin
include(":protocolInspector")
```

### 1.2 protocolInspector/build.gradle.kts
Configure dependencies:
```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":logger"))
    implementation(libs.netty.all)
    implementation(libs.kotlinx.coroutines.core)

    // Protobuf Java library for dynamic message decoding
    implementation("com.google.protobuf:protobuf-java:3.25.1")
    implementation("com.google.protobuf:protobuf-java-util:3.25.1")

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.core)
}
```

---

## 2. Architecture & Design

```
Client  ◄──►  [KNet Decryption Pipeline]  ◄──►  [Protocol Inspector]  ◄──►  Remote Server
                                                         │
                                                         ▼
                                                [Recorded Logs]
```

### 2.1 WebSockets Interception
* **Handshake Upgrade**: Netty client/server channels initiate upgrades via `101 Switching Protocols`.
* **Frame Tap**: Once the handshake completes, we insert a custom `KNetWebSocketFrameHandler` to tap frames on both the client-facing and server-facing channels.
* **Model Representation**: Define `WebSocketFrameRecord` to store frame type (text, binary, control), payload size, timestamp, and payload string/hex.

### 2.2 HTTP/2 Frame Decoding
* **Stream Multiplexing**: HTTP/2 runs multiple concurrent streams on a single TCP connection.
* **Frame Decoder**: Integrate Netty's `Http2FrameCodecBuilder` to decode HTTP/2 frames (`Http2HeadersFrame`, `Http2DataFrame`, `Http2SettingsFrame`) into visual stream flows.

### 2.3 gRPC / Protobuf Decoding
* **Binary Serialization**: gRPC uses HTTP/2 POST with content-type `application/grpc`.
* **Dynamic Decoder**: Implement a parser that maps binary raw buffers to human-readable JSON using self-describing protobuf descriptor files `.desc` / `.proto` registered by the user.

---

## 3. Proposed Files

### 3.1 [NEW] [ProtocolModels.kt](file:///c:/Users/Anant.gupta/IdeaProjects/KNet/protocolInspector/src/main/kotlin/com/devuloopers/knet/protocol/ProtocolModels.kt)
Holds representation of WebSocket frames and HTTP/2 stream metadata.

### 3.2 [NEW] [KNetWebSocketFrameHandler.kt](file:///c:/Users/Anant.gupta/IdeaProjects/KNet/protocolInspector/src/main/kotlin/com/devuloopers/knet/protocol/websocket/KNetWebSocketFrameHandler.kt)
Taps WebSocket frames, logs metadata, and routes them to the storage/session managers.

### 3.3 [NEW] [ProtobufDynamicDecoder.kt](file:///c:/Users/Anant.gupta/IdeaProjects/KNet/protocolInspector/src/main/kotlin/com/devuloopers/knet/protocol/grpc/ProtobufDynamicDecoder.kt)
Loads schema descriptor files dynamically to translate gRPC binary bytes to JSON structures.

---

## 4. Verification Plan

### 4.1 Automated Tests
* **WebSocket Tapping Test**: Simulate a WebSocket client upgrading to a mock echo server, verifying that `Text` and `Binary` frames are intercepted and logged correctly.
* **Protobuf Decoding Test**: Feed raw protobuf byte streams with a mock `.desc` schema file and assert that it correctly decodes to the expected JSON string representation.
