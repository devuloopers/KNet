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
import com.devuloopers.knet.ui.theme.KNetColors

/**
 * Top Navigation Header containing App Branding, Active Navigation links, Proxy Status,
 * and quick-action icons.
 *
 * Meticulously replicates KNet's top-row toolbar with Material 3 Icons.
 * Move Widget Manager at the top as dropdown menu next to the Proxy Status indicator.
 */
@Composable
fun TopHeader(
    currentTab: String,
    onTabSelected: (String) -> Unit,
    visibleWidgets: Map<WidgetType, Boolean>,
    onToggleWidget: (WidgetType) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryTabs = listOf("Dashboard", "Live Traffic", "Sessions", "Collections")
    val secondaryTabs = listOf(
        "Breakpoints", "Rewrite Rules", "Map Local", "Map Remote",
        "WebSocket", "HTTP/2", "gRPC", "Certificates", "Settings"
    )

    var widgetManagerDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.BackgroundDark)
            .border(width = 1.dp, color = KNetColors.BorderDark, shape = RoundedCornerShape(0.dp))
    ) {
        // Upper line: Logo + Widget Manager + Proxy Status + Search + Icons (Height 48.dp matches h-12)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
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
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Network Debugging Proxy",
                    color = KNetColors.TextSecondary,
                    fontSize = 11.sp
                )
            }

            // Right side tools (Widget Manager Dropdown + Proxy Status + Search Box + Action Icons)
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                            fontWeight = FontWeight.Bold
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
                                            fontSize = 11.sp
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
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(KNetColors.SuccessGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Proxy: 127.0.0.1:8888",
                        color = KNetColors.TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Running",
                        color = KNetColors.SuccessGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 3. Search box with ⌘K badge
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                        .width(200.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = KNetColors.TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Search (Ctrl + K)",
                                color = KNetColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(KNetColors.FieldDark, RoundedCornerShape(3.dp))
                                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "⌘K",
                                color = KNetColors.TextSecondary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 4. Icon tools list matching SVG buttons (Material 3 Icons)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = KNetColors.TextSecondary,
                        modifier = Modifier.size(16.dp).clickable { }
                    )
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Help",
                        tint = KNetColors.TextSecondary,
                        modifier = Modifier.size(16.dp).clickable { }
                    )
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = KNetColors.TextSecondary,
                        modifier = Modifier.size(16.dp).clickable { }
                    )
                    Icon(
                        imageVector = Icons.Default.Brightness4,
                        contentDescription = "Theme",
                        tint = KNetColors.TextSecondary,
                        modifier = Modifier.size(16.dp).clickable { }
                    )
                }

                // Avatar Profile Indigo w-8 h-8
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF6366F1), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "K",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Lower line: Tabs list split by divider (Height 40.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Render Primary Tabs
            primaryTabs.forEach { tab ->
                val isSelected = tab == currentTab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(IntrinsicSize.Min)
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = tab,
                        color = if (isSelected) Color.White else KNetColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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

            // Render Secondary Tabs
            secondaryTabs.forEach { tab ->
                val isSelected = tab == currentTab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(IntrinsicSize.Min)
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = tab,
                        color = if (isSelected) Color.White else KNetColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
