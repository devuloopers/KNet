package com.devuloopers.knet.ui.desktop.traffic.filter

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.layout.WidgetSearchBar

/**
 * Domain filter input bar.
 */
@Composable
public fun DomainFilter(
    domain: String,
    onDomainChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    WidgetSearchBar(
        query = domain,
        onQueryChange = onDomainChanged,
        placeholder = "Filter by host...",
        modifier = modifier
    )
}
