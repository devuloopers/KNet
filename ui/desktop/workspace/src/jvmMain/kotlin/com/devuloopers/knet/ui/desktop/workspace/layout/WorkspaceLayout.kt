package com.devuloopers.knet.ui.desktop.workspace.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.workspace.explorer.CollectionsExplorer
import com.devuloopers.knet.ui.desktop.workspace.explorer.EnvironmentExplorer
import com.devuloopers.knet.ui.desktop.workspace.explorer.HistoryExplorer
import com.devuloopers.knet.ui.desktop.workspace.model.ExplorerType
import com.devuloopers.knet.ui.desktop.workspace.model.WorkspaceIntent
import com.devuloopers.knet.ui.desktop.workspace.model.WorkspaceState
import com.devuloopers.knet.ui.desktop.workspace.viewmodel.WorkspaceViewModel

/**
 * Primary Workspace Layout container composable.
 *
 * Hosts the Explorer sidebar on the left and a swappable content area on the right.
 *
 * @param viewModel WorkspaceViewModel managing UDF layout state.
 * @param modifier Layout modifier.
 */
@Composable
fun WorkspaceLayout(
    viewModel: WorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is WorkspaceState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize().background(themeColors.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = themeColors.accent)
            }
        }

        is WorkspaceState.Success -> {
            Row(
                modifier = modifier
                    .fillMaxSize()
                    .background(themeColors.background)
            ) {
                // Explorer Sidebar
                Box(
                    modifier = Modifier
                        .width(state.layout.explorerWidthDp.dp)
                        .fillMaxHeight()
                        .background(themeColors.surface)
                ) {
                    when (state.activeExplorer) {
                        ExplorerType.COLLECTIONS -> CollectionsExplorer(
                            searchQuery = state.searchQuery,
                            expandedNodeIds = state.expandedNodes,
                            onNodeToggle = { viewModel.processIntent(WorkspaceIntent.ToggleNode(it)) }
                        )

                        ExplorerType.ENVIRONMENTS -> EnvironmentExplorer()
                        ExplorerType.HISTORY -> HistoryExplorer()
                    }
                }

                WorkspaceSplitter()

                // Main Feature Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(themeColors.background)
                )
            }
        }
    }
}

