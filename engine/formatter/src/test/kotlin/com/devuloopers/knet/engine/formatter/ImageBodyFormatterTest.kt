package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.formatters.ImageBodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageBodyFormatterTest {
    private val formatter = ImageBodyFormatter()

    @Test
    fun testImageMatchingAndLabels() {
        assertTrue(formatter.matches(mapOf("content-type" to "image/png"), ""))

        val png = formatter.format(mapOf("content-type" to "image/png"), "")
        assertTrue(png is BodyFormat.Image)
        assertEquals("PNG Image", png.label)

        val jpeg = formatter.format(mapOf("content-type" to "image/jpeg"), "")
        assertTrue(jpeg is BodyFormat.Image)
        assertEquals("JPEG Image", jpeg.label)
    }
}
