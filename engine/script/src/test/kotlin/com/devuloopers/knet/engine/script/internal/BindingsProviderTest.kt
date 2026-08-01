package com.devuloopers.knet.engine.script.internal

import com.devuloopers.knet.engine.script.TestFixtures
import com.devuloopers.knet.engine.script.api.EnvironmentStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BindingsProviderTest {

    @Test
    fun testBindingsMap() {
        val req = TestFixtures.createSampleRequest()
        val resp = TestFixtures.createSampleResponse()
        val env = EnvironmentStore()
        val collector = ResultCollector()

        val provider = BindingsProvider(req, resp, env, collector)
        val bindings = provider.createBindingsMap()

        assertNotNull(bindings["context"])
        assertEquals(req, bindings["request"])
        assertEquals(resp, bindings["response"])
        assertEquals(env, bindings["environment"])
        assertEquals(env, bindings["env"])
        assertEquals(collector, bindings["resultCollector"])
    }
}
