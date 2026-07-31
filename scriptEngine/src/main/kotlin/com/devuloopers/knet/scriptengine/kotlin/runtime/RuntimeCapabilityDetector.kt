package com.devuloopers.knet.scriptengine.kotlin.runtime

/**
 * Capability detector responsible for selecting the optimal available [KotlinRuntime] execution strategy.
 */
class RuntimeCapabilityDetector(
    private val nativeRuntime: KotlinRuntime = NativeKotlinRuntime(),
    private val expressionRuntime: KotlinRuntime = ExpressionRuntime()
) {

    /**
     * Resolves and returns the operational [KotlinRuntime].
     * Prefers [NativeKotlinRuntime] if JSR-223 scripting host is available,
     * otherwise gracefully falls back to [ExpressionRuntime].
     */
    fun selectRuntime(): KotlinRuntime {
        return if (nativeRuntime.isAvailable()) {
            nativeRuntime
        } else {
            expressionRuntime
        }
    }
}
