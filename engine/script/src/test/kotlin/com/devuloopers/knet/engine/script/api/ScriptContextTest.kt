package com.devuloopers.knet.engine.script.api

import com.devuloopers.knet.engine.script.TestFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScriptContextTest {

    @Test
    fun testScriptContextProperties() {
        val req = TestFixtures.createSampleRequest()
        val resp = TestFixtures.createSampleResponse()
        val env = EnvironmentStore()

        val context = ScriptContext(request = req, response = resp, environment = env)

        assertEquals(req, context.request)
        assertEquals(resp, context.response)
        assertEquals(env, context.environment)

        val nullContext = ScriptContext(request = req, response = null, environment = env)
        assertNull(nullContext.response)
        assertNotNull(nullContext.request)
    }
}
