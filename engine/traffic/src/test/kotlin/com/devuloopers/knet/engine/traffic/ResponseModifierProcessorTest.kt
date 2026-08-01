package com.devuloopers.knet.engine.traffic

import com.devuloopers.knet.engine.traffic.processors.ResponseModifierProcessor
import io.netty.handler.codec.http.HttpResponseStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ResponseModifierProcessorTest {

    @Test
    fun testResponseStatusAndHeaderMutation() {
        val rules = listOf(
            ModifierRule("r1", "Override Status", ".*", RuleTarget.RESPONSE_STATUS, RuleAction.MODIFY, newValue = "404"),
            ModifierRule("r2", "Add Server Tag", ".*", RuleTarget.RESPONSE_HEADER, RuleAction.ADD, "Server", "KNetProxy")
        )

        val response = TestFixtures.createHttpResponse(HttpResponseStatus.OK, """{"status":"ok"}""")

        ResponseModifierProcessor.process(response, rules)

        assertEquals(404, response.status().code())
        assertEquals("KNetProxy", response.headers().get("Server"))
    }
}
