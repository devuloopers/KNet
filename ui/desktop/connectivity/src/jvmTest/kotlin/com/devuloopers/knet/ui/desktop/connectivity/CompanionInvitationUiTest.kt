package com.devuloopers.knet.ui.desktop.connectivity

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import com.devuloopers.knet.ui.core.foundation.motion.Motion
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.connectivity.components.CompanionConnectionDrawer
import com.devuloopers.knet.ui.desktop.connectivity.model.*
import java.awt.EventQueue
import java.io.File
import kotlin.test.*

/** Renders the real drawer to verify that consumed secrets leave the QR surface and recovery stays usable. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class CompanionInvitationUiTest {
    @Test
    fun usedQrIsReplacedByExpiredCopyAndAnExplicitRegenerateAction() = EventQueue.invokeAndWait {
        val invitation = CompanionInvitationUiState.Ready("knet://pair/v3?test", "Office Mac", "192.0.2.10", 8183,
            301_000L, 300L, 1L)
        val state = mutableStateOf(ConnectDeviceUiState(activeDrawer = ConnectDeviceDrawer.COMPANION_CONNECTION,
            companionInvitation = invitation))
        val intents = mutableListOf<ConnectDeviceIntent>()
        ImageComposeScene(960, 720, Density(1f), content = {
            KNetTheme(motion = Motion(animationsEnabled = false)) { CompanionConnectionDrawer(state.value, intents::add) }
        }).use { scene ->
            fun render(time: Long) { scene.render(time).close() }
            repeat(4) { render(it * 100_000_000L) }
            fun nodes() = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
            fun hasQr() = nodes().any { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains("Companion pairing QR code") }
            assertTrue(hasQr())
            state.value = state.value.copy(companionInvitation = CompanionInvitationUiState.Expired)
            repeat(4) { render((it + 4) * 100_000_000L) }
            assertFalse(hasQr())
            assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text ->
                text.text == "This QR code has expired. Click to regenerate." } })
            assertTrue(intents.isEmpty())
            val button = nodes().single { it.config.getOrNull(SemanticsActions.OnClick) != null &&
                descendants(it).any { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == "Regenerate QR" } } }
            assertTrue(button.config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(listOf<ConnectDeviceIntent>(ConnectDeviceIntent.RefreshCompanionInvitation), intents)
            scene.render(900_000_000L).use { it.encodeToData()!!.use { data -> File("/tmp/knet-desktop-qr-expired.png").writeBytes(data.bytes) } }
            state.value = state.value.copy(companionInvitation = invitation.copy(qrPayload = "knet://pair/v3?fresh"))
            repeat(4) { render((it + 10) * 100_000_000L) }
            assertTrue(hasQr())
        }
    }

    @Test
    fun regenerateRemainsVisibleInCompactLargerTextDrawer() = EventQueue.invokeAndWait {
        ImageComposeScene(420, 600, Density(1f, 1.3f), content = {
            KNetTheme(motion = Motion(animationsEnabled = false)) { CompanionConnectionDrawer(
                ConnectDeviceUiState(activeDrawer = ConnectDeviceDrawer.COMPANION_CONNECTION,
                    companionInvitation = CompanionInvitationUiState.Expired), {}) }
        }).use { scene ->
            repeat(4) { scene.render(it * 100_000_000L).close() }
            val label = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }.single { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Regenerate QR" } }
            val rect = Rect(label.positionInWindow, label.size.toSize())
            assertTrue(rect.left >= 0 && rect.right <= 420 && rect.top >= 0 && rect.bottom <= 600, "Action clipped: $rect")
            scene.render(500_000_000L).use { it.encodeToData()!!.use { data -> File("/tmp/knet-desktop-qr-expired-compact.png").writeBytes(data.bytes) } }
        }
    }

    private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
}
