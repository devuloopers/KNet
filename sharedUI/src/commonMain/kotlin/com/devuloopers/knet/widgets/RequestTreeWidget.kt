package com.devuloopers.knet.widgets

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.components.ParameterNode
import com.devuloopers.knet.domain.inspector.model.TransactionUiModel
import com.devuloopers.knet.theme.KNetColors
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clipToBounds

/**
 * Unified, clean query parameters viewer displaying HTTP URL parameters in a two-column
 * key-value table with search filtering and a Copy Raw Query action button.
 *
 * @param transaction Selected transaction presentation model.
 * @param modifier Resizing constraints.
 */
@Composable
fun RequestTreeWidget(
    transaction: TransactionUiModel?,
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

    var searchQuery by remember { mutableStateOf("") }
    val rawQueryString = remember(transaction.queryParams) {
        transaction.queryParams.entries.joinToString("&") { (k, v) -> "$k=$v" }
    }

    SubFrame(
        modifier = modifier,
        resizeLeft = resizeLeft,
        resizeRight = resizeRight,
        resizeTop = resizeTop,
        resizeBottom = resizeBottom,
        headerContent = {
            // Header Bar: Search Input Box + Copy Raw Query Action Button
            Row(
                modifier = Modifier.fillMaxWidth().clipToBounds(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Input Box
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
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(KNetColors.ActiveBlue),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search parameters...",
                                            color = KNetColors.TextSecondary,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 14.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }

                if (rawQueryString.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CopyButton(textToCopy = rawQueryString, label = "Copy Raw Query")
                }
            }
        }
    ) {
        // Content Viewport: Unified Key-Value Table
        if (transaction.queryParams.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No query parameters in this request",
                    color = KNetColors.TextSecondary,
                    fontSize = 11.sp
                )
            }
        } else {
            val filteredParams = remember(transaction.queryParams, searchQuery) {
                if (searchQuery.isBlank()) {
                    transaction.queryParams
                } else {
                    transaction.queryParams.filter { (k, v) ->
                        k.contains(searchQuery, ignoreCase = true) || v.toString().contains(searchQuery, ignoreCase = true)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .background(KNetColors.SurfaceDark, RoundedCornerShape(4.dp))
                    .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Table Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "KEY", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.4f))
                    Text(text = "VALUE", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
                }

                if (filteredParams.isEmpty()) {
                    Text(
                        text = "No parameters match search query",
                        color = KNetColors.TextSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    filteredParams.forEach { (key, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = key,
                                color = KNetColors.ActiveBlue,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(0.4f)
                            )
                            Text(
                                text = value.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Recursively renders query parameters or nested object entries as ParameterNodes with depth indentation.
 */
@Composable
private fun RenderQueryParamsTree(params: Map<String, Any>, depth: Int) {
    params.forEach { (key, rawValue) ->
        if (rawValue is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val nestedMap = rawValue as? Map<String, Any>
            ParameterNode(depth = depth, name = key, value = null)
            if (nestedMap != null) {
                RenderQueryParamsTree(params = nestedMap, depth = depth + 1)
            }
        } else {
            ParameterNode(depth = depth, name = key, value = rawValue.toString())
        }
    }
}
