package com.devuloopers.knet.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors

/**
 * Key-value metadata row printed inside side panel inspectors.
 *
 * @param label The metadata descriptor label.
 * @param value The value string to display.
 */
@Composable
fun DetailItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = KNetColors.TextSecondary, fontSize = 11.sp)
        Text(text = value, color = KNetColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
