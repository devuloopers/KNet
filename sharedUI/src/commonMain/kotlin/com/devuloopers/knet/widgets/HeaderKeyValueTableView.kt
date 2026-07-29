package com.devuloopers.knet.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors

/**
 * A reusable, high-performance Key-Value table widget for displaying HTTP Headers,
 * Cookies, and metadata key-value lists across KNet.
 *
 * @param headers Map of key-value pair strings to display.
 * @param emptyPlaceholder Text displayed when [headers] is empty.
 * @param keyColor Color applied to key labels (defaults to ActiveBlue).
 * @param valueColor Color applied to value labels (defaults to White).
 * @param modifier Layout modifier for custom sizing and styling.
 */
@Composable
fun HeaderKeyValueTableView(
    headers: Map<String, String>,
    emptyPlaceholder: String = "No headers available",
    keyColor: Color = KNetColors.ActiveBlue,
    valueColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    if (headers.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyPlaceholder,
                color = KNetColors.TextSecondary.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(headers.entries.toList()) { (key, value) ->
            HeaderKeyValueRow(
                key = key,
                value = value,
                keyColor = keyColor,
                valueColor = valueColor
            )
        }
    }
}

/**
 * Single row representation for a header key-value pair.
 */
@Composable
private fun HeaderKeyValueRow(
    key: String,
    value: String,
    keyColor: Color,
    valueColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KNetColors.FieldDark.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .border(0.5.dp, KNetColors.BorderDark.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = key,
            color = keyColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.4f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.6f)
        )
    }
}
