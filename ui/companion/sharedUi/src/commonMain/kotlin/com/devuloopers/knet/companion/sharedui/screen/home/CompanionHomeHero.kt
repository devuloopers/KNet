package com.devuloopers.knet.companion.sharedui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.presentation.state.CompanionHomeReadiness
import com.devuloopers.knet.companion.presentation.state.CompanionHomeUiState
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
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
