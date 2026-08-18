package com.devuloopers.knet.scripting.model

/** Script source languages supported by KNet runtimes and editors. */
public enum class ScriptLanguage {
    JAVASCRIPT,
    KOTLIN,
}

/** Lifecycle point at which a script is evaluated. */
public enum class ScriptPhase {
    PRE_REQUEST,
    POST_RESPONSE,
    GLOBAL_RULE,
}

/** Reusable source template with one implementation per supported language. */
public data class ScriptSnippet(
    public val title: String,
    public val codeJs: String,
    public val codeKotlin: String,
    public val id: String = "",
    public val description: String = "",
)

/** One immutable assertion emitted by a script or collection runner. */
public data class ScriptAssertion(
    public val name: String,
    public val passed: Boolean,
    public val errorMessage: String? = null,
    public val durationMillis: Long = 0L,
    public val id: String = "",
) {
    init {
        require(durationMillis >= 0L) { "Assertion duration must not be negative." }
    }
}
