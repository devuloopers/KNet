package com.devuloopers.knet.scriptengine.snippets

import com.devuloopers.knet.scriptengine.api.ScriptLanguage

data class ScriptSnippet(
    val id: String,
    val title: String,
    val description: String,
    val codeJs: String,
    val codeKotlin: String
)

object SnippetRegistry {

    val SNIPPETS = listOf(
        ScriptSnippet(
            id = "status_200",
            title = "Status code: 200",
            description = "Assert HTTP status code is 200 OK",
            codeJs = """pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});""",
            codeKotlin = """test("Status code is 200") {
    response.statusCode == 200
}"""
        ),
        ScriptSnippet(
            id = "response_time",
            title = "Response time < 500ms",
            description = "Assert response latency is below 500ms",
            codeJs = """pm.test("Response time is less than 500ms", function () {
    pm.expect(pm.response.responseTime).to.be.below(500);
});""",
            codeKotlin = """test("Response time is less than 500ms") {
    response.latencyMs < 500
}"""
        ),
        ScriptSnippet(
            id = "json_check",
            title = "JSON Value Check",
            description = "Assert JSON field value",
            codeJs = """pm.test("Check JSON property", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData.success).to.eql(true);
});""",
            codeKotlin = """test("Check JSON property") {
    response.body.contains("\"success\": true")
}"""
        ),
        ScriptSnippet(
            id = "set_env",
            title = "Set Environment Variable",
            description = "Save variable to environment",
            codeJs = """pm.environment.set("authToken", "secret_token_value");""",
            codeKotlin = """env["authToken"] = "secret_token_value""""
        ),
        ScriptSnippet(
            id = "uuid_gen",
            title = "Generate UUID",
            description = "Generate a random UUID header",
            codeJs = """pm.request.headers.add({ key: "X-Request-ID", value: crypto.uuid() });""",
            codeKotlin = """request.headers["X-Request-ID"] = crypto.uuid()"""
        ),
        ScriptSnippet(
            id = "header_check",
            title = "Header Content-Type",
            description = "Assert response header presence",
            codeJs = """pm.test("Header Content-Type exists", function () {
    pm.response.to.have.header("Content-Type");
});""",
            codeKotlin = """test("Header Content-Type exists") {
    response.headers.containsKey("Content-Type") || response.headers.containsKey("content-type")
}"""
        ),
        ScriptSnippet(
            id = "status_201",
            title = "Status code: 201 Created",
            description = "Assert HTTP status code is 201",
            codeJs = """pm.test("Status code is 201", function () {
    pm.response.to.have.status(201);
});""",
            codeKotlin = """test("Status code is 201") {
    response.statusCode == 201
}"""
        ),
        ScriptSnippet(
            id = "sha256_hash",
            title = "SHA-256 Signature",
            description = "Generate SHA-256 hash header",
            codeJs = """pm.request.headers.add({ key: "X-Signature", value: crypto.sha256(pm.request.body) });""",
            codeKotlin = """request.headers["X-Signature"] = crypto.sha256(request.body)"""
        )
    )


    fun getCode(snippet: ScriptSnippet, language: ScriptLanguage): String {
        return if (language == ScriptLanguage.JAVASCRIPT) snippet.codeJs else snippet.codeKotlin
    }
}
