package com.devuloopers.knet.companion.sharedui.screen.certificate

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionCertificateExportState
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_download_note
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_downloaded_note
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_downloading_note
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_verification_note
import com.devuloopers.knet.companion.sharedui.generated.resources.dismiss
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Fixed-height feedback region that changes state without moving certificate actions. */
@Composable
internal fun CertificateSetupFeedbackCard(
    state: CompanionUiState,
    onAction: (CompanionAction) -> Unit,
) {
    val feedback = state.toCertificateFeedback()
    val motion = KNetTheme.motion
    val duration = if (motion.animationsEnabled) motion.durationNormal else motion.durationInstant
    KNetSurface(
        modifier = Modifier.fillMaxWidth().height(112.dp),
        color = KNetTheme.colors.surfaceVariant.copy(alpha = 0.58f),
        shape = KNetTheme.shapes.large,
        border = BorderStroke(1.dp, KNetTheme.colors.border),
    ) {
        AnimatedContent(
            targetState = feedback,
            modifier = Modifier.fillMaxSize().padding(horizontal = KNetTheme.spacing.lg),
            transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
            contentAlignment = Alignment.CenterStart,
            label = "CertificateSetupFeedback",
        ) { current ->
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (current.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        color = KNetTheme.colors.accent,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = current.icon,
                        contentDescription = null,
                        tint = if (current.error) KNetTheme.colors.semantic.error else KNetTheme.colors.accent,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Text(
                    text = current.message,
                    style = KNetTheme.typography.bodyMedium,
                    color = KNetTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                )
                if (current.dismissible) {
                    KNetButton(
                        onClick = { onAction(CompanionAction.ClearFailure) },
                        variant = ButtonVariant.Ghost,
                        size = ButtonSize.Compact,
                    ) {
                        Text(stringResource(Res.string.dismiss))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanionUiState.toCertificateFeedback(): CertificateFeedback {
    val currentFailure = failure
    val currentCertificate = certificate
    return when {
        currentFailure != null -> CertificateFeedback(
            message = currentFailure.message,
            icon = KNetIcons.Warning,
            error = true,
            dismissible = currentFailure.recoverable,
        )
        certificateExport is CompanionCertificateExportState.Saving -> CertificateFeedback(
            message = stringResource(Res.string.certificate_downloading_note),
            icon = KNetIcons.Download,
            loading = true,
        )
        currentCertificate is CompanionCertificateState.Verifying -> CertificateFeedback(
            message = stringResource(Res.string.certificate_verification_note),
            icon = KNetIcons.Shield,
            loading = true,
        )
        currentCertificate is CompanionCertificateState.Rejected -> CertificateFeedback(
            message = currentCertificate.reason.message,
            icon = KNetIcons.Warning,
            error = true,
        )
        certificateExport is CompanionCertificateExportState.Saved -> CertificateFeedback(
            message = stringResource(Res.string.certificate_downloaded_note),
            icon = KNetIcons.Info,
        )
        else -> CertificateFeedback(
            message = stringResource(Res.string.certificate_download_note),
            icon = KNetIcons.Info,
        )
    }
}

@Immutable
private data class CertificateFeedback(
    val message: String,
    val icon: ImageVector,
    val loading: Boolean = false,
    val error: Boolean = false,
    val dismissible: Boolean = false,
)
