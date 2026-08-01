package com.devuloopers.knet.engine.script.javascript

import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.engine.script.api.ScriptRequestModel
import com.devuloopers.knet.engine.script.api.ScriptResponseModel

/**
 * Strategy interface for JavaScript runtime execution.
 */
interface JavascriptRuntime {
    /**
     * Evaluates JavaScript source code asynchronously.
     */
    suspend fun execute(
        code: String,
        request: ScriptRequestModel,
        response: ScriptResponseModel?,
        environment: EnvironmentStore
    ): ScriptExecutionResult
}
