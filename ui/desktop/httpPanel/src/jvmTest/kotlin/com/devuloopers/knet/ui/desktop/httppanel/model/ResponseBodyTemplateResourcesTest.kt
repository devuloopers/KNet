package com.devuloopers.knet.ui.desktop.httppanel.model

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResponseBodyTemplateResourcesTest {
    @Test
    fun `html response mode uses packaged default document`() {
        val document = ResponseBodyTemplateResources.html

        assertTrue(document.isNotBlank())
        assertContains(document, "HTTP Response")
        assertContains(document, "200 OK")
        assertEquals(document, ResponseBodyMode.HTML.placeholder)
    }
}
