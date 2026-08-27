package com.devuloopers.knet.companion.sharedui.screen.certificate

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.devuloopers.knet.ui.core.foundation.color.Colors
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Decorative phone-to-desktop certificate download illustration rendered without remote assets. */
@Composable
internal fun CertificateDownloadIllustration(
    contentDescription: String,
    verified: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = KNetTheme.colors
    Box(
        modifier = modifier
            .clip(KNetTheme.shapes.extraLarge)
            .background(colors.background.copy(alpha = 0.52f))
            .semantics { this.contentDescription = contentDescription },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.014f
            drawCircle(
                color = colors.accent.copy(alpha = 0.07f),
                radius = size.minDimension * 0.44f,
                center = Offset(size.width * 0.5f, size.height * 0.54f),
            )
            drawCertificateDesktop(stroke, colors)
            drawCertificatePhone(stroke, colors, verified)
            repeat(5) { index ->
                drawCircle(
                    color = if (verified) colors.semantic.success else colors.accent,
                    radius = stroke * 0.55f,
                    center = Offset(
                        x = size.width * (0.475f + index * 0.025f),
                        y = size.height * 0.61f,
                    ),
                )
            }
        }
    }
}

private fun DrawScope.drawCertificateDesktop(stroke: Float, colors: Colors) {
    val desktop = Rect(
        left = size.width * 0.48f,
        top = size.height * 0.37f,
        right = size.width * 0.86f,
        bottom = size.height * 0.75f,
    )
    drawRoundRect(
        color = colors.textSecondary,
        topLeft = desktop.topLeft,
        size = desktop.size,
        cornerRadius = CornerRadius(stroke * 1.8f),
        style = Stroke(stroke),
    )
    drawRoundRect(
        color = colors.surfaceVariant,
        topLeft = desktop.topLeft + Offset(stroke * 1.5f, stroke * 1.5f),
        size = Size(desktop.width - stroke * 3f, desktop.height - stroke * 3f),
        cornerRadius = CornerRadius(stroke),
    )
    drawRoundRect(
        color = colors.textSecondary,
        topLeft = Offset(size.width * 0.44f, size.height * 0.76f),
        size = Size(size.width * 0.46f, stroke * 1.6f),
        cornerRadius = CornerRadius(stroke),
    )

    val document = Rect(
        left = desktop.left + desktop.width * 0.38f,
        top = desktop.top + desktop.height * 0.18f,
        right = desktop.left + desktop.width * 0.68f,
        bottom = desktop.top + desktop.height * 0.75f,
    )
    drawRoundRect(
        color = colors.textPrimary,
        topLeft = document.topLeft,
        size = document.size,
        cornerRadius = CornerRadius(stroke),
        style = Stroke(stroke * 0.72f),
    )
    repeat(3) { index ->
        drawLine(
            color = colors.textPrimary,
            start = Offset(document.left + document.width * 0.2f, document.top + document.height * (0.28f + index * 0.15f)),
            end = Offset(document.right - document.width * 0.2f, document.top + document.height * (0.28f + index * 0.15f)),
            strokeWidth = stroke * 0.55f,
        )
    }
    drawCircle(
        color = colors.textPrimary,
        radius = document.width * 0.17f,
        center = Offset(document.right, document.bottom),
        style = Stroke(stroke * 0.65f),
    )
}

private fun DrawScope.drawCertificatePhone(stroke: Float, colors: Colors, verified: Boolean) {
    val phone = Rect(
        left = size.width * 0.17f,
        top = size.height * 0.2f,
        right = size.width * 0.48f,
        bottom = size.height * 0.84f,
    )
    drawRoundRect(
        color = colors.textSecondary,
        topLeft = phone.topLeft,
        size = phone.size,
        cornerRadius = CornerRadius(stroke * 3.5f),
        style = Stroke(stroke),
    )
    drawRoundRect(
        color = colors.surfaceVariant,
        topLeft = phone.topLeft + Offset(stroke * 1.5f, stroke * 2.8f),
        size = Size(phone.width - stroke * 3f, phone.height - stroke * 5.6f),
        cornerRadius = CornerRadius(stroke * 2.3f),
    )
    drawLine(
        color = colors.textMuted,
        start = Offset(phone.left + phone.width * 0.38f, phone.top + stroke * 1.7f),
        end = Offset(phone.right - phone.width * 0.38f, phone.top + stroke * 1.7f),
        strokeWidth = stroke * 0.55f,
    )

    val shieldCenter = Offset(phone.center.x, phone.top + phone.height * 0.36f)
    val shieldWidth = phone.width * 0.38f
    val shieldHeight = phone.height * 0.22f
    val shield = Path().apply {
        moveTo(shieldCenter.x, shieldCenter.y - shieldHeight * 0.5f)
        lineTo(shieldCenter.x + shieldWidth * 0.5f, shieldCenter.y - shieldHeight * 0.28f)
        lineTo(shieldCenter.x + shieldWidth * 0.43f, shieldCenter.y + shieldHeight * 0.22f)
        quadraticTo(shieldCenter.x, shieldCenter.y + shieldHeight * 0.55f, shieldCenter.x - shieldWidth * 0.43f, shieldCenter.y + shieldHeight * 0.22f)
        lineTo(shieldCenter.x - shieldWidth * 0.5f, shieldCenter.y - shieldHeight * 0.28f)
        close()
    }
    val shieldColor = if (verified) colors.semantic.success else colors.accent
    drawPath(shield, color = shieldColor, style = Stroke(stroke * 0.85f))
    if (verified) {
        drawLine(
            color = colors.semantic.success,
            start = Offset(shieldCenter.x - shieldWidth * 0.18f, shieldCenter.y),
            end = Offset(shieldCenter.x - shieldWidth * 0.03f, shieldCenter.y + shieldHeight * 0.14f),
            strokeWidth = stroke * 0.75f,
        )
        drawLine(
            color = colors.semantic.success,
            start = Offset(shieldCenter.x - shieldWidth * 0.03f, shieldCenter.y + shieldHeight * 0.14f),
            end = Offset(shieldCenter.x + shieldWidth * 0.22f, shieldCenter.y - shieldHeight * 0.14f),
            strokeWidth = stroke * 0.75f,
        )
    } else {
        drawLine(
            color = colors.accent,
            start = Offset(shieldCenter.x, shieldCenter.y - shieldHeight * 0.16f),
            end = Offset(shieldCenter.x, shieldCenter.y + shieldHeight * 0.18f),
            strokeWidth = stroke * 0.75f,
        )
        drawLine(
            color = colors.accent,
            start = Offset(shieldCenter.x, shieldCenter.y + shieldHeight * 0.18f),
            end = Offset(shieldCenter.x - shieldWidth * 0.1f, shieldCenter.y + shieldHeight * 0.06f),
            strokeWidth = stroke * 0.75f,
        )
        drawLine(
            color = colors.accent,
            start = Offset(shieldCenter.x, shieldCenter.y + shieldHeight * 0.18f),
            end = Offset(shieldCenter.x + shieldWidth * 0.1f, shieldCenter.y + shieldHeight * 0.06f),
            strokeWidth = stroke * 0.75f,
        )
    }

    drawRoundRect(
        color = colors.accent,
        topLeft = Offset(phone.left + phone.width * 0.2f, phone.bottom - phone.height * 0.16f),
        size = Size(phone.width * 0.6f, phone.height * 0.07f),
        cornerRadius = CornerRadius(stroke * 2f),
    )
}
