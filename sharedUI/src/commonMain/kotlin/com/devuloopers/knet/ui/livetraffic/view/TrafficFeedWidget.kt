package com.devuloopers.knet.ui.livetraffic.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.livetraffic.model.LiveTrafficIntent
import com.devuloopers.knet.domain.livetraffic.model.LiveTrafficUiState
import com.devuloopers.knet.domain.livetraffic.model.ProtocolFilter
import com.devuloopers.knet.domain.livetraffic.model.TrafficItemUiState
import com.devuloopers.knet.theme.KNetColors

/**
 * Pure layout Composable view representing the Live Traffic feed list.
 * Adheres strictly to Clean Architecture by rendering pre-formatted UI states
 * and emitting user actions as [LiveTrafficIntent]s.
 *
 * @param state Immutable [LiveTrafficUiState] emitted by ViewModel.
 * @param onIntent Callback lambda emitting user intents.
 * @param modifier Layout modifiers.
 */
@Composable
fun TrafficFeedWidget(
    state: LiveTrafficUiState,
    onIntent: (LiveTrafficIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeFilter = when (state) {
        is LiveTrafficUiState.Success -> state.activeFilter
        is LiveTrafficUiState.Empty -> state.activeFilter
        else -> ProtocolFilter.ALL
    }

    val searchQuery = when (state) {
        is LiveTrafficUiState.Success -> state.searchQuery
        is LiveTrafficUiState.Empty -> state.searchQuery
        else -> ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.BackgroundDark)
    ) {
        // --- Toolbar Header Strip ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(KNetColors.SurfaceDark)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Feed",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Clear",
                color = KNetColors.ActiveBlue,
                fontSize = 11.sp,
                modifier = Modifier.clickable { onIntent(LiveTrafficIntent.ClearTraffic) }
            )
        }

        // --- Filter Chips Strip ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KNetColors.SurfaceDark)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProtocolFilter.entries.forEach { filter ->
                val isSelected = filter == activeFilter
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .background(
                            color = if (isSelected) KNetColors.ActiveBlue else KNetColors.SurfaceDark,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onIntent(LiveTrafficIntent.SelectProtocol(filter)) }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = filter.name,
                        color = if (isSelected) Color.White else KNetColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // --- Search Input Box ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .background(KNetColors.FieldDark, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = KNetColors.TextSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = { onIntent(LiveTrafficIntent.SearchQueryChanged(it)) },
                textStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(KNetColors.ActiveBlue),
                modifier = Modifier.weight(1f)
            )
            if (searchQuery.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear Search",
                    tint = KNetColors.TextSecondary,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onIntent(LiveTrafficIntent.SearchQueryChanged("")) }
                )
            }
        }

        // --- Table Column Header Row ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(KNetColors.SurfaceDark)
                .padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "#", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
            Text(text = "METHOD", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp))
            Text(text = "HOST", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(150.dp))
            Text(text = "PATH", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(text = "STATUS", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Right, modifier = Modifier.width(50.dp))
            Text(text = "TIME", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Right, modifier = Modifier.width(60.dp))
            Text(text = "SIZE", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Right, modifier = Modifier.width(55.dp))
        }

        // --- Body Content Rendering ---
        Box(modifier = Modifier.weight(1f)) {
            when (state) {
                is LiveTrafficUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(24.dp),
                        color = KNetColors.ActiveBlue,
                        strokeWidth = 2.dp
                    )
                }
                is LiveTrafficUiState.Empty -> {
                    Text(
                        text = "No traffic captured",
                        color = KNetColors.TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is LiveTrafficUiState.Success -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.items, key = { it.transactionId }) { item ->
                            TrafficItemRow(
                                item = item,
                                onSelect = { onIntent(LiveTrafficIntent.SelectTransaction(item.transactionId)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrafficItemRow(
    item: TrafficItemUiState,
    onSelect: () -> Unit
) {
    val methodColor = when (item.method.uppercase()) {
        "GET" -> KNetColors.SuccessGreen
        "POST" -> KNetColors.ErrorRed
        "WS" -> KNetColors.PurpleWS
        else -> KNetColors.TextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(if (item.isSelected) KNetColors.ActiveBlue.copy(alpha = 0.1f) else Color.Transparent)
            .clickable { onSelect() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(if (item.isSelected) KNetColors.ActiveBlue else Color.Transparent)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = item.id.toString(), color = KNetColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.width(36.dp))
        Text(text = item.method, color = methodColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp))
        Text(text = item.host, color = KNetColors.TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp))
        Text(text = item.path, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(text = item.status.toString(), color = if (item.status in 200..299) KNetColors.SuccessGreen else KNetColors.ErrorRed, fontSize = 10.sp, textAlign = TextAlign.Right, modifier = Modifier.width(50.dp))
        Text(text = item.formattedTime, color = KNetColors.TextSecondary, fontSize = 10.sp, textAlign = TextAlign.Right, modifier = Modifier.width(60.dp))
        Text(text = item.formattedSize, color = KNetColors.TextSecondary, fontSize = 10.sp, textAlign = TextAlign.Right, modifier = Modifier.width(55.dp))
    }
}
