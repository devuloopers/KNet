package com.devuloopers.knet.companion.sharedui.screen.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.devuloopers.knet.companion.presentation.state.CompanionHomeReadiness
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * Layered home artwork: branded endpoints stay sharp while the connection and secure-tunnel node
 * remain reactive to runtime state. Keeping motion in Compose also honors reduced-motion settings.
 */
@Composable
internal fun CompanionConnectionIllustration(readiness: CompanionHomeReadiness, statusColor: Color) {
    val motion = KNetTheme.motion
    val shouldAnimate = motion.animationsEnabled && readiness in ANIMATED_READINESS
    val infiniteTransition = rememberInfiniteTransition(label = "CompanionHomeConnectionMotion")
    val pulseScale = connectionPulseScale(shouldAnimate, infiniteTransition)
    val dotPhase = connectionDotPhase(shouldAnimate, infiniteTransition)
    val connectionColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = tween(homeAnimationDuration()),
        label = "CompanionHomeConnectionColor",
    )
    val desktopAlpha by animateFloatAsState(
        targetValue = desktopTargetAlpha(readiness),
        animationSpec = tween(homeAnimationDuration()),
        label = "CompanionHomeDesktopAlpha",
    )
    val description = stringResource(Res.string.home_illustration_description)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ILLUSTRATION_ASPECT_RATIO)
            .semantics { contentDescription = description },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawConnectionDots(connectionColor, dotPhase.value)
            drawCircle(
                color = connectionColor.copy(alpha = 0.10f + ((pulseScale.value - PULSE_MINIMUM) * 1.5f)),
                radius = size.height * CONNECTION_HALO_RADIUS_FRACTION,
                center = illustrationLockCenter(),
            )
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.weight(MOBILE_SLOT_WEIGHT).fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Image(
                    painter = painterResource(Res.drawable.home_connection_mobile),
                    contentDescription = null,
                    modifier = Modifier.fillMaxHeight(MOBILE_HEIGHT_FRACTION),
                    contentScale = ContentScale.Fit,
                )
            }
            Box(
                modifier = Modifier.weight(LOCK_SLOT_WEIGHT).fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.home_connection_lock),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxHeight(LOCK_HEIGHT_FRACTION)
                        .graphicsLayer {
                            scaleX = pulseScale.value
                            scaleY = pulseScale.value
                        },
                    contentScale = ContentScale.Fit,
                )
            }
            Box(
                modifier = Modifier.weight(DESKTOP_SLOT_WEIGHT).fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.home_connection_desktop),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxHeight(DESKTOP_HEIGHT_FRACTION)
                        .graphicsLayer { alpha = desktopAlpha },
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun connectionPulseScale(
    shouldAnimate: Boolean,
    transition: InfiniteTransition,
): State<Float> = if (shouldAnimate) {
    transition.animateFloat(
        initialValue = PULSE_MINIMUM,
        targetValue = PULSE_MAXIMUM,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_DURATION_MILLIS, easing = KNetTheme.motion.easingStandard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "CompanionHomeConnectionPulseScale",
    )
} else {
    remember { mutableFloatStateOf(1f) }
}

@Composable
private fun connectionDotPhase(
    shouldAnimate: Boolean,
    transition: InfiniteTransition,
): State<Float> = if (shouldAnimate) {
    transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(CONNECTION_TRAVEL_DURATION_MILLIS, easing = KNetTheme.motion.easingStandard),
            repeatMode = RepeatMode.Restart,
        ),
        label = "CompanionHomeConnectionDotPhase",
    )
} else {
    remember { mutableFloatStateOf(0f) }
}

private fun DrawScope.drawConnectionDots(color: Color, phase: Float) {
    val startX = size.width * MOBILE_SLOT_WEIGHT * MOBILE_CONNECTION_ANCHOR
    val endX = size.width * (
        MOBILE_SLOT_WEIGHT + LOCK_SLOT_WEIGHT + (DESKTOP_SLOT_WEIGHT * DESKTOP_CONNECTION_ANCHOR)
    )
    val centerY = size.height / 2f
    val spacing = (endX - startX) / (CONNECTION_DOT_COUNT - 1)

    repeat(CONNECTION_DOT_COUNT) { index ->
        val normalizedPosition = (index.toFloat() / CONNECTION_DOT_COUNT + phase) % 1f
        val emphasis = 1f - abs((normalizedPosition * 2f) - 1f)
        drawCircle(
            color = color.copy(alpha = 0.42f + (emphasis * 0.48f)),
            radius = size.height * (CONNECTION_DOT_RADIUS_FRACTION + emphasis * CONNECTION_DOT_PULSE_FRACTION),
            center = Offset(startX + (spacing * index), centerY),
        )
    }
}

private fun DrawScope.illustrationLockCenter(): Offset = Offset(
    x = size.width * (MOBILE_SLOT_WEIGHT + (LOCK_SLOT_WEIGHT / 2f)),
    y = size.height / 2f,
)

private fun desktopTargetAlpha(readiness: CompanionHomeReadiness): Float = when (readiness) {
    CompanionHomeReadiness.CHECKING -> DESKTOP_CHECKING_ALPHA
    CompanionHomeReadiness.UNAVAILABLE -> DESKTOP_UNAVAILABLE_ALPHA
    else -> 1f
}

private val ANIMATED_READINESS = setOf(
    CompanionHomeReadiness.CHECKING,
    CompanionHomeReadiness.PREPARING,
    CompanionHomeReadiness.ACTIVE,
)

private const val ILLUSTRATION_ASPECT_RATIO = 1.5f
private const val MOBILE_SLOT_WEIGHT = 0.24f
private const val LOCK_SLOT_WEIGHT = 0.28f
private const val DESKTOP_SLOT_WEIGHT = 0.48f
private const val MOBILE_HEIGHT_FRACTION = 0.55f
private const val LOCK_HEIGHT_FRACTION = 0.28f
private const val DESKTOP_HEIGHT_FRACTION = 0.58f
private const val MOBILE_CONNECTION_ANCHOR = 0.90f
private const val DESKTOP_CONNECTION_ANCHOR = 0.10f
private const val CONNECTION_HALO_RADIUS_FRACTION = 0.155f
private const val CONNECTION_DOT_RADIUS_FRACTION = 0.007f
private const val CONNECTION_DOT_PULSE_FRACTION = 0.003f
private const val CONNECTION_DOT_COUNT = 12
private const val PULSE_MINIMUM = 0.97f
private const val PULSE_MAXIMUM = 1.035f
private const val PULSE_DURATION_MILLIS = 1_200
private const val CONNECTION_TRAVEL_DURATION_MILLIS = 1_600
private const val DESKTOP_CHECKING_ALPHA = 0.78f
private const val DESKTOP_UNAVAILABLE_ALPHA = 0.52f
