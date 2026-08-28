package com.devuloopers.knet.companion.sharedui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.presentation.state.CompanionHomeReadiness
import com.devuloopers.knet.companion.presentation.state.CompanionHomeUiState
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CompanionHomeHeroCard(state: CompanionHomeUiState) {
    val statusColor = readinessColor(state.readiness)
    val duration = homeAnimationDuration()
    KNetSurface(
        modifier = Modifier.fillMaxWidth().widthIn(max = HomeContentWidth),
        color = KNetTheme.colors.surface,
        shape = KNetTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, KNetTheme.colors.border),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
        ) {
            AnimatedContent(
                targetState = state.readiness,
                transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
                label = "CompanionHomeReadinessBadge",
            ) { readiness -> HomeStatusBadge(readinessBadge(readiness), readinessColor(readiness)) }
            Crossfade(
                targetState = state.readiness,
                animationSpec = tween(duration),
                label = "CompanionHomeHeroCopy",
            ) { readiness ->
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
                ) {
                    Text(
                        text = stringResource(readinessTitle(readiness)),
                        style = KNetTheme.typography.hero,
                        color = KNetTheme.colors.textPrimary,
                    )
                    Text(
                        text = stringResource(readinessSummary(readiness)),
                        style = KNetTheme.typography.bodyLarge,
                        color = KNetTheme.colors.textSecondary,
                    )
                }
            }
            CompanionConnectionIllustration(state.readiness, statusColor)
        }
    }
}

@Composable
private fun CompanionConnectionIllustration(readiness: CompanionHomeReadiness, statusColor: Color) {
    val motion = KNetTheme.motion
    val infiniteTransition = rememberInfiniteTransition(label = "CompanionHomeConnectionPulse")
    val animatedPulse: State<Float> = if (
        motion.animationsEnabled && readiness in setOf(
            CompanionHomeReadiness.CHECKING,
            CompanionHomeReadiness.PREPARING,
            CompanionHomeReadiness.ACTIVE,
        )
    ) {
        infiniteTransition.animateFloat(
            initialValue = 0.82f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(1_200, easing = motion.easingStandard),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "CompanionHomeConnectionPulseScale",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    val lineColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = tween(homeAnimationDuration()),
        label = "CompanionHomeConnectionLineColor",
    )
    val description = stringResource(Res.string.home_illustration_description)
    Box(
        modifier = Modifier.fillMaxWidth().height(152.dp).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp)) {
            drawLine(
                color = lineColor.copy(alpha = 0.72f),
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                strokeWidth = 5f,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = KNetTheme.spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeEndpointNode(KNetIcons.Phone, lineColor)
            Box(
                modifier = Modifier.size(84.dp).graphicsLayer {
                    scaleX = animatedPulse.value
                    scaleY = animatedPulse.value
                }.clip(CircleShape).background(lineColor.copy(alpha = 0.12f))
                    .border(1.dp, lineColor.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KNetIcons.Lock, null, tint = lineColor, modifier = Modifier.size(38.dp))
            }
            HomeEndpointNode(KNetIcons.Desktop, lineColor)
        }
    }
}

@Composable
private fun HomeEndpointNode(icon: ImageVector, color: Color) {
    KNetSurface(
        modifier = Modifier.size(76.dp),
        color = KNetTheme.colors.surfaceVariant.copy(alpha = 0.72f),
        shape = KNetTheme.shapes.large,
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(38.dp))
    }
}

@Composable
private fun HomeStatusBadge(resource: StringResource, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(stringResource(resource), style = KNetTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun readinessColor(readiness: CompanionHomeReadiness): Color = when (readiness) {
    CompanionHomeReadiness.READY, CompanionHomeReadiness.ACTIVE -> KNetTheme.colors.semantic.success
    CompanionHomeReadiness.UNAVAILABLE -> KNetTheme.colors.semantic.warning
    CompanionHomeReadiness.NEEDS_ATTENTION -> KNetTheme.colors.semantic.error
    else -> KNetTheme.colors.accent
}

private fun readinessBadge(value: CompanionHomeReadiness): StringResource = when (value) {
    CompanionHomeReadiness.CHECKING -> Res.string.home_badge_checking
    CompanionHomeReadiness.READY -> Res.string.home_badge_ready
    CompanionHomeReadiness.PREPARING -> Res.string.home_badge_preparing
    CompanionHomeReadiness.ACTIVE -> Res.string.home_badge_active
    CompanionHomeReadiness.UNAVAILABLE -> Res.string.home_badge_unavailable
    CompanionHomeReadiness.NEEDS_ATTENTION -> Res.string.home_badge_attention
}

private fun readinessTitle(value: CompanionHomeReadiness): StringResource = when (value) {
    CompanionHomeReadiness.CHECKING -> Res.string.home_checking_title
    CompanionHomeReadiness.READY -> Res.string.home_ready_title
    CompanionHomeReadiness.PREPARING -> Res.string.home_preparing_title
    CompanionHomeReadiness.ACTIVE -> Res.string.home_active_title
    CompanionHomeReadiness.UNAVAILABLE -> Res.string.home_unavailable_title
    CompanionHomeReadiness.NEEDS_ATTENTION -> Res.string.home_attention_title
}

private fun readinessSummary(value: CompanionHomeReadiness): StringResource = when (value) {
    CompanionHomeReadiness.CHECKING -> Res.string.home_checking_summary
    CompanionHomeReadiness.READY -> Res.string.home_ready_summary
    CompanionHomeReadiness.PREPARING -> Res.string.home_preparing_summary
    CompanionHomeReadiness.ACTIVE -> Res.string.home_active_summary
    CompanionHomeReadiness.UNAVAILABLE -> Res.string.home_unavailable_summary
    CompanionHomeReadiness.NEEDS_ATTENTION -> Res.string.home_attention_summary
}
