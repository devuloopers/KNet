package com.devuloopers.knet.ui.desktop.traffic.filter

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.layout.WidgetSearchBar

/**
 * Traffic search bar.
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
        placeholder = "Search live traffic...",
        modifier = modifier
    )
}
