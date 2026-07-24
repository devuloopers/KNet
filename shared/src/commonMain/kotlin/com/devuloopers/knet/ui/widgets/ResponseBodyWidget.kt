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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.data.MockTransaction
import com.devuloopers.knet.ui.theme.KNetColors

/**
 * Response payload body formatter sub-frame. Supports Response, Headers, and Hex tabs.
 *
 * Meticulously matches the styling and options from HTML mockup.
 */
@Composable
fun ResponseBodyWidget(
    transaction: MockTransaction?,
    modifier: Modifier = Modifier
) {
    if (transaction == null || transaction.responseBody.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().background(KNetColors.SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No response payload body", color = KNetColors.TextSecondary, fontSize = 11.sp)
        }
        return
    }

    SubFrame(
        headerContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Tabs
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val bodyTabs = listOf("Response", "Headers (10)", "Cookies (2)", "Raw", "Hex", "Preview")
                    bodyTabs.forEach { tab ->
                        val isSelected = tab == "Response"
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(IntrinsicSize.Min)
                                .clickable { }
                        ) {
                            Text(
                                text = tab,
                                color = if (isSelected) Color.White else KNetColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(if (isSelected) KNetColors.ActiveBlue else Color.Transparent)
                            )
                        }
                    }
                }

                // Right tools matching SVGs (Material 3 Icons)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "JSON", color = KNetColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Format",
                                tint = KNetColors.TextSecondary,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = KNetColors.TextSecondary,
                        modifier = Modifier.size(14.dp).clickable { }
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = KNetColors.TextSecondary,
                        modifier = Modifier.size(14.dp).clickable { }
                    )
                }
            }
        },
        modifier = modifier
    ) {
        // Content: JSON with line numbers & syntax highlighting
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(KNetColors.BackgroundDark, RoundedCornerShape(4.dp))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val lines = transaction.responseBody.split("\n")
                lines.forEachIndexed { index, lineText ->
                    JsonLineViewer(lineNumber = index + 1, lineText = lineText)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Footer status info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "JSON", color = KNetColors.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "UTF-8", color = KNetColors.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "LF", color = KNetColors.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "1.12 KB", color = KNetColors.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "200 OK", color = KNetColors.SuccessGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(text = "83 ms", color = KNetColors.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
