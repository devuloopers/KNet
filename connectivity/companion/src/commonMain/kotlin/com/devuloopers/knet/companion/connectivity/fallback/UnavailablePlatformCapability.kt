package com.devuloopers.knet.companion.connectivity.fallback

import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode

/** Creates the consistent typed failure used by unavailable native companion capabilities. */
internal fun unavailablePlatformCapability(platformName: String, capability: String): CompanionFailure =
    CompanionFailure(
        code = CompanionFailureCode.PLATFORM_ADAPTER_UNAVAILABLE,
        message = "The $platformName $capability adapter is not implemented.",
        recoverable = false,
    )
