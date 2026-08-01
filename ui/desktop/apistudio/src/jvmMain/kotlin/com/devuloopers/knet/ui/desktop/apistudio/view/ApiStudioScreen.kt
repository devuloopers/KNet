package com.devuloopers.knet.ui.desktop.apistudio.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.apistudio.editor.RequestEditor
import com.devuloopers.knet.ui.desktop.apistudio.editor.RequestTabBar
import com.devuloopers.knet.ui.desktop.apistudio.response.ResponseViewer
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import com.devuloopers.knet.ui.core.layout.SplitLayout

/**
 * Top-level API Studio Screen composable hosting request tab bar, request authoring editor, and response preview viewer.
 *
 * @param viewModel ApiStudioViewModel managing UDF state.
 * @param modifier Layout modifier.
 */
@Composable
public fun ApiStudioScreen(
    viewModel: ApiStudioViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.BackgroundDark)
    ) {
        RequestTabBar(
            tabs = state.tabs,
            activeTabId = state.activeTabId,
            onTabSelected = { viewModel.selectTab(it) },
            onTabClosed = { viewModel.closeTab(it) },
            onNewTabClicked = { viewModel.openNewTab() }
        )

        SplitLayout(
            leftContent = {
                RequestEditor(
                    state = state.editorState,
                    executionState = state.executionState,
                    selectedEnvironment = state.selectedEnvironment,
                    onUrlChanged = { viewModel.updateUrl(it) },
                    onMethodChanged = { viewModel.updateMethod(it) },
                    onEnvironmentSelected = { viewModel.selectEnvironment(it) },
                    onSend = { viewModel.executeRequest() },
                    onCancel = {}
                )
            },
            rightContent = {
                ResponseViewer(presentation = state.responsePresentation)
            }
        )
    }
}
