package com.devuloopers.knet.ui.desktop.apistudio.response

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.components.button.KNetCopyButton
import com.devuloopers.knet.ui.core.components.button.KNetCopyDropdownButton
import com.devuloopers.knet.ui.core.components.button.KNetCopyOption
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.button.KNetSegmentedButton
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.divider.VerticalDivider
import com.devuloopers.knet.ui.core.components.keyvalue.KNetKeyValueEditor
import com.devuloopers.knet.ui.core.components.keyvalue.KNetReadOnlyKeyValueViewer
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tabs.ScrollableTabRow
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.model.TestResult
import com.devuloopers.knet.ui.desktop.apistudio.theme.ApiStudioColors
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor

/**
 * Closed set of copy format capabilities supported by Response Inspector views.
 *
 * @property label User-facing format label.
 */
public enum class CopyFormatType(val label: String) {
    RAW("RAW"),
    JSON("JSON"),
    TEXT("TEXT")
}

/**
 * Closed set of response inspector sub-tabs with strongly-typed copy format capabilities.
 *
 * @property baseLabel Display label for the sub-tab.
 * @property supportedCopyFormats List of supported [CopyFormatType] options.
 */
public enum class ResponseSubTab(
    val baseLabel: String,
    val supportedCopyFormats: List<CopyFormatType>
) {
    BODY(
        baseLabel = "Body",
        supportedCopyFormats = listOf(CopyFormatType.JSON)
    ),
    HEADERS(
        baseLabel = "Headers",
        supportedCopyFormats = listOf(CopyFormatType.RAW, CopyFormatType.JSON)
    ),
    COOKIES(
        baseLabel = "Cookies",
        supportedCopyFormats = listOf(CopyFormatType.RAW, CopyFormatType.JSON)
    ),
    TEST_RESULTS(
        baseLabel = "Test Results",
        supportedCopyFormats = listOf(CopyFormatType.TEXT)
    ),
    CONSOLE(
        baseLabel = "Console",
        supportedCopyFormats = listOf(CopyFormatType.TEXT)
    );

    val isMultiFormatCopy: Boolean get() = supportedCopyFormats.size > 1
}

/**
 * Right-pane Response Inspector component displaying response status, metrics, responsive sub-tabs,
 * payload code viewer, header & cookie key-value tables, assertion results, and script console logs.
 */
@Composable
public fun ResponseInspectorView(
    statusCode: Int = 200,
    statusText: String = "OK",
    durationMs: Long = 124L,
    sizeBytes: Long = 4966L,
    responseBody: String = "",
    headers: Map<String, String> = mapOf(
        "Content-Type" to "application/json; charset=utf-8",
        "Content-Length" to "4966",
        "Server" to "KNet/1.0 Netty",
        "Date" to "Tue, 04 Aug 2026 11:22:00 GMT"
    ),
    cookies: Map<String, String> = mapOf(
        "sessionId" to "s_98a7f6c5e4; Path=/; Secure; HttpOnly",
        "theme" to "dark; Path=/"
    ),
    testResults: List<TestResult> = listOf(
        TestResult("Status code is 200", true),
        TestResult("Response time is less than 500ms", true),
        TestResult("Content-Type header is present", true)
    ),
    consoleLogs: List<String> = listOf(
        "[INFO] Preparing POST request to https://api.knet.dev/v1/users/create",
        "[INFO] Pre-request script executed cleanly (0 ms)",
        "[NET] Connection established in 42 ms",
        "[NET] Received response: 200 OK (4966 bytes)",
        "[TEST] Executed 3 test assertions (Passed: 3/3)"
    ),
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    var activeSubTab by remember { mutableStateOf(ResponseSubTab.BODY) }
    val currentConsoleLogs = remember(consoleLogs) { mutableStateListOf(*consoleLogs.toTypedArray()) }

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
        // 1. Response Summary Bar (Horizontally scrollable for desktop responsiveness)
        val summaryScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .horizontalScroll(summaryScrollState)
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
                        modifier = Modifier.size(18.dp),
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

    var selectedFormatIndex by remember(activeSubTab) { mutableStateOf(0) }

    val activeFormatType = remember(activeSubTab, selectedFormatIndex) {
        val formats = activeSubTab.supportedCopyFormats
        if (selectedFormatIndex in formats.indices) formats[selectedFormatIndex] else formats.first()
    }

    val formatToJsonObject: (Map<String, String>) -> String = { map ->
        if (map.isEmpty()) "{}"
        else map.entries.joinToString(
            separator = ",\n  ",
            prefix = "{\n  ",
            postfix = "\n}"
        ) { (k, v) -> "\"$k\": \"$v\"" }
    }

    val activeTextToCopy = remember(activeSubTab, activeFormatType, displayBody, headers, cookies, testResults, currentConsoleLogs) {
        when (activeSubTab) {
            ResponseSubTab.BODY -> displayBody
            ResponseSubTab.HEADERS -> if (activeFormatType == CopyFormatType.RAW) {
                headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            } else {
                formatToJsonObject(headers)
            }
            ResponseSubTab.COOKIES -> if (activeFormatType == CopyFormatType.RAW) {
                cookies.entries.joinToString("\n") { "${it.key}=${it.value}" }
            } else {
                formatToJsonObject(cookies)
            }
            ResponseSubTab.TEST_RESULTS -> {
                val passedCount = testResults.count { it.passed }
                buildString {
                    appendLine("TEST RESULTS ($passedCount/${testResults.size} Passed)")
                    appendLine("-".repeat(40))
                    testResults.forEach { res ->
                        val status = if (res.passed) "[PASS]" else "[FAIL]"
                        val err = if (!res.passed && !res.errorMessage.isNullOrBlank()) " - ${res.errorMessage}" else ""
                        appendLine("$status ${res.name}$err")
                    }
                }
            }
            ResponseSubTab.CONSOLE -> currentConsoleLogs.joinToString("\n")
        }
    }

            // Quick Action: Declaratively Driven Segmented Format Toggle + Copy Button
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeSubTab.isMultiFormatCopy) {
                    KNetSegmentedButton(
                        options = activeSubTab.supportedCopyFormats.map { it.label },
                        selectedIndex = selectedFormatIndex,
                        onOptionSelected = { selectedFormatIndex = it }
                    )
                }
                KNetCopyButton(
                    textToCopy = activeTextToCopy,
                    copiedText = "Copied as ${activeFormatType.label.lowercase()}"
                )
            }
        }

        HorizontalDivider(color = themeColors.border)

        // 2. Responsive Scrollable Sub-Tabs Bar
        ScrollableTabRow(modifier = Modifier.fillMaxWidth()) {
            ResponseSubTab.entries.forEach { subTab ->
                val labelWithBadge = when (subTab) {
                    ResponseSubTab.BODY -> "Body"
                    ResponseSubTab.HEADERS -> "Headers (${headers.size})"
                    ResponseSubTab.COOKIES -> "Cookies (${cookies.size})"
                    ResponseSubTab.TEST_RESULTS -> "Test Results (${testResults.count { it.passed }}/${testResults.size})"
                    ResponseSubTab.CONSOLE -> "Console (${currentConsoleLogs.size})"
                }
                KNetTab(
                    title = labelWithBadge,
                    selected = subTab == activeSubTab,
                    onClick = { activeSubTab = subTab }
                )
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
                    val headerEntries = remember(headers) {
                        headers.entries.mapIndexed { idx, (k, v) -> KeyValueEntry("h_$idx", k, v) }
                    }
                    KNetReadOnlyKeyValueViewer(
                        entries = headerEntries,
                        keyHeader = "HEADER NAME",
                        valueHeader = "VALUE",
                        modifier = Modifier.padding(spacing.md)
                    )
                }
                ResponseSubTab.COOKIES -> {
                    val cookieEntries = remember(cookies) {
                        cookies.entries.mapIndexed { idx, (k, v) -> KeyValueEntry("c_$idx", k, v) }
                    }
                    KNetReadOnlyKeyValueViewer(
                        entries = cookieEntries,
                        keyHeader = "COOKIE NAME",
                        valueHeader = "VALUE",
                        modifier = Modifier.padding(spacing.md)
                    )
                }
                ResponseSubTab.TEST_RESULTS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val passedCount = testResults.count { it.passed }
                        Text(
                            text = "PASSING TESTS ($passedCount/${testResults.size})",
                            style = typography.caption.copy(
                                color = if (passedCount == testResults.size) ApiStudioColors.GetText else themeColors.semantic.warning,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(testResults) { res ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(themeColors.surfaceVariant)
                                        .border(1.dp, themeColors.border, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (res.passed) KNetIcons.Check else KNetIcons.Close,
                                        contentDescription = if (res.passed) "Passed" else "Failed",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (res.passed) ApiStudioColors.GetText else themeColors.semantic.error
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = res.name,
                                            style = typography.bodySmall.copy(
                                                color = themeColors.textPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                        if (!res.passed && !res.errorMessage.isNullOrBlank()) {
                                            Text(
                                                text = res.errorMessage,
                                                style = typography.caption.copy(color = themeColors.semantic.error)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (res.passed) "PASS" else "FAIL",
                                        style = typography.caption.copy(
                                            color = if (res.passed) ApiStudioColors.GetText else themeColors.semantic.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                ResponseSubTab.CONSOLE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0F0F17))
                            .padding(spacing.md)
                    ) {

                        if (currentConsoleLogs.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No console output logged.",
                                    style = typography.caption.copy(color = themeColors.textMuted)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(currentConsoleLogs) { log ->
                                    Text(
                                        text = log,
                                        style = typography.codeSmall.copy(
                                            color = when {
                                                log.contains("[ERROR]") -> themeColors.semantic.error
                                                log.contains("[TEST]") -> ApiStudioColors.GetText
                                                log.contains("[NET]") -> Color(0xFF89B4FA)
                                                else -> themeColors.textPrimary
                                            },
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
