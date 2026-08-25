package com.devuloopers.knet.engine.sse.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SseIncrementalParserTest {
    @Test
    fun `all byte chunkings produce identical event semantics`() {
        val fixture = ("\uFEFFid: 7\r\nevent: price\r\ndata: first\r\ndata: second\r\nretry: 1500\r\n\r\n" +
            ": keep alive\ndata: next\n\n").encodeToByteArray()
        val expected = parse(fixture, fixture.size)

        for (chunkSize in 1..fixture.size) {
            val actual = parse(fixture, chunkSize)
            assertEquals(expected.map(::summary), actual.map(::summary), "chunk size $chunkSize")
            expected.zip(actual).forEach { (left, right) ->
                assertContentEquals(left.copyRawRecord(), right.copyRawRecord())
            }
        }
    }

    @Test
    fun `supports CR LF CRLF comments state and field rules`() {
        val parser = SseIncrementalParser()
        val output = parser.accept(
            "id: initial\rid: bad\u0000id\nretry: 12x\r\nretry: 42\r\nunknown:value\r\n\r\n".encodeToByteArray(),
        )

        val record = assertIs<SseParseResult.Record>(output.single()).value
        assertEquals(SseRecordKind.STATE_UPDATE, record.kind)
        assertEquals("initial", record.lastEventId)
        assertEquals(42L, record.retryMillis)
        assertNull(record.data)
        assertEquals("initial", parser.currentLastEventId())
        assertEquals(42L, parser.currentRetryMillis())
    }

    @Test
    fun `empty data dispatches an event while incomplete EOF data is discarded`() {
        val parser = SseIncrementalParser()
        val first = parser.accept("data\n\n".encodeToByteArray())
        val record = assertIs<SseParseResult.Record>(first.single()).value
        assertEquals(SseRecordKind.EVENT, record.kind)
        assertEquals("", record.data)
        assertEquals("message", record.eventType)

        assertTrue(parser.accept("data: not dispatched".encodeToByteArray()).isEmpty())
        assertTrue(parser.finish().isEmpty())
    }

    @Test
    fun `oversized record emits an explicit bounded gap and resumes`() {
        val parser = SseIncrementalParser(
            SseLimits(
                maximumLineBytes = 8,
                maximumRecordBytes = 16,
                maximumDataCharacters = 8,
            ),
        )
        val output = parser.accept("data: 123456789\n\ndata: ok\n\n".encodeToByteArray())

        assertIs<SseParseResult.Gap>(output[0])
        val recovered = assertIs<SseParseResult.Record>(output[1]).value
        assertEquals("ok", recovered.data)
    }

    @Test
    fun `comment record retains text without inventing an event`() {
        val result = SseIncrementalParser().accept(": heartbeat\n\n".encodeToByteArray()).single()
        val record = assertIs<SseParseResult.Record>(result).value
        assertEquals(SseRecordKind.COMMENT, record.kind)
        assertEquals(listOf("heartbeat"), record.comments)
        assertNull(record.eventType)
    }

    @Test
    fun `malformed utf8 becomes a bounded gap and parsing resumes`() {
        val malformed = byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(),
            ':'.code.toByte(), ' '.code.toByte(), 0xC3.toByte(), 0x28.toByte(), '\n'.code.toByte(), '\n'.code.toByte())
        val parser = SseIncrementalParser()

        val output = parser.accept(malformed + "data: recovered\n\n".encodeToByteArray())

        val gap = assertIs<SseParseResult.Gap>(output[0])
        assertEquals("sse_malformed_utf8", gap.reason)
        assertEquals("recovered", assertIs<SseParseResult.Record>(output[1]).value.data)
    }

    @Test
    fun `bom is stripped only at the beginning of the stream`() {
        val parser = SseIncrementalParser()

        val output = parser.accept("\n\uFEFFdata: later\n\n".encodeToByteArray())

        assertEquals(SseRecordKind.STATE_UPDATE, assertIs<SseParseResult.Record>(output.single()).value.kind)
    }

    private fun parse(bytes: ByteArray, chunkSize: Int): List<SseParsedRecord> {
        val parser = SseIncrementalParser()
        return buildList {
            bytes.asList().chunked(chunkSize).forEach { chunk ->
                parser.accept(chunk.toByteArray()).forEach { result ->
                    if (result is SseParseResult.Record) add(result.value)
                }
            }
            parser.finish().forEach { result ->
                if (result is SseParseResult.Record) add(result.value)
            }
        }
    }

    private fun summary(record: SseParsedRecord): List<Any?> = listOf(
        record.kind,
        record.eventType,
        record.data,
        record.lastEventId,
        record.retryMillis,
        record.comments,
    )
}
