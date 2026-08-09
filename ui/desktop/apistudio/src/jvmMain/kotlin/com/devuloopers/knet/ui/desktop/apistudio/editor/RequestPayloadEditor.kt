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
import com.devuloopers.knet.ui.desktop.apistudio.editor.AuthEditorView
import com.devuloopers.knet.ui.desktop.apistudio.editor.BodyEditorView
import com.devuloopers.knet.ui.desktop.apistudio.editor.ScriptEditorView
import com.devuloopers.knet.engine.script.api.ScriptLanguage
import com.devuloopers.knet.ui.desktop.apistudio.model.AuthState
import com.devuloopers.knet.ui.desktop.apistudio.model.BodyMode
import com.devuloopers.knet.ui.desktop.apistudio.model.BodyState
import com.devuloopers.knet.ui.desktop.apistudio.model.ScriptPhase
import com.devuloopers.knet.ui.desktop.apistudio.model.ScriptState

/**
 * Closed set of request authoring sub-tabs ordered according to standard developer workflow:
 * Params -> Auth -> Headers -> Body -> Cookies -> Scripts.
 */
public enum class RequestSubTab {
    PARAMS,
    AUTH,
    HEADERS,
    BODY,
    COOKIES,
    SCRIPTS
}

/**
 * Request authoring payload editor component hosting sub-tabs bar and input panels.
 *
 * @param bodyPayload Raw request body payload content string.
 * @param onBodyPayloadChanged Callback when body payload text changes.
 * @param queryParams Key-value pairs of request query parameters.
 * @param onQueryParamsChanged Callback when query parameters are modified in the Params table.
 * @param modifier Layout modifier.
 */
@Composable
public fun RequestPayloadEditor(
    bodyPayload: String,
    onBodyPayloadChanged: (String) -> Unit,
    queryParams: List<Pair<String, String>> = emptyList(),
    onQueryParamsChanged: (List<Pair<String, String>>) -> Unit = {},
    headers: List<Pair<String, String>> = emptyList(),
    onHeadersChanged: (List<Pair<String, String>>) -> Unit = {},
    cookies: List<Pair<String, String>> = emptyList(),
    onCookiesChanged: (List<Pair<String, String>>) -> Unit = {},
    preRequestScript: String = "",
    onPreRequestScriptChanged: (String) -> Unit = {},
    testScript: String = "",
    onTestScriptChanged: (String) -> Unit = {},
    activeSubTab: RequestSubTab = RequestSubTab.BODY,
    onSubTabSelected: (RequestSubTab) -> Unit = {},
    activeScriptPhase: ScriptPhase = ScriptPhase.PRE_REQUEST,
    onScriptPhaseSelected: (ScriptPhase) -> Unit = {},
    scriptLanguage: ScriptLanguage = ScriptLanguage.JAVASCRIPT,
    onScriptLanguageChanged: (ScriptLanguage) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    val paramEntries = remember(queryParams) {
        queryParams.mapIndexed { index, (paramKey, paramValue) ->
            KeyValueEntry("param_$index", paramKey, paramValue)
        }
    }
    val headerEntries = remember(headers) {
        if (headers.isEmpty()) {
            listOf(
                KeyValueEntry("h1", "Content-Type", "application/json"),
                KeyValueEntry("h2", "Accept", "*/*"),
                KeyValueEntry("h3", "Accept-Encoding", "gzip, deflate, br"),
                KeyValueEntry("h4", "Connection", "keep-alive"),
                KeyValueEntry("h5", "User-Agent", "KNet/1.0.0")
            )
        } else {
            headers.mapIndexed { index, (headerKey, headerValue) ->
                KeyValueEntry("header_$index", headerKey, headerValue)
            }
        }
    }
    val cookieEntries = remember(cookies) {
        cookies.mapIndexed { index, (cookieKey, cookieValue) ->
            KeyValueEntry("cookie_$index", cookieKey, cookieValue)
        }
    }
    var authState by remember { mutableStateOf(AuthState()) }
    var bodyState by remember {
        mutableStateOf(
            BodyState(
                mode = BodyMode.JSON,
                payloadText = bodyPayload
            )
        )
    }
    val scriptState = remember(preRequestScript, testScript, activeScriptPhase, scriptLanguage) {
        ScriptState(
            preRequestScript = preRequestScript,
            testScript = testScript,
            scriptLanguage = scriptLanguage,
            activePhase = activeScriptPhase
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
                // Resolve dynamic label — Body tab label reflects active body mode, Params & Headers reflect counts
                val activeParamsCount = queryParams.count { it.first.isNotBlank() }
                val activeHeadersCount = headerEntries.count { it.key.isNotBlank() }
                val tabLabel = when (subTab) {
                    RequestSubTab.PARAMS -> if (activeParamsCount > 0) "Params ($activeParamsCount)" else "Params"
                    RequestSubTab.HEADERS -> "Headers ($activeHeadersCount)"
                    RequestSubTab.AUTH -> "Auth"
                    RequestSubTab.BODY -> bodyState.mode.tabLabel
                    RequestSubTab.COOKIES -> if (cookies.isNotEmpty()) "Cookies (${cookies.size})" else "Cookies"
                    RequestSubTab.SCRIPTS -> "Scripts"
                }
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { onSubTabSelected(subTab) }
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
                        onEntryChange = { entryIndex, updatedEntry ->
                            val updatedEntries = paramEntries.toMutableList().apply { set(entryIndex, updatedEntry) }
                            onQueryParamsChanged(updatedEntries.map { it.key to it.value })
                        },
                        onAddEntry = {
                            val updatedList = queryParams + ("" to "")
                            onQueryParamsChanged(updatedList)
                        },
                        onRemoveEntry = { targetIndex ->
                            val updatedList = queryParams.toMutableList().apply { removeAt(targetIndex) }
                            onQueryParamsChanged(updatedList)
                        },
                        modifier = Modifier.padding(spacing.md)
                    )
                }
                RequestSubTab.HEADERS -> {
                    KNetKeyValueEditor(
                        entries = headerEntries,
                        onEntryChange = { entryIndex, updatedEntry ->
                            val updatedEntries = headerEntries.toMutableList().apply { set(entryIndex, updatedEntry) }
                            onHeadersChanged(updatedEntries.map { it.key to it.value })
                        },
                        onAddEntry = {
                            val updatedList = headers + ("" to "")
                            onHeadersChanged(updatedList)
                        },
                        onRemoveEntry = { targetIndex ->
                            val updatedList = headers.toMutableList().apply { removeAt(targetIndex) }
                            onHeadersChanged(updatedList)
                        },
                        modifier = Modifier.padding(spacing.md)
                    )
                }
                RequestSubTab.BODY -> {
                    BodyEditorView(
                        state = bodyState,
                        onStateChange = { updatedBodyState ->
                            bodyState = updatedBodyState
                            // Propagate payload text changes to parent callback for text-based modes
                            if (updatedBodyState.mode != BodyMode.FORM_DATA && updatedBodyState.mode != BodyMode.X_WWW_FORM_URLENCODED) {
                                onBodyPayloadChanged(updatedBodyState.payloadText)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                RequestSubTab.AUTH -> {
                    AuthEditorView(
                        state = authState,
                        onStateChange = { updatedAuthState -> authState = updatedAuthState },
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
                            onEntryChange = { entryIndex, updatedEntry ->
                                val updatedEntries = cookieEntries.toMutableList().apply { set(entryIndex, updatedEntry) }
                                onCookiesChanged(updatedEntries.map { it.key to it.value })
                            },
                            onAddEntry = {
                                val updatedList = cookies + ("" to "")
                                onCookiesChanged(updatedList)
                            },
                            onRemoveEntry = { targetIndex ->
                                val updatedList = cookies.toMutableList().apply { removeAt(targetIndex) }
                                onCookiesChanged(updatedList)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                RequestSubTab.SCRIPTS -> {
                    ScriptEditorView(
                        state = scriptState,
                        onStateChange = { updatedState ->
                            if (updatedState.activePhase != activeScriptPhase) {
                                onScriptPhaseSelected(updatedState.activePhase)
                            }
                            if (updatedState.scriptLanguage != scriptLanguage) {
                                onScriptLanguageChanged(updatedState.scriptLanguage)
                            }
                            if (updatedState.preRequestScript != preRequestScript) {
                                onPreRequestScriptChanged(updatedState.preRequestScript)
                            }
                            if (updatedState.testScript != testScript) {
                                onTestScriptChanged(updatedState.testScript)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
