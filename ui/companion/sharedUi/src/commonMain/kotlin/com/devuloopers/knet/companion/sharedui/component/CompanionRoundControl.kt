package com.devuloopers.knet.companion.sharedui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Closed set of portable symbols used by the primary mobile control. */
internal enum class CompanionControlSymbol { POWER, STOP, SCAN, DOWNLOAD, SETTINGS, CHECK, RETRY, DESKTOP, SHIELD, LINK }
/** Color communicates verified/running state without converting Stop into a destructive operation. */
internal enum class CompanionControlTone { ACCENT, SUCCESS, MUTED, WARNING }
/** Visual state is independent from callbacks and cannot turn a busy indicator into a tappable action. */
internal data class CompanionRoundControlState(
    val symbol: CompanionControlSymbol,
    val label: String,
    val caption: String? = null,
    val tone: CompanionControlTone = CompanionControlTone.ACCENT,
    val busy: Boolean = false,
    val enabled: Boolean = true,
)

/** One accessible primary action, with its circle and caption sharing the same touch target. */
@Composable
internal fun CompanionRoundControl(state: CompanionRoundControlState, onClick: (() -> Unit)? = null) {
    val color = when (state.tone) {
        CompanionControlTone.ACCENT -> KNetTheme.colors.accent
        CompanionControlTone.SUCCESS -> KNetTheme.colors.semantic.success
        CompanionControlTone.MUTED -> KNetTheme.colors.textMuted
        CompanionControlTone.WARNING -> KNetTheme.colors.semantic.warning
    }
    val diameter = if (LocalCompanionCompactBody.current) 160.dp else 196.dp
    val rotation = companionProgressRotation(state.busy)
    val surface = KNetTheme.colors.surfaceVariant
    val background = KNetTheme.colors.background
    val fillAccent = KNetTheme.colors.accent
    val text = KNetTheme.colors.textPrimary
    val actionable = onClick != null && !state.busy
    val description = state.caption ?: state.label
    val interaction = if (actionable) Modifier.clickable(enabled = state.enabled, role = Role.Button,
        onClickLabel = description, onClick = onClick) else Modifier
    Column(Modifier.widthIn(max = 320.dp).then(interaction).semantics(mergeDescendants = true) {
        if (state.busy) progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        if (!state.enabled && !actionable) disabled()
    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(diameter + 16.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val inset = 8.dp.toPx()
                val ringSize = Size(size.width - inset * 2, size.height - inset * 2)
                val radius = ringSize.width / 2
                drawCircle(Brush.radialGradient(listOf(lerp(surface, fillAccent, .16f), lerp(background, surface, .7f))), radius)
                drawCircle(color.copy(alpha = if (state.busy) .22f else .95f), radius,
                    style = Stroke(3.5.dp.toPx()))
                if (state.busy) {
                    repeat(2) { index -> drawArc(color, rotation + index * 180, 104f, false,
                        Offset(inset, inset), ringSize, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)) }
                } else {
                    drawArc(Brush.sweepGradient(listOf(Color.Transparent, color.copy(alpha = .65f), Color.Transparent)),
                        -92f, 85f, false, Offset(1.dp.toPx(), 1.dp.toPx()),
                        Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()), style = Stroke(2.dp.toPx()))
                }
            }
            Column(Modifier.size(diameter - 28.dp).clip(CircleShape), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                CompanionControlGlyph(state.symbol, text, Modifier.size(if (LocalCompanionCompactBody.current) 46.dp else 56.dp))
                Text(state.label, style = KNetTheme.typography.display, color = if (state.symbol == CompanionControlSymbol.CHECK) color else text, textAlign = TextAlign.Center)
            }
        }
        state.caption?.let { Text(it, Modifier.fillMaxWidth(), style = KNetTheme.typography.heading,
            color = text, textAlign = TextAlign.Center) }
    }
}

/** Rotation exists only while progress is visible and motion is enabled; no timer runs in static states. */
@Composable
private fun companionProgressRotation(busy: Boolean): Float {
    if (!busy || !KNetTheme.motion.animationsEnabled) return -90f
    val transition = rememberInfiniteTransition(label = "Companion progress")
    return transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "Progress rotation").value
}

/** Shared small progress glyph obeying the same reduced-motion policy as the main circle. */
@Composable
internal fun CompanionProgressGlyph(color: Color, modifier: Modifier = Modifier) {
    val rotation = companionProgressRotation(true)
    Canvas(modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        drawArc(color, rotation, 260f, false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Outline power/stop/download marks are drawn as vectors; other symbols reuse the KNet icon catalog. */
@Composable
private fun CompanionControlGlyph(symbol: CompanionControlSymbol, color: Color, modifier: Modifier) {
    if (symbol in setOf(CompanionControlSymbol.POWER, CompanionControlSymbol.STOP, CompanionControlSymbol.DOWNLOAD)) {
        Canvas(modifier) {
            val stroke = 4.dp.toPx()
            when (symbol) {
                CompanionControlSymbol.POWER -> {
                    drawArc(color, -48f, 276f, false, Offset(size.width * .12f, size.height * .15f),
                        Size(size.width * .76f, size.height * .76f), style = Stroke(stroke, cap = StrokeCap.Round))
                    drawLine(color, Offset(size.width / 2, size.height * .06f), Offset(size.width / 2, size.height * .48f), stroke, StrokeCap.Round)
                }
                CompanionControlSymbol.STOP -> drawRoundRect(color, Offset(size.width * .21f, size.height * .21f),
                    Size(size.width * .58f, size.height * .58f), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                CompanionControlSymbol.DOWNLOAD -> {
                    drawLine(color, Offset(size.width / 2, 2.dp.toPx()), Offset(size.width / 2, size.height * .65f), stroke, StrokeCap.Round)
                    val path = Path().apply { moveTo(size.width * .28f, size.height * .45f); lineTo(size.width / 2, size.height * .67f); lineTo(size.width * .72f, size.height * .45f) }
                    drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    val tray = Path().apply { moveTo(size.width * .08f, size.height * .7f); lineTo(size.width * .08f, size.height * .94f); lineTo(size.width * .92f, size.height * .94f); lineTo(size.width * .92f, size.height * .7f) }
                    drawPath(tray, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                else -> Unit
            }
        }
    } else Icon(when (symbol) {
        CompanionControlSymbol.SCAN -> KNetIcons.QrCodeScanner
        CompanionControlSymbol.SETTINGS -> KNetIcons.Settings
        CompanionControlSymbol.CHECK -> KNetIcons.Check
        CompanionControlSymbol.RETRY -> KNetIcons.Refresh
        CompanionControlSymbol.DESKTOP -> KNetIcons.Desktop
        CompanionControlSymbol.LINK -> KNetIcons.Hub
        else -> KNetIcons.Shield
    }, null, modifier, tint = color)
}
