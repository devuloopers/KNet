package com.devuloopers.knet.ui.sessions.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.controller.ProxyStateController
import com.devuloopers.knet.theme.KNetColors

/** Amber accent color matching the image Pause Recording / 4xx bar. */
private val AmberOrange = Color(0xFFF59E0B)

/** Error red matching the Delete icon / 5xx bar / warning item. */
private val ErrorRed = Color(0xFFEF4444)

/**
 * Data class representing a saved session archive entry in the archive table.
 *
 * @param id Unique identifier of the session.
 * @param name Display name / filename of the session archive.
 * @param dateCreated Human-readable creation timestamp.
 * @param requestCount Number of captured HTTP requests in the session.
 * @param formattedSize Human-readable byte size string.
 * @param hasWarning If true, the row is highlighted in red with a warning icon
 *                   to indicate a corrupted or flagged session.
 */
data class SavedSessionItem(
    val id: String,
    val name: String,
    val dateCreated: String,
    val requestCount: Int,
    val formattedSize: String,
    val hasWarning: Boolean = false
)

/**
 * Full-screen Sessions Manager UI for KNet.
 *
 * Layout structure (matches the approved UI mockup):
 * ```
 * ┌─────────────────────────────────────────────────────────┐
 * │  Current Recording Session Banner (metrics + actions)   │
 * ├──────────────────────────────┬──────────────────────────┤
 * │  Saved Session Archives      │  Session Quick Analytics │
 * │  (searchable table + icons)  │  (domains + status bar + │
 * │                              │   quick export buttons)  │
 * └──────────────────────────────┴──────────────────────────┘
 * ```
 *
 * TopHeader and SystemStatusBar are NOT rendered here —
 * they are permanently rendered by [AppNavDisplay].
 *
 * @param controller Provides live proxy state and active transaction count.
 * @param onOpenSession Callback when the user clicks the Open icon on an archive row.
 * @param modifier Optional layout modifier.
 */
@Composable
fun SessionsScreen(
    controller: ProxyStateController,
    onOpenSession: (SavedSessionItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var isRecordingPaused by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val allSessions = remember {
        listOf(
            SavedSessionItem("s-1", "Session_2026-07-26_Staging.har", "2026-07-26 18:22", 312, "4.2 MB"),
            SavedSessionItem("s-2", "Auth_Token_Debugging.har", "2026-07-25 11:05", 85, "1.1 MB"),
            SavedSessionItem("s-3", "Checkout_API_Failure.har", "2026-07-24 16:40", 19, "420 KB", hasWarning = true)
        )
    }

    val filteredSessions = remember(searchQuery) {
        if (searchQuery.isBlank()) allSessions
        else allSessions.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.BackgroundDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 1. Active Recording Session Hero Banner ──────────────────────────
        RecordingSessionBanner(
            isRecordingPaused = isRecordingPaused,
            totalRequests = controller.transactions.size,
            onTogglePause = { isRecordingPaused = !isRecordingPaused },
            onExportHar = { /* TODO: trigger HAR export */ },
            onClearBuffer = { /* TODO: clear buffer */ }
        )

        // ── 2. Split: Archive Table (left) + Analytics (right) ───────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left: Saved Session Archives table
            SavedSessionsPanel(
                sessions = filteredSessions,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onOpenSession = onOpenSession,
                onNewSession = { /* TODO */ },
                onImportHar = { /* TODO */ },
                modifier = Modifier
                    .weight(1.8f)
                    .fillMaxHeight()
            )

            // Right: Quick Analytics panel
            SessionAnalyticsPanel(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Top banner showing the currently active recording session metrics and action buttons.
 * Matches the "Current Recording Session" card in the UI mockup.
 *
 * @param isRecordingPaused Whether recording is currently paused.
 * @param totalRequests Live count of captured transactions.
 * @param onTogglePause Called when the Pause/Resume button is clicked.
 * @param onExportHar Called when Export Session (.HAR) is clicked.
 * @param onClearBuffer Called when Clear Buffer is clicked.
 */
@Composable
private fun RecordingSessionBanner(
    isRecordingPaused: Boolean,
    totalRequests: Int,
    onTogglePause: () -> Unit,
    onExportHar: () -> Unit,
    onClearBuffer: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(KNetColors.SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(8.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left: Status dot + title + metric row
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pulsing green / grey status dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (isRecordingPaused) KNetColors.TextSecondary
                                else KNetColors.SuccessGreen,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Current Recording Session",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    MetricItem(label = "START TIME", value = "14:30:12")
                    MetricItem(label = "TOTAL REQUESTS", value = "$totalRequests")
                    Column {
                        Text("DATA SIZE", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("1.8 MB", color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("(↑450 KB / ↓1.35 MB)", color = KNetColors.TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                    MetricItem(label = "UPTIME", value = "00:12:34")
                }
            }

            // Right: Action buttons
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Pause / Resume Toggle
                    SessionActionButton(
                        label = if (isRecordingPaused) "Resume Recording" else "Pause Recording",
                        icon = if (isRecordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        backgroundColor = (if (isRecordingPaused) KNetColors.SuccessGreen else AmberOrange).copy(alpha = 0.2f),
                        borderColor = if (isRecordingPaused) KNetColors.SuccessGreen else AmberOrange,
                        contentColor = if (isRecordingPaused) KNetColors.SuccessGreen else AmberOrange,
                        onClick = onTogglePause
                    )
                    // Export HAR
                    SessionActionButton(
                        label = "Export Session (.HAR)",
                        icon = Icons.Default.FileDownload,
                        backgroundColor = KNetColors.ActiveBlue.copy(alpha = 0.2f),
                        borderColor = KNetColors.ActiveBlue,
                        contentColor = KNetColors.ActiveBlue,
                        onClick = onExportHar
                    )
                }
                // Clear Buffer (outlined, full width of button row)
                Box(
                    modifier = Modifier
                        .background(KNetColors.BackgroundDark, RoundedCornerShape(6.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                        .clickable { onClearBuffer() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Buffer",
                            tint = KNetColors.TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Clear Buffer",
                            color = KNetColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Left panel — filterable table of saved HAR session archives.
 *
 * @param sessions Filtered list of session items to display.
 * @param searchQuery Current value of the search filter field.
 * @param onSearchQueryChange Called when the user types in the search field.
 * @param onOpenSession Called with the target item when Open icon is clicked.
 * @param onNewSession Called when "+ New Session" is clicked.
 * @param onImportHar Called when "Import .HAR" is clicked.
 * @param modifier Layout modifier.
 */
@Composable
private fun SavedSessionsPanel(
    sessions: List<SavedSessionItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenSession: (SavedSessionItem) -> Unit,
    onNewSession: () -> Unit,
    onImportHar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(KNetColors.SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header row: title + search bar + action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Saved Session Archives",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Search field
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = KNetColors.TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (searchQuery.isEmpty()) "Filter sessions..." else searchQuery,
                                color = if (searchQuery.isEmpty()) KNetColors.TextSecondary else Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // + New Session
                    Box(
                        modifier = Modifier
                            .background(KNetColors.ActiveBlue.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(1.dp, KNetColors.ActiveBlue, RoundedCornerShape(4.dp))
                            .clickable { onNewSession() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = KNetColors.ActiveBlue, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Session", color = KNetColors.ActiveBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Import .HAR
                    Box(
                        modifier = Modifier
                            .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                            .clickable { onImportHar() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, tint = KNetColors.TextSecondary, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import .HAR", color = KNetColors.TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Table header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Session Name", color = KNetColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                Text("Date Created", color = KNetColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                Text("Requests", color = KNetColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                Text("Size", color = KNetColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                Text("Actions", color = KNetColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Table rows
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sessions.forEach { item ->
                    SessionArchiveRow(
                        item = item,
                        onOpen = { onOpenSession(item) },
                        onExport = { /* TODO */ },
                        onDelete = { /* TODO */ }
                    )
                }
            }
        }
    }
}

/**
 * Single row in the Saved Session Archives table.
 *
 * Rows with [SavedSessionItem.hasWarning] are rendered with a red warning icon
 * and the session name highlighted in red to indicate a corrupted/flagged archive.
 *
 * @param item The session data to render.
 * @param onOpen Called when the Open-in-new-tab icon is clicked.
 * @param onExport Called when the Download icon is clicked.
 * @param onDelete Called when the Delete icon is clicked.
 */
@Composable
private fun SessionArchiveRow(
    item: SavedSessionItem,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val nameColor = if (item.hasWarning) ErrorRed else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KNetColors.BackgroundDark, RoundedCornerShape(4.dp))
            .border(
                width = 1.dp,
                color = if (item.hasWarning) ErrorRed.copy(alpha = 0.3f) else KNetColors.BorderDark,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Session name column — icon + name
        Row(
            modifier = Modifier.weight(2f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.hasWarning) Icons.Default.Warning else Icons.Default.Description,
                contentDescription = null,
                tint = nameColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(item.name, color = nameColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        Text(item.dateCreated, color = KNetColors.TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1.2f))
        Text("${item.requestCount} req", color = KNetColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(0.8f))
        Text(item.formattedSize, color = KNetColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(0.8f))

        // Action icons
        Row(
            modifier = Modifier.width(80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Open",
                tint = KNetColors.ActiveBlue,
                modifier = Modifier.size(14.dp).clickable { onOpen() }
            )
            Icon(
                imageVector = Icons.Default.FileDownload,
                contentDescription = "Export",
                tint = KNetColors.TextSecondary,
                modifier = Modifier.size(14.dp).clickable { onExport() }
            )
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = ErrorRed,
                modifier = Modifier.size(14.dp).clickable { onDelete() }
            )
        }
    }
}

/**
 * Right panel — Session Quick Analytics.
 *
 * Displays:
 * - Domain Breakdown: top host domains with percentage progress bars.
 * - Status Distribution: segmented bar (2xx / 4xx / 5xx) with legend.
 * - Quick Export: one-click export format buttons.
 *
 * @param modifier Layout modifier.
 */
@Composable
private fun SessionAnalyticsPanel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(KNetColors.SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            // Panel title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = KNetColors.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Session Quick Analytics",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // ── Domain Breakdown ──────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "DOMAIN BREAKDOWN",
                    color = KNetColors.TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                DomainProgressItem("api.github.com", 0.45f, "45%", KNetColors.ActiveBlue)
                DomainProgressItem("auth.stripe.com", 0.30f, "30%", AmberOrange)
                DomainProgressItem("cdn.example.com", 0.25f, "25%", KNetColors.SuccessGreen)
            }

            // ── Status Distribution ───────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "STATUS DISTRIBUTION",
                    color = KNetColors.TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                // Segmented bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clipToBounds()
                ) {
                    Box(modifier = Modifier.weight(0.85f).fillMaxHeight().background(KNetColors.SuccessGreen)) {
                        Text("85%", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center))
                    }
                    Box(modifier = Modifier.weight(0.10f).fillMaxHeight().background(AmberOrange)) {
                        Text("10%", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center))
                    }
                    Box(modifier = Modifier.weight(0.05f).fillMaxHeight().background(ErrorRed)) {
                        Text("5%", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center))
                    }
                }
                // Legend
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    StatusLegendDot(label = "2xx", color = KNetColors.SuccessGreen)
                    StatusLegendDot(label = "4xx", color = AmberOrange)
                    StatusLegendDot(label = "5xx", color = ErrorRed)
                }
            }

            // ── Quick Export ──────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "QUICK EXPORT",
                    color = KNetColors.TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                QuickExportButton("HAR v1.2 Archive")
                QuickExportButton("cURL Batch Script")
                QuickExportButton("Raw JSON Data")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable leaf composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reusable metric display — label above, monospaced value below.
 *
 * @param label The uppercase label shown above the value.
 * @param value The primary metric value shown in monospace font.
 */
@Composable
private fun MetricItem(label: String, value: String) {
    Column {
        Text(text = label, color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

/**
 * Styled action button used in the Recording Session banner.
 *
 * @param label Button label text.
 * @param icon Leading icon vector.
 * @param backgroundColor Fill color of the button background.
 * @param borderColor Border color.
 * @param contentColor Color of icon and label.
 * @param onClick Click handler.
 */
@Composable
private fun SessionActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: Color,
    borderColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, color = contentColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Domain name with a colored horizontal progress bar and percentage label.
 *
 * @param domain Hostname label.
 * @param percentage Fill fraction 0f–1f for the progress bar.
 * @param percentText Formatted percentage string (e.g. "45%").
 * @param barColor Fill color of the progress bar.
 */
@Composable
private fun DomainProgressItem(
    domain: String,
    percentage: Float,
    percentText: String,
    barColor: Color
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(domain, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text(percentText, color = KNetColors.TextSecondary, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(KNetColors.FieldDark, RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percentage)
                    .fillMaxHeight()
                    .background(barColor, RoundedCornerShape(2.dp))
            )
        }
    }
}

/**
 * Small dot + label legend item for the status distribution row.
 *
 * @param label Status category label (e.g. "2xx").
 * @param color Dot fill color.
 */
@Composable
private fun StatusLegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = KNetColors.TextSecondary, fontSize = 10.sp)
    }
}

/**
 * Full-width outlined export format button in the Quick Export section.
 *
 * @param label Export format name shown on the button.
 */
@Composable
private fun QuickExportButton(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(KNetColors.FieldDark, RoundedCornerShape(6.dp))
            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
            .clickable { /* TODO: trigger export */ }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
