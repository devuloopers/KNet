package com.devuloopers.knet.ui.apistudio.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.domain.apistudio.model.TestAssertionResult
import com.devuloopers.knet.domain.apistudio.usecase.ExecutionResult
import com.devuloopers.knet.theme.KNetColors

/**
 * Right column of the API Studio screen displaying the HTTP response and test results.
 *
 * Shows:
 * - Status code / latency / size header row
 * - Sub-tabs: Body | Headers | Cookies | Tests
 * - Dynamic viewer for the active tab content
 * - Stored test assertion results summary
 * - Export & copy action bar
 *
 * @param request The currently selected or draft [SavedApiRequest] (used for stored test results).
 * @param activeTab The currently selected response tab label.
 * @param latestResult The most recent [ExecutionResult] from the last send, or null.
 * @param testResults Live test assertion results from the last execution run.
 * @param onTabSelected Callback when a response sub-tab is clicked.
 * @param modifier Layout modifier for the panel container.
 */
@Composable
internal fun ResponseTestPanel(
    request: SavedApiRequest,
    activeTab: String,
    latestResult: ExecutionResult? = null,
    responsePresentation: com.devuloopers.knet.ui.apistudio.model.ResponsePresentation? = null,
    testResults: List<TestAssertionResult> = emptyList(),
    onTabSelected: (String) -> Unit,
    onClearResponse: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val respTabs = listOf("Body", "Headers", "Cookies", "Tests")

    Box(
        modifier = modifier
            .background(KNetColors.SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar with status badge, tabs, and clear button
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (latestResult != null) {
                        val statusColor = when {
                            latestResult.isSuccess -> KNetColors.SuccessGreen
                            latestResult.statusCode in 400..499 -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        }
                        Box(
                            modifier = Modifier
                                .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("${latestResult.statusCode} ${latestResult.statusText}", color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("•  ${latestResult.latencyMs} ms  •  ${latestResult.responseSizeBytes} B", color = KNetColors.TextSecondary, fontSize = 11.sp)
                    } else {
                        Text("RESPONSE", color = KNetColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (latestResult != null) {
                    Box(
                        modifier = Modifier
                            .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                            .clickable { onClearResponse() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Clear", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Sub-tabs bar: Body | Headers | Cookies | Tests
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                respTabs.forEach { tabName ->
                    val isSelected = activeTab == tabName
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) KNetColors.ActiveBlue.copy(alpha = 0.2f) else Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { onTabSelected(tabName) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tabName,
                                color = if (isSelected) KNetColors.ActiveBlue else KNetColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (tabName == "Tests" && testResults.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(modifier = Modifier.size(6.dp).background(KNetColors.SuccessGreen, CircleShape))
                            }
                        }
                    }
                }
            }

            // Dynamic Response Viewer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.2f)
                    .background(KNetColors.BackgroundDark, RoundedCornerShape(6.dp))
                    .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                when (activeTab) {
                    "Headers" -> ResponseHeadersViewer(headers = latestResult?.headers ?: emptyMap())
                    "Cookies" -> com.devuloopers.knet.ui.apistudio.view.tabs.ResponseCookiesTab(headers = latestResult?.headers ?: emptyMap(), presentation = responsePresentation)
                    "Tests" -> TestResultsViewer(
                        testResults = testResults,
                        preRequestScript = request.scripts.preRequest,
                        latestResult = latestResult
                    )
                    else -> com.devuloopers.knet.ui.apistudio.view.tabs.ResponseBodyTab(latestResult = latestResult, presentation = responsePresentation)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stored test results summary
            if (request.testResults.isNotEmpty()) {
                StoredTestResultsSummary(testResults = request.testResults)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Bottom Action Bar
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = KNetColors.TextSecondary, modifier = Modifier.size(14.dp).clickable { })
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = KNetColors.TextSecondary, modifier = Modifier.size(14.dp).clickable { })
                }
                Box(
                    modifier = Modifier
                        .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                        .clickable { }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Export Collection", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = KNetColors.TextSecondary, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResponseBodyViewer(latestResult: ExecutionResult?) {
    val bodyText = when {
        latestResult?.errorMessage != null -> "Error: ${latestResult.errorMessage}"
        latestResult?.responseBody?.isNotBlank() == true -> latestResult.responseBody
        else -> "No response payload. Enter a URL and click 'Send Request'."
    }
    val textColor = if (latestResult?.isSuccess == false) Color(0xFFEF4444)
    else if (latestResult == null) KNetColors.TextSecondary.copy(alpha = 0.6f)
    else Color(0xFF10B981)
    Text(text = bodyText, color = textColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
}

@Composable
private fun ResponseHeadersViewer(headers: Map<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (headers.isEmpty()) {
            Text("No response headers received yet", color = KNetColors.TextSecondary.copy(alpha = 0.6f), fontSize = 11.sp)
        } else {
            headers.forEach { (k, v) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(k, color = KNetColors.TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(v, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun TestResultsViewer(
    testResults: List<TestAssertionResult>,
    preRequestScript: String = "",
    latestResult: ExecutionResult? = null
) {
    val activeTestResults = testResults
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        if (latestResult != null && !latestResult.isSuccess && latestResult.statusCode == 0 && latestResult.errorMessage != null) {
            com.devuloopers.knet.ui.apistudio.view.components.ScriptErrorBlock(
                title = "Pre-request Script Error",
                errorMessage = "${latestResult.errorMessage}\nHTTP network request was skipped."
            )
        } else if (activeTestResults.isEmpty()) {
            val hasPreRequestAssertions = preRequestScript.contains("pm.test") || preRequestScript.contains("pm.response")
            if (hasPreRequestAssertions) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E2530), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFFE5A50A).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Lightbulb, contentDescription = "Warning", tint = Color(0xFFFFD54F), modifier = Modifier.size(14.dp))
                            Text(
                                text = "Pre-request Script Warning",
                                color = Color(0xFFFFD54F),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Test assertions (pm.test / pm.response) were detected in your Pre-request Script tab. Pre-request scripts run before receiving an HTTP response. Move them to the Tests tab to record test results.",
                            color = Color(0xFFF0F4F8),
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                Text("No test assertions evaluated yet. Click 'Send Request' to run test scripts.", color = KNetColors.TextSecondary.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        } else {
            val passCount = activeTestResults.count { it.passed }
            Text(
                text = "TEST ASSERTION RESULTS ($passCount/${activeTestResults.size} PASSED)",
                color = if (passCount == activeTestResults.size) KNetColors.SuccessGreen else Color(0xFFF59E0B),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            activeTestResults.forEach { test ->
                if (test.name.startsWith("Script Error:", ignoreCase = true)) {
                    com.devuloopers.knet.ui.apistudio.view.components.ScriptErrorBlock(
                        title = "Syntax Error / Script Execution Failure",
                        errorMessage = test.name
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(if (test.passed) KNetColors.SuccessGreen.copy(alpha = 0.1f) else Color(0xFFEF4444).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .border(1.dp, if (test.passed) KNetColors.SuccessGreen.copy(alpha = 0.3f) else Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (test.passed) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (test.passed) KNetColors.SuccessGreen else Color(0xFFEF4444),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = test.name,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (test.passed) "PASS" else "FAIL",
                            color = if (test.passed) KNetColors.SuccessGreen else Color(0xFFEF4444),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoredTestResultsSummary(testResults: List<TestAssertionResult>) {
    val passCount = testResults.count { it.passed }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("TEST RESULTS ($passCount/${testResults.size} PASSED)", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = KNetColors.SuccessGreen, modifier = Modifier.size(14.dp))
        }
        testResults.forEach { test ->
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(KNetColors.BackgroundDark, RoundedCornerShape(4.dp))
                    .border(1.dp, if (test.passed) KNetColors.SuccessGreen.copy(alpha = 0.3f) else Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(if (test.passed) KNetColors.SuccessGreen.copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(if (test.passed) "PASS" else "FAIL", color = if (test.passed) KNetColors.SuccessGreen else Color(0xFFEF4444), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(test.name, color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}
