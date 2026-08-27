package com.devuloopers.knet.companion.sharedui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Decorative, non-scannable phone-to-desktop QR illustration rendered without remote assets. */
@Composable
internal fun DesktopQrScanIllustration(
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = KNetTheme.colors
    Box(
        modifier = modifier
            .clip(KNetTheme.shapes.extraLarge)
            .background(colors.background.copy(alpha = 0.45f))
            .semantics { this.contentDescription = contentDescription },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.018f
            val desktop = Rect(
                left = size.width * 0.33f,
                top = size.height * 0.24f,
                right = size.width * 0.83f,
                bottom = size.height * 0.68f,
            )
            drawRoundRect(
                color = colors.textSecondary,
                topLeft = desktop.topLeft,
                size = desktop.size,
                cornerRadius = CornerRadius(stroke * 1.7f),
                style = Stroke(stroke),
            )
            drawRoundRect(
                color = colors.surfaceVariant,
                topLeft = desktop.topLeft + Offset(stroke * 1.7f, stroke * 1.7f),
                size = Size(desktop.width - stroke * 3.4f, desktop.height - stroke * 3.4f),
                cornerRadius = CornerRadius(stroke),
            )
            drawRect(
                color = colors.textMuted,
                topLeft = Offset(size.width * 0.54f, size.height * 0.69f),
                size = Size(size.width * 0.08f, size.height * 0.07f),
            )
            drawRoundRect(
                color = colors.textSecondary,
                topLeft = Offset(size.width * 0.43f, size.height * 0.75f),
                size = Size(size.width * 0.3f, stroke * 1.4f),
                cornerRadius = CornerRadius(stroke),
            )

            val phone = Rect(
                left = size.width * 0.18f,
                top = size.height * 0.39f,
                right = size.width * 0.38f,
                bottom = size.height * 0.78f,
            )
            drawRoundRect(
                color = colors.textSecondary,
                topLeft = phone.topLeft,
                size = phone.size,
                cornerRadius = CornerRadius(stroke * 2.2f),
                style = Stroke(stroke),
            )
            drawRoundRect(
                color = colors.surfaceVariant,
                topLeft = phone.topLeft + Offset(stroke * 1.5f, stroke * 2.5f),
                size = Size(phone.width - stroke * 3f, phone.height - stroke * 4f),
                cornerRadius = CornerRadius(stroke),
            )

            val beam = Path().apply {
                moveTo(phone.right - stroke, phone.top + phone.height * 0.28f)
                lineTo(desktop.left + desktop.width * 0.55f, desktop.top + desktop.height * 0.34f)
                lineTo(desktop.left + desktop.width * 0.55f, desktop.top + desktop.height * 0.75f)
                lineTo(phone.right - stroke, phone.top + phone.height * 0.52f)
                close()
            }
            drawPath(beam, colors.accent.copy(alpha = 0.22f))

            val markerCenter = Offset(desktop.left + desktop.width * 0.59f, desktop.top + desktop.height * 0.52f)
            drawDecorativeScanMarker(markerCenter, desktop.width * 0.22f, stroke, colors.accent)
        }
    }
}

private fun DrawScope.drawDecorativeScanMarker(
    center: Offset,
    markerSize: Float,
    strokeWidth: Float,
    color: Color,
) {
    val half = markerSize / 2f
    val arm = markerSize * 0.3f
    val left = center.x - half
    val top = center.y - half
    val right = center.x + half
    val bottom = center.y + half
    listOf(
        Offset(left, top) to Offset(left + arm, top),
        Offset(left, top) to Offset(left, top + arm),
        Offset(right, top) to Offset(right - arm, top),
        Offset(right, top) to Offset(right, top + arm),
        Offset(left, bottom) to Offset(left + arm, bottom),
        Offset(left, bottom) to Offset(left, bottom - arm),
        Offset(right, bottom) to Offset(right - arm, bottom),
        Offset(right, bottom) to Offset(right, bottom - arm),
    ).forEach { (start, end) ->
        drawLine(color = color, start = start, end = end, strokeWidth = strokeWidth)
    }
    drawCircle(color = color, radius = strokeWidth * 1.2f, center = center)
}
