package com.devuloopers.knet.testingserver.grpc

import com.devuloopers.knet.testingserver.grpc.v1.ProtocolLabGrpc
import io.grpc.Server
import io.grpc.protobuf.services.HealthStatusManager
import io.grpc.protobuf.services.ProtoReflectionServiceV1
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component

/**
 * Configuration for the native gRPC fixture listener.
 *
 * @property port Requested TCP port. Zero requests an ephemeral port for automated tests.
 */
@ConfigurationProperties("knet.testing-server.grpc")
data class GrpcServerProperties @ConstructorBinding constructor(
    val port: Int,
) {
    init {
        require(port in MIN_PORT..MAX_PORT) { "gRPC test port must be between 0 and 65535." }
    }

    private companion object {
        const val MIN_PORT = 0
        const val MAX_PORT = 65_535
    }
}

/**
 * Binds and releases the native grpc-java server with the Spring application lifecycle.
 *
 * Spring invokes lifecycle methods serially. Startup first constructs a complete server, then publishes it and
 * marks the service healthy. Shutdown marks health terminal before rejecting new calls and releasing the port.
 */
@Component
class GrpcServerLifecycle(
    private val properties: GrpcServerProperties,
    private val service: ProtocolLabGrpcService,
) : SmartLifecycle {
    private val healthStatusManager = HealthStatusManager()

    @Volatile
    private var server: Server? = null

    @Volatile
    private var running: Boolean = false

    /** Actual listener port, including the operating-system-selected value when configured with zero. */
    val boundPort: Int
        get() = server?.port ?: properties.port

    /** Starts the gRPC listener once and publishes service health only after binding succeeds. */
    override fun start() {
        if (running) return
        val startedServer = NettyServerBuilder.forPort(properties.port)
            .addService(service)
            .addService(healthStatusManager.healthService)
            .addService(ProtoReflectionServiceV1.newInstance())
            .build()
            .start()
        server = startedServer
        healthStatusManager.setStatus(
            ProtocolLabGrpc.SERVICE_NAME,
            io.grpc.health.v1.HealthCheckResponse.ServingStatus.SERVING,
        )
        running = true
    }

    /** Marks the service terminal and releases the listener without waiting on a coroutine thread. */
    override fun stop() {
        if (!running) return
        healthStatusManager.enterTerminalState()
        server?.shutdownNow()
        server = null
        running = false
    }

    /**
     * Stops the listener and reports completion to Spring.
     *
     * @param callback Lifecycle callback invoked after shutdown has been requested.
     */
    override fun stop(callback: Runnable) {
        stop()
        callback.run()
    }

    /** @return Whether the native listener is currently bound. */
    override fun isRunning(): Boolean = running

    /** @return True so the listener starts with the containing Spring context. */
    override fun isAutoStartup(): Boolean = true
}
