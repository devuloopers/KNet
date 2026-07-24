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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.data.MockTransaction
import com.devuloopers.knet.ui.theme.KNetColors

/**
 * Request payload body formatter sub-frame. Supports Pretty, Raw, and Hex tabs.
 *
 * Meticulously matches the styling and options from HTML mockup.
 */
@Composable
fun RequestBodyWidget(
    transaction: MockTransaction?,
    modifier: Modifier = Modifier,
    resizeLeft: ((Dp) -> Unit)? = null,
    resizeRight: ((Dp) -> Unit)? = null,
    resizeTop: ((Dp) -> Unit)? = null,
    resizeBottom: ((Dp) -> Unit)? = null
) {
    if (transaction == null || transaction.requestBody.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().background(KNetColors.SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No request payload body", color = KNetColors.TextSecondary, fontSize = 11.sp)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Tabs
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val bodyTabs = listOf("Pretty", "Raw", "Hex", "Preview")
                    bodyTabs.forEach { tab ->
                        val isSelected = tab == "Pretty"
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

                // Right tools matching SVGs
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
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Format",
                        tint = KNetColors.TextSecondary,
                        modifier = Modifier.size(14.dp).clickable { }
                    )
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save",
                        tint = KNetColors.TextSecondary,
                        modifier = Modifier.size(14.dp).clickable { }
                    )
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = KNetColors.TextSecondary,
                        modifier = Modifier.size(14.dp).clickable { }
                    )
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Full Screen",
                        tint = KNetColors.TextSecondary,
                        modifier = Modifier.size(14.dp).clickable { }
                    )
                }
            }
        }
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
                val lines = transaction.requestBody.split("\n")
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
                Text(
                    text = "JSON  |  UTF-8  |  LF  |  72 bytes",
                    color = KNetColors.TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Valid",
                        tint = KNetColors.SuccessGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Valid JSON",
                        color = KNetColors.SuccessGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Line by line JSON syntax highlighter.
 */
@Composable
fun JsonLineViewer(lineNumber: Int, lineText: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Line number
        Text(
            text = lineNumber.toString(),
            color = Color(0xFF484F58),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.End
        )
        Spacer(modifier = Modifier.width(12.dp))

        // Syntax highlight
        val keyColor = Color(0xFF79C0FF)
        val stringColor = Color(0xFFA5D6FF)
        val numberColor = Color(0xFFD2A8FF)
        val booleanColor = Color(0xFFFFAB70)

        val trimmed = lineText.trim()
        if (trimmed.startsWith("\"") && trimmed.contains(":")) {
            val parts = lineText.split(":", limit = 2)
            val keyPart = parts[0]
            val valPart = parts[1]

            Row {
                Text(text = keyPart, color = keyColor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Text(text = ":", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)

                val trimmedVal = valPart.trim()
                val valColor = when {
                    trimmedVal.startsWith("\"") -> stringColor
                    trimmedVal.startsWith("true") || trimmedVal.startsWith("false") -> booleanColor
                    trimmedVal.firstOrNull()?.isDigit() == true -> numberColor
                    else -> Color.White
                }
                Text(text = valPart, color = valColor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        } else {
            Text(text = lineText, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}
