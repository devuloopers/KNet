package com.devuloopers.knet.ui.apistudio.view.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.bodyformatter.formatter.BodyFormatterRegistry
import com.devuloopers.knet.domain.apistudio.usecase.ExecutionResult
import com.devuloopers.knet.editor.KNetCodeEditor
import com.devuloopers.knet.editor.model.EditorMode
import com.devuloopers.knet.theme.KNetColors
import com.devuloopers.knet.ui.apistudio.model.ResponsePresentation

/**
 * Response Body Tab content for the Response & Test panel.
 *
 * Renders the pre-computed HTTP response presentation model in read-only mode using [KNetCodeEditor],
 * providing syntax highlighting (JSON, XML, HTML, etc.), line numbering, code folding, and search.
 * Pure UI renderer performing zero string formatting or format detection during Compose render frames.
 *
 * @param latestResult The [ExecutionResult] from the last request run, or null.
 * @param presentation Pre-computed [ResponsePresentation] model built on background threads.
 * @param modifier Layout modifier applied to the container.
 */
@Composable
internal fun ResponseBodyTab(
    latestResult: ExecutionResult?,
    presentation: ResponsePresentation? = null,
    modifier: Modifier = Modifier
) {
    if (latestResult == null && presentation == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No response payload. Enter a URL and click 'Send Request'.",
                color = KNetColors.TextSecondary.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        return
    }

    if (latestResult?.errorMessage != null) {
        Box(modifier = modifier.fillMaxSize().padding(12.dp)) {
            Text(
                text = "Error: ${latestResult.errorMessage}",
                color = Color(0xFFEF4444),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        return
    }

    val rawBody = presentation?.rawBody ?: latestResult?.responseBody ?: ""
    if (rawBody.isBlank()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Response body is empty (0 bytes received).",
                color = KNetColors.TextSecondary.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        return
    }

    val resolvedFormat = presentation?.bodyFormat ?: remember(rawBody, latestResult?.headers) {
        BodyFormatterRegistry.resolveFormat(
            headers = latestResult?.headers ?: emptyMap(),
            bodyText = rawBody
        )
    }

    val prettyBody = presentation?.formattedBody ?: remember(rawBody, latestResult?.headers) {
        BodyFormatterRegistry.prettyPrintBody(
            headers = latestResult?.headers ?: emptyMap(),
            bodyText = rawBody
        )
    }

    KNetCodeEditor(
        code = prettyBody,
        mode = EditorMode.ReadOnly,
        bodyFormat = resolvedFormat,
        modifier = modifier.fillMaxSize()
    )
}
