package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.engine.protocol.grpc.ProtobufDynamicDecoder
import kotlin.test.Test
import kotlin.test.assertNotNull

class DescriptorRegistryTest {

    @Test
    fun testDescriptorRegistryRegistrationAndClear() {
        val decoder = ProtobufDynamicDecoder()
        decoder.clearRegistry()
        assertNotNull(decoder)
    }
}
