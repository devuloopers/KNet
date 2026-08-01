package com.devuloopers.knet.ui.desktop.inspector.response

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.table.EditableKeyValueTable
import com.devuloopers.knet.ui.core.table.KeyValuePair

/**
 * Response headers table view.
 */
@Composable
public fun ResponseHeadersView(
    headers: List<KeyValuePair>,
    modifier: Modifier = Modifier
) {
    EditableKeyValueTable(
        pairs = headers,
        onPairChange = { _, _, _ -> },
        onPairDelete = {},
        onAddPair = {},
        modifier = modifier
    )
}
