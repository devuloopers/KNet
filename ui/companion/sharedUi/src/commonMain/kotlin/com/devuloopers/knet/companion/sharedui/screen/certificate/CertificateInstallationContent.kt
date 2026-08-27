package com.devuloopers.knet.companion.sharedui.screen.certificate

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionCertificateExportState
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_continue
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_downloaded
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_install_illustration_description
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_install_privacy_note
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_rejected
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_rejected_note
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_root_name
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_trusted
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_trusted_note
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_verification_note
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_verifying
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_waiting
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_waiting_note
import com.devuloopers.knet.companion.sharedui.generated.resources.open_certificate_settings
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Post-download phase with platform guidance, event-driven verification, and explicit continuation. */
@Composable
internal fun CertificateInstallationContent(
    renderState: CertificateSetupRenderState,
    savedExport: CompanionCertificateExportState.Saved,
    installationGuidance: CertificateInstallationGuidance,
    onAction: (CompanionAction) -> Unit,
) {
    val motion = KNetTheme.motion
    val visibilityDuration = if (motion.animationsEnabled) motion.durationNormal else motion.durationInstant
    Column(
        modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xl),
    ) {
        CertificateInstallationHeading()
        CertificateDownloadIllustration(
            contentDescription = stringResource(Res.string.certificate_install_illustration_description),
            verified = false,
            modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1.54f),
        )
        DownloadedCertificateCard(savedExport = savedExport)
        CertificateGuidanceList(steps = installationGuidance.steps)
        AnimatedVisibility(
            visible = renderState.verification !is CertificateVerificationRenderState.Trusted,
            enter = fadeIn(tween(visibilityDuration)),
            exit = fadeOut(tween(visibilityDuration)) + shrinkVertically(tween(visibilityDuration)),
        ) {
            KNetButton(
                onClick = { onAction(CompanionAction.OpenCertificateTrustSettingsRequested) },
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Touch,
                enabled = !renderState.busy,
            ) {
                Icon(imageVector = KNetIcons.Settings, contentDescription = null, modifier = Modifier.size(21.dp))
                Spacer(modifier = Modifier.width(KNetTheme.spacing.sm))
                Text(stringResource(Res.string.open_certificate_settings))
            }
        }
        CertificateVerificationCard(verification = renderState.verification)
        KNetButton(
            onClick = { onAction(CompanionAction.ContinueCertificateSetupRequested) },
            modifier = Modifier.fillMaxWidth(),
            size = ButtonSize.Touch,
            enabled = renderState.canContinue,
            loading = renderState.verificationInProgress,
        ) {
            Text(stringResource(Res.string.certificate_continue))
        }
        CertificatePrivacyNote()
    }
}

@Composable
private fun DownloadedCertificateCard(savedExport: CompanionCertificateExportState.Saved) {
    KNetSurface(
        modifier = Modifier.fillMaxWidth(),
        color = KNetTheme.colors.surfaceVariant.copy(alpha = 0.58f),
        shape = KNetTheme.shapes.large,
        border = BorderStroke(1.dp, KNetTheme.colors.border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KNetSurface(
                modifier = Modifier.size(50.dp),
                color = KNetTheme.colors.interaction.selectedOverlay,
                shape = KNetTheme.shapes.medium,
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = KNetIcons.Shield,
                        contentDescription = null,
                        tint = KNetTheme.colors.accent,
                        modifier = Modifier.size(27.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(Res.string.certificate_root_name),
                    style = KNetTheme.typography.bodyMedium,
                    color = KNetTheme.colors.textPrimary,
                )
                Text(
                    text = savedExport.fileName,
                    style = KNetTheme.typography.caption,
                    color = KNetTheme.colors.textSecondary,
                )
                Text(
                    text = savedExport.locationDescription,
                    style = KNetTheme.typography.caption,
                    color = KNetTheme.colors.textMuted,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = KNetIcons.Check,
                        contentDescription = null,
                        tint = KNetTheme.colors.semantic.success,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(Res.string.certificate_downloaded),
                        style = KNetTheme.typography.caption,
                        color = KNetTheme.colors.semantic.success,
                    )
                }
            }
        }
    }
}

@Composable
private fun CertificateGuidanceList(steps: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md),
    ) {
        steps.forEachIndexed { index, step ->
            CertificateGuidanceStep(number = index + 1, text = step)
        }
    }
}

@Composable
private fun CertificateGuidanceStep(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KNetSurface(
            modifier = Modifier.size(34.dp),
            color = KNetTheme.colors.interaction.selectedOverlay,
            shape = CircleShape,
            border = BorderStroke(1.dp, KNetTheme.colors.borderFocused),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = number.toString(),
                    style = KNetTheme.typography.labelMedium,
                    color = KNetTheme.colors.accent,
                )
            }
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = KNetTheme.typography.bodyMedium,
            color = KNetTheme.colors.textSecondary,
        )
    }
}

/** Fixed-height verification surface that prevents trust updates from shifting surrounding actions. */
@Composable
private fun CertificateVerificationCard(verification: CertificateVerificationRenderState) {
    val motion = KNetTheme.motion
    val duration = if (motion.animationsEnabled) motion.durationNormal else motion.durationInstant
    KNetSurface(
        modifier = Modifier.fillMaxWidth().height(126.dp),
        color = KNetTheme.colors.surfaceVariant.copy(alpha = 0.58f),
        shape = KNetTheme.shapes.large,
        border = BorderStroke(1.dp, KNetTheme.colors.border),
    ) {
        AnimatedContent(
            targetState = verification,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
            contentAlignment = Alignment.CenterStart,
            label = "CertificateVerificationState",
        ) { current ->
            val content = current.toVerificationContent()
            Row(
                modifier = Modifier.fillMaxSize().padding(KNetTheme.spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (content.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = KNetTheme.colors.accent,
                        strokeWidth = 3.dp,
                    )
                } else {
                    Icon(
                        imageVector = content.icon,
                        contentDescription = null,
                        tint = content.tint,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs),
                ) {
                    Text(
                        text = content.title,
                        style = KNetTheme.typography.heading,
                        color = content.tint,
                    )
                    Text(
                        text = content.summary,
                        style = KNetTheme.typography.bodyMedium,
                        color = KNetTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CertificateVerificationRenderState.toVerificationContent(): VerificationContent = when (this) {
    CertificateVerificationRenderState.Waiting -> VerificationContent(
        title = stringResource(Res.string.certificate_waiting),
        summary = stringResource(Res.string.certificate_waiting_note),
        icon = KNetIcons.Shield,
        tint = KNetTheme.colors.textPrimary,
        loading = true,
    )
    CertificateVerificationRenderState.Verifying -> VerificationContent(
        title = stringResource(Res.string.certificate_verifying),
        summary = stringResource(Res.string.certificate_verification_note),
        icon = KNetIcons.Shield,
        tint = KNetTheme.colors.accent,
        loading = true,
    )
    CertificateVerificationRenderState.Trusted -> VerificationContent(
        title = stringResource(Res.string.certificate_trusted),
        summary = stringResource(Res.string.certificate_trusted_note),
        icon = KNetIcons.Check,
        tint = KNetTheme.colors.semantic.success,
    )
    is CertificateVerificationRenderState.Rejected -> VerificationContent(
        title = stringResource(Res.string.certificate_rejected),
        summary = message.ifBlank { stringResource(Res.string.certificate_rejected_note) },
        icon = KNetIcons.Warning,
        tint = KNetTheme.colors.semantic.warning,
    )
    is CertificateVerificationRenderState.Failed -> VerificationContent(
        title = stringResource(Res.string.certificate_rejected),
        summary = failure.message,
        icon = KNetIcons.Warning,
        tint = KNetTheme.colors.semantic.error,
    )
}

@Composable
private fun CertificatePrivacyNote() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = KNetIcons.Lock,
            contentDescription = null,
            tint = KNetTheme.colors.textMuted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(Res.string.certificate_install_privacy_note),
            modifier = Modifier.weight(1f),
            style = KNetTheme.typography.caption,
            color = KNetTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Immutable
private data class VerificationContent(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val tint: Color,
    val loading: Boolean = false,
)
