package com.devuloopers.knet.ui.core.components.keyvalue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.KNetCopyButton
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.checkbox.KNetCheckbox
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.input.KNetInputField
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Immutable data transfer object representing a key-value entry pair.
 *
 * @property id Unique entry identifier.
 * @property key String key or header name.
 * @property value Associated string value.
 * @property enabled Whether entry is active in request context.
 */
public data class KeyValueEntry(
    val id: String,
    val key: String,
    val value: String,
    val enabled: Boolean = true
)

/**
 * Domain-agnostic Key-Value editor composable table with headers and centered controls.
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
        // Table Header Row
        if (entries.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox spacer offset (24dp + 6dp padding)
                Spacer(modifier = Modifier.width(30.dp))
                Text(
                    text = "KEY",
                    style = typography.caption.copy(
                        color = themeColors.textMuted,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                Text(
                    text = "VALUE",
                    style = typography.caption.copy(
                        color = themeColors.textMuted,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                // Remove icon spacer offset (28dp)
                Spacer(modifier = Modifier.width(28.dp))
            }
        }

        // Key-Value Rows
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No parameters defined. Click 'Add Row' to start.",
                    style = typography.bodySmall.copy(color = themeColors.textMuted)
                )
            }
        } else {
            entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
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
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    )
                    KNetInputField(
                        value = entry.value,
                        onValueChange = { onEntryChange(index, entry.copy(value = it)) },
                        placeholder = "Value",
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    )
                    KNetIconButton(
                        onClick = { onRemoveEntry(index) },
                        icon = KNetIcons.Delete,
                        contentDescription = "Remove",
                        tint = themeColors.semantic.error
                    )
                }
            }
        }

        // "+ Add Row" Action Button
        KNetButton(
            onClick = onAddEntry,
            variant = ButtonVariant.Secondary,
            modifier = Modifier.padding(top = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = KNetIcons.Add,
                    contentDescription = "Add Row",
                    modifier = Modifier.size(14.dp),
                    tint = themeColors.textPrimary
                )
                Text("Add Row")
            }
        }
    }
}

/**
 * Domain-agnostic read-only Key-Value table viewer for server response headers, cookies, and metadata.
 * Displays key and value pairs cleanly with monospaced typography, per-row copy actions, and no editing controls.
 *
 * @param entries Read-only list of [KeyValueEntry] items to render.
 * @param modifier Composable layout modifier.
 * @param keyHeader Column header label for the key column (default: "HEADER NAME").
 * @param valueHeader Column header label for the value column (default: "VALUE").
 * @param emptyMessage Fallback text displayed when [entries] is empty.
 */
@Composable
public fun KNetReadOnlyKeyValueViewer(
    entries: List<KeyValueEntry>,
    modifier: Modifier = Modifier,
    keyHeader: String = "HEADER NAME",
    valueHeader: String = "VALUE",
    emptyMessage: String = "No data available."
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(modifier = modifier.fillMaxWidth()) {
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyMessage,
                    style = typography.bodySmall.copy(color = themeColors.textMuted)
                )
            }
        } else {
            // Table Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeColors.panelHeader)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = keyHeader,
                    style = typography.caption.copy(
                        color = themeColors.textMuted,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.weight(0.4f)
                )
                Text(
                    text = valueHeader,
                    style = typography.caption.copy(
                        color = themeColors.textMuted,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.weight(0.6f)
                )
            }

            HorizontalDivider(color = themeColors.border)

            // Read-Only Key-Value Rows
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(entries) { index, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (index % 2 == 1) themeColors.surfaceVariant.copy(alpha = 0.3f) else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.key,
                            style = typography.codeSmall.copy(
                                color = themeColors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.weight(0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.weight(0.6f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.value,
                                style = typography.codeSmall.copy(color = themeColors.textSecondary),
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            KNetCopyButton(textToCopy = entry.value)
                        }
                    }
                    HorizontalDivider(color = themeColors.border.copy(alpha = 0.4f))
                }
            }
        }
    }
}
