package com.devuloopers.knet.ui.desktop.apistudio.editor.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.table.EditableKeyValueTable
import com.devuloopers.knet.ui.core.table.KeyValuePair

/**
 * Request Headers editor tab composable.
 */
@Composable
public fun HeadersTab(
    headers: List<KeyValuePair>,
    onPairChange: (index: Int, key: String, value: String) -> Unit,
    onPairDelete: (index: Int) -> Unit,
    onAddPair: () -> Unit,
    modifier: Modifier = Modifier
) {
    EditableKeyValueTable(
        pairs = headers,
        onPairChange = onPairChange,
        onPairDelete = onPairDelete,
        onAddPair = onAddPair,
        modifier = modifier
    )
}
