package com.devuloopers.knet.ui.desktop.codeeditor.language

import com.devuloopers.knet.ui.desktop.codeeditor.language.builtin.BuiltInEditorLanguages
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class EditorLanguageRegistryTest {
    @Test
    fun unknownIdentifierRemainsExtensibleCustomLanguage() {
        val language = CodeLanguage.fromId("yaml", customDisplayName = "YAML")

        assertIs<CodeLanguage.Custom>(language)
        assertEquals("yaml", language.id)
        assertEquals("YAML", language.displayName)
    }

    @Test
    fun customLanguageCanBeAddedWithoutChangingBuiltInRegistry() {
        val yaml = CodeLanguage.Custom("yaml", "YAML")
        val support = EditorLanguageSupport(language = yaml, aliases = setOf("yml"), mimeTypes = setOf("application/yaml"))
        val extended = BuiltInEditorLanguages.registry.with(listOf(support))

        assertSame(support, extended.resolve(yaml))
        assertSame(support, extended.find("yml"))
        assertSame(support, extended.findByMimeType("application/yaml; charset=utf-8"))
        assertEquals(null, BuiltInEditorLanguages.registry.find("yaml"))
    }

    @Test
    fun duplicateAliasesFailDuringComposition() {
        assertFailsWith<IllegalArgumentException> {
            EditorLanguageRegistry(
                listOf(
                    EditorLanguageSupport(CodeLanguage.Custom("first", "First"), aliases = setOf("shared")),
                    EditorLanguageSupport(CodeLanguage.Custom("second", "Second"), aliases = setOf("shared")),
                    EditorLanguageSupport(CodeLanguage.PLAIN)
                )
            )
        }
    }
}
