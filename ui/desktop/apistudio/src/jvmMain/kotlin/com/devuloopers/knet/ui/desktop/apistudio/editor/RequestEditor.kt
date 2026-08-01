package com.devuloopers.knet.ui.desktop.apistudio.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.apistudio.editor.tabs.AuthTab
import com.devuloopers.knet.ui.desktop.apistudio.editor.tabs.BodyTab
import com.devuloopers.knet.ui.desktop.apistudio.editor.tabs.CookiesTab
import com.devuloopers.knet.ui.desktop.apistudio.editor.tabs.HeadersTab
import com.devuloopers.knet.ui.desktop.apistudio.editor.tabs.QueryTab
import com.devuloopers.knet.ui.desktop.apistudio.editor.tabs.ScriptTab
import com.devuloopers.knet.ui.desktop.apistudio.editor.tabs.TestsTab
import com.devuloopers.knet.ui.desktop.apistudio.model.ExecutionState
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestEditorState

/**
 * Main HTTP Request authoring editor container hosting RequestToolbar, Tab bar, and detail tabs (Query, Headers, Cookies, Auth, Body, Script, Tests).
 *
 * @param state Request editor state.
 * @param executionState Current execution status.
 * @param selectedEnvironment Currently selected environment name.
 * @param onUrlChanged Callback when URL changes.
 * @param onMethodChanged Callback when method changes.
 * @param onEnvironmentSelected Callback when environment changes.
 * @param onSend Callback when Send button is clicked.
 * @param onCancel Callback when Cancel button is clicked.
 * @param modifier Layout modifier.
 */
@Composable
public fun RequestEditor(
    state: RequestEditorState,
    executionState: ExecutionState,
    selectedEnvironment: String,
    onUrlChanged: (String) -> Unit,
    onMethodChanged: (String) -> Unit,
    onEnvironmentSelected: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSubTab by remember { mutableStateOf("Params") }

    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        RequestToolbar(
            url = state.url,
            method = state.method,
            selectedEnvironment = selectedEnvironment,
            executionState = executionState,
            onUrlChanged = onUrlChanged,
            onMethodChanged = onMethodChanged,
            onEnvironmentSelected = onEnvironmentSelected,
            onSend = onSend,
            onCancel = onCancel,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Sub-tabs switcher bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KNetColors.BackgroundDark)
                .padding(vertical = 4.dp)
        ) {
            val subTabs = listOf("Params", "Headers", "Cookies", "Auth", "Body", "Scripts", "Tests")
            subTabs.forEach { tabName ->
                val isSelected = tabName == activeSubTab
                Text(
                    text = tabName,
                    color = if (isSelected) KNetColors.ActiveBlue else KNetColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable { activeSubTab = tabName }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Sub-tab content view
        when (activeSubTab) {
            "Params" -> QueryTab(params = state.queryParams, onPairChange = { _, _, _ -> }, onPairDelete = {}, onAddPair = {})
            "Headers" -> HeadersTab(headers = state.headers, onPairChange = { _, _, _ -> }, onPairDelete = {}, onAddPair = {})
            "Cookies" -> CookiesTab(cookies = state.cookies, onPairChange = { _, _, _ -> }, onPairDelete = {}, onAddPair = {})
            "Auth" -> AuthTab(authType = state.authType, authToken = state.authToken, onAuthTypeChanged = {}, onAuthTokenChanged = {})
            "Body" -> BodyTab(bodyType = state.bodyType, bodyPayload = state.bodyPayload, onBodyTypeChanged = {}, onBodyPayloadChanged = {})
            "Scripts" -> ScriptTab(script = state.preRequestScript, onScriptChanged = {})
            "Tests" -> TestsTab(testScript = state.testScript, onTestScriptChanged = {})
        }
    }
}
