package com.devuloopers.knet.core.http.integration

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.clientNetwork.model.RequestFormField
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test

class MultipartIntegrationTest {

    @Test
    fun testMultipartUploadIntegration() = runBlocking {
        val client = KNetApiClient()
        val result = client.executeDetailed(
            url = "https://httpbin.org/post",
            method = HttpMethod.POST,
            body = OutboundRequestBody.Multipart(
                listOf(RequestFormField("test_param", "test_value"))
            )
        )

        assertNotNull(result)
        client.close()
    }
}
