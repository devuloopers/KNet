package com.devuloopers.knet.engine.script.javascript

import kotlin.test.Test
import kotlin.test.assertNotNull

class JavascriptRuntimeTest {

    @Test
    fun testResourceLoader() {
        val consoleSource = ScriptResourceLoader.loadSource("/runtime/console.js")
        val expectSource = ScriptResourceLoader.loadSource("/runtime/expect.js")

        assertNotNull(consoleSource)
        assertNotNull(expectSource)
    }
}
