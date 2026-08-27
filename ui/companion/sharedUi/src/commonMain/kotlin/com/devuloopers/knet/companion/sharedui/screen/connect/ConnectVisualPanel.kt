package com.devuloopers.knet.companion.sharedui.screen.connect

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionConnectVisualMode
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.component.DesktopQrScanIllustration
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.connect_illustration_description
import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScanner
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Stable responsive panel that crossfades between the onboarding illustration and native camera. */
@Composable
internal fun ConnectVisualPanel(
    mode: CompanionConnectVisualMode,
    state: CompanionUiState,
    scanner: CompanionInvitationScanner,
    onAction: (CompanionAction) -> Unit,
) {
    val motion = KNetTheme.motion
    val duration = if (motion.animationsEnabled) motion.durationNormal else motion.durationInstant
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val panelHeight = (maxWidth / 1.45f).coerceIn(220.dp, 300.dp)
        AnimatedContent(
            targetState = mode,
            modifier = Modifier.fillMaxWidth().height(panelHeight),
            transitionSpec = {
                fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
            },
            contentAlignment = Alignment.Center,
            label = "ConnectVisualPanel",
        ) { currentMode ->
            when (currentMode) {
                CompanionConnectVisualMode.Illustration -> DesktopQrScanIllustration(
                    contentDescription = stringResource(Res.string.connect_illustration_description),
                    modifier = Modifier.fillMaxSize(),
                )
                CompanionConnectVisualMode.Scanner -> InlineInvitationScanner(
                    state = state,
                    scanner = scanner,
                    onAction = onAction,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
