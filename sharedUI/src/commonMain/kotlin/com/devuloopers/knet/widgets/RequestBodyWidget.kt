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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.devuloopers.knet.bodyformatter.model.BodyFormat
import com.devuloopers.knet.domain.inspector.model.TransactionUiModel
import com.devuloopers.knet.domain.inspector.model.requestContentTypeBadge
import com.devuloopers.knet.editor.KNetCodeEditor
import com.devuloopers.knet.theme.KNetColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clipToBounds

/** Maximum payload size (2 MB) that will be formatted and rendered inline. */
private const val MAX_FORMATTABLE_SIZE_BYTES = 2 * 1024 * 1024

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

    // --- Large Payload Size Guard ---
    val bodySizeBytes = transaction.requestBody.length
    if (bodySizeBytes > MAX_FORMATTABLE_SIZE_BYTES) {
        val sizeMb = "%.2f".format(bodySizeBytes / (1024.0 * 1024.0))
        Box(
            modifier = modifier.fillMaxSize().background(KNetColors.SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "\uD83D\uDEAB Payload too large to render inline",
                    color = KNetColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Size: $sizeMb MB (limit: 2 MB)",
                    color = KNetColors.TextSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CopyButton(textToCopy = transaction.requestBody, label = "Copy Raw")
                }
            }
        }
        return
    }

    var searchQuery by remember { mutableStateOf("") }

    // --- Off-Thread Formatting via produceState ---
    val formattingResult by produceState<FormattingResult>(
        initialValue = FormattingResult.Loading,
        key1 = transaction.requestBody,
        key2 = transaction.requestHeaders
    ) {
        value = withContext(Dispatchers.Default) {
            val format = com.devuloopers.knet.bodyformatter.formatter.BodyFormatterRegistry.resolveFormat(
                transaction.requestHeaders,
                transaction.requestBody
            )
            val pretty = com.devuloopers.knet.bodyformatter.formatter.BodyFormatterRegistry.prettyPrintBody(
                transaction.requestHeaders,
                transaction.requestBody
            )
            FormattingResult.Ready(prettyBody = pretty, format = format)
        }
    }

    // Show loading shimmer while formatting is computing off-thread
    if (formattingResult is FormattingResult.Loading) {
        Box(
            modifier = modifier.fillMaxSize().background(KNetColors.SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = KNetColors.ActiveBlue,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Formatting request...", color = KNetColors.TextSecondary, fontSize = 10.sp)
            }
        }
        return
    }

    val readyResult = formattingResult as FormattingResult.Ready
    val prettyBody = readyResult.prettyBody
    val format = readyResult.format

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
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(KNetColors.ActiveBlue),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search request body...",
                                            color = KNetColors.TextSecondary,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 14.sp
                                        )
                                    }
                                    innerTextField()
                                }
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
            if (format is com.devuloopers.knet.bodyformatter.model.BodyFormat.GraphQL) {
                var gqlSubTab by remember { mutableStateOf("Query") }
                val hasVariables = format.variablesJson.isNotEmpty()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KNetColors.SurfaceDark)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val gqlTabs = listOf(
                        "Query",
                        if (hasVariables) "Arguments" else "Arguments (Empty)",
                        "Raw JSON"
                    )
                    gqlTabs.forEach { tabLabel ->
                        val isQueryTab = tabLabel == "Query"
                        val isArgsTab = tabLabel.startsWith("Arguments")
                        val isSelected = when {
                            isQueryTab -> gqlSubTab == "Query"
                            isArgsTab -> gqlSubTab == "Arguments"
                            else -> gqlSubTab == "Raw JSON"
                        }
                        Box(
                            modifier = Modifier
                                .background(if (isSelected) KNetColors.ActiveBlue else Color.Transparent, RoundedCornerShape(4.dp))
                                .clickable {
                                    gqlSubTab = when {
                                        isQueryTab -> "Query"
                                        isArgsTab -> "Arguments"
                                        else -> "Raw JSON"
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = tabLabel,
                                color = if (isSelected) Color.White else KNetColors.TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                val activeCodeText = when (gqlSubTab) {
                    "Query" -> format.queryText
                    "Arguments" -> format.variablesJson.ifEmpty { "// No arguments provided for this operation" }
                    else -> transaction.requestBody
                }
                val activeFormat = when (gqlSubTab) {
                    "Arguments", "Raw JSON" -> com.devuloopers.knet.bodyformatter.model.BodyFormat.Json(activeCodeText)
                    else -> format
                }

                KNetCodeEditor(
                    code = activeCodeText,
                    mode = com.devuloopers.knet.editor.model.EditorMode.ReadOnly,
                    bodyFormat = activeFormat,
                    searchQuery = searchQuery,
                    modifier = Modifier.weight(1f)
                )
            } else {
                KNetCodeEditor(
                    code = prettyBody,
                    mode = com.devuloopers.knet.editor.model.EditorMode.ReadOnly,
                    bodyFormat = format,
                    searchQuery = searchQuery,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val reqFormatLabel = format.badgeLabel
            val reqCharset = remember(transaction.requestHeaders) {
                val contentType = transaction.requestHeaders.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
                if (contentType.contains("charset=", ignoreCase = true)) {
                    contentType.substringAfter("charset=", "").substringBefore(";").trim().uppercase()
                } else "UTF-8"
            }
            val reqLineEnding = remember(transaction.requestBody) {
                if (transaction.requestBody.contains("\r\n")) "CRLF" else "LF"
            }
            val reqBodySize = remember(transaction.requestBody) {
                val bytes = transaction.requestBody.encodeToByteArray().size
                when {
                    bytes < 1024 -> "Req: $bytes B"
                    bytes < 1024 * 1024 -> "Req: %.2f KB".format(bytes / 1024.0)
                    else -> "Req: %.2f MB".format(bytes / (1024.0 * 1024.0))
                }
            }

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
                    text = "$reqFormatLabel  |  $reqCharset  |  $reqLineEnding  |  $reqBodySize",
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
                    val validityText = when (format) {
                        is com.devuloopers.knet.bodyformatter.model.BodyFormat.Json -> "Valid JSON"
                        is com.devuloopers.knet.bodyformatter.model.BodyFormat.Html -> "Valid HTML"
                        is com.devuloopers.knet.bodyformatter.model.BodyFormat.Xml -> "Valid XML"
                        else -> "Raw Payload"
                    }
                    Text(
                        text = validityText,
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
