package com.devuloopers.knet.ui.desktop.codeeditor.command

import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.session.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorCommandDispatcherTest {
    @Test
    fun builtInCommandsMutateOnlyThroughSession() {
        val session = EditorSession("value")
        val dispatcher = EditorCommandDispatcher()

        dispatcher.dispatch(EditorCommand.MoveCaret(EditorPosition(0, 5)), session)
        dispatcher.dispatch(EditorCommand.InsertText("!"), session)
        dispatcher.dispatch(EditorCommand.SplitLine(EditorPosition(0, 3), "  "), session)

        assertEquals("val\n  ue!", session.snapshot.text())
        assertTrue(dispatcher.dispatch(EditorCommand.Undo, session))
        assertEquals("value!", session.snapshot.text())
    }

    @Test
    fun customCommandsAreAdditive() {
        val commandId = EditorCommandId.Custom("test.insert")
        val dispatcher = EditorCommandDispatcher(
            extensionHandlers = listOf(
                EditorCommandHandler { command, session ->
                    if (command == EditorCommand.Custom(commandId)) {
                        session.insert("custom")
                        true
                    } else {
                        false
                    }
                }
            )
        )
        val session = EditorSession()

        assertTrue(dispatcher.dispatch(EditorCommand.Custom(commandId), session))
        assertEquals("custom", session.snapshot.text())
        assertFalse(dispatcher.dispatch(EditorCommand.Custom(EditorCommandId.Custom("test.unknown")), session))
    }
}
