package com.devuloopers.knet.ui.desktop.inspector.response

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.table.EditableKeyValueTable
import com.devuloopers.knet.ui.core.table.KeyValuePair

/**
 * Response trailers table view.
 */
@Composable
public fun ResponseTrailersView(
    trailers: List<KeyValuePair>,
    modifier: Modifier = Modifier
) {
    EditableKeyValueTable(
        pairs = trailers,
        onPairChange = { _, _, _ -> },
        onPairDelete = {},
        onAddPair = {},
        modifier = modifier
    )
}
