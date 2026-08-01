package com.devuloopers.knet.core.http.multipart

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.core.http.model.RequestBodyType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test

class MultipartUploadTest {

    @Test
    fun testMultipartFormExecution() = runBlocking {
        val client = KNetApiClient()
        val formParams = mapOf("file_name" to "avatar.png", "description" to "User Profile Photo")

        val result = client.execute(
            url = "https://httpbin.org/post",
            method = "POST",
            bodyType = RequestBodyType.MULTIPART,
            formParameters = formParams
        )

        assertNotNull(result)
        client.close()
    }
}
