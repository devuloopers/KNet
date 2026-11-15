package com.devuloopers.knet.application.usecase.apistudio

import com.devuloopers.knet.application.contract.export.StudioExportSession
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionEvent
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionResponseHead
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import kotlinx.coroutines.CancellationException

/** Export storage is ancillary: disk failures retire its evidence without aborting an API request/session. */
internal fun StudioExportSession.isolated(): StudioExportSession {
    val delegate = this
    return object : StudioExportSession by delegate {
        suspend fun safely(block: suspend () -> Unit) {
            try { block() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { delegate.release() }
        }
        override suspend fun response(head: HttpExecutionResponseHead) = safely { delegate.response(head) }
        override suspend fun append(bytes: ByteArray) = safely { delegate.append(bytes) }
        override suspend fun complete(result: ExecutionResult) = safely { delegate.complete(result) }
        override suspend fun event(event: ApiStudioProtocolExecutionEvent) = safely { delegate.event(event) }
    }
}
