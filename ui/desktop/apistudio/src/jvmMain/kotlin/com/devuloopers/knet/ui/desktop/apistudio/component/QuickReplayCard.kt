package com.devuloopers.knet.ui.desktop.apistudio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.badge.MethodBadge
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes

/**
 * One-click request replay card widget.
 *
 * @param title Request title.
 * @param method HTTP method string.
 * @param url Request URL.
 * @param onReplay Callback when card is clicked.
 * @param modifier Layout modifier.
 */
@Composable
public fun QuickReplayCard(
    title: String,
    method: String,
    url: String,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.SurfaceDark, KNetShapes.Medium)
            .clickable { onReplay() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                color = KNetColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            MethodBadge(method = method)
        }
        Text(
            text = url,
            color = KNetColors.TextMuted,
            fontSize = 10.sp
        )
    }
}
