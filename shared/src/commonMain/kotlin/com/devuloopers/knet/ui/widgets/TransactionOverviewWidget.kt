package com.devuloopers.knet.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
 * Isolated Transaction Overview Widget. Displays active request headers, scheme, status,
 * target host/port, interception actions, connection metadata, and tab selections.
 *
 * Meticulously replicates KNet's header inspector panel from the HTML design layout.
 */
@Composable
fun TransactionOverviewWidget(
    transaction: MockTransaction?,
    modifier: Modifier = Modifier
) {
    if (transaction == null) {
        Box(
            modifier = modifier.fillMaxWidth().background(KNetColors.BackgroundDark),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No transaction active", color = KNetColors.TextSecondary, fontSize = 11.sp)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.BackgroundDark)
            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(0.dp))
    ) {
        // Row 1: Badges & Path metadata + Interception actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = transaction.method,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = transaction.path,
                    color = KNetColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(10.dp))
                // Status Badge
                Box(
                    modifier = Modifier
                        .background(KNetColors.SuccessGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .border(1.dp, KNetColors.SuccessGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "200 OK",
                        color = KNetColors.SuccessGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Badges
                Box(
                    modifier = Modifier
                        .background(KNetColors.BorderDark.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = "HTTP/1.1", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .background(KNetColors.BorderDark.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = "HTTPS", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Intercept Actions (Material 3 Icons)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = KNetColors.ActiveBlue),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Forward",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Forward", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = KNetColors.ErrorRed),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Drop",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Drop", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = KNetColors.WarningOrange),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Edit", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                // Replay Split Button Group
                Row(
                    modifier = Modifier
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                        .background(KNetColors.SurfaceDark, RoundedCornerShape(4.dp))
                        .height(26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Replay",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Replay", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .background(KNetColors.BorderDark)
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select Option",
                        tint = Color.White,
                        modifier = Modifier
                            .clickable { }
                            .padding(horizontal = 4.dp)
                            .size(10.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    tint = KNetColors.TextSecondary,
                    modifier = Modifier
                        .clickable { }
                        .padding(horizontal = 4.dp)
                        .size(16.dp)
                )
            }
        }

        // Row 2: Connection details row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val details = listOf(
                Icons.Default.Language to "api.example.com",
                Icons.Default.Computer to "93.184.216.34:443",
                Icons.Default.Schedule to "10:15:23",
                Icons.Default.FlashOn to "3.45 s",
                Icons.Default.ArrowDownward to "1.12 KB / 83 ms"
            )
            details.forEach { (icon, text) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = KNetColors.TextSecondary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = text,
                        color = KNetColors.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Row 3: Detail Panel Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                "Overview", "Request", "Response", "Timeline", "Headers (12)",
                "Cookies (2)", "Auth", "WebSocket", "HTTP/2", "gRPC"
            )
            tabs.forEach { tab ->
                val isSelected = tab == "Request"
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(IntrinsicSize.Min)
                        .clickable { }
                ) {
                    Text(
                        text = tab,
                        color = if (isSelected) Color.White else KNetColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
