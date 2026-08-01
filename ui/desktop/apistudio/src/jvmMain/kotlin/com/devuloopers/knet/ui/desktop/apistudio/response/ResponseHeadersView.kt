package com.devuloopers.knet.ui.desktop.apistudio.response

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.table.EditableKeyValueTable
import com.devuloopers.knet.ui.core.table.KeyValuePair

/**
 * Response headers key-value table view composable.
 */
@Composable
public fun ResponseHeadersView(
    headers: Map<String, String>,
    modifier: Modifier = Modifier
) {
    val pairs = headers.map { KeyValuePair(it.key, it.value) }
    EditableKeyValueTable(
        pairs = pairs,
        onPairChange = { _, _, _ -> },
        onPairDelete = {},
        onAddPair = {},
        modifier = modifier
    )
}
