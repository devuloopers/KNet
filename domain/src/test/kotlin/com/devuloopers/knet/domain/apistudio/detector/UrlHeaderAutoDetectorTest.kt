package com.devuloopers.knet.domain.apistudio.detector

import com.devuloopers.knet.domain.apistudio.model.HttpMethod
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.domain.apistudio.model.defaultHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UrlParameterExtractorTest {

    private val extractor = UrlParameterExtractor()

    @Test
    fun `test Path variables and query params extraction`() {
        val result = extractor.extract("https://api.example.com/users/:id/posts/:postId?sort=desc&limit=20")

        assertTrue(result.pathVariables.containsKey("id"))
        assertTrue(result.pathVariables.containsKey("postId"))
        assertEquals("desc", result.queryParameters["sort"])
        assertEquals("20", result.queryParameters["limit"])
    }

    @Test
    fun `test default headers seeded for SavedApiRequest`() {
        val req = SavedApiRequest(
            id = "req-1",
            name = "Test Request",
            method = HttpMethod.GET,
            url = "https://api.github.com/users/octocat/repos"
        )

        assertEquals(6, req.headers.size)
        val userAgent = req.headers.find { it.key == "User-Agent" }
        assertEquals("KNet-Desktop/2.4.0", userAgent?.value)
        assertTrue(userAgent?.isAuto == true)

        val accept = req.headers.find { it.key == "Accept" }
        assertEquals("*/*", accept?.value)
        assertTrue(accept?.isAuto == true)

        val host = req.headers.find { it.key == "Host" }
        assertEquals("<auto>", host?.value)
        assertTrue(host?.isAuto == true)
    }
}
