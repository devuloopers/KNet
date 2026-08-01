package com.devuloopers.knet.engine.script.regression

import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptContext
import com.devuloopers.knet.engine.script.api.ScriptEngine
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.engine.script.api.ScriptLanguage
import com.devuloopers.knet.engine.script.api.ScriptRequestModel
import com.devuloopers.knet.engine.script.api.ScriptResponseModel
import com.devuloopers.knet.engine.script.api.ScriptTestResult
import kotlin.test.Test
import kotlin.test.assertNotNull

class MigrationRegressionTest {

    @Test
    fun testPublicApiTypesExistAndAreAccessible() {
        val lang: ScriptLanguage = ScriptLanguage.JAVASCRIPT
        val req = ScriptRequestModel("https://a.com", "GET", mutableMapOf(), mutableMapOf(), "")
        val resp = ScriptResponseModel(200, "OK", 10L, 100L, emptyMap(), "")
        val env = EnvironmentStore()
        val context = ScriptContext(req, resp, env)
        val testResult = ScriptTestResult("T1", true)
        val success = ScriptExecutionResult.Success(req, listOf(testResult), emptyMap(), emptyList())

        assertNotNull(lang)
        assertNotNull(context)
        assertNotNull(success)
    }
}
