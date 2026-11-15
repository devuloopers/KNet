package com.devuloopers.knet.application.usecase.apistudio

import com.devuloopers.knet.application.contract.apistudio.*
import com.devuloopers.knet.application.contract.export.StudioExportRecorder
import kotlinx.coroutines.flow.*

/** Executes one native document while retaining original events independently of the UI window. */
public class ExecuteApiStudioProtocolDocumentUseCase(
    private val registry: ApiStudioProtocolExecutorRegistry,
    private val recorder: StudioExportRecorder? = null,
    private val authoring: ApiStudioProtocolAuthoringRegistry? = null,
) {
    /** Each collection owns its own archive; drafts and later invocations cannot mutate it. */
    public fun execute(command: ApiStudioProtocolExecutionCommand): Flow<ApiStudioProtocolExecutionEvent> = flow {
        val archive = try { authoring?.readDocument(command.document)?.getOrNull()?.let { recorder?.protocol(command, it)?.isolated() } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { null }
        try {
            registry.execute(command).collect { event ->
                archive?.event(event)
                emit(if (event is ApiStudioProtocolExecutionEvent.Started) event.copy(exportSession = archive) else event)
            }
        } finally { archive?.interrupted(); if (archive?.eligible?.value == false) archive.release() }
    }
}
