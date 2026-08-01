package com.devuloopers.knet.ui.desktop.scripting.workspace

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.table.EditableKeyValueTable
import com.devuloopers.knet.ui.core.table.KeyValuePair
import com.devuloopers.knet.ui.core.theme.KNetColors

/**
 * Context explorer displaying the active request / response properties available to scripts.
 */
@Composable
public fun ContextExplorer(
    contextProperties: List<KeyValuePair>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .padding(8.dp)
    ) {
        Text(
            text = "Execution Context",
            color = KNetColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        EditableKeyValueTable(
            pairs = contextProperties,
            onPairChange = { _, _, _ -> },
            onPairDelete = {},
            onAddPair = {},
            modifier = Modifier.weight(1f)
        )
    }
}
