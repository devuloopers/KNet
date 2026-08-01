package com.devuloopers.knet.ui.desktop.inspector.request

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.table.EditableKeyValueTable
import com.devuloopers.knet.ui.core.table.KeyValuePair

/**
 * Request headers table view.
 */
@Composable
public fun RequestHeadersView(
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
