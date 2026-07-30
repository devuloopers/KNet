package com.devuloopers.knet.scriptengine.bridge

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.core.ScriptEngineManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit test suite for Console Bridge logging in script contexts.
 * Verifies console log levels (log, warn, error, info), Unicode strings, and Emojis.
 */
class ConsoleBridgeTest {

    private val engineManager = ScriptEngineManager()
    private val request = ScriptRequestModel("http://localhost", "GET", mutableMapOf(), mutableMapOf(), "")

    /**
     * Verifies logging of Unicode characters and Emojis through console.log.
     */
    @Test
    fun testConsoleUnicodeAndEmojiLogging() = runBlocking {
        val script = """
            console.log("🌐 KNet Multi-Language Engine 🔥");
            console.warn("⚠️ High latency detected");
            console.error("❌ Authentication failed");
        """.trimIndent()

        val result = engineManager.execute(
            language = ScriptLanguage.JAVASCRIPT,
            code = script,
            request = request,
            response = null,
            environment = EnvironmentStore()
        )

        assertTrue(result is ScriptExecutionResult.Success)
        val logs = (result as ScriptExecutionResult.Success).logs
        assertEquals(3, logs.size)
        assertTrue(logs[0].contains("🌐 KNet Multi-Language Engine 🔥"))
        assertTrue(logs[1].contains("⚠️ High latency detected"))
        assertTrue(logs[2].contains("❌ Authentication failed"))
    }
}
