package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.formatters.FormDataBodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormDataBodyFormatterTest {
    private val formatter = FormDataBodyFormatter()

    @Test
    fun testFormDataParsing() {
        assertTrue(formatter.matches(mapOf("content-type" to "application/x-www-form-urlencoded"), TestFixtures.SAMPLE_FORM_DATA))

        val result = formatter.format(mapOf("content-type" to "application/x-www-form-urlencoded"), TestFixtures.SAMPLE_FORM_DATA)
        assertTrue(result is BodyFormat.FormData)
        assertEquals(3, result.pairs.size)
        assertEquals("name" to "KNet", result.pairs[0])
    }
}
