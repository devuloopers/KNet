package com.devuloopers.knet.ui.desktop.httppanel.model

import com.devuloopers.knet.scripting.model.ScriptLanguage
import com.devuloopers.knet.scripting.model.ScriptPhase
import com.devuloopers.knet.scripting.model.ScriptSnippet

/**
 * State DTO holding configuration for script editing in API Studio.
 *
 * @property preRequestScript Pre-request script source code.
 * @property testScript Post-response test script source code.
 * @property scriptLanguage Target scripting language engine (JavaScript vs Kotlin).
 * @property activePhase Active script editing tab phase (Pre-request vs Tests).
 */
data class ScriptState(
    val preRequestScript: String = "",
    val testScript: String = "",
    val scriptLanguage: ScriptLanguage = ScriptLanguage.JAVASCRIPT,
    val activePhase: ScriptPhase = ScriptPhase.POST_RESPONSE
)

val ScriptPhase.editorLabel: String
    get() = when (this) {
        ScriptPhase.PRE_REQUEST -> "Pre-request"
        ScriptPhase.POST_RESPONSE -> "Post-response / Tests"
        ScriptPhase.GLOBAL_RULE -> "Global rule"
    }

/**
 * Registry providing standard built-in quick code snippets for API Studio scripts.
 */
object ScriptSnippetRegistry {

    /**
     * Default list of built-in quick code snippets for JavaScript and Kotlin scripting.
     */
    val DEFAULT_SNIPPETS: List<ScriptSnippet> = listOf(
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
