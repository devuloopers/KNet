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
import com.devuloopers.knet.ui.core.components.keyvalue.KNetKeyValueEditor
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Context explorer displaying the active request / response properties available to scripts.
 */
@Composable
public fun ContextExplorer(
    contextProperties: List<KeyValueEntry>,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .padding(8.dp)
    ) {
        Text(
            text = "Execution Context",
            style = typography.caption.copy(
                color = themeColors.textPrimary,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        KNetKeyValueEditor(
            entries = contextProperties,
            onEntryChange = { _, _ -> },
            onAddEntry = {},
            onRemoveEntry = {},
            modifier = Modifier.weight(1f)
        )
    }
}

