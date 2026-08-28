package com.devuloopers.knet.companion.sharedui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionHomeUiState
import com.devuloopers.knet.companion.sharedui.component.CompanionBrandHeader
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Reactive inspection Home. Its fixed header and stable card geometry prevent asynchronous state jumps. */
@Composable
internal fun CompanionHomeScreen(
    state: CompanionHomeUiState,
    onAction: (CompanionAction) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(KNetTheme.colors.background).safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            CompanionBrandHeader(
                modifier = Modifier.fillMaxWidth().widthIn(max = HomeContentWidth)
                    .padding(horizontal = KNetTheme.spacing.lg, vertical = KNetTheme.spacing.xl),
            )
            HorizontalDivider(color = KNetTheme.colors.border.copy(alpha = 0.7f))
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = KNetTheme.spacing.lg, vertical = KNetTheme.spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
            ) {
                CompanionHomeHeroCard(state)
                CompanionInspectionControlCard(state, onAction)
                CompanionReadinessSummary(state)
                CompanionInspectionConfiguration(state)
                CompanionHomePrivacyNote()
                Spacer(modifier = Modifier.height(KNetTheme.spacing.lg))
            }
        }
    }
}

@Composable
internal fun homeAnimationDuration(): Int =
    if (KNetTheme.motion.animationsEnabled) KNetTheme.motion.durationSlow else KNetTheme.motion.durationInstant

internal val HomeContentWidth = 560.dp
