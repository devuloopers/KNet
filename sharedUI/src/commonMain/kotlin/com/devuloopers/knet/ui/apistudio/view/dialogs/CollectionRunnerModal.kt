package com.devuloopers.knet.ui.apistudio.view.dialogs

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.devuloopers.knet.domain.apistudio.runner.SuiteRunSummary
import com.devuloopers.knet.theme.KNetColors

/**
 * Interactive Modal Dialog showing live batch test runner progress and results.
 */
@Composable
fun CollectionRunnerModal(
    collectionName: String,
    isRunning: Boolean,
    summary: SuiteRunSummary?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(520.dp)
                .background(KNetColors.SurfaceDark, RoundedCornerShape(12.dp))
                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("COLLECTION TEST RUNNER", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Text(collectionName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                if (isRunning) KNetColors.ActiveBlue.copy(alpha = 0.15f) else KNetColors.SuccessGreen.copy(alpha = 0.15f),
                                RoundedCornerShape(4.dp)
                            )
                            .border(1.dp, if (isRunning) KNetColors.ActiveBlue else KNetColors.SuccessGreen, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isRunning) "RUNNING..." else "COMPLETED",
                            color = if (isRunning) KNetColors.ActiveBlue else KNetColors.SuccessGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress Bar
                if (isRunning) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = KNetColors.ActiveBlue,
                        trackColor = KNetColors.FieldDark
                    )
                } else if (summary != null) {
                    // Summary Metric Badges (Passed / Failed / Avg Latency)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KNetColors.BackgroundDark, RoundedCornerShape(6.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("TOTAL EXECUTED", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("${summary.totalRequests} Requests", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("PASSED", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("${summary.passedCount}", color = KNetColors.SuccessGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("FAILED", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("${summary.failedCount}", color = if (summary.failedCount > 0) Color(0xFFEF4444) else KNetColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("AVG LATENCY", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("${summary.averageLatencyMs} ms", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Executed Requests List Log
                Text("TEST RESULTS LOG", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(KNetColors.BackgroundDark, RoundedCornerShape(6.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    if (summary?.results.isNullOrEmpty() && isRunning) {
                        Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = KNetColors.ActiveBlue, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Executing API request suite...", color = KNetColors.TextSecondary, fontSize = 11.sp)
                        }
                    } else if (summary != null) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(summary.results) { res ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = res.request.method.name,
                                            color = Color(res.request.method.badgeColorHex),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(res.request.name, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${res.executionResult.statusCode} OK", color = KNetColors.SuccessGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("${res.executionResult.latencyMs} ms", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer Close Button
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .background(KNetColors.FieldDark, RoundedCornerShape(6.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Close", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
