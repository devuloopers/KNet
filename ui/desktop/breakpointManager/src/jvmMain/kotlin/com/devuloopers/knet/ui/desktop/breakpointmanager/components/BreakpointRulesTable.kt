package com.devuloopers.knet.ui.desktop.breakpointmanager.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.breakpointmanager.model.BreakpointRuleUiModel

/**
 * High-density table of all configured breakpoint rules.
 */
@Composable
public fun BreakpointRulesTable(
    rules: List<BreakpointRuleUiModel>,
    onToggleStatus: (String) -> Unit,
    onEditRule: (BreakpointRuleUiModel) -> Unit,
    onDeleteRule: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, themeColors.border, RoundedCornerShape(8.dp))
            .background(themeColors.surface)
    ) {
        // Table Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(themeColors.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Status",
                style = typography.caption.copy(color = themeColors.textMuted, fontWeight = FontWeight.Bold),
                modifier = Modifier.width(70.dp),
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = "URL Pattern",
                style = typography.caption.copy(color = themeColors.textMuted, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Method",
                style = typography.caption.copy(color = themeColors.textMuted, fontWeight = FontWeight.Bold),
                modifier = Modifier.width(90.dp),
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = "Phase",
                style = typography.caption.copy(color = themeColors.textMuted, fontWeight = FontWeight.Bold),
                modifier = Modifier.width(100.dp),
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = "Actions",
                style = typography.caption.copy(color = themeColors.textMuted, fontWeight = FontWeight.Bold),
                modifier = Modifier.width(80.dp),
                maxLines = 1,
                softWrap = false
            )
        }

            // Table Content Rows
            if (rules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No breakpoint rules configured",
                        style = typography.bodyMedium.copy(color = themeColors.textMuted),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (index % 2 == 0) themeColors.surface
                                    else themeColors.surfaceVariant.copy(alpha = 0.2f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = themeColors.border.copy(alpha = 0.3f)
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Status Switch
                            Box(modifier = Modifier.width(70.dp)) {
                                KNetSwitch(
                                    checked = rule.enabled,
                                    onCheckedChange = { onToggleStatus(rule.id) }
                                )
                            }

                            // URL Pattern (Monospace)
                            Text(
                                text = rule.urlPattern,
                                style = typography.bodyMedium.copy(
                                    color = if (rule.enabled) themeColors.textPrimary else themeColors.textMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            // Method Badge
                            Box(modifier = Modifier.width(90.dp)) {
                                val methodLabel = rule.method?.name ?: "ALL"
                                val badgeColor = if (rule.method != null) {
                                    Color(rule.method.badgeColorHex)
                                } else {
                                    Color(0xFF4B5563) // Gray for ALL
                                }
                                Row(
                                    modifier = Modifier
                                        .background(badgeColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = methodLabel,
                                        color = badgeColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Phase Tag
                            Box(modifier = Modifier.width(100.dp)) {
                                val phaseLabel = when (rule.phase) {
                                    BreakpointPhase.REQUEST -> "Request"
                                    BreakpointPhase.RESPONSE -> "Response"
                                    BreakpointPhase.BOTH -> "Both"
                                }
                                Row(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                                        .border(1.dp, themeColors.border, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = phaseLabel,
                                        color = themeColors.textSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Action Buttons (Edit & Delete)
                            Row(
                                modifier = Modifier.width(80.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Rule",
                                    tint = themeColors.textMuted,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onEditRule(rule) }
                                        .handCursor()
                                        .padding(4.dp)
                                )
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Rule",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onDeleteRule(rule.id) }
                                        .handCursor()
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
