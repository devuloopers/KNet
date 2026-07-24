package com.devuloopers.knet.ui.widgets

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
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
import com.devuloopers.knet.ui.data.MockTransaction
import com.devuloopers.knet.ui.theme.KNetColors

/**
 * Detailed Side Inspector Panel. Implements collapsible rows mapping request parameters,
 * TCP/DNS timeline delays, applied routing rules, notes edit boxes, and tags pills.
 *
 * Meticulously matches KNet's right-sidebar layout from HTML.
 */
@Composable
fun InspectorWidget(
    transaction: MockTransaction?,
    modifier: Modifier = Modifier
) {
    if (transaction == null) {
        Box(
            modifier = modifier.fillMaxSize().background(KNetColors.SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No transaction active", color = KNetColors.TextSecondary, fontSize = 11.sp)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.SurfaceDark)
            .verticalScroll(rememberScrollState())
    ) {
        // --- 1. Request Details Section ---
        InspectorSectionHeader(title = "Request Details")
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DetailRow(label = "Method", value = transaction.method, isMono = true)
            DetailRow(label = "Protocol", value = "HTTP/1.1", isMono = true)
            DetailRow(label = "Scheme", value = "https", isMono = true)
            DetailRow(label = "Host", value = transaction.host, isMono = true)
            DetailRow(label = "Path", value = transaction.path, isMono = true)
            DetailRow(label = "Remote IP", value = "93.184.216.34", isMono = true)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Remote Port", color = KNetColors.TextSecondary, fontSize = 11.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "443", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Secure connection",
                        tint = KNetColors.SuccessGreen,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KNetColors.BorderDark))

        // --- 2. Timings Section ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "TIMINGS", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(text = "View Full Timeline", color = KNetColors.ActiveBlue, fontSize = 10.sp, modifier = Modifier.clickable { })
        }
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DetailRow(label = "DNS Lookup", value = "${transaction.timingDnsMs} ms", isMono = true)
            DetailRow(label = "TCP Connect", value = "${transaction.timingTcpMs} ms", isMono = true)
            DetailRow(label = "TLS Handshake", value = "${transaction.timingTlsMs} ms", isMono = true)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "TTFB", color = KNetColors.ActiveBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(text = "${transaction.timingTtfbMs} ms", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            DetailRow(label = "Content Download", value = "${transaction.timingDownloadMs} ms", isMono = true)
            Spacer(modifier = Modifier.height(2.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KNetColors.BorderDark))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Total", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(text = "${transaction.totalTimeMs} ms", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KNetColors.BorderDark))

        // --- Collapsible Groups headers ---
        InspectorSectionCollapsed(title = "Cookies (2)")
        InspectorSectionCollapsed(title = "Request Headers (12)")
        InspectorSectionCollapsed(title = "Response Headers (10)")

        // --- 3. TLS Info Section ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "TLS", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(text = "TLS 1.3", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KNetColors.BorderDark))

        // --- 4. Applied Rules Section ---
        InspectorSectionHeader(title = "Applied Rules (3)")
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).background(KNetColors.WarningOrange, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Breakpoint: POST /v1/login", color = Color.White, fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).background(KNetColors.ActiveBlue, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Rewrite: api.example.com -> dev.api...", color = Color.White, fontSize = 11.sp)
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KNetColors.BorderDark))

        // --- 5. Notes Section ---
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "NOTES", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = "Add a note...", color = KNetColors.TextSecondary, fontSize = 11.sp)
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Note",
                        tint = KNetColors.TextSecondary,
                        modifier = Modifier.size(14.dp).clickable { }
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KNetColors.BorderDark))

        // --- 6. Tags Section ---
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "TAGS", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("auth", "login", "user").forEach { tag ->
                    Box(
                        modifier = Modifier
                            .background(KNetColors.BorderDark, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = tag, color = Color.White, fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Remove Tag",
                                tint = KNetColors.TextSecondary,
                                modifier = Modifier.size(10.dp).clickable { }
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(KNetColors.BorderDark, CircleShape)
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Tag",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectorSectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            color = KNetColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Collapse",
            tint = KNetColors.TextSecondary,
            modifier = Modifier.size(12.dp)
        )
    }
}

@Composable
private fun InspectorSectionCollapsed(title: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                color = KNetColors.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Expand",
                tint = KNetColors.TextSecondary,
                modifier = Modifier.size(12.dp)
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KNetColors.BorderDark))
    }
}

@Composable
private fun DetailRow(label: String, value: String, isMono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = KNetColors.TextSecondary, fontSize = 11.sp)
        Text(
            text = value,
            color = Color.White,
            fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default,
            fontSize = 11.sp
        )
    }
}
