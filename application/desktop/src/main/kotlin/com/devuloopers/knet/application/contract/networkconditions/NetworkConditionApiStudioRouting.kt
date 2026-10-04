package com.devuloopers.knet.application.contract.networkconditions

import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.contract.traffic.CaptureSessionState
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration

public const val NETWORK_CONDITIONS_PROXY_UNAVAILABLE_MESSAGE: String =
    "Network Conditions are enabled, but the local proxy is unavailable. Start the proxy and try again."

/**
 * Resolves the one local proxy route shared by HTTP and contributed API Studio protocols.
 * Network Conditions fail closed: a request is never silently sent directly when shaping was requested.
 */
public fun resolveApiStudioProxyPort(
    captureState: CaptureSessionState,
    networkConditions: NetworkConditionConfiguration?,
    proxyState: ProxyRuntimeState,
): Int? {
    val captureActive = captureState is CaptureSessionState.Capturing
    val conditionsActive = networkConditions?.enabled == true
    if (!captureActive && !conditionsActive) return null
    val port = (proxyState as? ProxyRuntimeState.Running)
        ?.handle
        ?.endpoints
        ?.endpoints
        ?.firstOrNull()
        ?.port
    if (conditionsActive && port == null) error(NETWORK_CONDITIONS_PROXY_UNAVAILABLE_MESSAGE)
    return port
}
