package com.devuloopers.knet.ui.core.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
public fun BottomPaneLayout(
    modifier: Modifier = Modifier,
    tabs: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    KNetBottomPane(
        modifier = modifier,
        tabs = tabs,
        actions = actions,
        content = content
    )
}
