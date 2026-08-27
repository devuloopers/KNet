package com.devuloopers.knet.companion.sharedui.screen.connect

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionConnectScanState
import com.devuloopers.knet.companion.presentation.state.CompanionConnectVisualMode
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.presentation.state.toConnectUiState
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.cancel_scanning
import com.devuloopers.knet.companion.sharedui.generated.resources.connect_summary
import com.devuloopers.knet.companion.sharedui.generated.resources.connect_title
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_qr
import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScanner
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Clean QR-only entry screen whose illustration becomes an inline camera without changing routes. */
@Composable
internal fun ConnectDesktopScreen(
    state: CompanionUiState,
    scanner: CompanionInvitationScanner,
    onAction: (CompanionAction) -> Unit,
) {
    val renderState = state.toConnectUiState()
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KNetTheme.spacing.lg, vertical = KNetTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            KNetSurface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                color = KNetTheme.colors.surface,
                shape = KNetTheme.shapes.extraLarge,
                border = BorderStroke(1.dp, KNetTheme.colors.border),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xxl),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md),
                    ) {
                        Text(
                            text = stringResource(Res.string.connect_title),
                            style = KNetTheme.typography.hero,
                            color = KNetTheme.colors.textPrimary,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(Res.string.connect_summary),
                            style = KNetTheme.typography.bodyLarge,
                            color = KNetTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    ConnectVisualPanel(
                        mode = renderState.visualMode,
                        state = state,
                        scanner = scanner,
                        onAction = onAction,
                    )
                    val scannerVisible = renderState.visualMode == CompanionConnectVisualMode.Scanner
                    val scanEnabled = renderState.scanState !is CompanionConnectScanState.Disabled
                    val scanLoading = renderState.scanState is CompanionConnectScanState.Loading
                    KNetButton(
                        onClick = {
                            onAction(
                                if (scannerVisible) {
                                    CompanionAction.InvitationScannerDismissed
                                } else {
                                    CompanionAction.ScanInvitationRequested
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        variant = if (scannerVisible) ButtonVariant.Secondary else ButtonVariant.Primary,
                        size = ButtonSize.Touch,
                        enabled = scannerVisible || scanEnabled,
                        loading = !scannerVisible && scanLoading,
                    ) {
                        Icon(
                            imageVector = if (scannerVisible) KNetIcons.Close else KNetIcons.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.size(KNetTheme.spacing.sm))
                        Text(
                            stringResource(
                                if (scannerVisible) Res.string.cancel_scanning else Res.string.scan_qr,
                            ),
                        )
                    }
                    val failure = renderState.failure
                    val motion = KNetTheme.motion
                    val failureAnimationDuration = if (motion.animationsEnabled) {
                        motion.durationNormal
                    } else {
                        motion.durationInstant
                    }
                    AnimatedVisibility(
                        visible = failure != null,
                        enter = fadeIn(tween(failureAnimationDuration)) +
                            expandVertically(tween(failureAnimationDuration)),
                        exit = fadeOut(tween(failureAnimationDuration)) +
                            shrinkVertically(tween(failureAnimationDuration)),
                    ) {
                        failure?.let { currentFailure ->
                            ConnectFailureCard(
                                failure = currentFailure,
                                onTryAgain = { onAction(CompanionAction.ClearFailure) },
                            )
                        }
                    }
                    ConnectFeedbackCard(feedback = renderState.feedback)
                }
            }
        }
    }
}
