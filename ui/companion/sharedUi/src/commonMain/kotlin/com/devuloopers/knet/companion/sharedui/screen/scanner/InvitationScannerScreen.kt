package com.devuloopers.knet.companion.sharedui.screen.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.back
import com.devuloopers.knet.companion.sharedui.generated.resources.camera_failed
import com.devuloopers.knet.companion.sharedui.generated.resources.camera_permission_denied
import com.devuloopers.knet.companion.sharedui.generated.resources.camera_permission_permanently_denied
import com.devuloopers.knet.companion.sharedui.generated.resources.camera_permission_required
import com.devuloopers.knet.companion.sharedui.generated.resources.camera_unavailable
import com.devuloopers.knet.companion.sharedui.generated.resources.import_qr
import com.devuloopers.knet.companion.sharedui.generated.resources.open_app_settings
import com.devuloopers.knet.companion.sharedui.generated.resources.retry
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_invitation_active
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_invitation_resolving
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_invitation_summary
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_invitation_title
import com.devuloopers.knet.companion.sharedui.generated.resources.use_camera
import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScanner
import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScannerState
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Full-height shared invitation-scanner stage around a product-owned native camera preview. */
@Composable
internal fun InvitationScannerScreen(
    state: CompanionUiState,
    scanner: CompanionInvitationScanner,
    onAction: (CompanionAction) -> Unit,
) {
    val scannerState by scanner.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KNetTheme.colors.background)
            .safeDrawingPadding()
            .padding(KNetTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KNetIconButton(
                onClick = { onAction(CompanionAction.InvitationScannerDismissed) },
                icon = KNetIcons.Back,
                contentDescription = stringResource(Res.string.back),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.scan_invitation_title),
                    style = KNetTheme.typography.titleLarge,
                    color = KNetTheme.colors.textPrimary,
                )
                Text(
                    text = stringResource(Res.string.scan_invitation_summary),
                    style = KNetTheme.typography.bodyMedium,
                    color = KNetTheme.colors.textSecondary,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(KNetTheme.shapes.medium)
                .background(KNetTheme.colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            ScannerBody(
                state = state,
                scannerState = scannerState,
                scanner = scanner,
                onAction = onAction,
            )
            if (scannerState == CompanionInvitationScannerState.ACTIVE &&
                !state.operationInProgress && state.failure == null
            ) {
                Box(
                    modifier = Modifier
                        .size(248.dp)
                        .border(3.dp, KNetTheme.colors.accent, RoundedCornerShape(24.dp)),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KNetButton(
                onClick = { onAction(CompanionAction.ImportInvitationImageRequested) },
                variant = ButtonVariant.Secondary,
                enabled = !state.operationInProgress,
            ) {
                Text(stringResource(Res.string.import_qr))
            }
            KNetButton(
                onClick = { onAction(CompanionAction.InvitationScannerDismissed) },
                variant = ButtonVariant.Ghost,
                enabled = !state.operationInProgress,
            ) {
                Text(stringResource(Res.string.back))
            }
        }
    }
}

@Composable
private fun BoxScope.ScannerBody(
    state: CompanionUiState,
    scannerState: CompanionInvitationScannerState,
    scanner: CompanionInvitationScanner,
    onAction: (CompanionAction) -> Unit,
) {
    val failure = state.failure
    when {
        state.operationInProgress -> ScannerMessage(
            message = stringResource(Res.string.scan_invitation_resolving),
            showProgress = true,
        )
        failure != null -> ScannerMessage(
            message = failure.message,
            actionLabel = stringResource(Res.string.retry),
            onAction = { onAction(CompanionAction.ClearFailure) },
        )
        scannerState == CompanionInvitationScannerState.STARTING ||
            scannerState == CompanionInvitationScannerState.ACTIVE -> {
            scanner.Preview(
                onPayloadDetected = { payload ->
                    onAction(CompanionAction.InvitationScanned(payload))
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (scannerState == CompanionInvitationScannerState.ACTIVE) {
                Text(
                    text = stringResource(Res.string.scan_invitation_active),
                    style = KNetTheme.typography.bodyMedium,
                    color = KNetTheme.colors.textPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(KNetTheme.spacing.lg)
                        .clip(KNetTheme.shapes.small)
                        .background(KNetTheme.colors.surfaceVariant.copy(alpha = 0.9f))
                        .padding(horizontal = KNetTheme.spacing.md, vertical = KNetTheme.spacing.sm),
                )
            }
        }
        scannerState == CompanionInvitationScannerState.PERMISSION_REQUIRED -> ScannerMessage(
            message = stringResource(Res.string.camera_permission_required),
            actionLabel = stringResource(Res.string.use_camera),
            onAction = scanner::requestCameraPermission,
        )
        scannerState == CompanionInvitationScannerState.PERMISSION_DENIED -> ScannerMessage(
            message = stringResource(Res.string.camera_permission_denied),
            actionLabel = stringResource(Res.string.use_camera),
            onAction = scanner::requestCameraPermission,
        )
        scannerState == CompanionInvitationScannerState.PERMISSION_PERMANENTLY_DENIED -> ScannerMessage(
            message = stringResource(Res.string.camera_permission_permanently_denied),
            actionLabel = stringResource(Res.string.open_app_settings),
            onAction = scanner::openApplicationSettings,
        )
        scannerState == CompanionInvitationScannerState.FAILED -> ScannerMessage(
            message = stringResource(Res.string.camera_failed),
            actionLabel = stringResource(Res.string.retry),
            onAction = scanner::requestCameraPermission,
        )
        else -> ScannerMessage(message = stringResource(Res.string.camera_unavailable))
    }
}

@Composable
private fun ScannerMessage(
    message: String,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.padding(KNetTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md),
    ) {
        if (showProgress) {
            CircularProgressIndicator(color = KNetTheme.colors.accent)
        } else {
            Icon(
                imageVector = KNetIcons.Info,
                contentDescription = null,
                tint = KNetTheme.colors.textSecondary,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = message,
            style = KNetTheme.typography.bodyMedium,
            color = KNetTheme.colors.textPrimary,
        )
        if (actionLabel != null && onAction != null) {
            KNetButton(onClick = onAction, variant = ButtonVariant.Secondary) {
                Text(actionLabel)
            }
        }
    }
}
