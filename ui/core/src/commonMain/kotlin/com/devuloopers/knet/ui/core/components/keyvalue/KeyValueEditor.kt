package com.devuloopers.knet.ui.core.components.keyvalue

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.checkbox.KNetCheckbox
import com.devuloopers.knet.ui.core.components.input.KNetInputField
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

public data class KeyValueEntry(
    val id: String,
    val key: String,
    val value: String,
    val enabled: Boolean = true
)

/**
 * Domain-agnostic Key-Value editor composable table.
 */
@Composable
public fun KNetKeyValueEditor(
    entries: List<KeyValueEntry>,
    onEntryChange: (index: Int, updated: KeyValueEntry) -> Unit,
    onAddEntry: () -> Unit,
    onRemoveEntry: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(modifier = modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KNetCheckbox(
                    checked = entry.enabled,
                    onCheckedChange = { onEntryChange(index, entry.copy(enabled = it)) },
                    modifier = Modifier.padding(end = 6.dp)
                )
                KNetInputField(
                    value = entry.key,
                    onValueChange = { onEntryChange(index, entry.copy(key = it)) },
                    placeholder = "Key",
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                )
                KNetInputField(
                    value = entry.value,
                    onValueChange = { onEntryChange(index, entry.copy(value = it)) },
                    placeholder = "Value",
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                )
                KNetIconButton(
                    onClick = { onRemoveEntry(index) },
                    icon = KNetIcons.Delete,
                    contentDescription = "Remove",
                    tint = themeColors.semantic.error
                )
            }
        }

        KNetButton(
            onClick = onAddEntry,
            variant = ButtonVariant.Secondary,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Add Row")
        }
    }
}
