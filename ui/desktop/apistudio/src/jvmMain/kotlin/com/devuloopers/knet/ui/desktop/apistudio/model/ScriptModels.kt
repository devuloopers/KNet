package com.devuloopers.knet.ui.desktop.apistudio.model

import com.devuloopers.knet.engine.script.api.ScriptLanguage

/**
 * Execution phase for request scripts in API Studio.
 */
public enum class ScriptPhase(val label: String) {
    PRE_REQUEST("Pre-request"),
    TESTS("Post-response / Tests")
}

/**
 * Represents a quick code snippet template for request/test scripts.
 *
 * @property title User-facing title string.
 * @property codeJs JavaScript code template.
 * @property codeKotlin Kotlin code template.
 */
public data class ScriptSnippet(
    val title: String,
    val codeJs: String,
    val codeKotlin: String
)

/**
 * State DTO holding configuration for script editing in API Studio.
 *
 * @property preRequestScript Pre-request script source code.
 * @property testScript Post-response test script source code.
 * @property scriptLanguage Target scripting language engine (JavaScript vs Kotlin).
 * @property activePhase Active script editing tab phase (Pre-request vs Tests).
 */
public data class ScriptState(
    val preRequestScript: String = "",
    val testScript: String = "",
    val scriptLanguage: ScriptLanguage = ScriptLanguage.JAVASCRIPT,
    val activePhase: ScriptPhase = ScriptPhase.TESTS
)

/**
 * Registry providing standard built-in quick code snippets for API Studio scripts.
 */
public object ScriptSnippetRegistry {

    /**
     * Default list of built-in quick code snippets for JavaScript and Kotlin scripting.
     */
    public val DEFAULT_SNIPPETS: List<ScriptSnippet> = listOf(
        ScriptSnippet(
            title = "Status 200",
            codeJs = """pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});""",
            codeKotlin = """test("Status code is 200") {
    response.statusCode == 200
}"""
        ),
        ScriptSnippet(
            title = "Response < 500ms",
            codeJs = """pm.test("Response time is less than 500ms", function () {
    pm.expect(pm.response.responseTime).to.be.below(500);
});""",
            codeKotlin = """test("Response time is less than 500ms") {
    response.latencyMs < 500
}"""
        ),
        ScriptSnippet(
            title = "Set Env Var",
            codeJs = """pm.environment.set("authToken", "secret_token_value");""",
            codeKotlin = """env["authToken"] = "secret_token_value""""
        ),
        ScriptSnippet(
            title = "Check JSON",
            codeJs = """pm.test("Check JSON property", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData.success).to.eql(true);
});""",
            codeKotlin = """test("Check JSON property") {
    response.body.contains("\"success\": true")
}"""
        )
    )
}
