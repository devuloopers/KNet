package com.devuloopers.knet.ui.core.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun WorkspaceLayout(
    modifier: Modifier = Modifier,
    toolbar: (@Composable () -> Unit)? = null,
    navigationRail: (@Composable () -> Unit)? = null,
    sidebar: (@Composable () -> Unit)? = null,
    detailPane: (@Composable () -> Unit)? = null,
    bottomPane: (@Composable () -> Unit)? = null,
    statusBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    KNetWorkspaceScaffold(
        modifier = modifier,
        toolbar = toolbar,
        navigationRail = navigationRail,
        sidebar = sidebar,
        detailPane = detailPane,
        bottomPane = bottomPane,
        statusBar = statusBar,
        content = content
    )
}
