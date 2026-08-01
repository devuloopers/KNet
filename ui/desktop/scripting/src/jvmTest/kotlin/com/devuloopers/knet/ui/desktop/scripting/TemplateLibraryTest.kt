package com.devuloopers.knet.ui.desktop.scripting

import com.devuloopers.knet.ui.desktop.scripting.model.ScriptTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests verifying TemplateLibrary item attributes.
 */
class TemplateLibraryTest {

    @Test
    fun `ScriptTemplate values match constructor fields`() {
        val template = ScriptTemplate(
            id = "jwt_auth",
            name = "JWT Authorizer",
            description = "Signs a JWT assertion header",
            code = "function signJwt() {}"
        )
        assertEquals("jwt_auth", template.id)
        assertEquals("JWT Authorizer", template.name)
        assertEquals("function signJwt() {}", template.code)
    }
}
