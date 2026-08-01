package com.devuloopers.knet.ui.desktop.apistudio.editor.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.table.EditableKeyValueTable
import com.devuloopers.knet.ui.core.table.KeyValuePair

/**
 * URL Query parameters editor tab composable.
 */
@Composable
public fun QueryTab(
    params: List<KeyValuePair>,
    onPairChange: (index: Int, key: String, value: String) -> Unit,
    onPairDelete: (index: Int) -> Unit,
    onAddPair: () -> Unit,
    modifier: Modifier = Modifier
) {
    EditableKeyValueTable(
        pairs = params,
        onPairChange = onPairChange,
        onPairDelete = onPairDelete,
        onAddPair = onAddPair,
        modifier = modifier
    )
}
