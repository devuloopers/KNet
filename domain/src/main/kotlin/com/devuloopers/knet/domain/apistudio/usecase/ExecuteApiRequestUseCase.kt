package com.devuloopers.knet.domain.apistudio.usecase

import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest

/**
 * Interface representing the result of executing an API request.
 */
data class ExecutionResult(
    val statusCode: Int,
    val statusText: String,
    val headers: Map<String, String>,
    val responseBody: String,
    val latencyMs: Long,
    val responseSizeBytes: Long,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

/**
 * Functional interface / contract for executing a [SavedApiRequest].
 */
fun interface ExecuteApiRequestUseCase {
    suspend operator fun invoke(request: SavedApiRequest): ExecutionResult
}
