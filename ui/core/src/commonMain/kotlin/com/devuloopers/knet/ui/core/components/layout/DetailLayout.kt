package com.devuloopers.knet.ui.core.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DetailLayout(
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    KNetDetailPane(
        modifier = modifier,
        title = title,
        actions = actions,
        content = content
    )
}
