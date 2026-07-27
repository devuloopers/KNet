package com.devuloopers.knet.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors

/**
 * Main App Navigation Top Header bar matching the styling and tabs from HTML mockup.
 *
 * Move Widget Manager at the top as dropdown menu next to the Proxy Status indicator.
 */
@Composable
fun TopHeader(
    currentTab: String,
    onTabSelected: (String) -> Unit,
    visibleWidgets: Map<WidgetType, Boolean>,
    onToggleWidget: (WidgetType) -> Unit,
    isProxyRunning: Boolean,
    proxyPort: Int,
    onToggleProxy: () -> Unit,
    onTrustCa: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryTabs = listOf("Live Traffic", "Sessions", "API Studio")
    val secondaryTabs = listOf("Rules", "Certificates", "Settings")

    var widgetManagerDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.BackgroundDark)
            .border(width = 1.dp, color = KNetColors.BorderDark, shape = RoundedCornerShape(0.dp))
            .clipToBounds()
    ) {
        // Upper line: Logo + Widget Manager + Proxy Status + Search + Icons (Height 48.dp matches h-12)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp)
                .clipToBounds(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // App Logo and title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(KNetColors.ActiveBlue, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "K",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "KNet",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Network Debugging Proxy",
                    color = KNetColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Right side tools (Widget Manager Dropdown + Proxy Status + Search Box + Action Icons)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clipToBounds()
            ) {
                // 1. Widget Manager Dropdown (Styled with Black background and Border)
                Box {
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                            .clickable { widgetManagerDropdownExpanded = !widgetManagerDropdownExpanded }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Widget Manager",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Dropdown Menu",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Dropdown menu list popup
                    DropdownMenu(
                        expanded = widgetManagerDropdownExpanded,
                        onDismissRequest = { widgetManagerDropdownExpanded = false },
                        modifier = Modifier
                            .background(KNetColors.SurfaceDark)
                            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                    ) {
                        WidgetType.values().forEach { widget ->
                            val isVisible = visibleWidgets[widget] ?: true
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isVisible) Icons.Default.Check else Icons.Default.Add,
                                            contentDescription = null,
                                            tint = if (isVisible) KNetColors.ActiveBlue else KNetColors.TextSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = widget.title,
                                            color = if (isVisible) Color.White else KNetColors.TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                },
                                onClick = {
                                    onToggleWidget(widget)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 2. Center Status Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                        .clickable { onToggleProxy() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (isProxyRunning) KNetColors.SuccessGreen else KNetColors.TextSecondary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Proxy: 127.0.0.1:$proxyPort",
                        color = KNetColors.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isProxyRunning) "Running" else "Stopped",
                        color = if (isProxyRunning) KNetColors.SuccessGreen else KNetColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Lower line: Tabs list split by divider (Height 40.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 16.dp)
                .horizontalScroll(rememberScrollState())
                .clipToBounds(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Render Primary Tabs
            primaryTabs.forEach { tab ->
                val isSelected = tab == currentTab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = tab,
                        color = if (isSelected) Color.White else KNetColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (isSelected) KNetColors.ActiveBlue else Color.Transparent)
                    )
                }
            }

            // Divider vertical bar
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .width(1.dp)
                    .height(16.dp)
                    .background(KNetColors.BorderDark)
            )

            // Render Secondary Tabs (Rules, Certificates, Settings)
            secondaryTabs.forEach { tab ->
                val isSelected = tab == currentTab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable {
                            if (tab == "Certificates") {
                                onTrustCa()
                            }
                            onTabSelected(tab)
                        }
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = tab,
                        color = if (isSelected) Color.White else KNetColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(vertical = 8.dp)
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
