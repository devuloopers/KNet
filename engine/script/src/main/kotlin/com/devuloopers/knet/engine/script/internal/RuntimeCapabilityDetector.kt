package com.devuloopers.knet.engine.script.internal

import com.devuloopers.knet.engine.script.kotlin.runtime.ExpressionRuntime
import com.devuloopers.knet.engine.script.kotlin.runtime.KotlinRuntime
import com.devuloopers.knet.engine.script.kotlin.runtime.NativeKotlinRuntime

/**
 * Capability detector responsible for selecting the optimal available [KotlinRuntime] execution strategy.
 */
class RuntimeCapabilityDetector(
    private val nativeRuntime: KotlinRuntime = NativeKotlinRuntime(),
    private val expressionRuntime: KotlinRuntime = ExpressionRuntime()
) {

    /**
     * Resolves and returns the operational [KotlinRuntime].
     */
    fun selectRuntime(): KotlinRuntime {
        return if (nativeRuntime.isAvailable()) {
            nativeRuntime
        } else {
            expressionRuntime
        }
    }
}
