package com.devuloopers.knet.companion.sharedui.screen.connect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.presentation.state.CompanionConnectFailureKind
import com.devuloopers.knet.companion.presentation.state.CompanionConnectFailureUiState
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.retry
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_another_qr
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_failure_expired_message
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_failure_expired_title
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_failure_generic_title
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_failure_invalid_message
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_failure_invalid_title
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_failure_pairing_title
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_failure_security_message
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_failure_security_title
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_failure_unreachable_message
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_failure_unreachable_title
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Inline, wrapping recovery card for typed QR and pairing failures. */
@Composable
internal fun ConnectFailureCard(
    failure: CompanionConnectFailureUiState,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = failureTitle(failure.kind)
    val message = failureMessage(failure)
    val scansAnotherCode = failure.kind == CompanionConnectFailureKind.EXPIRED_QR ||
        failure.kind == CompanionConnectFailureKind.SECURITY
    KNetSurface(
        modifier = modifier.fillMaxWidth(),
        color = KNetTheme.colors.semantic.errorContainer.copy(alpha = 0.5f),
        shape = KNetTheme.shapes.large,
        border = BorderStroke(1.dp, KNetTheme.colors.semantic.error),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = KNetIcons.Close,
                    contentDescription = null,
                    tint = KNetTheme.colors.semantic.error,
                    modifier = Modifier.size(30.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
                ) {
                    Text(
                        text = title,
                        style = KNetTheme.typography.titleLarge,
                        color = KNetTheme.colors.semantic.error,
                    )
                    Text(
                        text = message,
                        style = KNetTheme.typography.bodyMedium,
                        color = KNetTheme.colors.textSecondary,
                    )
                }
            }
            KNetButton(
                onClick = onTryAgain,
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Touch,
            ) {
                Icon(
                    imageVector = if (scansAnotherCode) KNetIcons.QrCodeScanner else KNetIcons.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(KNetTheme.spacing.sm))
                Text(stringResource(if (scansAnotherCode) Res.string.scan_another_qr else Res.string.retry))
            }
        }
    }
}

@Composable
private fun failureTitle(kind: CompanionConnectFailureKind): String = stringResource(
    when (kind) {
        CompanionConnectFailureKind.INVALID_QR -> Res.string.scan_failure_invalid_title
        CompanionConnectFailureKind.EXPIRED_QR -> Res.string.scan_failure_expired_title
        CompanionConnectFailureKind.DESKTOP_UNREACHABLE -> Res.string.scan_failure_unreachable_title
        CompanionConnectFailureKind.SECURITY -> Res.string.scan_failure_security_title
        CompanionConnectFailureKind.PAIRING -> Res.string.scan_failure_pairing_title
        CompanionConnectFailureKind.GENERAL -> Res.string.scan_failure_generic_title
    },
)

@Composable
private fun failureMessage(failure: CompanionConnectFailureUiState): String = when (failure.kind) {
    CompanionConnectFailureKind.INVALID_QR -> stringResource(Res.string.scan_failure_invalid_message)
    CompanionConnectFailureKind.EXPIRED_QR -> stringResource(Res.string.scan_failure_expired_message)
    CompanionConnectFailureKind.DESKTOP_UNREACHABLE -> stringResource(Res.string.scan_failure_unreachable_message)
    CompanionConnectFailureKind.SECURITY -> stringResource(Res.string.scan_failure_security_message)
    CompanionConnectFailureKind.PAIRING,
    CompanionConnectFailureKind.GENERAL,
    -> failure.failure.message
}
