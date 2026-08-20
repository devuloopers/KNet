package com.devuloopers.knet.ui.desktop.codeeditor.api

import com.devuloopers.knet.ui.desktop.codeeditor.component.shouldShowFoldActionSlot
import com.devuloopers.knet.ui.desktop.codeeditor.command.EditorCommandId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CodeEditorConfigurationTest {
    @Test
    fun wordWrapKeepsContentInsideTheViewportByDefault() {
        assertTrue(CodeEditorConfiguration().isWordWrapEnabled)
    }

    @Test
    fun foldActionSlotDependsOnConfigurationAndLanguageCapabilityNotRuntimeRegions() {
        assertTrue(shouldShowFoldActionSlot(configured = true, foldingSupported = true))
        assertFalse(shouldShowFoldActionSlot(configured = false, foldingSupported = true))
        assertFalse(shouldShowFoldActionSlot(configured = true, foldingSupported = false))
    }

    @Test
    fun headerActionsRequireUniqueCommandIdentities() {
        val commandId = EditorCommandId.Custom("test.format")

        assertFailsWith<IllegalArgumentException> {
            CodeEditorHeaderConfiguration(
                actions = listOf(
                    CodeEditorHeaderAction(commandId = commandId, label = "Format"),
                    CodeEditorHeaderAction(commandId = commandId, label = "Format again")
                )
            )
        }
    }
}
