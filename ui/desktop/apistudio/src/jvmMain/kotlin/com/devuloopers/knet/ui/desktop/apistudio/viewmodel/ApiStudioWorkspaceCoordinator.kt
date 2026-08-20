package com.devuloopers.knet.ui.desktop.apistudio.viewmodel

import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.usecase.UpdateWorkspaceLayoutUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Serializes API Studio workspace preference changes in the same order as UI intents. */
internal class ApiStudioWorkspaceCoordinator(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    private val updateWorkspaceLayout: UpdateWorkspaceLayoutUseCase,
    private val onFailure: (Throwable) -> Unit,
) {
    private data class Update(
        val transform: (WorkspaceLayoutSettings) -> WorkspaceLayoutSettings,
        val completion: CompletableDeferred<Result<Unit>>?
    )

    private val updates = Channel<Update>(capacity = Channel.UNLIMITED)

    init {
        scope.launch(dispatcher) {
            for (update in updates) {
                val result = updateResult(update.transform)
                update.completion?.complete(result) ?: result.onFailure(onFailure)
            }
        }
    }

    /** Enqueues a latest workspace preference change without blocking the UI caller. */
    fun schedule(transform: (WorkspaceLayoutSettings) -> WorkspaceLayoutSettings) {
        check(updates.trySend(Update(transform, completion = null)).isSuccess) {
            "API Studio workspace preference queue is unavailable."
        }
    }

    /** Applies a preference change in actor order and acknowledges its persistence result. */
    suspend fun updateAndAwait(
        transform: (WorkspaceLayoutSettings) -> WorkspaceLayoutSettings
    ): Result<Unit> {
        val completion = CompletableDeferred<Result<Unit>>()
        updates.send(Update(transform, completion))
        return completion.await()
    }

    private suspend fun updateResult(
        transform: (WorkspaceLayoutSettings) -> WorkspaceLayoutSettings
    ): Result<Unit> = try {
        updateWorkspaceLayout.execute(transform)
        Result.success(Unit)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        Result.failure(failure)
    }
}
