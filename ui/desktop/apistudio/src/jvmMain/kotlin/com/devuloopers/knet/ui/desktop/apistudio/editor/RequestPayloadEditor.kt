package com.devuloopers.knet.ui.desktop.apistudio.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.keyvalue.KNetKeyValueEditor
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.model.BodyMode
import com.devuloopers.knet.ui.desktop.apistudio.model.BodyState
import com.devuloopers.knet.ui.desktop.apistudio.model.ScriptState

public enum class RequestSubTab {
    PARAMS,
    HEADERS,
    AUTH,
    BODY,
    COOKIES,
    SCRIPTS
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
    var cookieEntries by remember {
        mutableStateOf(
            listOf(
                KeyValueEntry("c1", "session_id", "s%3A91283hsd89234jsdf89")
            )
        )
    }
    var authState by remember { mutableStateOf(com.devuloopers.knet.ui.desktop.apistudio.model.AuthState()) }
    var bodyState by remember {
        mutableStateOf(
            BodyState(
                mode = BodyMode.JSON,
                payloadText = bodyPayload
            )
        )
    }
    var scriptState by remember {
        mutableStateOf(
            ScriptState(
                testScript = """pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});""".trimIndent()
            )
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Request Sub-Tabs Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RequestSubTab.entries.forEach { subTab ->
                val isSelected = subTab == activeSubTab
                // Resolve dynamic label — Body tab label reflects active body mode
                val tabLabel = when (subTab) {
                    RequestSubTab.PARAMS -> "Params"
                    RequestSubTab.HEADERS -> "Headers (4)"
                    RequestSubTab.AUTH -> "Auth"
                    RequestSubTab.BODY -> bodyState.mode.tabLabel
                    RequestSubTab.COOKIES -> "Cookies"
                    RequestSubTab.SCRIPTS -> "Scripts"
                }
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { activeSubTab = subTab }
                        .handCursor()
                        .padding(top = 8.dp, bottom = 0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tabLabel,
                        style = typography.bodyMedium.copy(
                            color = if (isSelected) themeColors.accent else themeColors.textPrimary.copy(alpha = 0.7f),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
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
                    BodyEditorView(
                        state = bodyState,
                        onStateChange = { updated ->
                            bodyState = updated
                            // Propagate payload text changes to parent callback for text-based modes
                            if (updated.mode != BodyMode.FORM_DATA && updated.mode != BodyMode.X_WWW_FORM_URLENCODED) {
                                onBodyPayloadChanged(updated.payloadText)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                RequestSubTab.AUTH -> {
                    AuthEditorView(
                        state = authState,
                        onStateChange = { authState = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                RequestSubTab.COOKIES -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(spacing.md)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = spacing.md)
                        ) {
                            Icon(
                                imageVector = KNetIcons.Info,
                                contentDescription = "Info",
                                modifier = Modifier.size(14.dp),
                                tint = themeColors.textMuted
                            )
                            Text(
                                text = "Cookies configured here are automatically formatted into the 'Cookie' header when sending the request.",
                                style = typography.caption.copy(color = themeColors.textMuted)
                            )
                        }

                        KNetKeyValueEditor(
                            entries = cookieEntries,
                            onEntryChange = { idx, updated ->
                                cookieEntries = cookieEntries.toMutableList().apply { set(idx, updated) }
                            },
                            onAddEntry = {
                                cookieEntries = cookieEntries + KeyValueEntry("c_${System.currentTimeMillis()}", "", "")
                            },
                            onRemoveEntry = { idx ->
                                cookieEntries = cookieEntries.toMutableList().apply { removeAt(idx) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                RequestSubTab.SCRIPTS -> {
                    ScriptEditorView(
                        state = scriptState,
                        onStateChange = { scriptState = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
