package com.devuloopers.knet.ui.desktop.workspace.explorer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.feedback.EmptyState

/**
 * Collections explorer view managing API Postman collections, folders, and request items.
 *
 * @param searchQuery Active search query filter.
 * @param expandedNodeIds Set of expanded node IDs.
 * @param onNodeToggle Callback when node expand arrow is clicked.
 * @param modifier Layout modifier.
 */
@Composable
fun CollectionsExplorer(
    searchQuery: String = "",
    expandedNodeIds: Set<String> = emptySet(),
    onNodeToggle: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(6.dp)) {
        WorkspaceSearch(
            query = searchQuery,
            onQueryChange = {},
            modifier = Modifier.padding(bottom = 6.dp)
        )
        EmptyState(
            title = "No Collections",
            description = "Import or create a Postman API collection."
        )
    }
}
