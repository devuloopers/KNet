package com.devuloopers.knet.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.inspector.model.TransactionUiModel
import com.devuloopers.knet.theme.KNetColors

/**
 * Enumeration of the primary tabs available in KNet's middle inspector pane.
 *
 * @property label User-facing display title on the tab strip.
 */
enum class InspectorMiddleTab(val label: String) {
    OVERVIEW("Overview"),
    REQUEST("Request"),
    RESPONSE("Response"),
    HEADERS("Headers"),
    TIMELINE("Timeline")
}

/**
 * Unified, tab-driven inspector widget occupying the middle column of KNet's main UI.
 *
 * Provides a fixed top header displaying the transaction URL, method, status badge,
 * latency metrics, and action buttons (`Forward`, `Drop`, `Edit`, `Replay`).
 * Below the header, a tab strip lets the user switch between `Overview`, `Request`,
 * `Response`, `Headers`, and `Timeline` views, with the active view taking 100% of
 * the remaining viewport height.
 *
 * @param transaction The currently selected transaction data, or null if no transaction is selected.
 * @param modifier Optional [Modifier] for layout constraints.
 */
@Composable
fun MiddleInspectorWidget(
    transaction: TransactionUiModel?,
    modifier: Modifier = Modifier
) {
    if (transaction == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(KNetColors.BackgroundDark),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No transaction active",
                color = KNetColors.TextSecondary,
                fontSize = 12.sp
            )
        }
        return
    }

    var activeTab by remember { mutableStateOf(InspectorMiddleTab.OVERVIEW) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.BackgroundDark)
            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(0.dp))
            .clipToBounds()
    ) {
        // --- Fixed Header Section ---
        HeaderSection(
            transaction = transaction,
            activeTab = activeTab,
            onTabSelected = { activeTab = it }
        )

        // --- Dynamic Content Viewport (100% height for active tab) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (activeTab) {
                InspectorMiddleTab.OVERVIEW -> OverviewTabView(transaction)
                InspectorMiddleTab.REQUEST -> RequestTabView(transaction)
                InspectorMiddleTab.RESPONSE -> ResponseTabView(transaction)
                InspectorMiddleTab.HEADERS -> HeadersTabView(transaction)
                InspectorMiddleTab.TIMELINE -> TimelineTabView(transaction)
            }
        }
    }
}

/**
 * Renders the top header bar: URL/Method row, metadata summary, and primary tab strip.
 */
@Composable
private fun HeaderSection(
    transaction: TransactionUiModel,
    activeTab: InspectorMiddleTab,
    onTabSelected: (InspectorMiddleTab) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(KNetColors.BackgroundDark)
    ) {
        val availableWidth = maxWidth
        val isCompact = availableWidth < 520.dp
        val isUltraCompact = availableWidth < 380.dp

        Column(modifier = Modifier.fillMaxWidth()) {
            // Row 1: Method, Path, Status Badge & Action Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left group: Method badge, Path (flexible), Status Badge (non-wrapping)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    val methodColor = when (transaction.method.uppercase()) {
                        "GET" -> KNetColors.SuccessGreen
                        "POST" -> KNetColors.ErrorRed
                        "WS" -> KNetColors.PurpleWS
                        else -> KNetColors.TextSecondary
                    }
                    Text(
                        text = transaction.method,
                        color = methodColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = transaction.path,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Status Badge — strict non-wrapping
                    val isSuccess = transaction.status in 200..299
                    val statusColor = if (isSuccess) KNetColors.SuccessGreen else KNetColors.ErrorRed
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isCompact) "${transaction.status}" else "${transaction.status} ${transaction.statusText}",
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action Buttons (Forward / Drop / Edit / Replay) — adaptively collapses
                if (!isUltraCompact) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(containerColor = KNetColors.ActiveBlue),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (isCompact) 6.dp else 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Forward",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                            if (!isCompact) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Forward", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(containerColor = KNetColors.ErrorRed),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (isCompact) 6.dp else 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = "Drop",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                            if (!isCompact) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Drop", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(containerColor = KNetColors.WarningOrange),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (isCompact) 6.dp else 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                            if (!isCompact) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Edit", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                        // Replay Button
                        Row(
                            modifier = Modifier
                                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                                .background(KNetColors.SurfaceDark, RoundedCornerShape(4.dp))
                                .height(24.dp)
                                .clickable { }
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Replay",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                            if (!isCompact) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Replay", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }

            // Row 2: Host & Metrics Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val metrics = listOf(
                    Icons.Default.Language to transaction.host,
                    Icons.Default.Schedule to transaction.time,
                    Icons.Default.FlashOn to transaction.size
                )
                metrics.forEach { (icon, text) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = icon, contentDescription = null, tint = KNetColors.TextSecondary, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = text,
                            color = KNetColors.TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 3: Tab Strip Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(KNetColors.SurfaceDark)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InspectorMiddleTab.entries.forEach { tab ->
                    val isSelected = tab == activeTab
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(IntrinsicSize.Min)
                            .clickable { onTabSelected(tab) }
                    ) {
                        Text(
                            text = when (tab) {
                                InspectorMiddleTab.HEADERS -> if (isCompact) "Headers" else "Headers (${transaction.requestHeaders.size + transaction.responseHeaders.size})"
                                else -> tab.label
                            },
                            color = if (isSelected) Color.White else KNetColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (isSelected) KNetColors.ActiveBlue else Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Overview tab content: Key summary card with host, path, status, and duration details.
 */
@Composable
private fun OverviewTabView(transaction: TransactionUiModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Transaction Summary",
            color = KNetColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(KNetColors.SurfaceDark, RoundedCornerShape(6.dp))
                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryItemRow("Host", transaction.host)
            
            // --- Dedicated Path Code Box Section with Copy Option ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Path", color = KNetColors.TextSecondary, fontSize = 11.sp)
                    CopyButton(
                        textToCopy = transaction.path,
                        label = "Copy Path"
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KNetColors.FieldDark, RoundedCornerShape(6.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = transaction.path,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            SummaryItemRow("Method", transaction.method)
            SummaryItemRow("Status", "${transaction.status} ${transaction.statusText}")
            SummaryItemRow("Time", transaction.time)
            SummaryItemRow("Size", transaction.size)
            SummaryItemRow("Request Headers Count", transaction.requestHeaders.size.toString())
            SummaryItemRow("Response Headers Count", transaction.responseHeaders.size.toString())
        }
    }
}

@Composable
private fun SummaryItemRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = KNetColors.TextSecondary, fontSize = 11.sp)
        Text(text = value, color = KNetColors.TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Request tab content: Sub-tabs between Query Params (Tree) vs Request Body.
 */
@Composable
private fun RequestTabView(transaction: TransactionUiModel) {
    val queryCount = transaction.queryParams.size
    val hasBody = transaction.requestBody.isNotEmpty()
    var subTab by remember { mutableStateOf("Query Params") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Streamlined Sub-tab strip (Query Params vs Body)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KNetColors.SurfaceDark)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf(
                "Query Params ($queryCount)",
                if (hasBody) "Body" else "Body (Empty)"
            )
            tabs.forEach { tabLabel ->
                val isQueryParamsTab = tabLabel.startsWith("Query Params")
                val isSelected = if (isQueryParamsTab) subTab == "Query Params" else subTab == "Body"
                Box(
                    modifier = Modifier
                        .background(if (isSelected) KNetColors.ActiveBlue else Color.Transparent, RoundedCornerShape(4.dp))
                        .clickable { subTab = if (isQueryParamsTab) "Query Params" else "Body" }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = tabLabel,
                        color = if (isSelected) Color.White else KNetColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (subTab == "Query Params") {
                RequestTreeWidget(transaction = transaction, modifier = Modifier.fillMaxSize())
            } else {
                RequestBodyWidget(transaction = transaction, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * Response tab content: Full-height Response Body JSON viewer.
 */
@Composable
private fun ResponseTabView(transaction: TransactionUiModel) {
    ResponseBodyWidget(transaction = transaction, modifier = Modifier.fillMaxSize())
}

/**
 * Headers tab content: Side-by-side or stacked Request & Response HTTP headers tables.
 */
@Composable
private fun HeadersTabView(transaction: TransactionUiModel) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Request Headers Column
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(KNetColors.SurfaceDark, RoundedCornerShape(6.dp))
                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            Text(text = "Request Headers (${transaction.requestHeaders.size})", color = KNetColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            HeaderTable(transaction.requestHeaders)
        }

        // Response Headers Column
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(KNetColors.SurfaceDark, RoundedCornerShape(6.dp))
                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            Text(text = "Response Headers (${transaction.responseHeaders.size})", color = KNetColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            HeaderTable(transaction.responseHeaders)
        }
    }
}

@Composable
private fun HeaderTable(headers: Map<String, String>) {
    if (headers.isEmpty()) {
        Text(text = "No headers available", color = KNetColors.TextSecondary, fontSize = 11.sp)
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(headers.entries.toList()) { (key, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = key, color = KNetColors.ActiveBlue, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.4f))
                Text(text = value, color = KNetColors.TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.6f))
            }
        }
    }
}

/**
 * Timeline tab content: Connection phase timing gauges.
 */
@Composable
private fun TimelineTabView(transaction: TransactionUiModel) {
    TimingsWidget(transaction = transaction, modifier = Modifier.fillMaxSize())
}
