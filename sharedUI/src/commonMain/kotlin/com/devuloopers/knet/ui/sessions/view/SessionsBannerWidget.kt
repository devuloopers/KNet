package com.devuloopers.knet.ui.sessions.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.controller.ProxyStateController
import com.devuloopers.knet.theme.KNetColors

/**
 * Embedded, high-density Sessions Manager banner composable for KNet.
 * Renders on the same workspace screen right above the Traffic Feed & Inspector grid.
 */
@Composable
fun SessionsBannerWidget(
    controller: ProxyStateController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isRecordingPaused by remember { mutableStateOf(false) }

    val savedSessions = remember {
        listOf(
            SavedSessionItem("s-1", "Session_2026-07-26_Staging.har", "2026-07-26 18:22", 312, "4.2 MB"),
            SavedSessionItem("s-2", "Auth_Token_Debugging.har", "2026-07-25 11:05", 85, "1.1 MB"),
            SavedSessionItem("s-3", "Checkout_API_Failure.har", "2026-07-24 16:40", 19, "420 KB")
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.SurfaceDark)
            .border(1.dp, KNetColors.ActiveBlue.copy(alpha = 0.4f))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header Bar with Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (isRecordingPaused) KNetColors.TextSecondary else KNetColors.SuccessGreen,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Active Recording Session",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "${controller.transactions.size} requests  |  1.8 MB  |  Uptime 00:12:34",
                        color = KNetColors.TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Pause/Resume Button
                    Box(
                        modifier = Modifier
                            .background(
                                if (isRecordingPaused) KNetColors.SuccessGreen.copy(alpha = 0.2f)
                                else Color(0xFFF59E0B).copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (isRecordingPaused) KNetColors.SuccessGreen else Color(0xFFF59E0B),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { isRecordingPaused = !isRecordingPaused }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isRecordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                tint = if (isRecordingPaused) KNetColors.SuccessGreen else Color(0xFFF59E0B),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isRecordingPaused) "Resume" else "Pause",
                                color = if (isRecordingPaused) KNetColors.SuccessGreen else Color(0xFFF59E0B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Export HAR Button
                    Box(
                        modifier = Modifier
                            .background(KNetColors.ActiveBlue, RoundedCornerShape(4.dp))
                            .clickable { /* Trigger HAR export */ }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export .HAR", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Close Banner
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = KNetColors.TextSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onClose() }
                    )
                }
            }

            // Saved Archives Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved Archives:",
                    color = KNetColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                savedSessions.forEach { session ->
                    Box(
                        modifier = Modifier
                            .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${session.name} (${session.requestCount} req)",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export",
                                tint = KNetColors.ActiveBlue,
                                modifier = Modifier.size(12.dp).clickable { }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(12.dp).clickable { }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                        .clickable { }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, tint = KNetColors.TextSecondary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import .HAR", color = KNetColors.TextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
