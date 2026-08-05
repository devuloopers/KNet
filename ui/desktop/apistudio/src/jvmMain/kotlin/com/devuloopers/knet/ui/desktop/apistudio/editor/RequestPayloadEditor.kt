package com.devuloopers.knet.ui.desktop.apistudio.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.keyvalue.KNetKeyValueEditor
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor

public enum class RequestSubTab(val label: String) {
    PARAMS("Params"),
    HEADERS("Headers (4)"),
    AUTH("Auth"),
    BODY("Body (JSON)"),
    COOKIES("Cookies"),
    SCRIPTS("Scripts")
}

/**
 * Request authoring payload editor with sub-tabs bar and KNetKeyValueEditor / CodeEditorView area.
 */
@Composable
public fun RequestPayloadEditor(
    bodyPayload: String,
    onBodyPayloadChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    var activeSubTab by remember { mutableStateOf(RequestSubTab.BODY) }
    var paramEntries by remember {
        mutableStateOf(
            listOf(
                KeyValueEntry("p1", "page", "1"),
                KeyValueEntry("p2", "sort", "desc")
            )
        )
    }
    var headerEntries by remember {
        mutableStateOf(
            listOf(
                KeyValueEntry("h1", "Content-Type", "application/json"),
                KeyValueEntry("h2", "Accept", "application/json"),
                KeyValueEntry("h3", "Authorization", "Bearer eyJhbGciOi..."),
                KeyValueEntry("h4", "User-Agent", "KNet/1.0.0")
            )
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Request Sub-Tabs Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RequestSubTab.entries.forEach { subTab ->
                val isSelected = subTab == activeSubTab
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { activeSubTab = subTab }
                        .handCursor()
                        .padding(top = 8.dp, bottom = 0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = subTab.label,
                        style = typography.bodyMedium.copy(
                            color = if (isSelected) themeColors.accent else themeColors.textPrimary.copy(alpha = 0.7f),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (isSelected) themeColors.accent else androidx.compose.ui.graphics.Color.Transparent)
                    )
                }
            }
        }

        HorizontalDivider(color = themeColors.border)

        // Sub-Tab Panel Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (activeSubTab) {
                RequestSubTab.PARAMS -> {
                    KNetKeyValueEditor(
                        entries = paramEntries,
                        onEntryChange = { idx, updated ->
                            paramEntries = paramEntries.toMutableList().apply { set(idx, updated) }
                        },
                        onAddEntry = {
                            paramEntries = paramEntries + KeyValueEntry("p_${System.currentTimeMillis()}", "", "")
                        },
                        onRemoveEntry = { idx ->
                            paramEntries = paramEntries.toMutableList().apply { removeAt(idx) }
                        },
                        modifier = Modifier.padding(spacing.md)
                    )
                }
                RequestSubTab.HEADERS -> {
                    KNetKeyValueEditor(
                        entries = headerEntries,
                        onEntryChange = { idx, updated ->
                            headerEntries = headerEntries.toMutableList().apply { set(idx, updated) }
                        },
                        onAddEntry = {
                            headerEntries = headerEntries + KeyValueEntry("h_${System.currentTimeMillis()}", "", "")
                        },
                        onRemoveEntry = { idx ->
                            headerEntries = headerEntries.toMutableList().apply { removeAt(idx) }
                        },
                        modifier = Modifier.padding(spacing.md)
                    )
                }
                RequestSubTab.BODY -> {
                    val defaultJson = """
                        {
                          "username": "dev_admin",
                          "email": "admin@knet.dev",
                          "password": "********",
                          "roles": [
                            "admin",
                            "user"
                          ],
                          "active": true
                        }
                    """.trimIndent()
                    val currentCode = bodyPayload.ifBlank { defaultJson }

                    KNetCodeEditor(
                        code = currentCode,
                        mode = EditorMode.Editable(
                            onCodeChange = onBodyPayloadChanged,
                            placeholder = "Enter JSON payload..."
                        ),
                        languageHint = "json",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${activeSubTab.label} options",
                            style = typography.bodyMedium.copy(color = themeColors.textMuted)
                        )
                    }
                }
            }
        }
    }
}
