package com.devuloopers.knet.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors

/**
 * Isolated Quick Replay Widget. Controls concurrency limits, count targets,
 * import/export states, and scheduling replays.
 *
 * Meticulously matches KNet's bottom-right panel from HTML.
 */
@Composable
fun QuickReplayWidget(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.SurfaceDark)
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
            .clipToBounds(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Row 1: Import / Export Buttons (Material 3 Icons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                        .clickable { }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Publish,
                            contentDescription = "Import",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Import", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                        .clickable { }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Export",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Export", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KNetColors.BorderDark))
            Spacer(modifier = Modifier.height(8.dp))

            // Replay Header Collapsible
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "REPLAY", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Expand",
                    tint = KNetColors.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Spinner Input count fields
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SpinnerField(label = "Count", value = 1)
                SpinnerField(label = "Concurrency", value = 1)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons Row (Replay & Schedule)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = KNetColors.ActiveBlue),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .weight(1.3f)
                    .height(30.dp)
            ) {
                Text(text = "Replay", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = KNetColors.BackgroundDark),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .weight(1.7f)
                    .height(30.dp)
                    .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Schedule",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Schedule", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Reusable spinner input selector.
 */
@Composable
fun SpinnerField(label: String, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = KNetColors.TextSecondary, fontSize = 11.sp)

        // Spinner Box
        Row(
            modifier = Modifier
                .width(80.dp)
                .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value.toString(),
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            // Spin arrow controls (Material 3 Icons)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.ArrowDropUp,
                    contentDescription = "Increment",
                    tint = KNetColors.TextSecondary,
                    modifier = Modifier.size(12.dp).clickable { }
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Decrement",
                    tint = KNetColors.TextSecondary,
                    modifier = Modifier.size(12.dp).clickable { }
                )
            }
        }
    }
}
