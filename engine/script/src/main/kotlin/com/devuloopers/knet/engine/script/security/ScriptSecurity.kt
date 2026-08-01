package com.devuloopers.knet.engine.script.security

/**
 * Pre-execution static security scanner and policy enforcer for scripts.
 */
object ScriptSecurity {

    private val FORBIDDEN_TOKENS = listOf(
        "GlobalScope", "CoroutineScope", "runBlocking", "launch", "async",
        "Thread", "Executors", "System.exit", "Runtime.getRuntime",
        "ProcessBuilder", "ClassLoader", "Unsafe", "java.io", "java.nio"
    )

    /**
     * Validation result containing validity status and diagnostic error location.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val line: Int? = null
    )

    /**
     * Scans script code for restricted security keywords and unsafe API calls.
     *
     * @param scriptText Raw script code string.
     * @return [ValidationResult] detailing compliance.
     */
    fun validate(scriptText: String): ValidationResult {
        if (scriptText.isBlank()) return ValidationResult(isValid = true)

        val lines = scriptText.lines()
        lines.forEachIndexed { index, line ->
            FORBIDDEN_TOKENS.forEach { token ->
                if (line.contains(token)) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Restricted Keyword Error: Uses forbidden feature '$token'. Asynchronous threading and OS calls are restricted in scripts.",
                        line = index + 1
                    )
                }
            }
        }

        return ValidationResult(isValid = true)
    }
}
