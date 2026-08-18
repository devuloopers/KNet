package com.devuloopers.knet.ui.core.components.keyvalue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.KNetCopyButton
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.checkbox.KNetCheckbox
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.divider.VerticalDivider
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
data class KeyValueEntry(
    val id: String,
    val key: String,
    val value: String,
    val enabled: Boolean = true
)

/**
 * Domain-agnostic Key-Value editor composable table with panelHeader styling,
 * alternating row colors, and inline editing controls.
 *
 * @param entries Interactive list of [KeyValueEntry] items to render and edit.
 * @param onEntryChange Callback triggered when an entry's key, value, or enabled status changes.
 * @param onAddEntry Callback triggered when user clicks '+ Add Row'.
 * @param onRemoveEntry Callback triggered when user clicks delete icon for a row.
 * @param modifier Composable layout modifier.
 * @param keyHeader Column header label for key column (default: "KEY").
 * @param valueHeader Column header label for value column (default: "VALUE").
 * @param emptyMessage Message displayed when entries list is empty.
 * @param addLabel Label text displayed on add button (default: "Add Row").
 */
@Composable
fun KNetKeyValueEditor(
    entries: List<KeyValueEntry>,
    onEntryChange: (index: Int, updated: KeyValueEntry) -> Unit,
    onAddEntry: () -> Unit,
    onRemoveEntry: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    keyHeader: String = "KEY",
    valueHeader: String = "VALUE",
    emptyMessage: String = "No entries defined. Click 'Add Row' to start.",
    addLabel: String = "Add Row"
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(modifier = modifier.fillMaxWidth()) {
        // Table Header Row matching panelHeader design with cell borders
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(themeColors.panelHeader)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(36.dp),
                contentAlignment = Alignment.Center
            ) {
                // Header checkbox column spacer
            }
            VerticalDivider(color = themeColors.border, modifier = Modifier.height(16.dp))
            Text(
                text = keyHeader,
                style = typography.caption.copy(
                    color = themeColors.textMuted,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.weight(0.4f).padding(start = 8.dp)
            )
            VerticalDivider(color = themeColors.border, modifier = Modifier.height(16.dp))
            Text(
                text = valueHeader,
                style = typography.caption.copy(
                    color = themeColors.textMuted,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.weight(0.6f).padding(start = 8.dp)
            )
            VerticalDivider(color = themeColors.border, modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier.width(36.dp),
                contentAlignment = Alignment.Center
            ) {
                // Header action column spacer
            }
        }

        HorizontalDivider(color = themeColors.border)

        // Key-Value Rows with Grid Cell Borders & Seamless Inputs
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyMessage,
                    style = typography.bodySmall.copy(color = themeColors.textMuted)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
            ) {
                itemsIndexed(entries) { index, entry ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (index % 2 == 1) themeColors.surfaceVariant.copy(alpha = 0.3f) else Color.Transparent)
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.width(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                KNetCheckbox(
                                    checked = entry.enabled,
                                    onCheckedChange = { onEntryChange(index, entry.copy(enabled = it)) }
                                )
                            }
                            VerticalDivider(color = themeColors.border.copy(alpha = 0.3f), modifier = Modifier.height(28.dp))
                            Box(
                                modifier = Modifier
                                    .weight(0.4f)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (entry.key.isEmpty()) {
                                    Text(
                                        text = "Key",
                                        style = typography.codeSmall.copy(
                                            color = themeColors.textMuted,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                                BasicTextField(
                                    value = entry.key,
                                    onValueChange = { onEntryChange(index, entry.copy(key = it)) },
                                    textStyle = typography.codeSmall.copy(
                                        color = if (entry.enabled) themeColors.textPrimary else themeColors.textMuted,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    cursorBrush = SolidColor(themeColors.textPrimary),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            VerticalDivider(color = themeColors.border.copy(alpha = 0.3f), modifier = Modifier.height(28.dp))
                            Box(
                                modifier = Modifier
                                    .weight(0.6f)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (entry.value.isEmpty()) {
                                    Text(
                                        text = "Value",
                                        style = typography.codeSmall.copy(
                                            color = themeColors.textMuted
                                        )
                                    )
                                }
                                BasicTextField(
                                    value = entry.value,
                                    onValueChange = { onEntryChange(index, entry.copy(value = it)) },
                                    textStyle = typography.codeSmall.copy(
                                        color = if (entry.enabled) themeColors.textSecondary else themeColors.textMuted
                                    ),
                                    cursorBrush = SolidColor(themeColors.textPrimary),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            VerticalDivider(color = themeColors.border.copy(alpha = 0.3f), modifier = Modifier.height(28.dp))
                            Box(
                                modifier = Modifier.width(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                KNetIconButton(
                                    onClick = { onRemoveEntry(index) },
                                    icon = KNetIcons.Delete,
                                    contentDescription = "Remove",
                                    tint = themeColors.semantic.error
                                )
                            }
                        }
                        HorizontalDivider(color = themeColors.border.copy(alpha = 0.4f))
                    }
                }
            }
        }

        // "+ Add Row" Action Button
        KNetButton(
            onClick = onAddEntry,
            variant = ButtonVariant.Secondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = KNetIcons.Add,
                    contentDescription = addLabel,
                    modifier = Modifier.size(14.dp),
                    tint = themeColors.textPrimary
                )
                Text(addLabel)
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
 * @param allowMultiLine True to allow long values (like User-Agent or tokens) to wrap vertically across multiple lines.
 */
@Composable
fun KNetReadOnlyKeyValueViewer(
    entries: List<KeyValueEntry>,
    modifier: Modifier = Modifier,
    keyHeader: String = "HEADER NAME",
    valueHeader: String = "VALUE",
    emptyMessage: String = "No data available.",
    allowMultiLine: Boolean = true
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(modifier = modifier.fillMaxWidth()) {
        if (entries.isEmpty()) {
            com.devuloopers.knet.ui.core.components.placeholder.KNetEmptyStatePlaceholder(
                title = "No Items Available",
                subtitle = emptyMessage,
                modifier = Modifier.fillMaxSize()
            )
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
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = if (allowMultiLine) Alignment.Top else Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.key,
                            style = typography.codeSmall.copy(
                                color = themeColors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.weight(0.4f),
                            maxLines = if (allowMultiLine) Int.MAX_VALUE else 1,
                            overflow = if (allowMultiLine) TextOverflow.Clip else TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.weight(0.6f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = if (allowMultiLine) Alignment.Top else Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.value,
                                style = typography.codeSmall.copy(color = themeColors.textSecondary),
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                maxLines = if (allowMultiLine) Int.MAX_VALUE else 1,
                                overflow = if (allowMultiLine) TextOverflow.Clip else TextOverflow.Ellipsis
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
