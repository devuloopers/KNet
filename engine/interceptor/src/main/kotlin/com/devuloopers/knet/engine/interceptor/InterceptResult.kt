package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse

/**
 * Result of resolving a suspended intercepted event.
 */
sealed class InterceptResult {
    /**
     * Resumes the traffic pipeline, optionally using modified request/response DTOs.
     */
    class Resume(val modifiedRequest: HttpRequest? = null, val modifiedResponse: HttpResponse? = null) : InterceptResult()

    /**
     * Terminated status indicating that the connection should be dropped immediately.
     */
    object Drop : InterceptResult()

    /**
     * Suspension timed out automatically after configured duration.
     */
    object Timeout : InterceptResult()
}
