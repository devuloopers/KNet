package com.devuloopers.knet.engine.proxy.inspection

import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationCode
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CompositeProxyDuplexTransformerTest {
    @Test
    fun `composite applies edits in registration order and cancels in reverse order`() {
        val events = mutableListOf<String>()
        val first = Transformer("first", events) { payload -> payload + 2 }
        val second = Transformer("second", events, handlesNetworkConditions = true) { payload -> payload + 3 }
        val composite = composeProxyDuplexTransformers(listOf(first, second))!!

        val result = composite.transform(
            TrafficDirection.CLIENT_TO_SERVER,
            byteArrayOf(1),
            1L,
        ).toCompletableFuture().get(1, TimeUnit.SECONDS)
        composite.cancel(null)

        assertContentEquals(byteArrayOf(1, 2, 3), assertIs<ProxyDuplexTransformResult.Forward>(result).copyPayload())
        assertEquals(listOf("first:transform", "second:transform", "second:cancel", "first:cancel"), events)
        assertTrue(composite.handlesNetworkConditions)
    }

    @Test
    fun `drop short circuits later transforms`() {
        val events = mutableListOf<String>()
        val reason = TrafficTerminationReason.Protocol(MessageProtocolId("test"), TrafficTerminationCode("drop"))
        val dropping = Transformer("drop", events, result = ProxyDuplexTransformResult.DropConnection(reason))
        val unreachable = Transformer("unreachable", events) { payload -> payload }
        val composite = composeProxyDuplexTransformers(listOf(dropping, unreachable))!!

        val result = composite.transform(
            TrafficDirection.SERVER_TO_CLIENT,
            byteArrayOf(1),
            1L,
        ).toCompletableFuture().get(1, TimeUnit.SECONDS)

        assertIs<ProxyDuplexTransformResult.DropConnection>(result)
        assertEquals(listOf("drop:transform"), events)
    }

    private class Transformer(
        private val name: String,
        private val events: MutableList<String>,
        override val handlesNetworkConditions: Boolean = false,
        private val result: ProxyDuplexTransformResult? = null,
        private val edit: (ByteArray) -> ByteArray = { payload -> payload },
    ) : ProxyDuplexTransformer {
        override fun transform(
            direction: TrafficDirection,
            payload: ByteArray,
            occurredAtEpochMillis: Long,
        ): CompletableFuture<ProxyDuplexTransformResult> {
            events += "$name:transform"
            return CompletableFuture.completedFuture(
                result ?: ProxyDuplexTransformResult.Forward(edit(payload)),
            )
        }

        override fun cancel(reason: TrafficTerminationReason?) {
            events += "$name:cancel"
        }

    }
}
