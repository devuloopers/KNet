package com.devuloopers.knet.engine.script.runtime

import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptEngine
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.scripting.model.ScriptLanguage
import com.devuloopers.knet.engine.script.api.ScriptRequestModel
import com.devuloopers.knet.engine.script.api.ScriptResponseModel
import com.devuloopers.knet.engine.script.javascript.GraalJsScriptEngine
import com.devuloopers.knet.engine.script.kotlin.KotlinScriptEngine
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry and router for multi-language execution engines in KNet API Studio.
 * Selects the appropriate [ScriptEngine] based on requested [ScriptLanguage] and executes scripts.
 */
class ScriptEngineManager {

    private val engines = ConcurrentHashMap<ScriptLanguage, ScriptEngine>()

    init {
        registerEngine(GraalJsScriptEngine())
        registerEngine(KotlinScriptEngine())
    }

    /**
     * Registers a [ScriptEngine] implementation with the manager.
     *
     * @param engine The [ScriptEngine] instance to register.
     */
    fun registerEngine(engine: ScriptEngine) {
        engines[engine.language] = engine
    }

    /**
     * Obtains the registered [ScriptEngine] for the specified language.
     *
     * @param language The target [ScriptLanguage].
     * @return The registered [ScriptEngine], or null if unsupported.
     */
    fun getEngine(language: ScriptLanguage): ScriptEngine? {
        return engines[language]
    }

    /**
     * Routes and executes a script code block using the selected language engine.
     * Enforces timeout limits and returns unified execution results.
     *
     * @param language Target [ScriptLanguage] enum.
     * @param code Script source code text.
     * @param request HTTP request model.
     * @param response Optional HTTP response model.
     * @param environment Environment variables store.
     * @param timeoutMs Optional maximum execution time in milliseconds.
     * @return Result of script execution.
     */
    suspend fun execute(
        language: ScriptLanguage,
        code: String,
        request: ScriptRequestModel,
        response: ScriptResponseModel?,
        environment: EnvironmentStore,
        timeoutMs: Long = TimeoutExecutor.DEFAULT_TIMEOUT_MS
    ): ScriptExecutionResult {
        val engine = getEngine(language)
            ?: return ScriptExecutionResult.Error(message = "Scripting language $language is not supported.")

        return TimeoutExecutor.executeWithTimeout(timeoutMs = timeoutMs) {
            engine.execute(
                code = code,
                request = request,
                response = response,
                environment = environment
            )
        }
    }
}
