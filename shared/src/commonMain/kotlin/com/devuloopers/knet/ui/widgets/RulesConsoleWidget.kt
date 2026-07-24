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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.data.MockRule
import com.devuloopers.knet.ui.theme.KNetColors

/**
 * Rules Console Widget. Manages breakpoint lists, rewrite rules, showing hit counts,
 * last hits, and enable/disable toggles in the bottom tray.
 *
 * Meticulously matches KNet's bottom-panel rules console from HTML.
 */
@Composable
fun RulesConsoleWidget(
    rules: List<MockRule>,
    modifier: Modifier = Modifier
) {
    SubFrame(
        headerContent = {
            // Horizontal list of tabs with notification counts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf(
                    "Breakpoints" to 3,
                    "Replay Queue" to 2,
                    "Rewrite Rules" to 8,
                    "Throttle Profiles" to 3,
                    "Sessions" to 5,
                    "Collections" to 0,
                    "Diff" to 0,
                    "Console" to 0
                )
                tabs.forEach { (tab, count) ->
                    val isSelected = tab == "Breakpoints"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = tab,
                            color = if (isSelected) Color.White else KNetColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.drawBehind {
                                if (isSelected) {
                                    val strokeWidth = 2.dp.toPx()
                                    val y = size.height + 4.dp.toPx()
                                    drawLine(
                                        color = KNetColors.ActiveBlue,
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = strokeWidth
                                    )
                                }
                            }
                        )
                        if (count > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(
                                        color = if (isSelected) KNetColors.ErrorRed else Color(0xFF21262D),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = count.toString(),
                                    color = if (isSelected) Color.White else KNetColors.TextSecondary,
                                    style = TextStyle.Default.copy(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 8.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Table Columns Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KNetColors.BackgroundDark)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Name", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp))
                Text(text = "Type", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
                Text(text = "Condition", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(text = "Action", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                Text(text = "Enabled", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp))
                Text(text = "Hit Count", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(50.dp))
                Text(text = "Last Hit", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
                Text(text = "", color = KNetColors.TextSecondary, fontSize = 9.sp, modifier = Modifier.width(20.dp))
            }

            // Table rows list
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                rules.forEach { rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = rule.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp))
                        Text(text = rule.type, color = KNetColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                        Text(
                            text = rule.condition,
                            color = KNetColors.TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.weight(1f)
                        )
                        val actionColor = when (rule.action.lowercase()) {
                            "drop" -> KNetColors.ErrorRed
                            else -> KNetColors.ActiveBlue
                        }
                        Text(text = rule.action, color = actionColor, fontSize = 11.sp, modifier = Modifier.width(80.dp))

                        Box(modifier = Modifier.width(50.dp)) {
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = {},
                                modifier = Modifier.scaleScale()
                            )
                        }

                        Text(
                            text = rule.hitCount.toString(),
                            color = KNetColors.TextSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(50.dp)
                        )
                        Text(
                            text = "10:15:30",
                            color = KNetColors.TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.width(70.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = KNetColors.TextSecondary,
                            modifier = Modifier
                                .width(20.dp)
                                .clickable { }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.scaleScale(): Modifier = this
