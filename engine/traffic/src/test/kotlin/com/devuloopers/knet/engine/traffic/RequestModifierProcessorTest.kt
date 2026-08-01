package com.devuloopers.knet.engine.traffic

import com.devuloopers.knet.engine.traffic.processors.RequestModifierProcessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RequestModifierProcessorTest {

    @Test
    fun testRequestHeaderMutations() {
        val rules = listOf(
            ModifierRule("r1", "Add Key", ".*", RuleTarget.REQUEST_HEADER, RuleAction.ADD, "X-ApiKey", "secret123"),
            ModifierRule("r2", "Remove UserAgent", ".*", RuleTarget.REQUEST_HEADER, RuleAction.REMOVE, "User-Agent")
        )

        val request = TestFixtures.createHttpRequest("https://api.example.com/data")
        request.headers().set("User-Agent", "KNetClient")

        RequestModifierProcessor.process(request, "https://api.example.com/data", rules)

        assertEquals("secret123", request.headers().get("X-ApiKey"))
        assertNull(request.headers().get("User-Agent"))
    }

    @Test
    fun testRequestBodyTextReplacement() {
        val rule = ModifierRule("r1", "Replace User", ".*", RuleTarget.REQUEST_BODY, RuleAction.MODIFY, "ADMIN", "TEST_USER")
        val request = TestFixtures.createHttpRequest(
            uri = "https://api.example.com/data",
            body = """{"role":"ADMIN"}"""
        )

        RequestModifierProcessor.process(request, "https://api.example.com/data", listOf(rule))

        val bodyText = request.content().toString(Charsets.UTF_8)
        assertEquals("""{"role":"TEST_USER"}""", bodyText)
    }
}
