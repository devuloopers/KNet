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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.data.MockTransaction
import com.devuloopers.knet.ui.theme.KNetColors

/**
 * Isolated Traffic Feed Widget. Displays chronological Live Traffic list with protocol chips,
 * group headings, and request metadata tables.
 *
 * Meticulously matches KNet's left-sidebar layout from HTML.
 *
 * @param transactions The mock transaction dataset.
 * @param selectedTransaction The currently selected item.
 * @param onTransactionSelected Selection change callback.
 * @param modifier Resizing constraints.
 */
@Composable
fun TrafficFeedWidget(
    transactions: List<MockTransaction>,
    selectedTransaction: MockTransaction?,
    onTransactionSelected: (MockTransaction) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = listOf("All", "HTTP", "HTTPS", "WebSocket", "HTTP/2", "gRPC")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.BackgroundDark)
    ) {
        // 1. Header toolbar matching HTML mockup (with Material 3 Icons)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Live Traffic",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                // Count badge
                Box(
                    modifier = Modifier
                        .background(KNetColors.BorderDark, RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "312",
                        color = KNetColors.TextPrimary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = "Pause",
                    tint = KNetColors.TextSecondary,
                    modifier = Modifier.size(13.dp).clickable { }
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = KNetColors.TextSecondary,
                    modifier = Modifier.size(13.dp).clickable { }
                )
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear",
                    tint = KNetColors.TextSecondary,
                    modifier = Modifier.size(13.dp).clickable { }
                )
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    tint = KNetColors.TextSecondary,
                    modifier = Modifier.size(13.dp).clickable { }
                )
            }
        }

        // 2. Search Filter input
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
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
                        text = "Filter by host, path, method...",
                        color = KNetColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filter Options",
                    tint = KNetColors.TextSecondary,
                    modifier = Modifier.size(12.dp).clickable { }
                )
            }
        }

        // 3. Filters chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            filters.forEach { filter ->
                val isSelected = filter == "All"
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSelected) KNetColors.ActiveBlue else KNetColors.BorderDark,
                            shape = RoundedCornerShape(3.dp)
                        )
                        .clickable { }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = filter,
                        color = if (isSelected) Color.White else KNetColors.TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. Table Columns Header strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, KNetColors.BorderDark)
                .background(KNetColors.BackgroundDark)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "#", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.width(24.dp))
            Text(text = "Method", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.width(54.dp))
            Text(text = "Host", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.width(100.dp))
            Text(text = "Path", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.weight(1f))
            Text(text = "Status", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Right, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.width(42.dp))
            Text(text = "Time", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Right, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.width(60.dp))
            Text(text = "Size", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Right, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.width(48.dp))
        }

        // 5. Transactions Feed list
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand",
                            tint = KNetColors.TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Today — May 23, 2025",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(text = "120", color = KNetColors.TextSecondary, fontSize = 9.sp)
                }
            }

            items(transactions) { tx ->
                val isSelected = tx.id == selectedTransaction?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .background(if (isSelected) KNetColors.ActiveBlue.copy(alpha = 0.1f) else Color.Transparent)
                        .clickable { onTransactionSelected(tx) }
                ) {
                    // Left blue line indicator
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .background(if (isSelected) KNetColors.ActiveBlue else Color.Transparent)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .border(width = 0.dp, color = Color.Transparent),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tx.id.toString(),
                            color = KNetColors.TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            modifier = Modifier.width(24.dp)
                        )
                        val methodColor = when (tx.method.uppercase()) {
                            "GET" -> KNetColors.SuccessGreen
                            "POST" -> KNetColors.ErrorRed
                            "WS" -> KNetColors.PurpleWS
                            else -> KNetColors.TextSecondary
                        }
                        Text(
                            text = tx.method,
                            color = methodColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.width(54.dp)
                        )
                        Text(
                            text = tx.host,
                            color = KNetColors.TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(100.dp)
                        )
                        Text(
                            text = tx.path,
                            color = if (isSelected) Color.White else KNetColors.TextPrimary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        val statusColor = when (tx.status) {
                            in 200..299 -> KNetColors.SuccessGreen
                            101 -> KNetColors.SuccessGreen
                            else -> KNetColors.ErrorRed
                        }
                        Text(
                            text = tx.status.toString(),
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            maxLines = 1,
                            modifier = Modifier.width(42.dp)
                        )
                        Text(
                            text = tx.time,
                            color = KNetColors.TextSecondary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Right,
                            maxLines = 1,
                            modifier = Modifier.width(60.dp)
                        )
                        Text(
                            text = tx.size,
                            color = KNetColors.TextSecondary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Right,
                            maxLines = 1,
                            modifier = Modifier.width(48.dp)
                        )
                    }
                }
            }
        }

        // 6. Summary Footer bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(KNetColors.BackgroundDark)
                .border(1.dp, KNetColors.BorderDark)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Total: 312 requests", color = KNetColors.TextSecondary, fontSize = 9.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "12.4 MB", color = KNetColors.TextSecondary, fontSize = 9.sp)
                Text(text = "3m 42s", color = KNetColors.TextSecondary, fontSize = 9.sp)
            }
        }
    }
}
