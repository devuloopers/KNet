package com.devuloopers.knet.core.http.integration

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.core.http.model.RequestBodyType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test

class MultipartIntegrationTest {

    @Test
    fun testMultipartUploadIntegration() = runBlocking {
        val client = KNetApiClient()
        val result = client.execute(
            url = "https://httpbin.org/post",
            method = "POST",
            bodyType = RequestBodyType.MULTIPART,
            formParameters = mapOf("test_param" to "test_value")
        )

        assertNotNull(result)
        client.close()
    }
}
