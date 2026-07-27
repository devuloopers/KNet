package com.devuloopers.knet

import com.devuloopers.knet.domain.utils.prettyPrintJson
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the [prettyPrintJson] utility function.
 *
 * Covers:
 * - Simple flat objects and arrays
 * - Nested objects and arrays
 * - Escaped quotes inside string values
 * - Double-escaped backslashes inside string values
 * - Colons and commas inside string values (must not trigger formatting)
 * - Curly braces inside string values (must not trigger formatting)
 * - Empty objects and arrays (must stay on one line)
 * - Non-JSON input (must be returned unchanged)
 * - Idempotency (formatting already-pretty JSON produces identical output)
 */
class PrettyPrintJsonTest {

    // -------------------------------------------------------------------------
    // Basic formatting
    // -------------------------------------------------------------------------

    @Test
    fun flatObjectIsIndentedCorrectly() {
        val input = """{"name":"Alice","age":30}"""
        val expected = "{\n  \"name\": \"Alice\",\n  \"age\": 30\n}"
        assertEquals(expected, prettyPrintJson(input))
    }

    @Test
    fun flatArrayIsIndentedCorrectly() {
        val input = """[1,2,3]"""
        val expected = "[\n  1,\n  2,\n  3\n]"
        assertEquals(expected, prettyPrintJson(input))
    }

    // -------------------------------------------------------------------------
    // Nesting
    // -------------------------------------------------------------------------

    @Test
    fun nestedObjectIsIndentedWithIncreasingDepth() {
        val input = """{"user":{"name":"Alice"}}"""
        val expected = "{\n  \"user\": {\n    \"name\": \"Alice\"\n  }\n}"
        assertEquals(expected, prettyPrintJson(input))
    }

    @Test
    fun arrayOfObjectsIsFormattedCorrectly() {
        val input = """[{"id":1},{"id":2}]"""
        val expected = "[\n  {\n    \"id\": 1\n  },\n  {\n    \"id\": 2\n  }\n]"
        assertEquals(expected, prettyPrintJson(input))
    }

    // -------------------------------------------------------------------------
    // Edge cases: special characters inside strings
    // -------------------------------------------------------------------------

    @Test
    fun escapedQuoteInsideStringDoesNotBreakFormatting() {
        val input = """{"msg":"say \"hi\""}"""
        val expected = "{\n  \"msg\": \"say \\\"hi\\\"\"\n}"
        assertEquals(expected, prettyPrintJson(input))
    }

    @Test
    fun doubleBackslashInsideStringDoesNotBreakFormatting() {
        // Windows path: C:\\Users\\Alice
        val input = """{"path":"C:\\Users\\Alice"}"""
        val expected = "{\n  \"path\": \"C:\\\\Users\\\\Alice\"\n}"
        assertEquals(expected, prettyPrintJson(input))
    }

    @Test
    fun colonInsideStringValueIsNotTreatedAsSeparator() {
        val input = """{"time":"12:30:00"}"""
        val expected = "{\n  \"time\": \"12:30:00\"\n}"
        assertEquals(expected, prettyPrintJson(input))
    }

    @Test
    fun commaInsideStringValueIsNotTreatedAsElementSeparator() {
        val input = """{"tags":"red,green,blue"}"""
        val expected = "{\n  \"tags\": \"red,green,blue\"\n}"
        assertEquals(expected, prettyPrintJson(input))
    }

    @Test
    fun curlyBracesInsideStringValueAreNotTreatedAsStructural() {
        val input = """{"template":"{value}"}"""
        val expected = "{\n  \"template\": \"{value}\"\n}"
        assertEquals(expected, prettyPrintJson(input))
    }

    // -------------------------------------------------------------------------
    // Empty containers
    // -------------------------------------------------------------------------

    @Test
    fun emptyObjectStaysOnOneLine() {
        assertEquals("{}", prettyPrintJson("{}"))
    }

    @Test
    fun emptyArrayStaysOnOneLine() {
        assertEquals("[]", prettyPrintJson("[]"))
    }

    @Test
    fun nestedEmptyObjectStaysOnOneLine() {
        val input = """{"meta":{}}"""
        val expected = "{\n  \"meta\": {}\n}"
        assertEquals(expected, prettyPrintJson(input))
    }

    // -------------------------------------------------------------------------
    // Non-JSON passthrough
    // -------------------------------------------------------------------------

    @Test
    fun plainStringIsReturnedUnchanged() {
        val input = "Hello, world!"
        assertEquals(input, prettyPrintJson(input))
    }

    @Test
    fun xmlStringIsReturnedUnchanged() {
        val input = "<root><child/></root>"
        assertEquals(input, prettyPrintJson(input))
    }

    @Test
    fun emptyStringIsReturnedUnchanged() {
        assertEquals("", prettyPrintJson(""))
    }

    // -------------------------------------------------------------------------
    // Idempotency
    // -------------------------------------------------------------------------

    @Test
    fun formattingIsIdempotent() {
        val compact = """{"name":"Alice","age":30}"""
        val firstPass = prettyPrintJson(compact)
        val secondPass = prettyPrintJson(firstPass)
        assertEquals(firstPass, secondPass)
    }

    // -------------------------------------------------------------------------
    // decodeBodyToText Binary & Heuristic Detection
    // -------------------------------------------------------------------------

    @Test
    fun googleOctetStreamCompressibleMimeReturnsRawBytesForProtobufWireDecoder() {
        val bytes = ByteArray(512) { 0x01 }
        val headers = listOf("Content-Type" to "application/vnd.google.octet-stream-compressible")
        val result = com.devuloopers.knet.domain.utils.decodeBodyToText(bytes, headers)
        assertEquals(String(bytes, Charsets.ISO_8859_1), result)
    }

    @Test
    fun protobufPayloadWithEmbeddedTextStringsAndNullBytesTriggersBinaryHeuristic() {
        // Simulates a real Protobuf response containing embedded ASCII text ("Chrome WIN 150.0") mixed with null bytes and binary tags
        val textBytes = "Chrome WIN 150.0.7871.186".toByteArray(Charsets.UTF_8)
        val protobufBytes = byteArrayOf(0x0A, 0x1A, 0x00, 0x00) + textBytes + byteArrayOf(0x00, 0x12, 0x04, 0x08, 0x00)
        val headers = listOf("Content-Type" to "text/plain") // misleading Content-Type
        val result = com.devuloopers.knet.domain.utils.decodeBodyToText(protobufBytes, headers)
        assertEquals("[Binary payload — 0.0 KB · text/plain]", result)
    }
}
