package com.devuloopers.knet.bodyformatter

import com.devuloopers.knet.bodyformatter.formatter.FormDataBodyFormatter
import com.devuloopers.knet.bodyformatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormDataBodyFormatterTest {
    private val formatter = FormDataBodyFormatter()

    @Test
    fun testFormDataDecoding() {
        val rawForm = "grant_type=authorization_code&code=xyz%20123&client_id=knet_app"
        assertTrue(formatter.matches(mapOf("content-type" to "application/x-www-form-urlencoded"), rawForm))

        val result = formatter.format(mapOf("content-type" to "application/x-www-form-urlencoded"), rawForm)
        assertTrue(result is BodyFormat.FormData)
        assertEquals(3, result.pairs.size)
        assertEquals("grant_type" to "authorization_code", result.pairs[0])
        assertEquals("code" to "xyz 123", result.pairs[1])
        assertEquals("client_id" to "knet_app", result.pairs[2])
    }

    @Test
    fun testMultipartFormDataParsing() {
        val rawMultipart = """
            --boundary123
            Content-Disposition: form-data; name="field1"
            
            value1
            --boundary123
            Content-Disposition: form-data; name="file"; filename="avatar.png"
            Content-Type: image/png
            
            binary-data
            --boundary123--
        """.trimIndent()

        assertTrue(
            formatter.matches(
                mapOf("content-type" to "multipart/form-data; boundary=boundary123"),
                rawMultipart
            )
        )

        val result =
            formatter.format(mapOf("content-type" to "multipart/form-data; boundary=boundary123"), rawMultipart)
        assertTrue(result is BodyFormat.FormData)
        assertEquals(2, result.pairs.size)
        assertEquals("field1" to "value1", result.pairs[0])
        assertEquals("file [avatar.png]" to "binary-data", result.pairs[1])
    }
}
