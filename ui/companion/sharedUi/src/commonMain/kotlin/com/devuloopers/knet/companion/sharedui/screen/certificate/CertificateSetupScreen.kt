package com.devuloopers.knet.companion.sharedui.screen.certificate

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_illustration_description
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_install_summary
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_install_title
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_summary
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_title
import com.devuloopers.knet.companion.sharedui.generated.resources.download_certificate
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Reactive certificate download and installation screen shown after automatic QR pairing. */
@Composable
internal fun CertificateSetupScreen(
    state: CompanionUiState,
    installationGuidance: CertificateInstallationGuidance,
    onAction: (CompanionAction) -> Unit,
) {
    val renderState = state.toCertificateSetupRenderState()
    val motion = KNetTheme.motion
    val duration = if (motion.animationsEnabled) motion.durationNormal else motion.durationInstant
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
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .animateContentSize(animationSpec = tween(duration)),
                color = KNetTheme.colors.surface,
                shape = KNetTheme.shapes.extraLarge,
                border = BorderStroke(1.dp, KNetTheme.colors.border),
            ) {
                AnimatedContent(
                    targetState = renderState.phase,
                    transitionSpec = {
                        fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                    },
                    contentAlignment = Alignment.TopCenter,
                    label = "CertificateSetupPhase",
                ) { phase ->
                    when (phase) {
                        CertificateSetupPhase.Download -> CertificateDownloadContent(
                            state = state,
                            renderState = renderState,
                            onAction = onAction,
                        )
                        is CertificateSetupPhase.Installation -> CertificateInstallationContent(
                            renderState = renderState,
                            savedExport = phase.savedExport,
                            installationGuidance = installationGuidance,
                            onAction = onAction,
                        )
                        is CertificateSetupPhase.Verified -> CertificateVerifiedContent(
                            state = state,
                            savedExport = phase.savedExport,
                            onAction = onAction,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CertificateDownloadContent(
    state: CompanionUiState,
    renderState: CertificateSetupRenderState,
    onAction: (CompanionAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xxl),
    ) {
        CertificateScreenHeading(
            title = stringResource(Res.string.certificate_title),
            summary = stringResource(Res.string.certificate_summary),
        )
        CertificateDownloadHeroIllustration(
            contentDescription = stringResource(Res.string.certificate_illustration_description),
            modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth().aspectRatio(1.54f),
        )
        CertificateBenefitList()
        KNetButton(
            onClick = { onAction(CompanionAction.DownloadCertificateRequested) },
            modifier = Modifier.fillMaxWidth(),
            size = ButtonSize.Touch,
            enabled = !renderState.verificationInProgress,
            loading = renderState.downloadInProgress,
        ) {
            Icon(
                imageVector = KNetIcons.Download,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(KNetTheme.spacing.sm))
            Text(stringResource(Res.string.download_certificate))
        }
        CertificateSetupFeedbackCard(state = state, onAction = onAction)
    }
}

@Composable
internal fun CertificateScreenHeading(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md),
    ) {
        Text(
            text = title,
            style = KNetTheme.typography.hero,
            color = KNetTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = summary,
            style = KNetTheme.typography.bodyLarge,
            color = KNetTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun CertificateInstallationHeading() {
    CertificateScreenHeading(
        title = stringResource(Res.string.certificate_install_title),
        summary = stringResource(Res.string.certificate_install_summary),
    )
}
