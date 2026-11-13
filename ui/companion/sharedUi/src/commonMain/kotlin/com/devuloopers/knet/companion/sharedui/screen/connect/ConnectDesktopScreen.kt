package com.devuloopers.knet.companion.sharedui.screen.connect

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.*
import com.devuloopers.knet.companion.sharedui.component.*
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import com.devuloopers.knet.companion.sharedui.scanner.*
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Circular QR entry, full-page pairing and native camera recovery use the same portable mobile shell. */
@Composable
internal fun ConnectDesktopScreen(state: CompanionUiState, scanner: CompanionInvitationScanner,
    onAction: (CompanionAction) -> Unit) {
    val render = state.toConnectUiState()
    val camera by scanner.state.collectAsState()
    var permissionFlow by rememberSaveable(stateSaver = cameraEntrySaver) { mutableStateOf(CameraEntryPhase.IDLE) }
    val requesting = permissionFlow == CameraEntryPhase.REQUESTING || camera == CompanionInvitationScannerState.PERMISSION_REQUESTING
    val permissionMissing = camera in setOf(CompanionInvitationScannerState.PERMISSION_REQUIRED,
        CompanionInvitationScannerState.PERMISSION_REQUESTING, CompanionInvitationScannerState.PERMISSION_DENIED,
        CompanionInvitationScannerState.PERMISSION_PERMANENTLY_DENIED)
    val scanning = state.invitationScannerVisible && !permissionMissing
    val failed = render.failure
    val busy = state.operationInProgress
    val canScan = render.scanState == CompanionConnectScanState.Enabled && !requesting
    val currentOnAction by rememberUpdatedState(onAction)
    // Permission is native UI, not an invitation/scanner session. Gallery keeps its own invitation intent and
    // can never accidentally trigger a camera request or be dismissed because camera permission was denied.
    LaunchedEffect(camera, permissionFlow, state.desktopManagement, busy) {
        if (state.desktopManagement != CompanionDesktopManagementState.Closed &&
            state.desktopManagement != CompanionDesktopManagementState.Adding) {
            permissionFlow = CameraEntryPhase.IDLE
        } else if (!busy && permissionFlow != CameraEntryPhase.IDLE) {
            when (camera) {
                CompanionInvitationScannerState.STARTING, CompanionInvitationScannerState.ACTIVE -> {
                    permissionFlow = CameraEntryPhase.IDLE
                    currentOnAction(CompanionAction.ScanInvitationRequested)
                }
                CompanionInvitationScannerState.PERMISSION_DENIED,
                CompanionInvitationScannerState.PERMISSION_PERMANENTLY_DENIED -> {
                    if (permissionFlow == CameraEntryPhase.REQUESTING) permissionFlow = CameraEntryPhase.IDLE
                }
                CompanionInvitationScannerState.PERMISSION_REQUIRED -> {
                    // A restored explicit Scan intent can outlive the native host that owned its prompt.
                    if (permissionFlow == CameraEntryPhase.REQUESTING) scanner.requestCameraPermission()
                }
                CompanionInvitationScannerState.FAILED, CompanionInvitationScannerState.UNAVAILABLE -> permissionFlow = CameraEntryPhase.IDLE
                CompanionInvitationScannerState.PERMISSION_REQUESTING -> Unit
            }
        }
    }
    val scan: () -> Unit = scan@{
        if (!canScan || permissionFlow == CameraEntryPhase.REQUESTING ||
            scanner.state.value == CompanionInvitationScannerState.PERMISSION_REQUESTING) return@scan
        when (scanner.state.value) {
            CompanionInvitationScannerState.PERMISSION_REQUIRED, CompanionInvitationScannerState.PERMISSION_DENIED -> {
                permissionFlow = CameraEntryPhase.REQUESTING
                scanner.requestCameraPermission()
            }
            CompanionInvitationScannerState.PERMISSION_PERMANENTLY_DENIED -> permissionFlow = CameraEntryPhase.HELP
            CompanionInvitationScannerState.PERMISSION_REQUESTING -> Unit
            else -> onAction(CompanionAction.ScanInvitationRequested)
        }
    }
    val chooseImage: () -> Unit = {
        permissionFlow = CameraEntryPhase.IDLE
        onAction(CompanionAction.PickInvitationImageRequested)
    }
    CompanionHeroPage {
        when {
            busy -> {
                CompanionHeading(stringResource(Res.string.pairing_title), stringResource(Res.string.pairing_summary), centered = true)
                CompanionRoundControl(CompanionRoundControlState(CompanionControlSymbol.LINK,
                    stringResource(Res.string.pairing_action), busy = true))
                CompanionPanel {
                    CompanionStatusRow(stringResource(Res.string.pairing_invitation), stringResource(Res.string.pairing_read), KNetTheme.colors.semantic.success)
                    HorizontalDivider(color = KNetTheme.colors.border)
                    CompanionStatusRow(stringResource(Res.string.pairing_identity), stringResource(Res.string.pairing_verifying))
                    HorizontalDivider(color = KNetTheme.colors.border)
                    CompanionStatusRow(stringResource(Res.string.pairing_label), stringResource(Res.string.pairing_in_progress))
                }
                CompanionFootnote(stringResource(Res.string.pairing_note))
            }
            failed != null -> {
                CompanionHeading(failureTitle(failed.kind), failureMessage(failed), centered = true)
                CompanionRoundControl(CompanionRoundControlState(CompanionControlSymbol.SCAN,
                    stringResource(Res.string.control_scan), stringResource(Res.string.scan_another_qr), enabled = canScan)) {
                    onAction(CompanionAction.ClearFailure)
                    scan()
                }
                ChooseQrImage(canScan, chooseImage)
                ConnectionGuidance(render.feedback)
            }
            scanning -> {
                CompanionHeading(stringResource(Res.string.scanner_title), stringResource(Res.string.scan_invitation_active), centered = true)
                InlineInvitationScanner(state, scanner, onAction,
                    Modifier.fillMaxWidth().heightIn(max = 360.dp).aspectRatio(1f))
                ChooseQrImage(canScan, chooseImage)
                CompanionPanel { CompanionInfo(stringResource(Res.string.scanner_desktop_hint), KNetIcons.Desktop) }
            }
            else -> {
                CompanionHeading(stringResource(Res.string.connect_title), stringResource(Res.string.connect_summary), centered = true)
                CompanionRoundControl(CompanionRoundControlState(CompanionControlSymbol.SCAN,
                    stringResource(Res.string.control_scan), stringResource(Res.string.scan_qr), enabled = canScan)) {
                    scan()
                }
                ChooseQrImage(canScan, chooseImage)
                ConnectionGuidance(render.feedback)
                CompanionFootnote(stringResource(Res.string.connect_security_note))
            }
        }
        if (!busy && state.activeRegistration == null && state.registrations.isNotEmpty() &&
            state.desktopManagement != CompanionDesktopManagementState.Adding) {
            CompanionActionButton(stringResource(Res.string.desktops_title),
                { permissionFlow = CameraEntryPhase.IDLE; onAction(CompanionAction.OpenDesktopsRequested) }, variant = ButtonVariant.Ghost)
        }
    }
    if (permissionFlow == CameraEntryPhase.HELP && !busy) CameraPermissionSheet(
        onDismiss = { permissionFlow = CameraEntryPhase.IDLE },
        onSettings = { permissionFlow = CameraEntryPhase.SETTINGS; scanner.openApplicationSettings() },
        onChooseImage = chooseImage,
    )
}

/** Save only the explicit user intent; permission facts always come from the platform adapter. */
private enum class CameraEntryPhase { IDLE, REQUESTING, HELP, SETTINGS }
private val cameraEntrySaver = Saver<CameraEntryPhase, String>(save = { it.name }, restore = { CameraEntryPhase.valueOf(it) })

/** Visible secondary action reused on Connect, scanner, recovery and the camera help sheet. */
@Composable
internal fun ChooseQrImage(enabled: Boolean = true, onClick: () -> Unit) {
    CompanionActionButton(stringResource(Res.string.select_qr_image), onClick,
        variant = ButtonVariant.Secondary, enabled = enabled, leadingIcon = KNetIcons.Image)
}

@Composable
private fun ConnectionGuidance(feedback: CompanionConnectFeedback) {
    CompanionPanel {
        CompanionInfo(stringResource(Res.string.scanner_desktop_hint), KNetIcons.Desktop)
        HorizontalDivider(color = KNetTheme.colors.border)
        CompanionInfo(stringResource(when (feedback) {
            CompanionConnectFeedback.NetworkUnavailable -> Res.string.network_pairing_unavailable
            CompanionConnectFeedback.CheckingNetwork -> Res.string.checking_local_network
            else -> Res.string.same_local_network
        }), KNetIcons.Wifi, busy = feedback == CompanionConnectFeedback.CheckingNetwork)
    }
}
