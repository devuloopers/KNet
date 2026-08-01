package com.devuloopers.knet.core.http.execution

import com.devuloopers.knet.core.http.model.ApiExecutionResult
import com.devuloopers.knet.core.http.model.AuthType
import com.devuloopers.knet.core.http.model.RequestBodyType
import com.devuloopers.knet.domain.collection.model.SavedApiRequest

/**
 * Interface contract defining universal outbound HTTP execution.
 * Decouples consumer applications and ViewModels from underlying HTTP engine implementations.
 */
interface HttpExecutor : AutoCloseable {

    /**
     * Executes an HTTP call defined by a domain [SavedApiRequest] model.
     *
     * @param request Immutable domain request model.
     * @return [ApiExecutionResult] containing response details and timing metrics.
     */
    suspend fun execute(request: SavedApiRequest): ApiExecutionResult

    /**
     * Executes an HTTP call with fine-grained parameter specification.
     */
    suspend fun execute(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        bodyType: RequestBodyType = RequestBodyType.NONE,
        formParameters: Map<String, String> = emptyMap(),
        authType: AuthType = AuthType.NONE,
        authToken: String = ""
    ): ApiExecutionResult
}
