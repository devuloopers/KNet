package com.devuloopers.knet.scriptengine.sandbox

/**
 * Pre-execution static security scanner for scripts.
 */
object ScriptSanitizer {

    private val FORBIDDEN_TOKENS = listOf(
        "GlobalScope", "CoroutineScope", "runBlocking", "launch", "async",
        "Thread", "Executors", "System.exit", "Runtime.getRuntime",
        "ProcessBuilder", "ClassLoader", "Unsafe", "java.io", "java.nio"
    )

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val line: Int? = null
    )

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
