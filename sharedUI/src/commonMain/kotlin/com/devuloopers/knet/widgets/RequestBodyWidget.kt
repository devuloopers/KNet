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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.inspector.model.TransactionUiModel
import com.devuloopers.knet.domain.inspector.model.requestContentTypeBadge
import com.devuloopers.knet.domain.utils.prettyPrintBody
import com.devuloopers.knet.domain.utils.prettyPrintJson
import com.devuloopers.knet.theme.KNetColors
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clipToBounds

/**
 * Request body and parameter editor widget displaying query parameters, form headers, and pretty JSON.
 *
 * @param transaction Selected transaction presentation model.
 * @param modifier Resizing constraints.
 */
@Composable
fun RequestBodyWidget(
    transaction: TransactionUiModel?,
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

    var searchQuery by remember { mutableStateOf("") }
    val prettyBody = remember(transaction.requestBody) {
        prettyPrintBody(transaction.requestBody)
    }

    SubFrame(
        modifier = modifier,
        resizeLeft = resizeLeft,
        resizeRight = resizeRight,
        resizeTop = resizeTop,
        resizeBottom = resizeBottom,
        headerContent = {
            // Header Bar: Search Input Box + Copy Raw Body Button (matching Query Params strategy)
            Row(
                modifier = Modifier.fillMaxWidth().clipToBounds(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Content Type Badge Pill (e.g. JSON, Form Data, HTML)
                Box(
                    modifier = Modifier
                        .background(KNetColors.ActiveBlue.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .border(1.dp, KNetColors.ActiveBlue.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = transaction.requestContentTypeBadge,
                        color = KNetColors.ActiveBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

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
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(KNetColors.ActiveBlue),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(text = "Search request body...", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CopyButton(textToCopy = prettyBody, label = "Copy Formatted")
                    CopyButton(textToCopy = transaction.requestBody, label = "Copy Raw")
                }
            }
        }
    ) {
        // Content: Formatted Pretty Lines with Code Folding and Search Filtering
        Column(modifier = Modifier.fillMaxSize()) {
            CodeViewerWidget(
                codeText = prettyBody,
                searchQuery = searchQuery,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Footer status info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .clipToBounds(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "JSON  |  UTF-8  |  LF  |  72 bytes",
                    color = KNetColors.TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clipToBounds()
                ) {
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
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
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
