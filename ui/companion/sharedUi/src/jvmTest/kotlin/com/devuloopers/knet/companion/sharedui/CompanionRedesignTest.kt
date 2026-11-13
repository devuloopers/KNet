package com.devuloopers.knet.companion.sharedui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.devuloopers.knet.companion.application.usecase.CompanionDesktopSwitchPhase
import com.devuloopers.knet.companion.model.*
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.*
import com.devuloopers.knet.companion.sharedui.scanner.*
import com.devuloopers.knet.companion.sharedui.screen.certificate.*
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.awt.EventQueue
import java.io.File
import kotlin.test.*

/** Headless interaction and layout checks of the actual Android/iOS shared root, including its modal layers. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class CompanionRedesignTest {
    @Test
    fun scanRequestsNativePermissionDirectlyAndOpensScannerAfterGrant() = onUi {
        fixture(CompanionUiState(network = CompanionNetworkState.Available(false))) { ui ->
            ui.scanner.immediatePermissionResult = null
            assertTrue("Connect your desktop" in ui.labels())
            ui.assertVisible("Scan desktop QR")
            ui.assertButton("Choose QR image")
            ui.save("connect")
            ui.click("Scan desktop QR")
            ui.click("Scan desktop QR")
            ui.frame()
            assertEquals(1, ui.scanner.permissionRequests)
            assertTrue(ui.actions.isEmpty())
            assertFalse("Camera access needed" in ui.labels())
            assertFalse("Enable camera access" in ui.labels())
            ui.scanner.state.value = CompanionInvitationScannerState.STARTING
            ui.frame()
            assertEquals(listOf<CompanionAction>(CompanionAction.ScanInvitationRequested), ui.actions)
            ui.state.value = ui.state.value.copy(invitationScannerVisible = true)
            ui.frame()
            assertTrue(ui.scanner.previews > 0)
            assertEquals(1, ui.scanner.permissionRequests)
        }
    }

    @Test
    fun denialsStayOnConnectAndOnlyANewScanTapRetriesOrShowsSettingsHelp() = onUi {
        fixture(CompanionUiState(network = CompanionNetworkState.Available(false))) { ui ->
            ui.scanner.immediatePermissionResult = null
            ui.click("Scan desktop QR")
            ui.scanner.state.value = CompanionInvitationScannerState.PERMISSION_DENIED
            ui.frame()
            assertTrue("Connect your desktop" in ui.labels())
            assertFalse("Enable camera access" in ui.labels())
            assertEquals(1, ui.scanner.permissionRequests)
            ui.click("Scan desktop QR")
            ui.frame()
            assertEquals(2, ui.scanner.permissionRequests)
            ui.scanner.state.value = CompanionInvitationScannerState.PERMISSION_PERMANENTLY_DENIED
            ui.frame()
            assertFalse("Enable camera access" in ui.labels())
            ui.click("Scan desktop QR")
            ui.frame()
            assertTrue("Enable camera access" in ui.labels())
            assertEquals(2, ui.scanner.permissionRequests)
            ui.assertVisible("Open Settings")
            ui.save("camera-access")
            ui.click("Not now")
            ui.frame()
            assertFalse("Enable camera access" in ui.labels())
            ui.scanner.state.value = CompanionInvitationScannerState.STARTING
            ui.frame()
            assertTrue(ui.actions.isEmpty())
        }
    }

    @Test
    fun blockedPermissionUsesSettingsAndAutomaticallyScansWhenGrantedOnReturn() = onUi {
        fixture(CompanionUiState(network = CompanionNetworkState.Available(false))) { ui ->
            ui.scanner.state.value = CompanionInvitationScannerState.PERMISSION_PERMANENTLY_DENIED
            ui.frame()
            ui.click("Scan desktop QR")
            ui.frame()
            ui.click("Open Settings")
            ui.frame()
            assertEquals(1, ui.scanner.openSettingsCalls)
            assertEquals(0, ui.scanner.permissionRequests)
            assertFalse("Enable camera access" in ui.labels())
            assertTrue(ui.actions.isEmpty())
            ui.scanner.state.value = CompanionInvitationScannerState.STARTING
            ui.frame()
            assertEquals(listOf<CompanionAction>(CompanionAction.ScanInvitationRequested), ui.actions)
            ui.frame()
            assertEquals(1, ui.actions.size)
        }
    }

    @Test
    fun returningFromSettingsWithoutGrantDoesNotReopenHelpOrPromptAutomatically() = onUi {
        fixture(CompanionUiState(network = CompanionNetworkState.Available(false))) { ui ->
            ui.scanner.state.value = CompanionInvitationScannerState.PERMISSION_PERMANENTLY_DENIED
            ui.frame()
            ui.click("Scan desktop QR")
            ui.frame()
            ui.click("Open Settings")
            ui.frame()
            assertFalse("Enable camera access" in ui.labels())
            assertTrue(ui.actions.isEmpty())
            ui.click("Scan desktop QR")
            ui.frame()
            assertTrue("Enable camera access" in ui.labels())
            ui.click("Dismiss")
            ui.frame()
            assertFalse("Enable camera access" in ui.labels())
            assertEquals(0, ui.scanner.permissionRequests)
        }
    }

    @Test
    fun galleryButtonWorksWithBlockedCameraOnMainAndHelpWithoutRequestingPermission() = onUi {
        for (fromSheet in listOf(false, true)) fixture(CompanionUiState(network = CompanionNetworkState.Available(false))) { ui ->
            ui.scanner.state.value = CompanionInvitationScannerState.PERMISSION_PERMANENTLY_DENIED
            ui.frame()
            if (fromSheet) {
                ui.click("Scan desktop QR")
                ui.frame()
            }
            ui.click("Choose QR image")
            ui.frame()
            assertEquals(listOf<CompanionAction>(CompanionAction.PickInvitationImageRequested), ui.actions)
            assertEquals(0, ui.scanner.permissionRequests)
            assertEquals(0, ui.scanner.openSettingsCalls)
            assertFalse("Enable camera access" in ui.labels())
            ui.scanner.state.value = CompanionInvitationScannerState.STARTING
            ui.frame()
            assertEquals(1, ui.actions.size)
        }
    }

    @Test
    fun existingPermissionSkipsPromptAndBlockedSheetActionsRemainReachableWithLargeText() = onUi {
        fixture(CompanionUiState(network = CompanionNetworkState.Available(false)), width = 320, height = 600, scale = 1.3f) { ui ->
            ui.scanner.state.value = CompanionInvitationScannerState.STARTING
            ui.frame()
            ui.click("Scan desktop QR")
            assertEquals(CompanionAction.ScanInvitationRequested, ui.actions.last())
            assertEquals(0, ui.scanner.permissionRequests)
            ui.scanner.state.value = CompanionInvitationScannerState.PERMISSION_PERMANENTLY_DENIED
            ui.frame()
            ui.click("Scan desktop QR")
            ui.frame()
            ui.reveal("Open Settings")
            ui.assertVisible("Open Settings")
            ui.reveal("Not now")
            ui.assertVisible("Not now")
            ui.save("camera-access-large")
            ui.click("Not now")
            ui.frame()
            assertFalse("Enable camera access" in ui.labels())
        }
    }

    @Test
    fun scannerUsesNativePreviewAndExpiredQrOffersFreshScanAndImagePicker() = onUi {
        fixture(CompanionUiState(invitationScannerVisible = true, network = CompanionNetworkState.Available(false))) { ui ->
            ui.scanner.state.value = CompanionInvitationScannerState.ACTIVE
            ui.frame()
            assertTrue(ui.scanner.previews > 0)
            ui.save("scanner")
            ui.state.value = ui.state.value.copy(failure = CompanionFailure(CompanionFailureCode.INVITATION_EXPIRED, "Expired.", false))
            ui.frame()
            assertTrue("QR code expired" in ui.labels())
            ui.click("Scan new QR")
            assertEquals(listOf(CompanionAction.ClearFailure, CompanionAction.ScanInvitationRequested), ui.actions.takeLast(2))
            ui.click("Choose QR image")
            assertEquals(CompanionAction.PickInvitationImageRequested, ui.actions.last())
            ui.save("expired-qr")
        }
    }

    @Test
    fun androidAndIosGuidanceKeepSettingsActionVisibleWithLargerText() = onUi {
        for (platform in CertificateInstallationPlatform.entries) for (scale in listOf(1f, 1.3f)) fixture(
            ready().copy(certificateEnrollment = null, certificate = CompanionCertificateState.InstallationRequired,
                certificateExport = CompanionCertificateExportState.Saved(registration.desktopId,
                    if (platform == CertificateInstallationPlatform.IOS) "KNet.mobileconfig" else "KNet-Root-CA.crt", "Downloads/KNet")),
            scale = scale, platform = platform, width = if (scale > 1) 360 else 420, height = if (scale > 1) 740 else 920,
        ) { ui ->
            val label = if (platform == CertificateInstallationPlatform.IOS) "Open Settings" else "Open certificate settings"
            ui.assertVisible(label)
            ui.click(label)
            assertEquals(CompanionAction.OpenCertificateTrustSettingsRequested, ui.actions.last())
            assertFalse("Start inspection" in ui.labels())
            ui.reveal("Enable trust if required, then return to Companion.")
            ui.assertVisible("Enable trust if required, then return to Companion.")
            ui.assertVisible(label)
            ui.save("install-${platform.name.lowercase()}${if (scale > 1) "-large" else ""}")
        }
    }

    @Test
    fun certificateRootSupportsCheckingMissingTrustedAndUnavailableWithoutFalseDownload() = onUi {
        fixture(ready().copy(certificateEnrollment = null, certificate = CompanionCertificateState.Unknown)) { ui ->
            assertTrue("Checking certificate" in ui.labels())
            assertFalse("Download certificate" in ui.labels())
            ui.save("checking")
            ui.state.value = ui.state.value.copy(certificate = CompanionCertificateState.InstallationRequired)
            ui.frame()
            ui.assertVisible("Download certificate")
            ui.save("download")
            ui.state.value = ui.state.value.copy(certificate = CompanionCertificateState.Trusted(registration.rootCertificateSha256, 1_000L))
            ui.frame()
            ui.assertVisible("Continue")
            assertFalse("Download certificate" in ui.labels())
            ui.save("verified")
            ui.state.value = ui.state.value.copy(certificate = CompanionCertificateState.VerificationDeferred(
                CompanionFailure(CompanionFailureCode.TRANSPORT_UNAVAILABLE, "Office Mac could not be reached.", true)))
            ui.frame()
            ui.assertVisible("Retry")
            assertFalse("Download certificate" in ui.labels())
            ui.save("verification-unavailable")
        }
    }

    @Test
    fun homeRuntimeStatesShareStableStatusRowsAndTruthfulActions() = onUi {
        fixture(ready()) { ui ->
            ui.assertVisible("Start inspection")
            val original = ui.boundsForLabel("Start inspection")
            ui.click("Start inspection")
            assertEquals(CompanionAction.StartInspectionRequested, ui.actions.last())
            ui.save("ready")
            ui.state.value = ready().copy(inspection = CompanionInspectionState.Running(CompanionInspectionMode.DEVICE_VPN, 1_000L, true))
            ui.frame()
            assertTrue("Inspection is running" in ui.labels())
            ui.assertVisible("Stop inspection")
            assertEquals(original.top, ui.boundsForLabel("Stop inspection").top)
            ui.click("Stop inspection")
            assertEquals(CompanionAction.StopInspectionRequested, ui.actions.last())
            ui.save("running")
            ui.state.value = ready().copy(desktopAvailability = CompanionDesktopAvailability.Unavailable(registration.desktopId,
                CompanionFailure(CompanionFailureCode.TRANSPORT_UNAVAILABLE, "Desktop unavailable.", true)),
                certificate = CompanionCertificateState.VerificationDeferred(CompanionFailure(CompanionFailureCode.TRANSPORT_UNAVAILABLE, "Offline.", true)))
            ui.frame()
            ui.assertVisible("Switch desktop")
            assertTrue("Previously verified" in ui.labels())
            ui.click("Switch desktop")
            assertEquals(CompanionAction.OpenDesktopsRequested, ui.actions.last())
            ui.save("unavailable")
        }
    }

    @Test
    fun desktopManagerCanAddSwitchAndConfirmForgetWithoutClearingOtherDesktops() = onUi {
        fixture(ready().copy(registrations = listOf(registration, other), desktopManagement = CompanionDesktopManagementState.Listing)) { ui ->
            assertTrue("2 saved desktops" in ui.labels())
            ui.click("Switch")
            assertEquals(CompanionAction.RegistrationSelected(other.desktopId), ui.actions.last())
            ui.click("Add desktop")
            assertEquals(CompanionAction.AddDesktopRequested, ui.actions.last())
            ui.save("desktops")
            ui.state.value = ui.state.value.copy(desktopManagement = CompanionDesktopManagementState.ConfirmForget(other))
            ui.frame()
            assertTrue("Forget this desktop?" in ui.labels())
            ui.click("Forget desktop")
            assertEquals(CompanionAction.ForgetDesktopRequested(other.desktopId), ui.actions.last())
            ui.save("forget-desktop")
        }
    }

    @Test
    fun switchingAndRecoveryShowActualSelectionAndRetryTheSameTarget() = onUi {
        fixture(ready().copy(desktopManagement = CompanionDesktopManagementState.Switching(registration, other,
            CompanionDesktopSwitchPhase.VERIFYING_DESKTOP), operationInProgress = true)) { ui ->
            assertTrue("Switching desktop" in ui.labels())
            assertTrue("Verify selected desktop" in ui.labels())
            assertFalse("Start inspection" in ui.labels())
            ui.save("switching")
            ui.state.value = ui.state.value.copy(operationInProgress = false, desktopManagement = CompanionDesktopManagementState.SwitchFailed(other,
                CompanionFailure(CompanionFailureCode.TRANSPORT_UNAVAILABLE, "Personal MacBook is unavailable.", true)))
            ui.frame()
            assertTrue("Still selected" in ui.labels())
            ui.click("Retry switch")
            assertEquals(CompanionAction.RegistrationSelected(other.desktopId), ui.actions.last())
            ui.click("Back to desktops")
            assertEquals(CompanionAction.OpenDesktopsRequested, ui.actions.last())
            ui.save("switch-failed")
        }
    }

    @Test
    fun vpnExplanationRequiresExplicitContinueAndCanBeDismissed() = onUi {
        fixture(ready().copy(inspectionPermissionRequired = true)) { ui ->
            assertTrue("Allow local VPN" in ui.labels())
            ui.click("Continue")
            assertEquals(CompanionAction.VpnConsentRequested, ui.actions.last())
            ui.click("Not now")
            assertEquals(CompanionAction.InspectionPermissionDismissed, ui.actions.last())
            ui.save("vpn-explanation")
        }
    }

    @Test
    fun compactHomeCanScrollToItsActionAndSavedDesktopsRemainAvailableWithoutAnActiveSelection() = onUi {
        fixture(ready(), width = 320, height = 600, scale = 1.3f) { ui ->
            ui.reveal("Start inspection")
            ui.assertVisible("Start inspection")
            ui.save("compact-home")
            ui.state.value = CompanionUiState(registrations = listOf(other), network = CompanionNetworkState.Available(false))
            ui.frame()
            ui.reveal("Desktops")
            ui.click("Desktops")
            assertEquals(CompanionAction.OpenDesktopsRequested, ui.actions.last())
        }
    }

    @Test
    fun pairingReplacesCameraAndCannotStartAnotherOperation() = onUi {
        fixture(CompanionUiState(invitationScannerVisible = true, network = CompanionNetworkState.Available(false))) { ui ->
            ui.scanner.state.value = CompanionInvitationScannerState.ACTIVE
            ui.frame()
            val previews = ui.scanner.previews
            ui.state.value = ui.state.value.copy(operationInProgress = true)
            ui.frame()
            assertTrue("Pairing your desktop" in ui.labels())
            assertFalse("Choose QR image" in ui.labels())
            assertFalse("Scan desktop QR" in ui.labels())
            assertEquals(previews, ui.scanner.previews)
            assertEquals(1, ui.progressCount())
            ui.assertNotActionable("Pairing…")
            ui.save("pairing")
        }
    }

    @Test
    fun startingAndStoppingAreNonInteractiveAndPreserveTheControlPosition() = onUi {
        fixture(ready().copy(inspection = CompanionInspectionState.Preparing)) { ui ->
            assertTrue("Starting inspection" in ui.labels())
            ui.assertNotActionable("Starting…")
            assertTrue("Connecting" in ui.labels())
            ui.save("starting")
            val starting = ui.boundsForLabel("Starting…")
            ui.state.value = ready().copy(inspection = CompanionInspectionState.Stopping)
            ui.frame()
            ui.assertNotActionable("Stopping…")
            assertTrue("Stopping inspection" in ui.labels())
            assertTrue("Disconnecting" in ui.labels())
            assertEquals(starting.top, ui.boundsForLabel("Stopping…").top)
            ui.save("stopping")
        }
    }

    @Test
    fun transportConnectionAloneDoesNotClaimCaptureIsActive() = onUi {
        fixture(ready().copy(connection = CompanionConnectionState.Connected(registration.desktopId, CompanionTransportKind.DIRECT_LAN, 1_000L))) { ui ->
            assertTrue("Not running" in ui.labels())
            assertFalse("Active" in ui.labels())
            assertFalse("Stop inspection" in ui.labels())
            ui.click("Start inspection")
            assertEquals(CompanionAction.StartInspectionRequested, ui.actions.last())
        }
    }

    private fun fixture(state: CompanionUiState, width: Int = 420, height: Int = 920, scale: Float = 1f,
        platform: CertificateInstallationPlatform = CertificateInstallationPlatform.ANDROID, block: (Ui) -> Unit) {
        val ui = Ui(state, width, height)
        ImageComposeScene(width, height, Density(1f, scale), content = {
            KNetCompanionApp(state = ui.state.value, onAction = { ui.actions += it }, onExitRequested = {},
                certificateInstallationGuidance = CertificateInstallationGuidance(platform = platform, steps = listOf(
                    "Open the certificate installation settings on your device.",
                    "Select the downloaded KNet certificate and confirm.", "Enable trust if required, then return to Companion.")),
                invitationScanner = ui.scanner)
        }).use { ui.scene = it; ui.frame(); block(ui) }
    }

    private class Scanner : CompanionInvitationScanner {
        override val state = MutableStateFlow(CompanionInvitationScannerState.PERMISSION_REQUIRED)
        var openSettingsCalls = 0
        var permissionRequests = 0
        var immediatePermissionResult: CompanionInvitationScannerState? = CompanionInvitationScannerState.STARTING
        var previews = 0
        override fun requestCameraPermission() {
            permissionRequests++
            state.value = CompanionInvitationScannerState.PERMISSION_REQUESTING
            immediatePermissionResult?.let { state.value = it }
        }
        override fun openApplicationSettings() { openSettingsCalls++ }
        @Composable override fun Preview(onPayloadDetected: (String) -> Unit, modifier: Modifier) {
            SideEffect { previews++ }
            Box(modifier.background(KNetTheme.colors.surfaceVariant))
        }
        override fun close() = Unit
    }

    private class Ui(initial: CompanionUiState, val width: Int, val height: Int) {
        val state = mutableStateOf(initial)
        val actions = mutableListOf<CompanionAction>()
        val scanner = Scanner()
        lateinit var scene: ImageComposeScene
        private var time = 0L
        fun frame() { repeat(12) { scene.render(++time * 100_000_000L).close() } }
        fun labels() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        fun click(label: String) {
            val node = nodes().last { node -> node.config.getOrNull(SemanticsActions.OnClick) != null &&
                descendants(node).any { child -> child.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == label } ||
                    child.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains(label) } }
            assertTrue(assertNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.action).invoke())
        }
        fun boundsForLabel(label: String): Rect {
            val node = nodes().last { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == label } }
            return Rect(node.positionInWindow, node.size.toSize())
        }
        fun reveal(label: String) {
            repeat(8) {
                val bounds = boundsForLabel(label)
                if (bounds.top >= 0 && bounds.bottom <= height) return
                val scroll = nodes().mapNotNull { it.config.getOrNull(SemanticsActions.ScrollBy)?.action }.last()
                assertTrue(scroll.invoke(0f, if (bounds.bottom > height) bounds.bottom - height + 16f else bounds.top - 16f))
                frame()
            }
            assertVisible(label)
        }
        fun assertButton(label: String) {
            assertTrue(nodes().any { node -> node.config.getOrNull(SemanticsProperties.Role) == Role.Button &&
                descendants(node).any { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == label } } })
        }
        fun progressCount() = nodes().count { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null }
        fun assertNotActionable(label: String) {
            assertFalse(nodes().any { node -> node.config.getOrNull(SemanticsActions.OnClick) != null &&
                descendants(node).any { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == label } } })
        }
        fun assertVisible(label: String) {
            val rect = boundsForLabel(label)
            assertTrue(rect.top >= 0 && rect.bottom <= height && rect.left >= 0 && rect.right <= width, "$label outside viewport: $rect")
        }
        fun save(name: String) { scene.render(++time * 100_000_000L).use { it.encodeToData()?.use { data ->
            File("/tmp/knet-companion-redesign-$name.png").writeBytes(data.bytes)
        } } }
        private fun nodes() = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
        private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
    }

    private fun onUi(block: () -> Unit) = EventQueue.invokeAndWait(block)
    private val registration = certificateTestRegistration().copy(desktopDisplayName = CompanionDesktopDisplayName("Office Mac"))
    private val other = registration.copy(desktopId = CompanionDesktopId("other"), desktopDisplayName = CompanionDesktopDisplayName("Personal MacBook"),
        controlEndpoint = CompanionServiceEndpoint("192.168.1.25", 8183, CompanionEndpointScheme.HTTPS))
    private fun ready(): CompanionUiState = CompanionUiState(registrations = listOf(registration), activeRegistration = registration,
        certificate = CompanionCertificateState.Trusted(registration.rootCertificateSha256, 1_000L),
        certificateEnrollment = CompanionCertificateEnrollment(registration.desktopId, registration.rootCertificateSha256, 1_000L),
        desktopAvailability = CompanionDesktopAvailability.Available(registration.desktopId, 1_000L), network = CompanionNetworkState.Available(false))
}
