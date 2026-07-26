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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.inspector.model.TransactionUiModel
import com.devuloopers.knet.domain.inspector.model.responseContentTypeBadge
import com.devuloopers.knet.domain.utils.prettyPrintBody
import com.devuloopers.knet.domain.utils.prettyPrintJson
import com.devuloopers.knet.theme.KNetColors

import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clipToBounds

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

    var searchQuery by remember { mutableStateOf("") }
    val prettyBody = remember(transaction.responseBody) {
        prettyPrintBody(transaction.responseBody)
    }

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
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(KNetColors.ActiveBlue),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(text = "Search response body...", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                }
                                innerTextField()
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
                searchQuery = searchQuery,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Footer status info
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
                    Text(text = "JSON", color = KNetColors.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                    Text(text = "UTF-8", color = KNetColors.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                    Text(text = "LF", color = KNetColors.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                    Text(text = "1.12 KB", color = KNetColors.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                    Text(text = "200 OK", color = KNetColors.SuccessGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                    Text(text = "83 ms", color = KNetColors.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
