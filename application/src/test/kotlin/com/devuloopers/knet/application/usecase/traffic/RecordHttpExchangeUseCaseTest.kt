package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.port.traffic.RecordHttpExchangeCommand
import com.devuloopers.knet.application.port.traffic.TrafficBodyPayload
import com.devuloopers.knet.application.port.traffic.TrafficRecordPort
import com.devuloopers.knet.application.port.traffic.TrafficRecordReceipt
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Verifies the application recording use case preserves canonical metadata and body ownership. */
public class RecordHttpExchangeUseCaseTest {
    @Test
    public fun `delegates canonical heads while body payload retains its own bytes`() = runTest {
        val source = byteArrayOf(1, 2, 3)
        val port = RecordingPort()
        val command = RecordHttpExchangeCommand(
            exchangeId = ExchangeId("exchange-1"),
            request = RequestHead(
                method = HttpMethod.fromToken("POST"),
                target = RequestTarget.Origin("/items"),
                protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                headers = emptyList(),
            ),
            requestBody = TrafficBodyPayload(source),
            state = ExchangeState.FAILED,
            startedAtEpochMillis = 1L,
            completedAtEpochMillis = 2L,
            errorCode = "request-failed",
        )
        source[0] = 9

        val receipt = RecordHttpExchangeUseCase(port).execute(command)

        assertEquals(CaptureSessionId("session-1"), receipt.sessionId)
        assertEquals(command, port.recorded)
        val copy = ByteArray(3)
        command.requestBody?.copyInto(copy, sourceOffset = 0, length = 3)
        assertContentEquals(byteArrayOf(1, 2, 3), copy)
    }

    private class RecordingPort : TrafficRecordPort {
        var recorded: RecordHttpExchangeCommand? = null

        override suspend fun record(command: RecordHttpExchangeCommand): TrafficRecordReceipt {
            recorded = command
            return TrafficRecordReceipt(CaptureSessionId("session-1"), command.exchangeId)
        }
    }
}
