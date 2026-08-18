package com.devuloopers.knet.core.http.multipart

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.clientNetwork.model.RequestFormField
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test

class MultipartUploadTest {

    @Test
    fun testMultipartFormExecution() = runBlocking {
        val client = KNetApiClient()
        val formParams = mapOf("file_name" to "avatar.png", "description" to "User Profile Photo")

        val result = client.executeDetailed(
            url = "https://httpbin.org/post",
            method = HttpMethod.POST,
            body = OutboundRequestBody.Multipart(
                formParams.map { (name, value) -> RequestFormField(name, value) }
            )
        )

        assertNotNull(result)
        client.close()
    }
}
