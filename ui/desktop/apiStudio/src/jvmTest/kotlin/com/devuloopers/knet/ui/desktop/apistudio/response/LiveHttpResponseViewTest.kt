package com.devuloopers.knet.ui.desktop.apistudio.response

import kotlin.test.Test
import kotlin.test.assertEquals

class LiveHttpResponseViewTest {
    @Test
    fun `stable stream gap code is presented as readable text`() {
        assertEquals(
            "Sse decoder expansion limit",
            humanReadableStreamGapReason("sse_decoder_expansion_limit"),
        )
    }
}
