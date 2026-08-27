package com.devuloopers.knet.companion.sharedui.screen.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.camera_failed
import com.devuloopers.knet.companion.sharedui.generated.resources.camera_permission_denied
import com.devuloopers.knet.companion.sharedui.generated.resources.camera_permission_permanently_denied
import com.devuloopers.knet.companion.sharedui.generated.resources.camera_permission_required
import com.devuloopers.knet.companion.sharedui.generated.resources.camera_unavailable
import com.devuloopers.knet.companion.sharedui.generated.resources.open_app_settings
import com.devuloopers.knet.companion.sharedui.generated.resources.retry
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_invitation_active
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_invitation_retry
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_invitation_resolving
import com.devuloopers.knet.companion.sharedui.generated.resources.use_camera
import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScanner
import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScannerState
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.placeholder.KNetShimmerBox
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Inline scanner content that keeps all native camera states inside the Connect card's visual panel. */
@Composable
internal fun InlineInvitationScanner(
    state: CompanionUiState,
    scanner: CompanionInvitationScanner,
    onAction: (CompanionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scannerState by scanner.state.collectAsState()
    Box(
        modifier = modifier
            .clip(KNetTheme.shapes.extraLarge)
            .background(KNetTheme.colors.background.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        InlineScannerBody(
            state = state,
            scannerState = scannerState,
            scanner = scanner,
            onAction = onAction,
        )
        if (scannerState == CompanionInvitationScannerState.STARTING &&
            !state.operationInProgress && state.failure == null
        ) {
            KNetShimmerBox(modifier = Modifier.fillMaxSize())
        }
        if (scannerState == CompanionInvitationScannerState.ACTIVE && !state.operationInProgress) {
            ScannerTarget(hasFailure = state.failure != null)
        }
        if (state.operationInProgress) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(KNetTheme.colors.background.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                InlineScannerMessage(
                    message = stringResource(Res.string.scan_invitation_resolving),
                    showProgress = true,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.InlineScannerBody(
    state: CompanionUiState,
    scannerState: CompanionInvitationScannerState,
    scanner: CompanionInvitationScanner,
    onAction: (CompanionAction) -> Unit,
) {
    when {
        state.failure != null && (
            scannerState == CompanionInvitationScannerState.STARTING ||
                scannerState == CompanionInvitationScannerState.ACTIVE
            ) -> Unit
        scannerState == CompanionInvitationScannerState.STARTING ||
            scannerState == CompanionInvitationScannerState.ACTIVE && state.failure == null -> {
            scanner.Preview(
                onPayloadDetected = { payload ->
                    onAction(CompanionAction.InvitationScanned(payload))
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        scannerState == CompanionInvitationScannerState.PERMISSION_REQUIRED -> InlineScannerMessage(
            message = stringResource(Res.string.camera_permission_required),
            actionLabel = stringResource(Res.string.use_camera),
            onAction = scanner::requestCameraPermission,
        )
        scannerState == CompanionInvitationScannerState.PERMISSION_DENIED -> InlineScannerMessage(
            message = stringResource(Res.string.camera_permission_denied),
            actionLabel = stringResource(Res.string.use_camera),
            onAction = scanner::requestCameraPermission,
        )
        scannerState == CompanionInvitationScannerState.PERMISSION_PERMANENTLY_DENIED -> InlineScannerMessage(
            message = stringResource(Res.string.camera_permission_permanently_denied),
            actionLabel = stringResource(Res.string.open_app_settings),
            onAction = scanner::openApplicationSettings,
        )
        scannerState == CompanionInvitationScannerState.FAILED -> InlineScannerMessage(
            message = stringResource(Res.string.camera_failed),
            actionLabel = stringResource(Res.string.retry),
            onAction = scanner::requestCameraPermission,
        )
        else -> InlineScannerMessage(message = stringResource(Res.string.camera_unavailable))
    }
}

@Composable
private fun BoxScope.ScannerTarget(hasFailure: Boolean) {
    val targetColor = if (hasFailure) KNetTheme.colors.semantic.error else KNetTheme.colors.accent
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .padding(horizontal = KNetTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
    ) {
        Box(
            modifier = Modifier
                .size(136.dp)
                .border(3.dp, targetColor, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (hasFailure) {
                Icon(
                    imageVector = KNetIcons.Close,
                    contentDescription = null,
                    tint = KNetTheme.colors.semantic.error,
                    modifier = Modifier.size(42.dp),
                )
            }
        }
        Text(
            text = stringResource(
                if (hasFailure) Res.string.scan_invitation_retry else Res.string.scan_invitation_active,
            ),
            style = KNetTheme.typography.bodySmall,
            color = KNetTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(KNetTheme.shapes.small)
                .background(KNetTheme.colors.surfaceVariant.copy(alpha = 0.9f))
                .padding(horizontal = KNetTheme.spacing.md, vertical = KNetTheme.spacing.sm),
        )
    }
}

@Composable
private fun InlineScannerMessage(
    message: String,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(KNetTheme.spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md),
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = KNetTheme.colors.accent,
                    strokeWidth = 2.dp,
                )
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
                style = KNetTheme.typography.bodySmall,
                color = KNetTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                KNetButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
