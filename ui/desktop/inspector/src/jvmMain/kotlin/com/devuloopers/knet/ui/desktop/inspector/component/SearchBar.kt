package com.devuloopers.knet.ui.desktop.inspector.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.layout.WidgetSearchBar

/**
 * Inspector search bar primitive.
 */
@Composable
public fun SearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    WidgetSearchBar(
        query = query,
        onQueryChange = onQueryChanged,
        placeholder = "Search in headers or payload...",
        modifier = modifier
    )
}
