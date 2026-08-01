package com.devuloopers.knet.engine.script

import com.devuloopers.knet.engine.script.api.ScriptRequestModel
import com.devuloopers.knet.engine.script.api.ScriptResponseModel

object TestFixtures {

    fun createSampleRequest(): ScriptRequestModel {
        return ScriptRequestModel(
            url = "https://api.example.com/v1/users",
            method = "POST",
            headers = mutableMapOf("Content-Type" to "application/json", "User-Agent" to "KNet/1.0"),
            queryParams = mutableMapOf("page" to "1"),
            body = """{"name":"KNet Test User","email":"test@example.com"}"""
        )
    }

    fun createSampleResponse(): ScriptResponseModel {
        return ScriptResponseModel(
            statusCode = 200,
            statusText = "OK",
            latencyMs = 120L,
            responseSizeBytes = 256L,
            headers = mapOf("content-type" to "application/json", "x-transaction-id" to "tx_987654"),
            body = """{"success":true,"id":"usr_123456","name":"KNet Test User"}"""
        )
    }
}
