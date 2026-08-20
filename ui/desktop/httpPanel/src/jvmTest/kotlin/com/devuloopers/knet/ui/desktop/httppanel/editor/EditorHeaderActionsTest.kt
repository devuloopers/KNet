package com.devuloopers.knet.ui.desktop.httppanel.editor

import com.devuloopers.knet.ui.desktop.codeeditor.command.EditorCommand
import com.devuloopers.knet.ui.desktop.codeeditor.command.EditorCommandId
import kotlin.test.Test
import kotlin.test.assertEquals

class EditorHeaderActionsTest {
    @Test
    fun prettifyHandlerAcceptsOnlyItsNamespacedCommand() {
        var invocationCount = 0

        dispatchPrettifyEditorHeaderAction(EditorCommand.Custom(prettifyEditorCommandId)) {
            invocationCount += 1
        }
        dispatchPrettifyEditorHeaderAction(
            command = EditorCommand.Custom(EditorCommandId.Custom("another-feature.validate")),
            executePrettify = { invocationCount += 1 }
        )

        assertEquals(1, invocationCount)
    }
}
