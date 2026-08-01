package com.devuloopers.knet.engine.simulator

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion

object TestFixtures {

    fun createFullHttpRequest(body: String = "test_payload"): FullHttpRequest {
        val content = Unpooled.copiedBuffer(body, Charsets.UTF_8)
        return DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "https://api.example.com/v1/data", content)
    }
}
