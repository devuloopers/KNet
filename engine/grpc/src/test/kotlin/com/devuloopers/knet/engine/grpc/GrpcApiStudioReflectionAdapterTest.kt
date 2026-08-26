package com.devuloopers.knet.engine.grpc

import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolReflectionTarget
import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.protobuf.services.HealthStatusManager
import io.grpc.protobuf.services.ProtoReflectionServiceV1
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GrpcApiStudioReflectionAdapterTest {
    private var server: Server? = null

    @AfterTest
    fun tearDown() {
        server?.shutdownNow()
    }

    @Test
    fun `reflection imports callable methods without generated client stubs`() = runBlocking {
        val health = HealthStatusManager()
        server = NettyServerBuilder.forPort(0)
            .addService(health.healthService)
            .addService(ProtoReflectionServiceV1.newInstance())
            .build()
            .start()
        val descriptors = GrpcDescriptorRegistry()
        val adapter = GrpcApiStudioReflectionAdapter(
            descriptors = descriptors,
            channels = GrpcClientChannelFactory(byteArrayOf(1)),
        )

        val result = adapter.reflect(
            ApiStudioProtocolReflectionTarget(
                host = "127.0.0.1",
                port = requireNotNull(server).port,
                useTls = false,
                deadlineMillis = 5_000L,
            ),
        ).getOrThrow()

        assertEquals(2, result.summary.operationCount)
        assertNotNull(descriptors.resolve(GrpcMethodIdentity("grpc.health.v1.Health", "Check")))
        assertNotNull(descriptors.resolve(GrpcMethodIdentity("grpc.health.v1.Health", "Watch")))
        Unit
    }
}
