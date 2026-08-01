package com.devuloopers.knet.engine.traffic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MimeTypeUtilsTest {

    @Test
    fun testTextualMimeTypeDetection() {
        assertTrue(MimeTypeUtils.isTextualPayload("text/plain"))
        assertTrue(MimeTypeUtils.isTextualPayload("text/html; charset=utf-8"))
        assertTrue(MimeTypeUtils.isTextualPayload("application/json"))
        assertTrue(MimeTypeUtils.isTextualPayload("application/xml"))
        assertTrue(MimeTypeUtils.isTextualPayload("application/javascript"))
        assertTrue(MimeTypeUtils.isTextualPayload("application/graphql"))
        assertTrue(MimeTypeUtils.isTextualPayload("application/x-www-form-urlencoded"))
    }

    @Test
    fun testBinaryMimeTypeProtection() {
        assertFalse(MimeTypeUtils.isTextualPayload("image/png"))
        assertFalse(MimeTypeUtils.isTextualPayload("image/jpeg"))
        assertFalse(MimeTypeUtils.isTextualPayload("video/mp4"))
        assertFalse(MimeTypeUtils.isTextualPayload("application/pdf"))
        assertFalse(MimeTypeUtils.isTextualPayload("application/zip"))
        assertFalse(MimeTypeUtils.isTextualPayload("application/octet-stream"))
        assertFalse(MimeTypeUtils.isTextualPayload("application/protobuf"))
        assertFalse(MimeTypeUtils.isTextualPayload("application/x-gzip"))
    }
}
