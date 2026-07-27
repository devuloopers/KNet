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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.bodyformatter.model.BodyFormat
import com.devuloopers.knet.domain.inspector.model.TransactionUiModel
import com.devuloopers.knet.domain.inspector.model.responseContentTypeBadge
import com.devuloopers.knet.theme.KNetColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clipToBounds

/** Maximum payload size (2 MB) that will be formatted and rendered inline. */
private const val MAX_FORMATTABLE_SIZE_BYTES = 2 * 1024 * 1024

/**
 * Response payload viewer widget displaying formatted response body, HTTP status, and headers.
 *
 * @param transaction Selected transaction data model.
 * @param modifier Resizing constraints.
 */
@Composable
fun ResponseBodyWidget(
    transaction: TransactionUiModel?,
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

    // --- Large Payload Size Guard ---
    val bodySizeBytes = transaction.responseBody.length
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
                    CopyButton(textToCopy = transaction.responseBody, label = "Copy Raw")
                }
            }
        }
        return
    }

    var searchQuery by remember { mutableStateOf("") }

    // --- Off-Thread Formatting via produceState ---
    val formattingResult by produceState<FormattingResult>(
        initialValue = FormattingResult.Loading,
        key1 = transaction.responseBody,
        key2 = transaction.responseHeaders
    ) {
        value = withContext(Dispatchers.Default) {
            val format = com.devuloopers.knet.bodyformatter.formatter.BodyFormatterRegistry.resolveFormat(
                transaction.responseHeaders,
                transaction.responseBody
            )
            val pretty = com.devuloopers.knet.bodyformatter.formatter.BodyFormatterRegistry.prettyPrintBody(
                transaction.responseHeaders,
                transaction.responseBody
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
                Text(text = "Formatting response...", color = KNetColors.TextSecondary, fontSize = 10.sp)
            }
        }
        return
    }

    val readyResult = formattingResult as FormattingResult.Ready
    val prettyBody = readyResult.prettyBody
    val resolvedFormat = readyResult.format

    SubFrame(
        headerContent = {
            // Header Bar: Content-Type badge + Search Input Box + Copy Raw Body Button
            Row(
                modifier = Modifier.fillMaxWidth().clipToBounds(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Content Type Badge Pill (e.g. JSON, Form Data, HTML, PNG Image)
                Box(
                    modifier = Modifier
                        .background(KNetColors.ActiveBlue.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .border(1.dp, KNetColors.ActiveBlue.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = transaction.responseContentTypeBadge,
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
                                            text = "Search response body...",
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
                    CopyButton(textToCopy = transaction.responseBody, label = "Copy Raw")
                }
            }
        },
        modifier = modifier
    ) {
        // Content: Formatted Pretty Lines with Code Folding and Search Filtering
        Column(modifier = Modifier.fillMaxSize()) {
            CodeViewerWidget(
                codeText = prettyBody,
                bodyFormat = resolvedFormat,
                searchQuery = searchQuery,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Footer status info
            val formatLabel = resolvedFormat.badgeLabel
            val charset = remember(transaction.responseHeaders) {
                val contentType = transaction.responseHeaders.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
                if (contentType.contains("charset=", ignoreCase = true)) {
                    contentType.substringAfter("charset=", "").substringBefore(";").trim().uppercase()
                } else "UTF-8"
            }
            val lineEnding = remember(transaction.responseBody) {
                if (transaction.responseBody.contains("\r\n")) "CRLF" else "LF"
            }
            val bodySize = remember(transaction.responseBody) {
                val bytes = transaction.responseBody.encodeToByteArray().size
                when {
                    bytes < 1024 -> "Res: $bytes B"
                    bytes < 1024 * 1024 -> "Res: %.2f KB".format(bytes / 1024.0)
                    else -> "Res: %.2f MB".format(bytes / (1024.0 * 1024.0))
                }
            }
            val statusColor = when (transaction.status) {
                in 200..299 -> KNetColors.SuccessGreen
                in 300..399 -> KNetColors.ActiveBlue
                else -> Color(0xFFFF6B6B)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .horizontalScroll(rememberScrollState())
                    .clipToBounds(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = formatLabel, color = KNetColors.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                    Text(text = charset, color = KNetColors.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                    Text(text = lineEnding, color = KNetColors.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                    Text(text = bodySize, color = KNetColors.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                    Text(text = "${transaction.status} ${transaction.statusText}".trim(), color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                    Text(text = transaction.time, color = KNetColors.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
