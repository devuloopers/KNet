package com.devuloopers.knet.ui.desktop.workspace.explorer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.components.input.KNetSearchField

/**
 * Workspace search bar primitive wrapper for explorer sidebars.
 *
 * @param query Search query text.
 * @param onQueryChange Callback when query string changes.
 * @param modifier Layout modifier.
 */
@Composable
fun WorkspaceSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    KNetSearchField(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = "Filter collections & requests...",
        modifier = modifier
    )
}

