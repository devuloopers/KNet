package com.devuloopers.knet.ui.desktop.codeeditor

import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Regression test verifying public API accessibility for `:ui:desktop:codeEditor`.
 */
class MigrationRegressionTest {

    @Test
    fun `verify public API and EditorMode loadability`() {
        val mode = EditorMode.ReadOnly
        assertNotNull(mode)
        val editorRef = ::KNetCodeEditor
        assertNotNull(editorRef)
    }
}
