package com.devuloopers.knet.companion.sharedui.screen.certificate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.component.*
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Trust is checked before export; circular actions never substitute for platform installation or verification. */
@Composable
internal fun CertificateSetupScreen(state: CompanionUiState,
    installationGuidance: CertificateInstallationGuidance, onAction: (CompanionAction) -> Unit) {
    val render = state.toCertificateSetupRenderState()
    val phase = render.phase
    if (phase is CertificateSetupPhase.Installation || phase is CertificateSetupPhase.Verified) {
        CompanionPage(actions = {
            if (phase is CertificateSetupPhase.Installation) {
                CompanionActionButton(stringResource(if (installationGuidance.platform == CertificateInstallationPlatform.IOS)
                    Res.string.open_ios_settings else Res.string.open_certificate_settings),
                    { onAction(CompanionAction.OpenCertificateTrustSettingsRequested) }, enabled = !state.operationInProgress)
                CompanionFootnote(stringResource(Res.string.certificate_waiting_note))
            } else {
                CompanionActionButton(stringResource(Res.string.certificate_continue),
                    { onAction(CompanionAction.ContinueCertificateSetupRequested) }, enabled = render.canContinue,
                    loading = state.operationInProgress)
                CompanionFootnote(stringResource(Res.string.certificate_continue_note))
            }
        }) {
            if (phase is CertificateSetupPhase.Installation) {
                val ios = installationGuidance.platform == CertificateInstallationPlatform.IOS
                CompanionHeading(stringResource(if (ios) Res.string.certificate_ios_install_title else Res.string.certificate_install_title),
                    stringResource(Res.string.certificate_install_summary), centered = true)
                CompanionFootnote(if (ios) "iOS" else "Android")
                CompanionPanel {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(KNetIcons.Download, null, Modifier.size(30.dp), tint = KNetTheme.colors.textSecondary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(phase.savedExport.fileName, style = KNetTheme.typography.titleLarge, color = KNetTheme.colors.textPrimary)
                            Text(phase.savedExport.locationDescription, style = KNetTheme.typography.bodySmall, color = KNetTheme.colors.textSecondary)
                        }
                    }
                    HorizontalDivider(color = KNetTheme.colors.border)
                    CertificateGuidanceList(installationGuidance.steps)
                    HorizontalDivider(color = KNetTheme.colors.border)
                    CompanionInfo(verificationMessage(render.verification), KNetIcons.Schedule,
                        busy = render.verification == CertificateVerificationRenderState.Verifying)
                }
            } else {
                CompanionHeading(stringResource(Res.string.certificate_trusted), stringResource(Res.string.certificate_verified_summary), centered = true)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CompanionRoundControl(CompanionRoundControlState(CompanionControlSymbol.CHECK,
                        stringResource(Res.string.certificate_control_trusted), tone = CompanionControlTone.SUCCESS))
                }
                CertificateMetadata(state, verified = true)
            }
            CertificateFailure(state)
        }
    } else CompanionHeroPage {
        when (phase) {
            CertificateSetupPhase.Checking -> {
                CompanionHeading(stringResource(Res.string.certificate_check_title), stringResource(Res.string.certificate_check_summary), centered = true)
                CompanionRoundControl(CompanionRoundControlState(CompanionControlSymbol.SHIELD,
                    stringResource(Res.string.certificate_checking_action), busy = true))
                CompanionPanel {
                    CompanionInfo(stringResource(Res.string.certificate_verifying_desktop), KNetIcons.Shield,
                        tint = KNetTheme.colors.accent, busy = true)
                }
                CompanionFootnote(stringResource(Res.string.certificate_check_note))
            }
            CertificateSetupPhase.VerificationUnavailable -> {
                CompanionHeading(stringResource(Res.string.certificate_check_unavailable_title),
                    stringResource(Res.string.certificate_check_unavailable_summary), centered = true)
                CompanionRoundControl(CompanionRoundControlState(CompanionControlSymbol.RETRY,
                    stringResource(Res.string.retry), tone = CompanionControlTone.ACCENT, enabled = !render.busy)) {
                    onAction(CompanionAction.VerifyCertificateTrustRequested)
                }
                CompanionPanel { CompanionInfo(verificationMessage(render.verification), KNetIcons.Info) }
                CompanionFootnote(stringResource(Res.string.certificate_unavailable_note))
            }
            CertificateSetupPhase.Download -> {
                CompanionHeading(stringResource(Res.string.certificate_title), stringResource(Res.string.certificate_summary), centered = true)
                CompanionRoundControl(CompanionRoundControlState(CompanionControlSymbol.DOWNLOAD,
                    stringResource(if (render.downloadInProgress) Res.string.control_downloading else Res.string.control_download),
                    stringResource(Res.string.download_certificate), busy = render.downloadInProgress,
                    enabled = !render.verificationInProgress), onClick = if (render.downloadInProgress) null else {
                    { onAction(CompanionAction.DownloadCertificateRequested) }
                })
                CertificateMetadata(state)
                CompanionFootnote(stringResource(Res.string.certificate_download_note))
            }
        }
        if (phase != CertificateSetupPhase.VerificationUnavailable) CertificateFailure(state)
    }
}

@Composable
private fun CertificateMetadata(state: CompanionUiState, verified: Boolean = false) {
    CompanionPanel {
        CompanionStatusRow(stringResource(Res.string.certificate_label),
            stringResource(if (verified) Res.string.certificate_installed_and_trusted else Res.string.certificate_root_name),
            if (verified) KNetTheme.colors.semantic.success else KNetTheme.colors.textSecondary)
        HorizontalDivider(color = KNetTheme.colors.border)
        CompanionStatusRow(stringResource(Res.string.certificate_paired_desktop), state.activeRegistration?.desktopDisplayName?.value.orEmpty())
    }
}

@Composable
private fun CertificateFailure(state: CompanionUiState) {
    state.failure?.let { CompanionPanel { CompanionInfo(it.message, KNetIcons.Warning, KNetTheme.colors.semantic.warning) } }
}

/** Each platform owns its instructions; this checklist remains scrollable independently of Settings. */
@Composable
private fun CertificateGuidanceList(steps: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        steps.forEachIndexed { index, text ->
            if (index > 0) HorizontalDivider(color = KNetTheme.colors.border)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(KNetTheme.colors.border), contentAlignment = Alignment.Center) {
                    Text((index + 1).toString(), style = KNetTheme.typography.titleMedium, color = KNetTheme.colors.textPrimary)
                }
                Text(text, Modifier.weight(1f), style = KNetTheme.typography.bodyMedium, color = KNetTheme.colors.textPrimary)
            }
        }
    }
}

@Composable
private fun verificationMessage(state: CertificateVerificationRenderState): String = when (state) {
    CertificateVerificationRenderState.Waiting -> stringResource(Res.string.certificate_waiting)
    CertificateVerificationRenderState.Verifying -> stringResource(Res.string.certificate_verifying)
    CertificateVerificationRenderState.Trusted -> stringResource(Res.string.certificate_trusted)
    is CertificateVerificationRenderState.Failed -> state.failure.message
    is CertificateVerificationRenderState.Rejected -> state.message
}
