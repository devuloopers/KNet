package com.devuloopers.knet.engine.script.kotlin

import com.devuloopers.knet.engine.script.kotlin.runtime.NativeKotlinRuntime
import kotlin.test.Test
import kotlin.test.assertNotNull

class NativeKotlinRuntimeTest {

    private val runtime = NativeKotlinRuntime()

    @Test
    fun testNativeRuntimeAvailability() {
        assertNotNull(runtime)
    }
}
