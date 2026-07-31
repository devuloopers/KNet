package com.devuloopers.knet.ui.apistudio.viewmodel.handler

import com.devuloopers.knet.domain.apistudio.model.HttpMethod
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.ui.apistudio.handler.FormHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit test suite for [com.devuloopers.knet.ui.apistudio.handler.FormHandler].
 */
class FormHandlerTest {

    private val handler = FormHandler()
    private val baseRequest = SavedApiRequest(
        id = "r1",
        name = "Req 1",
        method = HttpMethod.GET,
        url = "http://localhost/api/users/:id"
    )

    @Test
    fun testUpdateUrlAndPathParamsExtraction() {
        val (updated, pathParams) = handler.updateUrl(baseRequest, "http://localhost/api/users/:id/details/:subId")

        assertEquals("http://localhost/api/users/:id/details/:subId", updated.url)
        assertEquals(2, pathParams.size)
        assertTrue(pathParams.containsKey("id"))
        assertTrue(pathParams.containsKey("subId"))
    }

    @Test
    fun testHeaderManagement() {
        val reqWithHeaders = handler.addHeader(baseRequest)
        val updatedReq = handler.updateHeaderKey(reqWithHeaders, "", "X-Custom-Auth")
        val finalReq = handler.updateHeaderValue(updatedReq, "X-Custom-Auth", "secret_value")

        val header = finalReq.headers.find { it.key == "X-Custom-Auth" }
        assertNotNull(header)
        assertEquals("secret_value", header.value)
    }
}
