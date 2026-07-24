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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.components.ParameterNode
import com.devuloopers.knet.ui.data.MockTransaction
import com.devuloopers.knet.ui.theme.KNetColors

/**
 * Request details sub-frame. Houses the request header sub-tabs (Headers, Query Params, Cookies)
 * and displays the dynamic parameter tree matching the target design.
 */
@Composable
fun RequestTreeWidget(
    transaction: MockTransaction?,
    modifier: Modifier = Modifier,
    resizeLeft: ((Dp) -> Unit)? = null,
    resizeRight: ((Dp) -> Unit)? = null,
    resizeTop: ((Dp) -> Unit)? = null,
    resizeBottom: ((Dp) -> Unit)? = null
) {
    if (transaction == null) {
        Box(
            modifier = modifier.fillMaxSize().background(KNetColors.SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No request active", color = KNetColors.TextSecondary, fontSize = 11.sp)
        }
        return
    }

    SubFrame(
        modifier = modifier,
        resizeLeft = resizeLeft,
        resizeRight = resizeRight,
        resizeTop = resizeTop,
        resizeBottom = resizeBottom,
        headerContent = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Row 1: Sub Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf("Headers", "Query Params (3)", "Body", "Auth", "Cookies", "Raw")
                    tabs.forEach { tab ->
                        val isSelected = tab.startsWith("Query Params")
                        Text(
                            text = tab,
                            color = if (isSelected) Color.White else KNetColors.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .clickable { }
                                .padding(vertical = 4.dp)
                                .drawBehind {
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
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Row 2: Tools (Tree/Table, Smart View)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .background(KNetColors.ActiveBlue, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "Tree", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "Table", color = KNetColors.TextSecondary, fontSize = 9.sp)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "JSON", color = KNetColors.TextSecondary, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterVertically))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Raw", color = KNetColors.TextSecondary, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterVertically))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Smart View", color = KNetColors.TextSecondary, fontSize = 9.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = true,
                            onCheckedChange = {},
                            modifier = Modifier.scaleScale()
                        )
                    }
                }
            }
        }
    ) {
        // Content: Parameter tree
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Search Input Row (with Material 3 Icon)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = KNetColors.TextSecondary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Search parameters...", color = KNetColors.TextSecondary, fontSize = 10.sp)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                        .clickable { }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Parameter",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Add Parameter", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Tree Nodes list
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ParameterNode(depth = 0, name = "filter", value = null)
                ParameterNode(depth = 1, name = "user", value = null)
                ParameterNode(depth = 2, name = "name", value = "john")
                ParameterNode(depth = 2, name = "age", value = "25")
                ParameterNode(depth = 1, name = "address", value = null)
                ParameterNode(depth = 2, name = "city", value = "Delhi")
                ParameterNode(depth = 2, name = "country", value = "India")
                ParameterNode(depth = 0, name = "page", value = "1")
                ParameterNode(depth = 0, name = "limit", value = "10")
            }
        }
    }
}

@Composable
private fun Modifier.scaleScale(): Modifier = this
