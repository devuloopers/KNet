package com.devuloopers.knet.application.usecase.apistudio

import com.devuloopers.knet.application.contract.apistudio.*
import com.devuloopers.knet.application.contract.export.StudioExportRecorder
import com.devuloopers.knet.application.contract.export.StudioExportSession
import kotlinx.coroutines.flow.*

/** Opens a native session with execution-owned export retention when configured by the product. */
public class OpenApiStudioProtocolSessionUseCase(
    private val registry: ApiStudioProtocolSessionExecutorRegistry,
    private val recorder: StudioExportRecorder? = null,
    private val authoring: ApiStudioProtocolAuthoringRegistry? = null,
) {
    /** The wrapper records before presentation truncates its timeline; no network retry is introduced. */
    public fun execute(command: ApiStudioProtocolExecutionCommand): Result<ApiStudioProtocolExecutionSession> = registry.open(command).map { session ->
        val archive = try { authoring?.readDocument(command.document)?.getOrNull()?.let { recorder?.protocol(command, it)?.isolated() } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { null }
        if (archive == null) session else object : ApiStudioProtocolExecutionSession {
            override val exportSession: StudioExportSession = archive
            override val events: Flow<ApiStudioProtocolExecutionEvent> = session.events.onEach { archive.event(it) }
                .map { if (it is ApiStudioProtocolExecutionEvent.Started) it.copy(exportSession = archive) else it }
                .onCompletion { archive.interrupted(); if (!archive.eligible.value) archive.release() }
            override suspend fun send(message: ApiStudioProtocolOutboundMessage): Result<Unit> = session.send(message)
            override suspend fun halfClose(): Result<Unit> = session.halfClose()
            override fun cancel() { archive.interrupted(); session.cancel() }
        }
    }
}
