package com.devuloopers.knet.companion.sharedui.screen.certificate

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionCertificateExportState
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_continue
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_continue_note
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_install_illustration_description
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_installed_and_trusted
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_paired_desktop
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_pinning_note
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_ready_to_configure
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_root_name
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_secure_inspection
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_trusted
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_verified_summary
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Verified certificate completion state shown until the user explicitly continues to inspection Home. */
@Composable
internal fun CertificateVerifiedContent(
    state: CompanionUiState,
    savedExport: CompanionCertificateExportState.Saved,
    onAction: (CompanionAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xl),
    ) {
        CertificateDownloadIllustration(
            contentDescription = stringResource(Res.string.certificate_install_illustration_description),
            verified = true,
            modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth().aspectRatio(1.54f),
        )
        CertificateScreenHeading(
            title = stringResource(Res.string.certificate_trusted),
            summary = stringResource(Res.string.certificate_verified_summary),
        )
        TrustedCertificateCard(savedExport = savedExport)
        CertificateReadinessRow(
            icon = KNetIcons.Desktop,
            title = stringResource(Res.string.certificate_paired_desktop),
            value = state.activeRegistration?.desktopDisplayName?.value.orEmpty(),
        )
        CertificateReadinessRow(
            icon = KNetIcons.Shield,
            title = stringResource(Res.string.certificate_secure_inspection),
            value = stringResource(Res.string.certificate_ready_to_configure),
        )
        KNetButton(
            onClick = { onAction(CompanionAction.ContinueCertificateSetupRequested) },
            modifier = Modifier.fillMaxWidth(),
            size = ButtonSize.Touch,
        ) {
            Text(stringResource(Res.string.certificate_continue))
            Spacer(modifier = Modifier.width(KNetTheme.spacing.sm))
            Icon(
                imageVector = KNetIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
        CertificateVerifiedNote(
            icon = KNetIcons.Info,
            text = stringResource(Res.string.certificate_continue_note),
            emphasized = true,
        )
        HorizontalDivider(color = KNetTheme.colors.border)
        CertificateVerifiedNote(
            icon = KNetIcons.Shield,
            text = stringResource(Res.string.certificate_pinning_note),
            emphasized = false,
        )
    }
}

@Composable
private fun TrustedCertificateCard(savedExport: CompanionCertificateExportState.Saved) {
    KNetSurface(
        modifier = Modifier.fillMaxWidth(),
        color = KNetTheme.colors.semantic.successContainer,
        shape = KNetTheme.shapes.large,
        border = BorderStroke(1.dp, KNetTheme.colors.semantic.success.copy(alpha = 0.62f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KNetSurface(
                modifier = Modifier.size(58.dp),
                color = KNetTheme.colors.background.copy(alpha = 0.52f),
                shape = KNetTheme.shapes.extraLarge,
                border = BorderStroke(2.dp, KNetTheme.colors.semantic.success),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = KNetIcons.Check,
                        contentDescription = null,
                        tint = KNetTheme.colors.semantic.success,
                        modifier = Modifier.size(31.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs),
            ) {
                Text(
                    text = stringResource(Res.string.certificate_root_name),
                    style = KNetTheme.typography.heading,
                    color = KNetTheme.colors.textPrimary,
                )
                Text(
                    text = savedExport.fileName,
                    style = KNetTheme.typography.bodyMedium,
                    color = KNetTheme.colors.textSecondary,
                )
                Text(
                    text = stringResource(Res.string.certificate_installed_and_trusted),
                    style = KNetTheme.typography.bodyMedium,
                    color = KNetTheme.colors.semantic.success,
                )
            }
        }
    }
}

@Composable
private fun CertificateReadinessRow(
    icon: ImageVector,
    title: String,
    value: String,
) {
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = KNetTheme.colors.accent,
                modifier = Modifier.size(32.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs),
            ) {
                Text(text = title, style = KNetTheme.typography.heading, color = KNetTheme.colors.textPrimary)
                Text(text = value, style = KNetTheme.typography.bodyMedium, color = KNetTheme.colors.textSecondary)
            }
            Icon(
                imageVector = KNetIcons.ChevronRight,
                contentDescription = null,
                tint = KNetTheme.colors.textMuted,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun CertificateVerifiedNote(
    icon: ImageVector,
    text: String,
    emphasized: Boolean,
) {
    KNetSurface(
        modifier = Modifier.fillMaxWidth(),
        color = if (emphasized) {
            KNetTheme.colors.surfaceVariant.copy(alpha = 0.42f)
        } else {
            KNetTheme.colors.surface
        },
        shape = KNetTheme.shapes.large,
        border = if (emphasized) BorderStroke(1.dp, KNetTheme.colors.border) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (emphasized) KNetTheme.colors.accent else KNetTheme.colors.textMuted,
                modifier = Modifier.size(if (emphasized) 28.dp else 22.dp),
            )
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = KNetTheme.typography.bodyMedium,
                color = if (emphasized) KNetTheme.colors.textSecondary else KNetTheme.colors.textMuted,
                textAlign = TextAlign.Start,
            )
        }
    }
}
