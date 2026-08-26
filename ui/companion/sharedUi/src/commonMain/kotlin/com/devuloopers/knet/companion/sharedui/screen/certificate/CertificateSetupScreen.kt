package com.devuloopers.knet.companion.sharedui.screen.certificate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionCertificateExportState
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.component.CompanionOnboardingScaffold
import com.devuloopers.knet.companion.sharedui.component.CompanionStatusRow
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_download_again
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_download_note
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_downloaded_file
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_downloaded_location
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_downloaded_note
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_install_required
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_rejected
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_summary
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_title
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_trusted
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_verifying
import com.devuloopers.knet.companion.sharedui.generated.resources.download_certificate
import com.devuloopers.knet.companion.sharedui.generated.resources.open_trust_settings
import com.devuloopers.knet.companion.sharedui.generated.resources.status_desktop
import com.devuloopers.knet.companion.sharedui.generated.resources.status_certificate
import com.devuloopers.knet.companion.sharedui.generated.resources.verify_certificate
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.card.KNetCard
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Certificate installation and authoritative TLS verification screen. */
@Composable
internal fun CertificateSetupScreen(
    state: CompanionUiState,
    installationGuidance: CertificateInstallationGuidance,
    onAction: (CompanionAction) -> Unit,
) {
    val status = when (state.certificate) {
        CompanionCertificateState.Unknown,
        CompanionCertificateState.InstallationRequired,
        -> stringResource(Res.string.certificate_install_required)
        CompanionCertificateState.Verifying -> stringResource(Res.string.certificate_verifying)
        is CompanionCertificateState.Trusted -> stringResource(Res.string.certificate_trusted)
        is CompanionCertificateState.Rejected -> stringResource(Res.string.certificate_rejected)
    }
    CompanionOnboardingScaffold(
        title = stringResource(Res.string.certificate_title),
        summary = stringResource(Res.string.certificate_summary),
        currentStep = 1,
        state = state,
        onAction = onAction,
    ) {
        val savedExport = state.certificateExport as? CompanionCertificateExportState.Saved
        val exportInProgress = state.certificateExport is CompanionCertificateExportState.Saving
        KNetCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg)) {
                CompanionStatusRow(
                    label = stringResource(Res.string.status_desktop),
                    value = state.activeRegistration?.desktopDisplayName.orEmpty(),
                    icon = KNetIcons.Info,
                    positive = true,
                )
                CompanionStatusRow(
                    label = stringResource(Res.string.status_certificate),
                    value = status,
                    icon = if (state.certificate is CompanionCertificateState.Trusted) {
                        KNetIcons.Check
                    } else {
                        KNetIcons.Warning
                    },
                    positive = state.certificate is CompanionCertificateState.Trusted,
                )
                if (savedExport == null) {
                    Text(
                        text = stringResource(Res.string.certificate_download_note),
                        style = KNetTheme.typography.bodySmall,
                        color = KNetTheme.colors.textSecondary,
                    )
                    KNetButton(
                        onClick = { onAction(CompanionAction.DownloadCertificateRequested) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.operationInProgress && !exportInProgress,
                        loading = exportInProgress,
                    ) { Text(stringResource(Res.string.download_certificate)) }
                    KNetButton(
                        onClick = { onAction(CompanionAction.VerifyCertificateTrustRequested) },
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.Secondary,
                        enabled = !state.operationInProgress && !exportInProgress,
                    ) { Text(stringResource(Res.string.verify_certificate)) }
                } else {
                    CompanionStatusRow(
                        label = stringResource(Res.string.certificate_downloaded_file),
                        value = savedExport.fileName,
                        icon = KNetIcons.Check,
                        positive = true,
                    )
                    CompanionStatusRow(
                        label = stringResource(Res.string.certificate_downloaded_location),
                        value = savedExport.locationDescription,
                        icon = KNetIcons.Info,
                        positive = true,
                    )
                    Text(
                        text = installationGuidance.title,
                        style = KNetTheme.typography.titleMedium,
                        color = KNetTheme.colors.textPrimary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm)) {
                        installationGuidance.steps.forEachIndexed { index, step ->
                            CertificateGuidanceStep(number = index + 1, text = step)
                        }
                    }
                    Text(
                        text = stringResource(Res.string.certificate_downloaded_note),
                        style = KNetTheme.typography.bodySmall,
                        color = KNetTheme.colors.textSecondary,
                    )
                    KNetButton(
                        onClick = { onAction(CompanionAction.OpenCertificateTrustSettingsRequested) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.operationInProgress,
                    ) { Text(stringResource(Res.string.open_trust_settings)) }
                    KNetButton(
                        onClick = { onAction(CompanionAction.VerifyCertificateTrustRequested) },
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.Secondary,
                        enabled = !state.operationInProgress,
                        loading = state.operationInProgress,
                    ) { Text(stringResource(Res.string.verify_certificate)) }
                    KNetButton(
                        onClick = { onAction(CompanionAction.DownloadCertificateRequested) },
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.Ghost,
                        enabled = !state.operationInProgress && !exportInProgress,
                    ) { Text(stringResource(Res.string.certificate_download_again)) }
                }
            }
        }
    }
}

@Composable
private fun CertificateGuidanceStep(number: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = number.toString(),
            modifier = Modifier.width(28.dp).alignByBaseline(),
            style = KNetTheme.typography.bodySmall,
            color = KNetTheme.colors.accent,
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f).alignByBaseline(),
            style = KNetTheme.typography.bodySmall,
            color = KNetTheme.colors.textSecondary,
        )
    }
}
