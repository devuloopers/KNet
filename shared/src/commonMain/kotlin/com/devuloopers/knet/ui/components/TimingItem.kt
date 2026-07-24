package com.devuloopers.knet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.theme.KNetColors

/**
 * Renders an individual timing metric with a visual horizontal progress bar.
 *
 * @param label The timing checkpoint label name (e.g. DNS Lookup).
 * @param value Timing display string value (e.g. 23 ms).
 * @param percentage Float between 0 and 1 representing the width share of the gauge.
 */
@Composable
fun TimingItem(
    label: String,
    value: String,
    percentage: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text(text = label, color = KNetColors.TextSecondary, fontSize = 10.sp)
            Text(text = value, color = KNetColors.TextPrimary, fontSize = 10.sp)
        }
        Spacer(modifier = Modifier.height(2.dp))
        // Progress horizontal bar representing timing
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(KNetColors.BorderDark, RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percentage)
                    .fillMaxHeight()
                    .background(KNetColors.ActiveBlue, RoundedCornerShape(2.dp))
            )
        }
    }
}
