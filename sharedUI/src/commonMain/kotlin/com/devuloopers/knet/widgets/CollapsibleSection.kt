package com.devuloopers.knet.widgets

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors

/**
 * Reusable Collapsible Header Section widget for categorizing lists (such as UNSAVED SESSIONS and SAVED COLLECTIONS)
 * with animated expansion state, badge counters, and customizable trailing header action controls.
 *
 * @param title Header title label text displayed on the section bar.
 * @param badgeCount Optional integer count displayed inside a stylized status badge.
 * @param isExpandedInitially Initial expansion state flag (defaults to true).
 * @param badgeColor Accent color used for the status badge background and text.
 * @param trailingContent Optional trailing composable slot for action icons or buttons.
 * @param content Child composable content rendered when expanded.
 */
@Composable
fun CollapsibleSection(
    title: String,
    badgeCount: Int? = null,
    isExpandedInitially: Boolean = true,
    badgeColor: Color = KNetColors.ActiveBlue,
    trailingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var isExpanded by remember { mutableStateOf(isExpandedInitially) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KNetColors.FieldDark, RoundedCornerShape(6.dp))
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Collapse section" else "Expand section",
                    tint = KNetColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (badgeCount != null && badgeCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(badgeColor.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$badgeCount",
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }


            }

            if (trailingContent != null) {
                trailingContent()
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                content()
            }
        }
    }
}
