package com.devuloopers.knet.ui.core.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SidebarLayout(
    modifier: Modifier = Modifier,
    title: String? = null,
    headerActions: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    KNetSidebar(
        modifier = modifier,
        title = title,
        headerActions = headerActions,
        footer = footer,
        content = content
    )
}
