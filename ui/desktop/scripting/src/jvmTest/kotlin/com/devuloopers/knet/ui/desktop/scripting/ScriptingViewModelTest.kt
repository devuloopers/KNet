package com.devuloopers.knet.ui.desktop.scripting

import com.devuloopers.knet.ui.desktop.scripting.model.ScriptingIntent
import com.devuloopers.knet.ui.desktop.scripting.model.TrafficScript
import com.devuloopers.knet.ui.desktop.scripting.viewmodel.ScriptingViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests verifying ScriptingViewModel UDF state transitions.
 */
class ScriptingViewModelTest {

    @Test
    fun `LoadScript updates active script state`() {
        val viewModel = ScriptingViewModel()
        val script = TrafficScript(id = "1", name = "init.js", code = "const x = 1;")
        
        viewModel.processIntent(ScriptingIntent.LoadScript(script))
        assertEquals(script, viewModel.uiState.value.activeScript)
        assertTrue(viewModel.uiState.value.openScripts.contains(script))
    }

    @Test
    fun `UpdateCode marks active script dirty`() {
        val viewModel = ScriptingViewModel()
        val script = TrafficScript(id = "1", name = "init.js", code = "const x = 1;")
        
        viewModel.processIntent(ScriptingIntent.LoadScript(script))
        viewModel.processIntent(ScriptingIntent.UpdateCode("const x = 2;"))
        assertEquals("const x = 2;", viewModel.uiState.value.activeScript?.code)
        assertEquals(viewModel.uiState.value.activeScript?.isDirty, true)
    }

    @Test
    fun `SaveScript marks active script clean`() {
        val viewModel = ScriptingViewModel()
        val script = TrafficScript(id = "1", name = "init.js", code = "const x = 1;")
        
        viewModel.processIntent(ScriptingIntent.LoadScript(script))
        viewModel.processIntent(ScriptingIntent.UpdateCode("const x = 2;"))
        viewModel.processIntent(ScriptingIntent.SaveScript)
        assertEquals(viewModel.uiState.value.activeScript?.isDirty, false)
    }
}
