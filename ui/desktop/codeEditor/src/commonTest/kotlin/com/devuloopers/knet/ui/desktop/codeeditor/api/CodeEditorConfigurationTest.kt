package com.devuloopers.knet.ui.desktop.codeeditor.api

import kotlin.test.Test
import kotlin.test.assertTrue

class CodeEditorConfigurationTest {
    @Test
    fun wordWrapKeepsContentInsideTheViewportByDefault() {
        assertTrue(CodeEditorConfiguration().isWordWrapEnabled)
    }
}
