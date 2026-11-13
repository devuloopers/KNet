package com.devuloopers.knet.companion.sharedui.screen.certificate

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionCertificateExportState
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.ui.core.foundation.motion.Motion
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue
import java.io.File
import kotlin.test.*

/** Actual common certificate screen keeps download behind the trust check on both platform UI paths. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class CertificateSetupScreenTest {
    @Test
    fun existingTrustTransitionsFromCheckingToContinueWithoutDownloadOrFileDetails() = EventQueue.invokeAndWait {
        fixture { ui ->
            assertTrue("Checking certificate" in ui.labels())
            assertFalse("Download certificate" in ui.labels())
            ui.save("checking")
            ui.state.value = ui.state.value.copy(certificate =
                CompanionCertificateState.Trusted(ui.registration.rootCertificateSha256, 1_000L))
            ui.frame()
            assertTrue("Certificate verified" in ui.labels())
            assertFalse("Download certificate" in ui.labels())
            assertFalse("Downloaded file" in ui.labels())
            ui.scrollToBottom()
            ui.click("Continue")
            assertEquals(listOf<CompanionAction>(CompanionAction.ContinueCertificateSetupRequested), ui.actions)
            ui.save("already-trusted")
        }
    }

    @Test
    fun unavailableCheckOffersRetryAndInstallationAppearsOnlyAfterMissingTrustAndExport() = EventQueue.invokeAndWait {
        fixture(scale = 1.25f) { ui ->
            ui.state.value = ui.state.value.copy(certificate = CompanionCertificateState.VerificationDeferred(
                CompanionFailure(CompanionFailureCode.TRANSPORT_UNAVAILABLE, "Desktop unavailable.", true)))
            ui.frame()
            assertTrue("Couldn’t verify certificate" in ui.labels())
            assertTrue("Desktop unavailable." in ui.labels())
            assertFalse("Download certificate" in ui.labels())
            ui.click("Retry")
            assertEquals(CompanionAction.VerifyCertificateTrustRequested, ui.actions.last())
            ui.save("unavailable")
            ui.state.value = ui.state.value.copy(certificate = CompanionCertificateState.Verifying)
            ui.frame()
            assertTrue("Checking certificate" in ui.labels())
            assertFalse("Retry" in ui.labels())
            ui.state.value = ui.state.value.copy(certificate = CompanionCertificateState.InstallationRequired)
            ui.frame()
            assertTrue("Download certificate" in ui.labels())
            assertFalse("Install certificate" in ui.labels())
            ui.state.value = ui.state.value.copy(certificateExport = CompanionCertificateExportState.Saved(
                ui.registration.desktopId, "KNet-root.crt", "Downloads"))
            ui.frame()
            assertTrue("Install certificate" in ui.labels())
            assertFalse("Certificate verified" in ui.labels())
            ui.save("installation")
        }
    }

    private fun fixture(scale: Float = 1f, block: (Ui) -> Unit) {
        val ui = Ui()
        ImageComposeScene(420, 920, Density(1f, scale), content = {
            KNetTheme(motion = Motion(animationsEnabled = false)) {
                CertificateSetupScreen(ui.state.value, CertificateInstallationGuidance(listOf("Install and trust the certificate in Settings.")),
                    onAction = { ui.actions += it })
            }
        }).use { scene -> ui.scene = scene; ui.frame(); block(ui) }
    }

    private class Ui {
        val registration = certificateTestRegistration()
        val state = mutableStateOf(CompanionUiState(activeRegistration = registration))
        val actions = mutableListOf<CompanionAction>()
        lateinit var scene: ImageComposeScene
        private var time = 0L
        fun frame() { repeat(4) { scene.render(++time * 1_000_000_000L).close() } }
        fun labels() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
        fun click(label: String) {
            val button = nodes().first { node -> node.config.getOrNull(SemanticsActions.OnClick) != null &&
                descendants(node).any { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == label } } }
            assertTrue(assertNotNull(button.config.getOrNull(SemanticsActions.OnClick)?.action).invoke())
        }
        fun scrollToBottom() {
            nodes().firstNotNullOfOrNull { it.config.getOrNull(SemanticsActions.ScrollBy)?.action }?.invoke(0f, 10_000f)
            frame()
        }
        fun save(name: String) {
            scene.render(++time * 1_000_000_000L).use { image -> image.encodeToData()?.use {
                File("/tmp/knet-companion-certificate-$name.png").writeBytes(it.bytes)
            }}
        }
        private fun nodes() = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
        private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
    }
}
