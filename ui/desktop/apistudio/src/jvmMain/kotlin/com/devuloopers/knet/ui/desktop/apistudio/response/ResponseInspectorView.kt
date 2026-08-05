package com.devuloopers.knet.ui.desktop.apistudio.response

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.KNetCopyButton
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.divider.VerticalDivider
import com.devuloopers.knet.ui.core.components.keyvalue.KNetKeyValueEditor
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.apistudio.theme.ApiStudioColors

public enum class ResponseSubTab(val label: String) {
    BODY("Body"),
    HEADERS("Headers (8)"),
    COOKIES("Cookies (2)")
}

/**
 * Right-pane Response Inspector component displaying response status, metrics, sub-tabs, and payload.
 */
@Composable
public fun ResponseInspectorView(
    statusCode: Int = 200,
    statusText: String = "OK",
    durationMs: Long = 124L,
    sizeBytes: Long = 4966L,
    responseBody: String = "",
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    var activeSubTab by remember { mutableStateOf(ResponseSubTab.BODY) }

    val formattedSize = remember(sizeBytes) {
        val kb = sizeBytes / 1024.0
        "${(kb * 100).toInt() / 100.0} KB"
    }

    val displayBody = remember(responseBody) {
        responseBody.ifBlank {
            """
            {
              "status": "success",
              "data": {
                "id": "usr_98a7f6c5e4",
                "username": "dev_admin",
                "created_at": "2023-10-27T14:32:11Z",
                "metadata": {
                  "last_login": null,
                  "login_count": 0
                }
              }
            }
            """.trimIndent()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.surface)
    ) {
        // 1. Response Summary Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(65.dp)
                .padding(horizontal = spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Pill
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = KNetIcons.Check,
                        contentDescription = "Success Status",
                        modifier = Modifier.size(20.dp),
                        tint = ApiStudioColors.GetText
                    )
                    Text(
                        text = "$statusCode $statusText",
                        style = typography.titleSmall.copy(
                            color = ApiStudioColors.GetText,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                VerticalDivider(color = themeColors.border, modifier = Modifier.height(16.dp))

                // Time & Size Metrics
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Time:",
                            style = typography.caption.copy(color = themeColors.textSecondary)
                        )
                        Text(
                            text = "$durationMs ms",
                            style = typography.codeSmall.copy(color = themeColors.textPrimary)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Size:",
                            style = typography.caption.copy(color = themeColors.textSecondary)
                        )
                        Text(
                            text = formattedSize,
                            style = typography.codeSmall.copy(color = themeColors.textPrimary)
                        )
                    }
                }
            }

            // Quick Actions: Copy Response & Download
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KNetCopyButton(textToCopy = displayBody)
                KNetIconButton(
                    onClick = {},
                    icon = KNetIcons.Download,
                    contentDescription = "Download Response",
                    tint = themeColors.textSecondary
                )
            }
        }

        HorizontalDivider(color = themeColors.border)

        // 2. Response Sub-Tabs Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ResponseSubTab.entries.forEach { subTab ->
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

        // 3. Response Content Viewer
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (activeSubTab) {
                ResponseSubTab.BODY -> {
                    KNetCodeEditor(
                        code = displayBody,
                        mode = EditorMode.ReadOnly,
                        languageHint = "json",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ResponseSubTab.HEADERS -> {
                    KNetKeyValueEditor(
                        entries = listOf(
                            KeyValueEntry("r1", "Content-Type", "application/json; charset=utf-8"),
                            KeyValueEntry("r2", "Content-Length", "$sizeBytes"),
                            KeyValueEntry("r3", "Server", "KNet/1.0 Netty"),
                            KeyValueEntry("r4", "Date", "Tue, 04 Aug 2026 11:22:00 GMT")
                        ),
                        onEntryChange = { _, _ -> },
                        onAddEntry = {},
                        onRemoveEntry = {},
                        modifier = Modifier.padding(spacing.md)
                    )
                }
                ResponseSubTab.COOKIES -> {
                    KNetKeyValueEditor(
                        entries = listOf(
                            KeyValueEntry("c1", "sessionId", "s_98a7f6c5e4; Path=/; Secure; HttpOnly"),
                            KeyValueEntry("c2", "theme", "dark; Path=/")
                        ),
                        onEntryChange = { _, _ -> },
                        onAddEntry = {},
                        onRemoveEntry = {},
                        modifier = Modifier.padding(spacing.md)
                    )
                }
            }
        }
    }
}
