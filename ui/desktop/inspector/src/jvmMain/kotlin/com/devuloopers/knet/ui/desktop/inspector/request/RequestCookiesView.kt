package com.devuloopers.knet.ui.desktop.inspector.request

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.table.EditableKeyValueTable
import com.devuloopers.knet.ui.core.table.KeyValuePair

/**
 * Request cookies table view.
 */
@Composable
public fun RequestCookiesView(
    cookies: List<KeyValuePair>,
    modifier: Modifier = Modifier
) {
    EditableKeyValueTable(
        pairs = cookies,
        onPairChange = { _, _, _ -> },
        onPairDelete = {},
        onAddPair = {},
        modifier = modifier
    )
}
