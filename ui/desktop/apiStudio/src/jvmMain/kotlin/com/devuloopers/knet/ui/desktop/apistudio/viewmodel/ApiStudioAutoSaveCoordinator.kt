package com.devuloopers.knet.ui.desktop.apistudio.viewmodel

import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestEditorState
import com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Immutable point-in-time API Studio document passed to the ordered persistence queue. */
internal data class ApiStudioAutoSaveSnapshot(
    val context: SessionContext,
    val title: String,
    val nameOrigin: RequestNameOrigin,
    val editorState: RequestEditorState,
    val revision: Long
) {
    /** Stable queue key preventing writes from one document from superseding another document. */
    val key: String = when (context) {
        is SessionContext.UnsavedDraft -> "draft:${context.sessionId}"
        is SessionContext.SavedRequest -> "saved:${context.requestId}"
        SessionContext.None -> "none"
    }
}

/**
 * Serializes debounced and explicit API Studio saves through one unbounded command channel.
 *
 * Editor events never launch database writes directly. A delayed job only submits the newest revision back
 * to the actor, while the actor itself is the sole persistence caller. Explicit flushes acknowledge completion,
 * allowing promotion to wait until the final draft state is durable before moving it transactionally.
 */
internal class ApiStudioAutoSaveCoordinator(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    private val debounceMillis: Long = 300L,
    private val persist: suspend (ApiStudioAutoSaveSnapshot) -> Unit,
    private val onFailure: (Throwable) -> Unit
) {
    private sealed interface Command {
        data class Schedule(val snapshot: ApiStudioAutoSaveSnapshot) : Command
        data class Persist(val snapshot: ApiStudioAutoSaveSnapshot) : Command
        data class Flush(
            val snapshot: ApiStudioAutoSaveSnapshot,
            val completion: CompletableDeferred<Result<Unit>>
        ) : Command
        data class Discard(val key: String, val completion: CompletableDeferred<Unit>) : Command
    }

    private val commands = Channel<Command>(capacity = Channel.UNLIMITED)

    init {
        scope.launch(dispatcher) {
            val latestRevisions = mutableMapOf<String, Long>()
            val pendingJobs = mutableMapOf<String, Job>()
            for (command in commands) {
                when (command) {
                    is Command.Schedule -> {
                        latestRevisions[command.snapshot.key] = command.snapshot.revision
                        pendingJobs.remove(command.snapshot.key)?.cancel()
                        pendingJobs[command.snapshot.key] = launch {
                            delay(debounceMillis)
                            commands.send(Command.Persist(command.snapshot))
                        }
                    }
                    is Command.Persist -> {
                        pendingJobs.remove(command.snapshot.key)
                        if (latestRevisions[command.snapshot.key] == command.snapshot.revision) {
                            persistSafely(command.snapshot)
                        }
                    }
                    is Command.Flush -> {
                        latestRevisions[command.snapshot.key] = command.snapshot.revision
                        pendingJobs.remove(command.snapshot.key)?.cancel()
                        command.completion.complete(persistResult(command.snapshot))
                    }
                    is Command.Discard -> {
                        latestRevisions.remove(command.key)
                        pendingJobs.remove(command.key)?.cancel()
                        command.completion.complete(Unit)
                    }
                }
            }
        }
    }

    /** Schedules the newest immutable document revision for debounced persistence. */
    fun schedule(snapshot: ApiStudioAutoSaveSnapshot) {
        check(commands.trySend(Command.Schedule(snapshot)).isSuccess) {
            "API Studio auto-save queue is unavailable."
        }
    }

    /** Persists a snapshot in actor order and returns only after the write completes. */
    suspend fun flush(snapshot: ApiStudioAutoSaveSnapshot): Result<Unit> {
        val completion = CompletableDeferred<Result<Unit>>()
        commands.send(Command.Flush(snapshot, completion))
        return completion.await()
    }

    /** Cancels pending work for a document after deletion or successful promotion. */
    suspend fun discard(key: String) {
        val completion = CompletableDeferred<Unit>()
        commands.send(Command.Discard(key, completion))
        completion.await()
    }

    private suspend fun persistSafely(snapshot: ApiStudioAutoSaveSnapshot) {
        persistResult(snapshot).onFailure(onFailure)
    }

    private suspend fun persistResult(snapshot: ApiStudioAutoSaveSnapshot): Result<Unit> = try {
        persist(snapshot)
        Result.success(Unit)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        Result.failure(failure)
    }
}
